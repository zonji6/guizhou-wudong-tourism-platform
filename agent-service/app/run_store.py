from __future__ import annotations

import hashlib
import hmac
import json
import uuid
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any, Literal

from redis.asyncio import Redis
from redis.exceptions import RedisError

try:
    from langgraph.checkpoint.redis.aio import AsyncRedisSaver as MaintainedAsyncRedisSaver
except ImportError:  # The adapter reports this capability explicitly; it never falls back to memory.
    MaintainedAsyncRedisSaver = None

from app.config import get_settings
from app.v3_contracts import (
    ASSISTANT_CONTRACT_VERSION,
    CHECKPOINT_VERSION,
    EVENT_VERSION,
    TOURISM_CONTRACT_VERSION,
    AssistantCardV3,
    AssistantEventV3,
    CandidateRecord,
    CheckpointEnvelopeV3,
    CompletedData,
    CompletedEvent,
    EmptySolutionSlot,
    FailedData,
    FailedEvent,
    PersistentStateV3,
    PublicSolutionSlot,
    RunSnapshot,
    RunStartedData,
    RunStartedEvent,
    SolutionSnapshot,
    StoppedData,
    StoppedEvent,
)


RETENTION_GRACE_SECONDS = 604800


class OfficialAsyncSaverAdapter:
    """Small lifecycle/put adapter around the maintained AsyncRedisSaver API."""

    def __init__(self, redis_url: str) -> None:
        self.redis_url = redis_url
        self._context: Any | None = None
        self._saver: Any | None = None
        self._status = "dependency_not_installed" if MaintainedAsyncRedisSaver is None else "not_initialized"

    @property
    def status(self) -> str:
        return self._status

    @property
    def checkpointer(self) -> Any | None:
        return self._saver

    async def setup(self) -> None:
        if MaintainedAsyncRedisSaver is None:
            return
        try:
            self._context = MaintainedAsyncRedisSaver.from_conn_string(self.redis_url)
            self._saver = await self._context.__aenter__()
            await self._saver.asetup()
            self._status = "available"
        except Exception:
            if self._context is not None:
                try:
                    await self._context.__aexit__(None, None, None)
                except Exception:
                    pass
            self._context = None
            self._saver = None
            self._status = "initialization_failed"

    async def close(self) -> None:
        if self._context is not None:
            await self._context.__aexit__(None, None, None)
        self._context = None
        self._saver = None

    async def put(self, thread_id: str, checkpoint: CheckpointEnvelopeV3) -> None:
        if self._saver is None:
            return
        checkpoint_id = str(
            uuid.uuid5(
                uuid.NAMESPACE_URL,
                f"wudong-a3:{hashlib.sha256(thread_id.encode('ascii')).hexdigest()}:{checkpoint.checkpoint_revision}:{checkpoint.checkpoint_digest}",
            )
        )
        config = {
            "configurable": {
                "thread_id": hashlib.sha256(thread_id.encode("ascii")).hexdigest(),
                "checkpoint_ns": "assistant-v3-envelope",
                "checkpoint_id": checkpoint_id,
            }
        }
        blob = checkpoint.model_dump(mode="json", by_alias=True)
        maintained_checkpoint = {
            "v": 4,
            "id": checkpoint_id,
            "ts": checkpoint.created_at,
            "channel_values": {"checkpointEnvelope": blob},
            "channel_versions": {"checkpointEnvelope": checkpoint.checkpoint_revision},
            "versions_seen": {},
            "updated_channels": ["checkpointEnvelope"],
        }
        try:
            saved = await self._saver.aput(
                config,
                maintained_checkpoint,
                {"source": "update", "step": checkpoint.checkpoint_revision, "parents": {}},
                {"checkpointEnvelope": checkpoint.checkpoint_revision},
            )
            receipt = await self._saver.aget_tuple(saved)
            restored = receipt.checkpoint["channel_values"]["checkpointEnvelope"] if receipt else None
            if not isinstance(restored, dict) or restored.get("checkpointDigest") != checkpoint.checkpoint_digest:
                raise RunStoreError("CHECKPOINT_COMMIT_FAILED")
        except RunStoreError:
            raise
        except Exception as exc:
            raise RunStoreError("CHECKPOINT_COMMIT_FAILED") from exc


def utc_now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).strftime("%Y-%m-%dT%H:%M:%SZ")


def canonical_json(value: object) -> str:
    if hasattr(value, "model_dump"):
        value = value.model_dump(mode="json", by_alias=True)
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def sha256_digest(value: object) -> str:
    return "sha256:" + hashlib.sha256(canonical_json(value).encode("utf-8")).hexdigest()


class RunStoreError(RuntimeError):
    def __init__(self, code: str) -> None:
        super().__init__(code)
        self.code = code


@dataclass(frozen=True, slots=True)
class StartRunResult:
    run_id: str
    fence_epoch: int
    event: AssistantEventV3 | None
    replayed: bool


@dataclass(frozen=True, slots=True)
class CommitResult:
    checkpoint: CheckpointEnvelopeV3
    events: list[AssistantEventV3]


@dataclass(frozen=True, slots=True)
class CancelResult:
    outcome: Literal["OK", "REPLAY", "TERMINAL"]
    event: AssistantEventV3 | None


@dataclass(frozen=True, slots=True)
class SessionSnapshot:
    thread_id: str
    logical_expires_at: str
    checkpoint_revision: int | None
    checkpoint_status: Literal["EMPTY", "AVAILABLE", "LOOKUP_UNAVAILABLE", "INCOMPATIBLE"]
    checkpoint: CheckpointEnvelopeV3 | None
    run: RunSnapshot | None
    can_start_new_run: bool


