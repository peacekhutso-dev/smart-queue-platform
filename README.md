# 🇿🇦 Smart Queue Platform — DHA eHomeAffairs Style

> A production-ready South African smart queue management system inspired by the Department of Home Affairs (DHA), with **Zero Trust security** (JWT + 2FA + Face Verification on every request).

---

## Overview

This platform enables South African citizens to:
- Authenticate securely with OTP verification
- Verify identity using SA ID numbers
- **Enrol and verify their face via DeepFace biometrics**
- Join virtual queues at DHA branches
- Receive real-time queue status via WebSocket
- Upload and validate documents before branch visits
- Chat with a DHA Virtual Assistant
- View nearby branches with estimated wait times
- Rate service after completion

---

## Architecture

```
smart-queue-platform/
├── frontend/          # React + TypeScript + Tailwind CSS
├── backend/           # Spring Boot + Java + JWT + WebSocket
├── face-service/      # Python FastAPI + DeepFace (Facenet512)
├── ml-service/        # Python FastAPI (wait-time prediction)
├── docker-compose.yml
├── .env
└── README.md
```

### Tech Stack

| Layer        | Technology                                                  |
|--------------|-------------------------------------------------------------|
| Frontend     | React 18, TypeScript, Tailwind CSS, Zustand                 |
| Backend      | Spring Boot 3.5, Spring Security, JWT, WebSocket            |
| Database     | MySQL 8                                                     |
| Cache        | Redis 7                                                     |
| Face Service | Python 3.11, FastAPI, DeepFace (Facenet512), OpenCV         |
| ML Service   | Python 3.11, FastAPI, Uvicorn                               |
| Realtime     | STOMP over WebSocket                                        |

---

## Zero Trust Security Architecture

Every protected API call passes **three sequential gates**:

```
React makes API call
        ↓
Spring Boot ZeroTrustFilter intercepts
        ↓
Gate 1: JWT valid?            ──── NO ──→  401 Unauthorized
        ↓ YES
Gate 2: Redis has 2fa:{userId}? ── NO ──→  403 + redirect to 2FA
        ↓ YES
Gate 3: Redis has face:{userId}? ─ NO ──→  403 + open face scan modal
        ↓ YES (and not expired)
API call proceeds ✅
```

### Session Lifetimes

| Session      | TTL       | Storage           |
|--------------|-----------|-------------------|
| JWT token    | 24 hours  | localStorage      |
| 2FA session  | 8 hours   | Redis `2fa:{uid}` |
| Face session | 4 hours   | Redis `face:{uid}`|

### Frontend Enforcement

`ProtectedLayout` checks session validity client-side before any page load:

```
isAuthenticated?  →  NO  → /login
isTwoFaValid()?   →  NO  → /verify-otp?session=true
isFaceValid()?    →  NO  → /verify-face?mode=verify
```

The Axios response interceptor also handles mid-session expiry:
- `403 REQUIRES_2FA` → logout + redirect to `/login?reason=session_expired`
- `403 REQUIRES_FACE` → open `FaceScanModal` inline, retry the original request on success

### User Onboarding Flow

```
Register → OTP Verify (sets 2fa:{uid}) → SA ID Verify → Face Enrol (sets face:{uid}) → Home ✅
```

### Session Renewal Flow (returning users)

```
Login → ProtectedLayout → /verify-otp?session=true → /verify-face?mode=verify → Home ✅
```

### Face Verification Service

Built with **DeepFace (Facenet512)** + cosine similarity:

| Endpoint     | Description                                          |
|--------------|------------------------------------------------------|
| `POST /enroll` | Extract 512-dim embedding from image, store in Redis |
| `POST /verify` | Compare live capture vs stored embedding             |
| `GET  /health` | Health check                                         |

- Threshold: cosine distance ≤ **0.30** (strict mode)
- Embeddings stored in Redis key: `face-embedding:{userId}`
- Spring Boot calls face service and, on success, writes `face:{userId}` (4h TTL) to Redis

---

## Prerequisites

