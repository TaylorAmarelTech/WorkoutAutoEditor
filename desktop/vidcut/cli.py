"""vidcut command-line interface."""
from __future__ import annotations

from pathlib import Path

import typer
from rich.console import Console
from rich.table import Table

from vidcut import editor, llm, pipeline

app = typer.Typer(
    add_completion=False,
    help="Local Gemma-powered video editor (Ollama + ffmpeg).",
)
console = Console()


def _check_ffmpeg() -> None:
    if not editor.have_ffmpeg():
        console.print("[red]ffmpeg / ffprobe not found on PATH.[/red]")
        console.print("Install: https://ffmpeg.org/download.html")
        raise typer.Exit(code=2)


@app.command()
def plan(
    source: Path = typer.Argument(..., exists=True, dir_okay=False, readable=True, help="Input video"),
    prompt: str = typer.Option(..., "--prompt", "-p", help="Editing instruction"),
    model: str = typer.Option(llm.DEFAULT_MODEL, "--model", "-m", help="Ollama model"),
    threshold: float = typer.Option(0.30, "--threshold", "-t", help="Scene change threshold (0.10 - 0.50)"),
):
    """Compute a cut plan and print it. Does not render."""
    _check_ffmpeg()
    console.print(f"[dim]Probing[/dim] {source.name}")
    result = pipeline.plan_only(source, prompt, model=model, scene_threshold=threshold)
    _print_plan(result)


@app.command()
def edit(
    source: Path = typer.Argument(..., exists=True, dir_okay=False, readable=True, help="Input video"),
    output: Path = typer.Option(..., "--output", "-o", help="Output mp4"),
    prompt: str = typer.Option(..., "--prompt", "-p", help="Editing instruction"),
    model: str = typer.Option(llm.DEFAULT_MODEL, "--model", "-m", help="Ollama model"),
    threshold: float = typer.Option(0.30, "--threshold", "-t", help="Scene change threshold"),
    fast: bool = typer.Option(False, "--fast", help="Stream-copy without re-encoding (only works on keyframe boundaries)"),
):
    """Plan and render the edit to OUTPUT."""
    _check_ffmpeg()
    console.print(f"[dim]Editing[/dim] {source.name} -> {output.name}")
    result = pipeline.edit(
        source, prompt, output,
        model=model, scene_threshold=threshold, reencode=not fast,
    )
    _print_plan(result)
    console.print(f"[green]Wrote[/green] {result.output} ({result.plan.total_ms / 1000:.1f}s)")


@app.command()
def serve(
    host: str = typer.Option("127.0.0.1", help="Bind host"),
    port: int = typer.Option(7860, help="Bind port"),
    share: bool = typer.Option(False, "--share", help="Open a public Gradio share link"),
):
    """Launch the Gradio web UI at http://host:port/."""
    try:
        from vidcut.webapp import launch
    except ImportError as e:
        console.print(f"[red]Gradio not installed.[/red] Run: pip install 'vidcut[ui]'")
        raise typer.Exit(code=2) from e
    launch(host=host, port=port, share=share)


def _print_plan(result) -> None:
    table = Table(title=f"Cut plan ({len(result.plan.spans)} spans, {result.plan.total_ms / 1000:.1f}s total)")
    table.add_column("#", justify="right")
    table.add_column("Start")
    table.add_column("End")
    table.add_column("Duration")
    table.add_column("Rationale", overflow="fold")
    for i, s in enumerate(result.plan.spans):
        table.add_row(
            str(i),
            f"{s.start_ms / 1000:.2f}s",
            f"{s.end_ms / 1000:.2f}s",
            f"{s.duration_ms / 1000:.2f}s",
            s.rationale or "-",
        )
    console.print(table)
    if result.plan.summary:
        console.print(f"[bold]Summary:[/bold] {result.plan.summary}")


if __name__ == "__main__":
    app()
