from __future__ import annotations

import uuid
from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field


class PatientCreate(BaseModel):
    # Optional: a sync client (Android) supplies its own stable client-generated UUID
    # here so a retried upload upserts instead of creating a duplicate patient. Omit it
    # for a plain "create a new patient" call (the server generates one).
    id: uuid.UUID | None = None
    name: str = Field(min_length=1, max_length=100)
    age: int = Field(ge=0, le=120)
    preferred_language: str = Field(min_length=1, max_length=50)
    region: str = Field(default="", max_length=100)


class PatientUpdate(BaseModel):
    """All fields optional: only the ones present in the request body are changed."""

    name: str | None = Field(default=None, min_length=1, max_length=100)
    age: int | None = Field(default=None, ge=0, le=120)
    preferred_language: str | None = Field(default=None, min_length=1, max_length=50)
    region: str | None = Field(default=None, max_length=100)


class PatientRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    name: str
    age: int
    preferred_language: str
    region: str
    created_at: datetime
    updated_at: datetime
