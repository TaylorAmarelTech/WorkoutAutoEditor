"""End-to-end pipeline: probe, scenes, plan, render."""
from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from vidcut import analyze, editor, llm
from vidcut.models import CutPlan, Scene, VideoMeta


@dataclass
class PipelineResult:
    meta: VideoMeta
    scenes: list[Scene]
    plan: CutPlan
    output: Path | None  # None when only planning, set when rendering


def plan_only(
    source: Path,
    instruction: str,
    model: str = llm.DEFAULT_MODEL,
    scene_threshold: float = 0.30,
) -> PipelineResult:
    """Run probe + scene detection + LLM plan. No render."""
    meta = analyze.probe(source)
    scenes = analyze.detect_scenes(source, threshold=scene_threshold)
    if meta.has_audio:
        scenes = analyze.annotate_audio_rms(source, scenes)
    plan = llm.plan_cuts(meta, scenes, instruction, model=model)
    return PipelineResult(meta=meta, scenes=scenes, plan=plan, output=None)


def edit(
    source: Path,
    instruction: str,
    output: Path,
    model: str = llm.DEFAULT_MODEL,
    scene_threshold: float = 0.30,
    reencode: bool = True,
) -> PipelineResult:
    """Plan + render."""
    result = plan_only(source, instruction, model=model, scene_threshold=scene_threshold)
    rendered = editor.render(source, result.plan, output, reencode=reencode)
    result.output = rendered
    return result
