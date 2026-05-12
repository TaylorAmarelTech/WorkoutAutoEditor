"""Data models shared across CLI, pipeline, and Gradio UI."""
from __future__ import annotations

from pydantic import BaseModel, Field, ValidationInfo, field_validator


class Scene(BaseModel):
    """A scene boundary detected from the source video."""
    start_ms: int
    end_ms: int
    label: str | None = None
    audio_rms: float = 0.0

    @property
    def duration_ms(self) -> int:
        return self.end_ms - self.start_ms


class CutSpan(BaseModel):
    """A single span the editor decided to keep."""
    start_ms: int = Field(..., ge=0)
    end_ms: int = Field(..., ge=0)
    rationale: str = ""

    @field_validator("end_ms")
    @classmethod
    def _end_after_start(cls, v: int, info: ValidationInfo) -> int:
        start = info.data.get("start_ms", 0)
        if v <= start:
            raise ValueError(f"end_ms ({v}) must be > start_ms ({start})")
        return v

    @property
    def duration_ms(self) -> int:
        return self.end_ms - self.start_ms


class CutPlan(BaseModel):
    """The full plan: ordered spans + a one-line summary."""
    summary: str = ""
    spans: list[CutSpan] = Field(default_factory=list)

    @property
    def total_ms(self) -> int:
        return sum(s.duration_ms for s in self.spans)


class VideoMeta(BaseModel):
    """ffprobe summary of the source."""
    path: str
    duration_ms: int
    width: int
    height: int
    fps: float
    has_audio: bool
