# FramePilot Canonical Export Format Specification (`schemaVersion = 2`)

## Package Layout (`FramePilot-<slug>/`)
- `README.md` — Project overview and package index
- `START-HERE.md` — Reading order for humans and text-only AI coding agents
- `OPENCODE-PROMPT.md` — Ready-to-use prompt summarizing dimensions, FPS, scenes, layers, motions, and semantic warnings
- `project.json` — Complete lossless canonical `FramePilotProject` JSON
- `project-schema.json` — Machine-readable schema metadata & coordinate rules
- `package-manifest.json` — Export inventory & schema version
- `creative-direction.md` — Reel runtime, transitions, voiceover, captions, music, and SFX cues
- `animation-plan.md` — Unified timebase summary across drawing cels, motion tracks, and camera
- `sketches/spatial-map.md` — Approximate 24×12 ASCII locator grid + exact coordinates
- `frames/frames-sequence.md` — Chronological frame-by-frame drawings, exposures, and notes
- `layers/layers-catalogue.md` — Back-to-front Z-order, visibility, lock, and labels
- `motion/motion-specs.md` — Orbits, motion paths, keyframes, easing, shape morphs, particles, and camera tracks
- `accessibility/missing-info-warnings.md` — Explicit list of `unlabelled` layers, strokes, and backgrounds
- `scenes/scene_<n>_<id>.md` & `.json` — Per-scene spatial breakdown and JSON
- `assets/scene_<n>_<id>.svg` — Supplementary SVG vector snapshot (non-primary)
- `skills/opencode-implementation-guide.md` — Implementation checklist for coding agents

## Coordinate Convention
- **Origin**: `(0, 0)` at **TOP-LEFT** of the canvas.
- **Axes**: `+X` increases **RIGHT** (`0..canvasWidth`, normalized `normX` in `0.0..1.0`); `+Y` increases **DOWNWARD** (`0..canvasHeight`, normalized `normY` in `0.0..1.0`).

## Semantic Honesty Rule
- Elements without creator-authored descriptions are explicitly marked `unlabelled` and listed in `accessibility/missing-info-warnings.md`. FramePilot never fabricates object recognition.
