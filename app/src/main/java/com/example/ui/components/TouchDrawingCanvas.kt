package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.ui.theme.FramePilotTheme
import com.example.ui.theme.InkCharcoal
import com.example.ui.theme.InkSlate
import com.example.ui.theme.PaperBorder
import com.example.ui.theme.PaperSheet
import java.util.UUID
import kotlin.math.hypot

data class TouchPoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1f
)

data class TouchStroke(
    val id: String = UUID.randomUUID().toString(),
    val points: List<TouchPoint>,
    val color: Color,
    val strokeWidth: Float,
    val alpha: Float = 1f
)

sealed interface DrawingHistoryCommand {
    data class AddStroke(val stroke: TouchStroke) : DrawingHistoryCommand
    data class ClearAll(val previousStrokes: List<TouchStroke>) : DrawingHistoryCommand
}

@Stable
class TouchDrawingState(
    initialStrokes: List<TouchStroke> = emptyList(),
    private val maxHistorySize: Int = 100
) {
    val strokes = mutableStateListOf<TouchStroke>().apply { addAll(initialStrokes) }
    internal val activePoints = mutableStateListOf<TouchPoint>()

    private val undoStack = ArrayDeque<DrawingHistoryCommand>()
    private val redoStack = ArrayDeque<DrawingHistoryCommand>()

    var undoStackSize by mutableIntStateOf(0)
        private set
    var redoStackSize by mutableIntStateOf(0)
        private set

    var scale by mutableFloatStateOf(1f)
        internal set
    var panOffset by mutableStateOf(Offset.Zero)
        internal set

    val canUndo: Boolean get() = undoStackSize > 0
    val canRedo: Boolean get() = redoStackSize > 0

    private fun syncCounts() {
        undoStackSize = undoStack.size
        redoStackSize = redoStack.size
    }

    private fun pushCommand(command: DrawingHistoryCommand) {
        undoStack.addLast(command)
        while (undoStack.size > maxHistorySize) {
            undoStack.removeFirst()
        }
        redoStack.clear()
        syncCounts()
    }

    fun commitStroke(stroke: TouchStroke) {
        if (stroke.points.isEmpty()) return
        strokes.add(stroke)
        pushCommand(DrawingHistoryCommand.AddStroke(stroke))
    }

    fun undo(): Boolean {
        if (undoStack.isEmpty()) return false
        val command = undoStack.removeLast()
        when (command) {
            is DrawingHistoryCommand.AddStroke -> {
                val idx = strokes.indexOfLast { it.id == command.stroke.id }
                if (idx >= 0) {
                    strokes.removeAt(idx)
                } else if (strokes.isNotEmpty()) {
                    strokes.removeAt(strokes.lastIndex)
                }
            }
            is DrawingHistoryCommand.ClearAll -> {
                strokes.clear()
                strokes.addAll(command.previousStrokes)
            }
        }
        redoStack.addLast(command)
        syncCounts()
        return true
    }

    fun redo(): Boolean {
        if (redoStack.isEmpty()) return false
        val command = redoStack.removeLast()
        when (command) {
            is DrawingHistoryCommand.AddStroke -> {
                strokes.add(command.stroke)
            }
            is DrawingHistoryCommand.ClearAll -> {
                strokes.clear()
            }
        }
        undoStack.addLast(command)
        syncCounts()
        return true
    }

    fun clear() {
        activePoints.clear()
        if (strokes.isEmpty()) return
        val snapshot = strokes.toList()
        strokes.clear()
        pushCommand(DrawingHistoryCommand.ClearAll(snapshot))
    }

    fun resetHistory() {
        undoStack.clear()
        redoStack.clear()
        syncCounts()
    }

    fun resetTransform() {
        scale = 1f
        panOffset = Offset.Zero
    }

    internal fun screenToCanvas(screenPos: Offset): Offset {
        val safeScale = scale.coerceAtLeast(0.1f)
        return Offset(
            x = (screenPos.x - panOffset.x) / safeScale,
            y = (screenPos.y - panOffset.y) / safeScale
        )
    }
}

