package com.example.engine

import com.example.domain.model.CameraKeyframe
import com.example.domain.model.CameraTrack
import com.example.domain.model.EasingType
import com.example.domain.model.MotionActionType
import com.example.domain.model.MotionKeyframe
import com.example.domain.model.MotionPathPoint
import com.example.domain.model.MotionTrack
import com.example.domain.model.OrbitDirection
import com.example.domain.model.ShapeMorphConfig
import com.example.domain.model.ShapeType
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

data class EvaluatedMotion(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val rotationDeg: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val opacity: Float = 1f,
    val absolutePosition: Pair<Float, Float>? = null,
    val morphPoints: List<Pair<Float, Float>> = emptyList(),
    val morphColorHex: String? = null,
    val particles: List<EvaluatedParticle> = emptyList()
)

data class EvaluatedParticle(
    val x: Float,
    val y: Float,
    val radius: Float,
    val alpha: Float,
    val vx: Float = 0f,
    val vy: Float = 0f,
    val isLeaf: Boolean = false
)

data class EvaluatedCamera(
    val panX: Float = 0f,
    val panY: Float = 0f,
    val zoom: Float = 1f,
    val rotationDeg: Float = 0f,
    val shakeX: Float = 0f,
    val shakeY: Float = 0f
)

object MotionEvaluator {

    fun applyEasing(t: Float, easing: EasingType): Float {
        val x = t.coerceIn(0f, 1f)
        return when (easing) {
            EasingType.LINEAR -> x
            EasingType.EASE_IN -> x * x * x
            EasingType.EASE_OUT -> 1f - (1f - x).pow(3)
            EasingType.EASE_IN_OUT ->
                if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
            EasingType.BOUNCE -> bounceEaseOut(x)
            EasingType.SPRING -> {
                val c4 = (2.0 * PI / 3.0).toFloat()
                if (x == 0f) 0f else if (x == 1f) 1f
                else (2.0.pow((-10f * x).toDouble()).toFloat() * sin((x * 10f - 0.75f) * c4) + 1f)
            }
        }
    }

    private fun bounceEaseOut(x: Float): Float {
        val n1 = 7.5625f
        val d1 = 2.75f
        return when {
            x < 1f / d1 -> n1 * x * x
            x < 2f / d1 -> {
                val t = x - 1.5f / d1
                n1 * t * t + 0.75f
            }
            x < 2.5f / d1 -> {
                val t = x - 2.25f / d1
                n1 * t * t + 0.9375f
            }
            else -> {
                val t = x - 2.625f / d1
                n1 * t * t + 0.984375f
            }
        }
    }

