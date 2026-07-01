"""
DHA Face Verification Service
Uses DeepFace (Facenet-512) for face recognition.
Stores reference embeddings in Redis.

Endpoints:
  POST /enroll  — store reference embedding for a userId
  POST /verify  — compare live capture against stored embedding
  GET  /health  — health check
"""

from __future__ import annotations

import base64
import io
import json
import os
import logging

import numpy as np
import redis
import uvicorn
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from PIL import Image
from pydantic import BaseModel
from deepface import DeepFace

# ── Configuration ──────────────────────────────────────────────────────────────

MODEL_NAME  = "Facenet512"   # 512-dim embedding, excellent accuracy
METRIC      = "cosine"
THRESHOLD   = 0.30           # cosine distance ≤ 0.30 → same person (strict)
EMBEDDING_KEY_PREFIX = "face-embedding:"

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("face-service")

app = FastAPI(title="DHA Face Verification Service", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

redis_client = redis.Redis(
    host=os.getenv("REDIS_HOST", "localhost"),
    port=int(os.getenv("REDIS_PORT", 6379)),
    decode_responses=True,
)


# ── Request / Response models ──────────────────────────────────────────────────

class FaceRequest(BaseModel):
    userId: int
    imageBase64: str


# ── Helpers ────────────────────────────────────────────────────────────────────

def decode_image(b64: str) -> np.ndarray:
    """Decode a base64 (or data-URI) image to a numpy RGB array."""
    if "," in b64:
        b64 = b64.split(",", 1)[1]
    raw = base64.b64decode(b64)
    img = Image.open(io.BytesIO(raw)).convert("RGB")
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
    try:
        redis_client.ping()
        redis_ok = True
    except Exception:
        redis_ok = False
    return {"status": "ok", "redis": redis_ok, "model": MODEL_NAME}


@app.post("/enroll")
def enroll(req: FaceRequest):
    """Store a reference face embedding for the given userId."""
    try:
        img = decode_image(req.imageBase64)
        embedding = get_embedding(img)

        key = f"{EMBEDDING_KEY_PREFIX}{req.userId}"
        redis_client.set(key, json.dumps(embedding))

        log.info("Enrolled face for userId=%s  embedding_dim=%d", req.userId, len(embedding))
        return {"success": True, "message": "Face enrolled successfully", "userId": req.userId}

    except ValueError as exc:
        log.warning("Enrollment rejected userId=%s: %s", req.userId, exc)
        raise HTTPException(status_code=400, detail=f"No face detected: {exc}")
    except Exception as exc:
        log.error("Enrollment error userId=%s: %s", req.userId, exc)
        raise HTTPException(status_code=500, detail=f"Enrollment failed: {exc}")


@app.post("/verify")
def verify(req: FaceRequest):
    """Compare live capture against stored reference embedding."""
    key = f"{EMBEDDING_KEY_PREFIX}{req.userId}"
    stored_json = redis_client.get(key)

    if not stored_json:
        log.info("No enrollment found for userId=%s", req.userId)
        return {
            "verified": False,
            "noEnrollment": True,
            "message": "No face enrolled for this user. Please enroll first.",
        }

    try:
        stored_embedding = json.loads(stored_json)
        img = decode_image(req.imageBase64)
        live_embedding = get_embedding(img)

        distance   = cosine_distance(stored_embedding, live_embedding)
        verified   = distance <= THRESHOLD
        similarity = 1.0 - distance

        log.info(
            "Verification userId=%s  distance=%.4f  threshold=%.2f  result=%s",
            req.userId, distance, THRESHOLD, "PASS" if verified else "FAIL",
        )

        return {
            "verified":    verified,
            "noEnrollment": False,
            "distance":    round(distance, 4),
            "similarity":  round(similarity, 4),
            "threshold":   THRESHOLD,
            "message":     "Face verified" if verified else "Face does not match",
        }

    except ValueError as exc:
        log.warning("Verification no-face userId=%s: %s", req.userId, exc)
        return {
            "verified": False,
            "noEnrollment": False,
            "message": f"No face detected in image: {exc}",
        }
    except Exception as exc:
        log.error("Verification error userId=%s: %s", req.userId, exc)
        raise HTTPException(status_code=500, detail=f"Verification failed: {exc}")


if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=8002, reload=True)
