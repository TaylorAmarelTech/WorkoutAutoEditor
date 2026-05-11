# WorkoutAutoEditor

Offline, on-device auto-editor for workout videos. Films a session, understands
what you did, follows your plain-English editing instructions, and renders a cut.
Everything runs locally - pose estimation via MediaPipe, instruction parsing
via Gemma 3 (small), video render via Media3 Transformer. No network at runtime
beyond the one-time model download.

## What it does

You film a workout. You type an instruction like *"Keep my heaviest squats and
presses, drop everything else, under 90 seconds total."* The app parses that
into a structured policy, asks you to confirm, then runs:

1. **Pose pass** - MediaPipe Pose Landmarker over the clip at 5 fps
2. **Audio pass** - RMS envelope for set-boundary detection
3. **Timeline build** - windowed exercise classification + per-rep state machines
4. **Keyframe review** - Gemma annotates a small set of ambiguous segments
5. **Cut-list** - six-stage pipeline applies your policy
6. **Render** - Media3 Transformer composes the surviving segments

Roughly 5-8 minutes of processing for a 10-minute clip on a Pixel 8 Pro plugged in.

## Build

The project builds in GitHub Actions - see `.github/workflows/android.yml`.

```
git push
# then download "WorkoutAutoEditor-debug-apk" artifact from the Actions run
```

To build locally, install JDK 17 + Android cmdline-tools + `platforms;android-35`
and `build-tools;35.0.0`, then:

```
./gradlew :app:assembleDebug
```

APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

## Two things to know up front

1. **First launch downloads the Gemma model** (~500 MB). Wi-Fi only is recommended.
2. **CI auto-populates `pose_landmarker_full.task`** into `app/src/main/assets/`
   before each build. For local builds, drop that file in yourself - URL is in
   `.github/workflows/android.yml`.

## Flow

```
Setup -> Home -> Record -> Plan -> Review -> Process -> Done
                    |        |        |
                    |        |        +- Bound to EditPipelineService
                    |        |           - shows parsed policy
                    |        |           - toggle warmups / rest / idle
                    |        |           - slide caps and target length
                    |        |
                    |        +- Type instruction; tap Start ->
                    |           service.startParsing() (cheap, ~3 s)
                    |
                    +- Or: Home -> Training -> record / label / save
```

## Algorithm at a glance

**Why per-frame VLM is wrong.** Running Gemma vision on every frame would burn
the battery, dominate latency, and produce results no better than what cheap
signals already give us. The architecture instead treats the LLM as a surgical
tool, called only on a small number of ambiguous keyframes after pose+audio
have done the structural work.

**Pose embeddings.** Each pose frame is converted to a 40-dim normalized vector:
translate so hip midpoint is the origin, scale so torso size is 1, then output
(dx, dy) for 20 hand-picked joint pairs. Vector components rather than scalar
distances preserve directional cues (wrist-above-shoulder distinguishes
overhead press from bicep curl).

**KNN with confidence floor.** Weighted k-NN (k=10) with inverse-distance
voting. If even the closest training sample is farther than
`minDistanceForConfidence` (default 2.5), the classifier returns UNKNOWN
rather than confidently mislabeling. The pipeline falls back to rules in that case.

**Six-stage cut list.** Filter by class -> drop low-rep / low-quality ->
group/warmup handling -> per-exercise cap -> merge & pad -> trim to target total.
Each stage has explicit rules; see `CutListBuilder.kt`.

## What's in the box

- Production-ready algorithmic core: pose embedding, k-NN, rep state machines,
  exercise classifier (KNN + rule fallback), cut-list builder, Media3 render.
- Service-bound state machine: `EditPipelineService` with explicit phases
  (IDLE -> PARSING -> AWAITING_CONFIRMATION -> PROCESSING -> DONE/FAILED) and
  StateFlows for the UI to observe.
- Compose UI: Setup, Home, Record, Plan, Review (toggles + sliders), Process
  (live progress), Training.
- Tests: `CutListBuilderTest` (6), `RepDetectorTest` (3), `PoseEmbedderTest` (5),
  `KnnClassifierTest` (4).

## What's not in the box

- **A canonical training set** - the Training screen is wired up, but you
  provide samples. Without training, the rule-based classifier handles
  squat / pushup / curl / press.
- **Production signing** - debug builds use the auto-generated debug keystore
  for sideloading. For Play Store, replace with a real keystore in
  `app/build.gradle.kts`.

See `ARCHITECTURE.md` for the full design rationale.
