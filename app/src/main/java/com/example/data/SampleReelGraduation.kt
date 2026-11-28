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
import com.example.domain.model.ElementGroup
import com.example.domain.model.FramePilotProject
import com.example.domain.model.JointPoint
import com.example.domain.model.Layer
import com.example.domain.model.LayerCategory
import com.example.domain.model.MotionActionType
import com.example.domain.model.MotionKeyframe
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
import com.example.engine.RigBonePreset
import com.example.engine.RigEngine
import kotlin.math.PI
import kotlin.math.sin

/**
 * Single flagship 30-second (6 scenes x 5s @ 24 FPS) vertical Reel demonstrating
 * FramePilot's full production workflow: joint-synced character cels, clean layered
 * environments, motion paths, orbits, shape morphs, styled particles (leaves, rain,
 * dawn motes, sparkles), smooth camera choreography, safe-area captions, and VO/audio direction.
 */
object SampleReelGraduation {

    private const val W = 1080
    private const val H = 1920

    private data class StudentCel(
        val pose: SkeletonPose,
        val shapes: List<SceneShape>,
        val strokes: List<DrawingStroke>,
        val groups: List<ElementGroup>
    )

    private fun pt(x: Float, y: Float) =
        StrokePoint(x = x, y = y, pressure = 1f, normX = x / W, normY = y / H)

    private fun centeredTextX(text: String, fontSizePx: Float): Float {
        val estWidth = text.length * fontSizePx * 0.56f
        return ((W - estWidth) / 2f).coerceIn(72f, W - 160f)
    }

    private fun buildPoseJoints(
        centerX: Float,
        centerY: Float,
        scale: Float = 1f,
        overrides: Map<String, Pair<Float, Float>> = emptyMap()
    ): Map<String, JointPoint> {
        val base = defaultStickmanJoints(540f, 960f)
        return base.mapValues { (name, j) ->
            val dx = (j.x - 540f) * scale
            val dy = (j.y - 960f) * scale
            val extra = overrides[name] ?: (0f to 0f)
            JointPoint(centerX + dx + extra.first * scale, centerY + dy + extra.second * scale)
        }
    }

    /**
     * Builds BOTH the SkeletonPose joints and matching character artwork anchored directly
     * to those exact joint coordinates so paused Pose Editor rigs and playback cels align 1:1.
     */
    private fun buildStudentCel(
        layerId: String,
        poseName: String,
        cx: Float,
        cy: Float,
        scale: Float = 1f,
        facingBack: Boolean = false,
        holdingPaper: Boolean = false,
        jointOverrides: Map<String, Pair<Float, Float>> = emptyMap()
    ): StudentCel {
        val joints = buildPoseJoints(cx, cy, scale, jointOverrides)
        val head = joints.getValue("head")
        val neck = joints.getValue("neck")
        val chest = joints.getValue("chest")
        val hip = joints.getValue("hip")
        val lSh = joints.getValue("leftShoulder")
        val lEl = joints.getValue("leftElbow")
        val lHa = joints.getValue("leftHand")
        val rSh = joints.getValue("rightShoulder")
        val rEl = joints.getValue("rightElbow")
        val rHa = joints.getValue("rightHand")
        val lKn = joints.getValue("leftKnee")
        val lFt = joints.getValue("leftFoot")
        val rKn = joints.getValue("rightKnee")
        val rFt = joints.getValue("rightFoot")

        val pose = SkeletonPose(name = poseName, joints = joints)
        fun att(preset: RigBonePreset) = RigEngine.createAttachment(preset, pose, layerId)

        val headGroupId = "$layerId-grp-head"
        val torsoGroupId = "$layerId-grp-torso"
        val headAtt = att(RigBonePreset.HEAD)
        val torsoAtt = att(RigBonePreset.TORSO)

        val groups = listOf(
            ElementGroup(id = headGroupId, name = "Head & Glasses", rigAttachment = headAtt),
            ElementGroup(id = torsoGroupId, name = "Torso & Backpack", rigAttachment = torsoAtt)
        )

        val headR = 44f * scale
        val jacketW = 84f * scale
        val jacketTop = neck.y + 4f * scale
        val jacketH = (hip.y - jacketTop + 16f * scale).coerceAtLeast(60f * scale)
        val limbW = 16f * scale

        val shapes = mutableListOf<SceneShape>()
        val strokes = mutableListOf<DrawingStroke>()

        // Backpack silhouette behind torso
        shapes += SceneShape(
            layerId = layerId,
            shapeType = ShapeType.RECTANGLE,
            x = chest.x - 50f * scale,
            y = jacketTop + 8f * scale,
            width = 100f * scale,
            height = jacketH * 0.78f,
            strokeColorHex = "#1E2A38",
            fillColorHex = "#2D3E50",
            strokeWidth = 3f,
            filled = true,
            semantic = SemanticInfo(whatIsIt = "Canvas student backpack"),
            groupId = torsoGroupId,
            rigAttachment = torsoAtt
        )

        // Left & right legs split into upper thigh + lower shin for realistic knee articulation
        strokes += DrawingStroke(
            layerId = layerId,
            tool = DrawingTool.PEN,
            colorHex = "#283344",
            strokeWidth = limbW,
            points = listOf(pt(hip.x - 16f * scale, hip.y), pt(lKn.x, lKn.y)),
            semanticLabel = "Graduate left thigh",
            rigAttachment = att(RigBonePreset.LEFT_THIGH)
        )
        strokes += DrawingStroke(
            layerId = layerId,
            tool = DrawingTool.PEN,
            colorHex = "#283344",
            strokeWidth = limbW,
            points = listOf(pt(lKn.x, lKn.y), pt(lFt.x, lFt.y)),
            semanticLabel = "Graduate left shin",
            rigAttachment = att(RigBonePreset.LEFT_SHIN)
        )
        strokes += DrawingStroke(
            layerId = layerId,
            tool = DrawingTool.PEN,
            colorHex = "#283344",
            strokeWidth = limbW,
            points = listOf(pt(hip.x + 16f * scale, hip.y), pt(rKn.x, rKn.y)),
            semanticLabel = "Graduate right thigh",
            rigAttachment = att(RigBonePreset.RIGHT_THIGH)
        )
        strokes += DrawingStroke(
            layerId = layerId,
            tool = DrawingTool.PEN,
            colorHex = "#283344",
            strokeWidth = limbW,
            points = listOf(pt(rKn.x, rKn.y), pt(rFt.x, rFt.y)),
            semanticLabel = "Graduate right shin",
            rigAttachment = att(RigBonePreset.RIGHT_SHIN)
        )

        // Shoes attached rigidly to feet
        shapes += SceneShape(
            layerId = layerId,
            shapeType = ShapeType.ELLIPSE,
            x = lFt.x - 18f * scale,
            y = lFt.y - 10f * scale,
            width = 36f * scale,
            height = 20f * scale,
            strokeColorHex = "#1C212B",
            fillColorHex = "#ECEAE4",
            strokeWidth = 2.5f,
            filled = true,
            rigAttachment = att(RigBonePreset.LEFT_FOOT)
        )
        shapes += SceneShape(
            layerId = layerId,
            shapeType = ShapeType.ELLIPSE,
            x = rFt.x - 18f * scale,
            y = rFt.y - 10f * scale,
            width = 36f * scale,
            height = 20f * scale,
            strokeColorHex = "#1C212B",
            fillColorHex = "#ECEAE4",
            strokeWidth = 2.5f,
            filled = true,
            rigAttachment = att(RigBonePreset.RIGHT_FOOT)
        )

        // Arms split into upper arm + forearm for crisp elbow articulation
        strokes += DrawingStroke(
            layerId = layerId,
            tool = DrawingTool.PEN,
            colorHex = "#4E708F",
            strokeWidth = 14f * scale,
            points = listOf(pt(lSh.x, lSh.y), pt(lEl.x, lEl.y)),
            semanticLabel = "Graduate left upper arm",
            rigAttachment = att(RigBonePreset.LEFT_UPPER_ARM)
        )
        strokes += DrawingStroke(
            layerId = layerId,
            tool = DrawingTool.PEN,
            colorHex = "#4E708F",
            strokeWidth = 14f * scale,
            points = listOf(pt(lEl.x, lEl.y), pt(lHa.x, lHa.y)),
            semanticLabel = "Graduate left forearm",
            rigAttachment = att(RigBonePreset.LEFT_FOREARM)
        )
        strokes += DrawingStroke(
            layerId = layerId,
            tool = DrawingTool.PEN,
            colorHex = "#4E708F",
            strokeWidth = 14f * scale,
            points = listOf(pt(rSh.x, rSh.y), pt(rEl.x, rEl.y)),
            semanticLabel = "Graduate right upper arm",
            rigAttachment = att(RigBonePreset.RIGHT_UPPER_ARM)
        )
        strokes += DrawingStroke(
            layerId = layerId,
            tool = DrawingTool.PEN,
            colorHex = "#4E708F",
            strokeWidth = 14f * scale,
            points = listOf(pt(rEl.x, rEl.y), pt(rHa.x, rHa.y)),
            semanticLabel = "Graduate right forearm",
            rigAttachment = att(RigBonePreset.RIGHT_FOREARM)
        )

        // Hands
        shapes += SceneShape(
            layerId = layerId,
            shapeType = ShapeType.ELLIPSE,
            x = lHa.x - 11f * scale,
            y = lHa.y - 11f * scale,
            width = 22f * scale,
            height = 22f * scale,
            strokeColorHex = "#C99674",
            fillColorHex = "#F4C9A8",
            strokeWidth = 2f,
            filled = true,
            rigAttachment = att(RigBonePreset.LEFT_HAND)
        )
        shapes += SceneShape(
            layerId = layerId,
            shapeType = ShapeType.ELLIPSE,
            x = rHa.x - 11f * scale,
            y = rHa.y - 11f * scale,
            width = 22f * scale,
            height = 22f * scale,
            strokeColorHex = "#C99674",
            fillColorHex = "#F4C9A8",
            strokeWidth = 2f,
            filled = true,
            rigAttachment = att(RigBonePreset.RIGHT_HAND)
        )

        // Denim overshirt torso centered cleanly on neck-hip axis
        shapes += SceneShape(
            layerId = layerId,
            shapeType = ShapeType.RECTANGLE,
            x = (chest.x + hip.x) * 0.5f - jacketW * 0.5f,
            y = jacketTop,
            width = jacketW,
            height = jacketH,
            strokeColorHex = "#2D4263",
            fillColorHex = "#5A7D9A",
            strokeWidth = 3.5f,
            filled = true,
            semantic = SemanticInfo(whatIsIt = "Blue denim overshirt"),
            groupId = torsoGroupId,
            rigAttachment = torsoAtt
        )

        // Portfolio / resume page in right hand
        if (holdingPaper) {
            shapes += SceneShape(
                layerId = layerId,
                shapeType = ShapeType.RECTANGLE,
                x = rHa.x - 6f * scale,
                y = rHa.y - 34f * scale,
                width = 40f * scale,
                height = 52f * scale,
                rotationDeg = -10f,
                strokeColorHex = "#7A746C",
                fillColorHex = "#FFF8EC",
                strokeWidth = 2.5f,
                filled = true,
                semantic = SemanticInfo(whatIsIt = "Resume & portfolio sheet"),
                rigAttachment = att(RigBonePreset.RIGHT_HAND)
            )
        }

        // Head centered cleanly on head joint
        shapes += SceneShape(
            layerId = layerId,
            shapeType = ShapeType.ELLIPSE,
            x = head.x - headR,
            y = head.y - headR,
            width = headR * 2f,
            height = headR * 2f,
            strokeColorHex = "#22242C",
            fillColorHex = if (facingBack) "#2A2B36" else "#F4C9A8",
            strokeWidth = 3.5f,
            filled = true,
            semantic = SemanticInfo(whatIsIt = "Graduate head"),
            groupId = headGroupId,
            rigAttachment = headAtt
        )

        // Hair cap
        shapes += SceneShape(
            layerId = layerId,
            shapeType = ShapeType.ELLIPSE,
            x = head.x - headR * 0.96f,
            y = head.y - headR * 1.04f,
            width = headR * 1.92f,
            height = headR * 0.95f,
            strokeColorHex = "#232530",
            fillColorHex = "#2B2D3A",
            strokeWidth = 0f,
            filled = true,
            groupId = headGroupId,
            rigAttachment = headAtt
        )

        if (!facingBack) {
            // Glasses lenses
            shapes += SceneShape(
                layerId = layerId,
                shapeType = ShapeType.ELLIPSE,
                x = head.x - 26f * scale,
                y = head.y - 6f * scale,
                width = 20f * scale,
                height = 20f * scale,
                strokeColorHex = "#1E1F26",
                strokeWidth = 3.5f,
                filled = false,
                semantic = SemanticInfo(whatIsIt = "Round glasses left lens"),
                groupId = headGroupId,
                rigAttachment = headAtt
            )
            shapes += SceneShape(
                layerId = layerId,
                shapeType = ShapeType.ELLIPSE,
                x = head.x + 6f * scale,
                y = head.y - 6f * scale,
                width = 20f * scale,
                height = 20f * scale,
                strokeColorHex = "#1E1F26",
                strokeWidth = 3.5f,
                filled = false,
                semantic = SemanticInfo(whatIsIt = "Round glasses right lens"),
                groupId = headGroupId,
                rigAttachment = headAtt
            )
            // Red student lanyard & campus ID badge
            strokes += DrawingStroke(
                layerId = layerId,
                tool = DrawingTool.PEN,
                colorHex = "#D94E41",
                strokeWidth = 4.5f * scale,
                points = listOf(
                    pt(chest.x - 16f * scale, neck.y + 8f * scale),
                    pt(chest.x, chest.y + 18f * scale),
                    pt(chest.x + 16f * scale, neck.y + 8f * scale)
                ),
                semanticLabel = "Red campus lanyard strap",
                groupId = torsoGroupId,
                rigAttachment = torsoAtt
            )
            shapes += SceneShape(
                layerId = layerId,
                shapeType = ShapeType.RECTANGLE,
                x = chest.x - 11f * scale,
                y = chest.y + 16f * scale,
                width = 22f * scale,
                height = 30f * scale,
                strokeColorHex = "#D94E41",
                fillColorHex = "#FFFDF9",
                strokeWidth = 2.5f,
                filled = true,
                semantic = SemanticInfo(whatIsIt = "Campus student ID badge"),
                groupId = torsoGroupId,
                rigAttachment = torsoAtt
            )
        }

        return StudentCel(pose = pose, shapes = shapes, strokes = strokes, groups = groups)
    }

