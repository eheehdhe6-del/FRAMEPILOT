package com.example.engine

import com.example.domain.model.StrokePoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class QuickShapeKind(val label: String) {
    LINE("Straight Line"),
    ARC("Smooth Arc"),
    CURVE("Smooth Curve"),
    POLYLINE("Polished Path"),
    CIRCLE("Circle"),
    OVAL("Oval"),
    RECTANGLE("Rectangle"),
    TRIANGLE("Triangle"),
    CLOSED_CURVE("Smooth Contour")
}

data class QuickShapeResult(
    val kind: QuickShapeKind,
    val snappedPoints: List<StrokePoint>
)

object QuickShapeDetector {

    fun detectAndSnap(
        rawPoints: List<StrokePoint>,
        canvasWidth: Int,
        canvasHeight: Int
    ): QuickShapeResult? {
        if (rawPoints.size < 3) return null

        val minX = rawPoints.minOf { it.x }
        val maxX = rawPoints.maxOf { it.x }
        val minY = rawPoints.minOf { it.y }
        val maxY = rawPoints.maxOf { it.y }
        val spanW = maxX - minX
        val spanH = maxY - minY
        val diag = hypot(spanW, spanH)
        if (diag < 24f) return null

        val pathLen = computePathLength(rawPoints)
        if (pathLen < 28f) return null

        val avgPressure = rawPoints.map { it.pressure }.average().toFloat().coerceIn(0.25f, 1.4f)
        val resampled = resampleByArcLength(rawPoints, 64)
        val resampledPairs = resampled.map { it.x to it.y }

        val first = rawPoints.first()
        val last = rawPoints.last()
        val endToEnd = hypot(last.x - first.x, last.y - first.y)
        val closureThreshold = max(48f, diag * 0.34f)
        val isClosedAttempt = endToEnd <= closureThreshold && pathLen >= diag * 1.9f && endToEnd < pathLen * 0.42f

        if (!isClosedAttempt) {
            return detectAndPolishOpenStroke(
                rawPoints = rawPoints,
                resampledPairs = resampledPairs,
                diag = diag,
                pathLen = pathLen,
                endToEnd = endToEnd,
                avgPressure = avgPressure,
                canvasWidth = canvasWidth,
                canvasHeight = canvasHeight
            )
        }

        // Closed shape candidates: Circle/Oval, Rectangle, Triangle
        val hull = convexHull(resampledPairs)
        if (hull.size >= 3) {
            val candidates = mutableListOf<ScoredCandidate>()
            evaluateEllipseCandidate(resampledPairs, diag)?.let { candidates.add(it) }
            evaluateRectangleCandidate(resampledPairs, hull, diag)?.let { candidates.add(it) }
            evaluateTriangleCandidate(resampledPairs, hull, diag)?.let { candidates.add(it) }

            val winner = candidates
                .filter { it.score <= 0.062f }
                .minByOrNull { it.score }

            if (winner != null) {
                val snappedPts = when (winner.kind) {
                    QuickShapeKind.CIRCLE, QuickShapeKind.OVAL -> {
                        winner.boundaryPoints.map { (x, y) ->
                            CanvasTransformEngine.makeNormalizedPoint(x, y, avgPressure, canvasWidth, canvasHeight)
                        }
                    }
                    QuickShapeKind.RECTANGLE, QuickShapeKind.TRIANGLE -> {
                        samplePolygonWithSharpCorners(winner.corners, avgPressure, canvasWidth, canvasHeight)
                    }
                    else -> emptyList()
                }
                if (snappedPts.size >= 3) {
                    return QuickShapeResult(winner.kind, snappedPts)
                }
            }
        }

        // Non-primitive closed contour (cloud, heart, bean, star, asymmetric loop) -> polish smoothly
        return polishClosedContour(
            resampledPairs = resampledPairs,
            diag = diag,
            avgPressure = avgPressure,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight
        )
    }

    private fun detectAndPolishOpenStroke(
        rawPoints: List<StrokePoint>,
        resampledPairs: List<Pair<Float, Float>>,
        diag: Float,
        pathLen: Float,
        endToEnd: Float,
        avgPressure: Float,
        canvasWidth: Int,
        canvasHeight: Int
    ): QuickShapeResult? {
        val first = rawPoints.first().let { it.x to it.y }
        val last = rawPoints.last().let { it.x to it.y }

        // 1. Straight line (open stroke with low perpendicular deviation)
        if (endToEnd >= 24f) {
            val straightness = endToEnd / pathLen.coerceAtLeast(1f)
            val maxPerpDev = resampledPairs.maxOf { p ->
                pointToSegmentDistance(p.first, p.second, first.first, first.second, last.first, last.second)
            }
            if (straightness >= 0.85f && (maxPerpDev / endToEnd) <= 0.14f) {
                val snappedEnds = snapLineEndpoints(first, last)
                val linePts = sampleLinePoints(
                    snappedEnds.first,
                    snappedEnds.second,
                    steps = 14,
                    pressure = avgPressure,
                    canvasWidth = canvasWidth,
                    canvasHeight = canvasHeight
                )
                return QuickShapeResult(QuickShapeKind.LINE, linePts)
            }
        }

        // 2. Sharp-cornered open path (e.g. L-corner, V-checkmark, Z-zigzag, staircase)
        val sharpCornerIndices = detectOpenSharpCorners(resampledPairs, diag)
        if (sharpCornerIndices.isNotEmpty()) {
            val splitIndices = listOf(0) + sharpCornerIndices + listOf(resampledPairs.lastIndex)
            val stitched = polishCorneredSegments(
                pts = resampledPairs,
                splitIndices = splitIndices,
                diag = diag,
                pressure = avgPressure,
                canvasWidth = canvasWidth,
                canvasHeight = canvasHeight
            )
            if (stitched.size >= 3) {
                return QuickShapeResult(QuickShapeKind.POLYLINE, stitched)
            }
        }

        // 3. Single-bend open arc (C-arc, dome, rainbow, eyebrow, parabolic swoosh)
        if (endToEnd >= 20f) {
            evaluateOpenArcCandidate(resampledPairs, diag)?.let { arcPairs ->
                val pts = arcPairs.map { (x, y) ->
                    CanvasTransformEngine.makeNormalizedPoint(x, y, avgPressure, canvasWidth, canvasHeight)
                }
                if (pts.size >= 3) {
                    return QuickShapeResult(QuickShapeKind.ARC, pts)
                }
            }
        }

        // 4. General smooth flowing curve (S-wave, sine wave, curl, ribbon, multi-bend line)
        val polishedCurve = polishOpenFlowingCurve(resampledPairs, diag)
        if (polishedCurve.size < 3) return null
        val outPts = polishedCurve.map { (x, y) ->
            CanvasTransformEngine.makeNormalizedPoint(x, y, avgPressure, canvasWidth, canvasHeight)
        }
        return QuickShapeResult(QuickShapeKind.CURVE, outPts)
    }

