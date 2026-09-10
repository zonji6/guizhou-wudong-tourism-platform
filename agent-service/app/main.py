from __future__ import annotations

import asyncio
import json
import uuid
from contextlib import asynccontextmanager
from datetime import datetime, timedelta, timezone
from typing import Any

from fastapi import FastAPI, Header, Request, WebSocket, WebSocketDisconnect
from fastapi.responses import JSONResponse
from pydantic import TypeAdapter, ValidationError

from app.auth import AuthFailure, AuthService, RouteProfile
from app.candidates import resolve_candidate, verify_service_credential
from app.config import get_settings
from app.restore import restore_session_state
from app.run_store import FencedAsyncRedisSaver, RunStoreError
from app.streaming import request_cancel, run_assistant
from app.v3_contracts import (
    ASSISTANT_CONTRACT_VERSION,
    TOURISM_CONTRACT_VERSION,
    WS_AUTH_VERSION,
    AnonymousMiniAuthFrame,
    AnonymousWebAuthFrame,
    AuthFailed,
    AuthOk,
    BusinessRequest,
    CandidateResolveRequest,
    UserAuthFrame,
)


store = FencedAsyncRedisSaver()
auth_service = AuthService()


@asynccontextmanager
async def lifespan(app: FastAPI):
    await store.setup()
    yield
    await auth_service.close()
    await store.close()


app = FastAPI(title="贵州乌东文旅 AI 服务", version="0.3.0", lifespan=lifespan)


def _strict_json(text: str) -> object:
    def reject_duplicates(pairs: list[tuple[str, object]]) -> dict[str, object]:
        result: dict[str, object] = {}
        for key, value in pairs:
            if key in result:
                raise ValueError("duplicate key")
            result[key] = value
        return result

    return json.loads(text, object_pairs_hook=reject_duplicates)


def _headers() -> dict[str, str]:
    return {
        "X-Wudong-Contract": TOURISM_CONTRACT_VERSION,
        "X-Request-Id": str(uuid.uuid4()),
        "Cache-Control": "no-store",
    }


def _success(data: object, status_code: int = 200) -> JSONResponse:
    return JSONResponse(
        {"success": True, "data": data, "message": None, "code": None, "details": None},
        status_code=status_code,
        headers=_headers(),
    )


def _failure(code: str, message: str, status_code: int) -> JSONResponse:
    return JSONResponse(
        {"success": False, "data": None, "message": message, "code": code, "details": None},
        status_code=status_code,
        headers=_headers(),
    )


@app.get("/health")
async def health() -> dict[str, Any]:
    settings = get_settings()
    return {
        "status": "ok",
        "assistantContractVersion": ASSISTANT_CONTRACT_VERSION,
        "tourismContractVersion": TOURISM_CONTRACT_VERSION,
        "deepseek": "configured" if settings.deepseek_api_key else "not_configured",
        "keywordRag": "available_when_java_is_ready",
        "vector": "configured_not_proven" if settings.vector_configured else "unavailable_embedding_not_configured",
        "redis": "available" if await store.ping() else "unavailable",
        "officialSaver": store.official_saver_status,
        "langsmith": "configured" if settings.langsmith_tracing and settings.langsmith_api_key else "disabled",
    }


@app.post("/internal/assistant/candidates/resolve")
async def candidate_resolve(
    request: Request,
    x_wudong_service_credential: str | None = Header(default=None),
    x_wudong_user_proof: str | None = Header(default=None),
    x_wudong_anonymous_proof: str | None = Header(default=None),
    x_wudong_contract: str | None = Header(default=None),
) -> JSONResponse:
    if x_wudong_contract != TOURISM_CONTRACT_VERSION:
        return _failure("CONTRACT_INCOMPATIBLE", "接口版本不匹配。", 400)
    try:
        verify_service_credential(get_settings().java_to_ai_credential_file, x_wudong_service_credential)
        if not x_wudong_user_proof or not x_wudong_user_proof.startswith("Bearer "):
            raise RunStoreError("INTERNAL_FORBIDDEN")
        payload = CandidateResolveRequest.model_validate(
            _strict_json((await request.body()).decode("utf-8")),
            strict=True,
        )
        result = await resolve_candidate(
            payload,
            user_token=x_wudong_user_proof[7:],
            anonymous_credential=x_wudong_anonymous_proof,
            auth=auth_service,
            store=store,
        )
        return _success(result.model_dump(mode="json", by_alias=True))
    except (UnicodeDecodeError, json.JSONDecodeError, ValidationError, ValueError):
        return _failure("VALIDATION_FAILED", "请求格式不正确。", 400)
    except AuthFailure as exc:
        return _failure(exc.code, "身份校验失败。", 403 if exc.close_code == 4403 else 401)
    except RunStoreError as exc:
        status = {
            "INTERNAL_AUTH_UNAVAILABLE": 503,
            "INTERNAL_FORBIDDEN": 403,
            "CANDIDATE_EXPIRED": 410,
            "CANDIDATE_VERSION_UNAVAILABLE": 410,
            "CANDIDATE_PAYLOAD_INVALID": 409,
            "CANDIDATE_BASE_MISMATCH": 409,
        }.get(exc.code, 503)
        return _failure(exc.code, "候选方案暂时无法解析。", status)


