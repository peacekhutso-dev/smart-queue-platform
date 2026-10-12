"""
Serves predictions from the model trained by scripts/train_wait_time.py.
This module is imported by app/main.py, so it must stay a lightweight
FastAPI router — no training logic here.
"""
import json
from datetime import datetime
from pathlib import Path

import joblib
import pandas as pd
from fastapi import APIRouter
from pydantic import BaseModel, Field

router = APIRouter()

ARTIFACTS_DIR = Path(__file__).resolve().parent.parent / "artifacts"

_bundle = joblib.load(ARTIFACTS_DIR / "wait_time_model.joblib")
_pipeline = _bundle["pipeline"]
FEATURE_ORDER = _bundle["features"]

with open(ARTIFACTS_DIR / "feature_metadata.json") as f:
    _metadata = json.load(f)


class WaitTimeRequest(BaseModel):
    doctor_type: str
    financial_class: str
    # Live queue-load figures only queue-service knows at join time — not
    # derivable from a clock, unlike hour/day_of_week/is_weekend below.
    patients_in_system: int = Field(ge=0)
    arrivals_last_60min: int = Field(ge=0)


class WaitTimeResponse(BaseModel):
    estimated_wait_minutes: int
    confidence: float
    method: str


@router.post("/wait-time", response_model=WaitTimeResponse)
def predict_wait_time(request: WaitTimeRequest):
    now = datetime.now()

    features = {
        "hour": now.hour,
        "minute_of_day": now.hour * 60 + now.minute,
        "day_of_week": now.weekday(),
        "is_weekend": int(now.weekday() >= 5),
        "patients_in_system": request.patients_in_system,
        "arrivals_last_60min": request.arrivals_last_60min,
        "Doctor Type": request.doctor_type,
        "Financial Class": request.financial_class,
    }
    row = pd.DataFrame([features], columns=FEATURE_ORDER)

    prediction_minutes = float(_pipeline.predict(row)[0])

    return WaitTimeResponse(
        estimated_wait_minutes=max(int(round(prediction_minutes)), 1),
        confidence=_metadata["test_r2"],
        method=_metadata["best_model"].lower(),
    )