CREATE_THREAD_LUA = r"""
if redis.call('EXISTS', KEYS[1]) == 1 then
  if redis.call('HGET', KEYS[1], 'ownerKind') ~= ARGV[1] or redis.call('HGET', KEYS[1], 'ownerDigest') ~= ARGV[2] then
    return {'ERR', 'FORBIDDEN'}
  end
  if (redis.call('HGET', KEYS[1], 'logicalExpiresAt') or '') <= ARGV[3] then return {'ERR', 'FORBIDDEN'} end
  return {'EXISTS'}
end
redis.call('HSET', KEYS[1],
  'ownerKind', ARGV[1], 'ownerDigest', ARGV[2], 'logicalExpiresAt', ARGV[4],
  'fenceEpoch', 0, 'currentRunState', '',
  'resultGate', 'CLOSED', 'eventGate', 'CLOSED', 'checkpointGate', 'CLOSED')
redis.call('EXPIREAT', KEYS[1], tonumber(ARGV[5]))
return {'OK'}
"""


REFRESH_RETENTION_LUA = r"""
for i=2,#KEYS do redis.call('SADD', KEYS[1], KEYS[i]) end
local registered = redis.call('SMEMBERS', KEYS[1])
local now = tonumber(redis.call('TIME')[1])
for _,key in ipairs(registered) do
  local ttl = redis.call('TTL', key)
  if ttl == -1 or (ttl >= 0 and now + ttl < tonumber(ARGV[1])) then
    redis.call('EXPIREAT', key, tonumber(ARGV[1]))
  end
end
local index_ttl = redis.call('TTL', KEYS[1])
if index_ttl == -1 or (index_ttl >= 0 and now + index_ttl < tonumber(ARGV[1])) then
  redis.call('EXPIREAT', KEYS[1], tonumber(ARGV[1]))
end
return {'OK'}
"""


START_RUN_LUA = r"""
local existing_owner_kind = redis.call('HGET', KEYS[1], 'ownerKind')
local existing_owner = redis.call('HGET', KEYS[1], 'ownerDigest')
if not existing_owner or existing_owner_kind ~= ARGV[1] or existing_owner ~= ARGV[2] then return {'ERR', 'FORBIDDEN'} end
if (redis.call('HGET', KEYS[1], 'logicalExpiresAt') or '') <= ARGV[3] then return {'ERR', 'FORBIDDEN'} end
local existing_request = redis.call('HGETALL', KEYS[3])
if #existing_request > 0 then
  local digest = redis.call('HGET', KEYS[3], 'digest')
  if digest ~= ARGV[7] then return {'ERR', 'RUN_REQUEST_CONFLICT'} end
  return {'REPLAY', redis.call('HGET', KEYS[3], 'runId'), redis.call('HGET', KEYS[3], 'epoch')}
end
local current_state = redis.call('HGET', KEYS[1], 'currentRunState')
if current_state == 'RUNNING' or current_state == 'CANCEL_REQUESTED' then return {'ERR', 'RUN_ALREADY_ACTIVE'} end
local current_revision = redis.call('HGET', KEYS[1], 'checkpointRevision') or ''
if current_revision ~= ARGV[8] then return {'ERR', 'CHECKPOINT_SEQUENCE_CONFLICT'} end
local rate = redis.call('INCR', KEYS[5])
if rate == 1 then redis.call('EXPIRE', KEYS[5], tonumber(ARGV[14])) end
if rate > tonumber(ARGV[13]) then redis.call('DECR', KEYS[5]); return {'ERR', 'RATE_LIMITED'} end
local epoch = redis.call('HINCRBY', KEYS[1], 'fenceEpoch', 1)
redis.call('HSET', KEYS[1],
  'logicalExpiresAt', ARGV[4], 'currentRunId', ARGV[5], 'currentRunState', 'RUNNING',
  'resultGate', 'OPEN', 'eventGate', 'OPEN', 'checkpointGate', 'OPEN')
redis.call('HSET', KEYS[2],
  'runId', ARGV[5], 'epoch', epoch, 'state', 'RUNNING', 'startedAt', ARGV[9],
  'cancelRequestedAt', '', 'finishedAt', '', 'errorCode', '', 'lastEventSequence', 1)
redis.call('HSET', KEYS[3], 'runId', ARGV[5], 'epoch', epoch, 'digest', ARGV[7])
redis.call('HSET', KEYS[4], 'payload', ARGV[10], 'digest', ARGV[11])
for i=1,4 do redis.call('EXPIREAT', KEYS[i], tonumber(ARGV[12])) end
return {'OK', ARGV[5], tostring(epoch)}
"""


APPEND_EVENT_LUA = r"""
if redis.call('HGET', KEYS[1], 'currentRunId') ~= ARGV[1] then return {'ERR','RUN_FENCE_CLOSED'} end
if redis.call('HGET', KEYS[2], 'epoch') ~= ARGV[2] then return {'ERR','RUN_FENCE_CLOSED'} end
if redis.call('HGET', KEYS[1], 'eventGate') ~= 'OPEN' then return {'ERR','RUN_FENCE_CLOSED'} end
if redis.call('HGET', KEYS[2], 'state') ~= 'RUNNING' then return {'ERR','RUN_FENCE_CLOSED'} end
local expected = tonumber(redis.call('HGET', KEYS[2], 'lastEventSequence') or '0') + 1
if expected ~= tonumber(ARGV[3]) then return {'ERR','EVENT_SEQUENCE_CONFLICT'} end
redis.call('HSET', KEYS[3], 'payload', ARGV[4], 'digest', ARGV[5])
redis.call('HSET', KEYS[2], 'lastEventSequence', ARGV[3])
redis.call('EXPIREAT', KEYS[3], tonumber(ARGV[6]))
return {'OK', ARGV[3]}
"""


