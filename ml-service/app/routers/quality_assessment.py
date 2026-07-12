import cv2
import numpy as np
from fastapi import APIRouter, UploadFile, File
from pydantic import BaseModel

router = APIRouter()

# Thresholds are deliberately simple, fixed constants — no training data
# involved. Each is a well-documented classical CV heuristic:
BLUR_VARIANCE_THRESHOLD = 100.0   # variance of Laplacian; below this = blurry
BRIGHTNESS_MIN = 60.0             # mean grayscale intensity; below = underexposed
BRIGHTNESS_MAX = 200.0            # above = overexposed / washed out
GLARE_PIXEL_THRESHOLD = 250       # grayscale value considered "near-saturated"
GLARE_RATIO_MAX = 0.05            # fraction of near-saturated pixels tolerated

PENALTY_PER_FAILED_CHECK = 30
PASS_SCORE = 60


class CheckResult(BaseModel):
    value: float
    passed: bool


class QualityAssessmentResponse(BaseModel):
    qualityScore: int
    passed: bool
    issues: list[str]
    checks: dict[str, CheckResult]


@router.post("/assess", response_model=QualityAssessmentResponse)
async def assess_quality(file: UploadFile = File(...)):
    content = await file.read()
    image = cv2.imdecode(np.frombuffer(content, np.uint8), cv2.IMREAD_COLOR)

    if image is None:
        return QualityAssessmentResponse(
            qualityScore=0,
            passed=False,
            issues=["Could not decode image"],
            checks={},
        )

    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)

    blur_variance = float(cv2.Laplacian(gray, cv2.CV_64F).var())
    blur_passed = blur_variance >= BLUR_VARIANCE_THRESHOLD

    brightness = float(gray.mean())
    brightness_passed = BRIGHTNESS_MIN <= brightness <= BRIGHTNESS_MAX

    glare_ratio = float(np.count_nonzero(gray > GLARE_PIXEL_THRESHOLD) / gray.size)
    glare_passed = glare_ratio <= GLARE_RATIO_MAX

    checks = {
        "blur": CheckResult(value=round(blur_variance, 2), passed=blur_passed),
        "brightness": CheckResult(value=round(brightness, 2), passed=brightness_passed),
        "glare": CheckResult(value=round(glare_ratio * 100, 2), passed=glare_passed),
    }

    issues = []
    if not blur_passed:
        issues.append("Image is too blurry")
    if not brightness_passed:
        issues.append("Image is too dark" if brightness < BRIGHTNESS_MIN else "Image is too bright/washed out")
    if not glare_passed:
        issues.append("Glare detected on document")

    score = max(0, 100 - PENALTY_PER_FAILED_CHECK * len(issues))

    return QualityAssessmentResponse(
        qualityScore=score,
        passed=score >= PASS_SCORE,
        issues=issues,
        checks=checks,
    )