    private fun captionCard(
        layerId: String,
        yTop: Float,
        height: Float,
        bgHex: String = "#171A24",
        bgAlpha: Float = 0.72f
    ) = SceneShape(
        layerId = layerId,
        shapeType = ShapeType.RECTANGLE,
        x = 84f,
        y = yTop,
        width = W - 168f,
        height = height,
        strokeColorHex = bgHex,
        fillColorHex = bgHex,
        strokeWidth = 0f,
        opacity = bgAlpha,
        filled = true,
        semantic = SemanticInfo(whatIsIt = "Caption readability backdrop pill")
    )

    fun create(): FramePilotProject {
        return FramePilotProject(
            id = "sample-graduation-reel",
            name = "After College Ends (30s)",
            createdAtIso = "2026-11-26T09:00:00Z",
            updatedAtIso = "2026-11-27T01:00:00Z",
            canvasWidth = W,
            canvasHeight = H,
            aspectRatioLabel = "9:16",
            fps = 24,
            scenes = listOf(
                buildScene1CampusHook(),
                buildScene2HallwayDrift(),
                buildScene3RainyCrossroads(),
                buildScene4LateNightDesk(),
                buildScene5SunriseClarity(),
                buildScene6NewChapter()
            ),
            creativeBrief = "A 30-second, 6-scene animated short film reel exploring post-graduation uncertainty and growth: from campus courtyard hook -> hallway routine -> rainy city crossroads -> midnight self-doubt -> dawn rooftop clarity -> stepping into a new chapter.",
            tutorialCompleted = true,
            exportSketchAsDirectionOnly = true,
            exportStyleGuideNote = "Follow my skill.md: warm editorial flat-vector short film style, atmospheric lighting, expressive character acting, crisp centered captions"
        )
    }

