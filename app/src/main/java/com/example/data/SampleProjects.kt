package com.example.data

import com.example.domain.model.AnimationFrame
import com.example.domain.model.AudioCue
import com.example.domain.model.AudioPlan
import com.example.domain.model.BackgroundSpec
import com.example.domain.model.BackgroundType
import com.example.domain.model.CameraKeyframe
import com.example.domain.model.CameraTrack
import com.example.domain.model.DrawingStroke
import com.example.domain.model.DrawingTool
import com.example.domain.model.EasingType
import com.example.domain.model.FramePilotProject
import com.example.domain.model.Layer
import com.example.domain.model.LayerCategory
import com.example.domain.model.MotionActionType
import com.example.domain.model.MotionPathPoint
import com.example.domain.model.MotionTargetType
import com.example.domain.model.MotionTrack
import com.example.domain.model.OrbitConfig
import com.example.domain.model.OrbitDirection
import com.example.domain.model.ParticleConfig
import com.example.domain.model.Scene
import com.example.domain.model.SceneShape
import com.example.domain.model.SceneText
import com.example.domain.model.SceneTransition
import com.example.domain.model.SceneTransitionType
import com.example.domain.model.SemanticInfo
import com.example.domain.model.ShapeMorphConfig
import com.example.domain.model.ShapeType
import com.example.domain.model.SkeletonPose
import com.example.domain.model.StrokePoint
import com.example.domain.model.TextAnimationStyle
import com.example.domain.model.defaultStickmanJoints
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object SampleProjects {

    fun createBlankProject(
        name: String,
        width: Int,
        height: Int,
        aspectLabel: String,
        fps: Int
    ): FramePilotProject {
        val now = "2026-11-25T12:00:00Z"
        val bgLayer = Layer(
            id = UUID.randomUUID().toString(),
            name = "Background",
            category = LayerCategory.BACKGROUND,
            orderIndex = 0,
            semantic = SemanticInfo(
                whatIsIt = "",
                whereLocated = "Full canvas backdrop"
            )
        )
        val drawLayer = Layer(
            id = UUID.randomUUID().toString(),
            name = "Main Sketch",
            category = LayerCategory.CHARACTER,
            orderIndex = 1
        )
        val frame1 = AnimationFrame(
            id = UUID.randomUUID().toString(),
            orderIndex = 0,
            exposureFrames = 1,
            note = "Opening frame"
        )
        val scene1 = Scene(
            id = UUID.randomUUID().toString(),
            orderIndex = 0,
            name = "Scene 1",
            durationMs = 4000L,
            background = BackgroundSpec(
                type = BackgroundType.SOLID,
                primaryColorHex = "#FFFDF9"
            ),
            layers = listOf(bgLayer, drawLayer),
            frames = listOf(frame1)
        )
        return FramePilotProject(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Untitled Studio" },
            createdAtIso = now,
            updatedAtIso = now,
            canvasWidth = width,
            canvasHeight = height,
            aspectRatioLabel = aspectLabel,
            fps = fps,
            scenes = listOf(scene1)
        )
    }

    /**
     * Rich sample Reel project demonstrating:
     * - Layered park background (sky gradient, mountains, trees, ground)
     * - Stickman character with 2D skeleton pose + hand-drawn strokes
     * - Butterfly orbiting the stickman
     * - Circle-to-square morphing prop
     * - Kinetic typography title
     * - Camera zoom + sparkles particles
     */
    fun createParkButterflySample(): FramePilotProject {
        val w = 1080
        val h = 1920
        val bgLayerId = "layer-bg-park"
        val charLayerId = "layer-char-stickman"
        val butterflyLayerId = "layer-prop-butterfly"
        val morphLayerId = "layer-prop-morph"
        val textLayerId = "layer-text-title"
        val fxLayerId = "layer-fx-sparkles"

        fun pt(x: Float, y: Float) = StrokePoint(x = x, y = y, pressure = 1f, normX = x / w, normY = y / h)

        val mountainStroke = DrawingStroke(
            id = "stroke-mountains",
            layerId = bgLayerId,
            tool = DrawingTool.PEN,
            colorHex = "#7E6BFF",
            strokeWidth = 10f,
            points = listOf(
                pt(0f, 1180f), pt(220f, 940f), pt(460f, 1130f),
                pt(760f, 890f), pt(1080f, 1170f)
            ),
            semanticLabel = "Distant lavender mountain ridge"
        )
        val groundStroke = DrawingStroke(
            id = "stroke-ground",
            layerId = bgLayerId,
            tool = DrawingTool.BRUSH,
            colorHex = "#2EC49A",
            strokeWidth = 22f,
            points = listOf(pt(0f, 1380f), pt(540f, 1350f), pt(1080f, 1385f)),
            semanticLabel = "Grassy park horizon line"
        )
        val treeTrunk = SceneShape(
            id = "shape-tree-trunk",
            layerId = bgLayerId,
            shapeType = ShapeType.RECTANGLE,
            x = 130f, y = 1120f, width = 46f, height = 250f,
            strokeColorHex = "#8D5B4C", fillColorHex = "#8D5B4C", filled = true,
            semantic = SemanticInfo(whatIsIt = "Tree trunk", represents = "Park tree on left")
        )
        val treeCanopy = SceneShape(
            id = "shape-tree-canopy",
            layerId = bgLayerId,
            shapeType = ShapeType.ELLIPSE,
            x = 70f, y = 940f, width = 175f, height = 200f,
            strokeColorHex = "#23B98E", fillColorHex = "#2EC49A", filled = true,
            semantic = SemanticInfo(whatIsIt = "Tree leaves", represents = "Green foliage canopy")
        )

        val bgLayer = Layer(
            id = bgLayerId,
            name = "Park Environment",
            category = LayerCategory.BACKGROUND,
            orderIndex = 0,
            semantic = SemanticInfo(
                whatIsIt = "Outdoor park backdrop",
                represents = "Sunny meadow with mountains and tree",
                whereLocated = "Behind all characters and props",
                inFrontOf = "Stickman, butterfly, and text"
            ),
            sceneStrokes = listOf(mountainStroke, groundStroke),
            sceneShapes = listOf(treeTrunk, treeCanopy)
        )

        // Stickman character strokes + 2D pose
        val headCircle = SceneShape(
            id = "shape-stickman-head",
            layerId = charLayerId,
            shapeType = ShapeType.ELLIPSE,
            x = 480f, y = 890f, width = 120f, height = 120f,
            strokeColorHex = "#22242C", fillColorHex = "#FFD15C", strokeWidth = 10f, filled = true,
            semantic = SemanticInfo(whatIsIt = "Stickman head")
        )
        val stickBody = DrawingStroke(
            id = "stroke-stickman-body",
            layerId = charLayerId,
            tool = DrawingTool.PEN,
            colorHex = "#22242C",
            strokeWidth = 12f,
            points = listOf(pt(540f, 1010f), pt(540f, 1210f)),
            semanticLabel = "Stickman torso"
        )
        val stickArms = DrawingStroke(
            id = "stroke-stickman-arms",
            layerId = charLayerId,
            tool = DrawingTool.PEN,
            colorHex = "#22242C",
            strokeWidth = 11f,
            points = listOf(pt(420f, 1110f), pt(540f, 1060f), pt(670f, 990f)),
            semanticLabel = "Stickman raised waving arms"
        )
        val stickLegs = DrawingStroke(
            id = "stroke-stickman-legs",
            layerId = charLayerId,
            tool = DrawingTool.PEN,
            colorHex = "#22242C",
            strokeWidth = 12f,
            points = listOf(pt(465f, 1380f), pt(540f, 1210f), pt(615f, 1380f)),
            semanticLabel = "Stickman standing legs"
        )

        val charLayer = Layer(
            id = charLayerId,
            name = "Stickman Hero",
            category = LayerCategory.CHARACTER,
            orderIndex = 1,
            semantic = SemanticInfo(
                whatIsIt = "Friendly stickman character",
                represents = "Main protagonist watching a butterfly",
                whatIsItDoing = "Standing in the park and waving",
                whereLocated = "Center-bottom of the 9:16 frame",
                behind = "Park Environment",
                inFrontOf = "Butterfly & Sparkles",
                howShouldItMove = "Gentle bounce and wave while looking up",
                whatShouldHappenNext = "Watches the butterfly complete an orbit"
            ),
            sceneStrokes = listOf(stickBody, stickArms, stickLegs),
            sceneShapes = listOf(headCircle),
            skeletonPose = SkeletonPose(
                name = "Friendly Wave",
                note = "Right hand raised toward orbiting butterfly",
                joints = defaultStickmanJoints(540f, 1140f)
            )
        )

        val leftWing = SceneShape(
            id = "shape-butterfly-left",
            layerId = butterflyLayerId,
            shapeType = ShapeType.ELLIPSE,
            x = 710f, y = 760f, width = 56f, height = 74f,
            rotationDeg = -20f,
            strokeColorHex = "#FF6B5B", fillColorHex = "#FF6B5B", filled = true,
            semantic = SemanticInfo(whatIsIt = "Butterfly left wing")
        )
        val rightWing = SceneShape(
            id = "shape-butterfly-right",
            layerId = butterflyLayerId,
            shapeType = ShapeType.ELLIPSE,
            x = 758f, y = 760f, width = 56f, height = 74f,
            rotationDeg = 20f,
            strokeColorHex = "#FF6B5B", fillColorHex = "#FFD15C", filled = true,
            semantic = SemanticInfo(whatIsIt = "Butterfly right wing")
        )
        val butterflyLayer = Layer(
            id = butterflyLayerId,
            name = "Coral Butterfly",
            category = LayerCategory.PROPS,
            orderIndex = 2,
            semantic = SemanticInfo(
                whatIsIt = "Coral and yellow butterfly",
                represents = "Playful companion flying around the stickman",
                whatIsItDoing = "Orbiting clockwise around the stickman",
                whereLocated = "Upper-right near stickman head",
                howShouldItMove = "Smooth clockwise orbit around Stickman Hero"
            ),
            sceneShapes = listOf(leftWing, rightWing)
        )

        val morphSeedShape = SceneShape(
            id = "shape-morph-prop",
            layerId = morphLayerId,
            shapeType = ShapeType.ELLIPSE,
            x = 780f, y = 1230f, width = 120f, height = 120f,
            strokeColorHex = "#3E9BFF", fillColorHex = "#4EA8FF", filled = true,
            semantic = SemanticInfo(
                whatIsIt = "Magic floating orb",
                represents = "Ball that transforms into a gift box",
                howShouldItMove = "Morph from circle to square while floating"
            )
        )
        val morphLayer = Layer(
            id = morphLayerId,
            name = "Morphing Orb",
            category = LayerCategory.PROPS,
            orderIndex = 3,
            semantic = SemanticInfo(
                whatIsIt = "Circle-to-square morphing shape",
                whatIsItDoing = "Morphs smoothly from blue ball into coral box"
            ),
            sceneShapes = listOf(morphSeedShape)
        )

        val titleLayer = Layer(
            id = textLayerId,
            name = "Opening Title",
            category = LayerCategory.TEXT,
            orderIndex = 4,
            semantic = SemanticInfo(
                whatIsIt = "Opening Reel title caption",
                whatIsItDoing = "Bounces in at top safe-area"
            ),
            sceneTexts = listOf(
                SceneText(
                    id = "text-hello-park",
                    layerId = textLayerId,
                    text = "Morning Walk!",
                    x = 270f,
                    y = 420f,
                    fontSizeSp = 64f,
                    colorHex = "#22242C",
                    animationStyle = TextAnimationStyle.BOUNCE_IN,
                    semantic = SemanticInfo(whatIsIt = "Reel hook headline")
                )
            )
        )

        val fxLayer = Layer(
            id = fxLayerId,
            name = "Sparkle Burst",
            category = LayerCategory.EFFECTS,
            orderIndex = 5,
            semantic = SemanticInfo(
                whatIsIt = "Golden sparkle particles",
                whatIsItDoing = "Burst gently behind the butterfly trail"
            )
        )

        // Two subtle cel drawings on frame-by-frame to show wing flutter
        val wingArc1 = DrawingStroke(
            id = "stroke-wing-flutter-1",
            layerId = butterflyLayerId,
            tool = DrawingTool.PEN,
            colorHex = "#FFD15C",
            strokeWidth = 6f,
            points = listOf(pt(705f, 750f), pt(735f, 725f), pt(760f, 745f)),
            semanticLabel = "Wing flutter accent arc (upstroke)"
        )
        val wingArc2 = DrawingStroke(
            id = "stroke-wing-flutter-2",
            layerId = butterflyLayerId,
            tool = DrawingTool.PEN,
            colorHex = "#FFD15C",
            strokeWidth = 6f,
            points = listOf(pt(705f, 835f), pt(735f, 855f), pt(765f, 835f)),
            semanticLabel = "Wing flutter accent arc (downstroke)"
        )

        val frame1 = AnimationFrame(
            id = "sample-frame-1",
            orderIndex = 0,
            exposureFrames = 4,
            note = "Butterfly wings up, stickman smiles",
            layerDrawings = mapOf(butterflyLayerId to listOf(wingArc1))
        )
        val frame2 = AnimationFrame(
            id = "sample-frame-2",
            orderIndex = 1,
            exposureFrames = 4,
            note = "Butterfly wings down",
            layerDrawings = mapOf(butterflyLayerId to listOf(wingArc2))
        )

        val orbitTrack = MotionTrack(
            id = "track-butterfly-orbit",
            name = "Butterfly Orbit",
            targetType = MotionTargetType.LAYER,
            targetId = butterflyLayerId,
            actionType = MotionActionType.ORBIT,
            startTimeMs = 0L,
            durationMs = 4000L,
            repeatCount = 1,
            easing = EasingType.EASE_IN_OUT,
            orbitConfig = OrbitConfig(
                targetId = charLayerId,
                targetName = "Stickman Hero",
                direction = OrbitDirection.CLOCKWISE,
                radiusPx = 230f,
                revolutions = 1f,
                verticalSquash = 0.6f
            ),
            orientToPath = false,
            description = "Make the butterfly fly smoothly clockwise around the Stickman Hero"
        )

        val bounceTrack = MotionTrack(
            id = "track-stickman-float",
            name = "Stickman Gentle Bounce",
            targetType = MotionTargetType.CHARACTER,
            targetId = charLayerId,
            actionType = MotionActionType.FLOAT,
            startTimeMs = 0L,
            durationMs = 4000L,
            repeatCount = 1,
            easing = EasingType.EASE_IN_OUT,
            description = "Subtle idle breathing/floating motion"
        )

        val morphTrack = MotionTrack(
            id = "track-orb-morph",
            name = "Orb to Box Morph",
            targetType = MotionTargetType.SHAPE,
            targetId = morphLayerId,
            actionType = MotionActionType.MORPH,
            startTimeMs = 500L,
            durationMs = 3000L,
            repeatCount = 1,
            pingPong = true,
            easing = EasingType.EASE_IN_OUT,
            morphConfig = ShapeMorphConfig(
                sourceShape = ShapeType.ELLIPSE,
                targetShape = ShapeType.RECTANGLE,
                sourceWidth = 120f,
                sourceHeight = 120f,
                targetWidth = 140f,
                targetHeight = 140f,
                sourceColorHex = "#4EA8FF",
                targetColorHex = "#FF6B5B",
                centerX = 840f,
                centerY = 1290f
            ),
            description = "Transforms blue ball into coral square using 32-point polyline resampling"
        )

        val particleTrack = MotionTrack(
            id = "track-sparkles",
            name = "Butterfly Sparkles",
            targetType = MotionTargetType.PARTICLES,
            targetId = fxLayerId,
            actionType = MotionActionType.PARTICLES,
            startTimeMs = 400L,
            durationMs = 3200L,
            repeatCount = 1,
            particleConfig = ParticleConfig(
                style = "Sparkles",
                originX = 540f,
                originY = 920f,
                count = 16,
                spreadRadius = 240f,
                colorHex = "#FFD15C",
                gravity = 0.15f
            ),
            description = "Golden sparkles radiate from around the character"
        )

        val cameraTrack = CameraTrack(
            keyframes = listOf(
                CameraKeyframe(id = "cam-1", timeMs = 0L, zoom = 1.0f, note = "Wide park establishing view"),
                CameraKeyframe(id = "cam-2", timeMs = 2500L, zoom = 1.12f, panY = -40f, note = "Gentle push-in as butterfly orbits")
            ),
            directionSummary = "Slow 12% push-in toward stickman and butterfly"
        )

        val scene = Scene(
            id = "scene-park-01",
            orderIndex = 0,
            name = "Park Morning Walk",
            durationMs = 4000L,
            background = BackgroundSpec(
                type = BackgroundType.GRADIENT,
                primaryColorHex = "#DFF2FF",
                secondaryColorHex = "#FFF7E4",
                gradientAngleDeg = 90f,
                textureNote = "Warm morning paper grain",
                environmentDescription = "A bright morning park with a soft sky gradient, distant lavender mountains, a green tree on the left, and grassy ground.",
                elementsDescription = listOf(
                    "Sky: soft blue-to-warm-cream vertical gradient",
                    "Mountains: lavender ridge across middle horizon",
                    "Tree: brown trunk and mint green canopy on left",
                    "Ground: green grass line at y=1360"
                )
            ),
            layers = listOf(bgLayer, charLayer, butterflyLayer, morphLayer, titleLayer, fxLayer),
            frames = listOf(frame1, frame2),
            motionTracks = listOf(orbitTrack, bounceTrack, morphTrack, particleTrack),
            cameraTrack = cameraTrack,
            audioPlan = AudioPlan(
                voiceoverText = "Even a tiny morning walk feels magical when a butterfly says hello.",
                captionsText = "Morning Walk! ✨",
                soundEffects = listOf(
                    AudioCue(timeMs = 200L, type = "SFX", description = "Soft chime sparkle when butterfly appears"),
                    AudioCue(timeMs = 1800L, type = "SFX", description = "Gentle wing flutter whoosh")
                ),
                musicTrackNote = "Warm upbeat acoustic ukulele lo-fi loop (95 BPM)",
                pausesAndSilenceNote = "0.4s breath before voiceover starts"
            ),
            transitionOut = SceneTransition(type = SceneTransitionType.CROSSFADE, durationMs = 350L),
            notes = "Sample Reel scene ready for OpenCode text-only export"
        )

        return FramePilotProject(
            id = "sample-park-butterfly",
            name = "Park Butterfly Reel",
            createdAtIso = "2026-11-25T10:00:00Z",
            updatedAtIso = "2026-11-25T10:00:00Z",
            canvasWidth = w,
            canvasHeight = h,
            aspectRatioLabel = "9:16",
            fps = 24,
            scenes = listOf(scene),
            creativeBrief = "A friendly stickman stands in a sunny park while a coral butterfly orbits around him and a blue orb morphs into a coral box."
        )
    }

    /**
     * Regression fixture matching Part 14 (23):
     * - 8 authored drawings (cels 1..8)
     * - 14 total exposure frames
     * - 30 FPS project
     * - Last-frame note about faster bouncing
     */
    fun createEightCelBasketballRegressionFixture(): FramePilotProject {
        val w = 1080
        val h = 1920
        val layerId = "basketball-layer-main"
        val bgLayerId = "basketball-layer-court"

        val courtLayer = Layer(
            id = bgLayerId,
            name = "Court Floor",
            category = LayerCategory.BACKGROUND,
            orderIndex = 0,
            semantic = SemanticInfo(
                whatIsIt = "Basketball court hardwood line",
                whereLocated = "Lower third of canvas at y=1500"
            ),
            sceneStrokes = listOf(
                DrawingStroke(
                    id = "court-floor-line",
                    layerId = bgLayerId,
                    tool = DrawingTool.LINE,
                    colorHex = "#8D5B4C",
                    strokeWidth = 10f,
                    points = listOf(
                        StrokePoint(80f, 1500f, 1f, 80f / w, 1500f / h),
                        StrokePoint(1000f, 1500f, 1f, 1000f / w, 1500f / h)
                    ),
                    semanticLabel = "Court floor baseline"
                )
            )
        )

        val ballLayer = Layer(
            id = layerId,
            name = "Bouncing Basketball",
            category = LayerCategory.PROPS,
            orderIndex = 1,
            semantic = SemanticInfo(
                whatIsIt = "Orange basketball drawn across 8 cels",
                represents = "Dribbling ball accelerating on the final bounce",
                howShouldItMove = "Drops from high arc, squashes on floor at cel 5, rebounds faster"
            )
        )

        // 8 authored drawings totaling 14 exposure frames: 2 + 2 + 2 + 1 + 2 + 2 + 2 + 1 = 14
        val exposures = listOf(2, 2, 2, 1, 2, 2, 2, 1)
        val yPositions = listOf(420f, 680f, 1020f, 1340f, 1435f, 1180f, 880f, 640f)
        val squashFactor = listOf(1.0f, 1.0f, 0.96f, 0.90f, 0.72f, 0.94f, 1.0f, 1.0f)

        val frames = (0 until 8).map { idx ->
            val cy = yPositions[idx]
            val sy = squashFactor[idx]
            val rx = 72f / sy
            val ry = 72f * sy
            val circlePoints = (0..16).map { step ->
                val angle = (step / 16f) * 2f * PI.toFloat()
                val px = 540f + cos(angle) * rx
                val py = cy + sin(angle) * ry
                StrokePoint(px, py, 1f, px / w, py / h)
            }
            val ballStroke = DrawingStroke(
                id = "bball-cel-${idx + 1}-outline",
                layerId = layerId,
                tool = DrawingTool.PEN,
                colorHex = "#FF6B5B",
                strokeWidth = 9f,
                points = circlePoints,
                semanticLabel = "Basketball outline at cel ${idx + 1}"
            )
            val note = if (idx == 7) {
                "Faster bouncing on final rebound — accelerate speed from here!"
            } else {
                "Bounce cel ${idx + 1} (y=${cy.toInt()})"
            }
            AnimationFrame(
                id = "bball-frame-uuid-${idx + 1}",
                orderIndex = idx,
                exposureFrames = exposures[idx],
                note = note,
                layerDrawings = mapOf(layerId to listOf(ballStroke))
            )
        }

        val scene = Scene(
            id = "bball-scene-1",
            orderIndex = 0,
            name = "Basketball Bounce Test",
            durationMs = 500L,
            background = BackgroundSpec(
                type = BackgroundType.SOLID,
                primaryColorHex = "#FFFDF9",
                environmentDescription = "Clean studio court backdrop with floor baseline"
            ),
            layers = listOf(courtLayer, ballLayer),
            frames = frames
        )

        return FramePilotProject(
            id = "fixture-8cel-basketball",
            name = "8-Cel Basketball Regression",
            createdAtIso = "2026-11-25T10:00:00Z",
            updatedAtIso = "2026-11-25T10:00:00Z",
            canvasWidth = w,
            canvasHeight = h,
            aspectRatioLabel = "9:16",
            fps = 30,
            scenes = listOf(scene),
            creativeBrief = "Regression fixture with 8 authored drawings, 14 total exposure frames, 30 FPS, and last-frame faster bouncing note."
        )
    }
}