async def _send_auth_failure(websocket: WebSocket, failure: AuthFailure) -> None:
    payload = AuthFailed(
        type="auth_failed",
        auth_version=WS_AUTH_VERSION,
        contract_version=TOURISM_CONTRACT_VERSION,
        code=failure.code,
        message=failure.message,
        retryable=failure.retryable,
        retry_after_seconds=None,
    ).model_dump(mode="json", by_alias=True, exclude_none=True)
    await websocket.send_json(payload)
    await websocket.close(code=failure.close_code, reason=failure.code)


def _user_thread_expiry() -> str:
    return (datetime.now(timezone.utc) + timedelta(days=30)).replace(microsecond=0).strftime("%Y-%m-%dT%H:%M:%SZ")


async def _session_payload(message: Any, principal: Any) -> dict[str, object]:
    if message.thread_id is None:
        if principal.owner_kind == "ANONYMOUS":
            if principal.thread_id is None:
                raise RunStoreError("FORBIDDEN")
            thread_id = principal.thread_id
            expires_at = principal.auth_expires_at
            await store.create_thread(
                thread_id=thread_id,
                owner_kind=principal.owner_kind,
                owner_digest=principal.owner_digest,
                logical_expires_at=expires_at,
            )
        else:
            expires_at = _user_thread_expiry()
            for _ in range(3):
                thread_id = str(uuid.uuid4())
                if await store.create_thread(
                    thread_id=thread_id,
                    owner_kind=principal.owner_kind,
                    owner_digest=principal.owner_digest,
                    logical_expires_at=expires_at,
                ):
                    break
            else:
                raise RunStoreError("CHECKPOINT_UNAVAILABLE")
    else:
        thread_id = message.thread_id
        if principal.owner_kind == "ANONYMOUS" and thread_id != principal.thread_id:
            raise RunStoreError("FORBIDDEN")
        await store.require_thread_access(thread_id, principal.owner_kind, principal.owner_digest)
    state = await restore_session_state(thread_id, principal, store)
    if message.run_id is None:
        if message.last_event_sequence != 0:
            raise RunStoreError("RUN_NOT_FOUND")
    elif (
        state.run is None
        or state.run.run_id != message.run_id
        or message.last_event_sequence > state.run.last_event_sequence
    ):
        raise RunStoreError("RUN_NOT_FOUND")
    return state.model_dump(mode="json", by_alias=True)


