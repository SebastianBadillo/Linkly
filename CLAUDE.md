# Linkly

URL shortener whose purpose is to **prove** caching, rate limiting and performance with real numbers
(k6 cache ON vs OFF, hit ratio, p95). Project 1 of a 6-project portfolio: the Docker setup, CI workflow
and k6 setup built here are reused by later projects, so keep them clean and generic.

## Stack
Java 21 · Spring Boot 4 · Maven wrapper · PostgreSQL + Flyway · Valkey (Spring Data Valkey) ·
Testcontainers · Docker Compose · **GitHub Actions** (not GitLab CI) · Prometheus + Grafana · k6 · Angular.

## Repo layout (monorepo)
- `backend/` — Spring Boot app (`pom.xml`, `mvnw`, `src/`). Run Maven from here: `cd backend && ./mvnw verify`.
- `frontend/` — Angular dashboard (added in M4).
- Root — shared things: `docker-compose.yml`, `.env.example`, `.github/workflows/`, `.claude/`, docs.

## Working method (always follow)
The developer must be able to explain every line in an interview. For each new feature use the `/spec` skill:
problem first → developer specifies the logic → Claude gap-checks and teaches missing concepts →
Claude implements with tests → developer explains it back. Do not jump straight to code on a new feature.

Also point out chances to use Claude Code features (skills, hooks, subagents, MCP) when they would help;
learning them is a goal of this project.

## Conventions
- Explain in clear, simple English (the developer is practising English).
- Every feature ships with tests: JUnit 5 + Mockito for unit tests, Testcontainers for integration tests.
- Schema changes only through Flyway migrations (`src/main/resources/db/migration`).
- No secrets in the repo: config comes from env vars; keep `.env.example` up to date.
- Significant decisions get a short ADR in `docs/adr/`.
- Commit messages: Conventional Commits (`feat:`, `fix:`, `test:`, `docs:`, `ci:`, `chore:`).

## Key decisions so far
- Redirect with **302** (not 301) so every click reaches the server (can count it, can change the target).
- Cache-aside on redirects; on update/delete: write Postgres first, **then** evict; TTL as safety net.
- Valkey down → **graceful degradation** to Postgres (short timeouts). Rate limiter **fails open**.
- Clicks: write-behind (Valkey `INCR` + batched flush). Losing a few seconds of clicks on crash is acceptable.

@CLAUDE.local.md
