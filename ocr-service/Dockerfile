FROM python:3.11-slim-bookworm

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PADDLE_PDX_MODEL_SOURCE=BOS \
    HOME=/opt/spendify \
    PADDLE_PDX_CACHE_HOME=/opt/spendify/.paddlex

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    libgl1 libglib2.0-0 libgomp1 \
    && useradd --system --uid 10001 --home-dir /opt/spendify --create-home spendify \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app

RUN chown -R spendify:spendify /opt/spendify
USER spendify

# Runtime networking is intentionally internal-only, so cache model weights now.
RUN python -c "from paddleocr import PaddleOCR; PaddleOCR(use_doc_orientation_classify=False, use_doc_unwarping=False, use_textline_orientation=False, engine='paddle')"

EXPOSE 8090
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8090", "--no-access-log"]
