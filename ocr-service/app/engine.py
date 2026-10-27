from __future__ import annotations

import statistics
import tempfile
import threading
from pathlib import Path
from typing import Any


class PaddleOcrEngine:
    """Loads PaddleOCR on first use and never persists caller documents."""

    def __init__(self) -> None:
        self._model: Any | None = None
        self._lock = threading.Lock()

    def extract(self, content: bytes, suffix: str) -> tuple[str, float]:
        model = self._get_model()
        path: Path | None = None
        try:
            with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as temporary:
                temporary.write(content)
                path = Path(temporary.name)
            pages = model.predict(str(path))
            lines: list[str] = []
            scores: list[float] = []
            for page in pages:
                payload = page.json() if callable(getattr(page, "json", None)) else page.json
                result = payload.get("res", payload)
                lines.extend(str(value) for value in result.get("rec_texts", []) if str(value).strip())
                scores.extend(float(value) for value in result.get("rec_scores", []))
            confidence = statistics.fmean(scores) if scores else 0.0
            return "\n".join(lines), round(confidence, 4)
        finally:
            if path is not None:
                path.unlink(missing_ok=True)

    def _get_model(self) -> Any:
        if self._model is not None:
            return self._model
        with self._lock:
            if self._model is None:
                from paddleocr import PaddleOCR

                self._model = PaddleOCR(
                    use_doc_orientation_classify=False,
                    use_doc_unwarping=False,
                    use_textline_orientation=False,
                    engine="paddle",
                )
        return self._model