@Composable
fun rememberTouchDrawingState(
    initialStrokes: List<TouchStroke> = emptyList(),
    maxHistorySize: Int = 100
): TouchDrawingState {
    return remember { TouchDrawingState(initialStrokes, maxHistorySize) }
}

/**
 * Compact stack-based Undo / Redo / Clear control bar for [TouchDrawingCanvas].
 */
@Composable
fun TouchDrawingUndoRedoBar(
    state: TouchDrawingState,
    modifier: Modifier = Modifier,
    confirmBeforeClear: Boolean = true
) {
    var showClearConfirm by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = PaperSheet,
        border = BorderStroke(1.dp, PaperBorder),
        shadowElevation = 2.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(
                onClick = { state.undo() },
                enabled = state.canUndo,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("touch_canvas_undo_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Undo,
                    contentDescription = "Undo stroke",
                    tint = if (state.canUndo) InkCharcoal else InkSlate.copy(alpha = 0.3f)
                )
            }
            Text(
                text = "${state.strokes.size}",
                style = MaterialTheme.typography.labelSmall,
                color = InkSlate,
                modifier = Modifier.testTag("touch_canvas_stroke_count")
            )
            IconButton(
                onClick = { state.redo() },
                enabled = state.canRedo,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("touch_canvas_redo_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Redo,
                    contentDescription = "Redo stroke",
                    tint = if (state.canRedo) InkCharcoal else InkSlate.copy(alpha = 0.3f)
                )
            }
            IconButton(
                onClick = {
                    if (confirmBeforeClear) showClearConfirm = true else state.clear()
                },
                enabled = state.strokes.isNotEmpty(),
                modifier = Modifier
                    .size(48.dp)
                    .testTag("touch_canvas_clear_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteSweep,
                    contentDescription = "Clear all drawings",
                    tint = if (state.strokes.isNotEmpty()) MaterialTheme.colorScheme.error else InkSlate.copy(alpha = 0.3f)
                )
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear all drawings?") },
            text = { Text("This clears all strokes from the canvas. You can still undo this action.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.clear()
                        showClearConfirm = false
                    },
                    modifier = Modifier.testTag("touch_canvas_confirm_clear_button")
                ) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirm = false },
                    modifier = Modifier.testTag("touch_canvas_cancel_clear_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Reusable Jetpack Compose Canvas component that lets users draw smoothly using touch or stylus gestures,
 * with stack-based undo/redo and optional two-finger pinch-to-zoom and pan support.
 */
@Composable
fun TouchDrawingCanvas(
    modifier: Modifier = Modifier,
    state: TouchDrawingState = rememberTouchDrawingState(),
    brushColor: Color = InkCharcoal,
    brushWidth: Float = 8f,
    brushAlpha: Float = 1f,
    backgroundColor: Color = PaperSheet,
    enablePinchZoomAndPan: Boolean = true,
    enabled: Boolean = true,
    onStrokeCompleted: ((TouchStroke) -> Unit)? = null
) {
    val latestColor by rememberUpdatedState(brushColor)
    val latestWidth by rememberUpdatedState(brushWidth)
    val latestAlpha by rememberUpdatedState(brushAlpha)
    val latestOnCompleted by rememberUpdatedState(onStrokeCompleted)

    Canvas(
        modifier = modifier
            .clipToBounds()
            .background(backgroundColor)
            .testTag("touch_drawing_canvas")
            .pointerInput(enabled, enablePinchZoomAndPan) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    firstDown.consume()

                    state.activePoints.clear()
                    val startCanvas = state.screenToCanvas(firstDown.position)
                    state.activePoints.add(
                        TouchPoint(
                            x = startCanvas.x,
                            y = startCanvas.y,
                            pressure = firstDown.pressure.coerceIn(0.2f, 1.5f)
                        )
                    )

                    var isMultiTouch = false
                    var dragDistance = 0f

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressedChanges = event.changes.filter { it.pressed }
                        if (pressedChanges.isEmpty()) break

                        if (enablePinchZoomAndPan && pressedChanges.size >= 2) {
                            if (!isMultiTouch) {
                                isMultiTouch = true
                                if (state.activePoints.size >= 4 && dragDistance > 16f) {
                                    val committed = TouchStroke(
                                        points = state.activePoints.toList(),
                                        color = latestColor,
                                        strokeWidth = latestWidth,
                                        alpha = latestAlpha
                                    )
                                    state.commitStroke(committed)
                                    latestOnCompleted?.invoke(committed)
                                }
                                state.activePoints.clear()
                            }
                            val zoomFactor = event.calculateZoom()
                            val panDelta = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = false)

                            val oldScale = state.scale
                            val newScale = (oldScale * zoomFactor).coerceIn(0.5f, 5f)
                            val docBefore = state.screenToCanvas(centroid)
                            state.scale = newScale
                            state.panOffset = Offset(
                                x = centroid.x - docBefore.x * newScale + panDelta.x,
                                y = centroid.y - docBefore.y * newScale + panDelta.y
                            )
                            pressedChanges.forEach { it.consume() }
                            continue
                        }

                        if (isMultiTouch) continue

                        val change = pressedChanges.first()
                        val delta = change.position - change.previousPosition
                        dragDistance += hypot(delta.x, delta.y)

                        val canvasPt = state.screenToCanvas(change.position)
                        val lastPt = state.activePoints.lastOrNull()
                        if (lastPt == null || hypot(canvasPt.x - lastPt.x, canvasPt.y - lastPt.y) >= 2f) {
                            state.activePoints.add(
                                TouchPoint(
                                    x = canvasPt.x,
                                    y = canvasPt.y,
                                    pressure = change.pressure.coerceIn(0.2f, 1.5f)
                                )
                            )
                        }
                        change.consume()
                    }

                    if (!isMultiTouch && state.activePoints.isNotEmpty()) {
                        val finished = TouchStroke(
                            points = state.activePoints.toList(),
                            color = latestColor,
                            strokeWidth = latestWidth,
                            alpha = latestAlpha
                        )
                        state.activePoints.clear()
                        state.commitStroke(finished)
                        latestOnCompleted?.invoke(finished)
                    }
                }
            }
    ) {
        withTransform({
            translate(state.panOffset.x, state.panOffset.y)
            scale(state.scale, state.scale, pivot = Offset.Zero)
        }) {
            state.strokes.forEach { stroke ->
                drawSmoothTouchStroke(stroke)
            }
            if (state.activePoints.isNotEmpty()) {
                drawSmoothTouchStroke(
                    TouchStroke(
                        points = state.activePoints.toList(),
                        color = brushColor,
                        strokeWidth = brushWidth,
                        alpha = brushAlpha
                    )
                )
            }
        }
    }
}

