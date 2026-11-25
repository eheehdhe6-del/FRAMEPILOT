package com.example.engine

import com.example.domain.model.AnimationFrame
import com.example.domain.model.DrawingStroke
import com.example.domain.model.FramePilotProject
import com.example.domain.model.Layer
import com.example.domain.model.Scene
import com.example.domain.model.SceneShape
import com.example.domain.model.SceneText
import com.example.domain.model.ShapeType
import com.example.domain.model.SkeletonPose
import com.example.domain.model.StrokePoint
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class MissingInfoWarning(
    val severity: String,
    val targetId: String,
    val targetName: String,
    val message: String
)

object SpatialMapGenerator {

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

    private val LAYER_DRAW_SYMBOLS = charArrayOf('#', '*', '+', '%', '&', 'X', 'W', 'M', 'K', 'Z')

    fun collectWarnings(project: FramePilotProject): List<MissingInfoWarning> {
        val warnings = mutableListOf<MissingInfoWarning>()
        project.scenes.sortedBy { it.orderIndex }.forEachIndexed { sIdx, scene ->
            val sceneTag = "Scene ${sIdx + 1} (${scene.name})"
            if (scene.background.environmentDescription.isBlank() &&
                scene.background.elementsDescription.isEmpty()
            ) {
                warnings.add(
                    MissingInfoWarning(
                        severity = "WARNING",
                        targetId = scene.id,
                        targetName = "$sceneTag Background",
                        message = "Background environment has no description; text-only AI knows color/type (${scene.background.type}) only."
                    )
                )
            }
            scene.orderedLayers().forEach { layer ->
                val hasContent = layer.sceneStrokes.isNotEmpty() ||
                    layer.sceneShapes.isNotEmpty() ||
                    layer.sceneTexts.isNotEmpty() ||
                    scene.frames.any {
                        !it.layerDrawings[layer.id].isNullOrEmpty() || !it.layerShapes[layer.id].isNullOrEmpty()
                    }
                if (hasContent && layer.semantic.isUnlabelled()) {
                    warnings.add(
                        MissingInfoWarning(
                            severity = "UNLABELLED",
                            targetId = layer.id,
                            targetName = "$sceneTag -> Layer '${layer.name}'",
                            message = "Layer contains visual geometry but has no creator description. Marked explicitly as unlabelled."
                        )
                    )
                }
                val unlabelledStrokes = layer.sceneStrokes.count { it.semanticLabel.isNullOrBlank() }
                if (unlabelledStrokes > 0 && layer.semantic.isUnlabelled()) {
                    warnings.add(
                        MissingInfoWarning(
                            severity = "UNLABELLED",
                            targetId = layer.id,
                            targetName = "$sceneTag -> Layer '${layer.name}' strokes",
                            message = "$unlabelledStrokes scene stroke(s) have no individual or layer semantic label (marked unlabelled)."
                        )
                    )
                }
            }
        }
        return warnings
    }

