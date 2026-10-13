"""
DHA Face Verification Service
Uses DeepFace (Facenet-512) for face recognition.

DHA already has the citizen's photo on file — it's the face photo from their
verified SA ID/passport document (stored in document-service, linked to that
citizen's ID number). There is no separate biometric enrollment step and
nothing is persisted here: every /verify call is handed both the live
capture and that on-file reference photo (verification-service fetches it
from document-service fresh each time) and simply compares the two. A
userId with no verified ID document on file has nothing to compare against,
so verification-service fails that request closed before it ever reaches
here — this service never treats "no reference photo" as a pass.

Endpoints:
  POST /verify           — compare a live capture against the DHA record (reference) photo
  POST /check-liveness   — passive anti-spoofing check (see liveness_service.py)
  GET  /health           — health check
"""

from __future__ import annotations

import base64
import hmac
import io
import os
import logging

import numpy as np
import uvicorn
from fastapi import Depends, FastAPI, Header, HTTPException
from PIL import Image, ImageOps
from pydantic import BaseModel
from deepface import DeepFace

import liveness_service

# ── Configuration ──────────────────────────────────────────────────────────────

MODEL_NAME  = "Facenet512"   # 512-dim embedding, excellent accuracy
METRIC      = "cosine"
THRESHOLD   = 0.30           # cosine distance ≤ 0.30 → same person (strict)
GATEWAY_SHARED_SECRET = os.getenv("GATEWAY_SHARED_SECRET", "dev-only-insecure-shared-secret-change-me")

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("face-service")

app = FastAPI(title="DHA Face Verification Service", version="1.0.0")

# No CORSMiddleware: this service is only ever called service-to-service by
# verification-service, never directly from a browser, so there is no origin
# to allow — a wildcard here previously let any web page script requests to
# it if it could also guess/brute-force the (previously nonexistent) auth.


def require_gateway_secret(x_gateway_secret: str | None = Header(default=None)):
    """Only verification-service (which knows GATEWAY_SHARED_SECRET) may call
    this service. Without this, this service's userId-keyed DHA-record
    enrollment/verification was reachable by anyone who could reach its
    published port, with the request body's userId trusted outright —
    letting an attacker overwrite or verify against any victim's biometric
    record."""
    if not x_gateway_secret or not hmac.compare_digest(x_gateway_secret, GATEWAY_SHARED_SECRET):
        raise HTTPException(status_code=403, detail="Forbidden")


# ── Request / Response models ──────────────────────────────────────────────────

class FaceRequest(BaseModel):
    userId: str
    imageBase64: str            # live capture from the citizen's camera
    referenceImageBase64: str   # the photo on the citizen's Home Affairs record (identity-service)


class LivenessRequest(BaseModel):
    imageBase64: str


# ── Helpers ────────────────────────────────────────────────────────────────────

# Longest side, in pixels, of any image handed to DeepFace / the liveness
# models. Citizen record photos are stored as uploaded — a 12MP phone photo
# is ~36MB as an RGB array, and detection makes several full-size copies of
# it, which OOM-killed this container at its memory limit. Facenet512 only
# sees a 160x160 face crop anyway, so this costs no accuracy.
MAX_IMAGE_SIDE = int(os.getenv("FACE_MAX_IMAGE_SIDE", "1024"))


def decode_image(b64: str) -> np.ndarray:
    """Decode a base64 (or data-URI) image to a numpy RGB array, upright
    (phone photos carry their rotation in EXIF) and downscaled."""
    if "," in b64:
        b64 = b64.split(",", 1)[1]
    raw = base64.b64decode(b64)
    img = ImageOps.exif_transpose(Image.open(io.BytesIO(raw))).convert("RGB")
    img.thumbnail((MAX_IMAGE_SIDE, MAX_IMAGE_SIDE))
    return np.array(img)


def get_embedding(img_array: np.ndarray) -> list[float]:
    """Extract a face embedding using DeepFace."""
    results = DeepFace.represent(
        img_path=img_array,
        model_name=MODEL_NAME,
        enforce_detection=True,
        detector_backend="opencv",
    )
    if not results:
        raise ValueError("No face detected in the image")
    return results[0]["embedding"]


def cosine_distance(a: list[float], b: list[float]) -> float:
    va, vb = np.array(a), np.array(b)
    similarity = float(np.dot(va, vb) / (np.linalg.norm(va) * np.linalg.norm(vb)))
    return 1.0 - similarity


# ── Routes ─────────────────────────────────────────────────────────────────────

@app.get("/health")
def health():
    return {"status": "ok", "model": MODEL_NAME}


@app.post("/verify", dependencies=[Depends(require_gateway_secret)])
def verify(req: FaceRequest):
    """Compare a live capture against the DHA record (reference) photo.

    Both images arrive on every call — nothing is persisted or enrolled
    here. verification-service fetches the reference photo (the photo on the
    citizen's Home Affairs record) from identity-service fresh each time, and
    rejects a userId with no photo on file before ever reaching this endpoint.
    """
    try:
        live_embedding = get_embedding(decode_image(req.imageBase64))
    except ValueError as exc:
        log.warning("Verification no-face (live capture) userId=%s: %s", req.userId, exc)
        return {"verified": False, "message": f"No face detected in image: {exc}"}
    except Exception as exc:
        log.error("Verification error userId=%s: %s", req.userId, exc)
        raise HTTPException(status_code=500, detail=f"Verification failed: {exc}")

    try:
        reference_embedding = get_embedding(decode_image(req.referenceImageBase64))
    except ValueError as exc:
        log.error("No face detected in DHA record photo userId=%s: %s", req.userId, exc)
        raise HTTPException(status_code=422, detail=f"No face detected in the on-file DHA record photo: {exc}")
    except Exception as exc:
        log.error("Reference photo error userId=%s: %s", req.userId, exc)
        raise HTTPException(status_code=500, detail=f"Verification failed: {exc}")

    distance   = cosine_distance(reference_embedding, live_embedding)
    verified   = distance <= THRESHOLD
    similarity = 1.0 - distance

    log.info(
        "Verification userId=%s  distance=%.4f  threshold=%.2f  result=%s",
        req.userId, distance, THRESHOLD, "PASS" if verified else "FAIL",
    )

    return {
        "verified":   verified,
        "distance":   round(distance, 4),
        "similarity": round(similarity, 4),
        "threshold":  THRESHOLD,
        "message":    "Face verified against DHA record" if verified else "Face does not match DHA record",
    }


@app.post("/check-liveness", dependencies=[Depends(require_gateway_secret)])
def check_liveness(req: LivenessRequest):
    """Passive anti-spoofing check — see liveness_service.py for the model
    and docs/decisions/face-verification-vendor-selection.md for why this
    replaced the external Luxand Cloud call (self-hosted, no vendor quota,
    no biometric frame leaving this infrastructure — same passive-only
    limitation as before, just no longer someone else's API to run out of)."""
    try:
        img = decode_image(req.imageBase64)
    except Exception as exc:
        log.warning("Liveness check: could not decode image: %s", exc)
        raise HTTPException(status_code=400, detail=f"Could not decode image: {exc}")

    try:
        is_live, confidence = liveness_service.check_liveness(img)
    except Exception as exc:
        log.error("Liveness check error: %s", exc)
        raise HTTPException(status_code=500, detail=f"Liveness check failed: {exc}")

    log.info("Liveness check: live=%s confidence=%.4f", is_live, confidence)
    return {
        "live": is_live,
        "confidence": round(confidence, 4),
        "message": "Live person confirmed" if is_live else "Liveness check failed — please use a live camera capture",
    }


if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=8002, reload=True)
