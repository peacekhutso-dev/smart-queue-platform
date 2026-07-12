from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.routers import wait_time, document_validation, quality_assessment, classification

app = FastAPI(
    title="DHA Smart Queue ML Service",
    description="Wait time prediction and document validation for DHA Smart Queue Platform",
    version="1.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(wait_time.router, prefix="/predict", tags=["Wait Time"])
app.include_router(document_validation.router, prefix="/validate", tags=["Document Validation"])
app.include_router(quality_assessment.router, prefix="/quality", tags=["Quality Assessment"])
app.include_router(classification.router, prefix="/classify", tags=["Document Classification"])

@app.get("/health")
def health():
    return {"status": "healthy", "service": "dha-ml-service"}