    fun generateSceneSpatialMap(project: FramePilotProject, scene: Scene): String {
        val sb = StringBuilder()
        val w = project.canvasWidth
        val h = project.canvasHeight
        val orderedLayers = scene.orderedLayers()

        sb.appendLine("# Spatial Coordinate Map — ${scene.name}")
        sb.appendLine()
        sb.appendLine("- **Project**: ${project.name} (`${project.id}`)")
        sb.appendLine("- **Scene ID**: `${scene.id}` (Order #${scene.orderIndex + 1})")
        sb.appendLine("- **Canvas Dimensions**: ${w}px × ${h}px (Aspect Ratio: ${project.aspectRatioLabel})")
        sb.appendLine("- **Frame Rate**: ${project.fps} FPS | **Effective Duration**: ${scene.effectiveDurationMs(project.fps)} ms")
        sb.appendLine("- **Coordinate Convention**: Origin `(0, 0)` at **TOP-LEFT**. `+X` increases **RIGHT** (`0..$w`, norm `0.0..1.0`). `+Y` increases **DOWNWARD** (`0..$h`, norm `0.0..1.0`).")
        sb.appendLine("- **Semantic Honesty Rule**: FramePilot does NOT invent object recognition. Any stroke or layer without a creator-authored description is explicitly marked `unlabelled`, while its exact geometric shape, dimensions, color, and polyline path are fully preserved.")
        sb.appendLine()

        // Background section
        sb.appendLine("## 1. Background & Environment")
        sb.appendLine("- **Type**: ${scene.background.type.label}")
        sb.appendLine("- **Primary Color**: `${scene.background.primaryColorHex}` | **Secondary Color**: `${scene.background.secondaryColorHex}`")
        if (!scene.background.imageUri.isNullOrBlank()) {
            sb.appendLine("- **Reference/Background Image URI**: `${scene.background.imageUri}`")
        }
        if (scene.background.textureNote.isNotBlank()) {
            sb.appendLine("- **Texture / Atmosphere**: ${scene.background.textureNote}")
        }
        val envDesc = scene.background.environmentDescription.ifBlank { "unlabelled (no environment description provided)" }
        sb.appendLine("- **Environment Description**: $envDesc")
        if (scene.background.elementsDescription.isNotEmpty()) {
            sb.appendLine("- **Background Elements**:")
            scene.background.elementsDescription.forEach { el ->
                sb.appendLine("  - $el")
            }
        }
        sb.appendLine()

        // True 2D Visual ASCII Render (rasterized strokes, shapes, poses, text)
        val firstFrame = TimelineEngine.normalizeOrder(scene.frames).firstOrNull()
        sb.appendLine("## 2. 2D Visual Canvas Render (Text-Only AI Sight)")
        sb.appendLine("> Rasterized directly from visible strokes, shapes, 2D stickman skeletons, and text positions so non-vision AI models can see the exact visual layout and silhouettes.")
        sb.appendLine()
        sb.appendLine("```text")
        sb.appendLine(buildVisualAsciiCanvas(w, h, orderedLayers, firstFrame))
        sb.appendLine("```")
        sb.appendLine()

        // Z-order and Depth Relationships
        sb.appendLine("## 3. Layer Order (Back-to-Front Z-Order) & Exact Visual Geometry")
        if (orderedLayers.isEmpty()) {
            sb.appendLine("_No layers in scene._")
        } else {
            orderedLayers.forEachIndexed { zIdx, layer ->
                appendLayerVisualDetails(sb, project, orderedLayers, zIdx, layer)
            }
        }
        return sb.toString()
    }

