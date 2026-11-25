# FramePilot Test & Verification Report

## Status Summary
- **Implemented**: Project Library, New Project Presets, Document-Space Multi-Touch Canvas (Pencil, Pen, Brush, Partial Eraser, Fill, Shapes, Text, Select/Transform, Lasso, Eyedropper, Hand, Grid, 9:16 Safe Area), Layers & Backgrounds (including Photo Picker), Chronological Frame-by-Frame Timeline (Add Blank, Duplicate, Delete, Reorder, Exposure, Onion Skin, Frame Notes, Playback), Motion Studio (Simple & Advanced controls, Orbit, Path Drawing, 32-pt Shape Morph Resampling, Particles, 2D Pose Helper, Camera Zoom/Pan/Shake), Multi-Scene Reel Planner, AI Semantic Inspector, Canonical OpenCode Package Export & Round-Trip ZIP/JSON Import, Sample Reel & 8-Cel Basketball Regression Fixture.
- **Tested (Executed & Passed)**:
  - `gradle :app:testDebugUnitTest` — **18/18 tests passed**:
    - `FrameSequenceAndRegressionTests` (Tests 1–8: initial sequence `1..5`, duplicate, insert blank, delete, reorder, forward vs explicit reverse playback, exposure ticks, undo/redo, plus the 8-cel Basketball regression fixture with 14 exposure ticks, 30 FPS, last-frame note, and lossless ZIP round-trip).
    - `CanvasMotionAndExportTests` (inverse coordinate transforms, centroid-anchored pinch-zoom, partial stroke splitting eraser, orbit/path/morph evaluator, top-left spatial map & honest `unlabelled` warnings, full canonical ZIP package export & round-trip import).
    - `StudioRobolectricUiTests` (Robolectric Compose UI journey: create project -> Draw Frames add/duplicate/delete -> Motion Studio -> OpenCode Export Preview).
    - `ExampleRobolectricTest` & `ExampleUnitTest`.
- **Built**: `gradle :app:assembleDebug` succeeded, producing `app/build/outputs/apk/debug/app-debug.apk`.
- **Installed / Not Yet Verified on Physical Hardware**: Physical device / streaming emulator touch-gesture and stylus testing has not been executed by the agent (no `adb` or emulator access inside the build container).
