package com.example.data

import com.example.domain.model.BackgroundType
import com.example.domain.model.CURRENT_SCHEMA_VERSION
import com.example.domain.model.DrawingTool
import com.example.domain.model.FramePilotProject
import com.example.domain.model.Scene
import com.example.domain.model.ShapeType
import com.example.engine.MotionEvaluator
import com.example.engine.RigEngine
import com.example.engine.SpatialMapGenerator
import com.example.engine.TimelineEngine
import com.squareup.moshi.Types
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

data class ExportOptions(
    val sketchAsDirectionOnly: Boolean = true,
    val externalStyleGuideNote: String = ""
)

data class ExportedPackage(
    val rootFolderName: String,
    val files: Map<String, String>
)

object PackageExporter {

    fun generatePackage(
        project: FramePilotProject,
        options: ExportOptions = ExportOptions(
            sketchAsDirectionOnly = project.exportSketchAsDirectionOnly,
            externalStyleGuideNote = project.exportStyleGuideNote
        )
    ): ExportedPackage {
        val cleanStyleNote = options.externalStyleGuideNote.trim()
        val effectiveOptions = options.copy(externalStyleGuideNote = cleanStyleNote)
        val normalized = ProjectSerializer.normalizeProject(
            project.copy(
                exportSketchAsDirectionOnly = effectiveOptions.sketchAsDirectionOnly,
                exportStyleGuideNote = cleanStyleNote
            )
        )
        val safeName = normalized.name.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "framepilot-project" }
        val root = "FramePilot-$safeName"

        val files = linkedMapOf<String, String>()
        val projectJson = ProjectSerializer.toJson(normalized)
        val warnings = SpatialMapGenerator.collectWarnings(normalized)

        files["project.json"] = projectJson
        files["project-schema.json"] = buildSchemaJson()
        files["README.md"] = buildReadme(normalized, effectiveOptions)
        files["START-HERE.md"] = buildStartHere(normalized, warnings.size, effectiveOptions)
        files["OPENCODE-PROMPT.md"] = buildOpenCodePrompt(normalized, warnings, effectiveOptions)
        files["creative-direction.md"] = buildCreativeDirection(normalized)
        files["animation-plan.md"] = buildAnimationPlan(normalized)
        files["sketches/spatial-map.md"] = buildAllSpatialMaps(normalized, effectiveOptions)
        files["frames/frames-sequence.md"] = buildAllFramesMarkdown(normalized)
        files["layers/layers-catalogue.md"] = buildLayersCatalogue(normalized)
        files["motion/motion-specs.md"] = buildMotionSpecs(normalized)
        files["accessibility/missing-info-warnings.md"] = buildWarningsMarkdown(warnings)
        files["skills/opencode-implementation-guide.md"] = buildOpenCodeSkillGuide(effectiveOptions)

        normalized.scenes.sortedBy { it.orderIndex }.forEachIndexed { idx, scene ->
            val num = idx + 1
            files["scenes/scene_${num}_${scene.id}.md"] = SpatialMapGenerator.generateSceneSpatialMap(
                project = normalized,
                scene = scene,
                sketchAsDirectionOnly = effectiveOptions.sketchAsDirectionOnly,
                externalStyleGuideNote = cleanStyleNote
            )
            files["scenes/scene_${num}_${scene.id}.json"] = ProjectSerializer.moshi.adapter(Scene::class.java).indent("  ").toJson(scene)
            files["assets/scene_${num}_${scene.id}.svg"] = buildSceneSvg(normalized, scene)
        }