    fun evaluateTrack(
        track: MotionTrack,
        timeMs: Long,
        canvasWidth: Int,
        canvasHeight: Int,
        targetCenters: Map<String, Pair<Float, Float>> = emptyMap(),
        selfCenter: Pair<Float, Float>? = null
    ): EvaluatedMotion {
        val dur = track.durationMs.coerceAtLeast(1L)
        val elapsed = (timeMs - track.startTimeMs).coerceAtLeast(0L)
        val totalSpan = dur * track.repeatCount.coerceAtLeast(1)

        val rawProgress = if (timeMs < track.startTimeMs) {
            0f
        } else if (elapsed >= totalSpan) {
            if (track.pingPong && track.repeatCount % 2 == 0) 0f else 1f
        } else {
            val cycle = (elapsed / dur).toInt()
            val localT = (elapsed % dur).toFloat() / dur.toFloat()
            if (track.pingPong && cycle % 2 == 1) 1f - localT else localT
        }

        val eased = applyEasing(rawProgress, track.easing)

        // If custom keyframes exist, interpolate them first
        val kfEval = if (track.keyframes.isNotEmpty()) {
            evaluateKeyframes(track.keyframes, timeMs - track.startTimeMs)
        } else null

        var offsetX = kfEval?.offsetX ?: 0f
        var offsetY = kfEval?.offsetY ?: 0f
        var rotationDeg = kfEval?.rotationDeg ?: 0f
        var scaleX = kfEval?.scaleX ?: 1f
        var scaleY = kfEval?.scaleY ?: 1f
        var opacity = kfEval?.opacity ?: 1f
        var absPos: Pair<Float, Float>? = null

        when (track.actionType) {
            MotionActionType.MOVE -> {
                if (track.pathPoints.size >= 2) {
                    val sample = samplePath(track.pathPoints, eased)
                    val first = track.pathPoints.first()
                    offsetX += sample.x - first.x
                    offsetY += sample.y - first.y
                    if (track.orientToPath) rotationDeg += sample.angleDeg
                } else if (kfEval == null) {
                    offsetX += eased * canvasWidth * 0.25f
                }
            }
            MotionActionType.FOLLOW_PATH -> {
                if (track.pathPoints.size >= 2) {
                    val sample = samplePath(track.pathPoints, eased)
                    val base = selfCenter ?: (track.pathPoints.first().x to track.pathPoints.first().y)
                    offsetX += sample.x - base.first
                    offsetY += sample.y - base.second
                    absPos = sample.x to sample.y
                    if (track.orientToPath) rotationDeg += sample.angleDeg
                }
            }
            MotionActionType.ORBIT -> {
                val orbit = track.orbitConfig
                if (orbit != null) {
                    val anchor = targetCenters[orbit.targetId]
                        ?: (canvasWidth / 2f to canvasHeight / 2f)
                    val dirSign = if (orbit.direction == OrbitDirection.CLOCKWISE) 1f else -1f
                    val angleRad = dirSign * eased * orbit.revolutions * 2f * PI.toFloat() - PI.toFloat() / 2f
                    val targetX = anchor.first + cos(angleRad) * orbit.radiusPx
                    val targetY = anchor.second + sin(angleRad) * orbit.radiusPx * orbit.verticalSquash
                    val base = selfCenter ?: anchor
                    offsetX += targetX - base.first
                    offsetY += targetY - base.second
                    absPos = targetX to targetY
                    if (track.orientToPath) {
                        rotationDeg += (angleRad * 180f / PI.toFloat()) + if (dirSign > 0) 90f else -90f
                    }
                }
            }
            MotionActionType.BOUNCE -> {
                val hops = 2f
                val bounceWave = kotlin.math.abs(sin(rawProgress * hops * PI.toFloat()))
                offsetY -= bounceWave * (canvasHeight * 0.12f)
                val squash = 1f - (1f - bounceWave) * 0.08f
                scaleY *= squash
                scaleX *= (2f - squash)
            }
            MotionActionType.ROTATE -> {
                if (kfEval == null) rotationDeg += eased * 360f
            }
            MotionActionType.SCALE -> {
                if (kfEval == null) {
                    val s = 1f + sin(eased * PI.toFloat()) * 0.45f
                    scaleX *= s
                    scaleY *= s
                }
            }
            MotionActionType.FADE -> {
                if (kfEval == null) {
                    opacity = (1f - eased).coerceIn(0f, 1f)
                }
            }
            MotionActionType.ENTER_SCENE -> {
                offsetX += (1f - eased) * (-canvasWidth * 0.55f)
                opacity *= eased.coerceIn(0.15f, 1f)
            }
            MotionActionType.EXIT_SCENE -> {
                offsetX += eased * (canvasWidth * 0.65f)
                opacity *= (1f - eased * 0.85f)
            }
            MotionActionType.SHAKE -> {
                val freq = 18f
                offsetX += sin(rawProgress * freq * PI.toFloat()) * 16f * (1f - rawProgress * 0.5f)
                offsetY += cos(rawProgress * freq * 1.3f * PI.toFloat()) * 10f * (1f - rawProgress * 0.5f)
            }
            MotionActionType.FLOAT -> {
                offsetY += sin(rawProgress * 2f * PI.toFloat()) * 12f
                rotationDeg += sin(rawProgress * 2f * PI.toFloat()) * 1.2f
            }
            MotionActionType.ATTACH -> {
                val parentCenter = track.parentTargetId?.let { targetCenters[it] }
                if (parentCenter != null && selfCenter != null) {
                    offsetX += (parentCenter.first - selfCenter.first) * 0.15f
                    offsetY += (parentCenter.second - selfCenter.second) * 0.15f
                }
            }
            MotionActionType.MORPH -> {
                val morph = track.morphConfig
                if (morph != null) {
                    val poly = interpolateMorph(morph, eased)
                    val color = lerpHexColor(morph.sourceColorHex, morph.targetColorHex, eased)
                    return EvaluatedMotion(
                        offsetX = offsetX,
                        offsetY = offsetY,
                        rotationDeg = rotationDeg,
                        scaleX = scaleX,
                        scaleY = scaleY,
                        opacity = opacity,
                        morphPoints = poly,
                        morphColorHex = color
                    )
                }
            }
            MotionActionType.PARTICLES -> {
                val cfg = track.particleConfig
                if (cfg != null) {
                    val styleLower = cfg.style.lowercase()
                    val count = cfg.count.coerceIn(1, 70)
                    val list = when {
                        styleLower.contains("rain") -> {
                            (0 until count).map { idx ->
                                val seedX = ((idx * 173 + 37) % 1000) / 1000f
                                val seedPhase = ((idx * 293 + 71) % 1000) / 1000f
                                val life = (rawProgress * 3.8f + seedPhase) % 1f
                                val spanW = (cfg.spreadRadius * 2f).coerceAtLeast(canvasWidth * 0.9f)
                                val spanH = canvasHeight * 0.72f
                                val px = (cfg.originX - spanW * 0.5f) + seedX * spanW - life * 110f
                                val py = (cfg.originY - 120f) + life * spanH
                                val edgeAlpha = sin(life * PI.toFloat()).coerceIn(0f, 1f) * 0.78f
                                EvaluatedParticle(
                                    x = px,
                                    y = py,
                                    radius = 2.6f + (idx % 2) * 0.8f,
                                    alpha = edgeAlpha,
                                    vx = -11f,
                                    vy = 44f
                                )
                            }
                        }
                        styleLower.contains("leaf") || styleLower.contains("leaves") || styleLower.contains("autumn") -> {
                            (0 until count).map { idx ->
                                val seedX = ((idx * 197 + 53) % 1000) / 1000f
                                val seedPhase = ((idx * 317 + 19) % 1000) / 1000f
                                val life = (rawProgress * 1.35f + seedPhase) % 1f
                                val spanW = (cfg.spreadRadius * 2f).coerceAtLeast(canvasWidth * 0.85f)
                                val sway = sin(life * 4f * PI.toFloat() + idx) * 28f
                                val px = (cfg.originX - spanW * 0.5f) + seedX * spanW + sway + life * 90f
                                val py = (cfg.originY - 180f) + life * (canvasHeight * 0.65f)
                                val edgeAlpha = sin(life * PI.toFloat()).coerceIn(0f, 1f) * 0.88f
                                EvaluatedParticle(
                                    x = px,
                                    y = py,
                                    radius = 7f + (idx % 3) * 2f,
                                    alpha = edgeAlpha,
                                    isLeaf = true
                                )
                            }
                        }
                        styleLower.contains("dust") || styleLower.contains("ember") || styleLower.contains("mote") -> {
                            (0 until count).map { idx ->
                                val seedX = ((idx * 211 + 89) % 1000) / 1000f
                                val seedPhase = ((idx * 373 + 41) % 1000) / 1000f
                                val life = (rawProgress * 1.25f + seedPhase) % 1f
                                val spanW = (cfg.spreadRadius * 2f).coerceAtLeast(canvasWidth * 0.8f)
                                val driftX = sin(life * 2f * PI.toFloat() + idx) * 22f
                                val px = (cfg.originX - spanW * 0.5f) + seedX * spanW + driftX
                                val py = (cfg.originY + 220f) - life * 480f
                                val edgeAlpha = sin(life * PI.toFloat()).coerceIn(0f, 1f) * 0.82f
                                EvaluatedParticle(
                                    x = px,
                                    y = py,
                                    radius = 4.5f + (idx % 3) * 1.8f,
                                    alpha = edgeAlpha
                                )
                            }
                        }
                        else -> {
                            (0 until count).map { idx ->
                                val angle = (idx.toFloat() / count.toFloat()) * 2f * PI.toFloat() + idx * 0.37f
                                val dist = eased * cfg.spreadRadius * (0.5f + ((idx % 5) * 0.14f))
                                val px = cfg.originX + cos(angle) * dist
                                val py = cfg.originY + sin(angle) * dist + cfg.gravity * eased * eased * 240f
                                EvaluatedParticle(
                                    x = px,
                                    y = py,
                                    radius = (8.5f - idx % 4 * 1.2f) * (1f - eased * 0.4f),
                                    alpha = sin(eased * PI.toFloat()).coerceIn(0f, 1f).takeIf { rawProgress > 0.02f } ?: 0.85f
                                )
                            }
                        }
                    }
                    return EvaluatedMotion(particles = list)
                }
            }
        }

        return EvaluatedMotion(
            offsetX = offsetX,
            offsetY = offsetY,
            rotationDeg = rotationDeg,
            scaleX = scaleX,
            scaleY = scaleY,
            opacity = opacity,
            absolutePosition = absPos
        )
    }

