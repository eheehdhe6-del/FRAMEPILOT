# FramePilot Technical Architecture

## Package Structure (`com.example`)
- `domain/model/ProjectModels.kt`: Canonical `schemaVersion = 2` data classes (`FramePilotProject`, `Scene`, `BackgroundSpec`, `Layer`, `DrawingStroke`, `SceneShape`, `SceneText`, `SkeletonPose`, `AnimationFrame`, `MotionTrack`, `CameraTrack`, `AudioPlan`) with Moshi codegen adapters and stable UUID identities.
- `engine/`
  - `CanvasTransformEngine.kt`: Document-space inverse coordinate transforms (`screenToDocument`, `documentToScreen`), centroid-anchored pinch-zoom, fit-to-viewport, polyline segment splitting for partial stroke erasing, and hit-testing.
  - `TimelineEngine.kt`: Single source of truth for chronological frame ordering (`orderIndex`), derived visible numbering (`index + 1`), duplicate/insert/delete/reorder operations, exposure tick expansion, and strict left-to-right playback (unless `reverse = true`).
  - `MotionEvaluator.kt`: Deterministic evaluator for easing curves, arc-length path sampling, clockwise/counter-clockwise orbits, 32-point polyline shape morph resampling, particles, keyframes, and camera interpolation.
  - `UndoRedoManager.kt`: Bounded (60-step) immutable project snapshot history covering drawing, erasing, layers, frames, motion, and object transforms.
  - `SpatialMapGenerator.kt`: Text-only spatial coordinate map generator (`Origin: top-left (0,0), +X right, +Y down`), 24×12 ASCII locator grid, exact coordinate stroke/shape catalogues, depth relationships, and honest `UNLABELLED` warnings.
- `data/`
  - `ProjectSerializer.kt`: Deterministic Moshi JSON serialization with v1 migration support.
  - `ProjectStore.kt`: Thread-safe (`Mutex`) atomic file persistence (`.tmp` write + rename) under `filesDir/projects/<id>/project.json` + DataStore user preferences.
  - `PackageExporter.kt` & `PackageImporter.kt`: Canonical folder/ZIP exporter and lossless round-trip importer.
  - `SampleProjects.kt`: Built-in "Park Butterfly Reel" sample and the "8-Cel Basketball" regression fixture.
- `ui/`
  - `theme/`: Warm Paper-First Material 3 color palette (`PaperCream`, `StudioCoral`, `StudioLavender`, `StudioMint`) with bundled `Outfit`, `Plus Jakarta Sans`, and `JetBrains Mono` fonts.
  - `components/StudioRenderHelpers.kt`: Shared `DrawScope` renderer used by `StudioCanvas`, project thumbnails, and frame thumbnails.
  - `library/`: `LibraryViewModel`, `ProjectLibraryScreen`, `NewProjectSheet`.
  - `studio/`: `StudioViewModel` (debounced 700ms autosave + lifecycle flush), `StudioScreen`, `StudioCanvas`, `TimelinePanel`, `LayersSheet`, `InspectorAndMotionSheets`.
  - `export/`: `ExportPreviewScreen` with clipboard copy, SAF ZIP export, and `FileProvider` share.
