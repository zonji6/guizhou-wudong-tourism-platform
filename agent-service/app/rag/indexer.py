from app.rag.chunker import chunk_document
from app.rag.embedding_client import DashScopeEmbeddingClient


class KnowledgeIndexer:
    async def index(self, document_id: int, title: str, content: str) -> dict:
        chunks = chunk_document(document_id, title, content)
        vectors = await DashScopeEmbeddingClient().embed([item["content"] for item in chunks])
        return {"index": "idx:udong:knowledge", "document_id": document_id, "chunks": len(chunks), "vectors": len(vectors)}