private fun DrawScope.drawSmoothTouchStroke(stroke: TouchStroke) {
    val pts = stroke.points
    if (pts.isEmpty()) return
    val tint = stroke.color.copy(alpha = stroke.alpha.coerceIn(0f, 1f))

    if (pts.size == 1) {
        val p = pts.first()
        drawCircle(
            color = tint,
            radius = (stroke.strokeWidth * 0.5f * p.pressure).coerceAtLeast(1f),
            center = Offset(p.x, p.y)
        )
        return
    }

    val path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) {
            val prev = pts[i - 1]
            val curr = pts[i]
            val midX = (prev.x + curr.x) / 2f
            val midY = (prev.y + curr.y) / 2f
            quadraticTo(prev.x, prev.y, midX, midY)
        }
        val last = pts.last()
        lineTo(last.x, last.y)
    }

    val avgPressure = pts.map { it.pressure }.average().toFloat().coerceIn(0.6f, 1.4f)
    drawPath(
        path = path,
        color = tint,
        style = Stroke(
            width = (stroke.strokeWidth * avgPressure).coerceAtLeast(1.5f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

@Preview(showBackground = true)
@Composable
private fun TouchDrawingCanvasPreview() {
    FramePilotTheme {
        TouchDrawingCanvas(modifier = Modifier.fillMaxSize())
    }
}
