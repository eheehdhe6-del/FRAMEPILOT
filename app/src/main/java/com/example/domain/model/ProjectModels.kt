package com.example.domain.model

import com.squareup.moshi.JsonClass
import java.util.UUID

const val CURRENT_SCHEMA_VERSION = 2

enum class AspectRatioPreset(
    val label: String,
    val width: Int,
    val height: Int,
    val shortCode: String
) {
    REEL_9_16("Reel / Short (9:16)", 1080, 1920, "9:16"),
    YOUTUBE_16_9("YouTube (16:9)", 1920, 1080, "16:9"),
    SQUARE_1_1("Square (1:1)", 1080, 1080, "1:1"),
    PORTRAIT_4_5("Portrait (4:5)", 1080, 1350, "4:5"),
    CUSTOM("Custom", 1080, 1080, "Custom")
}

enum class StudioMode(val label: String, val subtitle: String) {
    SCENE_LAYOUT("Scene Layout", "Compose characters, props & backgrounds"),
    DRAW_FRAMES("Draw Frames", "Draw frame-by-frame sequences"),
    MOTION_STUDIO("Motion Studio", "Animate movement, paths, morphs & camera")
}

enum class DrawingTool(val label: String) {
    PENCIL("Pencil"),
    PEN("Pen"),
    BRUSH("Brush"),
    ERASER("Eraser"),
    FILL("Fill"),
    LINE("Line"),
    RECTANGLE("Rect"),
    ELLIPSE("Ellipse"),
    ARROW("Arrow"),
    TEXT("Text"),
    SELECT("Select"),
    LASSO("Lasso"),
    EYEDROPPER("Picker"),
    HAND("Hand")
}

enum class LayerCategory(val label: String, val colorHex: String) {
    CHARACTER("Character", "#FF6B5B"),
    BACKGROUND("Background", "#4EA8FF"),
    PROPS("Props", "#2EC49A"),
    EFFECTS("Effects", "#FFD15C"),
    TEXT("Text", "#7E6BFF"),
    CUSTOM("Custom", "#8C90A0")
}

enum class BackgroundType(val label: String) {
    SOLID("Solid Color"),
    GRADIENT("Gradient Sky/Backdrop"),
    IMAGE("Reference / Photo"),
    ENVIRONMENT_LAYERS("Layered Environment")
}

enum class ShapeType(val label: String) {
    LINE("Line"),
    RECTANGLE("Rectangle"),
    ELLIPSE("Ellipse"),
    ARROW("Arrow"),
    POLYGON("Polygon")
}

enum class TextAnimationStyle(val label: String) {
    NONE("Static"),
    TYPEWRITER("Typewriter"),
    BOUNCE_IN("Bounce In"),
    SLIDE_UP("Slide Up"),
    PULSE("Pulse"),
    FADE_IN("Fade In")
}

enum class MotionActionType(val label: String, val beginnerPrompt: String) {
    MOVE("Move", "Slide from one spot to another"),
    BOUNCE("Bounce", "Hop or bounce repeatedly"),
    ROTATE("Rotate", "Spin around its center"),
    SCALE("Scale", "Grow larger or shrink down"),
    FADE("Fade", "Fade in or fade out"),
    FOLLOW_PATH("Follow Path", "Travel along a drawn path"),
    ORBIT("Orbit Object", "Fly or circle around another element"),
    ENTER_SCENE("Enter Scene", "Arrive from off-screen"),
    EXIT_SCENE("Exit Scene", "Leave off-screen"),
    SHAKE("Shake", "Vibrate or jiggle for impact"),
    MORPH("Morph Shape", "Transform one shape into another"),
    FLOAT("Float", "Drift gently up and down"),
    ATTACH("Attach", "Stick to a parent character or object"),
    PARTICLES("Particles", "Emit sparkles, dust, or confetti")
}