    private fun evaluateOpenArcCandidate(
        pts: List<Pair<Float, Float>>,
        diag: Float
    ): List<Pair<Float, Float>>? {
        if (pts.size < 6) return null
        val first = pts.first()
        val last = pts.last()
        val dx = last.first - first.first
        val dy = last.second - first.second
        val chordLen = hypot(dx, dy)
        if (chordLen < 20f) return null

        val nx = -dy / chordLen
        val ny = dx / chordLen

        var maxPos = 0f
        var maxNeg = 0f
        var maxAbsIdx = 0
        var maxAbsDist = 0f
        for (i in 1 until pts.lastIndex) {
            val p = pts[i]
            val signed = (p.first - first.first) * nx + (p.second - first.second) * ny
            if (signed > maxPos) maxPos = signed
            if (-signed > maxNeg) maxNeg = -signed
            val ad = abs(signed)
            if (ad > maxAbsDist) {
                maxAbsDist = ad
                maxAbsIdx = i
            }
        }

        val majorExcursion = max(maxPos, maxNeg)
        val minorExcursion = min(maxPos, maxNeg)
        // Must have clear single-sided curvature without an S-inflection
        if (majorExcursion < 10f || minorExcursion > majorExcursion * 0.14f) return null

        // Check that projections along chord progress reasonably (avoid figure-8 / tangled loops)
        val candidates = mutableListOf<Pair<Float, List<Pair<Float, Float>>>>()

        // Candidate 1: Exact Circular Arc through (first, apex, last)
        val apex = pts[maxAbsIdx]
        fitCircularArc(first, apex, last, diag)?.let { circleArc ->
            val err = bidirectionalPerimeterRms(pts, circleArc, closed = false) / diag.coerceAtLeast(1f)
            candidates.add(err to circleArc)
        }

        // Candidate 2: Optimal Quadratic Bezier Arc (great for tapered swooshes / eyebrows / parabolas)
        fitQuadraticBezierArc(pts)?.let { quadArc ->
            val err = bidirectionalPerimeterRms(pts, quadArc, closed = false) / diag.coerceAtLeast(1f)
            candidates.add(err to quadArc)
        }

        val best = candidates.minByOrNull { it.first } ?: return null
        return if (best.first <= 0.088f) best.second else null
    }

    private fun fitCircularArc(
        a: Pair<Float, Float>,
        m: Pair<Float, Float>,
        b: Pair<Float, Float>,
        diag: Float
    ): List<Pair<Float, Float>>? {
        val d = 2f * (
            a.first * (m.second - b.second) +
                m.first * (b.second - a.second) +
                b.first * (a.second - m.second)
            )
        if (abs(d) < 1e-2f) return null

        val aSq = a.first * a.first + a.second * a.second
        val mSq = m.first * m.first + m.second * m.second
        val bSq = b.first * b.first + b.second * b.second

        val cx = (aSq * (m.second - b.second) + mSq * (b.second - a.second) + bSq * (a.second - m.second)) / d
        val cy = (aSq * (b.first - m.first) + mSq * (a.first - b.first) + bSq * (m.first - a.first)) / d
        val r = hypot(a.first - cx, a.second - cy)
        if (r < 12f || r > diag * 12f) return null

        val thA = atan2(a.second - cy, a.first - cx)
        val thM = atan2(m.second - cy, m.first - cx)
        val thB = atan2(b.second - cy, b.first - cx)

        val dAB = normalizeAngleSigned(thB - thA)
        val dAM = normalizeAngleSigned(thM - thA)

        val sweep = if ((dAB >= 0f && dAM in 0f..dAB) || (dAB < 0f && dAM in dAB..0f)) {
            dAB
        } else {
            if (dAB >= 0f) dAB - (2f * PI.toFloat()) else dAB + (2f * PI.toFloat())
        }

        if (abs(sweep) > 1.65f * PI.toFloat()) return null

        val steps = 38
        return (0..steps).map { i ->
            val t = i.toFloat() / steps.toFloat()
            val th = thA + sweep * t
            (cx + r * cos(th)) to (cy + r * sin(th))
        }
    }

    private fun fitQuadraticBezierArc(pts: List<Pair<Float, Float>>): List<Pair<Float, Float>>? {
        if (pts.size < 4) return null
        val p0 = pts.first()
        val p2 = pts.last()

        val cum = FloatArray(pts.size)
        var total = 0f
        for (i in 1 until pts.size) {
            total += hypot(pts[i].first - pts[i - 1].first, pts[i].second - pts[i - 1].second)
            cum[i] = total
        }
        if (total < 1e-3f) return null

        var sumW2 = 0f
        var sumWX = 0f
        var sumWY = 0f
        for (i in 1 until pts.lastIndex) {
            val t = cum[i] / total
            val omt = 1f - t
            val w = 2f * t * omt
            val rx = pts[i].first - omt * omt * p0.first - t * t * p2.first
            val ry = pts[i].second - omt * omt * p0.second - t * t * p2.second
            sumW2 += w * w
            sumWX += w * rx
            sumWY += w * ry
        }
        if (sumW2 < 1e-4f) return null
        val p1 = (sumWX / sumW2) to (sumWY / sumW2)

        val steps = 36
        val sampled = (0..steps).map { i ->
            val t = i.toFloat() / steps.toFloat()
            val omt = 1f - t
            val x = omt * omt * p0.first + 2f * omt * t * p1.first + t * t * p2.first
            val y = omt * omt * p0.second + 2f * omt * t * p1.second + t * t * p2.second
            x to y
        }
        return resamplePairsByArcLength(sampled, 36)
    }

    private fun polishOpenFlowingCurve(
        pts: List<Pair<Float, Float>>,
        diag: Float
    ): List<Pair<Float, Float>> {
        val smoothed = smoothPairsEndpointPreserving(pts, passes = 3)
        val epsilon = (diag * 0.015f).coerceIn(3.5f, 14f)
        val anchors = rdpSimplifyPairs(smoothed, epsilon)
        if (anchors.size < 2) return smoothed
        if (anchors.size == 2) {
            return (0..14).map { i ->
                val t = i / 14f
                (anchors[0].first + (anchors[1].first - anchors[0].first) * t) to
                    (anchors[0].second + (anchors[1].second - anchors[0].second) * t)
            }
        }
        val spline = centripetalCatmullRomOpen(anchors, samplesPerSpan = 10)
        return resamplePairsByArcLength(spline, targetCount = 48)
    }

