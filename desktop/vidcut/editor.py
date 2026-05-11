"""Apply a CutPlan to a source video using ffmpeg."""
from __future__ import annotations

import shutil
import subprocess
import tempfile
from pathlib import Path

from vidcut.models import CutPlan


def render(source: Path, plan: CutPlan, output: Path, reencode: bool = True) -> Path:
    """Render the planned spans into a single MP4.

    reencode=True is safest (handles arbitrary cut points). reencode=False
    is faster (stream-copy) but only works when cut points fall on keyframes.
    """
    if not plan.spans:
        raise ValueError("CutPlan has no spans")

    with tempfile.TemporaryDirectory() as tmp:
        tmpdir = Path(tmp)
        clip_paths: list[Path] = []
        for i, span in enumerate(plan.spans):
            clip = tmpdir / f"clip_{i:03d}.mp4"
            _cut_span(source, span.start_ms, span.end_ms, clip, reencode)
            if clip.exists() and clip.stat().st_size > 0:
                clip_paths.append(clip)
        if not clip_paths:
            raise RuntimeError("No clips produced; ffmpeg failures during cutting")

        listfile = tmpdir / "concat.txt"
        listfile.write_text("\n".join(f"file '{p.as_posix()}'" for p in clip_paths))
        output.parent.mkdir(parents=True, exist_ok=True)
        if output.exists():
            output.unlink()
        _concat(listfile, output, reencode=False)
    return output


def _cut_span(src: Path, start_ms: int, end_ms: int, dst: Path, reencode: bool) -> None:
    start_s = start_ms / 1000.0
    duration_s = (end_ms - start_ms) / 1000.0
    if reencode:
        cmd = [
            "ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
            "-ss", f"{start_s:.3f}",
            "-i", str(src),
            "-t", f"{duration_s:.3f}",
            "-c:v", "libx264", "-preset", "veryfast", "-crf", "20",
            "-c:a", "aac", "-b:a", "128k",
            "-movflags", "+faststart",
            str(dst),
        ]
    else:
        cmd = [
            "ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
            "-ss", f"{start_s:.3f}",
            "-i", str(src),
            "-t", f"{duration_s:.3f}",
            "-c", "copy",
            str(dst),
        ]
    subprocess.run(cmd, check=True)


def _concat(listfile: Path, dst: Path, reencode: bool) -> None:
    base = [
        "ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
        "-f", "concat", "-safe", "0",
        "-i", str(listfile),
    ]
    if reencode:
        cmd = base + [
            "-c:v", "libx264", "-preset", "veryfast", "-crf", "20",
            "-c:a", "aac", "-b:a", "128k",
            "-movflags", "+faststart",
            str(dst),
        ]
    else:
        cmd = base + ["-c", "copy", "-movflags", "+faststart", str(dst)]
    subprocess.run(cmd, check=True)


def have_ffmpeg() -> bool:
    return shutil.which("ffmpeg") is not None and shutil.which("ffprobe") is not None
