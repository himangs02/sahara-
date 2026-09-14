"""SQLAlchemy models for the Stage 1/2 vertical slice: caregivers (users), patients,
and the caregiver<->patient link table. Primary keys are UUIDs so they can later
accept the same client-generated ids Android already produces (Patient.syncId,
GameResult.syncId) without any ID remapping.

Every timestamp and default here is declared to match alembic/versions/0001_initial_schema.py
exactly (timezone-aware TIMESTAMP, and the same server_default values) so that
Base.metadata.create_all() (used by the test suite) and `alembic upgrade head`
(used against real PostgreSQL) produce equivalent schemas. Stage 2 found and fixed a
drift here: the original Stage 1 models declared plain `DateTime` (no timezone) and
Python-side-only defaults for `is_active`/`region`/`relationship_type`, while the
migration already had `DateTime(timezone=True)` and server_default values -- a row
inserted outside the ORM (raw SQL, another service) would previously not have gotten
those defaults. No new migration was needed since the migration was already correct;
only the ORM model was brought in line with it.
"""

from __future__ import annotations

import uuid
from datetime import datetime

from sqlalchemy import Boolean, DateTime, Float, ForeignKey, Integer, String, UniqueConstraint, func, true
from sqlalchemy.orm import Mapped, mapped_column

from app.db.database import Base

_TIMESTAMP = DateTime(timezone=True)


class User(Base):
    """A caregiver account. (Table name stays `users` -- generic -- since a future
    stage may add other authenticated roles.)"""

    __tablename__ = "users"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    email: Mapped[str] = mapped_column(String(255), unique=True, index=True, nullable=False)
    password_hash: Mapped[str] = mapped_column(String(255), nullable=False)
    is_active: Mapped[bool] = mapped_column(default=True, server_default=true(), nullable=False)
    created_at: Mapped[datetime] = mapped_column(_TIMESTAMP, server_default=func.now(), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(
        _TIMESTAMP, server_default=func.now(), onupdate=func.now(), nullable=False
    )


class Patient(Base):
    __tablename__ = "patients"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    name: Mapped[str] = mapped_column(String(100), nullable=False)
    age: Mapped[int] = mapped_column(nullable=False)
    preferred_language: Mapped[str] = mapped_column(String(50), nullable=False)
    region: Mapped[str] = mapped_column(String(100), nullable=False, default="", server_default="")
    created_at: Mapped[datetime] = mapped_column(_TIMESTAMP, server_default=func.now(), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(
        _TIMESTAMP, server_default=func.now(), onupdate=func.now(), nullable=False
    )


class CaregiverPatientRelationship(Base):
    """Links a caregiver to a patient. The unique constraint is what makes a
    duplicate link impossible at the database level, independent of any
    application-level check. Foreign keys cascade on delete so removing a caregiver
    or patient never leaves an orphaned relationship row behind."""

    __tablename__ = "caregiver_patient_relationships"
    __table_args__ = (UniqueConstraint("caregiver_id", "patient_id", name="uq_caregiver_patient"),)

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    caregiver_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True
    )
    patient_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("patients.id", ondelete="CASCADE"), nullable=False, index=True
    )
    relationship_type: Mapped[str] = mapped_column(
        String(50), nullable=False, default="primary_caregiver", server_default="primary_caregiver"
    )
    created_at: Mapped[datetime] = mapped_column(_TIMESTAMP, server_default=func.now(), nullable=False)


class GameResult(Base):
    """One completed (or abandoned) cognitive-game session, uploaded from Android.

    The primary key is NOT server-generated: it is the same client-generated UUID
    (`GameResult.syncId` in the Android app) used for the local Room row and the local
    sync_queue entry, so re-uploading after a dropped connection upserts instead of
    creating a duplicate -- the same idempotency pattern as `Patient.id` above.
    """

    __tablename__ = "game_results"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True)
    patient_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("patients.id", ondelete="CASCADE"), nullable=False, index=True
    )
    game_type: Mapped[str] = mapped_column(String(50), nullable=False)
    difficulty: Mapped[str] = mapped_column(String(20), nullable=False)
    total_pairs: Mapped[int] = mapped_column(Integer, nullable=False)
    matched_pairs: Mapped[int] = mapped_column(Integer, nullable=False)
    mistakes: Mapped[int] = mapped_column(Integer, nullable=False)
    completion_time_seconds: Mapped[int] = mapped_column(Integer, nullable=False)
    accuracy: Mapped[float] = mapped_column(Float, nullable=False)
    completed: Mapped[bool] = mapped_column(Boolean, nullable=False)
    occurred_at: Mapped[datetime] = mapped_column(_TIMESTAMP, nullable=False)
    synced_at: Mapped[datetime] = mapped_column(_TIMESTAMP, server_default=func.now(), nullable=False)


class Reminder(Base):
    """A daily reminder *definition*, synced from Android's local Room store (Stage 3C).

    The primary key is the same client-generated UUID as the local Room reminder id, so
    a retried upload upserts instead of duplicating -- the same idempotency pattern as
    GameResult/Patient above. Recurrence is modeled exactly like the Android domain model
    (`minute_of_day`, minutes after local midnight, re-evaluated in whatever timezone the
    device is currently in) rather than inventing a second, absolute-timestamp scheduling
    model here -- there is deliberately only one reminder architecture.

    Device-specific state (today's completion flag, an in-progress snooze) is NOT stored
    here: only the reminder's definition (title/description/type/time/enabled) syncs; see
    app/reminders/service.py.

    `deleted_at` is a soft-delete marker rather than a hard row delete, specifically so a
    stale, already-queued create/update from another device cannot resurrect a reminder
    the caregiver already deleted (see upsert_reminder's ReminderDeletedError).
    """

    __tablename__ = "reminders"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True)
    patient_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("patients.id", ondelete="CASCADE"), nullable=False, index=True
    )
    title: Mapped[str] = mapped_column(String(200), nullable=False)
    description: Mapped[str] = mapped_column(String(1000), nullable=False, default="", server_default="")
    reminder_type: Mapped[str] = mapped_column(String(50), nullable=False)
    minute_of_day: Mapped[int] = mapped_column(Integer, nullable=False)
    enabled: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True, server_default=true())
    deleted_at: Mapped[datetime | None] = mapped_column(_TIMESTAMP, nullable=True)
    created_at: Mapped[datetime] = mapped_column(_TIMESTAMP, server_default=func.now(), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(
        _TIMESTAMP, server_default=func.now(), onupdate=func.now(), nullable=False
    )