    fun appendLayerVisualDetails(
        sb: StringBuilder,
        project: FramePilotProject,
        orderedLayers: List<Layer>,
        zIdx: Int,
        layer: Layer,
        maxPolylinePts: Int = 14
    ) {
        val w = project.canvasWidth
        val h = project.canvasHeight
        val label = layer.semantic.displayLabel("unlabelled")
        sb.appendLine("### Z=$zIdx — ${layer.name} [${layer.category.label}] (`${layer.id}`)")
        sb.appendLine("- **Visibility**: ${layer.visible} | **Locked**: ${layer.locked} | **Opacity**: ${fmt(layer.opacity)}")
        sb.appendLine("- **Creator Label (What is this?)**: $label")
        if (layer.semantic.represents.isNotBlank()) {
            sb.appendLine("- **Represents**: ${layer.semantic.represents}")
        }
        if (layer.semantic.whatIsItDoing.isNotBlank()) {
            sb.appendLine("- **Action / State**: ${layer.semantic.whatIsItDoing}")
        }
        if (layer.semantic.whereLocated.isNotBlank()) {
            sb.appendLine("- **Creator Location Note**: ${layer.semantic.whereLocated}")
        }
        val behindLayer = orderedLayers.getOrNull(zIdx - 1)?.name ?: "Background"
        val frontLayer = orderedLayers.getOrNull(zIdx + 1)?.name ?: "Topmost (nothing in front)"
        sb.appendLine("- **Depth Relationship**: Behind = ${layer.semantic.behind.ifBlank { behindLayer }}; In Front = ${layer.semantic.inFrontOf.ifBlank { frontLayer }}")
        if (layer.semantic.howShouldItMove.isNotBlank()) {
            sb.appendLine("- **Intended Movement**: ${layer.semantic.howShouldItMove}")
        }
        if (layer.semantic.whatShouldHappenNext.isNotBlank()) {
            sb.appendLine("- **What Happens Next**: ${layer.semantic.whatShouldHappenNext}")
        }

        val bbox = computeLayerBounds(layer, w, h)
        if (bbox != null) {
            val region = describeRegion(bbox.centerX, bbox.centerY, w, h)
            sb.appendLine("- **Exact Bounding Box**: x=[${fmt(bbox.minX)}..${fmt(bbox.maxX)}] (norm ${fmt(bbox.minX / w)}..${fmt(bbox.maxX / w)}), y=[${fmt(bbox.minY)}..${fmt(bbox.maxY)}] (norm ${fmt(bbox.minY / h)}..${fmt(bbox.maxY / h)}), size=(${fmt(bbox.maxX - bbox.minX)}×${fmt(bbox.maxY - bbox.minY)}px), center=(${fmt(bbox.centerX)}, ${fmt(bbox.centerY)}), region=$region")
        }

        if (layer.sceneShapes.isNotEmpty()) {
            sb.appendLine("- **Shapes (${layer.sceneShapes.size})**:")
            layer.sceneShapes.forEach { s ->
                sb.appendLine("  - " + summarizeShape(s, w, h))
            }
        }
        if (layer.sceneTexts.isNotEmpty()) {
            sb.appendLine("- **Typography Elements (${layer.sceneTexts.size})**:")
            layer.sceneTexts.forEach { t ->
                sb.appendLine("  - " + summarizeText(t, w, h))
            }
        }
        if (layer.skeletonPose != null) {
            sb.appendLine("- **2D Pose Keyframe (`${layer.skeletonPose.name}`)**: ${layer.skeletonPose.note.ifBlank { "No pose note" }}")
            sb.appendLine("  - Bones: `head-neck-chest-hip`, `neck-leftShoulder-leftElbow-leftHand`, `neck-rightShoulder-rightElbow-rightHand`, `hip-leftKnee-leftFoot`, `hip-rightKnee-rightFoot`")
            layer.skeletonPose.joints.entries.sortedBy { it.key }.forEach { (joint, pt) ->
                sb.appendLine("  - `$joint`: (${fmt(pt.x)}, ${fmt(pt.y)}) norm=(${fmt(pt.x / w)}, ${fmt(pt.y / h)})")
            }
        }
        if (layer.sceneStrokes.isNotEmpty()) {
            sb.appendLine("- **Stroke Catalogue (${layer.sceneStrokes.size} strokes)**:")
            layer.sceneStrokes.forEach { st ->
                sb.appendLine("  - " + summarizeStroke(st, w, h, maxPolylinePts))
            }
        }
        sb.appendLine()
    }

    fun summarizeShape(shape: SceneShape, w: Int, h: Int): String {
        val label = shape.semantic.displayLabel("unlabelled")
        val cx = shape.x + shape.width / 2f
        val cy = shape.y + shape.height / 2f
        val region = describeRegion(cx, cy, w, h)
        val fillStr = if (shape.filled && !shape.fillColorHex.isNullOrBlank()) shape.fillColorHex else "none"
        return "`${shape.id}` **${shape.shapeType.name}**: region=$region, pos=(${fmt(shape.x)}, ${fmt(shape.y)}), size=(${fmt(shape.width)}×${fmt(shape.height)}px), center=(${fmt(cx)}, ${fmt(cy)}) norm=(${fmt(cx / w)}, ${fmt(cy / h)}), rot=${fmt(shape.rotationDeg)}°, stroke=`${shape.strokeColorHex}` width=${fmt(shape.strokeWidth)}, fill=`$fillStr`, opacity=${fmt(shape.opacity)}, label=$label"
    }

    fun summarizeText(text: SceneText, w: Int, h: Int): String {
        val region = describeRegion(text.x, text.y, w, h)
        return "`${text.id}` **\"${text.text}\"**: region=$region, pos=(${fmt(text.x)}, ${fmt(text.y)}) norm=(${fmt(text.x / w)}, ${fmt(text.y / h)}), size=${fmt(text.fontSizeSp)}sp, rot=${fmt(text.rotationDeg)}°, color=`${text.colorHex}`, opacity=${fmt(text.opacity)}, anim=${text.animationStyle.name}, label=${text.semantic.displayLabel("unlabelled")}"
    }

