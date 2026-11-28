package com.example

import com.example.data.PackageExporter
import com.example.data.PackageImporter
import com.example.data.SampleProjects
import com.example.domain.model.AnimationFrame
import com.example.domain.model.BackgroundSpec
import com.example.domain.model.BackgroundType
import com.example.domain.model.DrawingStroke
import com.example.domain.model.EasingType
import com.example.domain.model.JointPoint
import com.example.domain.model.Layer
import com.example.domain.model.LayerCategory
import com.example.domain.model.MotionActionType
import com.example.domain.model.MotionPathPoint
import com.example.domain.model.MotionTrack
import com.example.domain.model.OrbitConfig
import com.example.domain.model.OrbitDirection
import com.example.domain.model.Scene
import com.example.domain.model.SceneShape
import com.example.domain.model.SceneText
import com.example.domain.model.SemanticInfo
import com.example.domain.model.ShapeMorphConfig
import com.example.domain.model.ShapeType
import com.example.domain.model.SkeletonPose
import com.example.domain.model.StrokePoint
import com.example.engine.CanvasTransformEngine
import com.example.engine.CanvasViewportState
import com.example.engine.MotionEvaluator
import com.example.engine.QuickShapeDetector
import com.example.engine.QuickShapeKind
import com.example.engine.SpatialMapGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class CanvasMotionAndExportTests {

    @Test
    fun `canvas coordinate inverse transform round-trips accurately under zoom and pan`() {
        val vp = CanvasViewportState(scale = 2.5f, panX = 140f, panY = -80f)
        val docX = 432f
        val docY = 789f
        val (sx, sy) = vp.documentToScreen(docX, docY)
        val (rx, ry) = vp.screenToDocument(sx, sy)
        assertEquals(docX, rx, 0.01f)
        assertEquals(docY, ry, 0.01f)
    }

    @Test
    fun `pinch zoom keeps focal document point stationary`() {
        val initial = CanvasViewportState(scale = 1.0f, panX = 50f, panY = 100f)
        val focalX = 300f
        val focalY = 400f
        val beforeDoc = initial.screenToDocument(focalX, focalY)

        val zoomed = CanvasTransformEngine.applyPinchAndPan(
            current = initial,
            zoomFactor = 2.0f,
            panDeltaX = 0f,
            panDeltaY = 0f,
            centroidScreenX = focalX,
            centroidScreenY = focalY
        )
        val afterDoc = zoomed.screenToDocument(focalX, focalY)
        assertEquals(beforeDoc.first, afterDoc.first, 0.01f)
        assertEquals(beforeDoc.second, afterDoc.second, 0.01f)
    }

    @Test
    fun `partial eraser splits intersected stroke cleanly without erasing untouched strokes`() {
        val longLine = DrawingStroke(
            id = "long-1",
            layerId = "l1",
            points = listOf(
                StrokePoint(0f, 100f),
                StrokePoint(50f, 100f),
                StrokePoint(100f, 100f),
                StrokePoint(150f, 100f),
                StrokePoint(200f, 100f)
            )
        )
        val untouched = DrawingStroke(
            id = "other-1",
            layerId = "l1",
            points = listOf(StrokePoint(0f, 500f), StrokePoint(200f, 500f))
        )

        val afterErase = CanvasTransformEngine.eraseAtPoint(
            strokes = listOf(longLine, untouched),
            docX = 100f,
            docY = 100f,
            eraserRadius = 20f
        )

        assertTrue(afterErase.any { it.id == "other-1" })
        // Split into left half (0..50) and right half (150..200)
        val splitParts = afterErase.filter { it.id != "other-1" }
        assertEquals(2, splitParts.size)
    }

    @Test
    fun `motion evaluator computes orbit path sampling and shape morph resampling`() {
        val orbitTrack = MotionTrack(
            id = "t-orbit",
            name = "Butterfly Orbit",
            targetId = "butterfly",
            actionType = MotionActionType.ORBIT,
            durationMs = 2000L,
            easing = EasingType.LINEAR,
            orbitConfig = OrbitConfig(targetId = "hero", direction = OrbitDirection.CLOCKWISE, radiusPx = 200f)
        )
        val centers = mapOf("hero" to (500f to 500f), "butterfly" to (500f to 300f))
        val evalQuarter = MotionEvaluator.evaluateTrack(orbitTrack, 500L, 1080, 1920, centers, centers["butterfly"])
        assertEquals(700f, evalQuarter.absolutePosition!!.first, 2f)

        val pathSample = MotionEvaluator.samplePath(
            listOf(MotionPathPoint(0f, 0f), MotionPathPoint(100f, 0f), MotionPathPoint(100f, 100f)),
            0.5f
        )
        assertEquals(100f, pathSample.x, 1f)
        assertEquals(0f, pathSample.y, 1f)

        val morphTrack = MotionTrack(
            id = "t-morph",
            name = "Morph",
            targetId = "orb",
            actionType = MotionActionType.MORPH,
            durationMs = 1000L,
            morphConfig = ShapeMorphConfig(sourceShape = ShapeType.ELLIPSE, targetShape = ShapeType.RECTANGLE)
        )
        val morphed = MotionEvaluator.evaluateTrack(morphTrack, 500L, 1080, 1920)
        assertEquals(32, morphed.morphPoints.size)
    }

    @Test
    fun `spatial map explicitly marks unlabelled layers and enforces top-left origin`() {
        val blankProj = SampleProjects.createBlankProject("Unlabelled Test", 1080, 1920, "9:16", 24)
        val layerId = blankProj.scenes.first().layers.last().id
        val withStroke = blankProj.copy(
            scenes = listOf(
                blankProj.scenes.first().copy(
                    layers = blankProj.scenes.first().layers.map { l ->
                        if (l.id == layerId) {
                            l.copy(
                                sceneStrokes = listOf(
                                    DrawingStroke(
                                        layerId = layerId,
                                        points = listOf(StrokePoint(100f, 200f, 1f, 0.09f, 0.10f))
                                    )
                                )
                            )
                        } else l
                    }
                )
            )
        )

        val warnings = SpatialMapGenerator.collectWarnings(withStroke)
        assertTrue(warnings.any { it.severity == "UNLABELLED" })

        val mapMd = SpatialMapGenerator.generateSceneSpatialMap(withStroke, withStroke.scenes.first())
        assertTrue(mapMd.contains("TOP-LEFT"))
        assertTrue(mapMd.contains("unlabelled"))
    }

    @Test
    fun `full canonical package export and re-import round trip preserves sample project`() {
        val sample = SampleProjects.createParkButterflySample()
        val pkg = PackageExporter.generatePackage(sample)

        // Required canonical files present
        assertTrue(pkg.files.containsKey("README.md"))
        assertTrue(pkg.files.containsKey("START-HERE.md"))
        assertTrue(pkg.files.containsKey("OPENCODE-PROMPT.md"))
        assertTrue(pkg.files.containsKey("project.json"))
        assertTrue(pkg.files.containsKey("project-schema.json"))
        assertTrue(pkg.files.containsKey("package-manifest.json"))
        assertTrue(pkg.files.containsKey("creative-direction.md"))
        assertTrue(pkg.files.containsKey("animation-plan.md"))
        assertTrue(pkg.files.containsKey("sketches/spatial-map.md"))
        assertTrue(pkg.files.containsKey("frames/frames-sequence.md"))
        assertTrue(pkg.files.containsKey("layers/layers-catalogue.md"))
        assertTrue(pkg.files.containsKey("motion/motion-specs.md"))
        assertTrue(pkg.files.containsKey("accessibility/missing-info-warnings.md"))

        val zipBytes = PackageExporter.exportToZipBytes(sample)
        val reimported = PackageImporter.importBytes(zipBytes, assignNewId = false).project

        assertEquals(sample.id, reimported.id)
        assertEquals(sample.name, reimported.name)
        assertEquals(sample.fps, reimported.fps)
        assertEquals(sample.canvasWidth, reimported.canvasWidth)
        assertEquals(sample.canvasHeight, reimported.canvasHeight)
        assertEquals(sample.scenes.first().layers.size, reimported.scenes.first().layers.size)
        assertEquals(sample.scenes.first().frames.size, reimported.scenes.first().frames.size)
        assertEquals(sample.scenes.first().motionTracks.size, reimported.scenes.first().motionTracks.size)
        assertEquals(sample.scenes.first().background.environmentDescription, reimported.scenes.first().background.environmentDescription)
        assertFalse(reimported.scenes.first().layers[1].semantic.whatIsIt.isBlank())
    }

    @Test
    fun `stroke summarizer classifies lines, closed circles, and curved paths with polyline trace`() {
        val straightLine = DrawingStroke(
            id = "st-line",
            layerId = "l1",
            colorHex = "#FF6B5B",
            points = (0..10).map { i -> StrokePoint(100f + i * 50f, 300f) }
        )
        val lineSummary = SpatialMapGenerator.summarizeStroke(straightLine, 1080, 1920)
        assertTrue(lineSummary.contains("straight horizontal line"))
        assertTrue(lineSummary.contains("trace("))

        val circlePts = (0..24).map { i ->
            val a = 2.0 * PI * i / 24.0
            StrokePoint(540f + 160f * cos(a).toFloat(), 960f + 160f * sin(a).toFloat())
        }
        val circleStroke = DrawingStroke(
            id = "st-circle",
            layerId = "l1",
            colorHex = "#4EA8FF",
            isFilledLoop = true,
            fillColorHex = "#E4F1FF",
            points = circlePts
        )
        val circleSummary = SpatialMapGenerator.summarizeStroke(circleStroke, 1080, 1920)
        assertTrue(circleSummary.contains("filled closed circle-like loop"))
        assertTrue(circleSummary.contains("#E4F1FF"))

        val wavePts = listOf(
            StrokePoint(100f, 500f),
            StrokePoint(200f, 380f),
            StrokePoint(320f, 620f),
            StrokePoint(440f, 360f),
            StrokePoint(580f, 640f)
        )
        val waveStroke = DrawingStroke(id = "st-wave", layerId = "l1", points = wavePts)
        val waveSummary = SpatialMapGenerator.summarizeStroke(waveStroke, 1080, 1920)
        assertTrue(waveSummary.contains("bends"))
    }

    @Test
    fun `opencode prompt and svg export give text-only ai complete visual sight of drawings, shapes, poses, and cels`() {
        val base = SampleProjects.createBlankProject("Visual Export Test", 1080, 1920, "9:16", 24)
        val heroLayer = Layer(
            id = "layer-hero",
            name = "Hero Rig",
            category = LayerCategory.CHARACTER,
            orderIndex = 0,
            semantic = SemanticInfo(whatIsIt = "Stick figure waving", whatIsItDoing = "Waving right arm"),
            skeletonPose = SkeletonPose(
                id = "pose-1",
                name = "Wave",
                joints = mapOf(
                    "head" to JointPoint(540f, 700f),
                    "neck" to JointPoint(540f, 760f),
                    "chest" to JointPoint(540f, 830f),
                    "hip" to JointPoint(540f, 950f),
                    "rightShoulder" to JointPoint(580f, 770f),
                    "rightElbow" to JointPoint(640f, 720f),
                    "rightHand" to JointPoint(690f, 660f)
                )
            ),
            sceneShapes = listOf(
                SceneShape(
                    id = "sh-poly",
                    layerId = "layer-hero",
                    shapeType = ShapeType.POLYGON,
                    x = 120f,
                    y = 200f,
                    width = 180f,
                    height = 180f,
                    rotationDeg = 15f,
                    strokeColorHex = "#FFD15C",
                    fillColorHex = "#FFF3C4",
                    filled = true
                ),
                SceneShape(
                    id = "sh-arrow",
                    layerId = "layer-hero",
                    shapeType = ShapeType.ARROW,
                    x = 300f,
                    y = 400f,
                    width = 240f,
                    height = 60f,
                    strokeColorHex = "#FF6B5B"
                )
            ),
            sceneStrokes = listOf(
                DrawingStroke(
                    id = "st-arc",
                    layerId = "layer-hero",
                    colorHex = "#2EC49A",
                    points = listOf(
                        StrokePoint(200f, 1200f),
                        StrokePoint(360f, 1120f),
                        StrokePoint(540f, 1210f)
                    )
                )
            ),
            sceneTexts = listOf(
                SceneText(
                    id = "tx-title",
                    layerId = "layer-hero",
                    text = "HELLO!",
                    x = 420f,
                    y = 180f,
                    colorHex = "#22242C"
                )
            )
        )
        val celFrame = AnimationFrame(
            id = "f-cel-2",
            orderIndex = 1,
            exposureFrames = 2,
            note = "Jump pose",
            layerShapes = mapOf(
                "layer-hero" to listOf(
                    SceneShape(
                        id = "cel-rect",
                        layerId = "layer-hero",
                        shapeType = ShapeType.RECTANGLE,
                        x = 700f,
                        y = 1300f,
                        width = 150f,
                        height = 120f
                    )
                )
            ),
            layerPoses = mapOf(
                "layer-hero" to SkeletonPose(
                    id = "pose-2",
                    name = "Jump",
                    joints = mapOf("head" to JointPoint(540f, 600f), "neck" to JointPoint(540f, 660f))
                )
            ),
            layerDrawings = mapOf(
                "layer-hero" to listOf(
                    DrawingStroke(
                        id = "cel-stroke-1",
                        layerId = "layer-hero",
                        points = listOf(StrokePoint(500f, 1500f), StrokePoint(700f, 1500f))
                    )
                )
            )
        )
        val richScene = Scene(
            id = "scene-visual",
            orderIndex = 0,
            name = "Visual Scene",
            background = BackgroundSpec(
                type = BackgroundType.GRADIENT,
                primaryColorHex = "#FFFDF9",
                secondaryColorHex = "#E4F1FF",
                gradientAngleDeg = 45f,
                environmentDescription = "Sunny paper sky"
            ),
            layers = listOf(heroLayer),
            frames = listOf(AnimationFrame(id = "f-cel-1", orderIndex = 0), celFrame)
        )
        val project = base.copy(scenes = listOf(richScene))

        val pkg = PackageExporter.generatePackage(project)
        val prompt = pkg.files.getValue("OPENCODE-PROMPT.md")
        assertTrue(prompt.contains("2D Visual Canvas Render"))
        assertTrue(prompt.contains("Legend:"))
        assertTrue(prompt.contains("@")) // skeleton head
        assertTrue(prompt.contains("\"HELLO!\""))
        assertTrue(prompt.contains("Colors Used"))
        assertTrue(prompt.contains("POLYGON"))
        assertTrue(prompt.contains("ARROW"))
        assertTrue(prompt.contains("shape=\"smooth curved arc\""))
        assertTrue(prompt.contains("rightHand"))
        assertTrue(prompt.contains("cel shape"))
        assertTrue(prompt.contains("cel pose 'Jump'"))
        assertTrue(prompt.contains("cel stroke"))

        val framesMd = pkg.files.getValue("frames/frames-sequence.md")
        assertTrue(framesMd.contains("cel-rect"))
        assertTrue(framesMd.contains("Jump"))
        assertTrue(framesMd.contains("straight horizontal line"))

        val svg = pkg.files.getValue("assets/scene_1_scene-visual.svg")
        assertTrue(svg.contains("<linearGradient"))
        assertTrue(svg.contains("<polygon points="))
        assertTrue(svg.contains("transform=\"rotate(15.0"))
        assertTrue(svg.contains("skeleton-pose"))
        assertTrue(svg.contains(" Q200.0 1200.0 "))
        assertTrue(svg.contains("font-weight=\"bold\""))
    }

    @Test
    fun `quick shape snaps wobbly line, circle, oval, rectangle, and triangle cleanly for export`() {
        val w = 1080
        val h = 1920

        // 1. Wobbly line
        val wobblyLine = (0..12).map { i ->
            val wobble = if (i % 2 == 0) 10f else -10f
            StrokePoint(120f + i * 50f, 300f + wobble)
        }
        val snappedLine = QuickShapeDetector.detectAndSnap(wobblyLine, w, h)
        assertEquals(QuickShapeKind.LINE, snappedLine?.kind)
        val lineMapSummary = SpatialMapGenerator.summarizeStroke(
            DrawingStroke(layerId = "l1", points = snappedLine!!.snappedPoints), w, h
        )
        assertTrue(lineMapSummary.contains("straight horizontal line"))

        // 2. Rough circle
        val roughCircle = (0..28).map { i ->
            val a = 2.0 * PI * i / 28.0
            val jitter = if (i % 3 == 0) 12f else -8f
            StrokePoint(540f + (180f + jitter) * cos(a).toFloat(), 800f + (175f - jitter) * sin(a).toFloat())
        }
        val snappedCircle = QuickShapeDetector.detectAndSnap(roughCircle, w, h)
        assertEquals(QuickShapeKind.CIRCLE, snappedCircle?.kind)
        val circleMapSummary = SpatialMapGenerator.summarizeStroke(
            DrawingStroke(layerId = "l1", points = snappedCircle!!.snappedPoints), w, h
        )
        assertTrue(circleMapSummary.contains("closed circle-like loop"))

        // 3. Rough oval
        val roughOval = (0..28).map { i ->
            val a = 2.0 * PI * i / 28.0
            val jitter = if (i % 2 == 0) 8f else -8f
            StrokePoint(540f + (280f + jitter) * cos(a).toFloat(), 960f + (120f - jitter) * sin(a).toFloat())
        }
        val snappedOval = QuickShapeDetector.detectAndSnap(roughOval, w, h)
        assertEquals(QuickShapeKind.OVAL, snappedOval?.kind)
        val ovalMapSummary = SpatialMapGenerator.summarizeStroke(
            DrawingStroke(layerId = "l1", points = snappedOval!!.snappedPoints), w, h
        )
        assertTrue(ovalMapSummary.contains("closed oval/ellipse loop"))

        // 4. Rough rectangle
        val roughRect = buildList {
            for (i in 0..8) add(StrokePoint(200f + i * 50f, 400f + (if (i % 2 == 0) 6f else -6f)))
            for (i in 0..6) add(StrokePoint(600f + (if (i % 2 == 0) 5f else -5f), 400f + i * 50f))
            for (i in 0..8) add(StrokePoint(600f - i * 50f, 700f + (if (i % 2 == 0) 6f else -6f)))
            for (i in 0..6) add(StrokePoint(200f + (if (i % 2 == 0) 5f else -5f), 700f - i * 50f))
        }
        val snappedRect = QuickShapeDetector.detectAndSnap(roughRect, w, h)
        assertEquals(QuickShapeKind.RECTANGLE, snappedRect?.kind)
        val rectMapSummary = SpatialMapGenerator.summarizeStroke(
            DrawingStroke(layerId = "l1", points = snappedRect!!.snappedPoints), w, h
        )
        assertTrue(rectMapSummary.contains("closed rectangle/square (4 corners)"))

        // 5. Rough triangle
        val v0 = 540f to 300f
        val v1 = 760f to 680f
        val v2 = 320f to 680f
        fun edge(a: Pair<Float, Float>, b: Pair<Float, Float>): List<StrokePoint> = (0..8).map { i ->
            val t = i / 8f
            val jitter = if (i in 1..7 && i % 2 == 0) 6f else 0f
            StrokePoint(a.first + (b.first - a.first) * t + jitter, a.second + (b.second - a.second) * t - jitter)
        }
        val roughTri = edge(v0, v1) + edge(v1, v2) + edge(v2, v0)
        val snappedTri = QuickShapeDetector.detectAndSnap(roughTri, w, h)
        assertEquals(QuickShapeKind.TRIANGLE, snappedTri?.kind)
        val triMapSummary = SpatialMapGenerator.summarizeStroke(
            DrawingStroke(layerId = "l1", points = snappedTri!!.snappedPoints), w, h
        )
        assertTrue(triMapSummary.contains("closed triangle (3 corners)"))

        // 6. Wobbly open C-arc should snap to Smooth Arc (ARC) and export as smooth curved arc
        val roughArc = (0..18).map { i ->
            val a = Math.PI * i / 18.0
            val jitter = if (i in 1..17 && i % 2 == 0) 6f else if (i in 1..17) -6f else 0f
            StrokePoint(
                200f + 300f * (1f - cos(a).toFloat()) + jitter * 0.4f,
                900f - (180f + jitter) * sin(a).toFloat()
            )
        }
        val snappedArc = QuickShapeDetector.detectAndSnap(roughArc, w, h)
        assertEquals(QuickShapeKind.ARC, snappedArc?.kind)
        val arcMapSummary = SpatialMapGenerator.summarizeStroke(
            DrawingStroke(layerId = "l1", points = snappedArc!!.snappedPoints), w, h
        )
        assertTrue(arcMapSummary.contains("smooth curved arc"))

        // 7. Open freehand wobbly S-wave should polish into Smooth Curve (CURVE), NOT Straight Line
        val wobblyWave = (0..24).map { i ->
            val t = i / 24.0
            val x = 120.0 + t * 640.0
            val jitter = if (i in 1..23 && i % 2 == 0) 10.0 else if (i in 1..23) -10.0 else 0.0
            val y = 1220.0 + sin(t * 2.5 * Math.PI) * 140.0 + jitter
            StrokePoint(x.toFloat(), y.toFloat())
        }
        val snappedWave = QuickShapeDetector.detectAndSnap(wobblyWave, w, h)
        assertEquals(QuickShapeKind.CURVE, snappedWave?.kind)
        val waveMapSummary = SpatialMapGenerator.summarizeStroke(
            DrawingStroke(layerId = "l1", points = snappedWave!!.snappedPoints), w, h
        )
        assertTrue(waveMapSummary.contains("curved wave/S-path"))

        // 8. Sharp L-corner path should preserve its crisp corner as Polished Path (POLYLINE)
        val lShape = buildList {
            for (i in 0..10) {
                val jitter = if (i in 1..9 && i % 2 == 0) 5f else 0f
                add(StrokePoint(220f + jitter, 300f + i * 36f))
            }
            for (i in 1..10) {
                val jitter = if (i in 1..9 && i % 2 == 0) 5f else 0f
                add(StrokePoint(220f + i * 36f, 660f + jitter))
            }
        }
        val snappedCorner = QuickShapeDetector.detectAndSnap(lShape, w, h)
        assertEquals(QuickShapeKind.POLYLINE, snappedCorner?.kind)

        // 9. Irregular closed cloud contour should polish smoothly as Smooth Contour (CLOSED_CURVE)
        val cloudContour = (0..40).map { i ->
            val a = 2.0 * PI * i / 40.0
            val radius = 185.0 + 68.0 * cos(5.0 * a)
            val jitter = if (i in 1..39 && i % 2 == 0) 4.0 else -4.0
            StrokePoint(
                (540.0 + (radius + jitter) * cos(a)).toFloat(),
                (1400.0 + (radius + jitter) * sin(a)).toFloat()
            )
        }
        val snappedContour = QuickShapeDetector.detectAndSnap(cloudContour, w, h)
        assertEquals(QuickShapeKind.CLOSED_CURVE, snappedContour?.kind)

        // 10. Tiny dot/tap below minimum size should stay untouched (null)
        val tinyTap = listOf(StrokePoint(100f, 100f), StrokePoint(104f, 103f), StrokePoint(107f, 105f))
        assertEquals(null, QuickShapeDetector.detectAndSnap(tinyTap, w, h))
    }

    @Test
    fun `pose geometry computes scale and angle cleanly and preserves center on whole-body transform`() {
        val joints = com.example.domain.model.defaultStickmanJoints(540f, 1140f)
        val (cx, cy) = com.example.ui.studio.PoseGeometry.computeCenter(joints)
        assertEquals(1f, com.example.ui.studio.PoseGeometry.computeScale(joints), 0.01f)
        assertEquals(0f, com.example.ui.studio.PoseGeometry.computeAngleDeg(joints), 0.01f)

        val scaledRotated = com.example.ui.studio.PoseGeometry.transformJointsFromAnchor(
            anchorJoints = joints,
            centerX = cx,
            centerY = cy,
            scaleRatio = 1.6f,
            deltaAngleDeg = 45f
        )
        assertEquals(1.6f, com.example.ui.studio.PoseGeometry.computeScale(scaledRotated), 0.02f)
        assertEquals(45f, com.example.ui.studio.PoseGeometry.computeAngleDeg(scaledRotated), 0.2f)

        val rotatedNegative = com.example.ui.studio.PoseGeometry.transformJointsFromAnchor(
            anchorJoints = joints,
            centerX = cx,
            centerY = cy,
            scaleRatio = 0.5f,
            deltaAngleDeg = -90f
        )
        assertEquals(0.5f, com.example.ui.studio.PoseGeometry.computeScale(rotatedNegative), 0.02f)
        assertEquals(-90f, com.example.ui.studio.PoseGeometry.computeAngleDeg(rotatedNegative), 0.2f)
    }

    @Test
    fun `export options explicitly instruct opencode that rough stickman sketches are direction only unless toggled off`() {
        val sample = SampleProjects.createParkButterflySample()

        val directionPkg = PackageExporter.generatePackage(
            sample,
            com.example.data.ExportOptions(
                sketchAsDirectionOnly = true,
                externalStyleGuideNote = "Follow my cyberpunk anime skill.md"
            )
        )
        val directionPrompt = directionPkg.files.getValue("OPENCODE-PROMPT.md")
        assertTrue(directionPrompt.contains("Sketch Fidelity ≠ Final Output Style"))
        assertTrue(directionPrompt.contains("DO NOT COPY ROUGH SKETCH OR STICKMAN STYLE"))
        assertTrue(directionPrompt.contains("skill.md"))
        assertTrue(directionPrompt.contains("Follow my cyberpunk anime skill.md"))
        assertTrue(directionPkg.files.getValue("START-HERE.md").contains("SKETCH = DIRECTION ONLY"))
        assertTrue(directionPkg.files.getValue("sketches/spatial-map.md").contains("Art Direction Rule (Direction Only)"))
        assertTrue(directionPkg.files.getValue("skills/opencode-implementation-guide.md").contains("Sketch Simplicity ≠ Output Visual Style"))
        assertTrue(directionPkg.files.getValue("package-manifest.json").contains("\"sketchAsDirectionOnly\": true"))

        val zipBytes = PackageExporter.exportToZipBytes(
            sample,
            com.example.data.ExportOptions(
                sketchAsDirectionOnly = true,
                externalStyleGuideNote = "Follow my cyberpunk anime skill.md"
            )
        )
        val reimported = PackageImporter.importBytes(zipBytes, assignNewId = false).project
        assertTrue(reimported.exportSketchAsDirectionOnly)
        assertEquals("Follow my cyberpunk anime skill.md", reimported.exportStyleGuideNote)

        val literalPkg = PackageExporter.generatePackage(
            sample,
            com.example.data.ExportOptions(sketchAsDirectionOnly = false)
        )
        val literalPrompt = literalPkg.files.getValue("OPENCODE-PROMPT.md")
        assertFalse(literalPrompt.contains("DO NOT COPY ROUGH SKETCH OR STICKMAN STYLE"))
        assertTrue(literalPrompt.contains("Faithful Sketch Reconstruction"))
        assertTrue(literalPkg.files.getValue("sketches/spatial-map.md").contains("Faithful Sketch Mode"))
    }

    @Test
    fun `after graduation 30s multi-scene reel showcases all 6 scenes, full 30s duration, motion tracks, and clean zip round-trip`() {
        val sample = SampleProjects.createAfterGraduationReelSample()
        assertEquals("sample-graduation-reel", sample.id)
        assertEquals("After College Ends (30s)", sample.name)
        assertEquals(1080, sample.canvasWidth)
        assertEquals(1920, sample.canvasHeight)
        assertEquals(24, sample.fps)
        assertEquals(6, sample.scenes.size)
        assertEquals(30_000L, sample.totalDurationMs)
        assertTrue(sample.exportSketchAsDirectionOnly)
        assertTrue(sample.exportStyleGuideNote.contains("skill.md"))

        val pkg = PackageExporter.generatePackage(sample)
        (1..6).forEach { sceneNum ->
            val sc = sample.scenes[sceneNum - 1]
            assertTrue(
                "Missing SVG snapshot for scene $sceneNum (${sc.id})",
                pkg.files.containsKey("assets/scene_${sceneNum}_${sc.id}.svg")
            )
        }

        val prompt = pkg.files.getValue("OPENCODE-PROMPT.md")
        assertTrue(prompt.contains("WHO ARE YOU"))
        assertTrue(prompt.contains("WHEN COLLEGE ENDS?"))
        assertTrue(prompt.contains("Campus Hook"))
        assertTrue(prompt.contains("Hallway Routine"))
        assertTrue(prompt.contains("Rainy Crossroads"))
        assertTrue(prompt.contains("Late-Night Doubt"))
        assertTrue(prompt.contains("Sunrise Clarity"))
        assertTrue(prompt.contains("New Chapter Finale"))
        assertTrue(prompt.contains("Orbit Object"))
        assertTrue(prompt.contains("Follow Path"))
        assertTrue(prompt.contains("Morph Shape"))
        assertTrue(prompt.contains("Particles"))

        val animPlan = pkg.files.getValue("animation-plan.md")
        assertTrue(animPlan.contains("ORBIT"))
        assertTrue(animPlan.contains("FOLLOW_PATH"))
        assertTrue(animPlan.contains("MORPH"))
        assertTrue(animPlan.contains("PARTICLES"))

        // Verify zero random camera shake and non-overlapping captions across all 6 scenes
        sample.scenes.forEach { sc ->
            assertTrue(
                "Scene ${sc.name} must not have camera shake",
                sc.cameraTrack.keyframes.all { it.shakeIntensity == 0f }
            )
            val texts = sc.layers.flatMap { it.sceneTexts }.sortedBy { it.y }
            for (i in 1 until texts.size) {
                val gap = texts[i].y - texts[i - 1].y
                assertTrue(
                    "Captions in ${sc.name} overlap (${texts[i - 1].text} vs ${texts[i].text}, gap=$gap)",
                    gap >= 50f
                )
            }
        }

        // Verify rain particles emit diagonal velocity streaks rather than exploding dots
        val rainTrack = sample.scenes[2].motionTracks.first { it.actionType == MotionActionType.PARTICLES }
        val rainEval = MotionEvaluator.evaluateTrack(rainTrack, 1200L, 1080, 1920)
        assertTrue(rainEval.particles.isNotEmpty())
        assertTrue(rainEval.particles.all { it.vy > 0f && it.vx < 0f })

        val zipBytes = PackageExporter.exportToZipBytes(sample)
        val reimported = PackageImporter.importBytes(zipBytes, assignNewId = false).project
        assertEquals(sample.id, reimported.id)
        assertEquals(6, reimported.scenes.size)
        assertEquals(30_000L, reimported.totalDurationMs)
    }

    @Test
    fun `rig engine deforms attached shapes, strokes, and text when skeleton pose changes and forces hard cuts`() {
        val sample = SampleProjects.createAfterGraduationReelSample()
        assertTrue(
            "All sample transitions must be Hard Cut (0ms)",
            sample.scenes.all {
                it.transitionOut.type == com.example.domain.model.SceneTransitionType.CUT &&
                    it.transitionOut.durationMs == 0L
            }
        )

        // Serializer must coerce legacy CROSSFADE into CUT(0ms)
        val legacyWithFade = sample.copy(
            scenes = sample.scenes.map { sc ->
                sc.copy(
                    transitionOut = com.example.domain.model.SceneTransition(
                        type = com.example.domain.model.SceneTransitionType.CROSSFADE,
                        durationMs = 450L
                    )
                )
            }
        )
        val encoded = com.example.data.ProjectSerializer.toJson(legacyWithFade)
        val normalized = com.example.data.ProjectSerializer.fromJson(encoded)
        assertTrue(
            "ProjectSerializer must normalize all scene transitions to Hard Cut (0ms)",
            normalized.scenes.all {
                it.transitionOut.type == com.example.domain.model.SceneTransitionType.CUT &&
                    it.transitionOut.durationMs == 0L
            }
        )

        // Build a character rig with attached shin shape, sleeve stroke, and grouped text tag
        val baseJoints = com.example.domain.model.defaultStickmanJoints(540f, 960f)
        val basePose = SkeletonPose(id = "rig-1", name = "Idle", joints = baseJoints)
        val shinAtt = com.example.engine.RigEngine.createAttachment(
            com.example.engine.RigBonePreset.RIGHT_SHIN,
            basePose,
            rigLayerId = "hero"
        )
        val handAtt = com.example.engine.RigEngine.createAttachment(
            com.example.engine.RigBonePreset.RIGHT_HAND,
            basePose,
            rigLayerId = "hero"
        )
        val shinRect = SceneShape(
            id = "shin-box",
            layerId = "hero",
            shapeType = ShapeType.RECTANGLE,
            x = 560f,
            y = 1070f,
            width = 36f,
            height = 70f,
            groupId = "grp-leg",
            rigAttachment = shinAtt
        )
        val propStroke = DrawingStroke(
            id = "hand-wand",
            layerId = "hero",
            groupId = "grp-leg",
            rigAttachment = handAtt,
            points = listOf(StrokePoint(645f, 880f), StrokePoint(675f, 860f))
        )
        val badgeText = SceneText(
            id = "hand-badge",
            layerId = "hero",
            text = "RUN!",
            x = 645f,
            y = 870f,
            groupId = "grp-leg",
            rigAttachment = handAtt
        )

        // Move rightKnee +140px X, -60px Y and rightHand +180px X, -120px Y (running pose)
        val runPose = basePose.copy(
            joints = baseJoints + mapOf(
                "rightKnee" to JointPoint(570f + 140f, 1065f - 60f),
                "rightFoot" to JointPoint(585f + 140f, 1140f - 60f),
                "rightHand" to JointPoint(645f + 180f, 880f - 120f)
            )
        )

        val defShape = com.example.engine.RigEngine.deformShape(shinRect, runPose)
        val defStroke = com.example.engine.RigEngine.deformStroke(propStroke, runPose, 1080, 1920)
        val defText = com.example.engine.RigEngine.deformText(badgeText, runPose)

        assertTrue("Attached shape must move right with rightKnee/rightFoot", defShape.x > shinRect.x + 100f)
        assertTrue("Attached stroke must move right with rightHand", defStroke.points.first().x > propStroke.points.first().x + 140f)
        assertTrue("Attached text must follow rightHand pose", defText.x > badgeText.x + 140f)
        assertTrue("Attached text must follow rightHand Y pose", defText.y < badgeText.y - 80f)

        // Spatial map & SVG export reflect deformed positions & group name
        val layer = Layer(
            id = "hero",
            name = "Runner Hero",
            category = LayerCategory.CHARACTER,
            skeletonPose = runPose,
            groups = listOf(com.example.domain.model.ElementGroup(id = "grp-leg", name = "Runner Group", rigAttachment = shinAtt)),
            sceneShapes = listOf(shinRect),
            sceneStrokes = listOf(propStroke),
            sceneTexts = listOf(badgeText)
        )
        val scene = Scene(
            id = "sc-run",
            orderIndex = 0,
            name = "Run Scene",
            layers = listOf(layer),
            frames = listOf(AnimationFrame(orderIndex = 0))
        )
        val proj = SampleProjects.createBlankProject("Rig Run Test", 1080, 1920, "9:16", 24).copy(scenes = listOf(scene))
        val mapMd = SpatialMapGenerator.generateSceneSpatialMap(proj, scene)
        assertTrue(mapMd.contains("Runner Group"))
        assertTrue(mapMd.contains("rigBone=`R Hand`"))
        assertTrue(mapMd.contains("rigBone=`R Shin`"))
        val pkg = PackageExporter.generatePackage(proj)
        val svg = pkg.files.getValue("assets/scene_1_sc-run.svg")
        assertTrue(svg.contains("RUN!"))
    }
}