    private fun polishClosedContour(
        resampledPairs: List<Pair<Float, Float>>,
        diag: Float,
        avgPressure: Float,
        canvasWidth: Int,
        canvasHeight: Int
    ): QuickShapeResult? {
        val baseRing = if (resampledPairs.size >= 2 &&
            hypot(resampledPairs.last().first - resampledPairs.first().first, resampledPairs.last().second - resampledPairs.first().second) < 4f
        ) {
            resampledPairs.dropLast(1)
        } else {
            resampledPairs
        }
        if (baseRing.size < 6) return null

        val sharpCorners = detectClosedSharpCorners(baseRing, diag)
        val finalPairs = if (sharpCorners.size >= 2) {
            // Cornered closed contour (e.g. star, heart, jagged crown): polish spans between corners
            val orderedCorners = sharpCorners.sorted()
            val stitched = mutableListOf<Pair<Float, Float>>()
            for (cIdx in orderedCorners.indices) {
                val startI = orderedCorners[cIdx]
                val endI = orderedCorners[(cIdx + 1) % orderedCorners.size]
                val span = mutableListOf<Pair<Float, Float>>()
                var cur = startI
                while (true) {
                    span.add(baseRing[cur])
                    if (cur == endI) break
                    cur = (cur + 1) % baseRing.size
                }
                val polishedSpan = polishSpanKeepingEndpoints(span, diag)
                if (stitched.isEmpty()) {
                    stitched.addAll(polishedSpan)
                } else {
                    stitched.addAll(polishedSpan.drop(1))
                }
            }
            stitched
        } else {
            // Smooth closed contour (cloud, bean, organic ring): periodic smoothing + centripetal Catmull-Rom
            val smoothed = smoothPairsClosed(baseRing, passes = 3)
            val epsilon = (diag * 0.016f).coerceIn(4f, 14f)
            val anchors = rdpSimplifyClosedPairs(smoothed, epsilon)
            if (anchors.size < 3) return null
            val spline = centripetalCatmullRomClosed(anchors, samplesPerSpan = 8)
            val resampled = resamplePairsByArcLength(spline, targetCount = 64)
            resampled + resampled.first()
        }

        val pts = finalPairs.map { (x, y) ->
            CanvasTransformEngine.makeNormalizedPoint(x, y, avgPressure, canvasWidth, canvasHeight)
        }
        return if (pts.size >= 4) QuickShapeResult(QuickShapeKind.CLOSED_CURVE, pts) else null
    }

    private fun detectOpenSharpCorners(
        pts: List<Pair<Float, Float>>,
        diag: Float
    ): List<Int> {
        if (pts.size < 18) return emptyList()
        val sm = smoothPairsEndpointPreserving(pts, passes = 2)
        val k = 6
        val minLeg = max(18f, diag * 0.06f)

        val rawCandidates = mutableListOf<Pair<Int, Float>>() // index -> turningAngleDeg
        for (i in k..(sm.lastIndex - k)) {
            val v1x = sm[i].first - sm[i - k].first
            val v1y = sm[i].second - sm[i - k].second
            val v2x = sm[i + k].first - sm[i].first
            val v2y = sm[i + k].second - sm[i].second
            val m1 = hypot(v1x, v1y)
            val m2 = hypot(v2x, v2y)
            if (m1 < minLeg || m2 < minLeg) continue
            val cosT = ((v1x * v2x + v1y * v2y) / (m1 * m2)).coerceIn(-1f, 1f)
            val turnDeg = (acos(cosT.toDouble()) * 180.0 / PI).toFloat()
            if (turnDeg >= 55f) {
                rawCandidates.add(i to turnDeg)
            }
        }
        if (rawCandidates.isEmpty()) return emptyList()

        // Non-maximum suppression within +/- k
        val suppressed = mutableListOf<Int>()
        var idx = 0
        while (idx < rawCandidates.size) {
            var bestIdx = rawCandidates[idx].first
            var bestTurn = rawCandidates[idx].second
            var j = idx + 1
            while (j < rawCandidates.size && rawCandidates[j].first - bestIdx <= k + 1) {
                if (rawCandidates[j].second > bestTurn) {
                    bestTurn = rawCandidates[j].second
                    bestIdx = rawCandidates[j].first
                }
                j++
            }
            // Refine apex on smoothed curve within +/- 2 samples
            val lo = (bestIdx - 2).coerceAtLeast(k)
            val hi = (bestIdx + 2).coerceAtMost(sm.lastIndex - k)
            var apexIdx = bestIdx
            var maxApexDist = -1f
            val leftRef = sm[(bestIdx - k).coerceAtLeast(0)]
            val rightRef = sm[(bestIdx + k).coerceAtMost(sm.lastIndex)]
            for (c in lo..hi) {
                val d = pointToSegmentDistance(sm[c].first, sm[c].second, leftRef.first, leftRef.second, rightRef.first, rightRef.second)
                if (d > maxApexDist) {
                    maxApexDist = d
                    apexIdx = c
                }
            }

            if (isTrueSharpCorner(sm, apexIdx, k)) {
                suppressed.add(apexIdx)
            }
            idx = j
        }

        // Enforce minimum spacing between corners and endpoints
        val filtered = mutableListOf<Int>()
        for (c in suppressed) {
            val prevIdx = filtered.lastOrNull() ?: 0
            val legLen = hypot(pts[c].first - pts[prevIdx].first, pts[c].second - pts[prevIdx].second)
            val remLen = hypot(pts.last().first - pts[c].first, pts.last().second - pts[c].second)
            if (legLen >= max(20f, diag * 0.08f) && remLen >= max(20f, diag * 0.08f)) {
                filtered.add(c)
            }
        }
        return filtered
    }

