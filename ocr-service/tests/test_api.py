from fastapi.testclient import TestClient

from app.main import app, get_engine


class StubEngine:
    def extract(self, content: bytes, suffix: str) -> tuple[str, float]:
        assert content == b"synthetic-image"
        assert suffix == ".png"
        return "PUBLIC BANK\nAmount: RM 100.00", 0.94


def test_health_does_not_load_model() -> None:
    response = TestClient(app).get("/health")
    assert response.status_code == 200
    assert response.json()["retention"] == "none"


def test_ocr_uses_ephemeral_engine_boundary() -> None:
    app.dependency_overrides[get_engine] = lambda: StubEngine()
    try:
        response = TestClient(app).post(
            "/v1/ocr",
            files={"file": ("public-bank.png", b"synthetic-image", "image/png")},
        )
    finally:
        app.dependency_overrides.clear()
    assert response.status_code == 200
    assert response.json() == {
        "text": "PUBLIC BANK\nAmount: RM 100.00",
        "confidence": 0.94,
    }


def test_rejects_unsupported_media() -> None:
    response = TestClient(app).post(
        "/v1/ocr",
        files={"file": ("secret.txt", b"not-an-image", "text/plain")},
    )
    assert response.status_code == 415

