package com.example.ui.studio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Workspaces
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import com.example.engine.QuickShapeDetector
import com.example.engine.QuickShapeKind
import com.example.engine.RigBonePreset
import com.example.engine.RigEngine
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
import com.example.ui.theme.StudioMintSoft
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.atan2
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

    // Transient interactive stroke/shape state (zero project serialization during drag!)
    val activeStrokePoints = remember { mutableStateListOf<StrokePoint>() }
    var shapeDragStart by remember { mutableStateOf<Offset?>(null) }
    var shapeDragCurrent by remember { mutableStateOf<Offset?>(null) }
    val lassoPoints = remember { mutableStateListOf<Pair<Float, Float>>() }
    var pendingTextDocPos by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var showAttachRigDialog by remember { mutableStateOf(false) }

    // QuickShape (hold-briefly-or-release) state
    var isFreehandGestureActive by remember { mutableStateOf(false) }
    var lastPointerMoveTimeMs by remember { mutableLongStateOf(0L) }
    var holdAnchorScreenPos by remember { mutableStateOf<Offset?>(null) }
    var holdSnappedKind by remember { mutableStateOf<QuickShapeKind?>(null) }

    val latestState by rememberUpdatedState(state)

    LaunchedEffect(canvasSize, project.id, state.isCanvasFullscreen, state.hasCustomViewport) {
        if (canvasSize.width > 0 && canvasSize.height > 0 && !state.hasCustomViewport) {
            viewModel.fitCanvasToViewport(canvasSize.width.toFloat(), canvasSize.height.toFloat())
        }
    }

    LaunchedEffect(
        isFreehandGestureActive,
        state.quickShapeEnabled,
        state.activeTool,
        state.isRecordingPathForTrackId,
        project.id
    ) {
        val isBrush = state.activeTool in listOf(DrawingTool.PENCIL, DrawingTool.PEN, DrawingTool.BRUSH)
        if (!isFreehandGestureActive || !state.quickShapeEnabled || !isBrush || state.isRecordingPathForTrackId != null) {
            return@LaunchedEffect
        }
        while (isActive && isFreehandGestureActive && holdSnappedKind == null) {
            delay(60L)
            val idleMs = System.currentTimeMillis() - lastPointerMoveTimeMs
            if (idleMs >= 400L && activeStrokePoints.size >= 4) {
                val snapped = QuickShapeDetector.detectAndSnap(
                    rawPoints = activeStrokePoints.toList(),
                    canvasWidth = project.canvasWidth,
                    canvasHeight = project.canvasHeight
                )
                if (snapped != null) {
                    activeStrokePoints.clear()
                    activeStrokePoints.addAll(snapped.snappedPoints)
                    holdSnappedKind = snapped.kind
                }
            }
        }
    }

    val orderedFrames = remember(scene.frames) { TimelineEngine.normalizeOrder(scene.frames) }
    val activeFrameIndex = orderedFrames.indexOfFirst { it.id == state.selectedFrameId }.coerceAtLeast(0)
    val activeFrame = orderedFrames.getOrNull(activeFrameIndex)
    val prevFrame = if (activeFrameIndex > 0) orderedFrames[activeFrameIndex - 1] else null
    val nextFrame = if (activeFrameIndex < orderedFrames.lastIndex) orderedFrames[activeFrameIndex + 1] else null

    val orderedScenes = remember(project.scenes) { project.scenes.sortedBy { it.orderIndex } }
    val activeSceneIndex = orderedScenes.indexOfFirst { it.id == scene.id }
    val ghostPrevScene = if (state.connectedScenesMode && prevFrame == null && activeSceneIndex > 0) {
        val prevSc = orderedScenes[activeSceneIndex - 1]
        prevSc to TimelineEngine.normalizeOrder(prevSc.frames).lastOrNull()
    } else null
    val ghostNextScene = if (state.connectedScenesMode && nextFrame == null && activeSceneIndex >= 0 && activeSceneIndex < orderedScenes.lastIndex) {
        val nextSc = orderedScenes[activeSceneIndex + 1]
        nextSc to TimelineEngine.normalizeOrder(nextSc.frames).firstOrNull()
    } else null

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
                .pointerInput(project.id, state.activeTool, state.isRecordingPathForTrackId, state.activeSheet) {
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

                        // Direct 2D Pose drag state when Pose Editor is active
                        val isPoseEditMode = st.activeSheet == StudioSheet.POSE_EDITOR
                        var poseDragMode: String? = null // "WHOLE", "ROTATE", "SCALE", "PAN", or joint name
                        var poseDragRecorded = false
                        var prevPoseDoc = docStart
                        var poseCenterX = 0f
                        var poseCenterY = 0f
                        var poseStartAngleDeg = 0f
                        var poseStartScale = 1f
                        var poseStartPointerAngleDeg = 0f
                        var poseStartPointerDist = 1f

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

                        if (isPoseEditMode) {
                            val activePose = st.activePoseFor(st.selectedLayer)
                            if (activePose != null && activePose.joints.isNotEmpty()) {
                                val hitRadius = kotlin.math.max(58f, 38f / st.viewport.scale.coerceAtLeast(0.15f))
                                val handleHitRadius = (28f / st.viewport.scale.coerceAtLeast(0.15f)).coerceIn(32f, 80f)
                                if (st.poseEditWholeBody) {
                                    val rawMinX = activePose.joints.values.minOf { it.x } - 30f
                                    val rawMaxX = activePose.joints.values.maxOf { it.x } + 30f
                                    val rawMinY = activePose.joints.values.minOf { it.y } - 30f
                                    val rawMaxY = activePose.joints.values.maxOf { it.y } + 30f
                                    val boxW = (rawMaxX - rawMinX).coerceAtLeast(40f)
                                    val boxH = (rawMaxY - rawMinY).coerceAtLeast(40f)
                                    poseCenterX = (rawMinX + rawMaxX) / 2f
                                    poseCenterY = (rawMinY + rawMaxY) / 2f
                                    val rotHandleX = poseCenterX
                                    val rotHandleY = rawMinY - 46f
                                    val scaleHandleX = rawMinX + boxW
                                    val scaleHandleY = rawMinY + boxH
                                    val distRot = hypot(docStart.first - rotHandleX, docStart.second - rotHandleY)
                                    val distScale = hypot(docStart.first - scaleHandleX, docStart.second - scaleHandleY)

                                    poseDragMode = when {
                                        distRot <= handleHitRadius && distRot <= distScale -> {
                                            poseStartAngleDeg = PoseGeometry.computeAngleDeg(activePose.joints)
                                            poseStartPointerAngleDeg = Math.toDegrees(
                                                atan2((docStart.second - poseCenterY).toDouble(), (docStart.first - poseCenterX).toDouble())
                                            ).toFloat()
                                            "ROTATE"
                                        }
                                        distScale <= handleHitRadius -> {
                                            poseStartScale = PoseGeometry.computeScale(activePose.joints)
                                            poseStartPointerDist = hypot(docStart.first - poseCenterX, docStart.second - poseCenterY).coerceAtLeast(24f)
                                            "SCALE"
                                        }
                                        else -> "WHOLE"
                                    }
                                } else {
                                    val nearest = activePose.joints.entries.minByOrNull { (_, jp) ->
                                        hypot(jp.x - docStart.first, jp.y - docStart.second)
                                    }
                                    if (nearest != null && hypot(nearest.value.x - docStart.first, nearest.value.y - docStart.second) <= hitRadius) {
                                        poseDragMode = nearest.key
                                        viewModel.selectPoseJoint(nearest.key)
                                    } else {
                                        val minX = activePose.joints.values.minOf { it.x } - 48f
                                        val maxX = activePose.joints.values.maxOf { it.x } + 48f
                                        val minY = activePose.joints.values.minOf { it.y } - 48f
                                        val maxY = activePose.joints.values.maxOf { it.y } + 48f
                                        poseDragMode = if (docStart.first in minX..maxX && docStart.second in minY..maxY) {
                                            "WHOLE"
                                        } else {
                                            "PAN"
                                        }
                                    }
                                }
                            } else {
                                poseDragMode = "PAN"
                            }
                        } else {
                            // Initial pointer down action
                            val isBrushTool = tool in listOf(DrawingTool.PENCIL, DrawingTool.PEN, DrawingTool.BRUSH) &&
                                st.isRecordingPathForTrackId == null
                            when (tool) {
                                DrawingTool.PENCIL, DrawingTool.PEN, DrawingTool.BRUSH -> {
                                    activeStrokePoints.clear()
                                    holdSnappedKind = null
                                    holdAnchorScreenPos = firstDown.position
                                    lastPointerMoveTimeMs = System.currentTimeMillis()
                                    isFreehandGestureActive = isBrushTool
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
                        }

                        while (true) {
                            val event = awaitPointerEvent()
                            val activeChanges = event.changes.filter { it.pressed }
                            if (activeChanges.isEmpty()) break

                            if (activeChanges.size >= 2) {
                                // Safe transition from drawing to 2-finger zoom/pan!
                                if (!isMultiTouch) {
                                    isMultiTouch = true
                                    isFreehandGestureActive = false
                                    val preSnapped = holdSnappedKind
                                    holdSnappedKind = null
                                    if (activeStrokePoints.size >= 4 && dragAccumulated > 18f) {
                                        viewModel.addCompletedStroke(activeStrokePoints.toList(), preSnappedKind = preSnapped)
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

                            if (isPoseEditMode) {
                                val mode = poseDragMode
                                val layerId = latestState.selectedLayer?.id ?: latestState.selectedLayerId
                                when (mode) {
                                    "WHOLE" -> {
                                        val dx = docPt.first - prevPoseDoc.first
                                        val dy = docPt.second - prevPoseDoc.second
                                        if (abs(dx) > 0.1f || abs(dy) > 0.1f) {
                                            viewModel.moveLayerPoseBy(layerId, dx, dy, recordUndo = !poseDragRecorded)
                                            poseDragRecorded = true
                                            prevPoseDoc = docPt
                                        }
                                    }
                                    "ROTATE" -> {
                                        val curAngleDeg = Math.toDegrees(
                                            atan2((docPt.second - poseCenterY).toDouble(), (docPt.first - poseCenterX).toDouble())
                                        ).toFloat()
                                        val deltaDeg = PoseGeometry.normalizeAngleDeg(curAngleDeg - poseStartPointerAngleDeg)
                                        viewModel.setLayerPoseAngle(
                                            layerId = layerId,
                                            targetAngleDeg = poseStartAngleDeg + deltaDeg,
                                            recordUndo = !poseDragRecorded
                                        )
                                        poseDragRecorded = true
                                    }
                                    "SCALE" -> {
                                        val curDist = hypot(docPt.first - poseCenterX, docPt.second - poseCenterY).coerceAtLeast(8f)
                                        val targetScale = poseStartScale * (curDist / poseStartPointerDist)
                                        viewModel.setLayerPoseScale(
                                            layerId = layerId,
                                            targetScale = targetScale,
                                            recordUndo = !poseDragRecorded
                                        )
                                        poseDragRecorded = true
                                    }
                                    "PAN" -> {
                                        val vp = latestState.viewport
                                        viewModel.updateViewport(vp.copy(panX = vp.panX + deltaScreen.x, panY = vp.panY + deltaScreen.y))
                                    }
                                    null -> {}
                                    else -> {
                                        val dx = docPt.first - prevPoseDoc.first
                                        val dy = docPt.second - prevPoseDoc.second
                                        val curJoint = latestState.activePoseFor(latestState.selectedLayer)?.joints?.get(mode)
                                        if (curJoint != null && (abs(dx) > 0.1f || abs(dy) > 0.1f)) {
                                            viewModel.updateLayerPoseJoint(
                                                layerId = layerId,
                                                jointName = mode,
                                                x = curJoint.x + dx,
                                                y = curJoint.y + dy,
                                                recordUndo = !poseDragRecorded
                                            )
                                            poseDragRecorded = true
                                            prevPoseDoc = docPt
                                        }
                                    }
                                }
                            } else {
                                when (tool) {
                                    DrawingTool.HAND -> {
                                        val vp = latestState.viewport
                                        viewModel.updateViewport(vp.copy(panX = vp.panX + deltaScreen.x, panY = vp.panY + deltaScreen.y))
                                    }
                                    DrawingTool.PENCIL, DrawingTool.PEN, DrawingTool.BRUSH -> {
                                        if (holdSnappedKind == null) {
                                            val anchor = holdAnchorScreenPos
                                            if (anchor == null || hypot(change.position.x - anchor.x, change.position.y - anchor.y) > 10f) {
                                                holdAnchorScreenPos = change.position
                                                lastPointerMoveTimeMs = System.currentTimeMillis()
                                            }
                                            addFreehandPoint(change)
                                        }
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
                                        val hasSel = latestState.hasAnySelection
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
                            }
                            change.consume()
                        }

                        // Pointer lifted
                        isFreehandGestureActive = false
                        val preSnapped = holdSnappedKind
                        holdSnappedKind = null
                        if (isPoseEditMode) {
                            viewModel.finishPoseTransformSession()
                        } else if (!isMultiTouch) {
                            when (tool) {
                                DrawingTool.PENCIL, DrawingTool.PEN, DrawingTool.BRUSH -> {
                                    if (activeStrokePoints.isNotEmpty()) {
                                        viewModel.addCompletedStroke(activeStrokePoints.toList(), preSnappedKind = preSnapped)
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
                    showPoseSkeleton = (state.activeMode != StudioMode.DRAW_FRAMES || state.activeSheet == StudioSheet.POSE_EDITOR) && !state.isPlaying,
                    selectedShapeIds = state.selectedShapeIds,
                    selectedTextIds = state.selectedTextIds,
                    selectedStrokeIds = state.selectedStrokeIds,
                    activeMotionTrackId = state.selectedMotionTrackId,
                    poseHighlightLayerId = if (state.activeSheet == StudioSheet.POSE_EDITOR) (state.selectedLayer?.id ?: state.selectedLayerId) else null,
                    highlightedPoseJoint = if (state.activeSheet == StudioSheet.POSE_EDITOR && !state.poseEditWholeBody) state.selectedPoseJoint else null,
                    highlightWholePose = state.activeSheet == StudioSheet.POSE_EDITOR && state.poseEditWholeBody,
                    ghostPrevScene = ghostPrevScene,
                    ghostNextScene = ghostNextScene
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

        val hudInsetsModifier = if (state.isCanvasFullscreen) {
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
        } else {
            Modifier
        }

        // Top-Left Active Layer, Quick 2D Pose, Fullscreen Controls & Recording Path Pills
        Column(
            modifier = hudInsetsModifier
                .align(Alignment.TopStart)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.Start
        ) {
            if (state.isCanvasFullscreen) {
                Surface(
                    shape = CircleShape,
                    color = PaperSheet.copy(alpha = 0.94f),
                    border = BorderStroke(1.dp, PaperBorder),
                    shadowElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .clickable { viewModel.setCanvasFullscreen(false) }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .testTag("canvas_fullscreen_exit"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Rounded.FullscreenExit,
                                contentDescription = "Exit full screen",
                                tint = StudioCoral,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                "Exit",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = StudioCoral
                            )
                        }
                        IconButton(
                            onClick = { viewModel.undo() },
                            enabled = state.canUndo,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("fullscreen_undo_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Undo,
                                contentDescription = "Undo",
                                tint = if (state.canUndo) InkCharcoal else InkSlate.copy(alpha = 0.3f),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.redo() },
                            enabled = state.canRedo,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("fullscreen_redo_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Redo,
                                contentDescription = "Redo",
                                tint = if (state.canRedo) InkCharcoal else InkSlate.copy(alpha = 0.3f),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.toggleHideToolsInFullscreen() },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("fullscreen_tools_toggle")
                        ) {
                            Icon(
                                if (state.hideToolsInFullscreen) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                contentDescription = if (state.hideToolsInFullscreen) "Show drawing tools" else "Hide drawing tools",
                                tint = InkSlate,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }

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
            } else if (!(state.isCanvasFullscreen && state.hideToolsInFullscreen)) {
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
                if (!state.isPlaying && state.activeSheet != StudioSheet.POSE_EDITOR) {
                    val hasRig = state.selectedLayer?.skeletonPose != null ||
                        scene.layers.any { it.skeletonPose != null }
                    Surface(
                        shape = CircleShape,
                        color = StudioMintSoft.copy(alpha = 0.96f),
                        border = BorderStroke(1.dp, StudioMint.copy(alpha = 0.45f)),
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .clickable { viewModel.openPoseEditorQuick() }
                            .testTag("studio_pose_editor_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.AccessibilityNew, contentDescription = "Open 2D Pose Editor", tint = StudioMint, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(
                                if (hasRig) "Edit Pose" else "Add 2D Rig",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = StudioMint
                            )
                        }
                    }
                }
            }
        }

        // Top-Right Zoom, View & Fullscreen HUD
        if (!(state.isCanvasFullscreen && state.hideToolsInFullscreen)) {
            Row(
                modifier = hudInsetsModifier
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
                        IconButton(
                            onClick = { viewModel.toggleCanvasFullscreen() },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("canvas_fullscreen_toggle")
                        ) {
                            Icon(
                                if (state.isCanvasFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                contentDescription = if (state.isCanvasFullscreen) "Exit full screen drawing" else "Full screen drawing",
                                tint = if (state.isCanvasFullscreen) StudioCoral else InkSlate,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }
        }

        // QuickShape Live / Snap Feedback Pill
        val activeQuickShapeLabel = holdSnappedKind?.let { "Snapped · ${it.label}" } ?: state.quickShapeBadge
        AnimatedVisibility(
            visible = activeQuickShapeLabel != null && !state.isPlaying,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = hudInsetsModifier
                .align(Alignment.TopCenter)
                .padding(top = 54.dp)
        ) {
            Box(
                modifier = Modifier
                    .shadow(3.dp, CircleShape)
                    .background(StudioLavender, CircleShape)
                    .testTag("quick_shape_feedback_pill")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        Icons.Rounded.AutoFixHigh,
                        contentDescription = null,
                        tint = PaperSheet,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        activeQuickShapeLabel.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = PaperSheet
                    )
                }
            }
        }

        // Right-Edge Compact QuickShape Toggle Button
        if (!state.isPlaying && state.activeSheet != StudioSheet.POSE_EDITOR && !(state.isCanvasFullscreen && state.hideToolsInFullscreen)) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (state.quickShapeEnabled) StudioLavenderSoft else PaperSheet.copy(alpha = 0.94f),
                border = BorderStroke(
                    width = if (state.quickShapeEnabled) 2.dp else 1.dp,
                    color = if (state.quickShapeEnabled) StudioLavender else PaperBorder
                ),
                shadowElevation = if (state.quickShapeEnabled) 4.dp else 2.dp,
                modifier = hudInsetsModifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp)
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clickable { viewModel.toggleQuickShape() }
                    .semantics {
                        contentDescription = if (state.quickShapeEnabled) {
                            "QuickShape auto-snap is ON. Tap to turn off."
                        } else {
                            "QuickShape auto-snap is OFF. Tap to turn on."
                        }
                    }
                    .testTag("canvas_quick_shape_toggle")
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(
                                    color = if (state.quickShapeEnabled) StudioLavender else InkSlate.copy(alpha = 0.35f),
                                    shape = CircleShape
                                )
                        )
                        Icon(
                            Icons.Rounded.AutoFixHigh,
                            contentDescription = null,
                            tint = if (state.quickShapeEnabled) StudioLavender else InkSlate,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        "Snap",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (state.quickShapeEnabled) FontWeight.Bold else FontWeight.Medium,
                        color = if (state.quickShapeEnabled) StudioLavender else InkCharcoal
                    )
                    Text(
                        if (state.quickShapeEnabled) "ON" else "OFF",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (state.quickShapeEnabled) StudioLavender else InkSlate
                    )
                }
            }
        }

        // Contextual Selection Action Pill (only shown when something is selected!)
        if (state.hasAnySelection && !state.isPlaying && state.activeSheet != StudioSheet.POSE_EDITOR) {
            val activeGroup = state.activeGroupForSelection()
            val canAttachRig = state.hasAnySelection
            val attachedBoneLabel = remember(scene.layers, state.selectedShapeIds, state.selectedTextIds, state.selectedStrokeIds) {
                val attachments = buildList {
                    scene.layers.forEach { l ->
                        l.sceneShapes.filter { it.id in state.selectedShapeIds }.mapNotNullTo(this) { it.rigAttachment }
                        l.sceneTexts.filter { it.id in state.selectedTextIds }.mapNotNullTo(this) { it.rigAttachment }
                        l.sceneStrokes.filter { it.id in state.selectedStrokeIds }.mapNotNullTo(this) { it.rigAttachment }
                    }
                }
                if (attachments.isEmpty()) null else {
                    val distinct = attachments.map { RigEngine.displayBoneLabel(it) }.distinct()
                    if (distinct.size == 1) distinct.first() else "${distinct.size} bones"
                }
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = PaperSheet,
                border = BorderStroke(1.dp, StudioLavender),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 8.dp, vertical = 12.dp)
                    .testTag("selection_context_bar")
            ) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Multi-select toggle
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (state.multiSelectMode) StudioLavenderSoft else PaperLinen,
                        border = if (state.multiSelectMode) BorderStroke(1.dp, StudioLavender) else null,
                        modifier = Modifier
                            .clickable { viewModel.toggleMultiSelectMode() }
                            .testTag("selection_multi_toggle")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                if (state.multiSelectMode) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                                contentDescription = "Toggle multi-select",
                                tint = if (state.multiSelectMode) StudioLavender else InkSlate,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                if (state.selectionCount > 1) "Multi (${state.selectionCount})" else "Multi",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (state.multiSelectMode) FontWeight.Bold else FontWeight.Medium,
                                color = if (state.multiSelectMode) StudioLavender else InkCharcoal
                            )
                        }
                    }

                    // Group / Ungroup actions
                    if (activeGroup != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = StudioMintSoft,
                            modifier = Modifier
                                .clickable { viewModel.ungroupSelection() }
                                .testTag("selection_ungroup_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Rounded.Workspaces, contentDescription = null, tint = StudioMint, modifier = Modifier.size(15.dp))
                                Text("Ungroup", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = StudioMint)
                            }
                        }
                    }
                    if (state.selectionCount >= 2 && activeGroup == null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = StudioMintSoft,
                            modifier = Modifier
                                .clickable { viewModel.groupSelection() }
                                .testTag("selection_group_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Rounded.SelectAll, contentDescription = null, tint = StudioMint, modifier = Modifier.size(15.dp))
                                Text("Group", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = StudioMint)
                            }
                        }
                    }

                    // Attach to 2D Skeleton Rig button
                    if (canAttachRig) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (attachedBoneLabel != null) StudioLavenderSoft else PaperLinen,
                            border = BorderStroke(1.dp, if (attachedBoneLabel != null) StudioLavender else PaperBorder),
                            modifier = Modifier
                                .clickable { showAttachRigDialog = true }
                                .testTag("selection_attach_rig_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Link,
                                    contentDescription = "Attach selection to 2D skeleton rig",
                                    tint = StudioLavender,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    if (attachedBoneLabel != null) "Rig: $attachedBoneLabel" else "Attach Rig",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = StudioLavender
                                )
                            }
                        }
                    }

                    IconButton(onClick = { viewModel.transformSelection(scaleFactor = 0.85f) }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Rounded.ZoomOut, contentDescription = "Shrink selected", tint = InkCharcoal)
                    }
                    IconButton(onClick = { viewModel.transformSelection(scaleFactor = 1.18f) }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Rounded.ZoomIn, contentDescription = "Grow selected", tint = InkCharcoal)
                    }
                    IconButton(onClick = { viewModel.transformSelection(rotationDeltaDeg = 15f) }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Rounded.RotateRight, contentDescription = "Rotate selected", tint = InkCharcoal)
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = StudioLavenderSoft,
                        modifier = Modifier
                            .clickable { viewModel.openSheet(StudioSheet.MOTION_ACTION) }
                            .padding(horizontal = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = StudioLavender, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Animate", style = MaterialTheme.typography.labelMedium, color = StudioLavender)
                        }
                    }
                    TextButton(onClick = { viewModel.openSheet(StudioSheet.AI_INSPECTOR) }) {
                        Text("Describe", color = StudioMint)
                    }
                    IconButton(onClick = { viewModel.deleteSelection() }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete selected", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (showAttachRigDialog) {
        AttachToRigDialog(
            state = state,
            onDismiss = { showAttachRigDialog = false },
            onAttachPreset = { preset, rigLayerId ->
                viewModel.attachSelectionToRig(preset, rigLayerId)
                showAttachRigDialog = false
            },
            onDetach = {
                viewModel.detachSelectionFromRig()
                showAttachRigDialog = false
            }
        )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AttachToRigDialog(
    state: StudioUiState,
    onDismiss: () -> Unit,
    onAttachPreset: (RigBonePreset, String) -> Unit,
    onDetach: () -> Unit
) {
    val scene = state.activeScene ?: return
    val rigCandidateLayers = remember(scene.layers) {
        val withRig = scene.orderedLayers().filter { it.skeletonPose != null }
        if (withRig.isNotEmpty()) withRig else listOfNotNull(state.selectedLayer)
    }
    var targetRigLayerId by remember(rigCandidateLayers) {
        mutableStateOf(
            rigCandidateLayers.firstOrNull { it.id == state.selectedLayerId }?.id
                ?: rigCandidateLayers.firstOrNull()?.id
                ?: state.selectedLayerId
        )
    }
    val hasAnyAttached = remember(scene.layers, state.selectedShapeIds, state.selectedTextIds, state.selectedStrokeIds) {
        scene.layers.any { l ->
            l.sceneShapes.any { it.id in state.selectedShapeIds && it.rigAttachment != null } ||
                l.sceneTexts.any { it.id in state.selectedTextIds && it.rigAttachment != null } ||
                l.sceneStrokes.any { it.id in state.selectedStrokeIds && it.rigAttachment != null }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Attach ${state.selectionCount} Element(s) to 2D Rig")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Attached shapes, strokes & text bend, move, and scale automatically whenever you pose the skeleton joints.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSlate
                )
                if (rigCandidateLayers.size > 1) {
                    Text("Target Skeleton Rig", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rigCandidateLayers.forEach { layer ->
                            FilterChip(
                                selected = targetRigLayerId == layer.id,
                                onClick = { targetRigLayerId = layer.id },
                                label = { Text(layer.name) }
                            )
                        }
                    }
                } else {
                    val targetName = rigCandidateLayers.firstOrNull()?.name ?: "Selected Layer"
                    val hasPose = rigCandidateLayers.firstOrNull()?.skeletonPose != null
                    Text(
                        if (hasPose) "Binding to skeleton on '$targetName'" else "Creates a 2D skeleton rig on '$targetName' and binds selection",
                        style = MaterialTheme.typography.labelMedium,
                        color = StudioLavender
                    )
                }

                Text("Pick Skeleton Part / Bone", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RigBonePreset.entries.forEach { preset ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = PaperLinen,
                            border = BorderStroke(1.dp, PaperBorder),
                            modifier = Modifier
                                .clickable { onAttachPreset(preset, targetRigLayerId) }
                                .testTag("pose_rig_preset_${preset.id}")
                        ) {
                            Text(
                                preset.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = InkCharcoal,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = {
            if (hasAnyAttached) {
                TextButton(
                    onClick = onDetach,
                    modifier = Modifier.testTag("rig_detach_button")
                ) {
                    Icon(Icons.Rounded.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(4.dp))
                    Text("Detach Rig", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    )
}