    private fun detectClosedSharpCorners(
        ring: List<Pair<Float, Float>>,
        diag: Float
    ): List<Int> {
        val n = ring.size
        if (n < 20) return emptyList()
        val sm = smoothPairsClosed(ring, passes = 2)
        val k = 5
        val minLeg = max(16f, diag * 0.055f)
        val candidates = mutableListOf<Pair<Int, Float>>()
        for (i in 0 until n) {
            val prev = sm[(i - k + n) % n]
            val curr = sm[i]
            val next = sm[(i + k) % n]
            val v1x = curr.first - prev.first
            val v1y = curr.second - prev.second
            val v2x = next.first - curr.first
            val v2y = next.second - curr.second
            val m1 = hypot(v1x, v1y)
            val m2 = hypot(v2x, v2y)
            if (m1 < minLeg || m2 < minLeg) continue
            val cosT = ((v1x * v2x + v1y * v2y) / (m1 * m2)).coerceIn(-1f, 1f)
            val turnDeg = (acos(cosT.toDouble()) * 180.0 / PI).toFloat()
            if (turnDeg >= 58f) {
                candidates.add(i to turnDeg)
            }
        }
        if (candidates.isEmpty()) return emptyList()

        val result = mutableListOf<Int>()
        for ((idx, turn) in candidates) {
            val isLocalMax = candidates.none { (otherIdx, otherTurn) ->
                val ringDist = min(abs(otherIdx - idx), n - abs(otherIdx - idx))
                ringDist in 1..k && otherTurn > turn
            }
            if (isLocalMax) {
                val windowPts = (-k..k).map { offset -> sm[(idx + offset + n) % n] }
                if (isTrueSharpCorner(windowPts, k, k)) {
                    result.add(idx)
                }
            }
        }
        return result
    }

    private fun isTrueSharpCorner(
        smPts: List<Pair<Float, Float>>,
        apexIdx: Int,
        k: Int
    ): Boolean {
        val start = (apexIdx - k).coerceAtLeast(0)
        val end = (apexIdx + k).coerceAtMost(smPts.lastIndex)
        if (apexIdx <= start + 2 || apexIdx >= end - 2) return false

        val a = smPts[start]
        val aFlank = smPts[apexIdx - 2]
        val bFlank = smPts[apexIdx + 2]
        val b = smPts[end]

        // Intersect left flank (a -> aFlank) and right flank (b -> bFlank) to recover sharp corner vertex
        val cornerVertex = intersectLines(a, aFlank, b, bFlank)
            ?.takeIf { v -> hypot(v.first - smPts[apexIdx].first, v.second - smPts[apexIdx].second) < hypot(b.first - a.first, b.second - a.second) * 0.35f }
            ?: smPts[apexIdx]

        // Smooth quadratic Bezier fitting through the same midpoint apex
        val m = smPts[apexIdx]
        val ctrl = (2f * m.first - 0.5f * (a.first + b.first)) to (2f * m.second - 0.5f * (a.second + b.second))

        var errCornerSq = 0.0
        var errQuadSq = 0.0
        val count = end - start + 1
        for (i in start..end) {
            val p = smPts[i]
            val dCorner = min(
                pointToSegmentDistance(p.first, p.second, a.first, a.second, cornerVertex.first, cornerVertex.second),
                pointToSegmentDistance(p.first, p.second, cornerVertex.first, cornerVertex.second, b.first, b.second)
            )
            errCornerSq += dCorner * dCorner

            val t = (i - start).toFloat() / (end - start).toFloat()
            val omt = 1f - t
            val qx = omt * omt * a.first + 2f * omt * t * ctrl.first + t * t * b.first
            val qy = omt * omt * a.second + 2f * omt * t * ctrl.second + t * t * b.second
            val dQuad = hypot(p.first - qx, p.second - qy)
            errQuadSq += dQuad * dQuad
        }
        val rmsCorner = sqrt(errCornerSq / count).toFloat()
        val rmsQuad = sqrt(errQuadSq / count).toFloat()
        return rmsCorner < rmsQuad * 0.72f
    }

    private fun intersectLines(
        p1: Pair<Float, Float>,
        p2: Pair<Float, Float>,
        p3: Pair<Float, Float>,
        p4: Pair<Float, Float>
    ): Pair<Float, Float>? {
        val dx1 = p2.first - p1.first
        val dy1 = p2.second - p1.second
        val dx2 = p4.first - p3.first
        val dy2 = p4.second - p3.second
        val denom = dx1 * dy2 - dy1 * dx2
        if (abs(denom) < 1e-3f) return null
        val t = ((p3.first - p1.first) * dy2 - (p3.second - p1.second) * dx2) / denom
        return (p1.first + t * dx1) to (p1.second + t * dy1)
    }

    private fun polishCorneredSegments(
        pts: List<Pair<Float, Float>>,
        splitIndices: List<Int>,
        diag: Float,
        pressure: Float,
        canvasWidth: Int,
        canvasHeight: Int
    ): List<StrokePoint> {
        val combinedPairs = mutableListOf<Pair<Float, Float>>()
        for (s in 0 until splitIndices.lastIndex) {
            val i0 = splitIndices[s]
            val i1 = splitIndices[s + 1]
            if (i1 <= i0) continue
            val seg = pts.subList(i0, i1 + 1)
            val polishedSeg = polishSpanKeepingEndpoints(seg, diag)
            if (combinedPairs.isEmpty()) {
                combinedPairs.addAll(polishedSeg)
            } else {
                combinedPairs.addAll(polishedSeg.drop(1))
            }
        }
        return combinedPairs.map { (x, y) ->
            CanvasTransformEngine.makeNormalizedPoint(x, y, pressure, canvasWidth, canvasHeight)
        }
    }

    private fun polishSpanKeepingEndpoints(
        seg: List<Pair<Float, Float>>,
        diag: Float
    ): List<Pair<Float, Float>> {
        if (seg.size <= 2) return seg
        val a = seg.first()
        val b = seg.last()
        val chord = hypot(b.first - a.first, b.second - a.second)
        var segLen = 0f
        for (i in 1 until seg.size) {
            segLen += hypot(seg[i].first - seg[i - 1].first, seg[i].second - seg[i - 1].second)
        }
        val maxDev = seg.maxOf { p -> pointToSegmentDistance(p.first, p.second, a.first, a.second, b.first, b.second) }
        val straightness = if (segLen > 1e-3f) chord / segLen else 1f

        return if (chord >= 10f && straightness >= 0.88f && maxDev / chord <= 0.13f) {
            // Sharp corner bracket fractions so midpoint quadratic Bezier keeps crisp corners
            val ts = floatArrayOf(0f, 0.02f, 0.25f, 0.5f, 0.75f, 0.98f, 1f)
            ts.map { t ->
                (a.first + (b.first - a.first) * t) to (a.second + (b.second - a.second) * t)
            }
        } else {
            val curved = polishOpenFlowingCurve(seg, diag)
            if (curved.size >= 3) {
                val pStart = curved.first()
                val pNext = curved[1]
                val pBeforeEnd = curved[curved.size - 2]
                val pEnd = curved.last()
                val bracketStart = (pStart.first + (pNext.first - pStart.first) * 0.06f) to
                    (pStart.second + (pNext.second - pStart.second) * 0.06f)
                val bracketEnd = (pEnd.first + (pBeforeEnd.first - pEnd.first) * 0.06f) to
                    (pEnd.second + (pBeforeEnd.second - pEnd.second) * 0.06f)
                listOf(pStart, bracketStart) + curved.subList(1, curved.lastIndex) + listOf(bracketEnd, pEnd)
            } else {
                curved
            }
        }
    }

