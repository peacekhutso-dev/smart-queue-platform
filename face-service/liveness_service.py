"""
Wraps the vendored Silent-Face-Anti-Spoofing model (see liveness/NOTICE.md)
for use as this service's liveness/anti-spoofing check — replaces the
external Luxand Cloud API verification-service previously called for this,
so no biometric frame needs to leave this infrastructure for liveness
either, and there's no per-request vendor quota to run out of.

Passive, single-frame check only — same category of limitation Luxand had
(not certified against presentation attacks per docs/decisions/
face-verification-vendor-selection.md). Swapping the vendor doesn't change
that; see the ADR for what would.
"""
from __future__ import annotations

import os
import sys

import numpy as np

LIVENESS_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "liveness")
sys.path.insert(0, LIVENESS_DIR)

from src.anti_spoof_predict import AntiSpoofPredict  # noqa: E402
from src.generate_patches import CropImage  # noqa: E402
from src.utility import parse_model_name  # noqa: E402

MODEL_DIR = os.path.join(LIVENESS_DIR, "resources", "anti_spoof_models")

_predictor = AntiSpoofPredict(device_id=0)
_cropper = CropImage()
_model_files = [f for f in os.listdir(MODEL_DIR) if f.endswith(".pth")]


def check_liveness(image: np.ndarray) -> tuple[bool, float]:
    """image: RGB numpy array (H, W, 3). Returns (is_live, confidence)."""
    bgr = image[:, :, ::-1]  # the vendored code (and its RetinaFace detector) expects BGR, like cv2.imread gives
    bbox = _predictor.get_bbox(bgr)

    prediction = np.zeros((1, 3))
    for model_name in _model_files:
        h_input, w_input, model_type, scale = parse_model_name(model_name)
        param = {
            "org_img": bgr, "bbox": bbox, "scale": scale,
            "out_w": w_input, "out_h": h_input, "crop": scale is not None,
        }
        patch = _cropper.crop(**param)
        prediction += _predictor.predict(patch, os.path.join(MODEL_DIR, model_name))

    label = int(np.argmax(prediction))
    confidence = float(prediction[0][label] / len(_model_files))
    is_live = label == 1  # 1 = real, {0, 2} = the two spoof classes (print/replay)
    return is_live, confidence