    fun summarizeStroke(stroke: DrawingStroke, w: Int, h: Int, maxTracePoints: Int = 14): String {
        val label = stroke.semanticLabel?.takeIf { it.isNotBlank() } ?: "unlabelled"
        val pts = stroke.points
        if (pts.isEmpty()) return "`${stroke.id}` [${stroke.tool.name}] (0 pts) label=$label"
        val minX = pts.minOf { it.x }
        val maxX = pts.maxOf { it.x }
        val minY = pts.minOf { it.y }
        val maxY = pts.maxOf { it.y }
        val spanW = maxX - minX
        val spanH = maxY - minY
        val cx = (minX + maxX) / 2f
        val cy = (minY + maxY) / 2f
        val region = describeRegion(cx, cy, w, h)
        val pathLen = computePathLength(pts)
        val simplified = simplifyPolyline(pts, maxTracePoints)
        val shapeDesc = classifyStrokeShape(pts, simplified, stroke.isFilledLoop)
        val fillInfo = if (stroke.isFilledLoop && !stroke.fillColorHex.isNullOrBlank()) " fill=`${stroke.fillColorHex}`" else ""
        val traceStr = simplified.joinToString(" -> ") { p ->
            "(${fmt(p.x)},${fmt(p.y)} | n:${fmt(p.x / w)},${fmt(p.y / h)})"
        }
        return "`${stroke.id}` [${stroke.tool.name}] shape=\"$shapeDesc\" region=$region color=`${stroke.colorHex}`$fillInfo width=${fmt(stroke.strokeWidth)} opacity=${fmt(stroke.opacity)} len=${fmt(pathLen)}px span=${fmt(spanW)}×${fmt(spanH)}px bbox=[${fmt(minX)}..${fmt(maxX)}, ${fmt(minY)}..${fmt(maxY)}] norm=[${fmt(minX / w)}..${fmt(maxX / w)}, ${fmt(minY / h)}..${fmt(maxY / h)}] trace(${pts.size}pts->${simplified.size}kpts)=$traceStr label=$label"
    }

