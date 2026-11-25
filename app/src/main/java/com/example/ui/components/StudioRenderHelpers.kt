package com.example.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.domain.model.AnimationFrame
import com.example.domain.model.BackgroundType
import com.example.domain.model.DrawingStroke
import com.example.domain.model.DrawingTool
import com.example.domain.model.FramePilotProject
import com.example.domain.model.Layer
import com.example.domain.model.MotionActionType
import com.example.domain.model.Scene
import com.example.domain.model.SceneShape
import com.example.domain.model.SceneText
import com.example.domain.model.ShapeType
import com.example.domain.model.SkeletonPose
import com.example.engine.EvaluatedMotion
import com.example.engine.MotionEvaluator
import com.example.engine.SpatialMapGenerator
import com.example.engine.TimelineEngine
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

fun parseHexColor(hex: String?, fallback: Color = Color(0xFF22242C)): Color {
    if (hex.isNullOrBlank()) return fallback
    return runCatching {
        val clean = hex.trim().removePrefix("#")
        val value = when (clean.length) {
            6 -> 0xFF000000L or clean.toLong(16)
            8 -> clean.toLong(16)
            else -> return fallback
        }
        Color(value)
    }.getOrDefault(fallback)
}

fun colorToHex(color: Color): String {
    val r = (color.red * 255f).toInt().coerceIn(0, 255)
    val g = (color.green * 255f).toInt().coerceIn(0, 255)
    val b = (color.blue * 255f).toInt().coerceIn(0, 255)
    return String.format("#%02X%02X%02X", r, g, b)
}

/**
 * Shared document-space renderer used across StudioCanvas, Project thumbnails, and Frame thumbnails.
 */