    private fun buildScene1CampusHook(): Scene {
        val bgId = "grad-s1-bg"
        val crowdId = "grad-s1-crowd"
        val heroId = "grad-s1-hero"
        val titleId = "grad-s1-text"
        val fxId = "grad-s1-fx"

        val bgLayer = Layer(
            id = bgId,
            name = "Campus Courtyard",
            category = LayerCategory.BACKGROUND,
            orderIndex = 0,
            semantic = SemanticInfo(
                whatIsIt = "University brick hall & golden-hour courtyard trees",
                represents = "Familiar college campus at sunset",
                whereLocated = "Behind protagonist and passing students"
            ),
            sceneShapes = listOf(
                SceneShape(layerId = bgId, shapeType = ShapeType.RECTANGLE, x = 130f, y = 520f, width = 820f, height = 520f, strokeColorHex = "#8C6D58", fillColorHex = "#B38A6E", strokeWidth = 0f, filled = true, semantic = SemanticInfo(whatIsIt = "University hall facade")),
                SceneShape(layerId = bgId, shapeType = ShapeType.RECTANGLE, x = 470f, y = 800f, width = 140f, height = 240f, strokeColorHex = "#3E2F28", fillColorHex = "#4D3B32", strokeWidth = 0f, filled = true, semantic = SemanticInfo(whatIsIt = "Main campus doorway")),
                SceneShape(layerId = bgId, shapeType = ShapeType.ELLIPSE, x = 490f, y = 555f, width = 100f, height = 100f, strokeColorHex = "#5A473C", fillColorHex = "#FFF3D6", strokeWidth = 4f, filled = true, semantic = SemanticInfo(whatIsIt = "Campus clock tower face")),
                SceneShape(layerId = bgId, shapeType = ShapeType.RECTANGLE, x = 0f, y = 1040f, width = W.toFloat(), height = 880f, strokeColorHex = "#635042", fillColorHex = "#7A6353", strokeWidth = 0f, filled = true, semantic = SemanticInfo(whatIsIt = "Courtyard stone ground")),
                SceneShape(layerId = bgId, shapeType = ShapeType.ELLIPSE, x = 50f, y = 780f, width = 220f, height = 250f, strokeColorHex = "#366356", fillColorHex = "#3C6E60", strokeWidth = 0f, filled = true, semantic = SemanticInfo(whatIsIt = "Left courtyard tree canopy")),
                SceneShape(layerId = bgId, shapeType = ShapeType.ELLIPSE, x = 810f, y = 780f, width = 220f, height = 250f, strokeColorHex = "#366356", fillColorHex = "#3C6E60", strokeWidth = 0f, filled = true, semantic = SemanticInfo(whatIsIt = "Right courtyard tree canopy"))
            ),
            sceneStrokes = listOf(
                DrawingStroke(layerId = bgId, tool = DrawingTool.LINE, colorHex = "#9B806B", strokeWidth = 6f, points = listOf(pt(390f, 1040f), pt(140f, 1780f)), semanticLabel = "Walkway left perspective"),
                DrawingStroke(layerId = bgId, tool = DrawingTool.LINE, colorHex = "#9B806B", strokeWidth = 6f, points = listOf(pt(690f, 1040f), pt(940f, 1780f)), semanticLabel = "Walkway right perspective")
            )
        )

        val crowdLayer = Layer(
            id = crowdId,
            name = "Classmates Silhouette",
            category = LayerCategory.PROPS,
            orderIndex = 1,
            semantic = SemanticInfo(whatIsIt = "Background students walking across courtyard", howShouldItMove = "Gentle horizontal stroll"),
            sceneShapes = listOf(
                SceneShape(layerId = crowdId, shapeType = ShapeType.RECTANGLE, x = 195f, y = 1085f, width = 54f, height = 148f, strokeColorHex = "#4C6073", fillColorHex = "#4C6073", strokeWidth = 0f, opacity = 0.78f, filled = true, semantic = SemanticInfo(whatIsIt = "Student silhouette left")),
                SceneShape(layerId = crowdId, shapeType = ShapeType.RECTANGLE, x = 835f, y = 1070f, width = 54f, height = 152f, strokeColorHex = "#5A4B69", fillColorHex = "#5A4B69", strokeWidth = 0f, opacity = 0.78f, filled = true, semantic = SemanticInfo(whatIsIt = "Student silhouette right"))
            )
        )

        val heroCel = buildStudentCel(
            layerId = heroId,
            poseName = "Courtyard Pause",
            cx = 540f,
            cy = 1220f,
            scale = 1.16f,
            holdingPaper = false,
            jointOverrides = mapOf(
                "leftHand" to (42f to -28f),
                "leftElbow" to (16f to -14f),
                "rightHand" to (-42f to 42f),
                "rightElbow" to (-12f to 16f)
            )
        )
        val heroLayer = Layer(
            id = heroId,
            name = "Graduate Protagonist",
            category = LayerCategory.CHARACTER,
            orderIndex = 2,
            semantic = SemanticInfo(
                whatIsIt = "Student with round glasses, blue overshirt, backpack & red lanyard",
                whatIsItDoing = "Standing center-frame looking ahead as classmates drift past",
                howShouldItMove = "Gentle breathing float while camera slowly pushes in"
            ),
            sceneShapes = heroCel.shapes,
            sceneStrokes = heroCel.strokes,
            skeletonPose = heroCel.pose,
            groups = heroCel.groups
        )

        val titleLayer = Layer(
            id = titleId,
            name = "Opening Hook Title",
            category = LayerCategory.TEXT,
            orderIndex = 3,
            semantic = SemanticInfo(whatIsIt = "Main short-film hook headline"),
            sceneShapes = listOf(captionCard(titleId, 240f, 225f)),
            sceneTexts = listOf(
                SceneText(layerId = titleId, text = "WHO ARE YOU", x = centeredTextX("WHO ARE YOU", 48f), y = 315f, fontSizeSp = 48f, colorHex = "#FFFDF9", animationStyle = TextAnimationStyle.BOUNCE_IN, semantic = SemanticInfo(whatIsIt = "Top hook line")),
                SceneText(layerId = titleId, text = "WHEN COLLEGE ENDS?", x = centeredTextX("WHEN COLLEGE ENDS?", 44f), y = 385f, fontSizeSp = 44f, colorHex = "#FF9E44", animationStyle = TextAnimationStyle.BOUNCE_IN, semantic = SemanticInfo(whatIsIt = "Highlighted orange hook line")),
                SceneText(layerId = titleId, text = "A 30s SHORT FILM", x = centeredTextX("A 30s SHORT FILM", 24f), y = 440f, fontSizeSp = 24f, colorHex = "#E5D8C8", animationStyle = TextAnimationStyle.FADE_IN, semantic = SemanticInfo(whatIsIt = "Subtitle"))
            )
        )

        val fxLayer = Layer(
            id = fxId,
            name = "Autumn Leaves",
            category = LayerCategory.EFFECTS,
            orderIndex = 4,
            semantic = SemanticInfo(whatIsIt = "Drifting golden autumn leaves")
        )

        return Scene(
            id = "grad-scene-1",
            orderIndex = 0,
            name = "Campus Hook",
            durationMs = 5000L,
            background = BackgroundSpec(
                type = BackgroundType.GRADIENT,
                primaryColorHex = "#8A6C52",
                secondaryColorHex = "#E5B48A",
                gradientAngleDeg = 90f,
                textureNote = "Warm nostalgic sunset haze",
                environmentDescription = "Golden-hour college courtyard in front of a classic brick university hall flanked by leafy trees.",
                elementsDescription = listOf("University brick facade with clock", "Symmetrical walkway leading to doorway", "Students passing in background")
            ),
            layers = listOf(bgLayer, crowdLayer, heroLayer, titleLayer, fxLayer),
            frames = listOf(
                AnimationFrame(id = "grad-s1-f1", orderIndex = 0, exposureFrames = 24, note = "Poised establishing stance")
            ),
            motionTracks = listOf(
                MotionTrack(
                    id = "grad-s1-tr-hero",
                    name = "Protagonist Subtle Float",
                    targetType = MotionTargetType.CHARACTER,
                    targetId = heroId,
                    actionType = MotionActionType.FLOAT,
                    durationMs = 5000L,
                    description = "Gentle grounded breathing presence"
                ),
                MotionTrack(
                    id = "grad-s1-tr-crowd",
                    name = "Classmates Drift",
                    targetType = MotionTargetType.LAYER,
                    targetId = crowdId,
                    actionType = MotionActionType.MOVE,
                    durationMs = 5000L,
                    keyframes = listOf(
                        MotionKeyframe(timeMs = 0L, offsetX = -24f),
                        MotionKeyframe(timeMs = 5000L, offsetX = 48f)
                    ),
                    description = "Background classmates subtly walk across courtyard"
                ),
                MotionTrack(
                    id = "grad-s1-tr-leaves",
                    name = "Golden Leaf Drift",
                    targetType = MotionTargetType.PARTICLES,
                    targetId = fxId,
                    actionType = MotionActionType.PARTICLES,
                    durationMs = 5000L,
                    particleConfig = ParticleConfig(style = "Autumn Leaves", originX = 540f, originY = 580f, count = 18, spreadRadius = 480f, colorHex = "#FFAE52", gravity = 0.2f),
                    description = "Warm leaves drifting diagonally across campus frame"
                )
            ),
            cameraTrack = CameraTrack(
                keyframes = listOf(
                    CameraKeyframe(timeMs = 0L, zoom = 1.0f, note = "Wide courtyard establishing shot"),
                    CameraKeyframe(timeMs = 5000L, zoom = 1.08f, panY = -30f, note = "Smooth 8% push-in onto graduate")
                ),
                directionSummary = "Smooth 8% push-in toward protagonist"
            ),
            audioPlan = AudioPlan(
                voiceoverText = "You're getting closer to graduation. And the closer it gets, the more one question starts to weigh on you.",
                captionsText = "WHO ARE YOU WHEN COLLEGE ENDS?",
                soundEffects = listOf(AudioCue(timeMs = 250L, type = "SFX", description = "Soft campus bell chime")),
                musicTrackNote = "Warm minimal Rhodes piano + distant campus ambience (76 BPM)",
                pausesAndSilenceNote = "0.5s beat after hook title"
            ),
            transitionOut = SceneTransition(type = SceneTransitionType.CUT, durationMs = 0L),
            notes = "Scene 1 of 6 (0s-5s): Establish hook & character silhouette"
        )
    }

