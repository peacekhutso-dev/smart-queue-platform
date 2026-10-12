
# Business Logic: Home Affairs Digital Service Management Module

**Status:** Proposed — not yet implemented (see [Implementation Status](#implementation-status))
**Date:** 2026-07-11
**Component:** cross-cutting (new catalog data + `document-service`, `ml-service`, `verification-service`, `queue-service`)

## Context

The platform currently exposes a fixed, hardcoded list of 5 service types
with no distinction by applicant category (citizen vs. permanent resident
vs. foreign national vs. refugee/asylum seeker), and no fees anywhere in the
system. This document captures the intended business logic for a fuller
**Home Affairs Digital Service Management Module**: applicants select a
category, see only the services/fees/documents relevant to them, upload
documents through an Intelligent Document Processing (IDP) pipeline, and
only reach appointment booking once their application is validated as
"ready."

This is a **business rules reference**, not an implementation spec. The
actual applicant types, services, fees, and document requirements must be
stored as data (DB rows / config), not hardcoded in application code — see
[Implementation Note](#implementation-note-do-not-hardcode).

## Step 1 — Applicant Type Selection

Applicant selects one of:
- South African Citizen
- Permanent Resident
- Foreign National
- Refugee / Asylum Seeker

The selected type drives which services, fees, and document rules are shown
in every subsequent step.

## Step 2 — Services & Fees by Applicant Type

### South African Citizens

| Service | Official Fee |
|---|---:|
| First Smart ID Card (16+) | Free |
| Smart ID Replacement | R140 |
| Temporary Identity Certificate (TIC) | R70 |
| Adult Tourist Passport (32 pages) | R600 |
| Adult Maxi Passport (48 pages) | R1 200 |
| Child Passport | R600 |
| Emergency Travel Certificate | R140 |
| Birth Registration | Free |
| Birth Certificate (First Issue) | Free |
| Birth Certificate (Duplicate) | R75 |
| Marriage Registration | Free |
| Marriage Certificate (Duplicate) | R75 |
| Death Registration | Free |
| Death Certificate (Duplicate) | R75 |
| Amendment of Personal Particulars | R70–R140 |
| Retention of SA Citizenship | R300 |
| Reinstatement of SA Citizenship | R300 |
| Renunciation of SA Citizenship | Free |

### Foreign Nationals

| Service | Official Fee* |
|---|---:|
| Visitor Visa | R425 |
| Study Visa | R425 |
| Work Visa | R425 |
| Critical Skills Visa | R425 |
| Business Visa | R425 |
| Relative Visa | R425 |
| Permanent Residence Permit | R1 520 |
| Visa Renewal | Varies |
| Visa Extension | Varies |

\* Excludes applicable VFS Global service charges.

### Refugees & Asylum Seekers

| Service | Official Fee |
|---|---:|
| Refugee Status Application | Free |
| Asylum Seeker Permit | Free |
| Permit Renewal | Free |
| Refugee Identity Document | Free |
| Refugee Travel Document | Free |

## Step 3 — Dynamic Document Requirements

Required documents are keyed by (applicant type, service), not by service
alone. Examples:

| Service | Required Documents |
|---|---|
| First Smart ID Card | Birth Certificate, Proof of Residence |
| Smart ID Replacement | Existing ID (if available), Affidavit (if lost/stolen), Proof of Residence |
| Adult Passport | SA Smart ID, Existing Passport (renewals), Passport Photo |
| Study Visa | Foreign Passport, University Acceptance Letter, Proof of Medical Cover, Financial Proof, Police Clearance Certificate |
| Permanent Residence Permit | Passport, Current Visa, Police Clearance Certificate, Medical Report, Financial Statements |

## Step 4 — Document Upload

Applicant uploads into a labelled slot per required document. The AI
independently verifies the uploaded file actually matches the expected
document type for that slot (not just trusting the label).

## Step 5 — Intelligent Document Processing (IDP) Pipeline

```
Upload → File Validation → Document Optimization →
AI Document Classification & Validation → OCR Extraction →
Business Rules Validation → Image Quality Assessment →
Fraud Detection → Biometric Verification (where applicable) →
Secure Storage
```

## Step 6 — AI Document Classification & Validation

Confirms the uploaded file's detected type matches the expected slot type.
Mismatch (e.g. expected "Passport", detected "Utility Bill") is rejected
with a specific re-upload prompt.

Supported categories: SA Smart ID, Green Barcoded ID Book, Passport, Birth
Certificate, Marriage Certificate, Death Certificate, Proof of Residence,
Police Clearance Certificate, Utility Bill, Bank Statement, Medical Report,
Visa Documents, Affidavit, Foreign Passport.

## Step 7 — OCR Extraction (per document type)

- **Smart ID:** SA ID number, full name, DOB, citizenship status
- **Passport:** passport number, nationality, expiry date, DOB
- **Birth Certificate:** child's name, parents' names, registration number, DOB
- **Proof of Residence:** name, address, municipality, issue date
- **Visa:** category, visa number, expiry date, passport number

## Step 8 — Business Rules Validation

- **SA Smart ID:** valid 13-digit number, valid Luhn checksum, DOB matches
  encoded ID, citizenship digit valid, matches registered applicant profile.
- **Passport:** number detected, not expired, name matches applicant.
- **Proof of Residence:** issued within last 3 months, address detected.
- **Visa:** category identified, not expired, passport number matches
  uploaded passport, applicant details consistent.

## Step 9 — Image Quality Assessment

Blur, brightness, cropping, rotation, missing edges, glare, readability.
Poor-quality uploads are rejected before OCR runs.

## Step 10 — Fraud Detection

Duplicate uploads, edited/manipulated documents, missing security features,
inconsistent info across documents, repeated submissions across accounts.

## Step 11 — Biometric Verification (where applicable)

```
Uploaded Passport/Smart ID → Extract Portrait → Capture Live Selfie →
Liveness Detection → Face Verification →
Compare with Simulated DHA Database → Verified / Rejected
```

## Step 12 — Application Readiness Assessment

Only applications that pass every prior stage are marked `READY TO BOOK
APPOINTMENT` and allowed into the queue-booking module.

## Evaluation Strategy (for dissertation write-up)

| Document Type | Sample Size |
|---|---:|
| SA Smart ID | 30 |
| Green Barcoded ID Book | 20 |
| SA Passport | 30 |
| Birth Certificate | 20 |
| Marriage Certificate | 15 |
| Proof of Residence | 30 |
| Foreign Passport | 20 |
| Study Visa | 10 |
| Work Visa | 10 |
| Utility Bill | 20 |

Metrics: classification accuracy, OCR extraction accuracy, business-rule
validation accuracy, image-quality detection accuracy, face-verification
success rate, average processing time, overall readiness success rate.

## Scope Boundary

This module is a digital **pre-submission and appointment-readiness**
layer, not a replacement for DHA's actual immigration/civic case management
systems. It validates documents, confirms identity, and gates appointment
booking — it does not adjudicate applications.

## Implementation Note: Do Not Hardcode

Applicant types, services, fees, and document requirements must live as
**data** (DB tables / admin-editable config), keyed by
`(applicant_type, service)`, not as enums or switch statements in code. This
lets fees/requirements be corrected without a redeploy and matches DHA's own
fee schedule changing over time. See prior discussion: recommended as a
DB-backed catalog, either a new `catalog-service` or tables within
`document-service`, queried at runtime by the IDP pipeline and by the
frontend service-selection screen.

## Implementation Status

Not implemented. Current system has only:
- 5 hardcoded `ServiceType` values with no applicant-type dimension
  (`frontend/src/types/index.ts`)
- Hardcoded `SERVICE_LABELS` / `REQUIRED_DOCS` objects in
  `frontend/src/pages/DocumentsPage.tsx`
- A 6-value `DocumentType` enum in `document-service`'s `Document` entity
- No fee/price field anywhere in the codebase
- Partial IDP pipeline: `ml-service` has document classification
  (`document_validation.py`) and quality assessment
  (`quality_assessment.py`) only — no OCR extraction, business-rules
  validation, fraud detection, or readiness-assessment endpoints yet
- `verification-service` covers biometric liveness + face match (step 11)
  already, per [face-verification-vendor-selection.md](face-verification-vendor-selection.md)

## Next Steps (not started)

1. Design `(applicant_type, service)` catalog schema — services, fees,
   required documents, validation rules — as DB data.
2. Extend `Document.DocumentType` to the fuller category list.
3. Add OCR extraction, business-rules validation, and fraud-detection
   endpoints to `ml-service`.
4. Add a readiness-assessment aggregation endpoint (likely in
   `document-service`) that checks all pipeline stages passed before
   allowing queue booking.
5. Rework `DocumentsPage.tsx` and add an applicant-type selection step to
   read from the catalog instead of hardcoded objects.