    fun evaluateKeyframes(keyframes: List<MotionKeyframe>, localTimeMs: Long): EvaluatedMotion {
        val sorted = keyframes.sortedBy { it.timeMs }
        if (sorted.isEmpty()) return EvaluatedMotion()
        if (localTimeMs <= sorted.first().timeMs) {
            val k = sorted.first()
            return EvaluatedMotion(k.offsetX, k.offsetY, k.rotationDeg, k.scaleX, k.scaleY, k.opacity)
        }
        if (localTimeMs >= sorted.last().timeMs) {
            val k = sorted.last()
            return EvaluatedMotion(k.offsetX, k.offsetY, k.rotationDeg, k.scaleX, k.scaleY, k.opacity)
        }
        for (i in 0 until sorted.lastIndex) {
            val a = sorted[i]
            val b = sorted[i + 1]
            if (localTimeMs in a.timeMs..b.timeMs) {
                val span = (b.timeMs - a.timeMs).coerceAtLeast(1L).toFloat()
                val rawT = (localTimeMs - a.timeMs).toFloat() / span
                val t = applyEasing(rawT, b.easing)
                return EvaluatedMotion(
                    offsetX = lerp(a.offsetX, b.offsetX, t),
                    offsetY = lerp(a.offsetY, b.offsetY, t),
                    rotationDeg = lerp(a.rotationDeg, b.rotationDeg, t),
                    scaleX = lerp(a.scaleX, b.scaleX, t),
                    scaleY = lerp(a.scaleY, b.scaleY, t),
                    opacity = lerp(a.opacity, b.opacity, t)
                )
            }
        }
        return EvaluatedMotion()
    }

