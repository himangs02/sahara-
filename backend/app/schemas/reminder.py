from __future__ import annotations

import uuid
from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field


class ReminderUpsert(BaseModel):
    # Required, like GameResultUpsert: a reminder always originates as a specific local
    # Room row identified by that device's own stable id, so re-uploading after a dropped
    # connection upserts instead of creating a duplicate.
    id: uuid.UUID
    title: str = Field(min_length=1, max_length=200)
    description: str = Field(default="", max_length=1000)
    reminder_type: str = Field(min_length=1, max_length=50)
    # Minutes after local midnight, matching the Android domain model exactly (0..1439) --
    # there is one reminder scheduling model, not a second server-side one.
    minute_of_day: int = Field(ge=0, le=1439)
    enabled: bool = True


class ReminderUpdate(BaseModel):
    """All fields optional: only the ones present in the request body are changed."""

    title: str | None = Field(default=None, min_length=1, max_length=200)
    description: str | None = Field(default=None, max_length=1000)
    reminder_type: str | None = Field(default=None, min_length=1, max_length=50)
    minute_of_day: int | None = Field(default=None, ge=0, le=1439)
    enabled: bool | None = None


class ReminderRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    patient_id: uuid.UUID
    title: str
    description: str
    reminder_type: str
    minute_of_day: int
    enabled: bool
    created_at: datetime
    updated_at: datetime
