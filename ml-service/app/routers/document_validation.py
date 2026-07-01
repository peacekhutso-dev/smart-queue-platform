from fastapi import APIRouter, UploadFile, File, Form
from pydantic import BaseModel
import re
import magic

router = APIRouter()

ALLOWED_TYPES = ["application/pdf", "image/jpeg", "image/png", "image/jpg"]
MAX_SIZE_MB = 10
SA_ID_PATTERN = re.compile(r'\b\d{13}\b')

class ValidationResponse(BaseModel):
    valid: bool
    status: str
    notes: str
    detected_document_type: str = "UNKNOWN"

def luhn_check(id_number: str) -> bool:
    if not re.match(r'^\d{13}$', id_number):
        return False
    total = 0
    for i, digit in enumerate(id_number[:-1]):
        n = int(digit)
        if i % 2 != 0:
            n *= 2
            if n > 9:
                n -= 9
        total += n
    check = (10 - (total % 10)) % 10
    return check == int(id_number[-1])

@router.post("/document", response_model=ValidationResponse)
async def validate_document(
    file: UploadFile = File(...),
    document_type: str = Form(default="OTHER")
):
    content = await file.read()
    size_mb = len(content) / (1024 * 1024)

    if size_mb > MAX_SIZE_MB:
        return ValidationResponse(
            valid=False,
            status="REJECTED",
            notes=f"File too large ({size_mb:.1f}MB). Maximum is {MAX_SIZE_MB}MB."
        )

    # MIME type check
    try:
        mime = magic.from_buffer(content[:1024], mime=True)
    except Exception:
        mime = file.content_type or "unknown"

    if mime not in ALLOWED_TYPES:
        return ValidationResponse(
            valid=False,
            status="REJECTED",
            notes=f"Invalid file type: {mime}. Only PDF, JPG, PNG are accepted."
        )

    detected = "UNKNOWN"
    notes = "Document received successfully."

    # Try to extract text for basic checks
    if mime == "application/pdf":
        detected = "PDF_DOCUMENT"
        notes = "PDF document validated. Queued for manual review."
    elif mime in ["image/jpeg", "image/png"]:
        detected = "IMAGE_DOCUMENT"
        notes = "Image document validated successfully."

    return ValidationResponse(
        valid=True,
        status="VALID",
        notes=notes,
        detected_document_type=detected
    )