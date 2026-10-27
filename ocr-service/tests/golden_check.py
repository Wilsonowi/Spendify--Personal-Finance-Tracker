from __future__ import annotations

import re
import sys
from pathlib import Path

sys.path.insert(0, "/app")

from app.engine import PaddleOcrEngine


FIXTURES = Path("/fixtures")
CASES = {
    "public-bank-tng-topup.png": ["PUBLIC BANK", "10000", "PB TNG 7812"],
    "tng-topup-credit.png": ["TNG EWALLET", "10000", "TNG 7812"],
    "tng-chicken-rice-payment.png": ["1250", "X001 DAVIDS CHICK"],
}


def normalized(value: str) -> str:
    return re.sub(r"[^A-Z0-9]+", " ", value.upper()).strip()


def compact(value: str) -> str:
    return normalized(value).replace(" ", "")


engine = PaddleOcrEngine()
for filename, expected_tokens in CASES.items():
    path = FIXTURES / filename
    text, confidence = engine.extract(path.read_bytes(), path.suffix)
    searchable = normalized(text)
    compacted = compact(text)
    for token in expected_tokens:
        candidate = normalized(token)
        assert candidate in searchable or candidate.replace(" ", "") in compacted, (
            f"{filename}: missing {token!r} in OCR output {text!r}"
        )
    assert confidence >= 0.70, f"{filename}: confidence {confidence:.3f} is below 0.70"
    print(f"{filename}: confidence={confidence:.3f} text={searchable}")

print("ocr-golden-ok")
