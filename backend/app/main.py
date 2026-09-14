from __future__ import annotations

import logging

from fastapi import FastAPI, status
from fastapi.responses import JSONResponse
from sqlalchemy import text

from app.auth.router import router as auth_router
from app.core.config import get_settings
from app.db.database import SessionLocal
from app.game_results.router import router as game_results_router
from app.patients.router import router as patients_router
from app.reminders.router import router as reminders_router

app = FastAPI(
    title="Sahara Backend",
    description="Caregiver auth and patient management API for the Sahara cognitive-care app.",
    version="0.1.0",
)

app.include_router(auth_router)
app.include_router(patients_router)
app.include_router(game_results_router)
app.include_router(reminders_router)

logger = logging.getLogger("sahara.health")


@app.get(
    "/health",
    tags=["health"],
    summary="Liveness and database connectivity check",
    responses={503: {"description": "The API process is up but the database is unreachable"}},
)
def health() -> dict:
    get_settings()  # fails fast if required env vars are missing
    db = SessionLocal()
    try:
        db.execute(text("SELECT 1"))
    except Exception:
        # Deliberately no exception detail in the response body -- that could leak
        # connection info (host, credentials in a driver error message, etc.). The
        # real error goes to the server's own logs only; a monitoring/orchestration
        # tool only needs the 503 + this small, fixed body.
        logger.exception("Health check: database connectivity check failed")
        return JSONResponse(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            content={"status": "ok", "database": "unavailable"},
        )
    finally:
        db.close()
    return {"status": "ok", "database": "ok"}
