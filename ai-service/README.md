# Combat AI Service (FastAPI)

## Run

```bash
cd ai-service
python -m venv .venv
.venv\\Scripts\\activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8001
```

## Endpoints

- `GET /healthz`
- `POST /strategies/generate`

该服务输出固定四类战术方案，并强制符合 `strategy-schema v1`。
