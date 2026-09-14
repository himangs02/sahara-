# Sahara Backend (Stage 1 + Stage 2)

FastAPI + PostgreSQL backend for the Sahara app: caregiver registration/login/JWT
auth, and patient create/list/detail/update with server-side authorization so a
caregiver can only ever see or change patients they're actually linked to.

**Not implemented yet** (see "What's intentionally not implemented" below): the
Android app is not connected to this API, there is no game-result/reminder/activity
sync, and there is no production deployment configuration.

## 1. Prerequisites

- Python 3.12+
- PostgreSQL 16 (via the bundled `docker-compose.yml`, a local install, or any
  reachable Postgres instance)
- Docker + Docker Compose, if you want the one-command local setup in step 2

## 2. How to start PostgreSQL

**Option A -- Docker Compose (recommended):**

```bash
cd backend
cp .env.example .env
# edit .env: set POSTGRES_PASSWORD and JWT_SECRET to real local values
docker compose up -d db
```

This starts a `postgres:16-alpine` container on `localhost:5432`, with data
persisted in a named Docker volume (`sahara_postgres_data`) so it survives container
restarts. It is local-development-only, not a production topology.

**Option B -- a PostgreSQL install you already have:**

```sql
CREATE DATABASE sahara;
CREATE USER sahara_user WITH PASSWORD 'changeme';
GRANT ALL PRIVILEGES ON DATABASE sahara TO sahara_user;
```

## 3. Python environment

```bash
cd backend
python -m venv .venv
# Windows
.venv\Scripts\activate
# macOS/Linux
source .venv/bin/activate

pip install -r requirements.txt
```

## 4. Configuring .env

Copy `.env.example` to `.env` and fill in real values. **Nothing in this project has
a hardcoded secret or credential** -- `app/core/config.py` requires `DATABASE_URL`
and `JWT_SECRET` from the environment, and the app fails to start without them
(verified: `python -c "from app.main import app"` with no env vars set raises a
`pydantic.ValidationError` naming the two missing fields, not a silent fallback).

```bash
cp .env.example .env
```

| Variable | Used by | Meaning |
|---|---|---|
| `DATABASE_URL` | the app | SQLAlchemy connection string, e.g. `postgresql+psycopg://sahara_user:changeme@localhost:5432/sahara` |
| `JWT_SECRET` | the app | Long random string signing access tokens. Generate with `python -c "import secrets; print(secrets.token_urlsafe(64))"` |
| `JWT_ALGORITHM` | the app | Defaults to `HS256` if unset |
| `ACCESS_TOKEN_EXPIRE_MINUTES` | the app | Defaults to `60` if unset |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | `docker-compose.yml` only | Builds the `db` container and the `api` container's `DATABASE_URL` for you; not read by the Python app directly |

`.env` is git-ignored; never commit it. Same for `.venv/`, `*.db`/`*.sqlite3`, and
`.pytest_cache/` (see `backend/.gitignore`).

## 5. Running Alembic

With `DATABASE_URL` set and PostgreSQL reachable:

```bash
alembic upgrade head
```

This creates `users`, `patients`, and `caregiver_patient_relationships`.

To create a new migration after changing `app/db/models.py`:

```bash
alembic revision -m "describe the change"
```

Autogeneration (`alembic revision --autogenerate`) also works once you have a live
Postgres reachable at `DATABASE_URL` matching the *previous* migration state -- the
existing `0001_initial_schema` migration was originally written by hand (no Postgres
was reachable while it was authored) and has since been validated against a real
PostgreSQL 16 instance in Stage 2 (see "Stage 2 validation" below).

**Downgrade:** `alembic downgrade base` drops everything this migration created;
`alembic upgrade head` re-creates it. Both directions are exercised by hand in Stage 2
validation and leave the schema byte-for-byte the same on a second `upgrade head`.

## 6. Starting FastAPI

Directly:

```bash
uvicorn app.main:app --reload
```

Or the whole stack (Postgres + API, API runs `alembic upgrade head` automatically on
container start via `docker-entrypoint.sh`):

```bash
docker compose up --build
```

The API is then at `http://127.0.0.1:8000`.

## 7. Running tests

```bash
pytest
```

By default, tests run against an isolated **in-memory SQLite** database (see
`tests/conftest.py`) so they need no external services and run in a few seconds. This
is what CI / a fresh clone should use.

To run the exact same suite against a **real PostgreSQL** database instead (what
Stage 2 validation actually does):

```bash
# a disposable database, separate from your dev DB -- tests create and drop all
# tables around every single test
createdb sahara_test
TEST_DATABASE_URL=postgresql+psycopg://sahara_user:changeme@localhost:5432/sahara_test pytest
```

Never point `TEST_DATABASE_URL` at a database you care about: every test drops all
tables at teardown.

## 8. API documentation

With the server running:

- Swagger UI: `http://127.0.0.1:8000/docs`
- ReDoc: `http://127.0.0.1:8000/redoc`
- Raw OpenAPI schema: `http://127.0.0.1:8000/openapi.json`

## Endpoints

