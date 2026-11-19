# Spec: API keys + rate limiting (M2)

Status: **confirmed** (2026-11-16). Next step: build it (`/spec` Step 3), then explain back (Step 4).

## Problem

Anyone can call `/api/**` without limit. A script sending 2,000 `POST /api/links` per second exhausts the
~10-connection DB pool, so normal redirects time out, and it can fill the table with spam links.
We need to know **who** is calling (API keys) and slow down **one** client without blocking everyone (per-key limit).

## API keys

- Flyway migration: table `api_keys` (`id`, `key_hash` unique, `name`, `created_at`, `revoked_at` nullable).
- `POST /api/keys` requires header `X-Admin-Token` equal to env var `ADMIN_TOKEN` (add to `.env.example`).
  - Wrong or missing admin token → **403**.
  - Success → `201 { id, key: "lk_<random>" }`. The key is shown **once**; we store only `sha256(key)`.
- Every other `/api/**` request needs header `X-API-Key`.
  - Missing, unknown or revoked → **401**.
- `GET /{code}` redirects stay public and are **not** rate limited.
- Later (with Passport): users generate keys from the dashboard after login.

Status codes: 401 = "I don't know who you are" · 403 = "I know you, but you're not allowed" · 429 = "too fast".

## Rate limiting: token bucket

- One bucket per API key: Valkey hash `ratelimit:{keyId}` with fields `tokens` (decimal) and `lastRefill`.
- Capacity **10**, refill **1 token every 6 s** (10/min). Both values come from config (env vars).
- A missing bucket = a full bucket.
- Per request, a **Lua script** runs atomically in Valkey (no other instance can interleave, which prevents
  two instances both taking the last token):
  1. `now` from Valkey `TIME` (one clock for all instances; avoids clock skew between app instances).
  2. `elapsed = max(0, now - lastRefill)`.
  3. `tokens = min(capacity, tokens + elapsed / 6)`, so **add, then cap, then check**.
  4. If `tokens >= 1`: `tokens -= 1`, allow. Else reject.
  5. Save `tokens`, `lastRefill = now`; set TTL = time to full refill (60 s).
- Rejected → **429**, `Retry-After: ceil((1 - tokens) * 6)` seconds, JSON body `{"error":"rate limit exceeded"}`.

Worked example: 0.17 tokens at 10:00:16, request at 10:00:19 → 0.17 + 3/6 = 0.67 < 1 → 429, `Retry-After: 2`.

## Failures

- Valkey error or timeout (~100 ms) → **fail open**: log a warning, allow the request.
  Trade-off: no limit during an outage, but the API stays up. (A bank would fail closed.)

## Tests

- Unit (JUnit 5 + Mockito): API key filter (missing / invalid / revoked → 401, valid → passes),
  admin token (→ 403), 429 response with `Retry-After`, fail-open when Valkey throws.
- Integration (Testcontainers Postgres + Valkey): 10 requests pass and the 11th gets 429; after waiting,
  requests pass again; **concurrency**: 20 parallel requests on a full bucket → exactly 10 allowed
  (proves the Lua script is atomic).

## Docs

- ADR 0005: admin-issued API keys + token bucket in Valkey with Lua (vs fixed window; fail open).
