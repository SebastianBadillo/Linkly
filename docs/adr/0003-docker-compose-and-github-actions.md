# ADR 0003: Run with Docker Compose, check with GitHub Actions

- **Status:** Accepted
- **Date:** 2026-11-15

## Context
Two problems:
1. Linkly only ran on the developer's laptop (needs Java 21, a hand-started Postgres, the right env vars).
   A recruiter who clones the repo should get it running with one command.
2. Nothing checked the code on each push. A broken commit could sit on `main` unnoticed.

This setup is also the template for the next 5 portfolio projects, so it must stay simple and generic.

## Decision
**Docker (`backend/Dockerfile`), multi-stage:**
- Stage `build`: `eclipse-temurin:21-jdk`. Copy `pom.xml` + wrapper first and run `dependency:go-offline`
  (a cached layer), then copy `src` and `package -DskipTests` (CI already runs the tests).
- Stage `run`: `eclipse-temurin:21-jre` with only the jar, as a **non-root** user.

**Docker Compose (`docker-compose.yml`):**
- Services `postgres` (17) and `app`. The app reaches the DB by **service name**: `postgres:5432`.
- Postgres has a `pg_isready` **healthcheck**; the app uses `depends_on: condition: service_healthy`,
  so Flyway never connects to a database that is still booting.
- Data lives in the named volume `pgdata` (survives `down`; `down -v` deletes it).
- Postgres has **no host port**: only the app needs it. The app port is `${APP_PORT:-8080}`.
- Config comes from `.env` (gitignored); `.env.example` (committed) lists every variable.
  Compose defaults (`${VAR:-default}`) make `docker compose up` work even without a `.env`, for local use only.

**CI (`.github/workflows/ci.yml`), GitHub Actions:**
- Runs on push to `main` and on pull requests into `main`.
- One job on `ubuntu-latest`: checkout → Temurin 21 with Maven cache → `./mvnw -B verify`.
- The runner has Docker, so the **Testcontainers** integration tests run against a real Postgres, as locally.
- `main` will be a protected branch: PRs must pass this check before merging.

## Alternatives considered
- **GitLab CI** (in the original plan): the repo is on GitHub, so GitHub Actions needs no extra account or mirror.
- **Single-stage Dockerfile:** simpler, but the image would ship Maven, the JDK and the source code. Rejected.
- **Build the jar on the host, copy it into the image:** faster, but needs Java on the host again, which is what we
  are trying to avoid. Rejected.
- **H2 in CI instead of Testcontainers:** faster, but a different database than production (SQL dialect,
  constraints). Rejected (see the project's testing rules).
- **Build and push the Docker image in CI now:** not needed until deployment (M4).

## Consequences
- `git clone` + `docker compose up -d --build` starts everything (checked: create → 201, redirect → 302,
  restart → data still there).
- CI catches broken commits; a green badge can go in the README.
- The app image is ~560 MB; it can be shrunk later (e.g. an Alpine JRE or jlink).
- On Windows, scripts can lose the executable bit: `mvnw` had to be marked with `git update-index --chmod=+x`.
