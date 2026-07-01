from fastapi import APIRouter
from pydantic import BaseModel
import math

router = APIRouter()

class WaitTimeRequest(BaseModel):
    queue_size: int
    active_counters: int
    avg_service_time_minutes: int
    service_type: str = "GENERAL_ENQUIRY"
    time_of_day_hour: int = 10

class WaitTimeResponse(BaseModel):
    estimated_wait_minutes: int
    confidence: float
    method: str

SERVICE_TYPE_MULTIPLIERS = {
    "SMART_ID": 1.2,
    "PASSPORT": 1.1,
    "BIRTH_CERTIFICATE": 0.9,
    "MARRIAGE_CERTIFICATE": 1.0,
    "GENERAL_ENQUIRY": 0.7,
}

PEAK_HOUR_MULTIPLIERS = {
    range(8, 10): 1.3,
    range(10, 12): 1.1,
    range(12, 14): 0.8,
    range(14, 16): 1.0,
    range(16, 18): 1.2,
}

@router.post("/wait-time", response_model=WaitTimeResponse)
def predict_wait_time(request: WaitTimeRequest):
    counters = max(request.active_counters, 1)
    base_wait = (request.queue_size * request.avg_service_time_minutes) / counters

    service_mult = SERVICE_TYPE_MULTIPLIERS.get(request.service_type, 1.0)

    peak_mult = 1.0
    for hour_range, mult in PEAK_HOUR_MULTIPLIERS.items():
        if request.time_of_day_hour in hour_range:
            peak_mult = mult
            break

    final_wait = int(math.ceil(base_wait * service_mult * peak_mult))
    final_wait = max(final_wait, 1)

    return WaitTimeResponse(
        estimated_wait_minutes=final_wait,
        confidence=0.75,
        method="formula_v1"
    )