enum class MotionTargetType(val label: String) {
    LAYER("Layer"),
    SHAPE("Shape"),
    TEXT("Text"),
    CHARACTER("Character"),
    PARTICLES("Particles"),
    CAMERA("Camera")
}

enum class EasingType(val label: String) {
    LINEAR("Linear"),
    EASE_IN("Ease In"),
    EASE_OUT("Ease Out"),
    EASE_IN_OUT("Smooth"),
    BOUNCE("Bounce"),
    SPRING("Spring")
}

enum class OrbitDirection(val label: String) {
    CLOCKWISE("Clockwise"),
    COUNTER_CLOCKWISE("Counter-clockwise")
}

enum class SceneTransitionType(val label: String) {
    CUT("Hard Cut"),
    CROSSFADE("Crossfade"),
    SLIDE_LEFT("Slide Left"),
    ZOOM_THROUGH("Zoom Through")
}

@JsonClass(generateAdapter = true)
data class FramePilotProject(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAtIso: String,
    val updatedAtIso: String,
    val canvasWidth: Int = 1080,
    val canvasHeight: Int = 1920,
    val aspectRatioLabel: String = "9:16",
    val fps: Int = 24,
    val scenes: List<Scene> = emptyList(),
    val creativeBrief: String = "",
    val tutorialCompleted: Boolean = false
) {
    val totalDurationMs: Long
        get() = scenes.sumOf { it.effectiveDurationMs(fps) }
}

@JsonClass(generateAdapter = true)
data class Scene(
    val id: String = UUID.randomUUID().toString(),
    val orderIndex: Int = 0,
    val name: String = "Scene 1",
    val durationMs: Long = 4000L,
    val background: BackgroundSpec = BackgroundSpec(),
    val layers: List<Layer> = emptyList(),
    val frames: List<AnimationFrame> = emptyList(),
    val motionTracks: List<MotionTrack> = emptyList(),
    val cameraTrack: CameraTrack = CameraTrack(),
    val audioPlan: AudioPlan = AudioPlan(),
    val transitionOut: SceneTransition = SceneTransition(),
    val notes: String = ""
) {
    fun orderedLayers(): List<Layer> = layers.sortedBy { it.orderIndex }

    fun orderedFrames(): List<AnimationFrame> = frames.sortedBy { it.orderIndex }

    fun totalExposureTicks(): Int = orderedFrames().sumOf { it.exposureFrames.coerceAtLeast(1) }

    fun framesDurationMs(fps: Int): Long {
        val safeFps = fps.coerceAtLeast(1)
        val ticks = totalExposureTicks()
        return (ticks * 1000L) / safeFps
    }

    fun effectiveDurationMs(fps: Int): Long {
        return maxOf(durationMs.coerceAtLeast(500L), framesDurationMs(fps))
    }
}

@JsonClass(generateAdapter = true)
data class BackgroundSpec(
    val type: BackgroundType = BackgroundType.SOLID,
    val primaryColorHex: String = "#FFFDF9",
    val secondaryColorHex: String = "#E4F1FF",
    val gradientAngleDeg: Float = 90f,
    val imageUri: String? = null,
    val textureNote: String = "",
    val environmentDescription: String = "",
    val elementsDescription: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class SemanticInfo(
    val whatIsIt: String = "",
    val represents: String = "",
    val whatIsItDoing: String = "",
    val whereLocated: String = "",
    val inFrontOf: String = "",
    val behind: String = "",
    val howShouldItMove: String = "",
    val whatShouldHappenNext: String = ""
) {
    fun isUnlabelled(): Boolean =
        whatIsIt.isBlank() && represents.isBlank() && whatIsItDoing.isBlank()

    fun displayLabel(fallback: String = "unlabelled"): String =
        whatIsIt.trim().ifBlank { represents.trim().ifBlank { fallback } }
}

@JsonClass(generateAdapter = true)
data class Layer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: LayerCategory = LayerCategory.CUSTOM,
    val orderIndex: Int = 0,
    val visible: Boolean = true,
    val locked: Boolean = false,
    val opacity: Float = 1f,
    val semantic: SemanticInfo = SemanticInfo(),
    val sceneStrokes: List<DrawingStroke> = emptyList(),
    val sceneShapes: List<SceneShape> = emptyList(),
    val sceneTexts: List<SceneText> = emptyList(),
    val skeletonPose: SkeletonPose? = null
)

