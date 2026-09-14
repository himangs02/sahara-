"""initial schema: users, patients, caregiver_patient_relationships

Revision ID: 0001
Revises:
Create Date: 2026-09-14

"""

from __future__ import annotations

from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op

revision: str = "0001"
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "users",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("email", sa.String(length=255), nullable=False),
        sa.Column("password_hash", sa.String(length=255), nullable=False),
        sa.Column("is_active", sa.Boolean(), nullable=False, server_default=sa.true()),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
    )
    op.create_index("ix_users_email", "users", ["email"], unique=True)

    op.create_table(
        "patients",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("name", sa.String(length=100), nullable=False),
        sa.Column("age", sa.Integer(), nullable=False),
        sa.Column("preferred_language", sa.String(length=50), nullable=False),
        sa.Column("region", sa.String(length=100), nullable=False, server_default=""),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
    )

    op.create_table(
        "caregiver_patient_relationships",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column(
            "caregiver_id",
            sa.Uuid(),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column(
            "patient_id",
            sa.Uuid(),
            sa.ForeignKey("patients.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column(
            "relationship_type",
            sa.String(length=50),
            nullable=False,
            server_default="primary_caregiver",
        ),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        sa.UniqueConstraint("caregiver_id", "patient_id", name="uq_caregiver_patient"),
    )
    op.create_index(
        "ix_caregiver_patient_relationships_caregiver_id",
        "caregiver_patient_relationships",
        ["caregiver_id"],
    )
    op.create_index(
        "ix_caregiver_patient_relationships_patient_id",
        "caregiver_patient_relationships",
        ["patient_id"],
    )


def downgrade() -> None:
    op.drop_index("ix_caregiver_patient_relationships_patient_id", table_name="caregiver_patient_relationships")
    op.drop_index("ix_caregiver_patient_relationships_caregiver_id", table_name="caregiver_patient_relationships")
    op.drop_table("caregiver_patient_relationships")
    op.drop_table("patients")
    op.drop_index("ix_users_email", table_name="users")
    op.drop_table("users")