    private fun buildScene2HallwayDrift(): Scene {
        val bgId = "grad-s2-bg"
        val heroId = "grad-s2-hero"
        val notesId = "grad-s2-notes"
        val textId = "grad-s2-text"

        val bgLayer = Layer(
            id = bgId,
            name = "Campus Hallway",
            category = LayerCategory.BACKGROUND,
            orderIndex = 0,
            semantic = SemanticInfo(whatIsIt = "Sunlit school corridor with classroom doors & wall clock"),
            sceneShapes = listOf(
                SceneShape(layerId = bgId, shapeType = ShapeType.RECTANGLE, x = 0f, y = 1040f, width = W.toFloat(), height = 880f, strokeColorHex = "#B8A28B", fillColorHex = "#C2AD97", strokeWidth = 0f, filled = true, semantic = SemanticInfo(whatIsIt = "Corridor floor")),
                SceneShape(layerId = bgId, shapeType = ShapeType.RECTANGLE, x = 420f, y = 620f, width = 240f, height = 420f, strokeColorHex = "#B59576", fillColorHex = "#D4B594", strokeWidth = 0f, filled = true, semantic = SemanticInfo(whatIsIt = "Far corridor sunlit doorway")),
                SceneShape(layerId = bgId, shapeType = ShapeType.RECTANGLE, x = 95f, y = 520f, width = 180f, height = 380f, strokeColorHex = "#F3CA7A", fillColorHex = "#F7D996", strokeWidth = 0f, filled = true, semantic = SemanticInfo(whatIsIt = "Left sunlit window panels")),
                SceneShape(layerId = bgId, shapeType = ShapeType.RECTANGLE, x = 805f, y = 520f, width = 180f, height = 430f, strokeColorHex = "#5A738E", fillColorHex = "#627D98", strokeWidth = 0f, filled = true, semantic = SemanticInfo(whatIsIt = "Right classroom doors")),
                SceneShape(layerId = bgId, shapeType = ShapeType.ELLIPSE, x = 495f, y = 415f, width = 90f, height = 90f, strokeColorHex = "#22242C", fillColorHex = "#FFFDF9", strokeWidth = 4f, filled = true, semantic = SemanticInfo(whatIsIt = "Classroom wall clock"))
            ),
            sceneStrokes = listOf(
                DrawingStroke(layerId = bgId, tool = DrawingTool.LINE, colorHex = "#9E8D7B", strokeWidth = 6f, points = listOf(pt(0f, 340f), pt(420f, 620f)), semanticLabel = "Top-left corridor perspective"),
                DrawingStroke(layerId = bgId, tool = DrawingTool.LINE, colorHex = "#9E8D7B", strokeWidth = 6f, points = listOf(pt(1080f, 340f), pt(660f, 620f)), semanticLabel = "Top-right corridor perspective")
            )
        )

        // Build rigged base character once, then drive all 8 walk cels via SkeletonPose keyframes
        val baseWalkCel = buildStudentCel(
            layerId = heroId,
            poseName = "Walk Neutral",
            cx = 540f,
            cy = 1145f,
            scale = 1.15f,
            holdingPaper = true
        )
        val walkPoses = (0 until 8).map { step ->
            val phase = (step / 8f) * 2f * PI.toFloat()
            val stride = sin(phase) * 34f
            val liftL = maxOf(0f, -sin(phase)) * 18f
            val liftR = maxOf(0f, sin(phase)) * 18f
            val bobY = -kotlin.math.abs(sin(phase)) * 8f
            SkeletonPose(
                name = "Walk Cel ${step + 1}",
                joints = buildPoseJoints(
                    centerX = 540f,
                    centerY = 1145f + bobY,
                    scale = 1.15f,
                    overrides = mapOf(
                        "leftFoot" to (stride * 0.6f to -liftL),
                        "leftKnee" to (stride * 0.3f to -liftL * 0.5f),
                        "rightFoot" to (-stride * 0.6f to -liftR),
                        "rightKnee" to (-stride * 0.3f to -liftR * 0.5f),
                        "leftHand" to (-stride * 0.4f to -6f),
                        "rightHand" to (-10f + stride * 0.25f to 20f)
                    )
                )
            )
        }

        val heroLayer = Layer(
            id = heroId,
            name = "Walking Graduate",
            category = LayerCategory.CHARACTER,
            orderIndex = 1,
            semantic = SemanticInfo(whatIsIt = "Protagonist walking down hallway holding exam sheet", whatIsItDoing = "Stepping forward smoothly through predictable semesters"),
            sceneShapes = baseWalkCel.shapes,
            sceneStrokes = baseWalkCel.strokes,
            skeletonPose = walkPoses.first(),
            groups = baseWalkCel.groups
        )

        val notesLayer = Layer(
            id = notesId,
            name = "Orbiting Semesters",
            category = LayerCategory.PROPS,
            orderIndex = 2,
            semantic = SemanticInfo(whatIsIt = "Floating exam & semester checklist card", howShouldItMove = "Orbits smoothly around student representing repeating cycle"),
            sceneShapes = listOf(
                SceneShape(layerId = notesId, shapeType = ShapeType.RECTANGLE, x = 720f, y = 920f, width = 82f, height = 104f, rotationDeg = 10f, strokeColorHex = "#5A7D9A", fillColorHex = "#FFFDF9", strokeWidth = 3f, filled = true, semantic = SemanticInfo(whatIsIt = "Exam checklist page"))
            )
        )

        val textLayer = Layer(
            id = textId,
            name = "Routine Caption",
            category = LayerCategory.TEXT,
            orderIndex = 3,
            semantic = SemanticInfo(whatIsIt = "On-screen kinetic narrative caption"),
            sceneShapes = listOf(captionCard(textId, 1445f, 165f)),
            sceneTexts = listOf(
                SceneText(layerId = textId, text = "ANOTHER SEMESTER.", x = centeredTextX("ANOTHER SEMESTER.", 40f), y = 1510f, fontSizeSp = 40f, colorHex = "#FFFDF9", animationStyle = TextAnimationStyle.SLIDE_UP),
                SceneText(layerId = textId, text = "ANOTHER EXAM.", x = centeredTextX("ANOTHER EXAM.", 40f), y = 1575f, fontSizeSp = 40f, colorHex = "#FF9E44", animationStyle = TextAnimationStyle.SLIDE_UP)
            )
        )

        val frames = walkPoses.mapIndexed { idx, pose ->
            AnimationFrame(
                id = "grad-s2-f${idx + 1}",
                orderIndex = idx,
                exposureFrames = 3,
                note = "Smooth walk stride cel ${idx + 1}/8",
                layerPoses = mapOf(heroId to pose)
            )
        }

        return Scene(
            id = "grad-scene-2",
            orderIndex = 1,
            name = "Hallway Routine",
            durationMs = 5000L,
            background = BackgroundSpec(
                type = BackgroundType.GRADIENT,
                primaryColorHex = "#F5E7D6",
                secondaryColorHex = "#D2C1AE",
                gradientAngleDeg = 90f,
                environmentDescription = "Sunlit school corridor with tall yellow window panes on the left and blue classroom doors on the right."
            ),
            layers = listOf(bgLayer, heroLayer, notesLayer, textLayer),
            frames = frames,
            motionTracks = listOf(
                MotionTrack(
                    id = "grad-s2-tr-orbit",
                    name = "Exam Orbit Cycle",
                    targetType = MotionTargetType.LAYER,
                    targetId = notesId,
                    actionType = MotionActionType.ORBIT,
                    durationMs = 5000L,
                    orbitConfig = OrbitConfig(targetId = heroId, targetName = "Walking Graduate", direction = OrbitDirection.CLOCKWISE, radiusPx = 185f, revolutions = 1f, verticalSquash = 0.38f),
                    description = "Exam sheet orbits smoothly around student to symbolize repeating semesters"
                )
            ),
            cameraTrack = CameraTrack(
                keyframes = listOf(
                    CameraKeyframe(timeMs = 0L, zoom = 1.0f, note = "Mid-corridor walk"),
                    CameraKeyframe(timeMs = 5000L, zoom = 1.10f, panY = -20f, note = "Smooth 10% forward dolly")
                ),
                directionSummary = "Smooth 10% forward dolly down the hallway"
            ),
            audioPlan = AudioPlan(
                voiceoverText = "For years, you've known what came next: another semester, another exam, another goal to reach.",
                captionsText = "ANOTHER SEMESTER. ANOTHER EXAM.",
                soundEffects = listOf(AudioCue(timeMs = 900L, type = "SFX", description = "Soft page flip & steady footsteps")),
                musicTrackNote = "Steady rhythmic pulse added beneath Rhodes piano",
                pausesAndSilenceNote = "Clean beat before Scene 3 rain cut"
            ),
            transitionOut = SceneTransition(type = SceneTransitionType.CUT, durationMs = 0L),
            notes = "Scene 2 of 6 (5s-10s): 8-cel smooth walk cycle + orbiting exam sheet"
        )
    }

