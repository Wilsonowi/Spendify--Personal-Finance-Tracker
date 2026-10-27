from __future__ import annotations

import asyncio
from pathlib import Path
from typing import Annotated, Protocol

from fastapi import Depends, FastAPI, File, HTTPException, UploadFile, status
from pydantic import BaseModel

from app.engine import PaddleOcrEngine

MAX_BYTES = 10 * 1024 * 1024
ALLOWED_TYPES = {
    "image/png": ".png",
    "image/jpeg": ".jpg",
    "image/webp": ".webp",
    "application/pdf": ".pdf",
}


class OcrEngine(Protocol):
    def extract(self, content: bytes, suffix: str) -> tuple[str, float]: ...


class OcrResponse(BaseModel):
    text: str
    confidence: float


engine = PaddleOcrEngine()


def get_engine() -> OcrEngine:
    return engine


app = FastAPI(
    title="Spendify Private OCR",
    version="0.1.0",
    description="Ephemeral OCR boundary for encrypted financial-slip ingestion.",
)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "retention": "none"}


@app.post("/v1/ocr", response_model=OcrResponse)
async def extract_text(
    file: Annotated[UploadFile, File()],
    ocr_engine: Annotated[OcrEngine, Depends(get_engine)],
) -> OcrResponse:
    content_type = (file.content_type or "").lower()
    if content_type not in ALLOWED_TYPES:
        raise HTTPException(status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
                            detail="Only PNG, JPEG, WebP, and PDF documents are supported.")
    content = await file.read(MAX_BYTES + 1)
    if not content or len(content) > MAX_BYTES:
        raise HTTPException(status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
                            detail="Document must be between 1 byte and 10 MB.")

    suffix = ALLOWED_TYPES[content_type]
    try:
        text, confidence = await asyncio.to_thread(ocr_engine.extract, content, suffix)
    except Exception as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
                            detail="OCR could not read this document.") from exc
    if not text.strip():
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
                            detail="No readable text was found.")
    return OcrResponse(text=text, confidence=max(0.0, min(1.0, confidence)))

