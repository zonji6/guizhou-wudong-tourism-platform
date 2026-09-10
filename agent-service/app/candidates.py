from __future__ import annotations

import hmac
from datetime import datetime, timezone
from pathlib import Path

from app.auth import AuthFailure, AuthService
from app.rag.retriever import KnowledgeUnavailable, RetrievalBundle, SharedKnowledgeRetriever
from app.run_store import FencedAsyncRedisSaver, RunStoreError, sha256_digest, utc_now
from app.v3_contracts import CandidateRecord, CandidateResolveRequest, CandidateResolveResponse


def verify_service_credential(path: Path | None, supplied: str | None) -> None:
    if path is None or not path.is_absolute() or not path.is_file():
        raise RunStoreError("INTERNAL_AUTH_UNAVAILABLE")
    expected = path.read_text(encoding="utf-8").strip()
    if not expected or supplied is None or not hmac.compare_digest(expected, supplied):
        raise RunStoreError("INTERNAL_FORBIDDEN")


def candidate_digest_material(candidate: CandidateRecord) -> dict[str, object]:
    return {
        "contractVersion": "tourism-api-v3-draft-r3",
        "candidateRef": candidate.candidate_ref.model_dump(mode="json", by_alias=True),
        "candidateType": candidate.candidate_type,
        "adoptionAction": candidate.adoption_action,
        "baseResource": candidate.base_resource.model_dump(mode="json", by_alias=True) if candidate.base_resource else None,
        "sourceOwnerKind": candidate.source_owner_kind,
        "createdAt": candidate.created_at,
        "expiresAt": candidate.expires_at,
        "knowledgeContext": candidate.knowledge_context.model_dump(mode="json", by_alias=True) if candidate.knowledge_context else None,
        "knowledgeDependencies": [
            item.model_dump(mode="json", by_alias=True) for item in candidate.knowledge_dependencies
        ],
        "payload": candidate.payload.model_dump(mode="json", by_alias=True),
    }


async def resolve_candidate(
    request: CandidateResolveRequest,
    *,
    user_token: str,
    anonymous_credential: str | None,
    auth: AuthService,
    store: FencedAsyncRedisSaver,
) -> CandidateResolveResponse:
    user = await auth.authenticate_user_proof(user_token)
    owner_kind, owner_digest = await store.get_thread_owner(request.candidate_ref.thread_id)
    if owner_kind == "USER":
        if user.owner_digest != owner_digest:
            raise RunStoreError("CANDIDATE_VERSION_UNAVAILABLE")
    elif owner_kind == "ANONYMOUS":
        if not anonymous_credential:
            raise RunStoreError("CANDIDATE_VERSION_UNAVAILABLE")
        anonymous = await auth.authenticate_anonymous_proof(anonymous_credential)
        if anonymous.thread_id != request.candidate_ref.thread_id or anonymous.owner_digest != owner_digest:
            raise RunStoreError("CANDIDATE_VERSION_UNAVAILABLE")
    else:
        raise RunStoreError("CANDIDATE_LOOKUP_UNAVAILABLE")
    candidate = await store.get_candidate(
        request.candidate_ref.thread_id,
        request.candidate_ref.candidate_id,
        request.candidate_ref.candidate_version,
    )
    if candidate is None:
        raise RunStoreError("CANDIDATE_VERSION_UNAVAILABLE")
    if candidate.source_owner_kind != owner_kind:
        raise RunStoreError("CANDIDATE_PAYLOAD_INVALID")
    expires_at = datetime.strptime(candidate.expires_at, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)
    if datetime.now(timezone.utc) > expires_at:
        raise RunStoreError("CANDIDATE_EXPIRED")
    if candidate.candidate_ref != request.candidate_ref:
        raise RunStoreError("CANDIDATE_VERSION_UNAVAILABLE")
    if candidate.candidate_type != request.expected_candidate_type or candidate.adoption_action != request.expected_action:
        raise RunStoreError("CANDIDATE_PAYLOAD_INVALID")
    if candidate.base_resource != request.expected_base:
        raise RunStoreError("CANDIDATE_BASE_MISMATCH")
    if not hmac.compare_digest(candidate.candidate_digest, sha256_digest(candidate_digest_material(candidate))):
        raise RunStoreError("CANDIDATE_PAYLOAD_INVALID")
    if candidate.knowledge_dependencies:
        try:
            await SharedKnowledgeRetriever().verify_for_delivery(
                RetrievalBundle(
                    mode=candidate.knowledge_context.retrieval_mode,
                    context=candidate.knowledge_context,
                    dependencies=candidate.knowledge_dependencies,
                    evidence=[],
                )
            )
        except KnowledgeUnavailable as exc:
            raise RunStoreError("CANDIDATE_LOOKUP_UNAVAILABLE") from exc
    return CandidateResolveResponse.model_validate(
        {**candidate.model_dump(mode="json", by_alias=True), "resolvedAt": utc_now()},
        strict=True,
    )