    private fun buildScene3RainyCrossroads(): Scene {
        val bgId = "grad-s3-bg"
        val carId = "grad-s3-car"
        val heroId = "grad-s3-hero"
        val fxId = "grad-s3-rain"
        val textId = "grad-s3-text"

        val bgLayer = Layer(
            id = bgId,
            name = "City Skyline & Crosswalk",
            category = LayerCategory.BACKGROUND,
            orderIndex = 0,
            semantic = SemanticInfo(whatIsIt = "Rainy evening city towers, glowing windows, asphalt road & zebra crosswalk"),
            sceneShapes = listOf(
                SceneShape(layerId = bgId, shapeType = ShapeType.RECTANGLE, x = 60f, y = 410f, width = 240f, height = 850f, strokeColorHex = "#242C3D", fillColorHex = "#262F42", strokeWidth = 0f, filled = true, semantic = SemanticInfo(whatIsIt = "Left city office tower")),
                SceneShape(layerId = bgId, shapeType = ShapeType.RECTANGLE, x = 350f, y = 520f, width = 200f, height = 740f, strokeColorHex = "#202838", fillColorHex = "#202838", strokeWidth = 0f, filled = true, semantic = SemanticInfo(whatIsIt = "Center mid-rise building")),
                SceneShape(layerId = bgId, shapeType = ShapeType.RECTANGLE, x = 760f, y = 380f, width = 260f, height = 880f, strokeColorHex = "#242C3D", fillColorHex = "#262F42", strokeWidth = 0f, filled = true, semantic = SemanticInfo(whatIsIt = "Right city office tower")),
                SceneShape(layerId = bgId, shapeType = ShapeType.RECTANGLE, x = 110f, y = 490f, width = 48f, height = 62f, strokeColorHex = "#F4B942", fillColorHex = "#F4B942", strokeWidth = 0f, filled = true, semantic = SemanticInfo(whatIsIt = "Warm lit office window")),
                SceneShape(layerId = bgId, shapeType = ShapeType.RECTANGLE, x = 190f, y = 620f, width = 48f, height = 62f, strokeColorHex = "#F4B942", fillColorHex = "#E5A93B", strokeWidth = 0f, filled = true),
                SceneShape(layerId = bgId, shapeType = ShapeType.RECTANGLE, x = 820f, y = 520f, width = 52f, height = 66f, strokeColorHex = "#F4B942", fillColorHex = "#F4B942", strokeWidth = 0f, filled = true, semantic = SemanticInfo(whatIsIt = "Warm lit city window")),
                SceneShape(layerId = bgId, shapeType = ShapeType.RECTANGLE, x = 910f, y = 670f, width = 52f, height = 66f, strokeColorHex = "#F4B942", fillColorHex = "#E5A93B", strokeWidth = 0f, filled = true),
                // Wet asphalt street & zebra crosswalk stripes (borderless)
                SceneShape(layerId = bgId, shapeType = ShapeType.RECTANGLE, x = 0f, y = 1230f, width = W.toFloat(), height = 690f, strokeColorHex = "#1A202C", fillColorHex = "#1A202C", strokeWidth = 0f, filled = true, semantic = SemanticInfo(whatIsIt = "Wet evening asphalt road")),
                SceneShape(layerId = bgId, shapeType = ShapeType.RECTANGLE, x = 120f, y = 1320f, width = 180f, height = 38f, strokeColorHex = "#BAC4CE", fillColorHex = "#BAC4CE", strokeWidth = 0f, filled = true),
                SceneShape(layerId = bgId, shapeType = ShapeType.RECTANGLE, x = 450f, y = 1320f, width = 180f, height = 38f, strokeColorHex = "#BAC4CE", fillColorHex = "#BAC4CE", strokeWidth = 0f, filled = true),
                SceneShape(layerId = bgId, shapeType = ShapeType.RECTANGLE, x = 780f, y = 1320f, width = 180f, height = 38f, strokeColorHex = "#BAC4CE", fillColorHex = "#BAC4CE", strokeWidth = 0f, filled = true)
            )
        )

        val carLayer = Layer(
            id = carId,
            name = "Passing City Car",
            category = LayerCategory.PROPS,
            orderIndex = 1,
            semantic = SemanticInfo(whatIsIt = "Dark blue sedan with glowing headlights driving across road", howShouldItMove = "Glides cleanly left-to-right across road"),
            sceneShapes = listOf(
                SceneShape(layerId = carId, shapeType = ShapeType.RECTANGLE, x = -230f, y = 1245f, width = 220f, height = 74f, strokeColorHex = "#3E5166", fillColorHex = "#3E5166", strokeWidth = 0f, filled = true, semantic = SemanticInfo(whatIsIt = "Sedan body")),
                SceneShape(layerId = carId, shapeType = ShapeType.RECTANGLE, x = -190f, y = 1212f, width = 120f, height = 40f, strokeColorHex = "#4B617A", fillColorHex = "#4B617A", strokeWidth = 0f, filled = true, semantic = SemanticInfo(whatIsIt = "Sedan cabin roof")),
                SceneShape(layerId = carId, shapeType = ShapeType.ELLIPSE, x = -30f, y = 1264f, width = 26f, height = 22f, strokeColorHex = "#FFE38F", fillColorHex = "#FFE38F", strokeWidth = 0f, filled = true, semantic = SemanticInfo(whatIsIt = "Headlight beam")),
                SceneShape(layerId = carId, shapeType = ShapeType.ELLIPSE, x = -205f, y = 1296f, width = 42f, height = 42f, strokeColorHex = "#10141D", fillColorHex = "#121721", strokeWidth = 0f, filled = true),
                SceneShape(layerId = carId, shapeType = ShapeType.ELLIPSE, x = -85f, y = 1296f, width = 42f, height = 42f, strokeColorHex = "#10141D", fillColorHex = "#121721", strokeWidth = 0f, filled = true)
            )
        )

        val heroCel = buildStudentCel(
            layerId = heroId,
            poseName = "Crosswalk Pause",
            cx = 540f,
            cy = 1135f,
            scale = 1.08f,
            holdingPaper = false,
            jointOverrides = mapOf(
                "head" to (0f to 8f),
                "leftHand" to (16f to 16f),
                "rightHand" to (-16f to 24f)
            )
        )
        val heroLayer = Layer(
            id = heroId,
            name = "Uncertain Graduate",
            category = LayerCategory.CHARACTER,
            orderIndex = 2,
            semantic = SemanticInfo(whatIsIt = "Graduate standing still at rainy crosswalk", whatIsItDoing = "Paused amid moving evening traffic"),
            sceneShapes = heroCel.shapes,
            sceneStrokes = heroCel.strokes,
            skeletonPose = heroCel.pose,
            groups = heroCel.groups
        )

        val rainLayer = Layer(
            id = fxId,
            name = "City Rain",
            category = LayerCategory.EFFECTS,
            orderIndex = 3,
            semantic = SemanticInfo(whatIsIt = "Diagonal falling rain streaks")
        )

        val textLayer = Layer(
            id = textId,
            name = "Crossroads Caption",
            category = LayerCategory.TEXT,
            orderIndex = 4,
            sceneShapes = listOf(captionCard(textId, 1445f, 165f)),
            sceneTexts = listOf(
                SceneText(layerId = textId, text = "NO ROADMAP.", x = centeredTextX("NO ROADMAP.", 44f), y = 1510f, fontSizeSp = 44f, colorHex = "#FFFDF9", animationStyle = TextAnimationStyle.FADE_IN),
                SceneText(layerId = textId, text = "NO TIMETABLE.", x = centeredTextX("NO TIMETABLE.", 42f), y = 1575f, fontSizeSp = 42f, colorHex = "#FF9E44", animationStyle = TextAnimationStyle.SLIDE_UP)
            )
        )

        return Scene(
            id = "grad-scene-3",
            orderIndex = 2,
            name = "Rainy Crossroads",
            durationMs = 5000L,
            background = BackgroundSpec(
                type = BackgroundType.GRADIENT,
                primaryColorHex = "#1E2536",
                secondaryColorHex = "#3A465E",
                gradientAngleDeg = 90f,
                environmentDescription = "Rainy dusk city intersection with glowing high-rise windows and crosswalk."
            ),
            layers = listOf(bgLayer, heroLayer, carLayer, rainLayer, textLayer),
            frames = listOf(AnimationFrame(id = "grad-s3-f1", orderIndex = 0, exposureFrames = 24, note = "Paused at crossroads in evening rain")),
            motionTracks = listOf(
                MotionTrack(
                    id = "grad-s3-tr-car",
                    name = "Crosswalk Traffic",
                    targetType = MotionTargetType.LAYER,
                    targetId = carId,
                    actionType = MotionActionType.FOLLOW_PATH,
                    durationMs = 4600L,
                    easing = EasingType.EASE_IN_OUT,
                    pathPoints = listOf(MotionPathPoint(-140f, 1280f), MotionPathPoint(1300f, 1280f)),
                    description = "City car enters from off-left and exits off-right across crosswalk"
                ),
                MotionTrack(
                    id = "grad-s3-tr-rain",
                    name = "Rain Storm Particles",
                    targetType = MotionTargetType.PARTICLES,
                    targetId = fxId,
                    actionType = MotionActionType.PARTICLES,
                    durationMs = 5000L,
                    particleConfig = ParticleConfig(style = "Rain Streaks", originX = 540f, originY = 260f, count = 34, spreadRadius = 540f, colorHex = "#A7CCF0", gravity = 0.9f),
                    description = "Continuous diagonal rain streaks falling across city skyline"
                )
            ),
            cameraTrack = CameraTrack(
                keyframes = listOf(
                    CameraKeyframe(timeMs = 0L, zoom = 1.0f, note = "Wide rainy crosswalk"),
                    CameraKeyframe(timeMs = 5000L, zoom = 1.07f, panY = -16f, note = "Slow quiet push-in")
                ),
                directionSummary = "Slow 7% moody push-in while traffic passes and rain falls"
            ),
            audioPlan = AudioPlan(
                voiceoverText = "Graduation means stepping into a world that doesn't come with a roadmap—no timetable, no grade.",
                captionsText = "NO ROADMAP. NO TIMETABLE.",
                soundEffects = listOf(AudioCue(timeMs = 400L, type = "SFX", description = "Soft rain ambiance and distant car pass")),
                musicTrackNote = "Warm cello pad enters beneath gentle rain",
                pausesAndSilenceNote = "Quiet breath on 'no grade'"
            ),
            transitionOut = SceneTransition(type = SceneTransitionType.CUT, durationMs = 0L),
            notes = "Scene 3 of 6 (10s-15s): Styled rain streaks + off-screen car path + crossroads mood"
        )
    }

