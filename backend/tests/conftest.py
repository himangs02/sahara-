"""Test fixtures.

By default the whole suite runs against an isolated in-memory SQLite database, so
`pytest` needs no external services. To validate the same suite against a real
PostgreSQL instance (Stage 2), set TEST_DATABASE_URL before running pytest, e.g.:

    TEST_DATABASE_URL=postgresql+psycopg://sahara_test:...@localhost:5432/sahara_test pytest

Required env vars (DATABASE_URL/JWT_SECRET/...) are set *before* importing anything
from `app`, since app.core.config.Settings() is constructed eagerly at import time and
would otherwise fail (by design -- Stage 1 never falls back to a hardcoded secret).
DATABASE_URL itself is only used to satisfy that startup check; the app's own module-
level engine is never used by tests -- get_db is overridden below to use the test
engine instead (built from TEST_DATABASE_URL when set, in-memory SQLite otherwise).
"""

from __future__ import annotations

import os

os.environ.setdefault("DATABASE_URL", os.environ.get("TEST_DATABASE_URL", "sqlite+pysqlite:///:memory:"))
os.environ.setdefault("JWT_SECRET", "test-only-secret-never-used-outside-pytest")
os.environ.setdefault("JWT_ALGORITHM", "HS256")
os.environ.setdefault("ACCESS_TOKEN_EXPIRE_MINUTES", "60")

import pytest  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402
from sqlalchemy import create_engine, event  # noqa: E402
from sqlalchemy.orm import sessionmaker  # noqa: E402
from sqlalchemy.pool import StaticPool  # noqa: E402

from app.db import models  # noqa: E402,F401  registers tables on Base.metadata
from app.db.database import Base, get_db  # noqa: E402
from app.main import app  # noqa: E402

TEST_DATABASE_URL = os.environ.get("TEST_DATABASE_URL")
USING_REAL_POSTGRES = bool(TEST_DATABASE_URL) and TEST_DATABASE_URL.startswith("postgresql")

if TEST_DATABASE_URL:
    test_engine = create_engine(TEST_DATABASE_URL, future=True)
else:
    # A single shared in-memory SQLite connection for the whole test session
    # (StaticPool keeps one physical connection alive so ":memory:" data isn't lost
    # between checkouts).
    test_engine = create_engine(
        "sqlite+pysqlite:///:memory:",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
        future=True,
    )

    @event.listens_for(test_engine, "connect")
    def _enable_sqlite_foreign_keys(dbapi_connection, _record):
        cursor = dbapi_connection.cursor()
        cursor.execute("PRAGMA foreign_keys=ON")
        cursor.close()

TestingSessionLocal = sessionmaker(bind=test_engine, autoflush=False, autocommit=False, future=True)


def _override_get_db():
    db = TestingSessionLocal()
    try:
        yield db
    finally:
        db.close()


app.dependency_overrides[get_db] = _override_get_db


@pytest.fixture(autouse=True)
def _fresh_database():
    """Every test starts against empty tables, so tests never leak data into each
    other -- and, when pointed at a real Postgres test database, never leave rows
    behind in a database a developer might reuse for something else."""
    Base.metadata.create_all(bind=test_engine)
    yield
    Base.metadata.drop_all(bind=test_engine)


@pytest.fixture
def client() -> TestClient:
    return TestClient(app)


@pytest.fixture
def db_session():
    db = TestingSessionLocal()
    try:
        yield db
    finally:
        db.close()


def register(client: TestClient, email: str, password: str = "correct-horse-battery") -> dict:
    response = client.post("/auth/register", json={"email": email, "password": password})
    assert response.status_code == 201, response.text
    return response.json()


def auth_headers(token: str) -> dict:
    return {"Authorization": f"Bearer {token}"}