    private fun smoothPairsEndpointPreserving(
        pts: List<Pair<Float, Float>>,
        passes: Int
    ): List<Pair<Float, Float>> {
        if (pts.size < 3 || passes <= 0) return pts
        var current = pts
        repeat(passes) {
            val next = MutableList(current.size) { idx -> current[idx] }
            for (i in 1 until current.lastIndex) {
                val x = 0.2f * current[i - 1].first + 0.6f * current[i].first + 0.2f * current[i + 1].first
                val y = 0.2f * current[i - 1].second + 0.6f * current[i].second + 0.2f * current[i + 1].second
                next[i] = x to y
            }
            current = next
        }
        return current
    }

    private fun smoothPairsClosed(
        ring: List<Pair<Float, Float>>,
        passes: Int
    ): List<Pair<Float, Float>> {
        if (ring.size < 4 || passes <= 0) return ring
        val n = ring.size
        var current = ring
        repeat(passes) {
            current = List(n) { i ->
                val prev = current[(i - 1 + n) % n]
                val curr = current[i]
                val next = current[(i + 1) % n]
                (0.2f * prev.first + 0.6f * curr.first + 0.2f * next.first) to
                    (0.2f * prev.second + 0.6f * curr.second + 0.2f * next.second)
            }
        }
        return current
    }

    private fun rdpSimplifyPairs(
        pts: List<Pair<Float, Float>>,
        epsilon: Float
    ): List<Pair<Float, Float>> {
        if (pts.size <= 2) return pts
        val start = pts.first()
        val end = pts.last()
        var maxDist = -1f
        var index = 0
        for (i in 1 until pts.lastIndex) {
            val d = pointToSegmentDistance(pts[i].first, pts[i].second, start.first, start.second, end.first, end.second)
            if (d > maxDist) {
                maxDist = d
                index = i
            }
        }
        return if (maxDist > epsilon && index > 0) {
            val left = rdpSimplifyPairs(pts.subList(0, index + 1), epsilon)
            val right = rdpSimplifyPairs(pts.subList(index, pts.size), epsilon)
            left.dropLast(1) + right
        } else {
            listOf(start, end)
        }
    }

    private fun rdpSimplifyClosedPairs(
        ring: List<Pair<Float, Float>>,
        epsilon: Float
    ): List<Pair<Float, Float>> {
        if (ring.size <= 4) return ring
        // Find two farthest opposite anchor points on the ring to split cleanly
        var maxD = -1f
        var splitA = 0
        var splitB = ring.size / 2
        for (i in ring.indices) {
            val j = (i + ring.size / 2) % ring.size
            val d = hypot(ring[j].first - ring[i].first, ring[j].second - ring[i].second)
            if (d > maxD) {
                maxD = d
                splitA = i
                splitB = j
            }
        }
        val arc1 = mutableListOf<Pair<Float, Float>>()
        var cur = splitA
        while (true) {
            arc1.add(ring[cur])
            if (cur == splitB) break
            cur = (cur + 1) % ring.size
        }
        val arc2 = mutableListOf<Pair<Float, Float>>()
        cur = splitB
        while (true) {
            arc2.add(ring[cur])
            if (cur == splitA) break
            cur = (cur + 1) % ring.size
        }
        val s1 = rdpSimplifyPairs(arc1, epsilon)
        val s2 = rdpSimplifyPairs(arc2, epsilon)
        return s1.dropLast(1) + s2.dropLast(1)
    }

    private fun centripetalCatmullRomOpen(
        anchors: List<Pair<Float, Float>>,
        samplesPerSpan: Int
    ): List<Pair<Float, Float>> {
        if (anchors.size < 2) return anchors
        val firstPhantom = (2f * anchors[0].first - anchors[1].first) to (2f * anchors[0].second - anchors[1].second)
        val lastPhantom = (2f * anchors.last().first - anchors[anchors.size - 2].first) to
            (2f * anchors.last().second - anchors[anchors.size - 2].second)
        val extended = listOf(firstPhantom) + anchors + listOf(lastPhantom)
        val out = mutableListOf<Pair<Float, Float>>()
        for (i in 0 until anchors.lastIndex) {
            val p0 = extended[i]
            val p1 = extended[i + 1]
            val p2 = extended[i + 2]
            val p3 = extended[i + 3]
            val spanPts = evalCentripetalSegment(p0, p1, p2, p3, samplesPerSpan)
            if (out.isEmpty()) out.addAll(spanPts) else out.addAll(spanPts.drop(1))
        }
        return out
    }

    private fun centripetalCatmullRomClosed(
        anchors: List<Pair<Float, Float>>,
        samplesPerSpan: Int
    ): List<Pair<Float, Float>> {
        val m = anchors.size
        if (m < 3) return anchors
        val out = mutableListOf<Pair<Float, Float>>()
        for (i in 0 until m) {
            val p0 = anchors[(i - 1 + m) % m]
            val p1 = anchors[i]
            val p2 = anchors[(i + 1) % m]
            val p3 = anchors[(i + 2) % m]
            val spanPts = evalCentripetalSegment(p0, p1, p2, p3, samplesPerSpan)
            if (out.isEmpty()) out.addAll(spanPts) else out.addAll(spanPts.drop(1))
        }
        return out
    }