    private fun buildScene4LateNightDesk(): Scene {
        val bgId = "grad-s4-bg"
        val heroId = "grad-s4-hero"
        val morphId = "grad-s4-morph"
        val textId = "grad-s4-text"

        val bgLayer = Layer(
            id = bgId,
            name = "Midnight Study Desk",
            category = LayerCategory.BACKGROUND,
            orderIndex = 0,
            semantic = SemanticInfo(whatIsIt = "Dark bedroom desk, starry night window & warm borderless desk lamp glow"),
            sceneShapes = listOf(
                SceneShape(layerId = bgId, shapeType = ShapeType.RECTANGLE, x = 560f, y = 330f, width = 360f, height = 380f, strokeColorHex = "#3E4C59", fillColorHex = "#101726", strokeWidth = 4f, filled = true, semantic = SemanticInfo(whatIsIt = "Night sky window")),
                // Moon + stars in window (borderless)
                SceneShape(layerId = bgId, shapeType = ShapeType.ELLIPSE, x = 795f, y = 390f, width = 56f, height = 56f, strokeColorHex = "#FFE38F", fillColorHex = "#FFE38F", strokeWidth = 0f, filled = true),
                SceneShape(layerId = bgId, shapeType = ShapeType.ELLIPSE, x = 620f, y = 415f, width = 10f, height = 10f, strokeColorHex = "#FFFDF9", fillColorHex = "#FFFDF9", strokeWidth = 0f, filled = true),
                SceneShape(layerId = bgId, shapeType = ShapeType.ELLIPSE, x = 690f, y = 510f, width = 8f, height = 8f, strokeColorHex = "#FFFDF9", fillColorHex = "#FFFDF9", strokeWidth = 0f, filled = true),
                // Soft borderless warm lamp glow
                SceneShape(layerId = bgId, shapeType = ShapeType.ELLIPSE, x = 140f, y = 600f, width = 460f, height = 380f, strokeColorHex = "#F5C065", fillColorHex = "#F5C065", strokeWidth = 0f, opacity = 0.22f, filled = true, semantic = SemanticInfo(whatIsIt = "Warm desk lamp glow halo")),
                // Study desk
                SceneShape(layerId = bgId, shapeType = ShapeType.RECTANGLE, x = 90f, y = 1130f, width = 900f, height = 150f, strokeColorHex = "#4B3429", fillColorHex = "#5C4033", strokeWidth = 0f, filled = true, semantic = SemanticInfo(whatIsIt = "Wooden study desk")),
                // Desk lamp shade
                SceneShape(layerId = bgId, shapeType = ShapeType.POLYGON, x = 230f, y = 960f, width = 90f, height = 70f, strokeColorHex = "#D4A359", fillColorHex = "#E6B566", strokeWidth = 0f, filled = true, semantic = SemanticInfo(whatIsIt = "Warm desk lamp shade"))
            ),
            sceneStrokes = listOf(
                DrawingStroke(layerId = bgId, tool = DrawingTool.LINE, colorHex = "#3E4C59", strokeWidth = 10f, points = listOf(pt(245f, 1130f), pt(275f, 1020f)), semanticLabel = "Desk lamp arm")
            )
        )

        // Rigged base desk graduate, animated via 4 smooth SkeletonPose keyframes
        val baseDeskCel = buildStudentCel(
            layerId = heroId,
            poseName = "Desk Neutral",
            cx = 550f,
            cy = 1015f,
            scale = 1.06f,
            holdingPaper = true
        )
        val headYSteps = listOf(18f, 10f, -2f, -10f)
        val handLiftSteps = listOf(18f, 12f, 2f, -6f)
        val deskPoses = headYSteps.indices.map { i ->
            SkeletonPose(
                name = "Desk Cel ${i + 1}",
                joints = buildPoseJoints(
                    centerX = 550f,
                    centerY = 1015f,
                    scale = 1.06f,
                    overrides = mapOf(
                        "head" to (-6f to headYSteps[i]),
                        "leftHand" to (48f to 16f),
                        "rightHand" to (-52f to handLiftSteps[i])
                    )
                )
            )
        }

        val heroLayer = Layer(
            id = heroId,
            name = "Late-Night Thinker",
            category = LayerCategory.CHARACTER,
            orderIndex = 1,
            semantic = SemanticInfo(whatIsIt = "Student at study desk late at night checking notes", whatIsItDoing = "Slowly lifting gaze toward warm desk spark"),
            sceneShapes = baseDeskCel.shapes,
            sceneStrokes = baseDeskCel.strokes,
            skeletonPose = deskPoses.first(),
            groups = baseDeskCel.groups
        )

        val morphLayer = Layer(
            id = morphId,
            name = "Doubt-to-Spark Morph",
            category = LayerCategory.PROPS,
            orderIndex = 2,
            semantic = SemanticInfo(whatIsIt = "Circle of doubt gently morphing into warm pentagon spark", howShouldItMove = "Smooth ping-pong morph above desk lamp"),
            sceneShapes = listOf(
                SceneShape(layerId = morphId, shapeType = ShapeType.ELLIPSE, x = 260f, y = 740f, width = 100f, height = 100f, strokeColorHex = "#5A7D9A", fillColorHex = "#5A7D9A", strokeWidth = 0f, filled = true)
            )
        )

        val textLayer = Layer(
            id = textId,
            name = "Doubt Caption",
            category = LayerCategory.TEXT,
            orderIndex = 3,
            sceneShapes = listOf(captionCard(textId, 1445f, 165f)),
            sceneTexts = listOf(
                SceneText(layerId = textId, text = "WHAT IF YOU FAIL?", x = centeredTextX("WHAT IF YOU FAIL?", 42f), y = 1510f, fontSizeSp = 42f, colorHex = "#FFFDF9", animationStyle = TextAnimationStyle.TYPEWRITER),
                SceneText(layerId = textId, text = "STILL SEARCHING.", x = centeredTextX("STILL SEARCHING.", 40f), y = 1575f, fontSizeSp = 40f, colorHex = "#FF9E44", animationStyle = TextAnimationStyle.FADE_IN)
            )
        )

        val frames = deskPoses.mapIndexed { idx, pose ->
            AnimationFrame(
                id = "grad-s4-f${idx + 1}",
                orderIndex = idx,
                exposureFrames = 15,
                note = "Contemplative head lift ${idx + 1}/4",
                layerPoses = mapOf(heroId to pose)
            )
        }

        return Scene(
            id = "grad-scene-4",
            orderIndex = 3,
            name = "Late-Night Doubt",
            durationMs = 5000L,
            background = BackgroundSpec(
                type = BackgroundType.GRADIENT,
                primaryColorHex = "#151924",
                secondaryColorHex = "#242B3D",
                gradientAngleDeg = 90f,
                environmentDescription = "Midnight bedroom with glowing starry window and warm study lamp."
            ),
            layers = listOf(bgLayer, heroLayer, morphLayer, textLayer),
            frames = frames,
            motionTracks = listOf(
                MotionTrack(
                    id = "grad-s4-tr-morph",
                    name = "Doubt to Idea Morph",
                    targetType = MotionTargetType.SHAPE,
                    targetId = morphId,
                    actionType = MotionActionType.MORPH,
                    durationMs = 4500L,
                    pingPong = true,
                    easing = EasingType.EASE_IN_OUT,
                    morphConfig = ShapeMorphConfig(
                        sourceShape = ShapeType.ELLIPSE,
                        targetShape = ShapeType.POLYGON,
                        sourceWidth = 96f,
                        sourceHeight = 96f,
                        targetWidth = 114f,
                        targetHeight = 114f,
                        sourceColorHex = "#5A7D9A",
                        targetColorHex = "#FFD15C",
                        centerX = 310f,
                        centerY = 790f
                    ),
                    description = "Doubt sphere smoothly morphs into golden idea star"
                )
            ),
            cameraTrack = CameraTrack(
                keyframes = listOf(
                    CameraKeyframe(timeMs = 0L, zoom = 1.0f, shakeIntensity = 0f, note = "Midnight study desk"),
                    CameraKeyframe(timeMs = 5000L, zoom = 1.08f, panY = -12f, shakeIntensity = 0f, note = "Smooth gentle push-in")
                ),
                directionSummary = "Calm 8% push-in toward lamp and protagonist (zero shake)"
            ),
            audioPlan = AudioPlan(
                voiceoverText = "What if you choose wrong? What if everyone else finds their place while you're still searching?",
                captionsText = "WHAT IF YOU FAIL? STILL SEARCHING.",
                soundEffects = listOf(AudioCue(timeMs = 600L, type = "SFX", description = "Soft paper rustle & quiet night breeze")),
                musicTrackNote = "Warm suspended piano chords resolving softly toward dawn"
            ),
            transitionOut = SceneTransition(type = SceneTransitionType.CUT, durationMs = 0L),
            notes = "Scene 4 of 6 (15s-20s): Smooth calm midnight desk, zero shake, shape morph"
        )
    }

