#!/bin/sh
# Applies pending Alembic migrations, then starts the API. Fails fast (set -e) so a
# broken migration never leaves the container silently serving against a stale schema.
set -e

alembic upgrade head
exec uvicorn app.main:app --host 0.0.0.0 --port "${PORT:-8000}"