    fun evaluateCamera(track: CameraTrack, timeMs: Long): EvaluatedCamera {
        val sorted = track.keyframes.sortedBy { it.timeMs }
        if (sorted.isEmpty()) return EvaluatedCamera()
        if (timeMs <= sorted.first().timeMs) return keyframeToCamera(sorted.first(), timeMs)
        if (timeMs >= sorted.last().timeMs) return keyframeToCamera(sorted.last(), timeMs)
        for (i in 0 until sorted.lastIndex) {
            val a = sorted[i]
            val b = sorted[i + 1]
            if (timeMs in a.timeMs..b.timeMs) {
                val span = (b.timeMs - a.timeMs).coerceAtLeast(1L).toFloat()
                val t = applyEasing((timeMs - a.timeMs).toFloat() / span, b.easing)
                val shake = lerp(a.shakeIntensity, b.shakeIntensity, t)
                return EvaluatedCamera(
                    panX = lerp(a.panX, b.panX, t),
                    panY = lerp(a.panY, b.panY, t),
                    zoom = lerp(a.zoom, b.zoom, t).coerceIn(0.4f, 4f),
                    rotationDeg = lerp(a.rotationDeg, b.rotationDeg, t),
                    shakeX = if (shake > 0f) sin(timeMs * 0.09f) * shake * 18f else 0f,
                    shakeY = if (shake > 0f) cos(timeMs * 0.12f) * shake * 14f else 0f
                )
            }
        }
        return EvaluatedCamera()
    }

    private fun keyframeToCamera(k: CameraKeyframe, timeMs: Long): EvaluatedCamera {
        return EvaluatedCamera(
            panX = k.panX,
            panY = k.panY,
            zoom = k.zoom.coerceIn(0.4f, 4f),
            rotationDeg = k.rotationDeg,
            shakeX = if (k.shakeIntensity > 0f) sin(timeMs * 0.09f) * k.shakeIntensity * 18f else 0f,
            shakeY = if (k.shakeIntensity > 0f) cos(timeMs * 0.12f) * k.shakeIntensity * 14f else 0f
        )
    }

    data class PathSample(val x: Float, val y: Float, val angleDeg: Float)

    fun samplePath(points: List<MotionPathPoint>, t: Float): PathSample {
        if (points.isEmpty()) return PathSample(0f, 0f, 0f)
        if (points.size == 1) return PathSample(points[0].x, points[0].y, 0f)
        val clampedT = t.coerceIn(0f, 1f)

        val cumLengths = FloatArray(points.size)
        var totalLength = 0f
        for (i in 1 until points.size) {
            totalLength += hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
            cumLengths[i] = totalLength
        }
        if (totalLength < 0.001f) return PathSample(points[0].x, points[0].y, 0f)

        val targetLen = clampedT * totalLength
        for (i in 1 until points.size) {
            if (targetLen <= cumLengths[i] || i == points.lastIndex) {
                val segLen = (cumLengths[i] - cumLengths[i - 1]).coerceAtLeast(0.001f)
                val localT = ((targetLen - cumLengths[i - 1]) / segLen).coerceIn(0f, 1f)
                val x = lerp(points[i - 1].x, points[i].x, localT)
                val y = lerp(points[i - 1].y, points[i].y, localT)
                val angle = atan2(points[i].y - points[i - 1].y, points[i].x - points[i - 1].x) * 180f / PI.toFloat()
                return PathSample(x, y, angle)
            }
        }
        val last = points.last()
        return PathSample(last.x, last.y, 0f)
    }