fun DrawScope.renderSceneDocument(
    project: FramePilotProject,
    scene: Scene,
    activeFrame: AnimationFrame?,
    previousFrame: AnimationFrame? = null,
    nextFrame: AnimationFrame? = null,
    onionSkinEnabled: Boolean = false,
    evaluateMotionAtMs: Long? = null,
    textMeasurer: TextMeasurer? = null,
    showPoseSkeleton: Boolean = true,
    selectedShapeId: String? = null,
    selectedTextId: String? = null,
    selectedStrokeIds: Set<String> = emptySet(),
    activeMotionTrackId: String? = null
) {
    val w = project.canvasWidth.toFloat().coerceAtLeast(1f)
    val h = project.canvasHeight.toFloat().coerceAtLeast(1f)

    // 1. Background
    val primaryBg = parseHexColor(scene.background.primaryColorHex, Color(0xFFFFFDF9))
    val secondaryBg = parseHexColor(scene.background.secondaryColorHex, Color(0xFFE4F1FF))
    when (scene.background.type) {
        BackgroundType.GRADIENT -> {
            val angleRad = scene.background.gradientAngleDeg * PI.toFloat() / 180f
            val dx = cos(angleRad) * w * 0.5f
            val dy = sin(angleRad) * h * 0.5f
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(primaryBg, secondaryBg),
                    start = Offset(w / 2f - dx, h / 2f - dy),
                    end = Offset(w / 2f + dx, h / 2f + dy)
                ),
                topLeft = Offset.Zero,
                size = Size(w, h)
            )
        }
        else -> {
            drawRect(color = primaryBg, topLeft = Offset.Zero, size = Size(w, h))
        }
    }

    // 2. Onion skin (previous = soft coral tint, next = soft sky blue tint)
    if (onionSkinEnabled && evaluateMotionAtMs == null) {
        previousFrame?.let { prev ->
            scene.orderedLayers().filter { it.visible }.forEach { layer ->
                prev.layerDrawings[layer.id].orEmpty().forEach { stroke ->
                    drawStrokeItem(stroke.copy(colorHex = "#FF6B5B", opacity = 0.25f))
                }
            }
        }
        nextFrame?.let { next ->
            scene.orderedLayers().filter { it.visible }.forEach { layer ->
                next.layerDrawings[layer.id].orEmpty().forEach { stroke ->
                    drawStrokeItem(stroke.copy(colorHex = "#3E9BFF", opacity = 0.22f))
                }
            }
        }
    }

    // Precompute layer centers for orbit / attach tracks
    val layerCenters = mutableMapOf<String, Pair<Float, Float>>()
    scene.orderedLayers().forEach { layer ->
        val b = SpatialMapGenerator.computeLayerBounds(layer, project.canvasWidth, project.canvasHeight)
        if (b != null) layerCenters[layer.id] = b.centerX to b.centerY
    }

    // 3. Layers in ascending Z-order
    val orderedLayers = scene.orderedLayers()
    orderedLayers.forEach { layer ->
        if (!layer.visible) return@forEach

        val layerTracks = if (evaluateMotionAtMs != null) {
            scene.motionTracks.filter { it.targetId == layer.id }
        } else emptyList()

        val combinedMotion = if (evaluateMotionAtMs != null && layerTracks.isNotEmpty()) {
            combineMotions(
                tracks = layerTracks.map { tr ->
                    MotionEvaluator.evaluateTrack(
                        track = tr,
                        timeMs = evaluateMotionAtMs,
                        canvasWidth = project.canvasWidth,
                        canvasHeight = project.canvasHeight,
                        targetCenters = layerCenters,
                        selfCenter = layerCenters[layer.id]
                    )
                }
            )
        } else EvaluatedMotion()

        val center = layerCenters[layer.id] ?: (w / 2f to h / 2f)

        // Draw morph override if active
        val morphEval = if (evaluateMotionAtMs != null) {
            layerTracks.firstOrNull { it.actionType == MotionActionType.MORPH }?.let { tr ->
                MotionEvaluator.evaluateTrack(tr, evaluateMotionAtMs, project.canvasWidth, project.canvasHeight)
            }
        } else null

        // Draw particle emitters on this layer if active
        val particleEval = if (evaluateMotionAtMs != null) {
            layerTracks.firstOrNull { it.actionType == MotionActionType.PARTICLES }?.let { tr ->
                MotionEvaluator.evaluateTrack(tr, evaluateMotionAtMs, project.canvasWidth, project.canvasHeight)
            }
        } else null

        withTransform({
            translate(combinedMotion.offsetX, combinedMotion.offsetY)
            rotate(combinedMotion.rotationDeg, pivot = Offset(center.first, center.second))
            scale(combinedMotion.scaleX, combinedMotion.scaleY, pivot = Offset(center.first, center.second))
        }) {
            val layerAlpha = (layer.opacity * combinedMotion.opacity).coerceIn(0f, 1f)

            if (morphEval != null && morphEval.morphPoints.isNotEmpty()) {
                val mPath = Path().apply {
                    morphEval.morphPoints.forEachIndexed { idx, pt ->
                        if (idx == 0) moveTo(pt.first, pt.second) else lineTo(pt.first, pt.second)
                    }
                    close()
                }
                val mColor = parseHexColor(morphEval.morphColorHex, Color(0xFF4EA8FF)).copy(alpha = layerAlpha)
                drawPath(path = mPath, color = mColor)
                drawPath(path = mPath, color = Color(0xFF22242C).copy(alpha = layerAlpha), style = Stroke(width = 6f))
            } else {
                // Scene persistent shapes
                layer.sceneShapes.forEach { shape ->
                    drawShapeItem(shape, layerAlpha, isSelected = shape.id == selectedShapeId)
                }
            }

            // Frame-specific shapes
            activeFrame?.layerShapes?.get(layer.id).orEmpty().forEach { shape ->
                drawShapeItem(shape, layerAlpha, isSelected = shape.id == selectedShapeId)
            }

            // Scene persistent strokes
            layer.sceneStrokes.forEach { stroke ->
                drawStrokeItem(stroke, layerAlpha, isSelected = stroke.id in selectedStrokeIds)
            }

            // Frame-specific strokes
            activeFrame?.layerDrawings?.get(layer.id).orEmpty().forEach { stroke ->
                drawStrokeItem(stroke, layerAlpha, isSelected = stroke.id in selectedStrokeIds)
            }

            // 2D Pose skeleton overlay (for character layers)
            val activePose = activeFrame?.layerPoses?.get(layer.id) ?: layer.skeletonPose
            if (showPoseSkeleton && activePose != null) {
                drawSkeletonPose(activePose, layerAlpha)
            }

            // Scene text elements
            if (textMeasurer != null) {
                layer.sceneTexts.forEach { textItem ->
                    drawTextItem(textItem, textMeasurer, layerAlpha, isSelected = textItem.id == selectedTextId)
                }
            }
        }

        // Particles render in world space around emitter
        if (particleEval != null && particleEval.particles.isNotEmpty()) {
            val trackCfg = layerTracks.firstOrNull { it.actionType == MotionActionType.PARTICLES }?.particleConfig
            val pColor = parseHexColor(trackCfg?.colorHex, Color(0xFFFFD15C))
            particleEval.particles.forEach { p ->
                drawCircle(
                    color = pColor.copy(alpha = p.alpha * layer.opacity),
                    radius = p.radius.coerceAtLeast(2f),
                    center = Offset(p.x, p.y)
                )
            }
        }
    }

    // 4. Optional motion path / orbit guide overlay in editor (when paused or track selected)
    if (evaluateMotionAtMs == null) {
        scene.motionTracks.forEach { track ->
            val isHighlighted = track.id == activeMotionTrackId
            val guideAlpha = if (isHighlighted) 0.85f else 0.35f
            if (track.pathPoints.size >= 2) {
                val path = Path().apply {
                    track.pathPoints.forEachIndexed { i, pt ->
                        if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                    }
                }
                drawPath(
                    path = path,
                    color = Color(0xFF7E6BFF).copy(alpha = guideAlpha),
                    style = Stroke(
                        width = if (isHighlighted) 6f else 4f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f), 0f)
                    )
                )
            }
            track.orbitConfig?.let { orbit ->
                val center = layerCenters[orbit.targetId] ?: (w / 2f to h / 2f)
                drawOval(
                    color = Color(0xFFFF6B5B).copy(alpha = guideAlpha),
                    topLeft = Offset(center.first - orbit.radiusPx, center.second - orbit.radiusPx * orbit.verticalSquash),
                    size = Size(orbit.radiusPx * 2f, orbit.radiusPx * 2f * orbit.verticalSquash),
                    style = Stroke(
                        width = if (isHighlighted) 5f else 3f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                    )
                )
            }
        }
    }
}

