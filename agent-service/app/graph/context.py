from __future__ import annotations

from dataclasses import dataclass

from app.v3_contracts import GenerateRequest, OwnerKind, UtcTimestamp


@dataclass(frozen=True, slots=True)
class RunContext:
    """One-run transient data; this object is never serialized as a checkpoint."""

    request: GenerateRequest
    run_id: str
    fence_epoch: int
    owner_kind: OwnerKind
    logical_expires_at: UtcTimestamp