async def _assistant_socket(websocket: WebSocket, profile: RouteProfile) -> None:
    await websocket.accept()
    send_lock = asyncio.Lock()
    receive_task: asyncio.Task[str] | None = None
    active_task: asyncio.Task[None] | None = None
    active_thread_id: str | None = None
    active_run_id: str | None = None
    pending_stop: tuple[str, str, int, Any] | None = None

    async def send_payload(payload: dict[str, object], principal: Any) -> None:
        await auth_service.revalidate(principal)
        async with send_lock:
            await websocket.send_json(payload)

    async def stream_generate(message: Any, principal: Any, run_id: str) -> None:
        try:
            async for event in run_assistant(message, principal, store, run_id=run_id):
                await send_payload(event, principal)
        except RunStoreError as exc:
            if exc.code != "RUN_FENCE_CLOSED":
                raise

    async def consume_execution(task: asyncio.Task[None], *, suppress_errors: bool = False) -> None:
        try:
            await task
        except asyncio.CancelledError:
            pass
        except Exception:
            if not suppress_errors:
                raise

    try:
        auth_service.validate_origin(websocket, profile)
        text = await asyncio.wait_for(websocket.receive_text(), timeout=get_settings().ws_auth_timeout_seconds)
        raw = _strict_json(text)
        frame_type = {
            "USER": UserAuthFrame,
            "ANONYMOUS_WEB": AnonymousWebAuthFrame,
            "ANONYMOUS_MINI": AnonymousMiniAuthFrame,
        }[profile.mode]
        frame = frame_type.model_validate(raw, strict=True)
        principal = await auth_service.authenticate(websocket, profile, frame)
        auth_ok = AuthOk(
            type="auth_ok",
            auth_version=WS_AUTH_VERSION,
            contract_version=TOURISM_CONTRACT_VERSION,
            connection_id=str(uuid.uuid4()),
            mode=principal.owner_kind,
            auth_expires_at=principal.auth_expires_at,
        )
        await websocket.send_json(auth_ok.model_dump(mode="json", by_alias=True))
        while True:
            if receive_task is None:
                receive_task = asyncio.create_task(websocket.receive_text())
            waiters: set[asyncio.Task[Any]] = {receive_task}
            if active_task is not None:
                waiters.add(active_task)
            done, _ = await asyncio.wait(
                waiters,
                timeout=get_settings().auth_recheck_seconds,
                return_when=asyncio.FIRST_COMPLETED,
            )
            if not done:
                principal = await auth_service.revalidate(principal)
                continue
            if active_task is not None and active_task in done:
                finished_task = active_task
                await consume_execution(finished_task, suppress_errors=pending_stop is not None)
                if pending_stop is not None:
                    stop_thread, stop_run, stop_epoch, stop_principal = pending_stop
                    stopped = await store.finish_stopped(
                        thread_id=stop_thread,
                        run_id=stop_run,
                        epoch=stop_epoch,
                        owner_kind=stop_principal.owner_kind,
                        owner_digest=stop_principal.owner_digest,
                    )
                    if stopped is not None:
                        await send_payload(stopped.model_dump(mode="json", by_alias=True), stop_principal)
                    pending_stop = None
                active_task = None
                active_thread_id = None
                active_run_id = None
                if receive_task not in done:
                    continue
            if receive_task not in done:
                continue
            text = receive_task.result()
            receive_task = None
            principal = await auth_service.revalidate(principal)
            message = TypeAdapter(BusinessRequest).validate_python(_strict_json(text), strict=True)
            if message.type == "session_state_request":
                await send_payload(await _session_payload(message, principal), principal)
            elif message.type == "generate":
                if active_task is not None:
                    raise RunStoreError("RUN_ALREADY_ACTIVE")
                await store.require_thread_access(message.thread_id, principal.owner_kind, principal.owner_digest)
                active_thread_id = message.thread_id
                active_run_id = str(uuid.uuid4())
                active_task = asyncio.create_task(stream_generate(message, principal, active_run_id))
            else:
                cancellation = await request_cancel(message, principal, store)
                if cancellation.event is not None:
                    await send_payload(cancellation.event, principal)
                if (
                    cancellation.outcome in {"OK", "REPLAY"}
                    and active_task is not None
                    and active_thread_id == cancellation.thread_id
                    and active_run_id == cancellation.run_id
                ):
                    active_task.cancel()
                    quiesced, _ = await asyncio.wait(
                        {active_task},
                        timeout=get_settings().ai_cancel_wait_seconds,
                    )
                    if active_task in quiesced:
                        await consume_execution(active_task, suppress_errors=True)
                        stopped = await store.finish_stopped(
                            thread_id=cancellation.thread_id,
                            run_id=cancellation.run_id,
                            epoch=cancellation.fence_epoch,
                            owner_kind=principal.owner_kind,
                            owner_digest=principal.owner_digest,
                        )
                        if stopped is not None:
                            await send_payload(stopped.model_dump(mode="json", by_alias=True), principal)
                        pending_stop = None
                        active_task = None
                        active_thread_id = None
                        active_run_id = None
                    else:
                        pending_stop = (
                            cancellation.thread_id,
                            cancellation.run_id,
                            cancellation.fence_epoch,
                            principal,
                        )
    except TimeoutError:
        await _send_auth_failure(websocket, AuthFailure("AUTH_REQUIRED", 4408, "认证超时。"))
    except AuthFailure as exc:
        await _send_auth_failure(websocket, exc)
    except (json.JSONDecodeError, ValidationError, ValueError, KeyError):
        await _send_auth_failure(websocket, AuthFailure("FORBIDDEN", 4403, "请求格式不正确。"))
    except RunStoreError as exc:
        close_code = (
            4403
            if exc.code in {"FORBIDDEN", "RUN_NOT_FOUND"}
            else 4429
            if exc.code in {"RATE_LIMITED", "RUN_ALREADY_ACTIVE"}
            else 4503
        )
        await _send_auth_failure(
            websocket,
            AuthFailure(
                exc.code,
                close_code,
                "无权访问该对话。" if close_code == 4403 else "服务状态暂不可用。",
                retryable=close_code != 4403,
            ),
        )
    except WebSocketDisconnect:
        return
    finally:
        pending = [task for task in (receive_task, active_task) if task is not None and not task.done()]
        for task in pending:
            task.cancel()
        if pending:
            await asyncio.gather(*pending, return_exceptions=True)
        if pending_stop is not None and active_task is not None and active_task.done():
            stop_thread, stop_run, stop_epoch, stop_principal = pending_stop
            try:
                await store.finish_stopped(
                    thread_id=stop_thread,
                    run_id=stop_run,
                    epoch=stop_epoch,
                    owner_kind=stop_principal.owner_kind,
                    owner_digest=stop_principal.owner_digest,
                )
            except RunStoreError:
                pass


@app.websocket("/ws/web/user")
async def web_user_socket(websocket: WebSocket) -> None:
    await _assistant_socket(websocket, RouteProfile(surface="WEB", mode="USER"))


@app.websocket("/ws/web/anonymous")
async def web_anonymous_socket(websocket: WebSocket) -> None:
    await _assistant_socket(websocket, RouteProfile(surface="WEB", mode="ANONYMOUS_WEB"))


@app.websocket("/ws/mini/user")
async def mini_user_socket(websocket: WebSocket) -> None:
    await _assistant_socket(websocket, RouteProfile(surface="MINI", mode="USER"))


@app.websocket("/ws/mini/anonymous")
async def mini_anonymous_socket(websocket: WebSocket) -> None:
    await _assistant_socket(websocket, RouteProfile(surface="MINI", mode="ANONYMOUS_MINI"))
