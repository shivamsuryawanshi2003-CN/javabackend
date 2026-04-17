# Java backend (API Gateway + Auth service)

Two Spring Boot apps: **api-gateway** (**8080**) and **authservice** (**8081**). **Browsers and the Next.js app must use the gateway only** (`http://localhost:8080`); the gateway proxies `/api/**`, `/oauth2/**`, `/login/**`, and `/error` to authservice.

**Ports:** Next.js **3000**, Python resume API **8000**, gateway **8080**, authservice **8081** — see [`../../PORTS.md`](../../PORTS.md).

---

## Prerequisites

- **JDK 17+** and **Maven 3.8+** on your `PATH`
- **Docker** (recommended) for PostgreSQL, Redis, Zookeeper, Kafka

---

## 1. Environment file

The repo includes **`.env/local.env`** (same keys as **`.env/local.env.example`**) so a clone is runnable after you fill secrets. The copy on **GitHub** must use **placeholders only**: GitHub **Push Protection** rejects commits that contain real OAuth client secrets, Azure app secrets, mail app passwords, and similar. Your **local** machine should keep the real values in `.env/local.env`; they are not re-uploaded. Details: **`.env/README.txt`**.

1. If **`.env/local.env`** is missing, copy **`.env/local.env.example`** to **`.env/local.env`**
2. Set **`JWT_SECRET`** (same value for **both** Java services) and **`AUTH_INTERNAL_API_KEY`**
3. For Next.js on **3000**: **`APP_FRONTEND_URL`**, **`GATEWAY_CORS_ORIGINS`**, and (for convenience) **`NEXT_PUBLIC_GATEWAY_URL`** — see **`.env/local.env.example`** or **`.env/local.env`**

Load env **before** starting either service (from this `java` folder):

```powershell
. .\scripts\load-env.ps1
```

```bash
source ./scripts/load-env.sh
```

---

## 2. Infrastructure (Docker)

From **`backend/java`** (same folder as this README):

```bash
docker compose up -d
```

Starts PostgreSQL (**5432**), Redis (**6379**), Zookeeper, Kafka (**9092**). Wait until Postgres is healthy before starting the apps.

---

## 3. Start the services

**Two terminals**, env loaded, cwd **`backend/java`**. Order: **authservice first**, then **api-gateway**.

**Terminal A — authservice**

```bash
cd authservice
mvn spring-boot:run
```

**Terminal B — API gateway**

```bash
cd api-gateway
mvn spring-boot:run
```

Default upstream: `JOBRA_AUTH_SERVICE_URI=http://localhost:8081`

### Connect the Next.js frontend (auth + OAuth)

The Java stack and the Next app use **different env files** — both must agree on URLs.

| What | Where |
|------|--------|
| Gateway, CORS, `APP_FRONTEND_URL`, JWT, OAuth, mail | **`backend/java/.env/local.env`** (load via `load-env` before `mvn`) |
| `NEXT_PUBLIC_GATEWAY_URL` for the browser | **`frontend/.env.local`** — copy from **`frontend/.env.example`** |

**Steps**

1. **Infrastructure:** `docker compose up -d` (Postgres/Redis/Kafka) if you are not using local DB/Redis on the same ports.
2. **Java:** Load env → start **authservice** → start **api-gateway** (order matters).
3. **Frontend env:** In `frontend/`, set **`NEXT_PUBLIC_GATEWAY_URL=http://localhost:8080`** (must match the gateway; never point this at **8081** for browser calls).
4. **Java env:** Keep **`APP_FRONTEND_URL=http://localhost:3000`** and **`GATEWAY_CORS_ORIGINS`** including `http://localhost:3000` so cross-origin requests from the dev server can send cookies (`credentials: "include"`).
5. **Run UI:** `cd frontend && npm run dev` → **http://localhost:3000**.

**Sanity check:** `GET http://localhost:8080/api/auth/session` returns **200** + JSON before testing login in the browser. After changing **`frontend/.env.local`**, restart **`npm run dev`** (Next.js reads `NEXT_PUBLIC_*` at startup).

---

## 4. Architecture (how a request moves)

```text
Browser / Next.js  ──►  API Gateway :8080  ──►  Authservice :8081  ──►  PostgreSQL / Redis / Kafka
                              │
                              ├── CORS, rate limits, JWT validation (public vs protected routes)
                              └── Same paths as authservice (e.g. POST /api/auth/login)
```

