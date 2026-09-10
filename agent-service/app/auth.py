from __future__ import annotations

import hashlib
import re
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Literal

import jwt
from fastapi import WebSocket
from redis.asyncio import Redis
from redis.exceptions import RedisError

from app.config import get_settings
from app.v3_contracts import AnonymousMiniAuthFrame, AnonymousWebAuthFrame, UserAuthFrame


UUID_RE = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
CREDENTIAL_RE = re.compile(r"^[A-Za-z0-9_-]{43}$")


@dataclass(frozen=True, slots=True)
class RouteProfile:
    surface: Literal["WEB", "MINI"]
    mode: Literal["USER", "ANONYMOUS_WEB", "ANONYMOUS_MINI"]


@dataclass(frozen=True, slots=True)
class AuthPrincipal:
    owner_kind: Literal["USER", "ANONYMOUS"]
    owner_digest: str
    surface: Literal["WEB", "MINI"]
    auth_expires_at: str
    subject: str | None
    sid: str | None
    thread_id: str | None
    access_token: str | None
    anonymous_credential: str | None


class AuthFailure(RuntimeError):
    def __init__(self, code: str, close_code: int, message: str, *, retryable: bool = False) -> None:
        super().__init__(code)
        self.code = code
        self.close_code = close_code
        self.message = message
        self.retryable = retryable


def _utc(value: int) -> str:
    return datetime.fromtimestamp(value, timezone.utc).replace(microsecond=0).strftime("%Y-%m-%dT%H:%M:%SZ")


def _read_public_key(path: Path | None) -> str:
    if path is None or not path.is_absolute() or not path.is_file():
        raise AuthFailure("AUTH_STATE_UNAVAILABLE", 4503, "登录校验暂不可用。", retryable=True)
    return path.read_text(encoding="utf-8")


