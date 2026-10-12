# DHA Smart Queue — Admin Dashboard

Counter-staff and admin dashboard for [smart-queue-platform](https://github.com) — a DHA-style
citizen queue management system. This app is the staff-facing companion to the citizen frontend:
it consumes the **same Spring Boot backend** and follows the **same Zero Trust security
architecture**, just with fingerprint verification in place of face verification (see below).

## Stack

| Layer         | Technology                                              |
|---------------|----------------------------------------------------------|
| Framework     | React 19, TypeScript, Vite                                |
| Styling       | Tailwind CSS v4                                           |
| Routing       | React Router 7                                            |
| State         | Zustand                                                   |
| HTTP          | Axios (JWT + Zero Trust response interceptor)             |
| Realtime      | STOMP over WebSocket (`@stomp/stompjs` + `sockjs-client`) |
| Biometric     | WebAuthn platform authenticator (`@simplewebauthn/browser`) — Touch ID / Windows Hello / Android fingerprint |
| Icons         | lucide-react                                              |
| Backend       | smart-queue-platform's Spring Boot API (not part of this repo) |

## Zero Trust security (shared with the citizen app)

Every protected API call passes three gates enforced by the backend's `ZeroTrustFilter`:

1. **JWT** — valid, unexpired bearer token
2. **2FA** — an OTP session (`2fa:{userId}` in Redis)
3. **Biometric** — role-aware:
   - `CITIZEN` → face verification (DeepFace, citizen app only)
   - `STAFF` / `ADMIN` → **fingerprint** verification (WebAuthn), since staff terminals don't do
     face enrollment

The frontend mirrors this exactly: `src/api/Axios.ts` attaches the JWT and reacts to
`401`/`403 REQUIRES_2FA`/`403 REQUIRES_FINGERPRINT` the same way the citizen app's interceptor
reacts to `REQUIRES_FACE`, and `src/store/AuthStore.ts` tracks the same session TTLs (2FA: 8h,
fingerprint: 4h).

Login flow: `/login` (email + password) → `/verify-otp` (OTP, currently logged to the backend
console — no SMS provider configured in dev) → `/verify-fingerprint` (enroll on first login,
verify on return visits) → dashboard.

## Development

A backend, MySQL, and Redis instance must be running (see smart-queue-platform's own README —
it's normally run via `docker-compose up -d` from that repo). This app proxies `/api` and `/ws`
to `http://localhost:8080` by default; override with `BACKEND_URL` if the backend runs elsewhere.

```bash
pnpm install
pnpm dev
```

Demo accounts (seeded by the backend's `DataSeeder`):

| Role  | Email               | Password       |
|-------|---------------------|----------------|
| STAFF | staff@dha.gov.za     | Staff@12345    |
| ADMIN | admin@dha.gov.za     | Admin@12345    |

## Key files

- `src/App.tsx` — routes (`/login`, `/verify-otp`, `/verify-fingerprint`, protected `/`)
- `src/components/layout/ProtectedLayout.tsx` — Zero Trust route gates
- `src/api/Axios.ts` / `src/api/Api.ts` — HTTP client + Zero Trust interceptor + API services
- `src/store/AuthStore.ts` / `src/store/BranchStore.ts` / `src/store/QueueStore.ts` — app state
- `src/pages/DashboardPage.tsx` — the counter dashboard (stats, now-serving, queue table)

See `AGENTS.md` for the Figma Make sandbox/dev-server details.
