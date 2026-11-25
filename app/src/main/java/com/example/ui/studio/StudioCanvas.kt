package com.example.ui.studio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.domain.model.DrawingStroke
import com.example.domain.model.DrawingTool
import com.example.domain.model.SceneShape
import com.example.domain.model.ShapeType
import com.example.domain.model.StrokePoint
import com.example.domain.model.StudioMode
import com.example.engine.CanvasTransformEngine
import com.example.engine.MotionEvaluator
import com.example.engine.TimelineEngine
import com.example.ui.components.drawShapeItem
import com.example.ui.components.drawStrokeItem
import com.example.ui.components.renderSceneDocument
import com.example.ui.theme.InkCharcoal
import com.example.ui.theme.InkSlate
import com.example.ui.theme.PaperBorder
import com.example.ui.theme.PaperLinen
import com.example.ui.theme.PaperSheet
import com.example.ui.theme.StudioCoral
import com.example.ui.theme.StudioLavender
import com.example.ui.theme.StudioLavenderSoft
import com.example.ui.theme.StudioMint
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun StudioCanvas(
    state: StudioUiState,
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier
) {
    val project = state.project ?: return
    val scene = state.activeScene ?: return
    val textMeasurer = rememberTextMeasurer()

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var hasInitialFit by remember(project.id) { mutableStateOf(false) }

    // Transient interactive stroke/shape state (zero project serialization during drag!)
    val activeStrokePoints = remember { mutableStateListOf<StrokePoint>() }
    var shapeDragStart by remember { mutableStateOf<Offset?>(null) }
    var shapeDragCurrent by remember { mutableStateOf<Offset?>(null) }
    val lassoPoints = remember { mutableStateListOf<Pair<Float, Float>>() }
    var pendingTextDocPos by remember { mutableStateOf<Pair<Float, Float>?>(null) }

    val latestState by rememberUpdatedState(state)

    LaunchedEffect(canvasSize, project.id) {
        if (!hasInitialFit && canvasSize.width > 0 && canvasSize.height > 0) {
            viewModel.fitCanvasToViewport(canvasSize.width.toFloat(), canvasSize.height.toFloat())
            hasInitialFit = true
        }
    }

    val orderedFrames = remember(scene.frames) { TimelineEngine.normalizeOrder(scene.frames) }
    val activeFrameIndex = orderedFrames.indexOfFirst { it.id == state.selectedFrameId }.coerceAtLeast(0)
    val activeFrame = orderedFrames.getOrNull(activeFrameIndex)
    val prevFrame = if (activeFrameIndex > 0) orderedFrames[activeFrameIndex - 1] else null
    val nextFrame = if (activeFrameIndex < orderedFrames.lastIndex) orderedFrames[activeFrameIndex + 1] else null

    val evaluateAtMs = if (state.isPlaying || state.activeMode == StudioMode.MOTION_STUDIO && state.playheadTimeMs > 0L) {
        state.playheadTimeMs
    } else null

    val evalCamera = remember(scene.cameraTrack, evaluateAtMs) {
        if (evaluateAtMs != null) MotionEvaluator.evaluateCamera(scene.cameraTrack, evaluateAtMs) else null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PaperLinen)
            .clipToBounds()
            .onSizeChanged { canvasSize = it }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("studio_drawing_canvas")
                .pointerInput(project.id, state.activeTool, state.isRecordingPathForTrackId) {
                    awaitEachGesture {
                        val firstDown = awaitFirstDown(requireUnconsumed = false)
                        val st = latestState
                        val proj = st.project ?: return@awaitEachGesture
                        val docStart = st.viewport.screenToDocument(firstDown.position.x, firstDown.position.y)

                        var isMultiTouch = false
                        var dragAccumulated = 0f
                        var prevSelectDoc = docStart
                        var movedSelection = false
                        var erasedInGesture = false

                        val tool = if (st.isRecordingPathForTrackId != null) DrawingTool.PEN else st.activeTool

                        fun addFreehandPoint(change: PointerInputChange) {
                            val doc = latestState.viewport.screenToDocument(change.position.x, change.position.y)
                            val last = activeStrokePoints.lastOrNull()
                            if (last == null || hypot(doc.first - last.x, doc.second - last.y) >= 2.5f) {
                                activeStrokePoints.add(
                                    CanvasTransformEngine.makeNormalizedPoint(
                                        docX = doc.first,
                                        docY = doc.second,
                                        pressure = change.pressure,
                                        canvasWidth = proj.canvasWidth,
                                        canvasHeight = proj.canvasHeight
                                    )
                                )
                            }
                        }

                        // Initial pointer down action
                        when (tool) {
                            DrawingTool.PENCIL, DrawingTool.PEN, DrawingTool.BRUSH -> {
                                activeStrokePoints.clear()
                                addFreehandPoint(firstDown)
                            }
                            DrawingTool.ERASER -> {
                                if (viewModel.eraseAt(docStart.first, docStart.second, recordUndo = !erasedInGesture)) {
                                    erasedInGesture = true
                                }
                            }
                            DrawingTool.LINE, DrawingTool.RECTANGLE, DrawingTool.ELLIPSE, DrawingTool.ARROW -> {
                                shapeDragStart = Offset(docStart.first, docStart.second)
                                shapeDragCurrent = Offset(docStart.first, docStart.second)
                            }
                            DrawingTool.LASSO -> {
                                lassoPoints.clear()
                                lassoPoints.add(docStart)
                            }
                            DrawingTool.SELECT -> {
                                // Tap or drag existing selection
                            }
                            else -> {}
                        }

                        while (true) {
                            val event = awaitPointerEvent()
                            val activeChanges = event.changes.filter { it.pressed }
                            if (activeChanges.isEmpty()) break

                            if (activeChanges.size >= 2) {
                                // Safe transition from drawing to 2-finger zoom/pan!
                                if (!isMultiTouch) {
                                    isMultiTouch = true
                                    if (activeStrokePoints.size >= 4 && dragAccumulated > 18f) {
                                        viewModel.addCompletedStroke(activeStrokePoints.toList())
                                    }
                                    activeStrokePoints.clear()
                                    shapeDragStart = null
                                    shapeDragCurrent = null
                                    lassoPoints.clear()
                                }
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val centroid = event.calculateCentroid(useCurrent = false)
                                val updated = CanvasTransformEngine.applyPinchAndPan(
                                    current = latestState.viewport,
                                    zoomFactor = zoom,
                                    panDeltaX = pan.x,
                                    panDeltaY = pan.y,
                                    centroidScreenX = centroid.x,
                                    centroidScreenY = centroid.y
                                )
                                viewModel.updateViewport(updated)
                                activeChanges.forEach { it.consume() }
                                continue
                            }

                            if (isMultiTouch) {
                                // Wait until all fingers lift after pinch-pan
                                continue
                            }

                            val change = activeChanges.first()
                            val deltaScreen = change.position - change.previousPosition
                            dragAccumulated += hypot(deltaScreen.x, deltaScreen.y)
                            val docPt = latestState.viewport.screenToDocument(change.position.x, change.position.y)

                            when (tool) {
                                DrawingTool.HAND -> {
                                    val vp = latestState.viewport
                                    viewModel.updateViewport(vp.copy(panX = vp.panX + deltaScreen.x, panY = vp.panY + deltaScreen.y))
                                }
                                DrawingTool.PENCIL, DrawingTool.PEN, DrawingTool.BRUSH -> {
                                    addFreehandPoint(change)
                                }
                                DrawingTool.ERASER -> {
                                    if (viewModel.eraseAt(docPt.first, docPt.second, recordUndo = !erasedInGesture)) {
                                        erasedInGesture = true
                                    }
                                }
                                DrawingTool.LINE, DrawingTool.RECTANGLE, DrawingTool.ELLIPSE, DrawingTool.ARROW -> {
                                    shapeDragCurrent = Offset(docPt.first, docPt.second)
                                }
                                DrawingTool.LASSO -> {
                                    val last = lassoPoints.lastOrNull()
                                    if (last == null || hypot(docPt.first - last.first, docPt.second - last.second) > 8f) {
                                        lassoPoints.add(docPt)
                                    }
                                }
                                DrawingTool.SELECT -> {
                                    val hasSel = latestState.selectedShapeId != null ||
                                        latestState.selectedTextId != null ||
                                        latestState.selectedStrokeIds.isNotEmpty()
                                    if (hasSel && dragAccumulated > 8f) {
                                        val dx = docPt.first - prevSelectDoc.first
                                        val dy = docPt.second - prevSelectDoc.second
                                        viewModel.moveSelectedBy(dx, dy, recordUndo = !movedSelection)
                                        movedSelection = true
                                        prevSelectDoc = docPt
                                    }
                                }
                                else -> {}
                            }
                            change.consume()
                        }

                        // Pointer lifted
                        if (!isMultiTouch) {
                            when (tool) {
                                DrawingTool.PENCIL, DrawingTool.PEN, DrawingTool.BRUSH -> {
                                    if (activeStrokePoints.isNotEmpty()) {
                                        viewModel.addCompletedStroke(activeStrokePoints.toList())
                                        activeStrokePoints.clear()
                                    }
                                }
                                DrawingTool.LINE, DrawingTool.RECTANGLE, DrawingTool.ELLIPSE, DrawingTool.ARROW -> {
                                    val s = shapeDragStart
                                    val e = shapeDragCurrent
                                    shapeDragStart = null
                                    shapeDragCurrent = null
                                    if (s != null && e != null && hypot(e.x - s.x, e.y - s.y) > 12f) {
                                        val shapeType = when (tool) {
                                            DrawingTool.LINE -> ShapeType.LINE
                                            DrawingTool.RECTANGLE -> ShapeType.RECTANGLE
                                            DrawingTool.ELLIPSE -> ShapeType.ELLIPSE
                                            else -> ShapeType.ARROW
                                        }
                                        viewModel.addShape(shapeType, s.x, s.y, e.x, e.y)
                                    }
                                }
                                DrawingTool.LASSO -> {
                                    if (lassoPoints.size >= 3) {
                                        viewModel.selectByLasso(lassoPoints.toList())
                                    }
                                    lassoPoints.clear()
                                }
                                DrawingTool.TEXT -> {
                                    if (dragAccumulated < 24f) {
                                        pendingTextDocPos = docStart
                                    }
                                }
                                DrawingTool.FILL -> {
                                    if (dragAccumulated < 24f) {
                                        viewModel.handleFillTap(docStart.first, docStart.second)
                                    }
                                }
                                DrawingTool.EYEDROPPER -> {
                                    if (dragAccumulated < 24f) {
                                        viewModel.handleEyedropperTap(docStart.first, docStart.second)
                                    }
                                }
                                DrawingTool.SELECT -> {
                                    if (!movedSelection && dragAccumulated < 20f) {
                                        viewModel.handleSelectTap(docStart.first, docStart.second)
                                    }
                                }
                                else -> {}
                            }
                        }
                    }
                }
        ) {
            val vp = state.viewport
            val docW = project.canvasWidth.toFloat()
            val docH = project.canvasHeight.toFloat()

            withTransform({
                translate(vp.panX, vp.panY)
                scale(vp.scale, vp.scale, pivot = Offset.Zero)
                if (evalCamera != null) {
                    val cx = docW / 2f
                    val cy = docH / 2f
                    translate(evalCamera.panX + evalCamera.shakeX, evalCamera.panY + evalCamera.shakeY)
                    scale(evalCamera.zoom, evalCamera.zoom, pivot = Offset(cx, cy))
                    rotate(evalCamera.rotationDeg, pivot = Offset(cx, cy))
                }
            }) {
                // Soft paper drop shadow
                drawRect(
                    color = InkCharcoal.copy(alpha = 0.08f),
                    topLeft = Offset(14f, 18f),
                    size = Size(docW, docH)
                )

                // Document content
                renderSceneDocument(
                    project = project,
                    scene = scene,
                    activeFrame = activeFrame,
                    previousFrame = prevFrame,
                    nextFrame = nextFrame,
                    onionSkinEnabled = state.onionSkinEnabled && state.activeMode == StudioMode.DRAW_FRAMES,
                    evaluateMotionAtMs = evaluateAtMs,
                    textMeasurer = textMeasurer,
                    showPoseSkeleton = state.activeMode != StudioMode.DRAW_FRAMES && !state.isPlaying,
                    selectedShapeId = state.selectedShapeId,
                    selectedTextId = state.selectedTextId,
                    selectedStrokeIds = state.selectedStrokeIds,
                    activeMotionTrackId = state.selectedMotionTrackId
                )

                // Optional document grid
                if (state.showGrid) {
                    val step = (docW / 8f).coerceAtLeast(60f)
                    val gridColor = InkCharcoal.copy(alpha = 0.10f)
                    var gx = step
                    while (gx < docW) {
                        drawLine(gridColor, Offset(gx, 0f), Offset(gx, docH), strokeWidth = 1.5f / vp.scale)
                        gx += step
                    }
                    var gy = step
                    while (gy < docH) {
                        drawLine(gridColor, Offset(0f, gy), Offset(docW, gy), strokeWidth = 1.5f / vp.scale)
                        gy += step
                    }
                }

                // 9:16 Reels / Shorts Safe-Area guides
                if (state.showSafeArea && project.aspectRatioLabel == "9:16" && !state.isPlaying) {
                    val safeColor = StudioLavender.copy(alpha = 0.42f)
                    val dash = PathEffect.dashPathEffect(floatArrayOf(16f, 12f), 0f)
                    drawRect(
                        color = safeColor,
                        topLeft = Offset(docW * 0.06f, docH * 0.13f),
                        size = Size(docW * 0.80f, docH * 0.65f),
                        style = Stroke(width = 3f / vp.scale, pathEffect = dash)
                    )
                }

                // Live freehand preview stroke
                if (activeStrokePoints.isNotEmpty()) {
                    val previewColor = if (state.isRecordingPathForTrackId != null) "#7E6BFF" else state.brushColorHex
                    val previewStroke = DrawingStroke(
                        id = "live-preview",
                        layerId = state.selectedLayerId,
                        tool = state.activeTool,
                        colorHex = previewColor,
                        strokeWidth = if (state.isRecordingPathForTrackId != null) 7f else state.brushSize,
                        opacity = state.brushOpacity,
                        points = activeStrokePoints.toList()
                    )
                    drawStrokeItem(previewStroke)
                }

                // Live shape drag preview
                val sStart = shapeDragStart
                val sEnd = shapeDragCurrent
                if (sStart != null && sEnd != null) {
                    val stype = when (state.activeTool) {
                        DrawingTool.LINE -> ShapeType.LINE
                        DrawingTool.RECTANGLE -> ShapeType.RECTANGLE
                        DrawingTool.ELLIPSE -> ShapeType.ELLIPSE
                        else -> ShapeType.ARROW
                    }
                    val previewShape = SceneShape(
                        id = "live-shape",
                        layerId = state.selectedLayerId,
                        shapeType = stype,
                        x = if (stype == ShapeType.LINE || stype == ShapeType.ARROW) sStart.x else minOf(sStart.x, sEnd.x),
                        y = if (stype == ShapeType.LINE || stype == ShapeType.ARROW) sStart.y else minOf(sStart.y, sEnd.y),
                        width = if (stype == ShapeType.LINE || stype == ShapeType.ARROW) sEnd.x - sStart.x else abs(sEnd.x - sStart.x),
                        height = if (stype == ShapeType.LINE || stype == ShapeType.ARROW) sEnd.y - sStart.y else abs(sEnd.y - sStart.y),
                        strokeColorHex = state.brushColorHex,
                        strokeWidth = state.brushSize,
                        opacity = state.brushOpacity
                    )
                    drawShapeItem(previewShape)
                }

                // Live Lasso preview
                if (lassoPoints.size >= 2) {
                    val lPath = Path().apply {
                        lassoPoints.forEachIndexed { idx, p ->
                            if (idx == 0) moveTo(p.first, p.second) else lineTo(p.first, p.second)
                        }
                    }
                    drawPath(
                        path = lPath,
                        color = StudioLavender,
                        style = Stroke(width = 4f / vp.scale, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f))
                    )
                }

                // Paper border outline
                drawRect(
                    color = PaperBorder,
                    topLeft = Offset.Zero,
                    size = Size(docW, docH),
                    style = Stroke(width = 2.5f / vp.scale)
                )
            }
        }

        // Top-Left Active Layer & Recording Path Pill
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.isRecordingPathForTrackId != null) {
                Surface(
                    shape = CircleShape,
                    color = StudioLavender,
                    shadowElevation = 2.dp
                ) {
                    Text(
                        "Draw movement path on canvas",
                        style = MaterialTheme.typography.labelMedium,
                        color = PaperSheet,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            } else {
                Surface(
                    shape = CircleShape,
                    color = PaperSheet.copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, PaperBorder),
                    modifier = Modifier
                        .clickable { viewModel.openSheet(StudioSheet.LAYERS) }
                        .testTag("canvas_layer_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Layers, contentDescription = null, tint = StudioCoral, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            state.selectedLayer?.name ?: "Layer",
                            style = MaterialTheme.typography.labelMedium,
                            color = InkCharcoal
                        )
                    }
                }
            }
        }

        // Top-Right Zoom & View HUD
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = PaperSheet.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, PaperBorder)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.toggleGrid() },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("canvas_grid_toggle")
                    ) {
                        Icon(
                            Icons.Rounded.GridOn,
                            contentDescription = "Toggle grid",
                            tint = if (state.showGrid) StudioCoral else InkSlate,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (project.aspectRatioLabel == "9:16") {
                        IconButton(
                            onClick = { viewModel.toggleSafeArea() },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("canvas_safe_area_toggle")
                        ) {
                            Icon(
                                Icons.Rounded.CropFree,
                                contentDescription = "Toggle safe area",
                                tint = if (state.showSafeArea) StudioLavender else InkSlate,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        "${(state.viewport.scale * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkCharcoal,
                        modifier = Modifier
                            .clickable {
                                if (canvasSize.width > 0 && canvasSize.height > 0) {
                                    viewModel.fitCanvasToViewport(canvasSize.width.toFloat(), canvasSize.height.toFloat())
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("canvas_zoom_reset")
                    )
                    IconButton(
                        onClick = {
                            if (canvasSize.width > 0 && canvasSize.height > 0) {
                                viewModel.fitCanvasToViewport(canvasSize.width.toFloat(), canvasSize.height.toFloat())
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Rounded.CenterFocusStrong,
                            contentDescription = "Reset and fit canvas",
                            tint = InkSlate,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Contextual Selection Action Pill (only shown when something is selected!)
        val hasSelection = state.selectedShapeId != null || state.selectedTextId != null || state.selectedStrokeIds.isNotEmpty()
        if (hasSelection && !state.isPlaying) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = PaperSheet,
                border = BorderStroke(1.dp, StudioLavender),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .testTag("selection_context_bar")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (state.selectedShapeId != null) {
                        IconButton(onClick = { viewModel.transformSelectedShape(scaleFactor = 0.85f) }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Rounded.ZoomOut, contentDescription = "Shrink shape", tint = InkCharcoal)
                        }
                        IconButton(onClick = { viewModel.transformSelectedShape(scaleFactor = 1.18f) }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Rounded.ZoomIn, contentDescription = "Grow shape", tint = InkCharcoal)
                        }
                        IconButton(onClick = { viewModel.transformSelectedShape(rotationDeltaDeg = 15f) }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Rounded.RotateRight, contentDescription = "Rotate shape", tint = InkCharcoal)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = StudioLavenderSoft,
                        modifier = Modifier
                            .clickable { viewModel.openSheet(StudioSheet.MOTION_ACTION) }
                            .padding(horizontal = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = StudioLavender, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Animate", style = MaterialTheme.typography.labelMedium, color = StudioLavender)
                        }
                    }
                    TextButton(onClick = { viewModel.openSheet(StudioSheet.AI_INSPECTOR) }) {
                        Text("Describe", color = StudioMint)
                    }
                    IconButton(onClick = { viewModel.deleteSelection() }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete selected", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    pendingTextDocPos?.let { pos ->
        var textInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pendingTextDocPos = null },
            title = { Text("Add Text to Scene") },
            text = {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    label = { Text("Title or caption text") },
                    singleLine = true,
                    modifier = Modifier.testTag("add_text_dialog_input")
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addTextElement(textInput, pos.first, pos.second)
                    pendingTextDocPos = null
                }) { Text("Place") }
            },
            dismissButton = {
                TextButton(onClick = { pendingTextDocPos = null }) { Text("Cancel") }
            }
        )
    }
}
