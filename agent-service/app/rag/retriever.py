from __future__ import annotations

from dataclasses import dataclass

from app.config import get_settings
from app.tools.tourism_v3_client import TourismV3Client, TourismV3Error
from app.tools.tourism_v3_contracts import KnowledgeEvidence
from app.v3_contracts import KnowledgeContext, KnowledgeDependency, KnowledgeReference, RetrievalMode


class KnowledgeUnavailable(RuntimeError):
    def __init__(self, code: str) -> None:
        super().__init__(code)
        self.code = code


@dataclass(frozen=True, slots=True)
class RetrievalBundle:
    mode: RetrievalMode
    context: KnowledgeContext | None
    dependencies: list[KnowledgeDependency]
    evidence: list[KnowledgeEvidence]

    @property
    def demo_data(self) -> bool:
        return any(item.demo_data for item in self.evidence)

    @property
    def references(self) -> list[KnowledgeReference]:
        seen: set[tuple[str, str]] = set()
        result: list[KnowledgeReference] = []
        for item in self.evidence:
            for reference in item.references:
                key = (reference.source_title, reference.detail_path)
                if key not in seen:
                    seen.add(key)
                    result.append(reference)
        return result


class SharedKnowledgeRetriever:
    """All three agents use one explicit retrieval route and one final verifier."""

    def __init__(self, client: TourismV3Client | None = None) -> None:
        self.client = client or TourismV3Client()
        self.settings = get_settings()

    async def retrieve(self, query: str, mode: RetrievalMode) -> RetrievalBundle:
        if mode == "NONE":
            return RetrievalBundle(mode="NONE", context=None, dependencies=[], evidence=[])
        if mode == "VECTOR":
            if not self.settings.vector_configured:
                raise KnowledgeUnavailable("VECTOR_CONFIGURATION_UNAVAILABLE")
            raise KnowledgeUnavailable("VECTOR_BUILD_NOT_READY")
        try:
            result = await self.client.search_keyword_knowledge(
                query.strip() or "乌东",
                self.settings.knowledge_search_max_results,
            )
        except TourismV3Error as exc:
            raise KnowledgeUnavailable(exc.code) from exc
        if any(item.rank != index for index, item in enumerate(result.results, 1)):
            raise KnowledgeUnavailable("CONTRACT_INCOMPATIBLE")
        evidence = [item.evidence for item in result.results]
        for item, source in zip(result.results, evidence, strict=True):
            if item.document_id != source.document_id or item.build_id != source.build_id:
                raise KnowledgeUnavailable("CONTRACT_INCOMPATIBLE")
        ordered = sorted(
            {
                (item.document_id, item.build_id): KnowledgeDependency(
                    document_id=item.document_id,
                    build_id=item.build_id,
                    snapshot_hash=item.snapshot_hash,
                )
                for item in evidence
            }.values(),
            key=lambda item: (item.document_id, item.build_id),
        )
        return RetrievalBundle(
            mode="KEYWORD_DEMO",
            context=KnowledgeContext(retrieval_mode="KEYWORD_DEMO", config_hash=result.config_hash),
            dependencies=ordered,
            evidence=evidence,
        )

    async def verify_for_delivery(self, bundle: RetrievalBundle) -> RetrievalBundle:
        if not bundle.dependencies:
            return bundle
        if bundle.context is None:
            raise KnowledgeUnavailable("FINAL_VALIDATION_FAILED")
        try:
            checked = await self.client.verify_knowledge(bundle.context, bundle.dependencies)
        except TourismV3Error as exc:
            code = (
                exc.code
                if exc.code in {"KNOWLEDGE_UPDATED", "KNOWLEDGE_ELIGIBILITY_UNAVAILABLE"}
                else "KNOWLEDGE_ELIGIBILITY_UNAVAILABLE"
            )
            raise KnowledgeUnavailable(code) from exc
        if checked.retrieval_mode != bundle.context.retrieval_mode or checked.config_hash != bundle.context.config_hash:
            raise KnowledgeUnavailable("KNOWLEDGE_UPDATED")
        if len(checked.results) != len(bundle.dependencies):
            raise KnowledgeUnavailable("KNOWLEDGE_UPDATED")
        refreshed: list[KnowledgeEvidence] = []
        for expected, actual in zip(bundle.dependencies, checked.results, strict=True):
            if (
                actual.document_id != expected.document_id
                or actual.build_id != expected.build_id
                or not actual.eligible
                or actual.evidence is None
                or actual.evidence.document_id != expected.document_id
                or actual.evidence.build_id != expected.build_id
                or actual.evidence.snapshot_hash != expected.snapshot_hash
            ):
                raise KnowledgeUnavailable("KNOWLEDGE_UPDATED")
            refreshed.append(actual.evidence)
        return RetrievalBundle(
            mode=bundle.mode,
            context=bundle.context,
            dependencies=bundle.dependencies,
            evidence=refreshed,
        )