    private fun evalCentripetalSegment(
        p0: Pair<Float, Float>,
        p1: Pair<Float, Float>,
        p2: Pair<Float, Float>,
        p3: Pair<Float, Float>,
        samples: Int
    ): List<Pair<Float, Float>> {
        fun knotInterval(a: Pair<Float, Float>, b: Pair<Float, Float>): Float {
            val d = hypot(b.first - a.first, b.second - a.second).coerceAtLeast(1e-3f)
            return d.toDouble().pow(0.5).toFloat().coerceAtLeast(1e-3f)
        }
        val t0 = 0f
        val t1 = t0 + knotInterval(p0, p1)
        val t2 = t1 + knotInterval(p1, p2)
        val t3 = t2 + knotInterval(p2, p3)

        fun lerpPt(a: Pair<Float, Float>, b: Pair<Float, Float>, ta: Float, tb: Float, t: Float): Pair<Float, Float> {
            val denom = (tb - ta).coerceAtLeast(1e-4f)
            val w1 = (tb - t) / denom
            val w2 = (t - ta) / denom
            return (w1 * a.first + w2 * b.first) to (w1 * a.second + w2 * b.second)
        }

        return (0..samples).map { s ->
            val t = t1 + (t2 - t1) * (s.toFloat() / samples.toFloat())
            val a1 = lerpPt(p0, p1, t0, t1, t)
            val a2 = lerpPt(p1, p2, t1, t2, t)
            val a3 = lerpPt(p2, p3, t2, t3, t)
            val b1 = lerpPt(a1, a2, t0, t2, t)
            val b2 = lerpPt(a2, a3, t1, t3, t)
            lerpPt(b1, b2, t1, t2, t)
        }
    }

    private fun resamplePairsByArcLength(
        pts: List<Pair<Float, Float>>,
        targetCount: Int
    ): List<Pair<Float, Float>> {
        if (pts.size <= 1 || targetCount <= 1) return pts
        val cum = FloatArray(pts.size)
        var total = 0f
        for (i in 1 until pts.size) {
            total += hypot(pts[i].first - pts[i - 1].first, pts[i].second - pts[i - 1].second)
            cum[i] = total
        }
        if (total < 1e-3f) return pts
        val out = mutableListOf<Pair<Float, Float>>()
        var segIdx = 1
        for (i in 0 until targetCount) {
            val target = (i.toFloat() / (targetCount - 1).toFloat()) * total
            while (segIdx < cum.lastIndex && cum[segIdx] < target) {
                segIdx++
            }
            val prevDist = cum[segIdx - 1]
            val segLen = (cum[segIdx] - prevDist).coerceAtLeast(1e-4f)
            val t = ((target - prevDist) / segLen).coerceIn(0f, 1f)
            val a = pts[segIdx - 1]
            val b = pts[segIdx]
            out.add((a.first + (b.first - a.first) * t) to (a.second + (b.second - a.second) * t))
        }
        return out
    }

    private fun normalizeAngleSigned(rad: Float): Float {
        var r = rad
        val twoPi = (2.0 * PI).toFloat()
        while (r > PI.toFloat()) r -= twoPi
        while (r <= -PI.toFloat()) r += twoPi
        return r
    }

    private data class ScoredCandidate(
        val kind: QuickShapeKind,
        val score: Float,
        val boundaryPoints: List<Pair<Float, Float>>,
        val corners: List<Pair<Float, Float>> = emptyList()
    )

    private fun evaluateEllipseCandidate(
        pts: List<Pair<Float, Float>>,
        diag: Float
    ): ScoredCandidate? {
        val mx = pts.map { it.first }.average().toFloat()
        val my = pts.map { it.second }.average().toFloat()
        var cxx = 0f
        var cyy = 0f
        var cxy = 0f
        pts.forEach { (x, y) ->
            val dx = x - mx
            val dy = y - my
            cxx += dx * dx
            cyy += dy * dy
            cxy += dx * dy
        }
        val rawTheta = 0.5f * atan2(2f * cxy, cxx - cyy)
        val theta = snapAngleIfNearCardinal(rawTheta, toleranceDeg = 7f)
        val cosT = cos(-theta)
        val sinT = sin(-theta)

        var minU = Float.POSITIVE_INFINITY
        var maxU = Float.NEGATIVE_INFINITY
        var minV = Float.POSITIVE_INFINITY
        var maxV = Float.NEGATIVE_INFINITY
        pts.forEach { (x, y) ->
            val dx = x - mx
            val dy = y - my
            val u = dx * cosT - dy * sinT
            val v = dx * sinT + dy * cosT
            if (u < minU) minU = u
            if (u > maxU) maxU = u
            if (v < minV) minV = v
            if (v > maxV) maxV = v
        }
        val rxRaw = (maxU - minU) / 2f
        val ryRaw = (maxV - minV) / 2f
        if (rxRaw < 10f || ryRaw < 10f) return null

        val cu = (minU + maxU) / 2f
        val cv = (minV + maxV) / 2f
        val cosBack = cos(theta)
        val sinBack = sin(theta)
        val cx = mx + (cu * cosBack - cv * sinBack)
        val cy = my + (cu * sinBack + cv * cosBack)

        val aspectRatio = min(rxRaw, ryRaw) / max(rxRaw, ryRaw)
        val isCircle = aspectRatio >= 0.82f
        val rx = if (isCircle) (rxRaw + ryRaw) / 2f else rxRaw
        val ry = if (isCircle) rx else ryRaw
        val kind = if (isCircle) QuickShapeKind.CIRCLE else QuickShapeKind.OVAL

        val ringSteps = 64
        val boundary = (0..ringSteps).map { idx ->
            val a = (2.0 * PI * idx / ringSteps.toDouble()).toFloat()
            val lx = rx * cos(a)
            val ly = ry * sin(a)
            (cx + lx * cosBack - ly * sinBack) to (cy + lx * sinBack + ly * cosBack)
        }
        val score = bidirectionalPerimeterRms(pts, boundary.dropLast(1), closed = true) / diag.coerceAtLeast(1f)
        return ScoredCandidate(kind = kind, score = score, boundaryPoints = boundary)
    }