private fun combineMotions(tracks: List<EvaluatedMotion>): EvaluatedMotion {
    var ox = 0f
    var oy = 0f
    var rot = 0f
    var sx = 1f
    var sy = 1f
    var op = 1f
    tracks.forEach { m ->
        ox += m.offsetX
        oy += m.offsetY
        rot += m.rotationDeg
        sx *= m.scaleX
        sy *= m.scaleY
        op *= m.opacity
    }
    return EvaluatedMotion(offsetX = ox, offsetY = oy, rotationDeg = rot, scaleX = sx, scaleY = sy, opacity = op)
}

fun DrawScope.drawStrokeItem(
    stroke: DrawingStroke,
    layerAlpha: Float = 1f,
    isSelected: Boolean = false
) {
    if (stroke.points.isEmpty()) return
    val baseColor = parseHexColor(stroke.colorHex).copy(alpha = (stroke.opacity * layerAlpha).coerceIn(0f, 1f))
    val widthMultiplier = when (stroke.tool) {
        DrawingTool.PENCIL -> 0.8f
        DrawingTool.PEN -> 1.0f
        DrawingTool.BRUSH -> 1.55f
        else -> 1.0f
    }
    val effectiveWidth = (stroke.strokeWidth * widthMultiplier).coerceAtLeast(1.5f)

    if (stroke.isFilledLoop && !stroke.fillColorHex.isNullOrBlank() && stroke.points.size >= 3) {
        val fillPath = Path().apply {
            stroke.points.forEachIndexed { idx, p ->
                if (idx == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
        }
        drawPath(
            path = fillPath,
            color = parseHexColor(stroke.fillColorHex).copy(alpha = (stroke.opacity * layerAlpha).coerceIn(0f, 1f))
        )
    }

    if (stroke.points.size == 1) {
        val p = stroke.points.first()
        drawCircle(color = baseColor, radius = effectiveWidth * 0.5f * p.pressure, center = Offset(p.x, p.y))
    } else {
        val path = Path().apply {
            moveTo(stroke.points[0].x, stroke.points[0].y)
            for (i in 1 until stroke.points.size) {
                val prev = stroke.points[i - 1]
                val curr = stroke.points[i]
                val midX = (prev.x + curr.x) / 2f
                val midY = (prev.y + curr.y) / 2f
                quadraticTo(prev.x, prev.y, midX, midY)
            }
            val last = stroke.points.last()
            lineTo(last.x, last.y)
        }
        val avgPressure = stroke.points.map { it.pressure }.average().toFloat().coerceIn(0.6f, 1.4f)
        drawPath(
            path = path,
            color = baseColor,
            style = Stroke(
                width = effectiveWidth * avgPressure,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }

    if (isSelected) {
        val minX = stroke.points.minOf { it.x } - 12f
        val maxX = stroke.points.maxOf { it.x } + 12f
        val minY = stroke.points.minOf { it.y } - 12f
        val maxY = stroke.points.maxOf { it.y } + 12f
        drawRect(
            color = Color(0xFF7E6BFF),
            topLeft = Offset(minX, minY),
            size = Size((maxX - minX).coerceAtLeast(20f), (maxY - minY).coerceAtLeast(20f)),
            style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f))
        )
    }
}

fun DrawScope.drawShapeItem(
    shape: SceneShape,
    layerAlpha: Float = 1f,
    isSelected: Boolean = false
) {
    val alpha = (shape.opacity * layerAlpha).coerceIn(0f, 1f)
    val strokeColor = parseHexColor(shape.strokeColorHex).copy(alpha = alpha)
    val fillColor = shape.fillColorHex?.let { parseHexColor(it).copy(alpha = alpha) }
    val cx = shape.x + shape.width / 2f
    val cy = shape.y + shape.height / 2f

    rotate(shape.rotationDeg, pivot = Offset(cx, cy)) {
        val left = min(shape.x, shape.x + shape.width)
        val top = min(shape.y, shape.y + shape.height)
        val w = kotlin.math.abs(shape.width).coerceAtLeast(4f)
        val h = kotlin.math.abs(shape.height).coerceAtLeast(4f)

        when (shape.shapeType) {
            ShapeType.RECTANGLE -> {
                if (shape.filled && fillColor != null) {
                    drawRoundRect(color = fillColor, topLeft = Offset(left, top), size = Size(w, h), cornerRadius = CornerRadius(14f, 14f))
                }
                drawRoundRect(color = strokeColor, topLeft = Offset(left, top), size = Size(w, h), cornerRadius = CornerRadius(14f, 14f), style = Stroke(width = shape.strokeWidth))
            }
            ShapeType.ELLIPSE -> {
                if (shape.filled && fillColor != null) {
                    drawOval(color = fillColor, topLeft = Offset(left, top), size = Size(w, h))
                }
                drawOval(color = strokeColor, topLeft = Offset(left, top), size = Size(w, h), style = Stroke(width = shape.strokeWidth))
            }
            ShapeType.LINE -> {
                drawLine(
                    color = strokeColor,
                    start = Offset(shape.x, shape.y),
                    end = Offset(shape.x + shape.width, shape.y + shape.height),
                    strokeWidth = shape.strokeWidth,
                    cap = StrokeCap.Round
                )
            }
            ShapeType.ARROW -> {
                val start = Offset(shape.x, shape.y)
                val end = Offset(shape.x + shape.width, shape.y + shape.height)
                drawLine(color = strokeColor, start = start, end = end, strokeWidth = shape.strokeWidth, cap = StrokeCap.Round)
                val angle = atan2(end.y - start.y, end.x - start.x)
                val headLen = (shape.strokeWidth * 3.5f).coerceIn(22f, 54f)
                val p1 = Offset(end.x - headLen * cos(angle - 0.5f), end.y - headLen * sin(angle - 0.5f))
                val p2 = Offset(end.x - headLen * cos(angle + 0.5f), end.y - headLen * sin(angle + 0.5f))
                drawLine(color = strokeColor, start = end, end = p1, strokeWidth = shape.strokeWidth, cap = StrokeCap.Round)
                drawLine(color = strokeColor, start = end, end = p2, strokeWidth = shape.strokeWidth, cap = StrokeCap.Round)
            }
            ShapeType.POLYGON -> {
                val poly = MotionEvaluator.sampleShapePolygon(ShapeType.POLYGON, cx, cy, w, h, 5)
                val path = Path().apply {
                    poly.forEachIndexed { i, pt -> if (i == 0) moveTo(pt.first, pt.second) else lineTo(pt.first, pt.second) }
                    close()
                }
                if (shape.filled && fillColor != null) drawPath(path, fillColor)
                drawPath(path, strokeColor, style = Stroke(width = shape.strokeWidth, join = StrokeJoin.Round))
            }
        }

        if (isSelected) {
            drawRect(
                color = Color(0xFF7E6BFF),
                topLeft = Offset(left - 10f, top - 10f),
                size = Size(w + 20f, h + 20f),
                style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f))
            )
            // Corner resize handles
            listOf(
                Offset(left - 10f, top - 10f),
                Offset(left + w + 10f, top - 10f),
                Offset(left - 10f, top + h + 10f),
                Offset(left + w + 10f, top + h + 10f)
            ).forEach { corner ->
                drawCircle(color = Color.White, radius = 10f, center = corner)
                drawCircle(color = Color(0xFF7E6BFF), radius = 10f, center = corner, style = Stroke(width = 3f))
            }
        }
    }
}

