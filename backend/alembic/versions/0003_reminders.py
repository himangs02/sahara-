"""reminders table

Revision ID: 0003
Revises: 0002
Create Date: 2026-09-14

"""

from __future__ import annotations

from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op

revision: str = "0003"
down_revision: Union[str, None] = "0002"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "reminders",
        # No default: the id is always supplied by the client (Android's stable,
        # client-generated Room reminder id) so a retried upload upserts instead of
        # duplicating -- same idempotency pattern as patients/game_results.
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column(
            "patient_id",
            sa.Uuid(),
            sa.ForeignKey("patients.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("title", sa.String(length=200), nullable=False),
        sa.Column("description", sa.String(length=1000), nullable=False, server_default=""),
        sa.Column("reminder_type", sa.String(length=50), nullable=False),
        sa.Column("minute_of_day", sa.Integer(), nullable=False),
        sa.Column("enabled", sa.Boolean(), nullable=False, server_default=sa.true()),
        # Soft-delete marker: prevents a stale, already-queued create/update from another
        # device resurrecting a reminder that was deleted in the meantime.
        sa.Column("deleted_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
    )
    op.create_index("ix_reminders_patient_id", "reminders", ["patient_id"])


def downgrade() -> None:
    op.drop_index("ix_reminders_patient_id", table_name="reminders")
    op.drop_table("reminders")
