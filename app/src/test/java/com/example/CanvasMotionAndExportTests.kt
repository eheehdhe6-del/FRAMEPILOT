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
}