    private fun evaluateRectangleCandidate(
        pts: List<Pair<Float, Float>>,
        hull: List<Pair<Float, Float>>,
        diag: Float
    ): ScoredCandidate? {
        val candidateAngles = mutableListOf(0f)
        for (i in hull.indices) {
            val a = hull[i]
            val b = hull[(i + 1) % hull.size]
            candidateAngles.add(atan2(b.second - a.second, b.first - a.first))
        }

        var bestArea = Float.POSITIVE_INFINITY
        var bestAngle = 0f
        var bestBounds = floatArrayOf(0f, 0f, 0f, 0f)

        candidateAngles.forEach { angle ->
            val cosA = cos(-angle)
            val sinA = sin(-angle)
            var minU = Float.POSITIVE_INFINITY
            var maxU = Float.NEGATIVE_INFINITY
            var minV = Float.POSITIVE_INFINITY
            var maxV = Float.NEGATIVE_INFINITY
            hull.forEach { (x, y) ->
                val u = x * cosA - y * sinA
                val v = x * sinA + y * cosA
                if (u < minU) minU = u
                if (u > maxU) maxU = u
                if (v < minV) minV = v
                if (v > maxV) maxV = v
            }
            val area = (maxU - minU) * (maxV - minV)
            if (area < bestArea) {
                bestArea = area
                bestAngle = angle
                bestBounds = floatArrayOf(minU, maxU, minV, maxV)
            }
        }

        // Snap near-axis-aligned rectangles within 7 degrees
        val snappedAngle = snapAngleIfNearCardinal(bestAngle, toleranceDeg = 7f)
        if (abs(snappedAngle - bestAngle) > 0.001f) {
            val cosA = cos(-snappedAngle)
            val sinA = sin(-snappedAngle)
            var minU = Float.POSITIVE_INFINITY
            var maxU = Float.NEGATIVE_INFINITY
            var minV = Float.POSITIVE_INFINITY
            var maxV = Float.NEGATIVE_INFINITY
            pts.forEach { (x, y) ->
                val u = x * cosA - y * sinA
                val v = x * sinA + y * cosA
                if (u < minU) minU = u
                if (u > maxU) maxU = u
                if (v < minV) minV = v
                if (v > maxV) maxV = v
            }
            bestAngle = snappedAngle
            bestBounds = floatArrayOf(minU, maxU, minV, maxV)
        }

        val w = bestBounds[1] - bestBounds[0]
        val h = bestBounds[3] - bestBounds[2]
        if (w < 16f || h < 16f) return null

        val cosB = cos(bestAngle)
        val sinB = sin(bestAngle)
        fun toWorld(u: Float, v: Float): Pair<Float, Float> =
            (u * cosB - v * sinB) to (u * sinB + v * cosB)

        val corners = listOf(
            toWorld(bestBounds[0], bestBounds[2]),
            toWorld(bestBounds[1], bestBounds[2]),
            toWorld(bestBounds[1], bestBounds[3]),
            toWorld(bestBounds[0], bestBounds[3])
        )

        // Ensure all 4 corners actually have nearby stroke evidence (prevents triangles/circles matching a bbox)
        val maxCornerGap = corners.maxOf { c ->
            pts.minOf { p -> hypot(p.first - c.first, p.second - c.second) }
        }
        if (maxCornerGap > diag * 0.20f) return null

        val score = bidirectionalPolygonRms(pts, corners) / diag.coerceAtLeast(1f)
        return ScoredCandidate(
            kind = QuickShapeKind.RECTANGLE,
            score = score,
            boundaryPoints = emptyList(),
            corners = corners
        )
    }

    private fun evaluateTriangleCandidate(
        pts: List<Pair<Float, Float>>,
        hull: List<Pair<Float, Float>>,
        diag: Float
    ): ScoredCandidate? {
        if (hull.size < 3) return null

        val reduced = hull.toMutableList()
        while (reduced.size > 3) {
            var minAreaLoss = Float.POSITIVE_INFINITY
            var removeIdx = 0
            for (i in reduced.indices) {
                val prev = reduced[(i - 1 + reduced.size) % reduced.size]
                val curr = reduced[i]
                val next = reduced[(i + 1) % reduced.size]
                val area = triangleArea(prev, curr, next)
                if (area < minAreaLoss) {
                    minAreaLoss = area
                    removeIdx = i
                }
            }
            reduced.removeAt(removeIdx)
        }

        if (!isValidTriangle(reduced, diag)) return null

        val maxCornerGap = reduced.maxOf { c ->
            pts.minOf { p -> hypot(p.first - c.first, p.second - c.second) }
        }
        if (maxCornerGap > diag * 0.22f) return null

        val score = bidirectionalPolygonRms(pts, reduced) / diag.coerceAtLeast(1f)
        return ScoredCandidate(
            kind = QuickShapeKind.TRIANGLE,
            score = score,
            boundaryPoints = emptyList(),
            corners = reduced
        )
    }

    private fun isValidTriangle(corners: List<Pair<Float, Float>>, diag: Float): Boolean {
        if (corners.size != 3) return false
        val area = triangleArea(corners[0], corners[1], corners[2])
        if (area < diag * diag * 0.08f) return false
        for (i in 0 until 3) {
            val a = corners[i]
            val b = corners[(i + 1) % 3]
            val c = corners[(i + 2) % 3]
            val ux = a.first - b.first
            val uy = a.second - b.second
            val vx = c.first - b.first
            val vy = c.second - b.second
            val mu = hypot(ux, uy)
            val mv = hypot(vx, vy)
            if (mu < 14f || mv < 14f) return false
            val cosTheta = ((ux * vx + uy * vy) / (mu * mv)).coerceIn(-1f, 1f)
            val angleDeg = acos(cosTheta.toDouble()) * 180.0 / PI
            if (angleDeg < 18.0 || angleDeg > 155.0) return false
        }
        return true
    }

    private fun triangleArea(
        a: Pair<Float, Float>,
        b: Pair<Float, Float>,
        c: Pair<Float, Float>
    ): Float {
        return abs(
            (a.first * (b.second - c.second) +
                b.first * (c.second - a.second) +
                c.first * (a.second - b.second)) / 2f
        )
    }

    private fun bidirectionalPolygonRms(
        strokePts: List<Pair<Float, Float>>,
        corners: List<Pair<Float, Float>>
    ): Float {
        val boundarySamples = mutableListOf<Pair<Float, Float>>()
        for (i in corners.indices) {
            val a = corners[i]
            val b = corners[(i + 1) % corners.size]
            for (s in 0 until 16) {
                val t = s / 16f
                boundarySamples.add((a.first + (b.first - a.first) * t) to (a.second + (b.second - a.second) * t))
            }
        }
        return bidirectionalPerimeterRms(strokePts, boundarySamples, closed = true)
    }

    private fun bidirectionalPerimeterRms(
        strokePts: List<Pair<Float, Float>>,
        boundaryPts: List<Pair<Float, Float>>,
        closed: Boolean
    ): Float {
        if (strokePts.isEmpty() || boundaryPts.isEmpty()) return Float.POSITIVE_INFINITY
        var sumSq1 = 0.0
        for (p in strokePts) {
            val d = minDistanceToClosedPolyline(p, boundaryPts, closed)
            sumSq1 += d * d
        }
        var sumSq2 = 0.0
        for (b in boundaryPts) {
            var minD = Float.POSITIVE_INFINITY
            for (p in strokePts) {
                val d = hypot(b.first - p.first, b.second - p.second)
                if (d < minD) minD = d
            }
            sumSq2 += minD * minD
        }
        val rms1 = sqrt(sumSq1 / strokePts.size).toFloat()
        val rms2 = sqrt(sumSq2 / boundaryPts.size).toFloat()
        return (rms1 + rms2) * 0.5f
    }

