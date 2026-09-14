from __future__ import annotations

import os
import sys
from logging.config import fileConfig

from alembic import context
from sqlalchemy import create_engine, pool

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.core.config import get_settings  # noqa: E402
from app.db import models  # noqa: E402,F401  registers all tables on Base.metadata
from app.db.database import Base  # noqa: E402

config = context.config

if config.config_file_name is not None:
    fileConfig(config.config_file_name)

# The connection string always comes from DATABASE_URL (via Settings), never from a
# value written into alembic.ini. It is read directly from `get_settings()` in both
# migration modes below rather than round-tripped through `config.set_main_option` /
# `engine_from_config` -- those go through Python's configparser, which treats a bare
# `%` in the URL as the start of an interpolation token and raises on any password
# containing a percent-encoded character (e.g. `%3A` for a literal `:`).
DATABASE_URL = get_settings().database_url

target_metadata = Base.metadata


def run_migrations_offline() -> None:
    context.configure(
        url=DATABASE_URL,
        target_metadata=target_metadata,
        literal_binds=True,
        dialect_opts={"paramstyle": "named"},
    )
    with context.begin_transaction():
        context.run_migrations()


def run_migrations_online() -> None:
    connectable = create_engine(DATABASE_URL, poolclass=pool.NullPool)
    with connectable.connect() as connection:
        context.configure(connection=connection, target_metadata=target_metadata)
        with context.begin_transaction():
            context.run_migrations()


if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()