@JsonClass(generateAdapter = true)
data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1f,
    val normX: Float = 0f,
    val normY: Float = 0f
)

@JsonClass(generateAdapter = true)
data class DrawingStroke(
    val id: String = UUID.randomUUID().toString(),
    val layerId: String,
    val objectId: String? = null,
    val tool: DrawingTool = DrawingTool.PEN,
    val colorHex: String = "#22242C",
    val strokeWidth: Float = 8f,
    val opacity: Float = 1f,
    val points: List<StrokePoint> = emptyList(),
    val semanticLabel: String? = null,
    val isFilledLoop: Boolean = false,
    val fillColorHex: String? = null
)

@JsonClass(generateAdapter = true)
data class SceneShape(
    val id: String = UUID.randomUUID().toString(),
    val layerId: String,
    val shapeType: ShapeType = ShapeType.ELLIPSE,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val rotationDeg: Float = 0f,
    val strokeColorHex: String = "#22242C",
    val fillColorHex: String? = null,
    val strokeWidth: Float = 6f,
    val opacity: Float = 1f,
    val filled: Boolean = false,
    val semantic: SemanticInfo = SemanticInfo()
)

@JsonClass(generateAdapter = true)
data class SceneText(
    val id: String = UUID.randomUUID().toString(),
    val layerId: String,
    val text: String,
    val x: Float,
    val y: Float,
    val fontSizeSp: Float = 48f,
    val colorHex: String = "#22242C",
    val rotationDeg: Float = 0f,
    val opacity: Float = 1f,
    val animationStyle: TextAnimationStyle = TextAnimationStyle.NONE,
    val semantic: SemanticInfo = SemanticInfo()
)

@JsonClass(generateAdapter = true)
data class JointPoint(
    val x: Float,
    val y: Float
)

@JsonClass(generateAdapter = true)
data class SkeletonPose(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Pose",
    val note: String = "",
    val joints: Map<String, JointPoint> = defaultStickmanJoints(540f, 960f)
)

fun defaultStickmanJoints(centerX: Float, centerY: Float): Map<String, JointPoint> = mapOf(
    "head" to JointPoint(centerX, centerY - 140f),
    "neck" to JointPoint(centerX, centerY - 95f),
    "chest" to JointPoint(centerX, centerY - 50f),
    "hip" to JointPoint(centerX, centerY + 30f),
    "leftShoulder" to JointPoint(centerX - 35f, centerY - 85f),
    "leftElbow" to JointPoint(centerX - 70f, centerY - 35f),
    "leftHand" to JointPoint(centerX - 95f, centerY + 15f),
    "rightShoulder" to JointPoint(centerX + 35f, centerY - 85f),
    "rightElbow" to JointPoint(centerX + 75f, centerY - 40f),
    "rightHand" to JointPoint(centerX + 105f, centerY - 80f),
    "leftKnee" to JointPoint(centerX - 30f, centerY + 105f),
    "leftFoot" to JointPoint(centerX - 45f, centerY + 180f),
    "rightKnee" to JointPoint(centerX + 30f, centerY + 105f),
    "rightFoot" to JointPoint(centerX + 45f, centerY + 180f)
)

