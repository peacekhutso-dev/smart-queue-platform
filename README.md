# 🇿🇦 Smart Queue Platform — DHA eHomeAffairs Style

> A South African smart queue management system inspired by the Department of Home Affairs (DHA), built as a **microservices architecture behind a single Zero Trust API Gateway** — JWT + 2FA + face verification enforced on every protected request, before it ever reaches a backend service.

---

## Overview

This platform enables South African citizens to:
- Authenticate securely (httpOnly-cookie JWT session + OTP 2FA)
- Verify identity using SA ID numbers (Luhn-validated)
- Verify their face against a DHA record via a **self-hosted** face-matching + liveness pipeline
- Join virtual queues at DHA branches and get a real, scannable **QR code ticket**
- Receive real-time queue status over WebSocket
- Upload and validate documents before branch visits (AI-assisted classification/quality check)
- Browse a catalog of DHA services, applicant types, fees, and required documents
- View nearby branches with estimated wait times
- Rate service after completion

---

## Architecture

The original monolith (`backend/`) was fully decomposed into **independent microservices**, each with its own database schema, Dockerfile, and deploy unit. There is no shared codebase between services — small "shared-looking" classes (`SecurityConfig`, `HeaderAuthFilter`, `JwtPrincipal`, `RestTemplateConfig`, etc.) are deliberately **copied per service**, not imported from a common library, so each service stays independently deployable.

```
smart-queue-platform/
├── frontend/               # React + TypeScript + Tailwind + Zustand (nginx in prod)
├── admin-dashboard/        # Staff/admin React app (Vite dev server, port 8443)
├── api-gateway/            # Spring Cloud Gateway — the ONLY public entry point + Zero Trust enforcement
├── identity-service/       # Auth, JWT issuance (RSA/JWKS), OTP, WebAuthn, password reset
├── verification-service/   # Face verification orchestration (liveness gate + match call)
├── face-service/           # Python FastAPI — DeepFace face match + self-hosted liveness/anti-spoofing
├── branch-service/         # Branch reference data + live queue-count cache
├── queue-service/          # Join/cancel/advance tickets, QR code generation, STOMP broadcast
├── document-service/       # Document upload/list/delete + ML quality/classification calls
├── feedback-service/       # Post-visit ratings
├── catalog-service/        # Applicant types, services, fees, required documents
├── payment-service/        # Payment intents (outbox pattern), webhook handling
├── notification-gateway/   # WebSocket relay for payment/system notifications
├── ml-service/             # Python FastAPI — wait-time prediction (scikit-learn) + document AI (Claude)
├── docs/decisions/         # Architecture decision records
├── infra/                  # MySQL init script, OpenTelemetry collector config
├── http/, postman/         # API request collections for manual testing
├── docker-compose.yml      # Full stack (shared-server profile — the default)
├── docker-compose.dev.yml  # Local-dev overlay: extra host ports (gateway, MySQL, ml-service)
├── .env.example            # Template for .env (secrets — gitignored)
└── README.md
```

### Tech Stack

| Layer              | Technology                                                       |
|--------------------|-------------------------------------------------------------------|
| Frontend           | React 18, TypeScript, Tailwind CSS, Zustand, served via nginx     |
| Gateway            | Spring Cloud Gateway (WebFlux), reactive JWT verification          |
| Backend services   | Spring Boot 3.5, Spring Security, WebSocket/STOMP                  |
| Database           | MySQL 8 — schema-per-service in one shared instance; payment-service gets its own isolated instance |
| Cache / sessions   | Redis 7 — OTP, 2FA/face session TTLs, face embeddings, JWKS cache  |
| Messaging          | RabbitMQ — queue-service → branch-service live count events, payment events |
| Face service       | Python 3.11, FastAPI, DeepFace (Facenet512), self-hosted liveness (PyTorch, OpenCV/Caffe detector) |
| ML service         | Python 3.11, FastAPI, scikit-learn (wait-time), Anthropic Claude (document classification) |
| QR codes           | ZXing (`com.google.zxing:core`/`javase`) — real scannable PNGs, not placeholders |
| Observability      | OpenTelemetry Collector, Micrometer tracing                        |
| Realtime           | STOMP over WebSocket (SockJS fallback)                             |

