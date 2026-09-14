"""Verifies data survives a real "application restart": a brand new engine and
connection pool, separate from the one the rest of the suite shares, must still see
data committed by a previous one. Against TEST_DATABASE_URL pointing at real
PostgreSQL this proves genuine durability; without it, it falls back to a real on-disk
SQLite file (not the shared in-memory database the rest of the suite uses) so the
check still means something.
"""

from __future__ import annotations

import os
import tempfile

from sqlalchemy import create_engine, select
from sqlalchemy.orm import sessionmaker

from app.core.security import hash_password
from app.db.database import Base
from app.db.models import User


def _persistent_test_url() -> tuple[str, str | None]:
    postgres_url = os.environ.get("TEST_DATABASE_URL")
    if postgres_url:
        return postgres_url, None
    fd, path = tempfile.mkstemp(suffix=".sqlite3")
    os.close(fd)
    return f"sqlite:///{path}", path


def test_data_persists_across_a_new_connection_pool():
    url, temp_path = _persistent_test_url()
    try:
        engine_one = create_engine(url, future=True)
        Base.metadata.create_all(bind=engine_one)
        SessionOne = sessionmaker(bind=engine_one, future=True)
        with SessionOne() as session:
            session.add(User(email="restart-check@example.com", password_hash=hash_password("password-1")))
            session.commit()
        engine_one.dispose()

        # A genuinely new engine/pool, as if the process had restarted.
        engine_two = create_engine(url, future=True)
        SessionTwo = sessionmaker(bind=engine_two, future=True)
        with SessionTwo() as session:
            found = session.execute(
                select(User).where(User.email == "restart-check@example.com")
            ).scalar_one()
            assert found.email == "restart-check@example.com"

        Base.metadata.drop_all(bind=engine_two)
        engine_two.dispose()
    finally:
        if temp_path and os.path.exists(temp_path):
            os.remove(temp_path)