| Method | Path | Auth required | Notes |
|---|---|---|---|
| POST | `/auth/register` | No | Creates a caregiver account, returns a JWT |
| POST | `/auth/login` | No | Returns a JWT |
| GET | `/auth/me` | Yes | Returns the authenticated caregiver's own profile |
| POST | `/patients` | Yes | Creates a patient, linked to the caller |
| GET | `/patients` | Yes | Lists only patients linked to the caller |
| GET | `/patients/{id}` | Yes | 404 if not found *or* not linked to the caller |
| PATCH | `/patients/{id}` | Yes | 404 if not found *or* not linked to the caller |
| GET | `/health` | No | 200 + `{"database":"ok"}` when reachable, 503 + `{"database":"unavailable"}` when not -- never a false-positive 200 |

## Development architecture

```
FastAPI routers (app/auth/router.py, app/patients/router.py)
        |
        v
service layer (app/auth/service.py, app/patients/service.py)  <- all authorization logic lives here
        |
        v
SQLAlchemy models (app/db/models.py) --- Alembic migrations (alembic/versions/)
        |
        v
PostgreSQL
```

- `app/core/deps.py` decodes and verifies the JWT and loads the caregiver row; every
  route depends on it for identity. **No route ever trusts a caregiver/patient id the
  client supplies as *who is calling* -- only the JWT does that.**
- `app/patients/service.py` re-checks `caregiver_patient_relationships` on every
  single read or write, independent of anything the Android client has already
  checked locally.

## Security notes

- **Passwords:** bcrypt, salt embedded in the hash, never stored or logged in
  plaintext. Verified by `tests/test_auth.py` (registered user's stored data never
  contains `password`/`password_hash` in any API response).
- **JWTs:** HS256, contain only `sub` (caregiver id), `iat`, `exp` -- no email, no
  role, no other claim. Expiry is configurable via `ACCESS_TOKEN_EXPIRE_MINUTES`. No
  refresh-token flow yet (Stage 1/2 scope).
- **No hardcoded secrets anywhere:** confirmed by inspection of `app/core/config.py`,
  `app/core/security.py`, `app/core/deps.py` -- every secret-shaped value is a
  required environment variable with no default.
- **Login/​registration failure messages never distinguish** "wrong password" from
  "unknown email" from "deactivated account" -- all three return the same 401, so the
  API can't be used to enumerate registered or disabled accounts.
- **Patient authorization is symmetric with the "unknown resource" case:** a patient
  that exists but isn't linked to the caller returns the exact same 404 body as a
  patient id that doesn't exist at all (`tests/test_patients.py::test_unauthorized_and_nonexistent_patient_are_indistinguishable`).
  Nothing in any response reveals that a given id belongs to *another* caregiver.
- **The `/health` endpoint never leaks connection details.** A database failure is
  logged server-side only (`logger.exception(...)`); the client only ever sees the
  fixed body `{"status":"ok","database":"unavailable"}` with a 503.
- **CORS:** no `CORSMiddleware` is registered at all. By default, browsers will
  refuse cross-origin requests to this API. This is intentional for Stage 1/2 (no
  browser frontend exists yet) -- add it deliberately, scoped to a specific origin
  allowlist, only when a browser client is introduced.
- **Foreign keys and cascades are enforced by the database itself**
  (`ON DELETE CASCADE` on both relationship foreign keys, a `UNIQUE` constraint on
  `(caregiver_id, patient_id)`), not just by application code -- verified directly
  against SQLite (with `PRAGMA foreign_keys=ON`, since SQLite doesn't enforce them by
  default) and against real PostgreSQL (which always enforces them) in
  `tests/test_data_integrity.py`.

## Stage 2 validation (real PostgreSQL)

Stage 1 only validated the schema against SQLite. Stage 2 ran the same Alembic
migration and the same pytest suite against a real local PostgreSQL 16 instance and
found/fixed one real issue: `app/db/models.py` declared plain (non-timezone-aware)
`DateTime` columns and Python-side-only defaults for `is_active` / `region` /
`relationship_type`, while `alembic/versions/0001_initial_schema.py` already had
timezone-aware timestamps and server-side defaults for those columns. This meant a
row inserted by anything other than the ORM's Python defaults (raw SQL, a future
service) could have ended up with `NULL`/naive values depending on backend. The model
now matches the migration exactly; no new migration was needed since the migration
itself was already correct -- only the ORM-side model was brought back in line with
it.

## What's intentionally NOT implemented yet

- **Android is not connected to this backend.** The Android app still uses
  `SimulatedRemoteDataSource` (an in-memory stand-in). Wiring Retrofit/OkHttp into
  Android and pointing it at this API is Stage 3.
- **No game result, reminder, or activity-session sync.** Only caregiver auth and
  patient CRUD exist. Sync is Stage 4.
- **No production deployment.** `docker-compose.yml` here is for local development
  only (a single Postgres container with a Docker volume, no replication, no backups,
  no TLS termination, no secrets manager). Real deployment configuration is Stage 10.
- **No endpoint to link a second caregiver to an existing patient.** The service
  function (`app/patients/service.py::link_caregiver_to_patient`) exists and is
  tested directly, but isn't exposed over HTTP -- a real "add co-caregiver" flow needs
  a consent/invite mechanism that's out of scope here.
- **No refresh tokens, rate limiting, or audit logging** -- deferred to Stage 9
  (security hardening).