- Java 21+
- Node.js 18+
- Python 3.11+
- MySQL 8
- Redis 7 (see setup below)
- Docker + Docker Compose (optional — for containerised deployments)

---

## Environment Variables

Create `.env` in the root:

```env
# Database
DB_HOST=localhost
DB_PORT=3306
DB_NAME=smart_queue
DB_USER=dha_user
DB_PASS=yourpassword

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# JWT
JWT_SECRET=your-256-bit-secret-key-here-change-this-in-production
JWT_EXPIRY=86400000

# ML Service
ML_SERVICE_URL=http://localhost:8001

# Face Verification Service (DeepFace)
FACE_SERVICE_URL=http://localhost:8002
FACE_DEV_BYPASS=false          # Set true to skip face checks in local dev
FACE_SESSION_TTL_HOURS=4
TWO_FA_TTL_HOURS=8

# CORS
FRONTEND_URL=http://localhost:5173
```

> Set `FACE_DEV_BYPASS=true` during local development if you don't want to run the face service.

---

## Redis Setup

### Linux / macOS
```bash
sudo apt install redis-server   # Ubuntu/Debian
brew install redis              # macOS
redis-server
```

### Windows (no Docker)
Redis is not officially supported on Windows. Use the community Windows port:

```powershell
# Download portable binary (one-time)
$dir = "$env:LOCALAPPDATA\Redis"
New-Item -ItemType Directory -Force $dir | Out-Null
Invoke-WebRequest -Uri "https://github.com/tporadowski/redis/releases/download/v5.0.14.1/Redis-x64-5.0.14.1.zip" -OutFile "$dir\redis.zip"
Expand-Archive "$dir\redis.zip" -DestinationPath $dir -Force

# Start Redis (run this every time before starting the backend)
& "$env:LOCALAPPDATA\Redis\redis-server.exe"
```

To install as a Windows service (requires admin, run once):
```powershell
& "$env:LOCALAPPDATA\Redis\redis-server.exe" --service-install redis.windows.conf --service-name Redis
Start-Service Redis
```

Verify Redis is running:
```bash
redis-cli ping   # should return PONG
```

---

## Database Setup (MySQL)

```sql
CREATE DATABASE smart_queue CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'dha_user'@'localhost' IDENTIFIED BY 'yourpassword';
GRANT ALL PRIVILEGES ON smart_queue.* TO 'dha_user'@'localhost';
FLUSH PRIVILEGES;
```

Tables are auto-created by Hibernate on startup (`ddl-auto: update`).

---

## Backend Setup (Spring Boot)

```bash
cd backend
mvn clean install -DskipTests
mvn spring-boot:run
```

Backend runs on **http://localhost:8080**

---

## Frontend Setup (React)

```bash
cd frontend
npm install
npm run dev
```

Frontend runs on **http://localhost:3000**

---

## Startup Order

Services must start in this order to avoid connection errors:

```
1. MySQL       (must be running before backend starts)
2. Redis       (must be running before backend starts)
3. Backend     mvn spring-boot:run           → http://localhost:8080
4. Face Service  python main.py              → http://localhost:8002
5. ML Service    uvicorn app.main:app ...    → http://localhost:8001
6. Frontend    npm run dev                   → http://localhost:3000
```

> The face service and ML service are optional for local dev — set `FACE_DEV_BYPASS=true` in `.env` to skip face checks without running the Python services.

---

## Face Service Setup (DeepFace)

```bash
cd face-service
python -m venv venv
# Windows:
venv\Scripts\activate
# macOS/Linux:
source venv/bin/activate

pip install -r requirements.txt
python main.py
```

Face service runs on **http://localhost:8002**

> First run downloads the Facenet512 model (~90 MB). Subsequent starts are instant.

---

## ML Service Setup (FastAPI)

```bash
cd ml-service
python -m venv venv
source venv/bin/activate   # or venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8001
```

---

## Docker (Full Stack)

```bash
docker-compose up --build
```

Starts MySQL, Redis, Backend, Frontend, Face Service, and ML service.

---

## API Endpoints

