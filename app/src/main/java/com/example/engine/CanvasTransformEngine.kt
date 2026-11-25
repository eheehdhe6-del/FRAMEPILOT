package com.example.engine

import com.example.domain.model.DrawingStroke
import com.example.domain.model.SceneShape
import com.example.domain.model.SceneText
import com.example.domain.model.StrokePoint
import java.util.UUID
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class CanvasViewportState(
    val scale: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f
) {
    fun screenToDocument(screenX: Float, screenY: Float): Pair<Float, Float> {
        val safeScale = scale.coerceAtLeast(0.05f)
        return ((screenX - panX) / safeScale) to ((screenY - panY) / safeScale)
    }

    fun documentToScreen(docX: Float, docY: Float): Pair<Float, Float> {
        return (docX * scale + panX) to (docY * scale + panY)
    }
}

object CanvasTransformEngine {
    const val MIN_ZOOM = 0.2f
    const val MAX_ZOOM = 8.0f

    fun fitToViewport(
        docWidth: Int,
        docHeight: Int,
        viewportWidth: Float,
        viewportHeight: Float,
        paddingPx: Float = 48f
    ): CanvasViewportState {
        if (viewportWidth <= 0f || viewportHeight <= 0f || docWidth <= 0 || docHeight <= 0) {
            return CanvasViewportState()
        }
        val availW = (viewportWidth - paddingPx * 2f).coerceAtLeast(32f)
        val availH = (viewportHeight - paddingPx * 2f).coerceAtLeast(32f)
        val scale = min(availW / docWidth.toFloat(), availH / docHeight.toFloat())
            .coerceIn(MIN_ZOOM, MAX_ZOOM)
        val panX = (viewportWidth - docWidth * scale) / 2f
        val panY = (viewportHeight - docHeight * scale) / 2f
        return CanvasViewportState(scale = scale, panX = panX, panY = panY)
    }

    fun applyPinchAndPan(
        current: CanvasViewportState,
        zoomFactor: Float,
        panDeltaX: Float,
        panDeltaY: Float,
        centroidScreenX: Float,
        centroidScreenY: Float
    ): CanvasViewportState {
        val oldScale = current.scale.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val newScale = (oldScale * zoomFactor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val effectiveFactor = newScale / oldScale

        // Keep document point under centroid stationary during zoom, then apply pan delta
        val docX = (centroidScreenX - current.panX) / oldScale
        val docY = (centroidScreenY - current.panY) / oldScale
        val newPanX = centroidScreenX - docX * (oldScale * effectiveFactor) + panDeltaX
        val newPanY = centroidScreenY - docY * (oldScale * effectiveFactor) + panDeltaY

        return CanvasViewportState(
            scale = newScale,
            panX = newPanX,
            panY = newPanY
        )
    }

    fun makeNormalizedPoint(
        docX: Float,
        docY: Float,
        pressure: Float,
        canvasWidth: Int,
        canvasHeight: Int
    ): StrokePoint {
        val normX = if (canvasWidth > 0) docX / canvasWidth.toFloat() else 0f
        val normY = if (canvasHeight > 0) docY / canvasHeight.toFloat() else 0f
        return StrokePoint(
            x = docX,
            y = docY,
            pressure = pressure.coerceIn(0.15f, 1.5f),
            normX = normX,
            normY = normY
        )
    }

    /**
     * Erases only the intersected parts of strokes by splitting polylines at erased points.
     * Single-point strokes inside the eraser radius are removed.
     */
    fun eraseAtPoint(
        strokes: List<DrawingStroke>,
        docX: Float,
        docY: Float,
        eraserRadius: Float
    ): List<DrawingStroke> {
        val result = mutableListOf<DrawingStroke>()
        for (stroke in strokes) {
            if (stroke.points.isEmpty()) continue
            if (stroke.points.size == 1) {
                val p = stroke.points.first()
                if (hypot(p.x - docX, p.y - docY) > eraserRadius + stroke.strokeWidth * 0.5f) {
                    result.add(stroke)
                }
                continue
            }

            val hitRadius = eraserRadius + stroke.strokeWidth * 0.35f
            val segments = mutableListOf<MutableList<StrokePoint>>()
            var currentSegment = mutableListOf<StrokePoint>()

            for (i in stroke.points.indices) {
                val p = stroke.points[i]
                val distToPt = hypot(p.x - docX, p.y - docY)
                val segCrosses = if (i > 0) {
                    distancePointToSegment(
                        docX, docY,
                        stroke.points[i - 1].x, stroke.points[i - 1].y,
                        p.x, p.y
                    ) <= hitRadius
                } else false

                if (distToPt <= hitRadius) {
                    if (currentSegment.isNotEmpty()) {
                        segments.add(currentSegment)
                        currentSegment = mutableListOf()
                    }
                } else {
                    if (segCrosses && currentSegment.isNotEmpty()) {
                        segments.add(currentSegment)
                        currentSegment = mutableListOf()
                    }
                    currentSegment.add(p)
                }
            }
            if (currentSegment.isNotEmpty()) {
                segments.add(currentSegment)
            }

            if (segments.size == 1 && segments[0].size == stroke.points.size) {
                result.add(stroke)
            } else {
                segments.filter { it.size >= 2 || (it.size == 1 && stroke.points.size == 1) }
                    .forEachIndexed { idx, seg ->
                        result.add(
                            stroke.copy(
                                id = if (idx == 0) stroke.id else UUID.randomUUID().toString(),
                                points = seg
                            )
                        )
                    }
            }
        }
        return result
    }

    fun distancePointToSegment(
        px: Float, py: Float,
        x1: Float, y1: Float,
        x2: Float, y2: Float
    ): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val lenSq = dx * dx + dy * dy
        if (lenSq <= 0.0001f) return hypot(px - x1, py - y1)
        val t = (((px - x1) * dx + (py - y1) * dy) / lenSq).coerceIn(0f, 1f)
        val projX = x1 + t * dx
        val projY = y1 + t * dy
        return hypot(px - projX, py - projY)
    }