| Layer | Responsibility |
|--------|----------------|
| **Gateway** | CORS, per-IP rate limits (Redis), forwards to authservice; OAuth browser URLs stay on **8080** |
| **Authservice** | REST auth, JWT cookies, OAuth2, SMTP OTP, MFA (TOTP), internal APIs with `X-Internal-Key` |

**Frontend rule:** set `NEXT_PUBLIC_GATEWAY_URL=http://localhost:8080` and call **`fetch(url, { credentials: "include" })`** so JWT cookies (`token`, `refreshToken`) are sent on **cross-origin** requests from the Next.js origin to the gateway (CORS + `GATEWAY_CORS_ORIGINS` must allow that origin).

---

## 5. Complete flows (for product / frontend design)

### 5.1 Email + password registration

1. `POST /api/auth/register` — body: `name`, `email`, `password` → account created **inactive**, SMTP OTP sent (async).
2. `POST /api/auth/verify-otp` — body: `email`, `otp` → account **active**.
3. Optional: `POST /api/auth/resend-otp?email=` if OTP expired / not received (rate-limited).

### 5.2 Email + password login

1. `POST /api/auth/login` — body: `email`, `password`, optional `mfaCode` for **ADMIN** / **SUPER_ADMIN** with MFA enabled.
2. **END_USER:** success → HttpOnly cookies set.
3. **Privileged, MFA not enrolled:** `403` with message about MFA enrollment → UI sends user to **`/mfa-setup`** (QR flow).
4. **Privileged, MFA enrolled:** requires valid **`mfaCode`** (TOTP); wrong/missing → `401`.

### 5.3 OAuth (Google, GitHub, Microsoft, LinkedIn)

1. Browser navigates to **`{GATEWAY}/oauth2/authorization/{provider}`** (e.g. `google`, `github`, `azure`, `linkedin`).
2. User signs in at the provider; callback hits the **gateway** (`/login/oauth2/code/...`).
3. **New user:** created **active** (provider-verified email; no SMTP OTP for that path).
4. **END_USER:** redirect to app (e.g. dashboard) with cookies.
5. **ADMIN / SUPER_ADMIN, MFA off:** redirect to login with `?error=mfa_enroll_required&email=...` → UI → **`/mfa-setup`**.
6. **ADMIN / SUPER_ADMIN, MFA on:** redirect to login with `?mfa=1&oauth=1&email=...&challenge=...` → UI calls **`POST /api/auth/oauth/mfa/verify`** with TOTP + `challengeToken` → cookies set.

### 5.4 Password reset

1. `POST /api/auth/password-reset/request` — body: `{ "email": "..." }` → generic success message (no account enumeration).
2. `POST /api/auth/password-reset/confirm` — body: `email`, `otp`, `newPassword`.

### 5.5 MFA enrollment (QR) — privileged only

1. `POST /api/auth/mfa/enroll/start` — body: `email`, `password` (OAuth-only users may set a new password here, min 8 chars) → returns `qrImageDataUrl`, `otpauthUri`, `secret`.
2. `POST /api/auth/mfa/enroll/confirm` — body: `email`, `password`, `code` (TOTP) → MFA enabled.

### 5.6 Session check (SPA)

- **`GET /api/auth/session`** — always **200**: `{ "authenticated": false }` when logged out (avoids noisy 401 in DevTools).
- **`GET /api/auth/me`** — **401** when logged out; **200** with profile when JWT cookie (or Bearer) present.

### 5.7 Logout / refresh

- `POST /api/auth/logout` — clears cookies / session server-side.
- `POST /api/auth/refresh` — new access cookie from refresh cookie (still authenticated).

---

## 6. HTTP API reference (call via **gateway** `:8080`)

Base URL: **`http://localhost:8080`** (or your `NEXT_PUBLIC_GATEWAY_URL`).