REQUEST_CANCEL_LUA = r"""
if redis.call('HGET', KEYS[1], 'ownerKind') ~= ARGV[9] or redis.call('HGET', KEYS[1], 'ownerDigest') ~= ARGV[10] then return {'ERR','FORBIDDEN'} end
local state = redis.call('HGET', KEYS[2], 'state')
if not state then return {'ERR','RUN_NOT_ACTIVE'} end
if redis.call('HGET', KEYS[2], 'epoch') ~= ARGV[2] then return {'ERR','RUN_FENCE_CLOSED'} end
if state ~= 'RUNNING' and state ~= 'CANCEL_REQUESTED' then return {'TERMINAL', state, redis.call('HGET', KEYS[2], 'lastEventSequence')} end
if redis.call('HGET', KEYS[1], 'currentRunId') ~= ARGV[1] then return {'ERR','RUN_NOT_ACTIVE'} end
if state == 'CANCEL_REQUESTED' then return {'REPLAY', redis.call('HGET', KEYS[2], 'lastEventSequence')} end
local expected = tonumber(redis.call('HGET', KEYS[2], 'lastEventSequence') or '0') + 1
if expected ~= tonumber(ARGV[3]) then return {'ERR','EVENT_SEQUENCE_CONFLICT'} end
redis.call('HSET', KEYS[1], 'currentRunState', 'CANCEL_REQUESTED', 'resultGate', 'CLOSED', 'eventGate', 'CLOSED', 'checkpointGate', 'CLOSED')
redis.call('HSET', KEYS[2], 'state', 'CANCEL_REQUESTED', 'cancelRequestedAt', ARGV[4], 'lastEventSequence', ARGV[3])
redis.call('HSET', KEYS[3], 'payload', ARGV[5], 'digest', ARGV[6])
redis.call('HSET', KEYS[4], 'runId', ARGV[1], 'digest', ARGV[7])
redis.call('EXPIREAT', KEYS[3], tonumber(ARGV[8]))
redis.call('EXPIREAT', KEYS[4], tonumber(ARGV[8]))
return {'OK', ARGV[3]}
"""


COMMIT_TERMINAL_LUA = r"""
if redis.call('HGET', KEYS[1], 'currentRunId') ~= ARGV[1] then return {'ERR','RUN_FENCE_CLOSED'} end
if redis.call('HGET', KEYS[2], 'epoch') ~= ARGV[2] then return {'ERR','RUN_FENCE_CLOSED'} end
if redis.call('HGET', KEYS[2], 'state') ~= 'RUNNING' then return {'ERR','RUN_FENCE_CLOSED'} end
if redis.call('HGET', KEYS[1], 'resultGate') ~= 'OPEN' or redis.call('HGET', KEYS[1], 'eventGate') ~= 'OPEN' or redis.call('HGET', KEYS[1], 'checkpointGate') ~= 'OPEN' then return {'ERR','RUN_FENCE_CLOSED'} end
local current_revision = redis.call('HGET', KEYS[1], 'checkpointRevision') or ''
if current_revision ~= ARGV[3] then return {'ERR','CHECKPOINT_SEQUENCE_CONFLICT'} end
if redis.call('EXISTS', KEYS[3]) ~= 1 then return {'ERR','CHECKPOINT_COMMIT_FAILED'} end
if ARGV[11] ~= '' and redis.call('EXISTS', KEYS[4]) ~= 1 then return {'ERR','CANDIDATE_LOOKUP_UNAVAILABLE'} end
local next_sequence = tonumber(redis.call('HGET', KEYS[2], 'lastEventSequence') or '0') + 1
if next_sequence ~= tonumber(ARGV[18]) then return {'ERR','EVENT_SEQUENCE_CONFLICT'} end
redis.call('HSET', KEYS[1],
  'checkpointRevision', ARGV[4], 'checkpointKey', KEYS[3], 'checkpointDigest', ARGV[5],
  'lastCard', ARGV[6], 'lastCardStatus', ARGV[7],
  'currentRunState', ARGV[14], 'resultGate', 'CLOSED', 'eventGate', 'CLOSED', 'checkpointGate', 'CLOSED')
if ARGV[8] == '' then redis.call('HDEL', KEYS[1], 'currentSolution') else redis.call('HSET', KEYS[1], 'currentSolution', ARGV[8]) end
if ARGV[9] == '' then redis.call('HDEL', KEYS[1], 'previousSolution') else redis.call('HSET', KEYS[1], 'previousSolution', ARGV[9]) end
if ARGV[11] ~= '' then
  redis.call('SADD', KEYS[5], ARGV[11])
  local index_ttl = redis.call('TTL', KEYS[5])
  local now = tonumber(redis.call('TIME')[1])
  if index_ttl == -1 or (index_ttl >= 0 and now + index_ttl < tonumber(ARGV[21])) then
    redis.call('EXPIREAT', KEYS[5], tonumber(ARGV[21]))
  end
end
redis.call('HSET', KEYS[2], 'state', ARGV[14], 'finishedAt', ARGV[12], 'errorCode', ARGV[13], 'lastEventSequence', tostring(next_sequence + 1), 'resultCheckpointRevision', ARGV[4])
redis.call('HSET', KEYS[6], 'payload', ARGV[15], 'digest', ARGV[16])
redis.call('HSET', KEYS[7], 'payload', ARGV[17], 'digest', ARGV[19])
redis.call('EXPIREAT', KEYS[6], tonumber(ARGV[20])); redis.call('EXPIREAT', KEYS[7], tonumber(ARGV[20]))
return {'OK', ARGV[4], tostring(next_sequence), tostring(next_sequence + 1)}
"""


FINISH_RUN_LUA = r"""
if redis.call('HGET', KEYS[1], 'currentRunId') ~= ARGV[1] then return {'ERR','RUN_FENCE_CLOSED'} end
if redis.call('HGET', KEYS[2], 'epoch') ~= ARGV[2] then return {'ERR','RUN_FENCE_CLOSED'} end
local state = redis.call('HGET', KEYS[2], 'state')
if state == 'STOPPED' then return {'REPLAY', redis.call('HGET', KEYS[2], 'lastEventSequence')} end
if state ~= 'CANCEL_REQUESTED' then return {'ERR','RUN_NOT_ACTIVE'} end
if redis.call('HGET', KEYS[1], 'resultGate') ~= 'CLOSED' or redis.call('HGET', KEYS[1], 'eventGate') ~= 'CLOSED' or redis.call('HGET', KEYS[1], 'checkpointGate') ~= 'CLOSED' then return {'ERR','RUN_FENCE_CLOSED'} end
local next_sequence = tonumber(redis.call('HGET', KEYS[2], 'lastEventSequence') or '0') + 1
if next_sequence ~= tonumber(ARGV[3]) then return {'ERR','EVENT_SEQUENCE_CONFLICT'} end
redis.call('HSET', KEYS[1], 'currentRunState', 'STOPPED', 'resultGate', 'CLOSED', 'eventGate', 'CLOSED', 'checkpointGate', 'CLOSED')
redis.call('HSET', KEYS[2], 'state', 'STOPPED', 'finishedAt', ARGV[4], 'errorCode', 'CANCELLED_BY_USER', 'lastEventSequence', ARGV[3])
redis.call('HSET', KEYS[3], 'payload', ARGV[5], 'digest', ARGV[6])
redis.call('EXPIREAT', KEYS[3], tonumber(ARGV[7]))
return {'OK', ARGV[3]}
"""


