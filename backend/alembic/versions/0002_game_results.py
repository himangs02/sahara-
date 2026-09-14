"""game_results table

Revision ID: 0002
Revises: 0001
Create Date: 2026-09-14

"""

from __future__ import annotations

from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op

revision: str = "0002"
down_revision: Union[str, None] = "0001"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "game_results",
        # No default: the id is always supplied by the client (Android's stable,
        # client-generated syncId) so a retried upload upserts instead of duplicating.
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column(
            "patient_id",
            sa.Uuid(),
            sa.ForeignKey("patients.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("game_type", sa.String(length=50), nullable=False),
        sa.Column("difficulty", sa.String(length=20), nullable=False),
        sa.Column("total_pairs", sa.Integer(), nullable=False),
        sa.Column("matched_pairs", sa.Integer(), nullable=False),
        sa.Column("mistakes", sa.Integer(), nullable=False),
        sa.Column("completion_time_seconds", sa.Integer(), nullable=False),
        sa.Column("accuracy", sa.Float(), nullable=False),
        sa.Column("completed", sa.Boolean(), nullable=False),
        sa.Column("occurred_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("synced_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
    )
    op.create_index("ix_game_results_patient_id", "game_results", ["patient_id"])


def downgrade() -> None:
    op.drop_index("ix_game_results_patient_id", table_name="game_results")
    op.drop_table("game_results")