        files["package-manifest.json"] = buildManifestJson(normalized, files.keys.toList(), effectiveOptions)
        return ExportedPackage(rootFolderName = root, files = files)
    }

    fun exportToZipBytes(
        project: FramePilotProject,
        options: ExportOptions = ExportOptions(
            sketchAsDirectionOnly = project.exportSketchAsDirectionOnly,
            externalStyleGuideNote = project.exportStyleGuideNote
        )
    ): ByteArray {
        val pkg = generatePackage(project, options)
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            pkg.files.forEach { (relPath, content) ->
                val entry = ZipEntry("${pkg.rootFolderName}/$relPath")
                zos.putNextEntry(entry)
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    private fun buildReadme(project: FramePilotProject, options: ExportOptions): String = buildString {
        appendLine("# ${project.name} — FramePilot Production Package")
        appendLine()
        appendLine("This canonical FramePilot package contains complete geometric, structural, semantic, and timing instructions for text-only AI coding agents (such as OpenCode) and supports lossless re-import into FramePilot.")
        appendLine()
        appendLine("## Project Overview")
        appendLine("- **Project ID**: `${project.id}`")
        appendLine("- **Schema Version**: `$CURRENT_SCHEMA_VERSION`")
        appendLine("- **Canvas**: ${project.canvasWidth} × ${project.canvasHeight} (${project.aspectRatioLabel})")
        appendLine("- **Frame Rate**: ${project.fps} FPS")
        appendLine("- **Scenes**: ${project.scenes.size} | **Total Duration**: ${project.totalDurationMs} ms")
        appendLine("- **Coordinate Convention**: Origin `(0, 0)` at top-left, `+X` right, `+Y` down.")
        appendLine(
            "- **Sketch Interpretation Mode**: " + if (options.sketchAsDirectionOnly) {
                "Direction-Only Blueprint (rough sketches & stickman rigs specify layout, pose, and motion only; follow user's skill.md or target style for final visual craft)"
            } else {
                "Faithful Sketch Reconstruction (closely preserve drawn shapes, strokes, and style)"
            }
        )
        if (options.sketchAsDirectionOnly && options.externalStyleGuideNote.isNotBlank()) {
            appendLine("- **Target Style / `skill.md` Reference**: ${options.externalStyleGuideNote}")
        }
        appendLine()
        appendLine("## Package Contents")
        appendLine("- `START-HERE.md` — Reading order for humans and AI coding agents")
        appendLine("- `OPENCODE-PROMPT.md` — Ready-to-paste prompt with complete scene, coordinate, and motion context")
        appendLine("- `project.json` — Lossless canonical FramePilot v$CURRENT_SCHEMA_VERSION project data")
        appendLine("- `project-schema.json` — Field definitions and coordinate convention")
        appendLine("- `creative-direction.md` — Reel/Short plan, voiceover, captions, music, and SFX")
        appendLine("- `animation-plan.md` — Unified timeline across backgrounds, characters, frames, motion, text, and camera")
        appendLine("- `sketches/spatial-map.md` — True 2D Visual ASCII Canvas Render + exact geometry & stroke polylines")
        appendLine("- `frames/frames-sequence.md` — Chronological frame-by-frame visual renders, shapes, poses & drawings")
        appendLine("- `layers/layers-catalogue.md` — Z-ordered layer hierarchy & depth relationships")
        appendLine("- `motion/motion-specs.md` — Orbits, paths, keyframes, easing, morphs, particles & camera")
        appendLine("- `accessibility/missing-info-warnings.md` — Honest list of unlabelled items")
        appendLine("- `assets/*.svg` — Full vector SVG scene snapshots (shapes, smoothed strokes, poses, gradients)")
    }

    private fun buildStartHere(project: FramePilotProject, warningCount: Int, options: ExportOptions): String = buildString {
        appendLine("# START HERE — OpenCode & AI Agent Reading Order")
        appendLine()
        appendLine("You do **NOT** need vision or image-reading capabilities to see or build this animation.")
        appendLine()
        if (options.sketchAsDirectionOnly) {
            appendLine("> **CRITICAL ART DIRECTION RULE (SKETCH = DIRECTION ONLY)**: The simplicity or roughness of the drawings and stickman figures in this export must **NOT** equal the visual quality of your output. Treat every rough stroke, basic shape, and stickman rig strictly as spatial layout, pose, scale, and motion direction. If the user supplied a `skill.md` or style guide, **follow that `skill.md` / target style**—not the raw stickman/sketch style.")
            if (options.externalStyleGuideNote.isNotBlank()) {
                appendLine("> **Target Style / `skill.md` Note**: ${options.externalStyleGuideNote}")
            }
            appendLine()
        }
        appendLine("1. Read `OPENCODE-PROMPT.md` for the self-contained 2D Visual ASCII Canvas Render, palette, exact geometry, stroke traces, poses, cels, and motion plan.")
        appendLine("2. Inspect `creative-direction.md` for Reel timing, voiceover, captions, and audio cues.")
        appendLine("3. Read `sketches/spatial-map.md` for full per-scene visual renders, depth order, and polyline geometry.")
        appendLine("4. Read `motion/motion-specs.md` and `frames/frames-sequence.md` for exact movement, easing, and cel timings.")
        appendLine("5. Parse `project.json` or `assets/*.svg` programmatically if generating Remotion, Canvas, SVG/CSS, Lottie, or Compose code directly from geometry.")
        appendLine()
        appendLine("> **Missing Information Warnings**: $warningCount item(s) flagged in `accessibility/missing-info-warnings.md`. Any unlabelled element is explicitly marked `unlabelled`—never guess its identity.")
    }

    private fun buildOpenCodePrompt(
        project: FramePilotProject,
        warnings: List<com.example.engine.MissingInfoWarning>,
        options: ExportOptions
    ): String = buildString {
        appendLine("# OpenCode Production Prompt — ${project.name}")
        appendLine()
        if (options.sketchAsDirectionOnly) {
            appendLine("## IMPORTANT: Sketch Fidelity ≠ Final Output Style (Direction Only)")
            appendLine("> **READ FIRST — DO NOT COPY ROUGH SKETCH OR STICKMAN STYLE**:")
            appendLine("> - **Directional Blueprint Only**: Every rough freehand stroke, crude shape, and 2D stickman figure below is **strictly a spatial layout, character pose/blocking, relative proportion, and motion direction**—**NOT** the target visual style or final rendering quality.")
            appendLine("> - **Never Downgrade Output to Match Sketch Simplicity**: Even if the sketch looks simple, wobbly, ugly, or stickman-like, do **NOT** render crude stick figures or rough placeholder art as the final output (unless explicitly requested).")
            appendLine("> - **Follow External `skill.md` & Style Specs**: If the user provided a `skill.md`, design system, or visual style instructions alongside this export, **strictly follow that `skill.md` / style guide** for characters, backgrounds, shading, typography, and polish—using the FramePilot coordinates, poses, and motion tracks purely as the blueprint for *placement*, *pose*, and *movement*.")
            if (options.externalStyleGuideNote.isNotBlank()) {
                appendLine("> - **Creator Style / `skill.md` Directive**: ${options.externalStyleGuideNote}")
            }
            appendLine()
        } else {
            appendLine("> **Visual Mode — Faithful Sketch Reconstruction**: Closely preserve the drawn shapes, strokes, proportions, and visual style captured below.")
            appendLine()
        }
        appendLine("> **Zero-Vision Complete Visual Specification**: This self-contained prompt gives text-only AI models (including OpenCode) exact sight of everything drawn on the canvas using an aspect-accurate 2D Visual ASCII Canvas Render, extracted color palette, geometric shape classifications, simplified polyline coordinates, vector shapes, 2D skeleton joints, frame-by-frame cels, and motion/camera specs.")
        appendLine()
        appendLine("- **Target Format**: ${project.canvasWidth}×${project.canvasHeight} (${project.aspectRatioLabel}) at ${project.fps} FPS")
        appendLine("- **Total Duration**: ${project.totalDurationMs} ms across ${project.scenes.size} scene(s)")
        appendLine("- **Coordinate System**: Origin `(0, 0)` at **TOP-LEFT**. `+X` increases **RIGHT** (`0..${project.canvasWidth}`, norm `0.0..1.0`). `+Y` increases **DOWNWARD** (`0..${project.canvasHeight}`, norm `0.0..1.0`).")
        if (project.creativeBrief.isNotBlank()) {
            appendLine("- **Creator Brief**: ${project.creativeBrief}")
        }
        appendLine()
        project.scenes.sortedBy { it.orderIndex }.forEachIndexed { idx, scene ->
            val orderedLayers = scene.orderedLayers()
            val orderedFrames = TimelineEngine.normalizeOrder(scene.frames)
            val firstFrame = orderedFrames.firstOrNull()

            appendLine("## Scene ${idx + 1}: ${scene.name} (${scene.effectiveDurationMs(project.fps)} ms)")
            appendLine("- **Background**: ${scene.background.type.label} (`${scene.background.primaryColorHex}` -> `${scene.background.secondaryColorHex}`, angle=${SpatialMapGenerator.fmt(scene.background.gradientAngleDeg)}°) — ${scene.background.environmentDescription.ifBlank { "unlabelled background" }}")
            if (scene.background.elementsDescription.isNotEmpty()) {
                appendLine("- **Background Elements**: ${scene.background.elementsDescription.joinToString("; ")}")
            }
            val palette = collectScenePalette(scene)
            if (palette.isNotEmpty()) {
                appendLine("- **Colors Used**: ${palette.joinToString(", ") { "`$it`" }}")
            }
            appendLine()

            appendLine("### 2D Visual Canvas Render (What the Creator Sees)")
            if (options.sketchAsDirectionOnly) {
                appendLine("_Compositional & pose direction only: use these positions, proportions, and silhouettes as blocking while rendering final visuals in your target `skill.md` / production style, not raw stickman or rough-sketch quality._")
            }
            appendLine("```text")
            appendLine(SpatialMapGenerator.buildVisualAsciiCanvas(project.canvasWidth, project.canvasHeight, orderedLayers, firstFrame))
            appendLine("```")
            appendLine()

            appendLine("### Layers & Exact Visual Geometry (Back-to-Front Z-Order)")
            val firstFramePoses = RigEngine.buildActivePosesByLayer(scene, firstFrame)
            if (orderedLayers.isEmpty()) {
                appendLine("_No layers in scene._")
                appendLine()
            } else {
                orderedLayers.forEachIndexed { zIdx, layer ->
                    SpatialMapGenerator.appendLayerVisualDetails(
                        sb = this,
                        project = project,
                        orderedLayers = orderedLayers,
                        zIdx = zIdx,
                        layer = layer,
                        maxPolylinePts = 12,
                        activePosesByLayer = firstFramePoses
                    )
                }
            }

            val hasCelArtwork = orderedFrames.any {
                it.layerDrawings.isNotEmpty() || it.layerShapes.isNotEmpty() || it.layerPoses.isNotEmpty()
            }
            if (orderedFrames.size > 1 || hasCelArtwork) {
                appendLine("### Frame-by-Frame Cels (${orderedFrames.size} cel(s), ${scene.totalExposureTicks()} ticks @ ${project.fps} FPS)")
                var tickCursor = 0
                orderedFrames.forEachIndexed { fIdx, frame ->
                    val startMs = (tickCursor * 1000L) / project.fps.coerceAtLeast(1)
                    tickCursor += frame.exposureFrames.coerceAtLeast(1)
                    val endMs = (tickCursor * 1000L) / project.fps.coerceAtLeast(1)
                    val celStrokes = frame.layerDrawings.values.sumOf { it.size }
                    val celShapes = frame.layerShapes.values.sumOf { it.size }
                    appendLine("- **Frame ${fIdx + 1}** (`${frame.id}`, ${frame.exposureFrames} tick(s), ${startMs}ms..${endMs}ms): $celStrokes stroke(s), $celShapes shape(s), ${frame.layerPoses.size} pose(s)${if (frame.note.isNotBlank()) " — ${frame.note}" else ""}")
                    if (celStrokes > 0 || celShapes > 0 || frame.layerPoses.isNotEmpty()) {
                        if (fIdx > 0 || orderedFrames.size > 1) {
                            appendLine("```text")
                            appendLine(SpatialMapGenerator.buildVisualAsciiCanvas(project.canvasWidth, project.canvasHeight, orderedLayers, frame))
                            appendLine("```")
                        }
                        frame.layerShapes.forEach { (layerId, shapes) ->
                            val layerName = scene.layers.find { it.id == layerId }?.name ?: layerId
                            shapes.forEach { sh ->
                                appendLine("  - [$layerName cel shape] " + SpatialMapGenerator.summarizeShape(sh, project.canvasWidth, project.canvasHeight))
                            }
                        }
                        frame.layerPoses.forEach { (layerId, pose) ->
                            val layerName = scene.layers.find { it.id == layerId }?.name ?: layerId
                            val jointsSummary = pose.joints.entries.sortedBy { it.key }
                                .joinToString(", ") { (k, pt) -> "$k=(${SpatialMapGenerator.fmt(pt.x)},${SpatialMapGenerator.fmt(pt.y)})" }
                            appendLine("  - [$layerName cel pose '${pose.name}']: $jointsSummary")
                        }
                        frame.layerDrawings.forEach { (layerId, strokes) ->
                            val layerName = scene.layers.find { it.id == layerId }?.name ?: layerId
                            strokes.forEach { st ->
                                appendLine("  - [$layerName cel stroke] " + SpatialMapGenerator.summarizeStroke(st, project.canvasWidth, project.canvasHeight, maxTracePoints = 12))
                            }
                        }
                    }
                }
                appendLine()
            }

            if (scene.motionTracks.isNotEmpty()) {
                appendLine("### Motion Tracks")
                scene.motionTracks.forEach { tr ->
                    appendLine("- **${tr.name}** -> target=`${tr.targetId}` (${tr.targetType.label}, ${tr.actionType.label}, ${tr.startTimeMs}ms..${tr.startTimeMs + tr.durationMs}ms, repeat=${tr.repeatCount}, pingPong=${tr.pingPong}, easing=${tr.easing.name}): ${tr.description}")
                    tr.orbitConfig?.let { o ->
                        appendLine("  - Orbit: target=`${o.targetName.ifBlank { o.targetId }}` (`${o.targetId}`), dir=${o.direction.name}, radius=${o.radiusPx}px, revolutions=${o.revolutions}, squash=${o.verticalSquash}")
                    }
                    if (tr.pathPoints.isNotEmpty()) {
                        appendLine("  - Path (${tr.pathPoints.size} pts, orient=${tr.orientToPath}): " + tr.pathPoints.joinToString(" -> ") { "(${it.x.toInt()},${it.y.toInt()})" })
                    }
                    tr.morphConfig?.let { m ->
                        appendLine("  - Morph: ${m.sourceShape.name} (${m.sourceWidth}×${m.sourceHeight}, ${m.sourceColorHex}) -> ${m.targetShape.name} (${m.targetWidth}×${m.targetHeight}, ${m.targetColorHex}) at (${m.centerX}, ${m.centerY})")
                    }
                    tr.particleConfig?.let { p ->
                        appendLine("  - Particles: ${p.style}, origin=(${p.originX}, ${p.originY}), count=${p.count}, spread=${p.spreadRadius}px, color=`${p.colorHex}`, gravity=${p.gravity}")
                    }
                    if (tr.keyframes.isNotEmpty()) {
                        appendLine("  - Keyframes: " + tr.keyframes.sortedBy { it.timeMs }.joinToString("; ") { k ->
                            "@${k.timeMs}ms(dx=${k.offsetX},dy=${k.offsetY},rot=${k.rotationDeg}°,sx=${k.scaleX},sy=${k.scaleY},op=${k.opacity})"
                        })
                    }
                }
                appendLine()
            }
            if (scene.cameraTrack.keyframes.isNotEmpty()) {
                appendLine("### Camera")
                appendLine("- **Summary**: ${scene.cameraTrack.directionSummary.ifBlank { "${scene.cameraTrack.keyframes.size} keyframe(s)" }}")
                scene.cameraTrack.keyframes.sortedBy { it.timeMs }.forEach { k ->
                    appendLine("  - @${k.timeMs}ms: pan=(${k.panX}, ${k.panY}), zoom=${k.zoom}x, rot=${k.rotationDeg}°, shake=${k.shakeIntensity} ${k.note}")
                }
                appendLine()
            }
            if (scene.audioPlan.voiceoverText.isNotBlank() || scene.audioPlan.captionsText.isNotBlank()) {
                appendLine("### Audio & Captions")
                if (scene.audioPlan.voiceoverText.isNotBlank()) appendLine("- **Voiceover**: \"${scene.audioPlan.voiceoverText}\"")
                if (scene.audioPlan.captionsText.isNotBlank()) appendLine("- **Captions**: \"${scene.audioPlan.captionsText}\"")
                appendLine()
            }
        }
        if (warnings.isNotEmpty()) {
            appendLine("## Semantic Honesty Warnings (${warnings.size})")
            warnings.take(10).forEach { w ->
                appendLine("- [${w.severity}] ${w.targetName}: ${w.message}")
            }
        }
    }

    private fun collectScenePalette(scene: Scene): List<String> {
        val colors = linkedSetOf<String>()
        colors.add(scene.background.primaryColorHex)
        if (scene.background.type == BackgroundType.GRADIENT) {
            colors.add(scene.background.secondaryColorHex)
        }
        scene.orderedLayers().forEach { layer ->
            layer.sceneStrokes.forEach { st ->
                colors.add(st.colorHex)
                if (st.isFilledLoop && !st.fillColorHex.isNullOrBlank()) colors.add(st.fillColorHex)
            }
            layer.sceneShapes.forEach { sh ->
                colors.add(sh.strokeColorHex)
                if (sh.filled && !sh.fillColorHex.isNullOrBlank()) colors.add(sh.fillColorHex)
            }
            layer.sceneTexts.forEach { tx -> colors.add(tx.colorHex) }
        }
        scene.frames.forEach { f ->
            f.layerDrawings.values.flatten().forEach { st ->
                colors.add(st.colorHex)
                if (st.isFilledLoop && !st.fillColorHex.isNullOrBlank()) colors.add(st.fillColorHex)
            }
            f.layerShapes.values.flatten().forEach { sh ->
                colors.add(sh.strokeColorHex)
                if (sh.filled && !sh.fillColorHex.isNullOrBlank()) colors.add(sh.fillColorHex)
            }
        }
        return colors.filter { it.isNotBlank() }
    }

    private fun buildCreativeDirection(project: FramePilotProject): String = buildString {
        appendLine("# Creative Direction & Reel Production Plan — ${project.name}")
        appendLine()
        appendLine("- **Aspect Ratio**: ${project.aspectRatioLabel} (${project.canvasWidth}×${project.canvasHeight})")
        appendLine("- **Total Runtime**: ${project.totalDurationMs} ms (${project.scenes.size} scene(s))")
        if (project.creativeBrief.isNotBlank()) {
            appendLine("- **Overall Brief**: ${project.creativeBrief}")
        }
        appendLine()
        var cursorMs = 0L
        project.scenes.sortedBy { it.orderIndex }.forEachIndexed { idx, scene ->
            val dur = scene.effectiveDurationMs(project.fps)
            appendLine("## Scene ${idx + 1}: ${scene.name} (`${cursorMs}ms .. ${cursorMs + dur}ms`)")
            cursorMs += dur
            appendLine("- **Transition Out**: Hard Cut (0ms)")
            appendLine("- **Background Environment**: ${scene.background.environmentDescription.ifBlank { "unlabelled" }}")
            appendLine("- **Camera Direction**: ${scene.cameraTrack.directionSummary.ifBlank { "Static camera" }}")
            appendLine("- **Voiceover**: ${scene.audioPlan.voiceoverText.ifBlank { "(none)" }}")
            appendLine("- **On-Screen Captions**: ${scene.audioPlan.captionsText.ifBlank { "(none)" }}")
            appendLine("- **Music**: ${scene.audioPlan.musicTrackNote.ifBlank { "(none)" }}")
            appendLine("- **Pauses / Silence**: ${scene.audioPlan.pausesAndSilenceNote.ifBlank { "(none)" }}")
            if (scene.audioPlan.soundEffects.isNotEmpty()) {
                appendLine("- **Sound Effect Cues**:")
                scene.audioPlan.soundEffects.forEach { cue ->
                    appendLine("  - @${cue.timeMs}ms [${cue.type}]: ${cue.description}")
                }
            }
            if (scene.notes.isNotBlank()) {
                appendLine("- **Scene Notes**: ${scene.notes}")
            }
            appendLine()
        }
    }

    private fun buildAnimationPlan(project: FramePilotProject): String = buildString {
        appendLine("# Unified Animation Plan — ${project.name}")
        appendLine()
        appendLine("All drawing frames, character poses, object paths, shape morphs, text, effects, and camera movements share a single millisecond/FPS project timebase.")
        appendLine()
        project.scenes.sortedBy { it.orderIndex }.forEachIndexed { idx, scene ->
            appendLine("## Scene ${idx + 1}: ${scene.name} (${scene.effectiveDurationMs(project.fps)}ms @ ${project.fps} FPS)")
            appendLine("- **Drawing Track**: ${scene.frames.size} frame cel(s), ${scene.totalExposureTicks()} exposure tick(s)")
            appendLine("- **Motion Tracks**: ${scene.motionTracks.size} track(s)")
            scene.motionTracks.forEach { tr ->
                appendLine("  - `${tr.id}` **${tr.name}** -> target=`${tr.targetId}` (${tr.targetType.name}), action=`${tr.actionType.name}`, window=`${tr.startTimeMs}..${tr.startTimeMs + tr.durationMs}ms`, repeat=${tr.repeatCount}, pingPong=${tr.pingPong}, easing=`${tr.easing.name}`")
            }
            appendLine("- **Camera Keyframes**: ${scene.cameraTrack.keyframes.size}")
            appendLine()
        }
    }

    private fun buildAllSpatialMaps(project: FramePilotProject, options: ExportOptions): String = buildString {
        project.scenes.sortedBy { it.orderIndex }.forEach { scene ->
            appendLine(
                SpatialMapGenerator.generateSceneSpatialMap(
                    project = project,
                    scene = scene,
                    sketchAsDirectionOnly = options.sketchAsDirectionOnly,
                    externalStyleGuideNote = options.externalStyleGuideNote
                )
            )
            appendLine("---")
            appendLine()
        }
    }

    private fun buildAllFramesMarkdown(project: FramePilotProject): String = buildString {
        project.scenes.sortedBy { it.orderIndex }.forEach { scene ->
            appendLine(SpatialMapGenerator.generateFrameSequenceMarkdown(project, scene))
        }
    }

    private fun buildLayersCatalogue(project: FramePilotProject): String = buildString {
        appendLine("# Layers & Depth Catalogue — ${project.name}")
        appendLine()
        project.scenes.sortedBy { it.orderIndex }.forEachIndexed { sIdx, scene ->
            appendLine("## Scene ${sIdx + 1}: ${scene.name}")
            scene.orderedLayers().forEachIndexed { z, layer ->
                appendLine("- **Z=$z `${layer.name}`** (`${layer.id}`, ${layer.category.label}): visible=${layer.visible}, locked=${layer.locked}, opacity=${layer.opacity}, label=${layer.semantic.displayLabel("unlabelled")}")
            }
            appendLine()
        }
    }

    private fun buildMotionSpecs(project: FramePilotProject): String = buildString {
        appendLine("# Motion Graphics Specifications — ${project.name}")
        appendLine()
        project.scenes.sortedBy { it.orderIndex }.forEachIndexed { sIdx, scene ->
            appendLine("## Scene ${sIdx + 1}: ${scene.name}")
            if (scene.motionTracks.isEmpty() && scene.cameraTrack.keyframes.isEmpty()) {
                appendLine("_No motion or camera tracks authored for this scene._")
            }
            scene.motionTracks.forEach { tr ->
                appendLine("### ${tr.name} (`${tr.id}`)")
                appendLine("- **Action**: ${tr.actionType.label} | **Target**: `${tr.targetId}` (${tr.targetType.label})")
                appendLine("- **Timing**: start=${tr.startTimeMs}ms, duration=${tr.durationMs}ms, repeat=${tr.repeatCount}, pingPong=${tr.pingPong}, easing=${tr.easing.name}")
                if (tr.description.isNotBlank()) appendLine("- **Description**: ${tr.description}")
                tr.orbitConfig?.let { o ->
                    appendLine("- **Orbit**: target=`${o.targetName.ifBlank { o.targetId }}` (`${o.targetId}`), direction=${o.direction.name}, radius=${o.radiusPx}px, revolutions=${o.revolutions}, verticalSquash=${o.verticalSquash}")
                }
                if (tr.pathPoints.isNotEmpty()) {
                    appendLine("- **Motion Path (${tr.pathPoints.size} pts, orientToPath=${tr.orientToPath})**: " + tr.pathPoints.joinToString(" -> ") { "(${it.x.toInt()},${it.y.toInt()})" })
                }
                tr.morphConfig?.let { m ->
                    appendLine("- **Shape Morph**: ${m.sourceShape.name} (${m.sourceWidth}×${m.sourceHeight}, ${m.sourceColorHex}) -> ${m.targetShape.name} (${m.targetWidth}×${m.targetHeight}, ${m.targetColorHex}) at center=(${m.centerX}, ${m.centerY}). Method: `${m.interpolationMethod}` (polyline perimeter resampling, not topology-preserving spline morph).")
                }
                tr.particleConfig?.let { p ->
                    appendLine("- **Particles**: style=${p.style}, origin=(${p.originX}, ${p.originY}), count=${p.count}, spread=${p.spreadRadius}px, color=${p.colorHex}, gravity=${p.gravity}")
                }
                if (tr.keyframes.isNotEmpty()) {
                    appendLine("- **Keyframes**:")
                    tr.keyframes.sortedBy { it.timeMs }.forEach { k ->
                        appendLine("  - @${k.timeMs}ms: offset=(${k.offsetX}, ${k.offsetY}), rot=${k.rotationDeg}°, scale=(${k.scaleX}, ${k.scaleY}), opacity=${k.opacity}, easing=${k.easing.name}")
                    }
                }
                appendLine()
            }
            if (scene.cameraTrack.keyframes.isNotEmpty()) {
                appendLine("### Camera Track")
                appendLine("- **Summary**: ${scene.cameraTrack.directionSummary.ifBlank { "Keyframed camera" }}")
                scene.cameraTrack.keyframes.sortedBy { it.timeMs }.forEach { k ->
                    appendLine("  - @${k.timeMs}ms: pan=(${k.panX}, ${k.panY}), zoom=${k.zoom}x, rot=${k.rotationDeg}°, shake=${k.shakeIntensity}, note=${k.note}")
                }
                appendLine()
            }
        }
    }

    private fun buildWarningsMarkdown(warnings: List<com.example.engine.MissingInfoWarning>): String = buildString {
        appendLine("# Missing Information & Semantic Honesty Report")
        appendLine()
        if (warnings.isEmpty()) {
            appendLine("All layers, strokes, and backgrounds in this project have creator-authored descriptions.")
        } else {
            appendLine("The following elements do not yet have creator descriptions. FramePilot explicitly reports them as `unlabelled` rather than guessing:")
            appendLine()
            warnings.forEach { w ->
                appendLine("- **[${w.severity}]** ${w.targetName} (`${w.targetId}`): ${w.message}")
            }
        }
    }

    private val svgPoseBones = listOf(
        "head" to "neck",
        "neck" to "chest",
        "chest" to "hip",
        "neck" to "leftShoulder",
        "leftShoulder" to "leftElbow",
        "leftElbow" to "leftHand",
        "neck" to "rightShoulder",
        "rightShoulder" to "rightElbow",
        "rightElbow" to "rightHand",
        "hip" to "leftKnee",
        "leftKnee" to "leftFoot",
        "hip" to "rightKnee",
        "rightKnee" to "rightFoot"
    )

    private fun buildSceneSvg(project: FramePilotProject, scene: Scene): String = buildString {
        val w = project.canvasWidth
        val h = project.canvasHeight
        appendLine("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $w $h" width="$w" height="$h">""")
        if (scene.background.type == BackgroundType.GRADIENT) {
            val angleRad = scene.background.gradientAngleDeg * PI / 180.0
            val dx = cos(angleRad) * 0.5
            val dy = sin(angleRad) * 0.5
            val x1Pct = ((0.5 - dx) * 100.0).coerceIn(0.0, 100.0)
            val y1Pct = ((0.5 - dy) * 100.0).coerceIn(0.0, 100.0)
            val x2Pct = ((0.5 + dx) * 100.0).coerceIn(0.0, 100.0)
            val y2Pct = ((0.5 + dy) * 100.0).coerceIn(0.0, 100.0)
            appendLine("""  <defs>""")
            appendLine("""    <linearGradient id="bgGrad_${scene.id}" x1="${x1Pct.toInt()}%" y1="${y1Pct.toInt()}%" x2="${x2Pct.toInt()}%" y2="${y2Pct.toInt()}%">""")
            appendLine("""      <stop offset="0%" stop-color="${scene.background.primaryColorHex}" />""")
            appendLine("""      <stop offset="100%" stop-color="${scene.background.secondaryColorHex}" />""")
            appendLine("""    </linearGradient>""")
            appendLine("""  </defs>""")
            appendLine("""  <rect width="$w" height="$h" fill="url(#bgGrad_${scene.id})" />""")
        } else {
            appendLine("""  <rect width="$w" height="$h" fill="${scene.background.primaryColorHex}" />""")
        }
        val firstFrame = TimelineEngine.normalizeOrder(scene.frames).firstOrNull()
        val activePosesByLayer = RigEngine.buildActivePosesByLayer(scene, firstFrame)
        scene.orderedLayers().filter { it.visible }.forEach { layer ->
            appendLine("""  <g id="${layer.id}" opacity="${layer.opacity}">""")
            val allShapes = (layer.sceneShapes + firstFrame?.layerShapes?.get(layer.id).orEmpty()).map { sh ->
                val targetPose = RigEngine.resolveTargetPose(sh.rigAttachment, layer.id, activePosesByLayer)
                RigEngine.deformShape(sh, targetPose)
            }
            allShapes.forEach { s ->
                val fill = if (s.filled) (s.fillColorHex ?: s.strokeColorHex) else "none"
                val cx = s.x + s.width / 2f
                val cy = s.y + s.height / 2f
                val left = min(s.x, s.x + s.width)
                val top = min(s.y, s.y + s.height)
                val sw = abs(s.width).coerceAtLeast(4f)
                val sh = abs(s.height).coerceAtLeast(4f)
                val transformAttr = if (abs(s.rotationDeg) > 0.01f) """ transform="rotate(${s.rotationDeg} $cx $cy)"""" else ""
                when (s.shapeType) {
                    ShapeType.RECTANGLE ->
                        appendLine("""    <rect x="$left" y="$top" width="$sw" height="$sh" rx="14" ry="14" stroke="${s.strokeColorHex}" stroke-width="${s.strokeWidth}" fill="$fill" opacity="${s.opacity}"$transformAttr />""")
                    ShapeType.ELLIPSE ->
                        appendLine("""    <ellipse cx="$cx" cy="$cy" rx="${sw / 2f}" ry="${sh / 2f}" stroke="${s.strokeColorHex}" stroke-width="${s.strokeWidth}" fill="$fill" opacity="${s.opacity}"$transformAttr />""")
                    ShapeType.LINE ->
                        appendLine("""    <line x1="${s.x}" y1="${s.y}" x2="${s.x + s.width}" y2="${s.y + s.height}" stroke="${s.strokeColorHex}" stroke-width="${s.strokeWidth}" stroke-linecap="round" opacity="${s.opacity}"$transformAttr />""")
                    ShapeType.ARROW -> {
                        val endX = s.x + s.width
                        val endY = s.y + s.height
                        val angle = atan2(endY - s.y, endX - s.x)
                        val headLen = (s.strokeWidth * 3.5f).coerceIn(22f, 54f)
                        val p1x = endX - headLen * cos(angle - 0.5f)
                        val p1y = endY - headLen * sin(angle - 0.5f)
                        val p2x = endX - headLen * cos(angle + 0.5f)
                        val p2y = endY - headLen * sin(angle + 0.5f)
                        appendLine("""    <g opacity="${s.opacity}"$transformAttr>""")
                        appendLine("""      <line x1="${s.x}" y1="${s.y}" x2="$endX" y2="$endY" stroke="${s.strokeColorHex}" stroke-width="${s.strokeWidth}" stroke-linecap="round" />""")
                        appendLine("""      <line x1="$endX" y1="$endY" x2="$p1x" y2="$p1y" stroke="${s.strokeColorHex}" stroke-width="${s.strokeWidth}" stroke-linecap="round" />""")
                        appendLine("""      <line x1="$endX" y1="$endY" x2="$p2x" y2="$p2y" stroke="${s.strokeColorHex}" stroke-width="${s.strokeWidth}" stroke-linecap="round" />""")
                        appendLine("""    </g>""")
                    }
                    ShapeType.POLYGON -> {
                        val poly = MotionEvaluator.sampleShapePolygon(ShapeType.POLYGON, cx, cy, sw, sh, 5)
                        val ptsAttr = poly.joinToString(" ") { "${it.first},${it.second}" }
                        appendLine("""    <polygon points="$ptsAttr" stroke="${s.strokeColorHex}" stroke-width="${s.strokeWidth}" stroke-linejoin="round" fill="$fill" opacity="${s.opacity}"$transformAttr />""")
                    }
                }
            }
            val strokes = (layer.sceneStrokes + firstFrame?.layerDrawings?.get(layer.id).orEmpty()).map { st ->
                val targetPose = RigEngine.resolveTargetPose(st.rigAttachment, layer.id, activePosesByLayer)
                RigEngine.deformStroke(st, targetPose, w, h)
            }
            strokes.forEach { st ->
                if (st.points.isNotEmpty()) {
                    val widthMult = when (st.tool) {
                        DrawingTool.PENCIL -> 0.8f
                        DrawingTool.BRUSH -> 1.55f
                        else -> 1.0f
                    }
                    val avgPressure = st.points.map { it.pressure }.average().toFloat().coerceIn(0.6f, 1.4f)
                    val effWidth = (st.strokeWidth * widthMult).coerceAtLeast(1.5f) * avgPressure

                    if (st.isFilledLoop && !st.fillColorHex.isNullOrBlank() && st.points.size >= 3) {
                        val fillD = st.points.mapIndexed { idx, p -> "${if (idx == 0) "M" else "L"}${p.x} ${p.y}" }.joinToString(" ") + " Z"
                        appendLine("""    <path d="$fillD" fill="${st.fillColorHex}" fill-opacity="${st.opacity}" stroke="none" />""")
                    }
                    if (st.points.size == 1) {
                        val p = st.points.first()
                        appendLine("""    <circle cx="${p.x}" cy="${p.y}" r="${effWidth * 0.5f}" fill="${st.colorHex}" fill-opacity="${st.opacity}" />""")
                    } else {
                        val d = buildString {
                            append("M${st.points[0].x} ${st.points[0].y}")
                            for (i in 1 until st.points.size) {
                                val prev = st.points[i - 1]
                                val curr = st.points[i]
                                val midX = (prev.x + curr.x) / 2f
                                val midY = (prev.y + curr.y) / 2f
                                append(" Q${prev.x} ${prev.y} $midX $midY")
                            }
                            val last = st.points.last()
                            append(" L${last.x} ${last.y}")
                        }
                        appendLine("""    <path d="$d" stroke="${st.colorHex}" stroke-width="$effWidth" stroke-opacity="${st.opacity}" fill="none" stroke-linecap="round" stroke-linejoin="round" />""")
                    }
                }
            }
            val activePose = activePosesByLayer[layer.id]
            if (activePose != null) {
                appendLine("""    <g class="skeleton-pose" data-pose-name="${activePose.name}">""")
                svgPoseBones.forEach { (a, b) ->
                    val pa = activePose.joints[a]
                    val pb = activePose.joints[b]
                    if (pa != null && pb != null) {
                        appendLine("""      <line x1="${pa.x}" y1="${pa.y}" x2="${pb.x}" y2="${pb.y}" stroke="#7E6BFF" stroke-opacity="0.45" stroke-width="4" stroke-linecap="round" />""")
                    }
                }
                activePose.joints.entries.sortedBy { it.key }.forEach { (jointName, pt) ->
                    appendLine("""      <circle data-joint="$jointName" cx="${pt.x}" cy="${pt.y}" r="8" fill="#FF6B5B" fill-opacity="0.75" />""")
                }
                appendLine("""    </g>""")
            }
            layer.sceneTexts.forEach { rawT ->
                val t = RigEngine.deformText(
                    rawT,
                    RigEngine.resolveTargetPose(rawT.rigAttachment, layer.id, activePosesByLayer)
                )
                val escaped = t.text
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                val rotAttr = if (abs(t.rotationDeg) > 0.01f) """ transform="rotate(${t.rotationDeg} ${t.x} ${t.y})"""" else ""
                appendLine("""    <text x="${t.x}" y="${t.y}" font-size="${t.fontSizeSp}" font-weight="bold" fill="${t.colorHex}" opacity="${t.opacity}"$rotAttr>$escaped</text>""")
            }
            appendLine("""  </g>""")
        }
        appendLine("</svg>")
    }

    private fun buildOpenCodeSkillGuide(options: ExportOptions): String = buildString {
        appendLine("# OpenCode Implementation Guide for FramePilot Packages")
        appendLine()
        appendLine("1. Parse `project.json` as the single source of truth.")
        appendLine("2. Render scenes in `orderIndex` order; within each scene render `layers` sorted by `orderIndex` ascending (0 = back).")
        appendLine("3. Use top-left `(0, 0)` coordinates with `+X` right and `+Y` down.")
        appendLine("4. Advance frame-by-frame drawings chronologically by `frames[i].orderIndex`, holding each cel for `exposureFrames / fps` seconds.")
        appendLine("5. Evaluate `motionTracks` and `cameraTrack` on the same millisecond timebase (`0..effectiveDurationMs`).")
        if (options.sketchAsDirectionOnly) {
            appendLine("6. **Sketch Simplicity ≠ Output Visual Style**: Never copy rough sketch strokes or stick-figure simplicity into final rendered assets. Treat drawings and 2D stickman skeletons strictly as layout, pose, proportion, and motion direction, and follow the user's `skill.md` or production style guide instead.")
            if (options.externalStyleGuideNote.isNotBlank()) {
                appendLine("7. **Target Style / `skill.md` Directive**: ${options.externalStyleGuideNote}")
            }
        }
    }

    private val anyMapAdapter = ProjectSerializer.moshi.adapter<Map<String, Any>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    ).indent("  ")

    private fun buildSchemaJson(): String {
        val map = linkedMapOf<String, Any>(
            "schema" to "https://framepilot.studio/schemas/project-v2.json",
            "schemaVersion" to CURRENT_SCHEMA_VERSION,
            "coordinateSystem" to "origin=top-left (0,0), +X=right, +Y=down",
            "requiredRootFields" to listOf("schemaVersion", "id", "name", "canvasWidth", "canvasHeight", "fps", "scenes"),
            "timelineOrderingRule" to "Frames are ordered strictly by ascending orderIndex; visible number = sorted index + 1."
        )
        return anyMapAdapter.toJson(map)
    }

    private fun buildManifestJson(project: FramePilotProject, fileList: List<String>, options: ExportOptions): String {
        val map = linkedMapOf<String, Any>(
            "format" to "framepilot-canonical-package",
            "schemaVersion" to CURRENT_SCHEMA_VERSION,
            "projectId" to project.id,
            "projectName" to project.name,
            "fps" to project.fps,
            "sceneCount" to project.scenes.size,
            "sketchAsDirectionOnly" to options.sketchAsDirectionOnly,
            "externalStyleGuideNote" to options.externalStyleGuideNote,
            "files" to fileList
        )
        return anyMapAdapter.toJson(map)
    }
}
