import base64
import json
import os

import anthropic
from fastapi import APIRouter, UploadFile, File, Form
from pydantic import BaseModel

router = APIRouter()

MODEL = "claude-opus-4-8"

# Full taxonomy from the IDP pipeline spec — Claude classifies against all of
# these regardless of what the citizen selected, so a genuine mismatch (e.g.
# expected Passport, uploaded a utility bill) is caught rather than laundered
# into a false match.
CATEGORY_DESCRIPTIONS = {
    "SA_SMART_ID": "South African Smart ID card (credit-card-sized, biometric chip)",
    "GREEN_ID_BOOK": "South African Green ID Book (older, non-chip identity document)",
    "PASSPORT": "Passport (any country), with a photo page and machine-readable zone",
    "BIRTH_CERTIFICATE": "Birth certificate / birth registration document",
    "MARRIAGE_CERTIFICATE": "Marriage certificate / marriage registration document",
    "DEATH_CERTIFICATE": "Death certificate",
    "PROOF_OF_RESIDENCE": "An official proof-of-residence letter or municipal document",
    "POLICE_CLEARANCE": "Police clearance certificate",
    "MATRIC_CERTIFICATE": "Matric / high school leaving certificate",
    "BANK_STATEMENT": "Bank account statement",
    "UTILITY_BILL": "Utility bill (electricity, water, etc.)",
    "AFFIDAVIT": "Sworn affidavit document",
    "MEDICAL_CERTIFICATE": "Medical certificate",
    "UNKNOWN_DOCUMENT": "Does not match any of the above categories",
}

# Document.DocumentType (Java, narrower) -> acceptable detected categories.
# Deliberately strict: PROOF_OF_RESIDENCE only matches PROOF_OF_RESIDENCE, not
# UTILITY_BILL/BANK_STATEMENT, even though those are common real-world proof
# of residence documents — the spec lists them as distinct categories.
EXPECTED_TYPE_MATCHES = {
    "SA_ID": {"SA_SMART_ID", "GREEN_ID_BOOK"},
    "PASSPORT": {"PASSPORT"},
    "BIRTH_CERTIFICATE": {"BIRTH_CERTIFICATE"},
    "MARRIAGE_CERTIFICATE": {"MARRIAGE_CERTIFICATE"},
    "PROOF_OF_RESIDENCE": {"PROOF_OF_RESIDENCE"},
}

SYSTEM_PROMPT = (
    "You classify identity and supporting documents for a South African Home Affairs digital service. Categories:\n"
    + "\n".join(f"- {name}: {desc}" for name, desc in CATEGORY_DESCRIPTIONS.items())
    + "\n\nIf the document is verified as matching the citizen's claimed type, also extract these fields where "
    "present (leave any that don't apply to this document type as null):\n"
    "- Smart ID / Green ID Book: idNumber, fullName, surname, dateOfBirth, citizenshipStatus\n"
    "- Passport: passportNumber, fullName, nationality, dateOfBirth, expiryDate, sex\n"
    "- Proof of Residence: fullName, address, municipality, postalCode, issueDate\n"
    "- Birth Certificate: childName, parentNames, registrationNumber, dateOfBirth"
)

# All extractable fields across every document type, as a flat set of
# nullable strings rather than a schema that varies per type (simpler than
# anyOf-per-category, and Claude already knows from the prompt which fields
# apply to which document).
EXTRACTED_FIELD_NAMES = [
    "idNumber", "fullName", "surname", "dateOfBirth", "citizenshipStatus",
    "passportNumber", "nationality", "expiryDate", "sex",
    "address", "municipality", "postalCode", "issueDate",
    "childName", "parentNames", "registrationNumber",
]

CLASSIFICATION_SCHEMA = {
    "type": "object",
    "properties": {
        "detected_type": {"type": "string", "enum": list(CATEGORY_DESCRIPTIONS.keys())},
        "reasoning": {"type": "string", "description": "One sentence explaining the visual evidence for this classification"},
        "extracted_fields": {
            "type": "object",
            "properties": {name: {"type": ["string", "null"]} for name in EXTRACTED_FIELD_NAMES},
            "required": EXTRACTED_FIELD_NAMES,
            "additionalProperties": False,
        },
    },
    "required": ["detected_type", "reasoning", "extracted_fields"],
    "additionalProperties": False,
}


class ClassificationResponse(BaseModel):
    expectedType: str
    detectedType: str
    verified: bool
    message: str
    extractedFields: dict[str, str | None] = {}


@router.post("/document", response_model=ClassificationResponse)
async def classify_document(
    file: UploadFile = File(...),
    expected_type: str = Form(...),
):
    if not os.environ.get("ANTHROPIC_API_KEY"):
        return _skip_result(expected_type, "Classification not configured (no API key); skipping check.")

    content = await file.read()
    b64 = base64.standard_b64encode(content).decode("utf-8")
    mime = file.content_type or "application/octet-stream"

    if mime == "application/pdf":
        content_block = {"type": "document", "source": {"type": "base64", "media_type": mime, "data": b64}}
    else:
        content_block = {"type": "image", "source": {"type": "base64", "media_type": mime, "data": b64}}

    try:
        client = anthropic.Anthropic()
        response = client.messages.create(
            model=MODEL,
            max_tokens=1024,
            system=SYSTEM_PROMPT,
            output_config={"format": {"type": "json_schema", "schema": CLASSIFICATION_SCHEMA}},
            messages=[{
                "role": "user",
                "content": [
                    content_block,
                    {"type": "text", "text": "Classify this document. The citizen claims it is: " + expected_type},
                ],
            }],
        )

        if response.stop_reason == "refusal":
            return _skip_result(expected_type, "Classification declined by safety filters; skipping check.")

        text = next(b.text for b in response.content if b.type == "text")
        parsed = json.loads(text)
        detected = parsed["detected_type"]
        fields = parsed.get("extracted_fields", {})

    except Exception as e:
        return _skip_result(expected_type, f"Classification unavailable ({e}); skipping check.")

    allowed = EXPECTED_TYPE_MATCHES.get(expected_type)
    verified = allowed is None or detected in allowed

    message = (
        "Document type verified."
        if verified
        else f"Incorrect document uploaded. Expected {expected_type}, got {detected}. Please upload a valid {expected_type.replace('_', ' ').title()}."
    )

    # No point handing back extracted fields for a document that's already
    # the wrong type, or a type with nothing structured to extract.
    if not verified or detected in ("OTHER", "UNKNOWN_DOCUMENT"):
        fields = {}

    return ClassificationResponse(
        expectedType=expected_type,
        detectedType=detected,
        verified=verified,
        message=message,
        extractedFields={k: v for k, v in fields.items() if v is not None},
    )


def _skip_result(expected_type: str, message: str) -> ClassificationResponse:
    return ClassificationResponse(
        expectedType=expected_type,
        detectedType="UNKNOWN_DOCUMENT",
        verified=True,
        message=message,
    )