### Auth
| Method | Endpoint                        | Description                    |
|--------|---------------------------------|--------------------------------|
| POST   | `/api/auth/register`            | Register new citizen           |
| POST   | `/api/auth/login`               | Login, receive JWT             |
| POST   | `/api/auth/send-otp`            | Send OTP to phone/email        |
| POST   | `/api/auth/verify-otp`          | Verify OTP → grants 2FA session|
| POST   | `/api/auth/verify-id`           | Validate SA ID number          |
| POST   | `/api/auth/face/enroll`         | Enrol face (first-time setup)  |
| POST   | `/api/auth/face/verify`         | Verify face → grants face session |

### Users
| Method | Endpoint       | Description              |
|--------|----------------|--------------------------|
| GET    | `/api/auth/me` | Get current user profile |

### Branches
| Method | Endpoint               | Description              |
|--------|------------------------|--------------------------|
| GET    | `/api/branches`        | List all branches        |
| GET    | `/api/branches/{id}`   | Branch details + wait    |
| GET    | `/api/branches/nearby` | Branches near location   |

### Queue
| Method | Endpoint                           | Description              |
|--------|------------------------------------|--------------------------|
| POST   | `/api/queue/join`                  | Join a queue             |
| GET    | `/api/queue/my-ticket`             | Current ticket status    |
| DELETE | `/api/queue/{ticketId}/cancel`     | Cancel queue entry       |
| GET    | `/api/queue/branch/{branchId}`     | Branch queue snapshot    |

### Documents
| Method | Endpoint                   | Description              |
|--------|----------------------------|--------------------------|
| POST   | `/api/documents/upload`    | Upload document          |
| GET    | `/api/documents/my-docs`   | List user documents      |
| DELETE | `/api/documents/{id}`      | Delete document          |

### Feedback
| Method | Endpoint        | Description              |
|--------|-----------------|--------------------------|
| POST   | `/api/feedback` | Submit service feedback  |

---

## Zero Trust Error Codes

| HTTP | Code              | Frontend Action                          |
|------|-------------------|------------------------------------------|
| 401  | `INVALID_TOKEN`   | Logout + redirect to `/login`            |
| 403  | `REQUIRES_2FA`    | Logout + redirect to `/login?reason=session_expired` |
| 403  | `REQUIRES_FACE`   | Open `FaceScanModal`, retry on success   |
| 400  | `NO_ENROLLMENT`   | Redirect to `/verify-face?mode=enroll`   |

---

## WebSocket Topics

Connect to: `ws://localhost:8080/ws`

| Topic                               | Description                          |
|-------------------------------------|--------------------------------------|
| `/topic/branch/{branchId}/queue`    | Live queue updates for a branch      |
| `/topic/user/{userId}/status`       | Personal ticket status updates       |

---

## Face Service Endpoints

| Method | Endpoint   | Description                        |
|--------|------------|------------------------------------|
| POST   | `/enroll`  | Store reference face embedding     |
| POST   | `/verify`  | Verify face vs stored embedding    |
| GET    | `/health`  | Service + Redis health check       |

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

---

## Deployment Checklist

- [ ] Set strong `JWT_SECRET` (min 256-bit random)
- [ ] Set `FACE_DEV_BYPASS=false` in production
- [ ] Use SSL/TLS (reverse proxy via Nginx)
- [ ] Replace `ddl-auto: update` with `validate` + Flyway migrations
- [ ] Configure Redis with AUTH password
- [ ] Set up Africa's Talking or Twilio for real SMS OTP
- [ ] Lock CORS to production domain only
- [ ] Enable face session revocation on suspicious activity

---

## Compliance

- **POPIA** (Protection of Personal Information Act) compliant architecture
- Biometric embeddings encrypted at rest in Redis
- Face images never persisted — only 512-dim vectors stored
- Zero Trust ensures even a stolen JWT cannot access the system without 2FA + face

---

## License

Academic / University Submission — Department of Home Affairs Smart Queue Platform  
Built for: University Submission — Smart Systems  
Year: 2026
