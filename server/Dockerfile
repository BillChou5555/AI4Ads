FROM python:3.11-slim

WORKDIR /app
ENV PYTHONUNBUFFERED=1

RUN apt-get update && apt-get install -y --no-install-recommends build-essential libpq-dev && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
# 先装 CPU-only torch，避免下载 GPU 版本（节省 ~1.5GB）
RUN pip install --no-cache-dir torch --index-url https://download.pytorch.org/whl/cpu
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# 预下载 BGE 模型（避免运行时下载）
RUN python -c "from sentence_transformers import SentenceTransformer; SentenceTransformer('BAAI/bge-large-zh-v1.5', device='cpu')"

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
