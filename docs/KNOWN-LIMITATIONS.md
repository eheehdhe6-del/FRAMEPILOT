# FramePilot Known Limitations

1. **Video / MP4 Rendering**: FramePilot is an authoring, animation-direction, and AI-package export studio; it previews animations live on-canvas but does not encode `.mp4` or `.gif` video files natively on-device.
2. **Audio Mixing**: `AudioPlan` stores voiceover scripts, caption text, music notes, pause instructions, and timestamped SFX cues for OpenCode and Reel production, but does not include a waveform audio recorder or mixer.
3. **2D Character Pose Helper**: `SkeletonPose` stores named 2D joint coordinates and renders stick-figure bones/joints for posing and AI export; it is not a full mesh-skinning or inverse-kinematics rigging engine.
4. **Shape Morphing**: `ShapeMorphConfig` previews and exports shape transformations using 32-point perimeter polyline resampling (`approximate_polyline_resampling`), not topology-preserving Bezier spline matching.
5. **Physical Device Testing**: All automated tests run via JUnit4 and Robolectric on the JVM; physical-device / streaming-emulator touch gesture testing has not been directly executed by the agent inside this build container (no ADB/emulator access in execution environment).
