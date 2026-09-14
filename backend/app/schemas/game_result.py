from __future__ import annotations

import uuid
from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field


class GameResultUpsert(BaseModel):
    # Required, unlike Patient.id: a game result has no meaningful "create with a
    # server-generated id" case -- it always originates as a specific completed
    # session on the device, identified by that device's own syncId.
    id: uuid.UUID
    game_type: str = Field(min_length=1, max_length=50)
    difficulty: str = Field(min_length=1, max_length=20)
    total_pairs: int = Field(ge=0)
    matched_pairs: int = Field(ge=0)
    mistakes: int = Field(ge=0)
    completion_time_seconds: int = Field(ge=0)
    accuracy: float = Field(ge=0, le=100)
    completed: bool
    occurred_at: datetime


class GameResultRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    patient_id: uuid.UUID
    game_type: str
    difficulty: str
    total_pairs: int
    matched_pairs: int
    mistakes: int
    completion_time_seconds: int
    accuracy: float
    completed: bool
    occurred_at: datetime
    synced_at: datetime