    private fun minDistanceToClosedPolyline(
        p: Pair<Float, Float>,
        poly: List<Pair<Float, Float>>,
        closed: Boolean
    ): Float {
        var minD = Float.POSITIVE_INFINITY
        val limit = if (closed) poly.size else poly.size - 1
        for (i in 0 until limit) {
            val a = poly[i]
            val b = poly[(i + 1) % poly.size]
            val d = pointToSegmentDistance(p.first, p.second, a.first, a.second, b.first, b.second)
            if (d < minD) minD = d
        }
        return minD
    }

    private fun snapLineEndpoints(
        a: Pair<Float, Float>,
        b: Pair<Float, Float>
    ): Pair<Pair<Float, Float>, Pair<Float, Float>> {
        val dx = b.first - a.first
        val dy = b.second - a.second
        val deg = abs(atan2(dy, dx) * 180.0 / PI)
        return when {
            deg <= 5.0 || deg >= 175.0 -> {
                val midY = (a.second + b.second) / 2f
                (a.first to midY) to (b.first to midY)
            }
            deg in 85.0..95.0 -> {
                val midX = (a.first + b.first) / 2f
                (midX to a.second) to (midX to b.second)
            }
            else -> a to b
        }
    }

    private fun snapAngleIfNearCardinal(rad: Float, toleranceDeg: Float): Float {
        val deg = (rad * 180.0 / PI)
        val snappedDeg = (kotlin.math.round(deg / 90.0) * 90.0)
        return if (abs(deg - snappedDeg) <= toleranceDeg) {
            (snappedDeg * PI / 180.0).toFloat()
        } else {
            rad
        }
    }

    private fun sampleLinePoints(
        a: Pair<Float, Float>,
        b: Pair<Float, Float>,
        steps: Int,
        pressure: Float,
        canvasWidth: Int,
        canvasHeight: Int
    ): List<StrokePoint> {
        return (0..steps).map { i ->
            val t = i.toFloat() / steps.toFloat()
            val x = a.first + (b.first - a.first) * t
            val y = a.second + (b.second - a.second) * t
            CanvasTransformEngine.makeNormalizedPoint(x, y, pressure, canvasWidth, canvasHeight)
        }
    }

    private fun samplePolygonWithSharpCorners(
        corners: List<Pair<Float, Float>>,
        pressure: Float,
        canvasWidth: Int,
        canvasHeight: Int
    ): List<StrokePoint> {
        val result = mutableListOf<StrokePoint>()
        // Fractional positions create crisp corners under midpoint quadratic Bezier smoothing
        val edgeTs = floatArrayOf(0f, 0.02f, 0.25f, 0.5f, 0.75f, 0.98f)
        for (i in corners.indices) {
            val a = corners[i]
            val b = corners[(i + 1) % corners.size]
            for (t in edgeTs) {
                val x = a.first + (b.first - a.first) * t
                val y = a.second + (b.second - a.second) * t
                result.add(CanvasTransformEngine.makeNormalizedPoint(x, y, pressure, canvasWidth, canvasHeight))
            }
        }
        val first = corners.first()
        result.add(CanvasTransformEngine.makeNormalizedPoint(first.first, first.second, pressure, canvasWidth, canvasHeight))
        return result
    }

    private fun resampleByArcLength(pts: List<StrokePoint>, count: Int): List<StrokePoint> {
        if (pts.size <= 1 || count <= 1) return pts
        val cum = FloatArray(pts.size)
        var total = 0f
        for (i in 1 until pts.size) {
            total += hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y)
            cum[i] = total
        }
        if (total < 1e-3f) return pts
        val out = mutableListOf<StrokePoint>()
        var segIdx = 1
        for (i in 0 until count) {
            val target = (i.toFloat() / (count - 1).toFloat()) * total
            while (segIdx < cum.lastIndex && cum[segIdx] < target) {
                segIdx++
            }
            val prevDist = cum[segIdx - 1]
            val segLen = (cum[segIdx] - prevDist).coerceAtLeast(1e-4f)
            val t = ((target - prevDist) / segLen).coerceIn(0f, 1f)
            val a = pts[segIdx - 1]
            val b = pts[segIdx]
            out.add(
                StrokePoint(
                    x = a.x + (b.x - a.x) * t,
                    y = a.y + (b.y - a.y) * t,
                    pressure = a.pressure + (b.pressure - a.pressure) * t,
                    normX = a.normX + (b.normX - a.normX) * t,
                    normY = a.normY + (b.normY - a.normY) * t
                )
            )
        }
        return out
    }

    private fun convexHull(points: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
        val sorted = points.distinct().sortedWith(compareBy<Pair<Float, Float>> { it.first }.thenBy { it.second })
        if (sorted.size <= 2) return sorted

        fun cross(o: Pair<Float, Float>, a: Pair<Float, Float>, b: Pair<Float, Float>): Float =
            (a.first - o.first) * (b.second - o.second) - (a.second - o.second) * (b.first - o.first)

        val lower = mutableListOf<Pair<Float, Float>>()
        for (p in sorted) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], p) <= 0f) {
                lower.removeAt(lower.lastIndex)
            }
            lower.add(p)
        }
        val upper = mutableListOf<Pair<Float, Float>>()
        for (p in sorted.asReversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], p) <= 0f) {
                upper.removeAt(upper.lastIndex)
            }
            upper.add(p)
        }
        lower.removeAt(lower.lastIndex)
        upper.removeAt(upper.lastIndex)
        return lower + upper
    }

    private fun computePathLength(pts: List<StrokePoint>): Float {
        var len = 0f
        for (i in 1 until pts.size) {
            len += hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y)
        }
        return len
    }

    private fun pointToSegmentDistance(
        px: Float, py: Float,
        x1: Float, y1: Float,
        x2: Float, y2: Float
    ): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val lenSq = dx * dx + dy * dy
        if (lenSq < 1e-4f) return hypot(px - x1, py - y1)
        val t = (((px - x1) * dx + (py - y1) * dy) / lenSq).coerceIn(0f, 1f)
        return hypot(px - (x1 + t * dx), py - (y1 + t * dy))
    }
}
