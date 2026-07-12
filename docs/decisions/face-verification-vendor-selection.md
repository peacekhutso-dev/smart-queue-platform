# Design Decision: Face Verification & Liveness Detection Vendor

**Status:** Accepted
**Date:** 2026-07-10
**Component:** `verification-service`

## Context

The Smart Queue platform simulates a Department of Home Affairs (DHA) identity
verification flow: a citizen enters their SA ID number, the system retrieves
their record, and their live selfie must be checked (a) for liveness — is
this a real person in front of the camera, not a photo/video replay — and
(b) for a match against their previously enrolled face.

Two candidate vendors were evaluated for the liveness/verification step:
**FaceTec** and **Luxand Cloud**. Luxand was selected as the faster path to a
working integration. Before wiring it in, we discussed whether relying on it
was appropriate given this is meant to be a real, potentially production
system rather than a disposable prototype.

## Options Considered

| Option | Description |
|---|---|
| A. Third-party vendor does everything (liveness + face match) | Send both the liveness check and the actual identity match to Luxand's cloud API. |
| B. Fully self-hosted | Build liveness detection and face matching in-house (already had a self-hosted DeepFace-based `face-service` doing face matching). |
| C. Hybrid — third-party liveness, self-hosted match | Use a vendor only for liveness (harder problem, less risk if wrong), keep the actual identity-match decision self-hosted. |

**Decision: Option C.** Luxand Cloud handles liveness detection only. The
existing self-hosted `face-service` (DeepFace, Facenet-512 embeddings, cosine
distance) remains the system of record for the actual face-match decision.

## Key Considerations Discussed

### 1. Data protection / POPIA
South Africa's Protection of Personal Information Act (POPIA) classifies
biometric data as **special personal information**. A DHA-adjacent system
sending citizens' face images to a third-party cloud API carries real
compliance exposure — where the data is processed, who has access, and
under what data processing agreement all matter if this were adopted for
real. Minimizing what leaves our infrastructure (liveness check only, not
the enrolled reference photo/embedding) reduces that exposure.

### 2. Cost and control at scale
Luxand's free tier is capped at 500 requests/month; paid tiers are
metered per request (~$49/month for 5,000 requests at the entry paid tier).
At national scale, a per-citizen-per-visit vendor bill for the core match
decision would be a significant recurring cost and a scaling risk. The
self-hosted DeepFace match has a fixed infrastructure cost instead.

### 3. Auditability
A self-hosted match decision can be inspected, tuned (threshold, model),
retrained, and explained if a citizen disputes a rejection. A third-party
match API is a black box — harder to justify in an appeals or audit
context for a government-adjacent system.

### 4. Vendor availability / lock-in
A small third-party vendor going down, changing pricing, or shutting down
would take a live citizen-facing verification flow with it if the vendor
sat on the critical match path. Scoping Luxand to liveness only (a
"nice-to-have" gate that degrades gracefully) limits blast radius compared
to it being load-bearing for the actual identity decision.

### 5. Industry practice — what banks actually do
Research during this discussion confirmed that banks and fintechs
overwhelmingly **do** use third-party vendors for KYC liveness/verification
(FaceTec, Onfido/Entrust, Jumio, Veriff, iProov, Sumsub, Persona, Socure) —
building this in-house is not the industry norm even at bank scale.

The distinguishing factor is not "third-party vs. self-hosted" but
**certification**: bank-grade vendors carry **iBeta Presentation Attack
Detection (PAD) certification** under ISO/IEC 30107-3 (Level 1/2/3),
independently verifying the liveness check actually resists photo/video/mask
spoofing. FaceTec is iBeta Level 1 & 2 certified. Luxand does not appear on
the list of iBeta-certified vendors found during this research.

**Implication:** Luxand is an acceptable choice to get a working liveness
gate quickly, but it is not the vendor a certified, production KYC flow
would ultimately ship on. This is a known, deliberate gap — see
[Consequences](#consequences).

## Decision

- Luxand Cloud (`POST https://api.luxand.cloud/photo/liveness`) is used
  **only** for the liveness check, gating both face enrollment and face
  verification before they reach the self-hosted `face-service`.
- The self-hosted DeepFace-based `face-service` remains the verifier of
  record for the actual 1:1 face match — this is the biometric decision
  that stays under our own data control.
- The Luxand integration is isolated behind a dedicated client
  (`LuxandLivenessClient` in `verification-service`) so it can be swapped
  for an iBeta-certified vendor (e.g., FaceTec) later without touching the
  rest of the verification flow.

## Consequences

**Accepted for now:**
- Liveness detection is not independently certified against spoofing
  (photo-of-photo, deepfake, mask attacks). Acceptable for a prototype/demo
  system; **not acceptable as-is if this system is adopted for real citizen
  use** without revisiting the vendor.
- Raw selfie frames are sent to Luxand's cloud for the liveness call only —
  still an external data transfer that would need a documented data
  processing agreement and POPIA review before production use, even though
  it's a smaller surface than sending the enrolled reference photo too.

**Before real-world adoption, revisit:**
1. Replace Luxand with an iBeta Level 2+ certified vendor (FaceTec or
   equivalent), or a self-hosted certified liveness model if one becomes
   available, using the existing `LuxandLivenessClient` seam as the
   integration point to swap.
2. Formal POPIA data processing assessment for any biometric frame that
   leaves our infrastructure, regardless of vendor.
3. Re-evaluate cost model once request volume is estimated at a realistic
   adoption scale.