class FencedAsyncRedisSaver:
    """Redis-only checkpoint and run-fence store; there is no memory fallback."""

    def __init__(self) -> None:
        redis_url = get_settings().redis_url
        self.redis: Redis = Redis.from_url(redis_url, decode_responses=True)
        self.official = OfficialAsyncSaverAdapter(redis_url)

    @property
    def official_saver_status(self) -> str:
        return self.official.status

    @property
    def official_checkpointer(self) -> Any | None:
        return self.official.checkpointer

    def official_graph_config(self, thread_id: str, fence_epoch: int) -> dict[str, object]:
        return {
            "configurable": {
                "thread_id": hashlib.sha256(thread_id.encode("ascii")).hexdigest(),
                "checkpoint_ns": f"assistant-v3-run-{fence_epoch}",
            }
        }

    async def setup(self) -> None:
        await self.official.setup()

    async def close(self) -> None:
        await self.official.close()
        await self.redis.aclose()

    async def ping(self) -> bool:
        try:
            return bool(await self.redis.ping())
        except RedisError:
            return False

    @staticmethod
    def _prefix(thread_id: str) -> str:
        thread_key = hashlib.sha256(thread_id.encode("ascii")).hexdigest()
        return f"wd:a3:{{{thread_key}}}"

    @classmethod
    def _keys(cls, thread_id: str) -> dict[str, str]:
        prefix = cls._prefix(thread_id)
        return {
            "meta": f"{prefix}:meta",
            "candidate_index": f"{prefix}:candidate-index",
            "retention_index": f"{prefix}:retention-index",
            "rate": f"{prefix}:rate",
        }

    @classmethod
    def _run_key(cls, thread_id: str, run_id: str) -> str:
        return f"{cls._prefix(thread_id)}:run:{run_id}"

    @classmethod
    def _request_key(cls, thread_id: str, request_id: str) -> str:
        return f"{cls._prefix(thread_id)}:request:{request_id}"

    @classmethod
    def _cancel_key(cls, thread_id: str, request_id: str) -> str:
        return f"{cls._prefix(thread_id)}:cancel:{request_id}"

    @classmethod
    def _event_key(cls, thread_id: str, run_id: str, sequence: int) -> str:
        return f"{cls._prefix(thread_id)}:event:{run_id}:{sequence}"

    @classmethod
    def _checkpoint_key(cls, thread_id: str, revision: int) -> str:
        return f"{cls._prefix(thread_id)}:checkpoint:{revision}"

    @classmethod
    def _candidate_key(cls, thread_id: str, candidate_id: str, version: int) -> str:
        return f"{cls._prefix(thread_id)}:candidate:{candidate_id}:{version}"

    @staticmethod
    def _cleanup_epoch(logical_expires_at: str) -> int:
        expires = datetime.strptime(logical_expires_at, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)
        return int(expires.timestamp()) + RETENTION_GRACE_SECONDS

    @classmethod
    def _ttl(cls, logical_expires_at: str) -> int:
        return max(60, cls._cleanup_epoch(logical_expires_at) - int(datetime.now(timezone.utc).timestamp()))

    async def _refresh_retention(self, thread_id: str, cleanup_epoch: int, *new_keys: str) -> None:
        keys = self._keys(thread_id)
        registered = (keys["retention_index"], keys["meta"], keys["candidate_index"], *new_keys)
        try:
            result = await self.redis.eval(
                REFRESH_RETENTION_LUA,
                len(registered),
                *registered,
                cleanup_epoch,
            )
        except RedisError as exc:
            raise RunStoreError("CHECKPOINT_UNAVAILABLE") from exc
        if result[0] != "OK":
            raise RunStoreError("CHECKPOINT_UNAVAILABLE")

    async def create_thread(
        self,
        *,
        thread_id: str,
        owner_kind: str,
        owner_digest: str,
        logical_expires_at: str,
    ) -> bool:
        keys = self._keys(thread_id)
        cleanup_epoch = self._cleanup_epoch(logical_expires_at)
        try:
            result = await self.redis.eval(
                CREATE_THREAD_LUA,
                1,
                keys["meta"],
                owner_kind,
                owner_digest,
                utc_now(),
                logical_expires_at,
                cleanup_epoch,
            )
        except RedisError as exc:
            raise RunStoreError("CHECKPOINT_UNAVAILABLE") from exc
        if result[0] == "ERR":
            raise RunStoreError(result[1])
        if result[0] == "EXISTS":
            cleanup_epoch = await self._thread_cleanup_epoch(thread_id)
        await self._refresh_retention(thread_id, cleanup_epoch)
        return result[0] == "OK"

    async def require_thread_access(self, thread_id: str, owner_kind: str, owner_digest: str) -> str:
        try:
            values = await self.redis.hmget(
                self._keys(thread_id)["meta"],
                "ownerKind",
                "ownerDigest",
                "logicalExpiresAt",
            )
        except RedisError as exc:
            raise RunStoreError("CHECKPOINT_UNAVAILABLE") from exc
        stored_kind, stored_digest, logical_expires_at = values
        if stored_kind != owner_kind or stored_digest != owner_digest or not logical_expires_at:
            raise RunStoreError("FORBIDDEN")
        try:
            expires = datetime.strptime(logical_expires_at, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)
        except ValueError as exc:
            raise RunStoreError("CHECKPOINT_INCOMPATIBLE") from exc
        if expires <= datetime.now(timezone.utc):
            raise RunStoreError("FORBIDDEN")
        return logical_expires_at

    async def _thread_cleanup_epoch(self, thread_id: str) -> int:
        try:
            logical_expires_at = await self.redis.hget(self._keys(thread_id)["meta"], "logicalExpiresAt")
        except RedisError as exc:
            raise RunStoreError("CHECKPOINT_UNAVAILABLE") from exc
        if not logical_expires_at:
            raise RunStoreError("CHECKPOINT_UNAVAILABLE")
        try:
            return self._cleanup_epoch(logical_expires_at)
        except ValueError as exc:
            raise RunStoreError("CHECKPOINT_INCOMPATIBLE") from exc

    async def start_run(
        self,
        *,
        thread_id: str,
        run_id: str,
        client_request_id: str,
        request_digest: str,
        owner_kind: str,
        owner_digest: str,
        logical_expires_at: str,
        expected_checkpoint_revision: int | None,
        agent_type: str,
        retrieval_mode: str,
    ) -> StartRunResult:
        keys = self._keys(thread_id)
        run_key = self._run_key(thread_id, run_id)
        request_key = self._request_key(thread_id, client_request_id)
        event_key = self._event_key(thread_id, run_id, 1)
        started_at = utc_now()
        event = AssistantEventV3(
            root=RunStartedEvent(
                assistant_contract_version=ASSISTANT_CONTRACT_VERSION,
                tourism_contract_version=TOURISM_CONTRACT_VERSION,
                event_version=EVENT_VERSION,
                thread_id=thread_id,
                run_id=run_id,
                event_sequence=1,
                run_state="RUNNING",
                emitted_at=started_at,
                type="run_started",
                data=RunStartedData(
                    agent_type=agent_type,
                    retrieval_mode=retrieval_mode,
                    started_at=started_at,
                    checkpoint_revision=expected_checkpoint_revision,
                ),
            )
        )
        cleanup_epoch = self._cleanup_epoch(logical_expires_at)
        try:
            result = await self.redis.eval(
                START_RUN_LUA,
                5,
                keys["meta"],
                run_key,
                request_key,
                event_key,
                keys["rate"],
                owner_kind,
                owner_digest,
                utc_now(),
                logical_expires_at,
                run_id,
                client_request_id,
                request_digest,
                str(expected_checkpoint_revision or ""),
                started_at,
                canonical_json(event),
                sha256_digest(event),
                cleanup_epoch,
                get_settings().ai_rate_requests,
                get_settings().ai_rate_window_seconds,
            )
        except RedisError as exc:
            raise RunStoreError("CHECKPOINT_UNAVAILABLE") from exc
        if result[0] == "ERR":
            raise RunStoreError(result[1])
        if result[0] == "REPLAY":
            return StartRunResult(run_id=result[1], fence_epoch=int(result[2]), event=None, replayed=True)
        try:
            checkpoint_key = await self.redis.hget(keys["meta"], "checkpointKey")
        except RedisError as exc:
            raise RunStoreError("CHECKPOINT_UNAVAILABLE") from exc
        await self._refresh_retention(
            thread_id,
            cleanup_epoch,
            run_key,
            request_key,
            event_key,
            *([checkpoint_key] if checkpoint_key else []),
        )
        return StartRunResult(run_id=run_id, fence_epoch=int(result[2]), event=event, replayed=False)

    async def _next_sequence(self, thread_id: str, run_id: str) -> int:
        value = await self.redis.hget(self._run_key(thread_id, run_id), "lastEventSequence")
        if value is None:
            raise RunStoreError("RUN_NOT_FOUND")
        return int(value) + 1

    async def append_event(self, thread_id: str, run_id: str, epoch: int, event: AssistantEventV3) -> None:
        sequence = event.root.event_sequence
        cleanup_epoch = await self._thread_cleanup_epoch(thread_id)
        event_key = self._event_key(thread_id, run_id, sequence)
        try:
            result = await self.redis.eval(
                APPEND_EVENT_LUA,
                3,
                self._keys(thread_id)["meta"],
                self._run_key(thread_id, run_id),
                event_key,
                run_id,
                epoch,
                sequence,
                canonical_json(event),
                sha256_digest(event),
                cleanup_epoch,
            )
        except RedisError as exc:
            raise RunStoreError("CHECKPOINT_UNAVAILABLE") from exc
        if result[0] != "OK":
            raise RunStoreError(result[1])
        await self._refresh_retention(thread_id, cleanup_epoch, event_key)

    async def next_event_sequence(self, thread_id: str, run_id: str) -> int:
        try:
            return await self._next_sequence(thread_id, run_id)
        except RedisError as exc:
            raise RunStoreError("RUN_STATUS_UNAVAILABLE") from exc

    async def request_cancel(
        self,
        *,
        thread_id: str,
        run_id: str,
        epoch: int,
        cancel_request_id: str,
        event: AssistantEventV3,
        owner_kind: str,
        owner_digest: str,
    ) -> CancelResult:
        sequence = event.root.event_sequence
        cleanup_epoch = await self._thread_cleanup_epoch(thread_id)
        event_key = self._event_key(thread_id, run_id, sequence)
        cancel_key = self._cancel_key(thread_id, cancel_request_id)
        try:
            result = await self.redis.eval(
                REQUEST_CANCEL_LUA,
                4,
                self._keys(thread_id)["meta"],
                self._run_key(thread_id, run_id),
                event_key,
                cancel_key,
                run_id,
                epoch,
                sequence,
                event.root.emitted_at,
                canonical_json(event),
                sha256_digest(event),
                sha256_digest({"cancelRequestId": cancel_request_id, "runId": run_id}),
                cleanup_epoch,
                owner_kind,
                owner_digest,
            )
        except RedisError as exc:
            raise RunStoreError("RUN_CANCEL_UNAVAILABLE") from exc
        if result[0] == "ERR":
            raise RunStoreError(result[1])
        if result[0] == "OK":
            await self._refresh_retention(thread_id, cleanup_epoch, event_key, cancel_key)
            return CancelResult(outcome="OK", event=event)
        persisted_event: AssistantEventV3 | None = None
        sequence_raw = result[1] if result[0] == "REPLAY" else result[2]
        try:
            raw_event = await self.redis.hget(self._event_key(thread_id, run_id, int(sequence_raw)), "payload")
        except (RedisError, TypeError, ValueError) as exc:
            raise RunStoreError("RUN_STATUS_UNAVAILABLE") from exc
        if raw_event:
            try:
                persisted_event = AssistantEventV3.model_validate_json(raw_event, strict=True)
            except ValueError as exc:
                raise RunStoreError("RUN_STATUS_UNAVAILABLE") from exc
        return CancelResult(outcome=result[0], event=persisted_event)

    async def finish_stopped(
        self,
        *,
        thread_id: str,
        run_id: str,
        epoch: int,
        owner_kind: str,
        owner_digest: str,
    ) -> AssistantEventV3 | None:
        await self.require_thread_access(thread_id, owner_kind, owner_digest)
        sequence = await self._next_sequence(thread_id, run_id)
        try:
            checkpoint_raw = await self.redis.hget(self._keys(thread_id)["meta"], "checkpointRevision")
        except RedisError as exc:
            raise RunStoreError("RUN_CANCEL_UNAVAILABLE") from exc
        checkpoint_revision = int(checkpoint_raw) if checkpoint_raw else None
        finished_at = utc_now()
        unavailable_current = EmptySolutionSlot(
            slot="CURRENT",
            status="LOOKUP_UNAVAILABLE",
            solution=None,
            code="RESTORE_REVALIDATION_REQUIRED",
        )
        unavailable_previous = EmptySolutionSlot(
            slot="PREVIOUS",
            status="LOOKUP_UNAVAILABLE",
            solution=None,
            code="RESTORE_REVALIDATION_REQUIRED",
        )
        event = AssistantEventV3(
            root=StoppedEvent(
                assistant_contract_version=ASSISTANT_CONTRACT_VERSION,
                tourism_contract_version=TOURISM_CONTRACT_VERSION,
                event_version=EVENT_VERSION,
                thread_id=thread_id,
                run_id=run_id,
                event_sequence=sequence,
                run_state="STOPPED",
                emitted_at=finished_at,
                type="stopped",
                data=StoppedData(
                    finished_at=finished_at,
                    error_code="CANCELLED_BY_USER",
                    checkpoint_revision=checkpoint_revision,
                    current_solution=unavailable_current,
                    previous_solution=unavailable_previous,
                ),
            )
        )
        cleanup_epoch = await self._thread_cleanup_epoch(thread_id)
        event_key = self._event_key(thread_id, run_id, sequence)
        try:
            result = await self.redis.eval(
                FINISH_RUN_LUA,
                3,
                self._keys(thread_id)["meta"],
                self._run_key(thread_id, run_id),
                event_key,
                run_id,
                epoch,
                sequence,
                finished_at,
                canonical_json(event),
                sha256_digest(event),
                cleanup_epoch,
            )
        except RedisError as exc:
            raise RunStoreError("RUN_CANCEL_UNAVAILABLE") from exc
        if result[0] == "ERR":
            raise RunStoreError(result[1])
        if result[0] == "REPLAY":
            return None
        await self._refresh_retention(thread_id, cleanup_epoch, event_key)
        return event

    async def load_checkpoint(
        self,
        thread_id: str,
        owner_kind: str,
        owner_digest: str,
    ) -> CheckpointEnvelopeV3 | None:
        await self.require_thread_access(thread_id, owner_kind, owner_digest)
        meta_key = self._keys(thread_id)["meta"]
        try:
            pointer = await self.redis.hmget(
                meta_key,
                "checkpointKey",
                "checkpointRevision",
                "checkpointDigest",
            )
            checkpoint_key, revision_raw, meta_digest = pointer
            if not checkpoint_key and not revision_raw and not meta_digest:
                return None
            if not checkpoint_key or not revision_raw or not meta_digest:
                raise RunStoreError("CHECKPOINT_INCOMPATIBLE")
            try:
                revision = int(revision_raw)
            except ValueError as exc:
                raise RunStoreError("CHECKPOINT_INCOMPATIBLE") from exc
            if checkpoint_key != self._checkpoint_key(thread_id, revision):
                raise RunStoreError("CHECKPOINT_INCOMPATIBLE")
            raw = await self.redis.get(checkpoint_key)
            confirmed_pointer = await self.redis.hmget(
                meta_key,
                "checkpointKey",
                "checkpointRevision",
                "checkpointDigest",
            )
        except RedisError as exc:
            raise RunStoreError("CHECKPOINT_UNAVAILABLE") from exc
        if confirmed_pointer != pointer:
            raise RunStoreError("CHECKPOINT_UNAVAILABLE")
        if not raw:
            raise RunStoreError("CHECKPOINT_UNAVAILABLE")
        try:
            checkpoint = CheckpointEnvelopeV3.model_validate_json(raw, strict=True)
        except ValueError as exc:
            raise RunStoreError("CHECKPOINT_INCOMPATIBLE") from exc
        material = checkpoint.model_dump(mode="json", by_alias=True)
        stored_digest = material.pop("checkpointDigest")
        expected_parent = checkpoint.checkpoint_revision - 1 or None
        if (
            checkpoint.checkpoint_revision != revision
            or checkpoint.parent_checkpoint_revision != expected_parent
            or not hmac.compare_digest(checkpoint.checkpoint_digest, meta_digest)
            or not hmac.compare_digest(checkpoint.checkpoint_digest, stored_digest)
            or not hmac.compare_digest(checkpoint.checkpoint_digest, sha256_digest(material))
        ):
            raise RunStoreError("CHECKPOINT_INCOMPATIBLE")
        try:
            committed_run = await self.redis.hmget(
                self._run_key(thread_id, checkpoint.committed_by_run_id),
                "epoch",
                "resultCheckpointRevision",
            )
        except RedisError as exc:
            raise RunStoreError("CHECKPOINT_UNAVAILABLE") from exc
        if committed_run != [str(checkpoint.committed_by_fence_epoch), str(revision)]:
            raise RunStoreError("CHECKPOINT_INCOMPATIBLE")
        return checkpoint

    async def prepare_candidate(self, candidate: CandidateRecord) -> tuple[str, str]:
        ref = candidate.candidate_ref
        key = self._candidate_key(ref.thread_id, ref.candidate_id, ref.candidate_version)
        raw = canonical_json(candidate)
        cleanup_epoch = self._cleanup_epoch(candidate.expires_at)
        try:
            created = await self.redis.set(key, raw, nx=True, exat=cleanup_epoch)
            if not created:
                current = await self.redis.get(key)
                if current != raw:
                    raise RunStoreError("CANDIDATE_PAYLOAD_INVALID")
        except RedisError as exc:
            raise RunStoreError("CANDIDATE_LOOKUP_UNAVAILABLE") from exc
        return key, f"{ref.candidate_id}:{ref.candidate_version}"

    async def commit_card(
        self,
        *,
        thread_id: str,
        run_id: str,
        epoch: int,
        state: PersistentStateV3,
        card: AssistantCardV3,
        candidate: CandidateRecord | None,
        terminal_state: Literal["COMPLETED", "FAILED"],
        error_code: str | None,
    ) -> CommitResult:
        meta_key = self._keys(thread_id)["meta"]
        try:
            parent_raw = await self.redis.hget(meta_key, "checkpointRevision")
        except RedisError as exc:
            raise RunStoreError("CHECKPOINT_UNAVAILABLE") from exc
        parent = int(parent_raw) if parent_raw else None
        revision = (parent or 0) + 1
        checkpoint_material = {
            "assistantContractVersion": ASSISTANT_CONTRACT_VERSION,
            "tourismContractVersion": TOURISM_CONTRACT_VERSION,
            "checkpointVersion": CHECKPOINT_VERSION,
            "checkpointRevision": revision,
            "parentCheckpointRevision": parent,
            "committedByRunId": run_id,
            "committedByFenceEpoch": epoch,
            "createdAt": utc_now(),
            "state": state.model_dump(mode="json", by_alias=True),
        }
        checkpoint = CheckpointEnvelopeV3.model_validate(
            {**checkpoint_material, "checkpointDigest": sha256_digest(checkpoint_material)},
            strict=True,
        )
        checkpoint_key = self._checkpoint_key(thread_id, revision)
        checkpoint_raw = canonical_json(checkpoint)
        cleanup_epoch = await self._thread_cleanup_epoch(thread_id)
        try:
            created = await self.redis.set(checkpoint_key, checkpoint_raw, nx=True, exat=cleanup_epoch)
            if not created and await self.redis.get(checkpoint_key) != checkpoint_raw:
                raise RunStoreError("CHECKPOINT_SEQUENCE_CONFLICT")
        except RedisError as exc:
            raise RunStoreError("CHECKPOINT_COMMIT_FAILED") from exc

        candidate_key = self._keys(thread_id)["candidate_index"]
        candidate_member = ""
        if candidate is not None:
            candidate_key, candidate_member = await self.prepare_candidate(candidate)

        await self.official.put(thread_id, checkpoint)

        current = state.current_solution
        previous = state.previous_solution
        current_slot = self._solution_slot("CURRENT", current)
        previous_slot = self._solution_slot("PREVIOUS", previous)
        sequence = await self._next_sequence(thread_id, run_id)
        finished_at = utc_now()
        card_event = AssistantEventV3.model_validate(
            {
                "assistantContractVersion": ASSISTANT_CONTRACT_VERSION,
                "tourismContractVersion": TOURISM_CONTRACT_VERSION,
                "eventVersion": EVENT_VERSION,
                "threadId": thread_id,
                "runId": run_id,
                "eventSequence": sequence,
                "runState": terminal_state,
                "emittedAt": finished_at,
                "type": "card_ready",
                "data": {"card": card.model_dump(mode="json", by_alias=True), "checkpointRevision": revision},
            },
            strict=True,
        )
        if terminal_state == "COMPLETED":
            terminal = AssistantEventV3(
                root=CompletedEvent(
                    assistant_contract_version=ASSISTANT_CONTRACT_VERSION,
                    tourism_contract_version=TOURISM_CONTRACT_VERSION,
                    event_version=EVENT_VERSION,
                    thread_id=thread_id,
                    run_id=run_id,
                    event_sequence=sequence + 1,
                    run_state="COMPLETED",
                    emitted_at=finished_at,
                    type="completed",
                    data=CompletedData(
                        finished_at=finished_at,
                        checkpoint_revision=revision,
                        current_solution=current_slot,
                        previous_solution=previous_slot,
                    ),
                )
            )
        else:
            terminal = AssistantEventV3(
                root=FailedEvent(
                    assistant_contract_version=ASSISTANT_CONTRACT_VERSION,
                    tourism_contract_version=TOURISM_CONTRACT_VERSION,
                    event_version=EVENT_VERSION,
                    thread_id=thread_id,
                    run_id=run_id,
                    event_sequence=sequence + 1,
                    run_state="FAILED",
                    emitted_at=finished_at,
                    type="failed",
                    data=FailedData(
                        finished_at=finished_at,
                        error_code=error_code or "FINAL_VALIDATION_FAILED",
                        retryable=True,
                        checkpoint_revision=revision,
                        current_solution=current_slot,
                        previous_solution=previous_slot,
                    ),
                )
            )
        try:
            result = await self.redis.eval(
                COMMIT_TERMINAL_LUA,
                7,
                meta_key,
                self._run_key(thread_id, run_id),
                checkpoint_key,
                candidate_key,
                self._keys(thread_id)["candidate_index"],
                self._event_key(thread_id, run_id, sequence),
                self._event_key(thread_id, run_id, sequence + 1),
                run_id,
                epoch,
                str(parent or ""),
                revision,
                checkpoint.checkpoint_digest,
                canonical_json(card),
                "AVAILABLE",
                canonical_json(current) if current else "",
                canonical_json(previous) if previous else "",
                candidate_key,
                candidate_member,
                finished_at,
                error_code or "",
                terminal_state,
                canonical_json(card_event),
                sha256_digest(card_event),
                canonical_json(terminal),
                sequence,
                sha256_digest(terminal),
                cleanup_epoch,
                self._cleanup_epoch(candidate.expires_at) if candidate is not None else cleanup_epoch,
            )
        except RedisError as exc:
            raise RunStoreError("CHECKPOINT_COMMIT_FAILED") from exc
        if result[0] != "OK":
            raise RunStoreError(result[1])
        await self._refresh_retention(
            thread_id,
            cleanup_epoch,
            checkpoint_key,
            self._event_key(thread_id, run_id, sequence),
            self._event_key(thread_id, run_id, sequence + 1),
        )
        return CommitResult(checkpoint=checkpoint, events=[card_event, terminal])

    @staticmethod
    def _solution_slot(slot: Literal["CURRENT", "PREVIOUS"], solution: SolutionSnapshot | None) -> PublicSolutionSlot:
        if solution is None:
            return EmptySolutionSlot(slot=slot, status="EMPTY", solution=None, code=None)
        return {
            "slot": slot,
            "status": "AVAILABLE",
            "solution": solution.card,
            "code": None,
        }

    async def get_candidate(self, thread_id: str, candidate_id: str, version: int) -> CandidateRecord | None:
        member = f"{candidate_id}:{version}"
        try:
            visible = await self.redis.sismember(self._keys(thread_id)["candidate_index"], member)
            if not visible:
                return None
            raw = await self.redis.get(self._candidate_key(thread_id, candidate_id, version))
        except RedisError as exc:
            raise RunStoreError("CANDIDATE_LOOKUP_UNAVAILABLE") from exc
        if not raw:
            return None
        try:
            return CandidateRecord.model_validate_json(raw, strict=True)
        except ValueError as exc:
            raise RunStoreError("CANDIDATE_PAYLOAD_INVALID") from exc

    async def get_thread_owner(self, thread_id: str) -> tuple[str, str]:
        try:
            values = await self.redis.hmget(
                self._keys(thread_id)["meta"],
                "ownerKind",
                "ownerDigest",
                "logicalExpiresAt",
            )
        except RedisError as exc:
            raise RunStoreError("CANDIDATE_LOOKUP_UNAVAILABLE") from exc
        owner_kind, owner_digest, logical_expires_at = values
        if not owner_kind or not owner_digest or not logical_expires_at:
            raise RunStoreError("CANDIDATE_VERSION_UNAVAILABLE")
        try:
            expires = datetime.strptime(logical_expires_at, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)
        except ValueError as exc:
            raise RunStoreError("CANDIDATE_PAYLOAD_INVALID") from exc
        if expires <= datetime.now(timezone.utc):
            raise RunStoreError("CANDIDATE_EXPIRED")
        return owner_kind, owner_digest

    async def get_run_epoch(self, thread_id: str, run_id: str) -> int:
        try:
            value = await self.redis.hget(self._run_key(thread_id, run_id), "epoch")
        except RedisError as exc:
            raise RunStoreError("RUN_STATUS_UNAVAILABLE") from exc
        if value is None:
            raise RunStoreError("RUN_NOT_FOUND")
        return int(value)

    async def session_snapshot(
        self,
        thread_id: str,
        owner_kind: str,
        owner_digest: str,
    ) -> SessionSnapshot:
        logical_expires_at = await self.require_thread_access(thread_id, owner_kind, owner_digest)
        keys = self._keys(thread_id)
        try:
            meta = await self.redis.hgetall(keys["meta"])
        except RedisError as exc:
            raise RunStoreError("CHECKPOINT_UNAVAILABLE") from exc
        run: RunSnapshot | None = None
        run_id = meta.get("currentRunId")
        if run_id:
            try:
                raw_run = await self.redis.hgetall(self._run_key(thread_id, run_id))
            except RedisError as exc:
                raise RunStoreError("RUN_STATUS_UNAVAILABLE") from exc
            if raw_run:
                try:
                    run = RunSnapshot(
                        run_id=run_id,
                        run_state=raw_run["state"],
                        last_event_sequence=int(raw_run.get("lastEventSequence", "0")),
                        started_at=raw_run["startedAt"],
                        cancel_requested_at=raw_run.get("cancelRequestedAt") or None,
                        finished_at=raw_run.get("finishedAt") or None,
                        error_code=raw_run.get("errorCode") or None,
                    )
                except (KeyError, ValueError) as exc:
                    raise RunStoreError("CHECKPOINT_INCOMPATIBLE") from exc
        stored_run_state = meta.get("currentRunState") or None
        run_state_consistent = (run is None and stored_run_state in {None, ""}) or (
            run is not None and run.run_state == stored_run_state
        )
        if not run_state_consistent:
            raise RunStoreError("CHECKPOINT_INCOMPATIBLE")

        checkpoint: CheckpointEnvelopeV3 | None = None
        checkpoint_status: Literal["EMPTY", "AVAILABLE", "LOOKUP_UNAVAILABLE", "INCOMPATIBLE"] = "EMPTY"
        try:
            checkpoint = await self.load_checkpoint(thread_id, owner_kind, owner_digest)
            if checkpoint is not None:
                checkpoint_status = "AVAILABLE"
        except RunStoreError as exc:
            if exc.code == "CHECKPOINT_INCOMPATIBLE":
                checkpoint_status = "INCOMPATIBLE"
            elif exc.code == "CHECKPOINT_UNAVAILABLE":
                checkpoint_status = "LOOKUP_UNAVAILABLE"
            else:
                raise
        can_start = stored_run_state not in {"RUNNING", "CANCEL_REQUESTED"}
        return SessionSnapshot(
            thread_id=thread_id,
            logical_expires_at=logical_expires_at,
            checkpoint_revision=checkpoint.checkpoint_revision if checkpoint is not None else None,
            checkpoint_status=checkpoint_status,
            checkpoint=checkpoint,
            run=run,
            can_start_new_run=can_start,
        )
