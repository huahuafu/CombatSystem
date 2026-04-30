from fastapi import FastAPI
from .models import GenerateRequest, GenerateResponse
from .generator import generate_strategies

app = FastAPI(title="combat-ai-service", version="1.0.0")


@app.get("/healthz")
def healthz() -> dict:
    return {"status": "ok"}


@app.post("/strategies/generate", response_model=GenerateResponse)
def generate(body: GenerateRequest) -> GenerateResponse:
    return generate_strategies(body)
