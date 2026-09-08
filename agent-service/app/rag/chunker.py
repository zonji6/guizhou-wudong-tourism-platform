def chunk_document(document_id: str, title: str, text: str, size: int = 500, overlap: int = 80) -> list[dict]:
    """按中文字符切块，并始终保留可展示的来源。"""
    if not text.strip():
        return []
    chunks: list[dict] = []
    start = 0
    while start < len(text):
        content = text[start : start + size]
        chunks.append({"document_id": document_id, "title": title, "content": content})
        if start + size >= len(text):
            break
        start += size - overlap
    return chunks