    private fun computePathLength(pts: List<StrokePoint>): Float {
        var total = 0f
        for (i in 1 until pts.size) {
            total += hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y)
        }
        return total
    }

    fun simplifyPolyline(pts: List<StrokePoint>, maxPoints: Int = 14): List<StrokePoint> {
        if (pts.size <= maxPoints.coerceAtLeast(2)) return pts
        val minX = pts.minOf { it.x }
        val maxX = pts.maxOf { it.x }
        val minY = pts.minOf { it.y }
        val maxY = pts.maxOf { it.y }
        val diag = hypot(maxX - minX, maxY - minY)
        var epsilon = (diag * 0.02f).coerceAtLeast(3f)
        var simplified = rdpSimplify(pts, epsilon)
        var guard = 0
        while (simplified.size > maxPoints && guard < 10) {
            epsilon *= 1.45f
            simplified = rdpSimplify(pts, epsilon)
            guard++
        }
        if (simplified.size > maxPoints) {
            val result = mutableListOf<StrokePoint>()
            val step = (simplified.size - 1).toFloat() / (maxPoints - 1)
            for (i in 0 until maxPoints - 1) {
                result.add(simplified[(i * step).roundToInt().coerceIn(0, simplified.lastIndex)])
            }
            result.add(simplified.last())
            return result
        }
        return simplified
    }

    private fun rdpSimplify(pts: List<StrokePoint>, epsilon: Float): List<StrokePoint> {
        if (pts.size <= 2) return pts
        val start = pts.first()
        val end = pts.last()
        var maxDist = -1f
        var index = 0
        for (i in 1 until pts.lastIndex) {
            val d = pointToSegmentDistance(pts[i], start, end)
            if (d > maxDist) {
                maxDist = d
                index = i
            }
        }
        return if (maxDist > epsilon && index > 0) {
            val left = rdpSimplify(pts.subList(0, index + 1), epsilon)
            val right = rdpSimplify(pts.subList(index, pts.size), epsilon)
            left.dropLast(1) + right
        } else {
            listOf(start, end)
        }
    }

    private fun pointToSegmentDistance(p: StrokePoint, a: StrokePoint, b: StrokePoint): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val lenSq = dx * dx + dy * dy
        if (lenSq < 1e-4f) return hypot(p.x - a.x, p.y - a.y)
        val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / lenSq).coerceIn(0f, 1f)
        val projX = a.x + t * dx
        val projY = a.y + t * dy
        return hypot(p.x - projX, p.y - projY)
    }

    private fun classifyStrokeShape(
        rawPts: List<StrokePoint>,
        simplified: List<StrokePoint>,
        isFilledLoop: Boolean
    ): String {
        if (rawPts.size <= 1) return "dot / single tap"
        val minX = rawPts.minOf { it.x }
        val maxX = rawPts.maxOf { it.x }
        val minY = rawPts.minOf { it.y }
        val maxY = rawPts.maxOf { it.y }
        val spanW = maxX - minX
        val spanH = maxY - minY
        val diag = hypot(spanW, spanH)
        if (diag < 12f) return "short dot / mark"

        val first = rawPts.first()
        val last = rawPts.last()
        val endToEnd = hypot(last.x - first.x, last.y - first.y)
        val pathLen = computePathLength(rawPts).coerceAtLeast(1f)
        val isClosed = isFilledLoop || (rawPts.size >= 4 && endToEnd <= max(24f, diag * 0.25f))

        if (isClosed) {
            val cx = rawPts.map { it.x }.average().toFloat()
            val cy = rawPts.map { it.y }.average().toFloat()
            val radii = rawPts.map { hypot(it.x - cx, it.y - cy) }
            val meanR = radii.average().toFloat().coerceAtLeast(1f)
            val stdDev = sqrt(radii.map { (it - meanR) * (it - meanR) }.average()).toFloat()
            val relVar = stdDev / meanR
            val aspect = spanW / spanH.coerceAtLeast(1f)
            val filledTag = if (isFilledLoop) "filled " else ""
            return when {
                relVar < 0.22f && aspect in 0.72f..1.38f -> "${filledTag}closed circle-like loop"
                relVar < 0.28f -> "${filledTag}closed oval/ellipse loop"
                else -> "${filledTag}closed freehand contour (${(simplified.size - 1).coerceAtLeast(3)} segments)"
            }
        }

        val straightness = endToEnd / pathLen
        if (straightness >= 0.92f) {
            val dx = last.x - first.x
            val dy = last.y - first.y
            val deg = abs(atan2(dy, dx) * 180.0 / PI)
            val orientation = when {
                deg <= 20.0 || deg >= 160.0 -> "horizontal"
                deg in 70.0..110.0 -> "vertical"
                else -> "diagonal"
            }
            return "straight $orientation line"
        }

        val turns = countSignificantTurns(simplified)
        return when {
            turns <= 1 -> "smooth curved arc"
            turns <= 3 -> "curved wave/S-path ($turns bends)"
            else -> "multi-bend freehand stroke ($turns bends)"
        }
    }

    private fun countSignificantTurns(pts: List<StrokePoint>): Int {
        if (pts.size < 3) return 0
        var turns = 0
        for (i in 1 until pts.lastIndex) {
            val ax = pts[i].x - pts[i - 1].x
            val ay = pts[i].y - pts[i - 1].y
            val bx = pts[i + 1].x - pts[i].x
            val by = pts[i + 1].y - pts[i].y
            val magA = hypot(ax, ay)
            val magB = hypot(bx, by)
            if (magA > 1f && magB > 1f) {
                val cosTheta = ((ax * bx + ay * by) / (magA * magB)).coerceIn(-1f, 1f)
                val angleDeg = Math.toDegrees(kotlin.math.acos(cosTheta.toDouble()))
                if (angleDeg > 35.0) turns++
            }
        }
        return turns
    }

    fun describeRegion(x: Float, y: Float, w: Int, h: Int): String {
        val nx = (x / w.coerceAtLeast(1)).coerceIn(0f, 1f)
        val ny = (y / h.coerceAtLeast(1)).coerceIn(0f, 1f)
        val horiz = when {
            nx < 0.33f -> "left"
            nx > 0.67f -> "right"
            else -> "center"
        }
        val vert = when {
            ny < 0.33f -> "top"
            ny > 0.67f -> "bottom"
            else -> "middle"
        }
        return if (horiz == "center" && vert == "middle") "dead-center" else "$vert-$horiz"
    }

    data class LayerBounds(
        val minX: Float,
        val minY: Float,
        val maxX: Float,
        val maxY: Float
    ) {
        val centerX: Float get() = (minX + maxX) / 2f
        val centerY: Float get() = (minY + maxY) / 2f
    }

    fun computeLayerBounds(layer: Layer, canvasW: Int, canvasH: Int): LayerBounds? {
        val xs = mutableListOf<Float>()
        val ys = mutableListOf<Float>()
        layer.sceneStrokes.forEach { st ->
            st.points.forEach { pt -> xs.add(pt.x); ys.add(pt.y) }
        }
        layer.sceneShapes.forEach { sh ->
            xs.add(sh.x); xs.add(sh.x + sh.width)
            ys.add(sh.y); ys.add(sh.y + sh.height)
        }
        layer.sceneTexts.forEach { tx ->
            xs.add(tx.x); ys.add(tx.y)
        }
        layer.skeletonPose?.joints?.values?.forEach { j ->
            xs.add(j.x); ys.add(j.y)
        }
        if (xs.isEmpty() || ys.isEmpty()) return null
        return LayerBounds(
            minX = xs.min().coerceIn(0f, canvasW.toFloat()),
            minY = ys.min().coerceIn(0f, canvasH.toFloat()),
            maxX = xs.max().coerceIn(0f, canvasW.toFloat()),
            maxY = ys.max().coerceIn(0f, canvasH.toFloat())
        )
    }

    fun buildVisualAsciiCanvas(
        w: Int,
        h: Int,
        layers: List<Layer>,
        frameOverlay: AnimationFrame? = null
    ): String {
        val (cols, rows) = when {
            h > w * 1.25f -> 34 to 26
            w > h * 1.25f -> 52 to 18
            else -> 36 to 20
        }
        val grid = Array(rows) { CharArray(cols) { '.' } }
        val legend = mutableListOf<String>()
        var symIdx = 0

        fun toCol(x: Float): Int = ((x / w.coerceAtLeast(1)) * (cols - 1)).roundToInt().coerceIn(0, cols - 1)
        fun toRow(y: Float): Int = ((y / h.coerceAtLeast(1)) * (rows - 1)).roundToInt().coerceIn(0, rows - 1)

        fun plot(c: Int, r: Int, ch: Char) {
            if (r in 0 until rows && c in 0 until cols) {
                grid[r][c] = ch
            }
        }

        fun drawLineGrid(x0: Float, y0: Float, x1: Float, y1: Float, ch: Char) {
            var c0 = toCol(x0)
            var r0 = toRow(y0)
            val c1 = toCol(x1)
            val r1 = toRow(y1)
            val dc = abs(c1 - c0)
            val dr = abs(r1 - r0)
            val sc = if (c0 < c1) 1 else -1
            val sr = if (r0 < r1) 1 else -1
            var err = dc - dr
            while (true) {
                plot(c0, r0, ch)
                if (c0 == c1 && r0 == r1) break
                val e2 = 2 * err
                if (e2 > -dr) {
                    err -= dr
                    c0 += sc
                }
                if (e2 < dc) {
                    err += dc
                    r0 += sr
                }
            }
        }

        fun drawPolylineGrid(pts: List<Pair<Float, Float>>, ch: Char, closed: Boolean) {
            if (pts.isEmpty()) return
            if (pts.size == 1) {
                plot(toCol(pts[0].first), toRow(pts[0].second), ch)
                return
            }
            for (i in 1 until pts.size) {
                drawLineGrid(pts[i - 1].first, pts[i - 1].second, pts[i].first, pts[i].second, ch)
            }
            if (closed && pts.size >= 3) {
                drawLineGrid(pts.last().first, pts.last().second, pts.first().first, pts.first().second, ch)
            }
        }

        fun rotatePoint(px: Float, py: Float, cx: Float, cy: Float, deg: Float): Pair<Float, Float> {
            if (abs(deg) < 0.01f) return px to py
            val rad = Math.toRadians(deg.toDouble())
            val cosA = cos(rad).toFloat()
            val sinA = sin(rad).toFloat()
            val dx = px - cx
            val dy = py - cy
            return (cx + dx * cosA - dy * sinA) to (cy + dx * sinA + dy * cosA)
        }

        fun drawShapeGrid(sh: SceneShape, ch: Char) {
            val cx = sh.x + sh.width / 2f
            val cy = sh.y + sh.height / 2f
            val left = min(sh.x, sh.x + sh.width)
            val right = max(sh.x, sh.x + sh.width)
            val top = min(sh.y, sh.y + sh.height)
            val bottom = max(sh.y, sh.y + sh.height)
            when (sh.shapeType) {
                ShapeType.LINE -> drawLineGrid(sh.x, sh.y, sh.x + sh.width, sh.y + sh.height, ch)
                ShapeType.ARROW -> {
                    val endX = sh.x + sh.width
                    val endY = sh.y + sh.height
                    drawLineGrid(sh.x, sh.y, endX, endY, ch)
                    val tipChar = when {
                        abs(sh.width) >= abs(sh.height) -> if (sh.width >= 0f) '>' else '<'
                        sh.height >= 0f -> 'v'
                        else -> '^'
                    }
                    plot(toCol(endX), toRow(endY), tipChar)
                }
                ShapeType.RECTANGLE -> {
                    val corners = listOf(
                        rotatePoint(left, top, cx, cy, sh.rotationDeg),
                        rotatePoint(right, top, cx, cy, sh.rotationDeg),
                        rotatePoint(right, bottom, cx, cy, sh.rotationDeg),
                        rotatePoint(left, bottom, cx, cy, sh.rotationDeg)
                    )
                    drawPolylineGrid(corners, ch, closed = true)
                }
                ShapeType.ELLIPSE -> {
                    val rx = abs(sh.width) / 2f
                    val ry = abs(sh.height) / 2f
                    val ring = (0 until 24).map { idx ->
                        val a = (2.0 * PI * idx / 24.0)
                        val px = cx + rx * cos(a).toFloat()
                        val py = cy + ry * sin(a).toFloat()
                        rotatePoint(px, py, cx, cy, sh.rotationDeg)
                    }
                    drawPolylineGrid(ring, ch, closed = true)
                }
                ShapeType.POLYGON -> {
                    val poly = MotionEvaluator.sampleShapePolygon(
                        ShapeType.POLYGON,
                        cx,
                        cy,
                        abs(sh.width),
                        abs(sh.height),
                        5
                    ).map { rotatePoint(it.first, it.second, cx, cy, sh.rotationDeg) }
                    drawPolylineGrid(poly, ch, closed = true)
                }
            }
        }

        fun drawPoseGrid(pose: SkeletonPose) {
            POSE_BONES.forEach { (a, b) ->
                val pa = pose.joints[a] ?: return@forEach
                val pb = pose.joints[b] ?: return@forEach
                val dx = pb.x - pa.x
                val dy = pb.y - pa.y
                val boneChar = when {
                    abs(dx) < abs(dy) * 0.45f -> '|'
                    abs(dy) < abs(dx) * 0.45f -> '-'
                    dx * dy > 0f -> '\\'
                    else -> '/'
                }
                drawLineGrid(pa.x, pa.y, pb.x, pb.y, boneChar)
            }
            pose.joints.forEach { (name, pt) ->
                plot(toCol(pt.x), toRow(pt.y), if (name == "head") '@' else 'o')
            }
        }

        layers.filter { it.visible }.forEach { layer ->
            val frameStrokes = frameOverlay?.layerDrawings?.get(layer.id).orEmpty()
            val frameShapes = frameOverlay?.layerShapes?.get(layer.id).orEmpty()
            val framePose = frameOverlay?.layerPoses?.get(layer.id)
            val allStrokes = layer.sceneStrokes + frameStrokes
            val allShapes = layer.sceneShapes + frameShapes
            val activePose = framePose ?: layer.skeletonPose

            if (allStrokes.isEmpty() && allShapes.isEmpty() && layer.sceneTexts.isEmpty() && activePose == null) {
                return@forEach
            }

            val symbol = LAYER_DRAW_SYMBOLS[symIdx % LAYER_DRAW_SYMBOLS.size]
            symIdx++

            val extras = buildList {
                if (allStrokes.isNotEmpty()) add("${allStrokes.size} stroke(s)")
                if (allShapes.isNotEmpty()) add("${allShapes.size} shape(s)")
                if (layer.sceneTexts.isNotEmpty()) add("${layer.sceneTexts.size} text")
                if (activePose != null) add("2D pose(@/o)")
            }.joinToString(", ")
            legend.add("$symbol = ${layer.name} [${layer.semantic.displayLabel("unlabelled")}] ($extras)")

            allShapes.forEach { drawShapeGrid(it, symbol) }
            allStrokes.forEach { st ->
                drawPolylineGrid(st.points.map { it.x to it.y }, symbol, closed = st.isFilledLoop)
            }
            activePose?.let { drawPoseGrid(it) }
            layer.sceneTexts.forEach { tx ->
                val tag = "\"${tx.text.take(10)}\""
                val startCol = toCol(tx.x).coerceIn(0, (cols - tag.length).coerceAtLeast(0))
                val r = toRow(tx.y)
                tag.forEachIndexed { idx, c ->
                    plot(startCol + idx, r, c)
                }
            }
        }

        val out = StringBuilder()
        out.appendLine("+(0,0)" + "-".repeat((cols - 12).coerceAtLeast(4)) + "-> +X ($w)")
        grid.forEach { row ->
            out.appendLine("|" + row.concatToString() + "|")
        }
        out.appendLine("v +Y ($h)")
        if (legend.isNotEmpty()) {
            out.appendLine("Legend: " + legend.joinToString(" | "))
        } else {
            out.appendLine("Legend: (empty canvas)")
        }
        return out.toString().trimEnd()
    }

    fun generateFrameSequenceMarkdown(project: FramePilotProject, scene: Scene): String {
        val ordered = TimelineEngine.normalizeOrder(scene.frames)
        val w = project.canvasWidth
        val h = project.canvasHeight
        val orderedLayers = scene.orderedLayers()
        val sb = StringBuilder()
        sb.appendLine("# Frame-by-Frame Sequence — ${scene.name}")
        sb.appendLine()
        sb.appendLine("- **FPS**: ${project.fps} | **Authored Cels**: ${ordered.size} | **Total Exposure Ticks**: ${scene.totalExposureTicks()}")
        sb.appendLine("- **Playback Order**: Strict Left-to-Right (`1 -> ${ordered.size}`)")
        sb.appendLine()
        var tickCursor = 0
        ordered.forEachIndexed { idx, frame ->
            val visibleNum = idx + 1
            val startMs = (tickCursor * 1000L) / project.fps.coerceAtLeast(1)
            tickCursor += frame.exposureFrames.coerceAtLeast(1)
            val endMs = (tickCursor * 1000L) / project.fps.coerceAtLeast(1)
            sb.appendLine("## Frame $visibleNum (ID: `${frame.id}`)")
            sb.appendLine("- **Exposure**: ${frame.exposureFrames} frame(s) (${startMs}ms .. ${endMs}ms)")
            if (frame.note.isNotBlank()) {
                sb.appendLine("- **Creator Frame Note**: ${frame.note}")
            }
            val totalStrokes = frame.layerDrawings.values.sumOf { it.size }
            val totalShapes = frame.layerShapes.values.sumOf { it.size }
            sb.appendLine("- **Cel Contents**: $totalStrokes stroke(s), $totalShapes shape(s), ${frame.layerPoses.size} pose override(s)")

            if (totalStrokes > 0 || totalShapes > 0 || frame.layerPoses.isNotEmpty()) {
                sb.appendLine("```text")
                sb.appendLine(buildVisualAsciiCanvas(w, h, orderedLayers, frame))
                sb.appendLine("```")
            }

            frame.layerShapes.forEach { (layerId, shapes) ->
                val layerName = scene.layers.find { it.id == layerId }?.name ?: layerId
                shapes.forEach { sh ->
                    sb.appendLine("  - [$layerName shape] " + summarizeShape(sh, w, h))
                }
            }
            frame.layerPoses.forEach { (layerId, pose) ->
                val layerName = scene.layers.find { it.id == layerId }?.name ?: layerId
                val jointsSummary = pose.joints.entries.sortedBy { it.key }
                    .joinToString(", ") { (k, pt) -> "$k=(${fmt(pt.x)},${fmt(pt.y)})" }
                sb.appendLine("  - [$layerName pose '${pose.name}']: $jointsSummary")
            }
            frame.layerDrawings.forEach { (layerId, strokes) ->
                val layerName = scene.layers.find { it.id == layerId }?.name ?: layerId
                strokes.forEach { st ->
                    sb.appendLine("  - [$layerName] " + summarizeStroke(st, w, h))
                }
            }
            sb.appendLine()
        }
        return sb.toString()
    }

    fun fmt(v: Float): String = String.format(Locale.US, "%.1f", v)
}