---

## Zero Trust Security Architecture

Every protected route passes through **`api-gateway`'s `ZeroTrustGlobalFilter`** — the single place in the whole system that verifies a JWT and enforces the 2FA/face gates. No downstream service re-implements this; each just trusts the identity the Gateway forwards.

```
Browser (httpOnly dha_token cookie, sent automatically)
        ↓
api-gateway :: ZeroTrustGlobalFilter
        ↓
Gate 1: JWT valid (signature via JWKS, not expired)? ── NO ──→ 401 INVALID_TOKEN
        ↓ YES
Gate 2: Redis has 2fa:{userId}?      ── NO ──→ 403 REQUIRES_2FA
        ↓ YES
Gate 3: Redis has face:{userId}?     ── NO ──→ 403 REQUIRES_FACE
        ↓ YES
Gateway strips any client-supplied X-User-*/X-Gateway-Secret headers,
then re-adds its OWN verified X-User-Id / X-User-Email / X-User-Role
+ X-Gateway-Secret, and forwards to the target service
        ↓
Downstream service's HeaderAuthFilter: trusts X-User-* ONLY if
X-Gateway-Secret matches — otherwise 403, even if reached directly
```

**Why the shared secret matters:** every service in this stack has **no host port published** except the `frontend` nginx — every other service, the Gateway included, is reachable only over the Docker-internal network. But defense-in-depth means each service's `HeaderAuthFilter` *also* refuses to trust `X-User-*` headers unless `X-Gateway-Secret` (an env var only the Gateway and downstream services know) matches — so even a service reachable directly (e.g. in a future non-Docker deployment) can't be handed a forged identity.

### Session Lifetimes

| Session      | TTL       | Storage                                    |
|--------------|-----------|---------------------------------------------|
| JWT session  | 24 hours  | **httpOnly, Secure, SameSite=Lax cookie** (`dha_token`) — never in localStorage, never in a JS-readable response body |
| 2FA session  | 8 hours   | Redis `2fa:{userId}`                       |
| Face session | No TTL — until the next login (cleared by `identity-service` on login), bounded by the JWT | Redis `face:{userId}` |

The JWT moved from a localStorage token to an httpOnly cookie specifically so an XSS on the page can't exfiltrate it — the frontend never sees the token value at all; `AuthStore` only keeps a non-sensitive `dha_authed` localStorage flag to know whether to expect a session across reloads.

### User Onboarding Flow

```
Register (sets httpOnly cookie) → OTP Verify (grants 2FA) → SA ID Verify → Face Verify (grants face session) → /welcome
```

Login follows the same path: login → confirm SA ID → Face Verify. Every login needs a fresh face scan, but once verified, the face session doesn't expire on a timer.

**Home Affairs already holds the citizen's record, photo included.** The live scan is always compared against the photo on the citizen's Home Affairs record (`identity-service`, table `citizen_photos`, keyed by the SA ID on their account). Citizens never upload it, and a live scan never becomes the record. With no photo on the record, verification fails closed with `NO_DHA_PHOTO`.

**Not getting stuck:** the Face Verify page has a **Sign out** link, so a citizen who can't finish the scan can log in later and land straight back on Face Verify.

### Citizen Records dev tool (development only)

There's no real population register to connect to, so for development an admin loads citizen records and their ID photos through the **admin dashboard → Citizen Records (dev)** page (`admin@dha.gov.za`, ADMIN role only). The 10 seeded citizens (`CitizenRecordSeeder`) start without photos — upload one for the SA ID you test with, and that citizen can face-verify. In production this data would come from DHA's own systems; this screen and its `/api/auth/admin/citizen-records` endpoints are for development only.