fun DrawScope.drawTextItem(
    item: SceneText,
    textMeasurer: TextMeasurer,
    layerAlpha: Float = 1f,
    isSelected: Boolean = false
) {
    val alpha = (item.opacity * layerAlpha).coerceIn(0f, 1f)
    val style = TextStyle(
        color = parseHexColor(item.colorHex).copy(alpha = alpha),
        fontSize = item.fontSizeSp.sp,
        fontWeight = FontWeight.Bold
    )
    val measured = textMeasurer.measure(item.text, style = style)
    rotate(item.rotationDeg, pivot = Offset(item.x, item.y)) {
        drawText(
            textLayoutResult = measured,
            topLeft = Offset(item.x, item.y - measured.size.height * 0.7f)
        )
        if (isSelected) {
            drawRect(
                color = Color(0xFF7E6BFF),
                topLeft = Offset(item.x - 10f, item.y - measured.size.height * 0.75f - 8f),
                size = Size(measured.size.width + 20f, measured.size.height + 16f),
                style = Stroke(width = 3.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f))
            )
        }
    }
}

private val POSE_BONES = listOf(
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

fun DrawScope.drawSkeletonPose(pose: SkeletonPose, layerAlpha: Float) {
    val boneColor = Color(0xFF7E6BFF).copy(alpha = 0.45f * layerAlpha)
    val jointColor = Color(0xFFFF6B5B).copy(alpha = 0.75f * layerAlpha)
    POSE_BONES.forEach { (a, b) ->
        val pa = pose.joints[a]
        val pb = pose.joints[b]
        if (pa != null && pb != null) {
            drawLine(color = boneColor, start = Offset(pa.x, pa.y), end = Offset(pb.x, pb.y), strokeWidth = 4f)
        }
    }
    pose.joints.values.forEach { pt ->
        drawCircle(color = jointColor, radius = 8f, center = Offset(pt.x, pt.y))
    }
}

fun DrawScope.renderProjectThumbnail(project: FramePilotProject, textMeasurer: TextMeasurer) {
    val scene = project.scenes.sortedBy { it.orderIndex }.firstOrNull() ?: return
    val frame = TimelineEngine.normalizeOrder(scene.frames).firstOrNull()
    val docW = project.canvasWidth.toFloat().coerceAtLeast(1f)
    val docH = project.canvasHeight.toFloat().coerceAtLeast(1f)
    val scale = min(size.width / docW, size.height / docH)
    val dx = (size.width - docW * scale) / 2f
    val dy = (size.height - docH * scale) / 2f

    withTransform({
        translate(dx, dy)
        scale(scale, scale, pivot = Offset.Zero)
    }) {
        renderSceneDocument(
            project = project,
            scene = scene,
            activeFrame = frame,
            textMeasurer = textMeasurer,
            showPoseSkeleton = false
        )
    }
}