| Method | Path | Auth at gateway | Purpose |
|--------|------|-----------------|--------|
| POST | `/api/auth/register` | Public | Register; OTP email (see SMTP env) |
| POST | `/api/auth/verify-otp` | Public | Verify email OTP |
| POST | `/api/auth/resend-otp` | Public | Query: `email` |
| POST | `/api/auth/login` | Public | Body: `email`, `password`, optional `mfaCode` |
| POST | `/api/auth/refresh` | Public | Refresh access token (cookies) |
| POST | `/api/auth/logout` | Public | Logout |
| POST | `/api/auth/password-reset/request` | Public | Body: `{ "email" }` |
| POST | `/api/auth/password-reset/confirm` | Public | Body: `email`, `otp`, `newPassword` |
| GET | `/api/auth/session` | Public | Soft session probe |
| GET | `/api/auth/me` | **JWT required** | Current user (401 if anonymous) |
| GET | `/api/auth/users/by-email` | Public | Query: `email` — dev/support; tighten in production if needed |
| POST | `/api/auth/mfa/enroll/start` | Public | Body: `email`, `password` — ADMIN/SUPER_ADMIN only |
| POST | `/api/auth/mfa/enroll/confirm` | Public | Body: `email`, `password`, `code` |
| POST | `/api/auth/oauth/mfa/verify` | Public | Body: `email`, `mfaCode`, `challengeToken` |
| POST | `/api/auth/internal/subscription` | Public at gateway; **authservice** checks `X-Internal-Key` | Body: `email`, `plan` |
| POST | `/api/auth/internal/role` | Public at gateway; **`X-Internal-Key`** | Body: `email`, `role` (`END_USER`, `ADMIN`, `SUPER_ADMIN`) |
| GET | `/api/admin/ping` | **JWT** + role ADMIN or SUPER_ADMIN | Health check for admin UI |

**OAuth entry (browser navigation, not JSON):**

- `GET /oauth2/authorization/google` (and `github`, `azure`, `linkedin`)

**Internal tools example (curl / Postman):**

```http
POST /api/auth/internal/role
X-Internal-Key: <AUTH_INTERNAL_API_KEY from .env>
Content-Type: application/json

{"email":"user@example.com","role":"SUPER_ADMIN"}
```

---

## 7. Adding a new authservice endpoint and using it from the frontend

1. **Implement** the handler in authservice (e.g. new method on a `@RestController` under `/api/...`).
2. **Security** — `SecurityConfig.java`:
   - **Public** (login/register style): add path to `permitAll()` list **or** use existing pattern.
   - **Authenticated**: omit from permit list; client must send JWT cookie or `Authorization: Bearer <access>`.
   - **Admin-only:** use `/api/admin/**` + `@PreAuthorize` or role checks.
3. **Gateway** — `GatewaySecurityPathProperties.java`:
   - If the route must be callable **without** JWT (e.g. new public POST), add the path to **`publicPatterns`**. If you skip this, the gateway will return **401** before the request reaches authservice.
4. **Rate limiting** (optional) — `GatewayRateLimitProperties.java`: add path patterns for sensitive routes.
5. **Frontend** — use **`${NEXT_PUBLIC_GATEWAY_URL}/your/path`**, **`credentials: "include"`** for cookie-based auth, **`Content-Type: application/json`** for bodies.

Keep **gateway public list** and **authservice `permitAll`** aligned, or you will see confusing 401s from the gateway.

---

## 8. Quick checks

| Check | URL |
|-------|-----|
| Gateway up | `GET http://localhost:8080/api/auth/session` → 200 + JSON |
| Logged-in profile | `GET http://localhost:8080/api/auth/me` with cookies → 200 |
| Authservice direct (dev only) | `http://localhost:8081` |
| Health | `GET .../actuator/health` on each service (aggregate may depend on Redis/DB) |

---

## 9. Build without running

```bash
cd authservice && mvn -q -DskipTests package
cd ../api-gateway && mvn -q -DskipTests package
```

---

## Troubleshooting

- **Port in use:** Stop the other process or change ports in config / `docker-compose.yml`.
- **`ERR_CONNECTION_REFUSED` on :8080:** Start **api-gateway**.
- **CORS from `http://localhost:3000`:** Set `GATEWAY_CORS_ORIGINS` in `.env/local.env`, restart **gateway**.
- **401 from gateway on a new route:** Add the path to gateway **`publicPatterns`** if it should be public.
- **SMTP / OTP:** Configure `MAIL_*` in `.env/local.env`; Gmail needs an **App password** for `MAIL_USERNAME`.
- **OAuth:** Set provider IDs in `.env`; redirect URIs must match the **gateway** host/port (e.g. `http://localhost:8080/login/oauth2/code/google`).