### Face Verification & Liveness (self-hosted)

| Step | What runs it | Notes |
|------|---------------|-------|
| Liveness / anti-spoofing | `face-service` — vendored [Silent-Face-Anti-Spoofing](https://github.com/minivision-ai/Silent-Face-Anti-Spoofing) (Apache-2.0), PyTorch MiniFASNet ensemble + OpenCV/Caffe face detector | Passive, single-frame check. Originally called Luxand Cloud's API for this (see `docs/decisions/face-verification-vendor-selection.md`); replaced with this self-hosted model so no biometric frame leaves this infrastructure for liveness either, and there's no third-party quota to run out of. Still not independently certified against presentation attacks (same caveat the ADR already flagged for Luxand) — acceptable for a prototype, not for real citizen use without revisiting. |
| Face match | `face-service` — DeepFace (Facenet512), cosine distance ≤ 0.30 | Stays self-hosted per the ADR's original reasoning: the actual match decision is the one thing that should stay under this system's own control (auditability, no per-request vendor cost, no vendor lock-in on the critical path). |

`verification-service` orchestrates both calls; a failed liveness check fails the whole verification closed (never silently skipped).

---

## Ticket QR Codes

`queue-service` generates a real, scannable QR PNG (via ZXing) encoding each ticket's `qrCode` string (`DHA-<ticketNumber>-<random>`), returned as a `data:image/png;base64,...` URI in the ticket response (`QueueEntryResponse.qrCodeImage`). It's stripped from any branch-wide broadcast (`QueueService.broadcastQueueUpdate`) alongside the plain `qrCode` string — both are meant to be private to the ticket holder.

---

## Wait-Time Prediction (ML)

`ml-service`'s `/predict/wait-time` is a real trained model, not a heuristic:
- **Data**: [Asiri's hospital outpatient dataset](https://www.kaggle.com/code/abdulqaderasiirii/hospital-wait-time) (Kaggle) — 29,998 records, used as a structural proxy for a public-service queue (see `ml-service/scripts/train_wait_time.py` and the accompanying research report for the full rationale).
- **Features**: hour of arrival, day of week, month-end flag (all computed server-side from the current timestamp), doctor type + financial class (label-encoded), and `process_period` (estimated at inference time from a historical per-`doctor_type`/hour lookup table, since it isn't known until service actually completes).
- **Models compared**: KNN, Random Forest, Gradient Boosting — **Gradient Boosting won** (MAE ≈ 4.2 minutes, R² ≈ 0.89 on the held-out test split).
- **Training vs. serving split**: `ml-service/scripts/train_wait_time.py` is an **offline-only** script (reads the dataset, trains, saves `model.pkl`/`preprocessor.pkl`/`encoders.pkl`/a process-period lookup table to `ml-service/app/artifacts/`) — it is never imported by the running app. `ml-service/app/routers/wait_time.py` is the actual FastAPI router, which only ever loads those saved artifacts and serves predictions.
- **Known gap**: `queue-service` doesn't call this endpoint yet — it still computes its displayed wait time with a simple inline formula (`queueSize × avgServiceTime / activeCounters`). Wiring queue-service to actually call `ml-service` (and deciding what a DHA queue entry's `doctor_type`/`financial_class` equivalents should be) is still open.

---

## Prerequisites

- Docker + Docker Compose (this is the only supported way to run the full stack — see below)
- For editing individual services without Docker: Java 21+, Node.js 18+, Python 3.11+, MySQL 8, Redis 7, RabbitMQ 3

---

## Running the full stack (Docker)

```bash
cp .env.example .env        # then fill in the blanks (see Environment Variables)
docker compose up --build
```

This builds and starts all 18 containers: MySQL (shared + payment-isolated), Redis, RabbitMQ, the OpenTelemetry Collector, all 10 Spring Boot services, both Python services, and the frontend. Every container has a CPU/memory limit; the limits add up to ~7.9 GB so the stack fits an 8 GB cap.

**Only one port is published to the host** — the `frontend` nginx, which serves the SPA and proxies `/api/**` and `/ws/**` to `api-gateway` on the Docker network. It binds to `${BIND_ADDR:-127.0.0.1}:${FRONTEND_HOST_PORT:-5173}`.

Open **http://localhost:5173**.

### Local development overlay

The Vite dev servers (`frontend` on 5173, `admin-dashboard` on 8443) proxy to the Gateway at `localhost:8080`, and DB GUIs want MySQL on a host port. `docker-compose.dev.yml` publishes those (on 127.0.0.1 only):

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d
```

| Service | Host port |
|---|---|
| `api-gateway` | `8080` |
| `mysql` / `payment-mysql` | `3306` / `3307` |
| `ml-service` | `8001` |

Never use the dev overlay on the shared server.

---

## Deploying to the shared server (csposrgradserver1)

The server runs **rootless Docker** with an **8 GB RAM / 4 CPU** cap across all of the account's containers. Only the nginx frontend may publish a host port (8090–8199, opened by the admin for the campus network and VPN); no ports below 1024, no privileged containers, no host networking. Secrets go in `.env`, never in the Compose file.

```bash
ssh s4352859@172.21.12.21
git clone https://github.com/peacekhutso-dev/smart-queue-platform.git
cd smart-queue-platform
cp .env.example .env         # fill every blank: openssl rand -hex 24
ss -ltn | grep 8090          # confirm the port is free
COMPOSE_PARALLEL_LIMIT=1 docker compose build   # one image at a time — builds count toward the 8 GB cap
docker compose up -d
docker compose ps
```

Server `.env` must set `BIND_ADDR=0.0.0.0`, `FRONTEND_HOST_PORT=<the agreed port>` and `PUBLIC_ORIGIN=http://172.21.12.21:<port>` (drives CORS, redirects and the WebAuthn origin).

> **Camera and WebAuthn need a secure context.** Browsers only expose the camera (face scan) and WebAuthn (fingerprint) on HTTPS or `localhost`, and a WebAuthn RP ID can't be an IP address. Over plain `http://172.21.12.21:<port>` those steps fail. Until the server has a hostname + TLS, demo through an SSH tunnel and browse `http://localhost:<port>`:
>
> ```bash
> ssh -L 8090:localhost:8090 s4352859@172.21.12.21
> ```
>
> (With the tunnel, leave `PUBLIC_ORIGIN` at `http://localhost:8090` and `WEBAUTHN_RP_ID=localhost`.)

**Loading citizen photos on the server (dev tool):** the admin dashboard isn't part of the server stack. Run it on your laptop against the tunnelled server instead. nginx forwards `/api` to the Gateway, and `http://localhost:8443` is already an allowed WebAuthn origin:

```bash
ssh -L 8090:localhost:8090 s4352859@172.21.12.21     # tunnel, as above
cd admin-dashboard && BACKEND_URL=http://localhost:8090 npm run dev
```

Updating: `git pull && COMPOSE_PARALLEL_LIMIT=1 docker compose build && docker compose up -d`. Containers keep running after you log out.

---

## Environment Variables

Root `.env` (gitignored) — start from `.env.example`. `docker compose` refuses to start if a required secret is missing. Generate secrets with `openssl rand -hex 24` (hex only — `infra/mysql/init-service-dbs.sh` embeds the DB passwords in SQL, so they must not contain quotes).

```env
# Exposure — the only published host port (frontend nginx)
BIND_ADDR=127.0.0.1             # 0.0.0.0 on the shared server
FRONTEND_HOST_PORT=5173         # 8090–8199 on the shared server
PUBLIC_ORIGIN=http://localhost:5173   # the URL people type into the browser

# Databases — one password per service user, plus the isolated payment DB
DB_PASS=
IDENTITY_DB_PASS=
BRANCH_DB_PASS=
QUEUE_DB_PASS=
DOCUMENT_DB_PASS=
FEEDBACK_DB_PASS=
CATALOG_DB_PASS=
PAYMENT_DB_PASS=

JWT_EXPIRY=86400000
JWT_COOKIE_SECURE=false         # true once served over HTTPS (compose default: false)

# Zero Trust / dev bypasses — must be false for any real deployment
ZERO_TRUST_BYPASS=false
FACE_DEV_BYPASS=false

# Shared secret between the Gateway and every downstream service — generate
# a real value with `openssl rand -hex 32` for anything beyond local dev.
GATEWAY_SHARED_SECRET=dev-only-insecure-shared-secret-change-me

# Local dev only: makes /api/auth/send-otp return the OTP directly in its
# response (and log it), since no real SMS/email provider is wired up.
# Never set true in a real deployment.
OTP_DEV_EXPOSE=true

TWO_FA_TTL_HOURS=8

WEBAUTHN_RP_ID=localhost
WEBAUTHN_ORIGINS=               # defaults to PUBLIC_ORIGIN + http://localhost:8443

ANTHROPIC_API_KEY=              # ml-service's document classification (Claude)
```

> **Getting an OTP in local dev**: with `OTP_DEV_EXPOSE=true`, `POST /api/auth/send-otp`'s response body carries the code directly (`data` field), and it's also logged (`docker compose logs identity-service | grep "OTP for"`). No SMS/email provider is configured in this prototype.

---

## Database Setup

Each service owns its own schema in the shared MySQL instance (`identity_db`, `branch_db`, `queue_db`, `document_db`, `feedback_db`, `catalog_db`), except `payment-service`, which gets its own fully isolated MySQL instance/credentials (tighter audit/access policy for payment data). Schemas are created via Hibernate `ddl-auto: update` plus `infra/mysql/init-service-dbs.sh` for the initial per-schema users (the `*_DB_PASS` values from `.env`) — **that init script only runs on first init of an empty `mysql_data` volume**; if you add a new schema/user block later against an already-initialized volume, run it manually:

```bash
docker compose exec mysql sh /docker-entrypoint-initdb.d/init-service-dbs.sh
```

---

## Running a single service without Docker

Each Spring Boot service is a standalone Maven project:

```bash
cd identity-service   # or any other service
mvn clean install -DskipTests
mvn spring-boot:run
```

It'll need its dependencies (MySQL/Redis/RabbitMQ) reachable at `localhost` with the ports/credentials matching that service's `application.yml` defaults, and `GATEWAY_SHARED_SECRET` set to match whatever `api-gateway` (or your test client) sends.

**Python services**:
```bash
cd face-service   # or ml-service
python -m venv venv && source venv/bin/activate
pip install -r requirements.txt
python main.py                          # face-service
uvicorn app.main:app --reload --port 8001   # ml-service
```
> `face-service`'s first run downloads the Facenet512 weights (~95 MB, baked into the Docker image at build time already).

**Frontend**:
```bash
cd frontend
npm install
npm run dev
```

---

## API Endpoints

All routes below go through the frontend's nginx as same-origin `/api/...` (or straight to `api-gateway` at `http://localhost:8080` with the dev overlay). The Gateway routes each prefix to its owning service — see `api-gateway/src/main/resources/application.yml` for the authoritative table.

### Auth — `identity-service`
| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/auth/register` | Register, sets `dha_token` cookie |
| POST | `/api/auth/login` | Login, sets `dha_token` cookie |
| POST | `/api/auth/logout` | Clears the cookie |
| POST | `/api/auth/send-otp` | Send OTP (dev: returned in response) |
| POST | `/api/auth/verify-otp` | Verify OTP → grants 2FA session |
| POST | `/api/auth/verify-id` | Validate + store SA ID number |
| GET  | `/api/auth/me` | Current user profile |
| GET  | `/.well-known/jwks.json` | RSA public key, used by other services to verify JWTs directly (STOMP CONNECT frames) |
| POST | `/api/auth/webauthn/register/options` / `/register/verify` / `/authenticate/options` / `/authenticate/verify` | WebAuthn/passkey support |
| GET / PUT | `/api/auth/admin/citizen-records` | **Dev only, ADMIN.** List / create-or-update Home Affairs citizen records |
| PUT / GET / DELETE | `/api/auth/admin/citizen-records/{saId}/photo` | **Dev only, ADMIN.** Upload (multipart, JPG/PNG ≤5MB) / view / remove the record's ID photo |

### Face verification — `verification-service` (→ `face-service`)
| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/auth/face/verify` | Live capture → liveness check → face match → grants face session |

### Branches — `branch-service`
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/branches` | List all branches |
| GET | `/api/branches/{id}` | Branch details |
| GET | `/api/branches/nearby` | Branches near a location |
| GET | `/api/branches/province/{province}` | Branches in a province |

### Queue — `queue-service`
| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/queue/join` | Join a queue, returns ticket incl. QR code |
| GET | `/api/queue/my-ticket` | Current ticket status |
| DELETE | `/api/queue/{ticketId}/cancel` | Cancel a queue entry |
| GET | `/api/queue/entries/{entryId}` | Single entry lookup |
| GET | `/api/queue/branch/{branchId}` | Branch queue snapshot |
| GET | `/api/queue/branch/{branchId}/stats` | Branch queue stats |
| GET | `/api/queue/history` | Caller's past tickets |
| PUT | `/api/queue/{entryId}/advance` | Staff: call/advance a ticket |

### Documents — `document-service`
| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/documents/upload` | Upload (multipart), runs ML quality/classification checks |
| GET | `/api/documents/my-docs` | List caller's documents |
| DELETE | `/api/documents/{id}` | Delete a document |

### Feedback — `feedback-service`
| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/feedback` | Submit post-visit feedback |

### Catalog — `catalog-service`
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/catalog` | Full catalog |
| GET | `/api/catalog/applicant-types` | Applicant types |
| GET | `/api/catalog/applicant-types/{code}/services` | Services for an applicant type |
| GET | `/api/catalog/services/{code}` | Service detail (incl. fee) |
| GET | `/api/catalog/services/{code}/documents` | Required documents for a service |
| POST/PUT/DELETE | `/api/catalog/applicant-types`, `/services`, `/documents` | Admin management endpoints |

### Payments — `payment-service`
| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/payments/intents` | Create a payment intent |
| GET | `/api/payments/{id}` | Payment status |
| POST | `/api/payments/webhooks/{provider}` | Provider webhook (exempt from the Zero Trust gate — auth is the provider's own signature) |

---

## WebSocket Topics

Two separate STOMP endpoints, both authenticated via the `dha_token` httpOnly cookie during the handshake (not a STOMP CONNECT header — the Gateway can't inspect inside the STOMP protocol, and JS can't read an httpOnly cookie to set one anyway):

| Endpoint | Service | Topic | Description |
|---|---|---|---|
| `/ws` | `queue-service` | `/topic/branch/{branchId}/queue` | Live queue updates for a branch (open to any authenticated citizen) |
| `/ws` | `queue-service` | *(per-connection)* | A SUBSCRIBE to a user-scoped topic is rejected unless it matches the connecting principal's own `userId` (or they're STAFF/ADMIN) |
| `/ws/notifications` | `notification-gateway` | `/topic/user/{userId}/payment-status` | Payment status updates, same per-user subscription check |

---

## SA ID Validation Logic

South African ID numbers follow `YYMMDD SSSS C A Z`:
- `YYMMDD` — Date of birth
- `SSSS` — Gender (0000–4999 Female, 5000–9999 Male)
- `C` — Citizenship (0 = SA, 1 = Permanent Resident)
- `Z` — Luhn checksum digit

---

## Queue State Machine

```
WAITING → CALLED → SERVING → COMPLETE
               ↓
            CANCELLED
```

Ticket numbers reset daily per branch — `queue_entries` has a unique constraint on `(branch_id, ticket_date, ticket_number)`, and concurrent joins that race for the same next number are retried in a fresh transaction (`TicketClaimService`) rather than allowed to silently collide.

---

## Known Follow-ups (not yet done)

- `queue-service` doesn't call `ml-service`'s trained wait-time model yet — still uses an inline formula (see [Wait-Time Prediction](#wait-time-prediction-ml) above).
- Liveness (self-hosted Silent-Face-Anti-Spoofing) and face match (DeepFace) are both passive/uncertified against presentation attacks — see `docs/decisions/face-verification-vendor-selection.md` for the tradeoffs and what a real deployment would need to revisit.
- `frontend/.env`'s `VITE_GOOGLE_MAPS_API_KEY` is committed in git history — lower severity since client-side Maps keys are normally referrer-restricted rather than secret, but worth confirming/rotating.
- No Flyway/Liquibase — schema changes rely on Hibernate `ddl-auto: update`, which has real limitations against already-populated tables (a `NOT NULL` column addition without a DB-side default can silently fail on non-empty tables under MySQL strict mode — see `queue-service`'s `QueueEntry.ticketDate` for the fix pattern: give it an explicit `columnDefinition` default).

---

## Deployment Checklist

- [ ] Generate a real `GATEWAY_SHARED_SECRET` and every `*_DB_PASS` (`openssl rand -hex 24`), not the dev values
- [ ] Get a hostname + TLS certificate so the camera and WebAuthn work without an SSH tunnel
- [ ] Set `ZERO_TRUST_BYPASS=false`, `FACE_DEV_BYPASS=false`, `OTP_DEV_EXPOSE=false`
- [ ] Set `JWT_COOKIE_SECURE=true` and serve everything over real HTTPS/TLS
- [ ] Replace Hibernate `ddl-auto: update` with `validate` + real migrations (Flyway/Liquibase)
- [ ] Configure Redis with an AUTH password
- [ ] Wire up a real SMS/email provider for OTP delivery (Africa's Talking / Twilio)
- [ ] Lock CORS to the production frontend domain only (currently `http://localhost:*` + configured `FRONTEND_URL`)
- [ ] Replace the Citizen Records dev tool with a real population-register integration
- [ ] Revisit the liveness/face-match vendor choice for iBeta/ISO 30107-3 certification before real citizen use
- [ ] Rotate/verify referrer-restriction on the Google Maps API key

---

## Compliance

- **POPIA** (Protection of Personal Information Act)-aware design: biometric data minimized (only 512-dim face embeddings persisted, never raw images; liveness frames aren't persisted at all now that the check is self-hosted, vs. previously being sent to a third-party API)
- Zero Trust means even a stolen JWT cookie cannot act without also passing the 2FA and face gates
- Every downstream service independently verifies `X-Gateway-Secret` before trusting forwarded identity headers, so a service reachable directly (bypassing the Gateway) can't be handed a forged identity

---

## Third-Party / Vendored Code

- **QR codes**: [ZXing](https://github.com/zxing/zxing) (`com.google.zxing:core`/`javase`), Apache-2.0
- **Face liveness**: [Silent-Face-Anti-Spoofing](https://github.com/minivision-ai/Silent-Face-Anti-Spoofing) (MiniVision AI), Apache-2.0 — vendored into `face-service/liveness/`, see `face-service/liveness/NOTICE.md`
- **Face matching**: [DeepFace](https://github.com/serengil/deepface) (Facenet512 model)
- **Wait-time dataset**: [Hospital wait time](https://www.kaggle.com/code/abdulqaderasiirii/hospital-wait-time) (A. Asiri, Kaggle)

---

## License

Academic / University Submission — Department of Home Affairs Smart Queue Platform
Built for: University Submission — Smart Systems
Year: 2026