    /**
     * Approximate polyline resampling morph between source and target shapes.
     * Both shapes are sampled into [vertexCount] ordered points around their perimeter
     * and interpolated vertex-by-vertex.
     */
    fun interpolateMorph(
        config: ShapeMorphConfig,
        t: Float,
        vertexCount: Int = 32
    ): List<Pair<Float, Float>> {
        val src = sampleShapePolygon(config.sourceShape, config.centerX, config.centerY, config.sourceWidth, config.sourceHeight, vertexCount)
        val dst = sampleShapePolygon(config.targetShape, config.centerX, config.centerY, config.targetWidth, config.targetHeight, vertexCount)
        val clamped = t.coerceIn(0f, 1f)
        return src.zip(dst).map { (a, b) ->
            lerp(a.first, b.first, clamped) to lerp(a.second, b.second, clamped)
        }
    }

    fun sampleShapePolygon(
        shape: ShapeType,
        cx: Float,
        cy: Float,
        width: Float,
        height: Float,
        vertexCount: Int = 32
    ): List<Pair<Float, Float>> {
        val hw = width / 2f
        val hh = height / 2f
        return when (shape) {
            ShapeType.ELLIPSE -> (0 until vertexCount).map { i ->
                val a = (i.toFloat() / vertexCount) * 2f * PI.toFloat() - PI.toFloat() / 2f
                (cx + cos(a) * hw) to (cy + sin(a) * hh)
            }
            ShapeType.RECTANGLE -> {
                val corners = listOf(
                    (cx - hw) to (cy - hh),
                    (cx + hw) to (cy - hh),
                    (cx + hw) to (cy + hh),
                    (cx - hw) to (cy + hh)
                )
                resampleClosedLoop(corners, vertexCount)
            }
            ShapeType.LINE, ShapeType.ARROW -> {
                (0 until vertexCount).map { i ->
                    val frac = i.toFloat() / (vertexCount - 1).coerceAtLeast(1)
                    val x = lerp(cx - hw, cx + hw, frac)
                    val y = cy + sin(frac * PI.toFloat()) * 4f
                    x to y
                }
            }
            ShapeType.POLYGON -> {
                val sides = 5
                val corners = (0 until sides).map { i ->
                    val a = (i.toFloat() / sides) * 2f * PI.toFloat() - PI.toFloat() / 2f
                    (cx + cos(a) * hw) to (cy + sin(a) * hh)
                }
                resampleClosedLoop(corners, vertexCount)
            }
        }
    }

    private fun resampleClosedLoop(corners: List<Pair<Float, Float>>, count: Int): List<Pair<Float, Float>> {
        val pts = corners + corners.first()
        val motionPts = pts.map { MotionPathPoint(it.first, it.second) }
        return (0 until count).map { i ->
            val s = samplePath(motionPts, i.toFloat() / count.toFloat())
            s.x to s.y
        }
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    fun lerpHexColor(hexA: String, hexB: String, t: Float): String {
        val a = parseColorInt(hexA)
        val b = parseColorInt(hexB)
        val clamped = t.coerceIn(0f, 1f)
        val r = (((a shr 16) and 0xFF) + ((((b shr 16) and 0xFF) - ((a shr 16) and 0xFF)) * clamped)).toInt().coerceIn(0, 255)
        val g = (((a shr 8) and 0xFF) + ((((b shr 8) and 0xFF) - ((a shr 8) and 0xFF)) * clamped)).toInt().coerceIn(0, 255)
        val bl = ((a and 0xFF) + (((b and 0xFF) - (a and 0xFF)) * clamped)).toInt().coerceIn(0, 255)
        return String.format("#%02X%02X%02X", r, g, bl)
    }

    private fun parseColorInt(hex: String): Int {
        val clean = hex.trim().removePrefix("#")
        return clean.toLongOrNull(16)?.toInt() ?: 0x22242C
    }
}
