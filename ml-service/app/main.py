import hmac
import os

from fastapi import Depends, FastAPI, Header, HTTPException
from app.routers import wait_time, document_validation, quality_assessment, classification

GATEWAY_SHARED_SECRET = os.environ.get("GATEWAY_SHARED_SECRET", "dev-only-insecure-shared-secret-change-me")

app = FastAPI(
    title="DHA Smart Queue ML Service",
    description="Wait time prediction and document validation for DHA Smart Queue Platform",
    version="1.0.0"
)

# No CORSMiddleware: this service is only ever called service-to-service by
# document-service, never directly from a browser — a wildcard here
# previously let any web page script requests to it, and there was no auth
# at all to stop it.


def require_gateway_secret(x_gateway_secret: str | None = Header(default=None)):
    """Only document-service (which knows GATEWAY_SHARED_SECRET) may call
    these endpoints. Previously there was no auth at all on any router."""
    if not x_gateway_secret or not hmac.compare_digest(x_gateway_secret, GATEWAY_SHARED_SECRET):
        raise HTTPException(status_code=403, detail="Forbidden")


_auth = [Depends(require_gateway_secret)]

app.include_router(wait_time.router, prefix="/predict", tags=["Wait Time"], dependencies=_auth)
app.include_router(document_validation.router, prefix="/validate", tags=["Document Validation"], dependencies=_auth)
app.include_router(quality_assessment.router, prefix="/quality", tags=["Quality Assessment"], dependencies=_auth)
app.include_router(classification.router, prefix="/classify", tags=["Document Classification"], dependencies=_auth)

@app.get("/health")
def health():
    return {"status": "healthy", "service": "dha-ml-service"}