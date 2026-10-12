# Testing the backend with Postman

This folder has a Postman collection that walks through the platform's Zero
Trust flow end to end — register → OTP → SA ID → document upload → face
verify → protected routes — against `api-gateway` on `localhost:8080`.

Files:
- `smart-queue.postman_collection.json` — the requests
- `local.postman_environment.json` — `baseUrl` + the variables the requests
  fill in for each other as you go (`email`, `otp`)

---

## 1. Prerequisites

Start the full stack from the repo root:

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build   # dev overlay publishes the gateway on :8080
```

Wait until `api-gateway` (`:8080`) is up — it's the only host you'll talk to;
everything else is reached through it.

## 2. Import into Postman

1. Open Postman → **Import** (top left).
2. Drag in both `smart-queue.postman_collection.json` and
   `local.postman_environment.json`, or select them via the file picker.
3. Top-right corner, the environment dropdown (defaults to "No Environment")
   → select **local**.

## 3. Run the requests, in order

Open the collection in the sidebar and run each request top to bottom with
the blue **Send** button. They're numbered because each one depends on state
the previous one created — don't skip around on a first run.

| # | Request | What it does |
|---|---|---|
| 1 | Register | Creates a fresh account (unique email generated automatically), sets the `dha_token` session cookie. This is **Gate 1** (valid JWT). |
| 1b | Login | Alternative to Register — use this on any run *after* the first, so you don't create a new account every time. Reuses `{{email}}` from whatever Register last set. |
| 2 | Send OTP | Dev mode returns the OTP directly in the response body; the test script saves it into `{{otp}}` automatically. |
| 3 | Verify OTP | Submits `{{otp}}`. This grants **Gate 2** (2FA session, 8h). |
| 4 | Verify SA ID | Submits a Luhn-valid sample SA ID number. |
| 5 | Face Verify | **Manual step**: paste a base64-encoded photo over `PASTE_BASE64_IMAGE_HERE` in the body (see below). This grants **Gate 3** (face session, until the next login). |
| 6 | Get current user | Confirms all three gates are open — a fully protected route. |
| 7 | List branches | A real business endpoint, no special setup needed. |
| 8 | Join queue | Uses `branchId: 1` by default — swap for a real ID from step 7's response if your seed data differs. |
| 9 | Logout | Clears the session cookie. |

You do **not** need to set any cookies or auth headers by hand — Postman's
cookie jar picks up `dha_token` from step 1/1b's response automatically and
sends it on every request after that in the same environment.

### Getting a base64 image for step 5 (Mac)

```bash
base64 -i photo.jpg | pbcopy
```

Then paste over `PASTE_BASE64_IMAGE_HERE` in step 5's request body.

### Getting an actual face match

Face Verify checks your live scan against the photo on the citizen's **Home
Affairs record** (identity-service), matched by the SA ID on your account.
Citizens never upload it. Before step 5, sign in to the admin dashboard as
`admin@dha.gov.za`, open **Citizen Records (dev)**, and upload a photo for the
SA ID you registered with. Without one, step 5 correctly returns `400` with
code `NO_DHA_PHOTO`. That's the right, testable behaviour, not a bug.

## 4. Re-running after the first pass

- Use **1b. Login** instead of **1. Register**.
- `{{email}}` persists in the `local` environment between runs, so Login
  keeps working against the same account until you manually clear it or run
  Register again.
- 2FA and face sessions expire (8h / 4h) — if enough time has passed since
  your last run, you'll need to redo steps 2–3 and/or 5 before protected
  routes work again.

## 5. Keeping the collection in sync with the app

Postman doesn't watch this repo's files automatically. If you change a
request inside the Postman app (new field, new endpoint, etc.), export it
again (collection **···** menu → Export) and overwrite the JSON files in
this folder, then commit — that's what keeps this folder useful as
reproducible test evidence rather than going stale.

## 6. What this does *not* cover

There's no automated test suite in this repo — this Postman collection is a
manual, repeatable way to exercise the API, not a CI-run test. WebSocket
endpoints (`/ws`, `/ws/notifications`) aren't covered here either; Postman
can test WebSocket/STOMP separately (New → WebSocket Request) but that's not
wired into this collection.