    private fun buildScene5SunriseClarity(): Scene {
        val bgId = "grad-s5-bg"
        val sunId = "grad-s5-sun"
        val planeId = "grad-s5-plane"
        val heroId = "grad-s5-hero"
        val fxId = "grad-s5-fx"
        val textId = "grad-s5-text"

        val sunLayer = Layer(
            id = sunId,
            name = "Rising Dawn Sun",
            category = LayerCategory.PROPS,
            orderIndex = 0,
            semantic = SemanticInfo(whatIsIt = "Warm glowing morning sun rising above city skyline"),
            sceneShapes = listOf(
                SceneShape(layerId = sunId, shapeType = ShapeType.ELLIPSE, x = 640f, y = 740f, width = 220f, height = 220f, strokeColorHex = "#FFE38F", fillColorHex = "#FFE38F", strokeWidth = 0f, opacity = 0.32f, filled = true),
                SceneShape(layerId = sunId, shapeType = ShapeType.ELLIPSE, x = 675f, y = 775f, width = 150f, height = 150f, strokeColorHex = "#FFD15C", fillColorHex = "#FFD15C", strokeWidth = 0f, filled = true)
            )
        )

        val bgLayer = Layer(
            id = bgId,
            name = "Dawn Rooftop Skyline",
            category = LayerCategory.BACKGROUND,
            orderIndex = 1,
            semantic = SemanticInfo(whatIsIt = "City rooftop railing overlooking sunrise skyline"),
            sceneShapes = listOf(
                SceneShape(layerId = bgId, shapeType = ShapeType.RECTANGLE, x = 60f, y = 940f, width = 170f, height = 340f, strokeColorHex = "#343E52", fillColorHex = "#343E52", strokeWidth = 0f, filled = true),
                SceneShape(layerId = bgId, shapeType = ShapeType.RECTANGLE, x = 255f, y = 870f, width = 200f, height = 410f, strokeColorHex = "#2D3648", fillColorHex = "#2D3648", strokeWidth = 0f, filled = true),
                SceneShape(layerId = bgId, shapeType = ShapeType.RECTANGLE, x = 760f, y = 910f, width = 240f, height = 370f, strokeColorHex = "#343E52", fillColorHex = "#343E52", strokeWidth = 0f, filled = true),
                SceneShape(layerId = bgId, shapeType = ShapeType.RECTANGLE, x = 0f, y = 1280f, width = W.toFloat(), height = 640f, strokeColorHex = "#222836", fillColorHex = "#222836", strokeWidth = 0f, filled = true, semantic = SemanticInfo(whatIsIt = "Rooftop terrace floor"))
            ),
            sceneStrokes = listOf(
                DrawingStroke(layerId = bgId, tool = DrawingTool.LINE, colorHex = "#1A202B", strokeWidth = 12f, points = listOf(pt(0f, 1274f), pt(1080f, 1274f)), semanticLabel = "Rooftop guardrail")
            )
        )

        // Folded white paper airplane (clean triangle wings, NOT an awkward arrow)
        val planeLayer = Layer(
            id = planeId,
            name = "Paper Airplane",
            category = LayerCategory.PROPS,
            orderIndex = 2,
            semantic = SemanticInfo(whatIsIt = "Folded white paper airplane soaring upward across sunrise sky", howShouldItMove = "Follow smooth curved flight arc"),
            sceneStrokes = listOf(
                DrawingStroke(
                    layerId = planeId,
                    tool = DrawingTool.PEN,
                    colorHex = "#FFFDF9",
                    strokeWidth = 6f,
                    isFilledLoop = true,
                    fillColorHex = "#FFFDF9",
                    points = listOf(pt(180f, 890f), pt(275f, 868f), pt(202f, 922f), pt(180f, 890f)),
                    semanticLabel = "Paper airplane upper wing"
                ),
                DrawingStroke(
                    layerId = planeId,
                    tool = DrawingTool.LINE,
                    colorHex = "#D8C8B4",
                    strokeWidth = 4f,
                    points = listOf(pt(186f, 904f), pt(275f, 868f)),
                    semanticLabel = "Paper airplane center crease"
                )
            )
        )

        val heroCel = buildStudentCel(
            layerId = heroId,
            poseName = "Sunrise Open Stance",
            cx = 520f,
            cy = 1135f,
            scale = 1.12f,
            facingBack = true,
            holdingPaper = false,
            jointOverrides = mapOf(
                "leftHand" to (-18f to -14f),
                "rightHand" to (18f to -14f)
            )
        )
        val heroLayer = Layer(
            id = heroId,
            name = "Hopeful Graduate",
            category = LayerCategory.CHARACTER,
            orderIndex = 3,
            semantic = SemanticInfo(whatIsIt = "Graduate standing at rooftop rail watching the sunrise"),
            sceneShapes = heroCel.shapes,
            sceneStrokes = heroCel.strokes,
            skeletonPose = heroCel.pose,
            groups = heroCel.groups
        )

        val fxLayer = Layer(
            id = fxId,
            name = "Sunrise Dust",
            category = LayerCategory.EFFECTS,
            orderIndex = 4
        )

        val textLayer = Layer(
            id = textId,
            name = "Clarity Headline",
            category = LayerCategory.TEXT,
            orderIndex = 5,
            sceneShapes = listOf(captionCard(textId, 1445f, 165f)),
            sceneTexts = listOf(
                SceneText(layerId = textId, text = "NOT BEHIND.", x = centeredTextX("NOT BEHIND.", 44f), y = 1510f, fontSizeSp = 44f, colorHex = "#FFFDF9", animationStyle = TextAnimationStyle.BOUNCE_IN),
                SceneText(layerId = textId, text = "STILL UNFOLDING.", x = centeredTextX("STILL UNFOLDING.", 42f), y = 1575f, fontSizeSp = 42f, colorHex = "#FFD15C", animationStyle = TextAnimationStyle.BOUNCE_IN)
            )
        )

        return Scene(
            id = "grad-scene-5",
            orderIndex = 4,
            name = "Sunrise Clarity",
            durationMs = 5000L,
            background = BackgroundSpec(
                type = BackgroundType.GRADIENT,
                primaryColorHex = "#F48C72",
                secondaryColorHex = "#FFE0A3",
                gradientAngleDeg = 90f,
                environmentDescription = "Warm coral-to-gold dawn sky over city rooftop."
            ),
            layers = listOf(sunLayer, bgLayer, planeLayer, heroLayer, fxLayer, textLayer),
            frames = listOf(AnimationFrame(id = "grad-s5-f1", orderIndex = 0, exposureFrames = 24, note = "Looking out over rising sun")),
            motionTracks = listOf(
                MotionTrack(
                    id = "grad-s5-tr-sun",
                    name = "Sunrise Ascent",
                    targetType = MotionTargetType.LAYER,
                    targetId = sunId,
                    actionType = MotionActionType.MOVE,
                    durationMs = 5000L,
                    keyframes = listOf(
                        MotionKeyframe(timeMs = 0L, offsetY = 90f, opacity = 0.85f),
                        MotionKeyframe(timeMs = 5000L, offsetY = -95f, scaleX = 1.12f, scaleY = 1.12f, opacity = 1f)
                    ),
                    description = "Golden sun rises steadily above skyline"
                ),
                MotionTrack(
                    id = "grad-s5-tr-plane",
                    name = "Paper Plane Soar",
                    targetType = MotionTargetType.LAYER,
                    targetId = planeId,
                    actionType = MotionActionType.FOLLOW_PATH,
                    durationMs = 4600L,
                    easing = EasingType.EASE_IN_OUT,
                    orientToPath = true,
                    pathPoints = listOf(
                        MotionPathPoint(220f, 895f),
                        MotionPathPoint(480f, 740f),
                        MotionPathPoint(740f, 595f),
                        MotionPathPoint(960f, 440f)
                    ),
                    description = "Folded paper airplane arcs smoothly upward toward sunrise"
                ),
                MotionTrack(
                    id = "grad-s5-tr-fx",
                    name = "Golden Morning Dust",
                    targetType = MotionTargetType.PARTICLES,
                    targetId = fxId,
                    actionType = MotionActionType.PARTICLES,
                    durationMs = 5000L,
                    particleConfig = ParticleConfig(style = "Sunrise Dust", originX = 540f, originY = 740f, count = 22, spreadRadius = 460f, colorHex = "#FFF3C4", gravity = -0.15f),
                    description = "Warm golden dust motes drifting gently upward"
                )
            ),
            cameraTrack = CameraTrack(
                keyframes = listOf(
                    CameraKeyframe(timeMs = 0L, zoom = 1.10f, note = "Intimate dawn framing"),
                    CameraKeyframe(timeMs = 5000L, zoom = 1.0f, note = "Expansive pull-back reveal of sunrise")
                ),
                directionSummary = "Hopeful smooth pull-back reveal as sun rises and plane soars"
            ),
            audioPlan = AudioPlan(
                voiceoverText = "Remember this: not knowing where life goes doesn't mean you fell behind. That's a life still unfolding.",
                captionsText = "NOT BEHIND. STILL UNFOLDING.",
                soundEffects = listOf(AudioCue(timeMs = 500L, type = "SFX", description = "Warm breeze and uplifting shimmer")),
                musicTrackNote = "Major-key acoustic guitar + warm strings swell"
            ),
            transitionOut = SceneTransition(type = SceneTransitionType.CUT, durationMs = 0L),
            notes = "Scene 5 of 6 (20s-25s): Dawn pull-back + paper airplane + rising sun"
        )
    }