    fun hitTestShape(shape: SceneShape, docX: Float, docY: Float, tolerance: Float = 24f): Boolean {
        val left = min(shape.x, shape.x + shape.width) - tolerance
        val right = max(shape.x, shape.x + shape.width) + tolerance
        val top = min(shape.y, shape.y + shape.height) - tolerance
        val bottom = max(shape.y, shape.y + shape.height) + tolerance
        return docX in left..right && docY in top..bottom
    }

    fun hitTestText(text: SceneText, docX: Float, docY: Float): Boolean {
        val approxWidth = max(120f, text.text.length * text.fontSizeSp * 0.6f)
        val approxHeight = text.fontSizeSp * 1.4f
        return docX in (text.x - 24f)..(text.x + approxWidth + 24f) &&
            docY in (text.y - approxHeight)..(text.y + 32f)
    }

    fun hitTestStroke(stroke: DrawingStroke, docX: Float, docY: Float, tolerance: Float = 26f): Boolean {
        if (stroke.points.isEmpty()) return false
        if (stroke.points.size == 1) {
            val p = stroke.points.first()
            return hypot(p.x - docX, p.y - docY) <= tolerance + stroke.strokeWidth
        }
        for (i in 1 until stroke.points.size) {
            val a = stroke.points[i - 1]
            val b = stroke.points[i]
            if (distancePointToSegment(docX, docY, a.x, a.y, b.x, b.y) <= tolerance + stroke.strokeWidth * 0.5f) {
                return true
            }
        }
        return false
    }

    fun pointInPolygon(x: Float, y: Float, polygon: List<Pair<Float, Float>>): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val xi = polygon[i].first
            val yi = polygon[i].second
            val xj = polygon[j].first
            val yj = polygon[j].second
            val intersect = ((yi > y) != (yj > y)) &&
                (x < (xj - xi) * (y - yi) / ((yj - yi).let { if (abs(it) < 1e-6f) 1e-6f else it }) + xi)
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }
}