@JsonClass(generateAdapter = true)
data class AnimationFrame(
    val id: String = UUID.randomUUID().toString(),
    val orderIndex: Int = 0,
    val exposureFrames: Int = 1,
    val note: String = "",
    val layerDrawings: Map<String, List<DrawingStroke>> = emptyMap(),
    val layerShapes: Map<String, List<SceneShape>> = emptyMap(),
    val layerPoses: Map<String, SkeletonPose> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class OrbitConfig(
    val targetId: String,
    val targetName: String = "",
    val direction: OrbitDirection = OrbitDirection.CLOCKWISE,
    val radiusPx: Float = 180f,
    val revolutions: Float = 1f,
    val verticalSquash: Float = 0.65f
)

@JsonClass(generateAdapter = true)
data class MotionPathPoint(
    val x: Float,
    val y: Float
)

@JsonClass(generateAdapter = true)
data class ShapeMorphConfig(
    val sourceShape: ShapeType = ShapeType.ELLIPSE,
    val targetShape: ShapeType = ShapeType.RECTANGLE,
    val sourceWidth: Float = 160f,
    val sourceHeight: Float = 160f,
    val targetWidth: Float = 180f,
    val targetHeight: Float = 180f,
    val sourceColorHex: String = "#4EA8FF",
    val targetColorHex: String = "#FF6B5B",
    val centerX: Float = 540f,
    val centerY: Float = 960f,
    val interpolationMethod: String = "approximate_polyline_resampling"
)

@JsonClass(generateAdapter = true)
data class ParticleConfig(
    val style: String = "Sparkles",
    val originX: Float = 540f,
    val originY: Float = 960f,
    val count: Int = 18,
    val spreadRadius: Float = 180f,
    val colorHex: String = "#FFD15C",
    val gravity: Float = 0.25f
)

@JsonClass(generateAdapter = true)
data class MotionKeyframe(
    val id: String = UUID.randomUUID().toString(),
    val timeMs: Long = 0L,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val rotationDeg: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val opacity: Float = 1f,
    val easing: EasingType = EasingType.EASE_IN_OUT
)

@JsonClass(generateAdapter = true)
data class MotionTrack(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val targetType: MotionTargetType = MotionTargetType.LAYER,
    val targetId: String,
    val actionType: MotionActionType = MotionActionType.MOVE,
    val startTimeMs: Long = 0L,
    val durationMs: Long = 2000L,
    val repeatCount: Int = 1,
    val pingPong: Boolean = false,
    val easing: EasingType = EasingType.EASE_IN_OUT,
    val orbitConfig: OrbitConfig? = null,
    val pathPoints: List<MotionPathPoint> = emptyList(),
    val orientToPath: Boolean = false,
    val morphConfig: ShapeMorphConfig? = null,
    val particleConfig: ParticleConfig? = null,
    val parentTargetId: String? = null,
    val keyframes: List<MotionKeyframe> = emptyList(),
    val description: String = ""
)

@JsonClass(generateAdapter = true)
data class CameraKeyframe(
    val id: String = UUID.randomUUID().toString(),
    val timeMs: Long = 0L,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val zoom: Float = 1f,
    val rotationDeg: Float = 0f,
    val shakeIntensity: Float = 0f,
    val easing: EasingType = EasingType.EASE_IN_OUT,
    val note: String = ""
)

@JsonClass(generateAdapter = true)
data class CameraTrack(
    val keyframes: List<CameraKeyframe> = emptyList(),
    val directionSummary: String = ""
)

@JsonClass(generateAdapter = true)
data class AudioCue(
    val id: String = UUID.randomUUID().toString(),
    val timeMs: Long = 0L,
    val type: String = "SFX",
    val description: String = ""
)

@JsonClass(generateAdapter = true)
data class AudioPlan(
    val voiceoverText: String = "",
    val captionsText: String = "",
    val soundEffects: List<AudioCue> = emptyList(),
    val musicTrackNote: String = "",
    val pausesAndSilenceNote: String = ""
)

@JsonClass(generateAdapter = true)
data class SceneTransition(
    val type: SceneTransitionType = SceneTransitionType.CUT,
    val durationMs: Long = 350L
)