    private fun buildScene6NewChapter(): Scene {
        val bgId = "grad-s6-bg"
        val heroId = "grad-s6-hero"
        val starsId = "grad-s6-stars"
        val fxId = "grad-s6-fx"
        val textId = "grad-s6-text"

        val bgLayer = Layer(
            id = bgId,
            name = "Morning Horizon",
            category = LayerCategory.BACKGROUND,
            orderIndex = 0,
            semantic = SemanticInfo(whatIsIt = "Bright sunlit neighborhood street & trees opening to clear sky"),
            sceneShapes = listOf(
                SceneShape(layerId = bgId, shapeType = ShapeType.RECTANGLE, x = 90f, y = 900f, width = 180f, height = 280f, strokeColorHex = "#E8C59E", fillColorHex = "#E8C59E", strokeWidth = 0f, filled = true),
                SceneShape(layerId = bgId, shapeType = ShapeType.RECTANGLE, x = 810f, y = 900f, width = 180f, height = 280f, strokeColorHex = "#E8C59E", fillColorHex = "#E8C59E", strokeWidth = 0f, filled = true),
                SceneShape(layerId = bgId, shapeType = ShapeType.ELLIPSE, x = 40f, y = 1010f, width = 140f, height = 170f, strokeColorHex = "#3E8E78", fillColorHex = "#3E8E78", strokeWidth = 0f, filled = true),
                SceneShape(layerId = bgId, shapeType = ShapeType.ELLIPSE, x = 900f, y = 1010f, width = 140f, height = 170f, strokeColorHex = "#3E8E78", fillColorHex = "#3E8E78", strokeWidth = 0f, filled = true),
                SceneShape(layerId = bgId, shapeType = ShapeType.RECTANGLE, x = 0f, y = 1180f, width = W.toFloat(), height = 740f, strokeColorHex = "#E5D3BC", fillColorHex = "#E5D3BC", strokeWidth = 0f, filled = true, semantic = SemanticInfo(whatIsIt = "Sunlit morning walkway"))
            )
        )

        // Rigged base wave graduate, animated via 6 smooth SkeletonPose keyframes
        val baseWaveCel = buildStudentCel(
            layerId = heroId,
            poseName = "Wave Neutral",
            cx = 540f,
            cy = 1110f,
            scale = 1.2f,
            holdingPaper = false
        )
        val wavePoses = (0 until 6).map { step ->
            val p = sin((step / 6f) * 2f * PI.toFloat())
            SkeletonPose(
                name = "Wave Cel ${step + 1}",
                joints = buildPoseJoints(
                    centerX = 540f,
                    centerY = 1110f - kotlin.math.abs(p) * 6f,
                    scale = 1.2f,
                    overrides = mapOf(
                        "rightElbow" to (16f + p * 6f to -28f),
                        "rightHand" to (32f + p * 22f to -76f - p * 10f),
                        "leftHand" to (-10f to 16f)
                    )
                )
            )
        }

        val heroLayer = Layer(
            id = heroId,
            name = "Confident Graduate",
            category = LayerCategory.CHARACTER,
            orderIndex = 1,
            semantic = SemanticInfo(whatIsIt = "Smiling graduate stepping forward into new chapter", whatIsItDoing = "Stepping forward with warm confident wave"),
            sceneShapes = baseWaveCel.shapes,
            sceneStrokes = baseWaveCel.strokes,
            skeletonPose = wavePoses.first(),
            groups = baseWaveCel.groups
        )

        val starsLayer = Layer(
            id = starsId,
            name = "Orbiting Future Spark",
            category = LayerCategory.PROPS,
            orderIndex = 2,
            semantic = SemanticInfo(whatIsIt = "Golden compass star orbiting protagonist"),
            sceneShapes = listOf(
                SceneShape(layerId = starsId, shapeType = ShapeType.POLYGON, x = 740f, y = 840f, width = 76f, height = 76f, strokeColorHex = "#FFD15C", fillColorHex = "#FFD15C", strokeWidth = 0f, filled = true)
            )
        )

        val fxLayer = Layer(
            id = fxId,
            name = "Celebration Sparkles",
            category = LayerCategory.EFFECTS,
            orderIndex = 3
        )

        val textLayer = Layer(
            id = textId,
            name = "Finale Message",
            category = LayerCategory.TEXT,
            orderIndex = 4,
            sceneShapes = listOf(
                captionCard(textId, 265f, 165f, bgHex = "#22242C", bgAlpha = 0.84f),
                captionCard(textId, 1455f, 105f, bgHex = "#22242C", bgAlpha = 0.84f)
            ),
            sceneTexts = listOf(
                SceneText(layerId = textId, text = "GRADUATION CHANGES", x = centeredTextX("GRADUATION CHANGES", 38f), y = 330f, fontSizeSp = 38f, colorHex = "#FFFDF9", animationStyle = TextAnimationStyle.FADE_IN),
                SceneText(layerId = textId, text = "YOUR TITLE.", x = centeredTextX("YOUR TITLE.", 44f), y = 395f, fontSizeSp = 44f, colorHex = "#FF8B7B", animationStyle = TextAnimationStyle.BOUNCE_IN),
                SceneText(layerId = textId, text = "YOU DECIDE YOUR IDENTITY.", x = centeredTextX("YOU DECIDE YOUR IDENTITY.", 36f), y = 1520f, fontSizeSp = 36f, colorHex = "#FFD15C", animationStyle = TextAnimationStyle.SLIDE_UP)
            )
        )

        val frames = wavePoses.mapIndexed { idx, pose ->
            AnimationFrame(
                id = "grad-s6-f${idx + 1}",
                orderIndex = idx,
                exposureFrames = 4,
                note = "Warm wave cel ${idx + 1}/6",
                layerPoses = mapOf(heroId to pose)
            )
        }

        return Scene(
            id = "grad-scene-6",
            orderIndex = 5,
            name = "New Chapter Finale",
            durationMs = 5000L,
            background = BackgroundSpec(
                type = BackgroundType.GRADIENT,
                primaryColorHex = "#CFE8FF",
                secondaryColorHex = "#FFF3DE",
                gradientAngleDeg = 90f,
                environmentDescription = "Bright sunlit morning neighborhood opening into a clear blue sky."
            ),
            layers = listOf(bgLayer, heroLayer, starsLayer, fxLayer, textLayer),
            frames = frames,
            motionTracks = listOf(
                MotionTrack(
                    id = "grad-s6-tr-orbit",
                    name = "Future Spark Orbit",
                    targetType = MotionTargetType.LAYER,
                    targetId = starsId,
                    actionType = MotionActionType.ORBIT,
                    durationMs = 4800L,
                    orbitConfig = OrbitConfig(targetId = heroId, targetName = "Confident Graduate", direction = OrbitDirection.COUNTER_CLOCKWISE, radiusPx = 230f, revolutions = 1f, verticalSquash = 0.5f),
                    description = "Golden future spark orbits smoothly around the protagonist"
                ),
                MotionTrack(
                    id = "grad-s6-tr-fx",
                    name = "Finale Sparkle Burst",
                    targetType = MotionTargetType.PARTICLES,
                    targetId = fxId,
                    actionType = MotionActionType.PARTICLES,
                    startTimeMs = 300L,
                    durationMs = 4400L,
                    particleConfig = ParticleConfig(style = "Sunrise Dust", originX = 540f, originY = 900f, count = 22, spreadRadius = 380f, colorHex = "#FFD15C", gravity = -0.1f),
                    description = "Radiant warm sparkles rising as closing message lands"
                )
            ),
            cameraTrack = CameraTrack(
                keyframes = listOf(
                    CameraKeyframe(timeMs = 0L, zoom = 1.0f, note = "Bright full-frame stance"),
                    CameraKeyframe(timeMs = 5000L, zoom = 1.06f, panY = -14f, note = "Warm smooth closing push-in")
                ),
                directionSummary = "Warm 6% closing push-in with rising golden sparkles"
            ),
            audioPlan = AudioPlan(
                voiceoverText = "Graduating changes your title—it doesn't decide your identity. Now you write what comes next.",
                captionsText = "GRADUATION CHANGES YOUR TITLE. YOU DECIDE YOUR IDENTITY.",
                soundEffects = listOf(AudioCue(timeMs = 300L, type = "SFX", description = "Warm uplifting chord chime")),
                musicTrackNote = "Full inspiring acoustic + warm synth resolution",
                pausesAndSilenceNote = "1.0s resonant hold on closing statement"
            ),
            transitionOut = SceneTransition(type = SceneTransitionType.CUT, durationMs = 0L),
            notes = "Scene 6 of 6 (25s-30s): Finale resolution, 6-cel smooth wave, orbit & particles"
        )
    }
}
