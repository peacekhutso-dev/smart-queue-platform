# 🇿🇦 Smart Queue Platform — DHA eHomeAffairs Style

> A production-ready South African smart queue management system inspired by the Department of Home Affairs (DHA), SARS eFiling, and Discovery Health portals.

---

## Overview

This platform enables South African citizens to:
- Authenticate securely with OTP verification
- Verify identity using SA ID numbers
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
├── ml-service/        # Python FastAPI (wait-time + OCR)
├── docker-compose.yml
├── .env
└── README.md
```

### Tech Stack

| Layer       | Technology                                    |
|-------------|-----------------------------------------------|
| Frontend    | React 18, TypeScript, Tailwind CSS, Zustand   |
| Backend     | Spring Boot 3, Spring Security, JWT, WebSocket|
| Database    | MySQL 8                                       |
| Cache       | Redis 7                                       |
| ML Service  | Python 3.11, FastAPI, Uvicorn                 |
| Realtime    | STOMP over WebSocket                          |

---

## Prerequisites

- Java 17+
- Node.js 18+
- Python 3.11+
- Docker + Docker Compose (recommended)
- MySQL 8
- Redis 7

---

## Environment Variables

Create `.env` in the root:

```env
# Database
DB_HOST=localhost
DB_PORT=3306
DB_NAME=smart_queue
DB_USER=root
DB_PASS=yourpassword

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# JWT
JWT_SECRET=your-256-bit-secret-key-here-change-this-in-production
JWT_EXPIRY=86400000

# ML Service
ML_SERVICE_URL=http://localhost:8001

# CORS
FRONTEND_URL=http://localhost:5173
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
cp ../. env .env
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

Frontend runs on **http://localhost:5173**

---

## ML Service Setup (FastAPI)

```bash
cd ml-service
python -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8001
```

ML service runs on **http://localhost:8001**

---

## Docker (Full Stack)

```bash
docker-compose up --build
```

This starts MySQL, Redis, Backend, Frontend, and ML service.

---

## API Endpoints

### Auth
| Method | Endpoint                      | Description              |
|--------|-------------------------------|--------------------------|
| POST   | `/api/auth/register`          | Register new citizen     |
| POST   | `/api/auth/login`             | Login, receive JWT       |
| POST   | `/api/auth/send-otp`          | Send OTP to phone/email  |
| POST   | `/api/auth/verify-otp`        | Verify OTP code          |
| POST   | `/api/auth/verify-id`         | Validate SA ID number    |

### Users
| Method | Endpoint                      | Description              |
|--------|-------------------------------|--------------------------|
| GET    | `/api/users/me`               | Get current user profile |
| PUT    | `/api/users/me`               | Update profile           |

### Branches
| Method | Endpoint                      | Description              |
|--------|-------------------------------|--------------------------|
| GET    | `/api/branches`               | List all branches        |
| GET    | `/api/branches/{id}`          | Branch details + wait    |
| GET    | `/api/branches/nearby`        | Branches near location   |

### Queue
| Method | Endpoint                          | Description              |
|--------|-----------------------------------|--------------------------|
| POST   | `/api/queue/join`                 | Join a queue             |
| GET    | `/api/queue/my-ticket`            | Current ticket status    |
| DELETE | `/api/queue/{ticketId}/cancel`    | Cancel queue entry       |
| GET    | `/api/queue/branch/{branchId}`    | Branch queue snapshot    |

### Documents
| Method | Endpoint                      | Description              |
|--------|-------------------------------|--------------------------|
| POST   | `/api/documents/upload`       | Upload document          |
| GET    | `/api/documents/my-docs`      | List user documents      |
| DELETE | `/api/documents/{id}`         | Delete document          |

### Feedback
| Method | Endpoint                      | Description              |
|--------|-------------------------------|--------------------------|
| POST   | `/api/feedback`               | Submit service feedback  |

---

## WebSocket Topics

Connect to: `ws://localhost:8080/ws`

| Topic                              | Description                          |
|------------------------------------|--------------------------------------|
| `/topic/branch/{branchId}/queue`   | Live queue updates for a branch      |
| `/topic/user/{userId}/status`      | Personal ticket status updates       |
| `/app/queue/join`                  | Send: join queue action              |

---

## ML Service Endpoints

| Method | Endpoint                  | Description                    |
|--------|---------------------------|--------------------------------|
| POST   | `/predict/wait-time`      | Predict wait time for queue    |
| POST   | `/validate/document`      | Validate uploaded document     |
| GET    | `/health`                 | ML service health check        |

### Wait Time Calculation (Lightweight)
Currently computed as:
```
wait_minutes = (queue_size × avg_service_time_minutes) / active_counters
```
Designed to be swapped with a trained regression model in Phase 3.

### Document Validation (Lightweight)
Currently:
1. MIME type validation (PDF, JPG, PNG)
2. File size check (max 10MB)
3. SA ID regex pattern check on extracted text
4. Certified copy keyword detection

Designed for full OCR/CV pipeline in Phase 3.

---

## Placeholder Services Explained

| Service              | Current Implementation       | Future Replacement              |
|----------------------|------------------------------|---------------------------------|
| Wait Time            | Formula-based calculation    | ML regression model             |
| Document OCR         | MIME + regex validation      | Tesseract / AWS Textract        |
| Face Verification    | Camera capture only          | DeepFace / AWS Rekognition      |
| SMS OTP              | Console log (dev mode)       | Twilio / Africa's Talking       |
| Push Notifications   | WebSocket only               | Firebase FCM                    |

---

## SA ID Validation Logic

South African ID numbers follow `YYMMDD SSSS C A Z`:
- `YYMMDD` — Date of birth
- `SSSS` — Gender (0000–4999 Female, 5000–9999 Male)
- `C` — Citizenship (0 = SA, 1 = Permanent Resident)
- `A` — Usually 8 (old), 0 (new)
- `Z` — Luhn checksum digit

Validation uses the Luhn algorithm.

---

## Queue State Machine

```
WAITING → CALLED → SERVING → COMPLETE
                ↓
             CANCELLED
```

---

## Future ML Integration Notes

- **Phase 3**: Train XGBoost or LightGBM model on historical queue data
- **Features**: time_of_day, day_of_week, branch_capacity, active_counters, service_type
- **Output**: predicted_wait_minutes (regression)
- **Deployment**: Replace `/predict/wait-time` endpoint with model inference
- **Face Verification**: Integrate DeepFace library with stored biometric templates

---

## Deployment Notes

### Production Checklist
- [ ] Set strong `JWT_SECRET` (min 256-bit)
- [ ] Use SSL/TLS (reverse proxy via Nginx)
- [ ] Replace `ddl-auto: update` with `validate` + Flyway migrations
- [ ] Configure Redis with AUTH password
- [ ] Set up Africa's Talking or Twilio for real SMS OTP
- [ ] Configure Firebase FCM for push notifications
- [ ] Enable CORS only for production domain
- [ ] Set up log aggregation (ELK stack or CloudWatch)

---

## Compliance

- **POPIA** (Protection of Personal Information Act) compliant architecture
- All biometric data encrypted at rest
- User consent required before data collection
- Data deletion endpoints available

---

## Screenshots

See `/docs/screenshots/` for UI reference mockups.

---

## License

Academic / University Submission — Department of Home Affairs Smart Queue Platform  
Built for: University Submission — Smart Systems  
Year: 2024