class AuthService:
    def __init__(self) -> None:
        self.settings = get_settings()
        self.redis: Redis = Redis.from_url(self.settings.redis_url, decode_responses=True)

    async def close(self) -> None:
        await self.redis.aclose()

    def validate_origin(self, websocket: WebSocket, profile: RouteProfile) -> None:
        origin = websocket.headers.get("origin")
        if profile.surface == "WEB":
            if origin != self.settings.public_web_origin:
                raise AuthFailure("ORIGIN_REJECTED", 4403, "请求来源不受信任。")
        elif origin not in {None, "https://servicewechat.com"}:
            raise AuthFailure("ORIGIN_REJECTED", 4403, "请求来源不受信任。")

    async def authenticate(
        self,
        websocket: WebSocket,
        profile: RouteProfile,
        frame: UserAuthFrame | AnonymousWebAuthFrame | AnonymousMiniAuthFrame,
    ) -> AuthPrincipal:
        if frame.mode != profile.mode:
            raise AuthFailure("AUTH_MODE_MISMATCH", 4403, "认证方式与入口不匹配。")
        if isinstance(frame, UserAuthFrame):
            return await self._authenticate_user(frame.access_token, profile.surface)
        if isinstance(frame, AnonymousMiniAuthFrame):
            return await self._authenticate_anonymous(frame.anonymous_credential, profile.surface)
        lane_id = websocket.cookies.get("WD_ANON_LANE")
        if not lane_id:
            raise AuthFailure("AUTH_REQUIRED", 4401, "需要匿名会话。")
        try:
            lane = await self.redis.hgetall(f"anon:lane:WEB:{lane_id}")
        except RedisError as exc:
            raise AuthFailure("AUTH_STATE_UNAVAILABLE", 4503, "认证状态暂不可用。", retryable=True) from exc
        thread_id = lane.get("currentThreadId")
        if not thread_id or not UUID_RE.fullmatch(thread_id):
            raise AuthFailure("ANONYMOUS_EXPIRED", 4401, "匿名会话已过期。")
        credential = websocket.cookies.get(f"WD_ANON_{thread_id.replace('-', '')}")
        if not credential:
            raise AuthFailure("AUTH_REQUIRED", 4401, "需要匿名会话。")
        return await self._authenticate_anonymous(credential, "WEB", expected_lane_id=lane_id)

    async def revalidate(self, principal: AuthPrincipal) -> AuthPrincipal:
        if principal.owner_kind == "USER":
            if not principal.access_token:
                raise AuthFailure("AUTH_REQUIRED", 4401, "需要登录。")
            return await self._authenticate_user(principal.access_token, principal.surface)
        if not principal.anonymous_credential:
            raise AuthFailure("AUTH_REQUIRED", 4401, "需要匿名会话。")
        refreshed = await self._authenticate_anonymous(principal.anonymous_credential, principal.surface)
        if refreshed.thread_id != principal.thread_id:
            raise AuthFailure("ANONYMOUS_REVOKED", 4401, "匿名会话已失效。")
        return refreshed

    async def authenticate_user_proof(self, token: str) -> AuthPrincipal:
        try:
            claims = jwt.decode(token, options={"verify_signature": False})
            sid = claims.get("sid")
            if not isinstance(sid, str) or not UUID_RE.fullmatch(sid):
                raise AuthFailure("FORBIDDEN", 4403, "用户证明无效。")
            login = await self.redis.hgetall(f"auth:login:{sid}")
        except RedisError as exc:
            raise AuthFailure("AUTH_STATE_UNAVAILABLE", 4503, "认证状态暂不可用。", retryable=True) from exc
        except jwt.PyJWTError as exc:
            raise AuthFailure("FORBIDDEN", 4403, "用户证明无效。") from exc
        surface = login.get("surface")
        if surface not in {"WEB", "MINI"}:
            raise AuthFailure("SESSION_REVOKED", 4401, "登录已失效，请重新登录。")
        return await self._authenticate_user(token, surface)

    async def authenticate_anonymous_proof(self, credential: str) -> AuthPrincipal:
        if not CREDENTIAL_RE.fullmatch(credential):
            raise AuthFailure("ANONYMOUS_EXPIRED", 4401, "匿名会话已过期。")
        digest = hashlib.sha256(credential.encode("ascii")).hexdigest()
        try:
            thread_id = await self.redis.get(f"anon:credential:{digest}")
            session = await self.redis.hgetall(f"anon:session:{thread_id}") if thread_id else {}
        except RedisError as exc:
            raise AuthFailure("AUTH_STATE_UNAVAILABLE", 4503, "认证状态暂不可用。", retryable=True) from exc
        surface = session.get("surface")
        if surface not in {"WEB", "MINI"}:
            raise AuthFailure("ANONYMOUS_REVOKED", 4401, "匿名会话已失效。")
        return await self._authenticate_anonymous(credential, surface)

    async def _authenticate_user(self, token: str, surface: Literal["WEB", "MINI"]) -> AuthPrincipal:
        if not self.settings.jwt_key_id:
            raise AuthFailure("AUTH_STATE_UNAVAILABLE", 4503, "登录校验暂不可用。", retryable=True)
        try:
            header = jwt.get_unverified_header(token)
            if header.get("alg") != "RS256" or header.get("typ") != "JWT" or header.get("kid") != self.settings.jwt_key_id:
                raise AuthFailure("FORBIDDEN", 4403, "登录凭据不受信任。")
            claims = jwt.decode(
                token,
                _read_public_key(self.settings.jwt_public_key_file),
                algorithms=["RS256"],
                audience="wudong-ai",
                issuer=self.settings.jwt_issuer,
                leeway=0,
                options={"require": ["sub", "sid", "iat", "nbf", "exp", "session_exp", "purpose", "role"]},
            )
        except AuthFailure:
            raise
        except jwt.ExpiredSignatureError as exc:
            raise AuthFailure("AUTH_EXPIRED", 4401, "登录已过期，请重新登录。") from exc
        except jwt.PyJWTError as exc:
            raise AuthFailure("FORBIDDEN", 4403, "登录凭据不受信任。") from exc
        subject, sid = claims.get("sub"), claims.get("sid")
        if not isinstance(subject, str) or not UUID_RE.fullmatch(subject) or not isinstance(sid, str) or not UUID_RE.fullmatch(sid):
            raise AuthFailure("FORBIDDEN", 4403, "登录凭据不受信任。")
        numeric = [claims.get(name) for name in ("iat", "nbf", "exp", "session_exp")]
        if any(isinstance(value, bool) or not isinstance(value, int) for value in numeric):
            raise AuthFailure("FORBIDDEN", 4403, "登录凭据不受信任。")
        issued_at, not_before, expires_at, session_expires = numeric
        if (
            claims.get("purpose") != "USER"
            or claims.get("role") != "USER"
            or not_before != issued_at
            or expires_at != min(issued_at + 900, session_expires)
            or session_expires > issued_at + 604800
        ):
            raise AuthFailure("FORBIDDEN", 4403, "登录凭据不受信任。")
        try:
            login = await self.redis.hgetall(f"auth:login:{sid}")
            if not login:
                raise AuthFailure("SESSION_REVOKED", 4401, "登录已失效，请重新登录。")
            lane = await self.redis.hgetall(f"auth:lane:{surface}:USER:{login.get('laneId', '')}")
        except RedisError as exc:
            raise AuthFailure("AUTH_STATE_UNAVAILABLE", 4503, "认证状态暂不可用。", retryable=True) from exc
        absolute = login.get("absoluteExpiresAt")
        if (
            login.get("accountId") != subject
            or login.get("surface") != surface
            or login.get("purpose") != "USER"
            or login.get("role") != "USER"
            or lane.get("currentSid") != sid
            or absolute != _utc(session_expires)
        ):
            raise AuthFailure("SESSION_REVOKED", 4401, "登录已失效，请重新登录。")
        return AuthPrincipal(
            owner_kind="USER",
            owner_digest=hashlib.sha256(f"USER:{subject}".encode("ascii")).hexdigest(),
            surface=surface,
            auth_expires_at=_utc(expires_at),
            subject=subject,
            sid=sid,
            thread_id=None,
            access_token=token,
            anonymous_credential=None,
        )

    async def _authenticate_anonymous(
        self,
        credential: str,
        surface: Literal["WEB", "MINI"],
        *,
        expected_lane_id: str | None = None,
    ) -> AuthPrincipal:
        if not CREDENTIAL_RE.fullmatch(credential):
            raise AuthFailure("ANONYMOUS_EXPIRED", 4401, "匿名会话已过期。")
        digest = hashlib.sha256(credential.encode("ascii")).hexdigest()
        try:
            thread_id = await self.redis.get(f"anon:credential:{digest}")
            session = await self.redis.hgetall(f"anon:session:{thread_id}") if thread_id else {}
            lane_id = session.get("laneId")
            lane = await self.redis.hgetall(f"anon:lane:{surface}:{lane_id}") if lane_id else {}
        except RedisError as exc:
            raise AuthFailure("AUTH_STATE_UNAVAILABLE", 4503, "认证状态暂不可用。", retryable=True) from exc
        if (
            not thread_id
            or not UUID_RE.fullmatch(thread_id)
            or session.get("credentialDigest") != digest
            or session.get("surface") != surface
            or session.get("ownerKind") != "ANONYMOUS"
            or lane.get("currentThreadId") != thread_id
            or (expected_lane_id is not None and lane_id != expected_lane_id)
        ):
            raise AuthFailure("ANONYMOUS_REVOKED", 4401, "匿名会话已失效。")
        expires_at = session.get("expiresAt")
        try:
            expires = datetime.strptime(expires_at, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)
        except (TypeError, ValueError) as exc:
            raise AuthFailure("AUTH_STATE_UNAVAILABLE", 4503, "认证状态暂不可用。", retryable=True) from exc
        if expires <= datetime.now(timezone.utc):
            raise AuthFailure("ANONYMOUS_EXPIRED", 4401, "匿名会话已过期。")
        return AuthPrincipal(
            owner_kind="ANONYMOUS",
            owner_digest=hashlib.sha256(f"ANONYMOUS:{thread_id}".encode("ascii")).hexdigest(),
            surface=surface,
            auth_expires_at=expires_at,
            subject=None,
            sid=None,
            thread_id=thread_id,
            access_token=None,
            anonymous_credential=credential,
        )
