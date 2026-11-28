package com.example.ui.studio

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ProjectStore
import com.example.data.SampleProjects
import com.example.domain.model.AnimationFrame
import com.example.domain.model.AudioPlan
import com.example.domain.model.BackgroundSpec
import com.example.domain.model.BackgroundType
import com.example.domain.model.CameraKeyframe
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
import com.example.domain.model.StudioMode
import com.example.domain.model.TextAnimationStyle
import com.example.domain.model.defaultStickmanJoints
import com.example.engine.CanvasTransformEngine
import com.example.engine.CanvasViewportState
import com.example.engine.QuickShapeDetector
import com.example.engine.QuickShapeKind
import com.example.engine.RigBonePreset
import com.example.engine.RigEngine
import com.example.engine.SpatialMapGenerator
import com.example.engine.TimelineEngine
import com.example.engine.UndoRedoManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object PoseGeometry {
    private val defaultOriginJoints by lazy { defaultStickmanJoints(0f, 0f) }
    private val defaultRmsRadius: Float by lazy { computeRmsRadius(defaultOriginJoints).coerceAtLeast(1f) }

    fun computeCenter(joints: Map<String, JointPoint>): Pair<Float, Float> {
        if (joints.isEmpty()) return 0f to 0f
        val minX = joints.values.minOf { it.x }
        val maxX = joints.values.maxOf { it.x }
        val minY = joints.values.minOf { it.y }
        val maxY = joints.values.maxOf { it.y }
        return (minX + maxX) / 2f to (minY + maxY) / 2f
    }

    fun computeRmsRadius(joints: Map<String, JointPoint>): Float {
        if (joints.isEmpty()) return 1f
        val meanX = joints.values.sumOf { it.x.toDouble() } / joints.size
        val meanY = joints.values.sumOf { it.y.toDouble() } / joints.size
        var sumSq = 0.0
        joints.values.forEach { pt ->
            val dx = pt.x.toDouble() - meanX
            val dy = pt.y.toDouble() - meanY
            sumSq += dx * dx + dy * dy
        }
        return sqrt(sumSq / joints.size).toFloat().coerceAtLeast(1f)
    }

    fun computeScale(joints: Map<String, JointPoint>): Float {
        if (joints.isEmpty()) return 1f
        return (computeRmsRadius(joints) / defaultRmsRadius).coerceIn(0.25f, 3.0f)
    }

    fun computeAngleDeg(joints: Map<String, JointPoint>): Float {
        val bottom = joints["hip"] ?: joints["chest"] ?: return 0f
        val top = joints["head"] ?: joints["neck"] ?: return 0f
        val dx = top.x - bottom.x
        val dy = top.y - bottom.y
        if (hypot(dx, dy) < 1e-3f) return 0f
        return normalizeAngleDeg(Math.toDegrees(atan2(dx.toDouble(), (-dy).toDouble())).toFloat())
    }

    fun normalizeAngleDeg(deg: Float): Float {
        var d = deg % 360f
        if (d > 180f) d -= 360f
        if (d <= -180f) d += 360f
        return d
    }

    fun transformJointsFromAnchor(
        anchorJoints: Map<String, JointPoint>,
        centerX: Float,
        centerY: Float,
        scaleRatio: Float,
        deltaAngleDeg: Float
    ): Map<String, JointPoint> {
        val rad = Math.toRadians(deltaAngleDeg.toDouble())
        val cosA = cos(rad).toFloat()
        val sinA = sin(rad).toFloat()
        return anchorJoints.mapValues { (_, pt) ->
            val rx = (pt.x - centerX) * scaleRatio
            val ry = (pt.y - centerY) * scaleRatio
            JointPoint(
                x = centerX + rx * cosA - ry * sinA,
                y = centerY + rx * sinA + ry * cosA
            )
        }
    }
}

private data class PoseTransformAnchor(
    val layerId: String,
    val frameId: String?,
    val baseJoints: Map<String, JointPoint>,
    val centerX: Float,
    val centerY: Float,
    val baseScale: Float,
    val baseAngleDeg: Float,
    val currentScale: Float = baseScale,
    val currentAngleDeg: Float = baseAngleDeg
)

enum class SaveStatus(val label: String) {
    SAVED("Saved"),
    SAVING("Saving..."),
    DIRTY("Unsaved")
}

enum class StudioSheet {
    NONE,
    LAYERS,
    BACKGROUND,
    AI_INSPECTOR,
    MOTION_ACTION,
    SCENE_PLANNER,
    COLOR_PICKER,
    POSE_EDITOR
}

data class StudioUiState(
    val isLoading: Boolean = true,
    val project: FramePilotProject? = null,
    val activeSceneId: String = "",
    val activeMode: StudioMode = StudioMode.SCENE_LAYOUT,
    val selectedLayerId: String = "",
    val selectedFrameId: String = "",
    val selectedShapeIds: Set<String> = emptySet(),
    val selectedTextIds: Set<String> = emptySet(),
    val selectedStrokeIds: Set<String> = emptySet(),
    val multiSelectMode: Boolean = false,
    val selectedMotionTrackId: String? = null,
    val activeTool: DrawingTool = DrawingTool.PEN,
    val brushColorHex: String = "#22242C",
    val brushSize: Float = 10f,
    val brushOpacity: Float = 1f,
    val viewport: CanvasViewportState = CanvasViewportState(),
    val hasCustomViewport: Boolean = false,
    val isCanvasFullscreen: Boolean = false,
    val hideToolsInFullscreen: Boolean = false,
    val showGrid: Boolean = false,
    val showSafeArea: Boolean = true,
    val onionSkinEnabled: Boolean = true,
    val quickShapeEnabled: Boolean = false,
    val quickShapeBadge: String? = null,
    val isPlaying: Boolean = false,
    val loopPlayback: Boolean = true,
    val reversePlayback: Boolean = false,
    val connectedScenesMode: Boolean = false,
    val playheadTimeMs: Long = 0L,
    val timelineExpanded: Boolean = true,
    val advancedMode: Boolean = false,
    val isRecordingPathForTrackId: String? = null,
    val saveStatus: SaveStatus = SaveStatus.SAVED,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val activeSheet: StudioSheet = StudioSheet.NONE,
    val poseEditWholeBody: Boolean = false,
    val selectedPoseJoint: String = "rightHand",
    val tutorialStep: Int? = null,
    val snackbarMessage: String? = null
) {
    val activeScene: Scene?
        get() = project?.scenes?.find { it.id == activeSceneId } ?: project?.scenes?.firstOrNull()

    val selectedLayer: Layer?
        get() = activeScene?.layers?.find { it.id == selectedLayerId } ?: activeScene?.orderedLayers()?.lastOrNull()

    val selectedFrame: AnimationFrame?
        get() = activeScene?.frames?.find { it.id == selectedFrameId } ?: activeScene?.orderedFrames()?.firstOrNull()

    val selectedShapeId: String?
        get() = selectedShapeIds.firstOrNull()

    val selectedTextId: String?
        get() = selectedTextIds.firstOrNull()

    val hasAnySelection: Boolean
        get() = selectedShapeIds.isNotEmpty() || selectedTextIds.isNotEmpty() || selectedStrokeIds.isNotEmpty()

    val selectionCount: Int
        get() = selectedShapeIds.size + selectedTextIds.size + selectedStrokeIds.size

    val selectedShape: SceneShape?
        get() = selectedShapeId?.let { id ->
            activeScene?.layers?.flatMap { it.sceneShapes }?.find { it.id == id }
                ?: selectedFrame?.layerShapes?.values?.flatten()?.find { it.id == id }
        }

    val selectedText: SceneText?
        get() = selectedTextId?.let { id ->
            activeScene?.layers?.flatMap { it.sceneTexts }?.find { it.id == id }
        }

    val selectedMotionTrack: MotionTrack?
        get() = selectedMotionTrackId?.let { id ->
            activeScene?.motionTracks?.find { it.id == id }
        }

    fun orderedScenes(): List<Scene> =
        project?.scenes?.sortedBy { it.orderIndex }.orEmpty()

    fun sceneStartOffsetMs(sceneId: String): Long {
        val proj = project ?: return 0L
        val fps = proj.fps.coerceAtLeast(1)
        var acc = 0L
        for (sc in orderedScenes()) {
            if (sc.id == sceneId) return acc
            acc += sc.effectiveDurationMs(fps)
        }
        return acc
    }

    fun globalPlayheadTimeMs(): Long {
        val scId = activeScene?.id.orEmpty()
        return sceneStartOffsetMs(scId) + playheadTimeMs
    }

    fun totalReelDurationMs(): Long {
        val proj = project ?: return 0L
        val fps = proj.fps.coerceAtLeast(1)
        return proj.scenes.sumOf { it.effectiveDurationMs(fps) }.coerceAtLeast(500L)
    }

    fun activePoseFor(layer: Layer?): SkeletonPose? {
        layer ?: return null
        return selectedFrame?.layerPoses?.get(layer.id) ?: layer.skeletonPose
    }

    fun activeGroupForSelection(): ElementGroup? {
        val scene = activeScene ?: return null
        val groupIds = buildSet {
            scene.layers.forEach { layer ->
                layer.sceneShapes.filter { it.id in selectedShapeIds && it.groupId != null }.forEach { add(it.groupId!!) }
                layer.sceneTexts.filter { it.id in selectedTextIds && it.groupId != null }.forEach { add(it.groupId!!) }
                layer.sceneStrokes.filter { it.id in selectedStrokeIds && it.groupId != null }.forEach { add(it.groupId!!) }
            }
        }
        val singleGroupId = groupIds.singleOrNull() ?: return null
        return scene.layers.flatMap { it.groups }.find { it.id == singleGroupId }
    }
}

class StudioViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ProjectStore(application)
    private val undoRedo = UndoRedoManager()
    private var autosaveJob: Job? = null
    private var playbackJob: Job? = null
    private var quickShapeBadgeJob: Job? = null

    private val _uiState = MutableStateFlow(StudioUiState())
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    private fun preferredLayerForScene(scene: Scene?): Layer? {
        scene ?: return null
        val ordered = scene.orderedLayers()
        return ordered.lastOrNull { it.skeletonPose != null }
            ?: ordered.lastOrNull { it.category == LayerCategory.CHARACTER }
            ?: ordered.lastOrNull()
    }

    fun loadProject(projectId: String) {
        viewModelScope.launch {
            undoRedo.clear()
            _uiState.update { it.copy(isLoading = true) }
            val loaded = store.loadProject(projectId)
                ?: SampleProjects.createAfterGraduationReelSample().also { store.saveProject(it) }
            val firstScene = loaded.scenes.sortedBy { it.orderIndex }.firstOrNull()
            val firstLayer = preferredLayerForScene(firstScene)
            val firstFrame = firstScene?.orderedFrames()?.firstOrNull()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    project = loaded,
                    activeSceneId = firstScene?.id ?: "",
                    selectedLayerId = firstLayer?.id ?: "",
                    selectedFrameId = firstFrame?.id ?: "",
                    selectedShapeIds = emptySet(),
                    selectedTextIds = emptySet(),
                    selectedStrokeIds = emptySet(),
                    multiSelectMode = false,
                    hasCustomViewport = false,
                    isCanvasFullscreen = false,
                    hideToolsInFullscreen = false,
                    showSafeArea = loaded.aspectRatioLabel == "9:16",
                    canUndo = false,
                    canRedo = false,
                    saveStatus = SaveStatus.SAVED,
                    tutorialStep = if (!loaded.tutorialCompleted) 0 else null
                )
            }
        }
    }

    private fun mutateProject(
        recordUndo: Boolean = true,
        transform: (FramePilotProject) -> FramePilotProject
    ) {
        val current = _uiState.value.project ?: return
        if (recordUndo) undoRedo.recordBeforeChange(current)
        val updated = transform(current)
        _uiState.update {
            it.copy(
                project = updated,
                canUndo = undoRedo.canUndo,
                canRedo = undoRedo.canRedo,
                saveStatus = SaveStatus.DIRTY
            )
        }
        scheduleAutosave()
    }

    private fun mutateActiveScene(
        recordUndo: Boolean = true,
        transform: (Scene) -> Scene
    ) {
        val sceneId = _uiState.value.activeScene?.id ?: return
        mutateProject(recordUndo = recordUndo) { proj ->
            proj.copy(scenes = proj.scenes.map { sc -> if (sc.id == sceneId) transform(sc) else sc })
        }
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(700L)
            flushSaveInternal()
        }
    }

    fun flushSave() {
        autosaveJob?.cancel()
        autosaveJob = null
        if (_uiState.value.saveStatus != SaveStatus.DIRTY) return
        viewModelScope.launch { flushSaveInternal() }
    }

    private suspend fun flushSaveInternal() {
        val proj = _uiState.value.project ?: return
        _uiState.update { it.copy(saveStatus = SaveStatus.SAVING) }
        val saved = store.saveProject(proj)
        _uiState.update {
            if (it.project?.id == saved.id) {
                it.copy(saveStatus = SaveStatus.SAVED)
            } else it
        }
    }

    private var poseTransformAnchor: PoseTransformAnchor? = null

    fun undo() {
        poseTransformAnchor = null
        val current = _uiState.value.project ?: return
        val prev = undoRedo.undo(current) ?: return
        restoreProjectSnapshot(prev)
    }

    fun redo() {
        poseTransformAnchor = null
        val current = _uiState.value.project ?: return
        val next = undoRedo.redo(current) ?: return
        restoreProjectSnapshot(next)
    }

    private fun restoreProjectSnapshot(snapshot: FramePilotProject) {
        val scene = snapshot.scenes.find { it.id == _uiState.value.activeSceneId } ?: snapshot.scenes.firstOrNull()
        val orderedFrames = scene?.orderedFrames().orEmpty()
        val frameId = orderedFrames.find { it.id == _uiState.value.selectedFrameId }?.id
            ?: orderedFrames.firstOrNull()?.id.orEmpty()
        val layerId = scene?.layers?.find { it.id == _uiState.value.selectedLayerId }?.id
            ?: preferredLayerForScene(scene)?.id.orEmpty()

        _uiState.update {
            it.copy(
                project = snapshot,
                activeSceneId = scene?.id.orEmpty(),
                selectedFrameId = frameId,
                selectedLayerId = layerId,
                canUndo = undoRedo.canUndo,
                canRedo = undoRedo.canRedo,
                saveStatus = SaveStatus.DIRTY
            )
        }
        scheduleAutosave()
    }

    // --- Mode, Tool & Viewport ---

    fun setMode(mode: StudioMode) {
        _uiState.update {
            it.copy(
                activeMode = mode,
                isRecordingPathForTrackId = null
            )
        }
    }

    fun setTool(tool: DrawingTool) {
        val keepSel = tool == DrawingTool.SELECT || tool == DrawingTool.LASSO
        _uiState.update {
            it.copy(
                activeTool = tool,
                isRecordingPathForTrackId = null,
                selectedShapeIds = if (keepSel) it.selectedShapeIds else emptySet(),
                selectedTextIds = if (keepSel) it.selectedTextIds else emptySet(),
                selectedStrokeIds = if (keepSel) it.selectedStrokeIds else emptySet(),
                multiSelectMode = if (keepSel) it.multiSelectMode else false
            )
        }
    }

    fun toggleMultiSelectMode() {
        _uiState.update { it.copy(multiSelectMode = !it.multiSelectMode) }
    }

    fun setBrushColor(hex: String) {
        _uiState.update { it.copy(brushColorHex = hex) }
    }

    fun setBrushSize(size: Float) {
        _uiState.update { it.copy(brushSize = size.coerceIn(2f, 64f)) }
    }

    fun setBrushOpacity(opacity: Float) {
        _uiState.update { it.copy(brushOpacity = opacity.coerceIn(0.1f, 1f)) }
    }

    fun updateViewport(viewport: CanvasViewportState) {
        _uiState.update { it.copy(viewport = viewport, hasCustomViewport = true) }
    }

    fun fitCanvasToViewport(viewWidth: Float, viewHeight: Float) {
        val st = _uiState.value
        val proj = st.project ?: return
        val fitted = CanvasTransformEngine.fitToViewport(
            docWidth = proj.canvasWidth,
            docHeight = proj.canvasHeight,
            viewportWidth = viewWidth,
            viewportHeight = viewHeight,
            paddingPx = if (st.isCanvasFullscreen) 6f else 12f
        )
        _uiState.update { it.copy(viewport = fitted, hasCustomViewport = false) }
    }

    fun toggleCanvasFullscreen() {
        _uiState.update { st ->
            val next = !st.isCanvasFullscreen
            st.copy(
                isCanvasFullscreen = next,
                hideToolsInFullscreen = if (!next) false else st.hideToolsInFullscreen,
                hasCustomViewport = false
            )
        }
    }

    fun setCanvasFullscreen(enabled: Boolean) {
        _uiState.update { st ->
            st.copy(
                isCanvasFullscreen = enabled,
                hideToolsInFullscreen = if (!enabled) false else st.hideToolsInFullscreen,
                hasCustomViewport = false
            )
        }
    }

    fun toggleHideToolsInFullscreen() {
        _uiState.update { it.copy(hideToolsInFullscreen = !it.hideToolsInFullscreen) }
    }

    fun toggleGrid() {
        _uiState.update { it.copy(showGrid = !it.showGrid) }
    }

    fun toggleSafeArea() {
        _uiState.update { it.copy(showSafeArea = !it.showSafeArea) }
    }

    fun toggleOnionSkin() {
        _uiState.update { it.copy(onionSkinEnabled = !it.onionSkinEnabled) }
    }

    fun toggleQuickShape() {
        val next = !_uiState.value.quickShapeEnabled
        quickShapeBadgeJob?.cancel()
        _uiState.update {
            it.copy(
                quickShapeEnabled = next,
                quickShapeBadge = if (next) "QuickShape Ready" else null
            )
        }
        if (next) {
            quickShapeBadgeJob = viewModelScope.launch {
                delay(1400L)
                _uiState.update { it.copy(quickShapeBadge = null) }
            }
        }
    }

    private fun showQuickShapeBadge(label: String) {
        quickShapeBadgeJob?.cancel()
        _uiState.update { it.copy(quickShapeBadge = label) }
        quickShapeBadgeJob = viewModelScope.launch {
            delay(1500L)
            _uiState.update { it.copy(quickShapeBadge = null) }
        }
    }

    fun toggleTimelineExpanded() {
        _uiState.update { it.copy(timelineExpanded = !it.timelineExpanded) }
    }

    fun toggleAdvancedMode() {
        val next = !_uiState.value.advancedMode
        _uiState.update { it.copy(advancedMode = next) }
        viewModelScope.launch { store.setAdvancedModeDefault(next) }
    }

    fun openSheet(sheet: StudioSheet) {
        _uiState.update { it.copy(activeSheet = sheet) }
    }

    fun closeSheet() {
        poseTransformAnchor = null
        _uiState.update { it.copy(activeSheet = StudioSheet.NONE) }
    }

    fun renameProject(newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        mutateProject { it.copy(name = trimmed) }
    }

    // --- Drawing, Erasing, Shapes, Fill, Text, Select ---

    fun addCompletedStroke(
        rawPoints: List<StrokePoint>,
        preSnappedKind: QuickShapeKind? = null
    ) {
        if (rawPoints.isEmpty()) return
        val state = _uiState.value
        val proj = state.project ?: return
        val layer = state.selectedLayer ?: return
        if (layer.locked) {
            showToast("Layer '${layer.name}' is locked")
            return
        }

        // Recording a motion path directly on the canvas!
        val recordingTrackId = state.isRecordingPathForTrackId
        if (recordingTrackId != null) {
            val motionPts = rawPoints.map { MotionPathPoint(it.x, it.y) }
            mutateActiveScene { scene ->
                scene.copy(
                    motionTracks = scene.motionTracks.map { tr ->
                        if (tr.id == recordingTrackId) tr.copy(pathPoints = motionPts) else tr
                    }
                )
            }
            _uiState.update { it.copy(isRecordingPathForTrackId = null, snackbarMessage = "Motion path recorded!") }
            return
        }

        val isFreehandBrush = state.activeTool in listOf(DrawingTool.PENCIL, DrawingTool.PEN, DrawingTool.BRUSH)
        val finalPoints = if (preSnappedKind != null) {
            showQuickShapeBadge("Snapped · ${preSnappedKind.label}")
            rawPoints
        } else if (state.quickShapeEnabled && isFreehandBrush) {
            val snapped = QuickShapeDetector.detectAndSnap(rawPoints, proj.canvasWidth, proj.canvasHeight)
            if (snapped != null) {
                showQuickShapeBadge("Snapped · ${snapped.kind.label}")
                snapped.snappedPoints
            } else {
                rawPoints
            }
        } else {
            rawPoints
        }

        val stroke = DrawingStroke(
            id = UUID.randomUUID().toString(),
            layerId = layer.id,
            tool = state.activeTool,
            colorHex = state.brushColorHex,
            strokeWidth = state.brushSize,
            opacity = state.brushOpacity,
            points = finalPoints
        )

        val drawToFrame = state.activeMode == StudioMode.DRAW_FRAMES
        mutateActiveScene { scene ->
            if (drawToFrame) {
                val targetFrameId = state.selectedFrame?.id ?: scene.orderedFrames().firstOrNull()?.id
                scene.copy(
                    frames = scene.frames.map { frame ->
                        if (frame.id == targetFrameId) {
                            val existing = frame.layerDrawings[layer.id].orEmpty()
                            frame.copy(layerDrawings = frame.layerDrawings + (layer.id to (existing + stroke)))
                        } else frame
                    }
                )
            } else {
                scene.copy(
                    layers = scene.layers.map { l ->
                        if (l.id == layer.id) l.copy(sceneStrokes = l.sceneStrokes + stroke) else l
                    }
                )
            }
        }
    }

    fun eraseAt(docX: Float, docY: Float, recordUndo: Boolean = true): Boolean {
        val state = _uiState.value
        val layer = state.selectedLayer ?: return false
        if (layer.locked) return false
        val radius = (state.brushSize * 1.8f).coerceAtLeast(18f)

        val drawToFrame = state.activeMode == StudioMode.DRAW_FRAMES
        val hasHit = if (drawToFrame) {
            state.selectedFrame?.layerDrawings?.get(layer.id).orEmpty().any {
                CanvasTransformEngine.hitTestStroke(it, docX, docY, radius)
            }
        } else {
            layer.sceneStrokes.any { CanvasTransformEngine.hitTestStroke(it, docX, docY, radius) }
        }
        if (!hasHit) return false

        mutateActiveScene(recordUndo = recordUndo) { scene ->
            if (drawToFrame) {
                val frameId = state.selectedFrame?.id
                scene.copy(
                    frames = scene.frames.map { f ->
                        if (f.id == frameId) {
                            val updated = CanvasTransformEngine.eraseAtPoint(f.layerDrawings[layer.id].orEmpty(), docX, docY, radius)
                            f.copy(layerDrawings = f.layerDrawings + (layer.id to updated))
                        } else f
                    }
                )
            } else {
                scene.copy(
                    layers = scene.layers.map { l ->
                        if (l.id == layer.id) {
                            l.copy(sceneStrokes = CanvasTransformEngine.eraseAtPoint(l.sceneStrokes, docX, docY, radius))
                        } else l
                    }
                )
            }
        }
        return true
    }

    fun addShape(
        shapeType: ShapeType,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float
    ) {
        val state = _uiState.value
        val layer = state.selectedLayer ?: return
        if (layer.locked) {
            showToast("Layer '${layer.name}' is locked")
            return
        }
        val width = if (shapeType == ShapeType.LINE || shapeType == ShapeType.ARROW) {
            endX - startX
        } else {
            abs(endX - startX).coerceAtLeast(24f)
        }
        val height = if (shapeType == ShapeType.LINE || shapeType == ShapeType.ARROW) {
            endY - startY
        } else {
            abs(endY - startY).coerceAtLeast(24f)
        }
        val x = if (shapeType == ShapeType.LINE || shapeType == ShapeType.ARROW) startX else minOf(startX, endX)
        val y = if (shapeType == ShapeType.LINE || shapeType == ShapeType.ARROW) startY else minOf(startY, endY)

        val shape = SceneShape(
            id = UUID.randomUUID().toString(),
            layerId = layer.id,
            shapeType = shapeType,
            x = x,
            y = y,
            width = width,
            height = height,
            strokeColorHex = state.brushColorHex,
            fillColorHex = if (shapeType == ShapeType.RECTANGLE || shapeType == ShapeType.ELLIPSE) state.brushColorHex else null,
            strokeWidth = state.brushSize.coerceAtLeast(4f),
            opacity = state.brushOpacity,
            filled = false
        )

        val drawToFrame = state.activeMode == StudioMode.DRAW_FRAMES
        mutateActiveScene { scene ->
            if (drawToFrame) {
                val frameId = state.selectedFrame?.id
                scene.copy(
                    frames = scene.frames.map { f ->
                        if (f.id == frameId) {
                            val existing = f.layerShapes[layer.id].orEmpty()
                            f.copy(layerShapes = f.layerShapes + (layer.id to (existing + shape)))
                        } else f
                    }
                )
            } else {
                scene.copy(
                    layers = scene.layers.map { l ->
                        if (l.id == layer.id) l.copy(sceneShapes = l.sceneShapes + shape) else l
                    }
                )
            }
        }
        _uiState.update {
            it.copy(
                selectedShapeIds = setOf(shape.id),
                selectedTextIds = emptySet(),
                selectedStrokeIds = emptySet()
            )
        }
    }

    fun addTextElement(text: String, docX: Float, docY: Float) {
        if (text.isBlank()) return
        val state = _uiState.value
        val layer = state.selectedLayer ?: return
        if (layer.locked) return
        val item = SceneText(
            id = UUID.randomUUID().toString(),
            layerId = layer.id,
            text = text.trim(),
            x = docX,
            y = docY,
            fontSizeSp = (state.brushSize * 4.5f).coerceIn(28f, 96f),
            colorHex = state.brushColorHex,
            opacity = state.brushOpacity,
            semantic = SemanticInfo(whatIsIt = "Text: ${text.trim()}")
        )
        mutateActiveScene { scene ->
            scene.copy(
                layers = scene.layers.map { l ->
                    if (l.id == layer.id) l.copy(sceneTexts = l.sceneTexts + item) else l
                }
            )
        }
        _uiState.update {
            it.copy(
                selectedTextIds = setOf(item.id),
                selectedShapeIds = emptySet(),
                selectedStrokeIds = emptySet()
            )
        }
    }

    fun handleFillTap(docX: Float, docY: Float) {
        val state = _uiState.value
        val proj = state.project ?: return
        val scene = state.activeScene ?: return
        val fillColor = state.brushColorHex
        val activePoses = RigEngine.buildActivePosesByLayer(scene, state.selectedFrame)

        // 1. Hit-test shapes top-down
        val hitShape = scene.orderedLayers().reversed()
            .filter { it.visible && !it.locked }
            .flatMap { layer ->
                layer.sceneShapes.reversed().map { sh ->
                    val targetPose = RigEngine.resolveTargetPose(sh.rigAttachment, layer.id, activePoses)
                    sh to RigEngine.deformShape(sh, targetPose)
                }
            }
            .firstOrNull { (_, deformed) -> CanvasTransformEngine.hitTestShape(deformed, docX, docY) }
            ?.first

        if (hitShape != null) {
            mutateActiveScene { sc ->
                sc.copy(
                    layers = sc.layers.map { l ->
                        l.copy(
                            sceneShapes = l.sceneShapes.map { s ->
                                if (s.id == hitShape.id) s.copy(filled = true, fillColorHex = fillColor) else s
                            }
                        )
                    }
                )
            }
            return
        }

        // 2. Hit-test closed drawn loops
        val activeLayer = state.selectedLayer
        if (activeLayer != null && !activeLayer.locked) {
            val closedStroke = activeLayer.sceneStrokes.reversed().firstOrNull { stroke ->
                val targetPose = RigEngine.resolveTargetPose(stroke.rigAttachment, activeLayer.id, activePoses)
                val deformed = RigEngine.deformStroke(stroke, targetPose, proj.canvasWidth, proj.canvasHeight)
                if (deformed.points.size < 4) return@firstOrNull false
                val first = deformed.points.first()
                val last = deformed.points.last()
                val isClosed = hypot(first.x - last.x, first.y - last.y) < 120f
                isClosed && CanvasTransformEngine.pointInPolygon(docX, docY, deformed.points.map { it.x to it.y })
            }
            if (closedStroke != null) {
                mutateActiveScene { sc ->
                    sc.copy(
                        layers = sc.layers.map { l ->
                            if (l.id == activeLayer.id) {
                                l.copy(
                                    sceneStrokes = l.sceneStrokes.map { st ->
                                        if (st.id == closedStroke.id) st.copy(isFilledLoop = true, fillColorHex = fillColor) else st
                                    }
                                )
                            } else l
                        }
                    )
                }
                return
            }
        }

        // 3. Otherwise fill scene background primary color
        mutateActiveScene { sc ->
            sc.copy(background = sc.background.copy(primaryColorHex = fillColor))
        }
        showToast("Filled background with $fillColor")
    }

    fun handleEyedropperTap(docX: Float, docY: Float) {
        val state = _uiState.value
        val proj = state.project ?: return
        val scene = state.activeScene ?: return
        val activePoses = RigEngine.buildActivePosesByLayer(scene, state.selectedFrame)
        val layersTopDown = scene.orderedLayers().reversed().filter { it.visible }
        val hitShape = layersTopDown.flatMap { layer ->
            layer.sceneShapes.reversed().map { sh ->
                RigEngine.deformShape(sh, RigEngine.resolveTargetPose(sh.rigAttachment, layer.id, activePoses))
            }
        }.firstOrNull { CanvasTransformEngine.hitTestShape(it, docX, docY) }
        val picked = when {
            hitShape != null -> hitShape.fillColorHex ?: hitShape.strokeColorHex
            else -> {
                val hitStroke = layersTopDown.flatMap { layer ->
                    layer.sceneStrokes.reversed().map { st ->
                        RigEngine.deformStroke(
                            st,
                            RigEngine.resolveTargetPose(st.rigAttachment, layer.id, activePoses),
                            proj.canvasWidth,
                            proj.canvasHeight
                        )
                    }
                }.firstOrNull { CanvasTransformEngine.hitTestStroke(it, docX, docY) }
                hitStroke?.colorHex ?: scene.background.primaryColorHex
            }
        }
        _uiState.update { it.copy(brushColorHex = picked, snackbarMessage = "Picked color $picked") }
    }

    private fun expandSelectionWithGroups(
        scene: Scene,
        shapeIds: Set<String>,
        textIds: Set<String>,
        strokeIds: Set<String>
    ): Triple<Set<String>, Set<String>, Set<String>> {
        val groupIds = buildSet {
            scene.layers.forEach { layer ->
                layer.sceneShapes.filter { it.id in shapeIds && it.groupId != null }.forEach { add(it.groupId!!) }
                layer.sceneTexts.filter { it.id in textIds && it.groupId != null }.forEach { add(it.groupId!!) }
                layer.sceneStrokes.filter { it.id in strokeIds && it.groupId != null }.forEach { add(it.groupId!!) }
            }
        }
        if (groupIds.isEmpty()) return Triple(shapeIds, textIds, strokeIds)
        val outShapes = shapeIds.toMutableSet()
        val outTexts = textIds.toMutableSet()
        val outStrokes = strokeIds.toMutableSet()
        scene.layers.forEach { layer ->
            layer.sceneShapes.filter { it.groupId in groupIds }.forEach { outShapes += it.id }
            layer.sceneTexts.filter { it.groupId in groupIds }.forEach { outTexts += it.id }
            layer.sceneStrokes.filter { it.groupId in groupIds }.forEach { outStrokes += it.id }
        }
        return Triple(outShapes, outTexts, outStrokes)
    }

    private fun applySelectionUpdate(
        targetShapes: Set<String>,
        targetTexts: Set<String>,
        targetStrokes: Set<String>,
        hitLayerId: String?
    ) {
        _uiState.update { st ->
            if (st.multiSelectMode) {
                val allShapesPresent = targetShapes.isNotEmpty() && targetShapes.all { it in st.selectedShapeIds }
                val allTextsPresent = targetTexts.isNotEmpty() && targetTexts.all { it in st.selectedTextIds }
                val allStrokesPresent = targetStrokes.isNotEmpty() && targetStrokes.all { it in st.selectedStrokeIds }
                val toggleOff = (targetShapes.isEmpty() || allShapesPresent) &&
                    (targetTexts.isEmpty() || allTextsPresent) &&
                    (targetStrokes.isEmpty() || allStrokesPresent)
                st.copy(
                    selectedShapeIds = if (toggleOff) st.selectedShapeIds - targetShapes else st.selectedShapeIds + targetShapes,
                    selectedTextIds = if (toggleOff) st.selectedTextIds - targetTexts else st.selectedTextIds + targetTexts,
                    selectedStrokeIds = if (toggleOff) st.selectedStrokeIds - targetStrokes else st.selectedStrokeIds + targetStrokes,
                    selectedLayerId = hitLayerId ?: st.selectedLayerId
                )
            } else {
                st.copy(
                    selectedShapeIds = targetShapes,
                    selectedTextIds = targetTexts,
                    selectedStrokeIds = targetStrokes,
                    selectedLayerId = hitLayerId ?: st.selectedLayerId
                )
            }
        }
    }

    fun handleSelectTap(docX: Float, docY: Float) {
        val state = _uiState.value
        val proj = state.project ?: return
        val scene = state.activeScene ?: return
        val activePoses = RigEngine.buildActivePosesByLayer(scene, state.selectedFrame)
        val visibleLayers = scene.orderedLayers().reversed().filter { it.visible }

        val hitText = visibleLayers.flatMap { layer ->
            layer.sceneTexts.reversed().map { tx ->
                val targetPose = RigEngine.resolveTargetPose(tx.rigAttachment, layer.id, activePoses)
                tx to RigEngine.deformText(tx, targetPose)
            }
        }.firstOrNull { (_, def) -> CanvasTransformEngine.hitTestText(def, docX, docY) }?.first
        if (hitText != null) {
            val (shIds, txIds, stIds) = expandSelectionWithGroups(
                scene, emptySet(), setOf(hitText.id), emptySet()
            )
            applySelectionUpdate(shIds, txIds, stIds, hitText.layerId)
            return
        }

        val hitShape = visibleLayers.flatMap { layer ->
            layer.sceneShapes.reversed().map { sh ->
                val targetPose = RigEngine.resolveTargetPose(sh.rigAttachment, layer.id, activePoses)
                sh to RigEngine.deformShape(sh, targetPose)
            }
        }.firstOrNull { (_, def) -> CanvasTransformEngine.hitTestShape(def, docX, docY) }?.first
        if (hitShape != null) {
            val (shIds, txIds, stIds) = expandSelectionWithGroups(
                scene, setOf(hitShape.id), emptySet(), emptySet()
            )
            applySelectionUpdate(shIds, txIds, stIds, hitShape.layerId)
            return
        }

        val hitStroke = visibleLayers.flatMap { layer ->
            layer.sceneStrokes.reversed().map { st ->
                val targetPose = RigEngine.resolveTargetPose(st.rigAttachment, layer.id, activePoses)
                st to RigEngine.deformStroke(st, targetPose, proj.canvasWidth, proj.canvasHeight)
            }
        }.firstOrNull { (_, def) -> CanvasTransformEngine.hitTestStroke(def, docX, docY) }?.first
        if (hitStroke != null) {
            val (shIds, txIds, stIds) = expandSelectionWithGroups(
                scene, emptySet(), emptySet(), setOf(hitStroke.id)
            )
            applySelectionUpdate(shIds, txIds, stIds, hitStroke.layerId)
            return
        }

        if (!state.multiSelectMode) {
            _uiState.update {
                it.copy(selectedShapeIds = emptySet(), selectedTextIds = emptySet(), selectedStrokeIds = emptySet())
            }
        }
    }

    fun selectByLasso(polygon: List<Pair<Float, Float>>) {
        if (polygon.size < 3) return
        val state = _uiState.value
        val proj = state.project ?: return
        val scene = state.activeScene ?: return
        val activePoses = RigEngine.buildActivePosesByLayer(scene, state.selectedFrame)
        val candidateLayers = scene.orderedLayers().reversed().filter { it.visible && !it.locked }

        val rawStrokeIds = mutableSetOf<String>()
        val rawShapeIds = mutableSetOf<String>()
        val rawTextIds = mutableSetOf<String>()
        var firstHitLayerId: String? = null

        candidateLayers.forEach { layer ->
            layer.sceneShapes.forEach { sh ->
                val def = RigEngine.deformShape(sh, RigEngine.resolveTargetPose(sh.rigAttachment, layer.id, activePoses))
                val cx = def.x + def.width / 2f
                val cy = def.y + def.height / 2f
                if (CanvasTransformEngine.pointInPolygon(cx, cy, polygon) ||
                    CanvasTransformEngine.pointInPolygon(def.x, def.y, polygon)
                ) {
                    rawShapeIds += sh.id
                    if (firstHitLayerId == null) firstHitLayerId = layer.id
                }
            }
            layer.sceneStrokes.forEach { st ->
                val def = RigEngine.deformStroke(
                    st,
                    RigEngine.resolveTargetPose(st.rigAttachment, layer.id, activePoses),
                    proj.canvasWidth,
                    proj.canvasHeight
                )
                if (def.points.any { pt -> CanvasTransformEngine.pointInPolygon(pt.x, pt.y, polygon) }) {
                    rawStrokeIds += st.id
                    if (firstHitLayerId == null) firstHitLayerId = layer.id
                }
            }
            layer.sceneTexts.forEach { tx ->
                val def = RigEngine.deformText(tx, RigEngine.resolveTargetPose(tx.rigAttachment, layer.id, activePoses))
                if (CanvasTransformEngine.pointInPolygon(def.x, def.y, polygon)) {
                    rawTextIds += tx.id
                    if (firstHitLayerId == null) firstHitLayerId = layer.id
                }
            }
        }

        val (shIds, txIds, stIds) = expandSelectionWithGroups(scene, rawShapeIds, rawTextIds, rawStrokeIds)
        val total = shIds.size + txIds.size + stIds.size
        _uiState.update { st ->
            if (total == 0 && st.multiSelectMode) {
                st.copy(snackbarMessage = "Nothing added inside lasso")
            } else {
                val finalShapes = if (st.multiSelectMode) st.selectedShapeIds + shIds else shIds
                val finalTexts = if (st.multiSelectMode) st.selectedTextIds + txIds else txIds
                val finalStrokes = if (st.multiSelectMode) st.selectedStrokeIds + stIds else stIds
                val count = finalShapes.size + finalTexts.size + finalStrokes.size
                st.copy(
                    selectedShapeIds = finalShapes,
                    selectedTextIds = finalTexts,
                    selectedStrokeIds = finalStrokes,
                    selectedLayerId = firstHitLayerId ?: st.selectedLayerId,
                    snackbarMessage = if (count > 0) "Selected $count item(s)" else "Nothing inside lasso"
                )
            }
        }
    }

    fun moveSelectedBy(dx: Float, dy: Float, recordUndo: Boolean = true) {
        val state = _uiState.value
        val shapeIds = state.selectedShapeIds
        val textIds = state.selectedTextIds
        val strokeIds = state.selectedStrokeIds
        val proj = state.project ?: return
        val curScene = state.activeScene ?: return
        if (shapeIds.isEmpty() && textIds.isEmpty() && strokeIds.isEmpty()) return
        val activePoses = RigEngine.buildActivePosesByLayer(curScene, state.selectedFrame)

        mutateActiveScene(recordUndo = recordUndo) { scene ->
            scene.copy(
                layers = scene.layers.map { l ->
                    if (l.locked) l else l.copy(
                        sceneShapes = l.sceneShapes.map { s ->
                            if (s.id in shapeIds) {
                                val att = s.rigAttachment
                                val targetPose = RigEngine.resolveTargetPose(att, l.id, activePoses)
                                val (rdx, rdy) = if (att != null && targetPose != null) {
                                    RigEngine.inverseDelta(dx, dy, att, targetPose)
                                } else dx to dy
                                s.copy(x = s.x + rdx, y = s.y + rdy)
                            } else s
                        },
                        sceneTexts = l.sceneTexts.map { t ->
                            if (t.id in textIds) {
                                val att = t.rigAttachment
                                val targetPose = RigEngine.resolveTargetPose(att, l.id, activePoses)
                                val (rdx, rdy) = if (att != null && targetPose != null) {
                                    RigEngine.inverseDelta(dx, dy, att, targetPose)
                                } else dx to dy
                                t.copy(x = t.x + rdx, y = t.y + rdy)
                            } else t
                        },
                        sceneStrokes = l.sceneStrokes.map { st ->
                            if (st.id in strokeIds) {
                                val att = st.rigAttachment
                                val targetPose = RigEngine.resolveTargetPose(att, l.id, activePoses)
                                val (rdx, rdy) = if (att != null && targetPose != null) {
                                    RigEngine.inverseDelta(dx, dy, att, targetPose)
                                } else dx to dy
                                st.copy(points = st.points.map { p ->
                                    CanvasTransformEngine.makeNormalizedPoint(
                                        p.x + rdx, p.y + rdy, p.pressure, proj.canvasWidth, proj.canvasHeight
                                    )
                                })
                            } else st
                        }
                    )
                }
            )
        }
    }

    fun transformSelectedShape(scaleFactor: Float? = null, rotationDeltaDeg: Float? = null, colorHex: String? = null) {
        transformSelection(scaleFactor = scaleFactor, rotationDeltaDeg = rotationDeltaDeg, colorHex = colorHex)
    }

    fun transformSelection(scaleFactor: Float? = null, rotationDeltaDeg: Float? = null, colorHex: String? = null) {
        val state = _uiState.value
        val proj = state.project ?: return
        val sceneSnapshot = state.activeScene ?: return
        val shapeIds = state.selectedShapeIds
        val textIds = state.selectedTextIds
        val strokeIds = state.selectedStrokeIds
        if (shapeIds.isEmpty() && textIds.isEmpty() && strokeIds.isEmpty()) return

        val sf = scaleFactor ?: 1f
        val rotDeg = rotationDeltaDeg ?: 0f
        val rotRad = Math.toRadians(rotDeg.toDouble())
        val cosA = cos(rotRad).toFloat()
        val sinA = sin(rotRad).toFloat()

        // Single-shape fast path preserves exact center and dimensions behavior
        if (state.selectionCount == 1 && shapeIds.size == 1) {
            val singleId = shapeIds.first()
            mutateActiveScene { scene ->
                scene.copy(
                    layers = scene.layers.map { l ->
                        l.copy(
                            sceneShapes = l.sceneShapes.map { s ->
                                if (s.id == singleId) {
                                    val newW = if (s.shapeType == ShapeType.LINE || s.shapeType == ShapeType.ARROW) {
                                        (s.width * sf).coerceIn(-1800f, 1800f)
                                    } else {
                                        (s.width * sf).coerceIn(20f, 1800f)
                                    }
                                    val newH = if (s.shapeType == ShapeType.LINE || s.shapeType == ShapeType.ARROW) {
                                        (s.height * sf).coerceIn(-1800f, 1800f)
                                    } else {
                                        (s.height * sf).coerceIn(20f, 1800f)
                                    }
                                    val oldCx = s.x + s.width / 2f
                                    val oldCy = s.y + s.height / 2f
                                    s.copy(
                                        x = oldCx - newW / 2f,
                                        y = oldCy - newH / 2f,
                                        width = newW,
                                        height = newH,
                                        rotationDeg = (s.rotationDeg + rotDeg) % 360f,
                                        strokeColorHex = colorHex ?: s.strokeColorHex,
                                        fillColorHex = if (colorHex != null && s.filled) colorHex else s.fillColorHex
                                    )
                                } else s
                            }
                        )
                    }
                )
            }
            return
        }

        // Combined pivot across all selected items in base document coordinates
        val xs = mutableListOf<Float>()
        val ys = mutableListOf<Float>()
        sceneSnapshot.layers.forEach { l ->
            l.sceneShapes.filter { it.id in shapeIds }.forEach { s ->
                xs += s.x; xs += s.x + s.width
                ys += s.y; ys += s.y + s.height
            }
            l.sceneTexts.filter { it.id in textIds }.forEach { t -> xs += t.x; ys += t.y }
            l.sceneStrokes.filter { it.id in strokeIds }.forEach { st ->
                st.points.forEach { p -> xs += p.x; ys += p.y }
            }
        }
        val pivotX = if (xs.isNotEmpty()) (xs.min() + xs.max()) / 2f else proj.canvasWidth / 2f
        val pivotY = if (ys.isNotEmpty()) (ys.min() + ys.max()) / 2f else proj.canvasHeight / 2f

        fun transformPt(px: Float, py: Float): Pair<Float, Float> {
            val rx = (px - pivotX) * sf
            val ry = (py - pivotY) * sf
            return (pivotX + rx * cosA - ry * sinA) to (pivotY + rx * sinA + ry * cosA)
        }

        mutateActiveScene { scene ->
            scene.copy(
                layers = scene.layers.map { l ->
                    if (l.locked) l else l.copy(
                        sceneShapes = l.sceneShapes.map { s ->
                            if (s.id in shapeIds) {
                                val cx = s.x + s.width / 2f
                                val cy = s.y + s.height / 2f
                                val (newCx, newCy) = transformPt(cx, cy)
                                val newW = (s.width * sf).let { if (s.shapeType == ShapeType.LINE || s.shapeType == ShapeType.ARROW) it else it.coerceIn(20f, 1800f) }
                                val newH = (s.height * sf).let { if (s.shapeType == ShapeType.LINE || s.shapeType == ShapeType.ARROW) it else it.coerceIn(20f, 1800f) }
                                s.copy(
                                    x = newCx - newW / 2f,
                                    y = newCy - newH / 2f,
                                    width = newW,
                                    height = newH,
                                    rotationDeg = (s.rotationDeg + rotDeg) % 360f,
                                    strokeColorHex = colorHex ?: s.strokeColorHex,
                                    fillColorHex = if (colorHex != null && s.filled) colorHex else s.fillColorHex
                                )
                            } else s
                        },
                        sceneTexts = l.sceneTexts.map { t ->
                            if (t.id in textIds) {
                                val (nx, ny) = transformPt(t.x, t.y)
                                t.copy(
                                    x = nx,
                                    y = ny,
                                    fontSizeSp = (t.fontSizeSp * sf).coerceIn(14f, 140f),
                                    rotationDeg = (t.rotationDeg + rotDeg) % 360f,
                                    colorHex = colorHex ?: t.colorHex
                                )
                            } else t
                        },
                        sceneStrokes = l.sceneStrokes.map { st ->
                            if (st.id in strokeIds) {
                                st.copy(
                                    colorHex = colorHex ?: st.colorHex,
                                    points = st.points.map { p ->
                                        val (nx, ny) = transformPt(p.x, p.y)
                                        CanvasTransformEngine.makeNormalizedPoint(
                                            nx, ny, p.pressure, proj.canvasWidth, proj.canvasHeight
                                        )
                                    }
                                )
                            } else st
                        }
                    )
                }
            )
        }
    }

    fun updateSelectedText(newText: String? = null, colorHex: String? = null, animStyle: TextAnimationStyle? = null) {
        val textIds = _uiState.value.selectedTextIds
        if (textIds.isEmpty()) return
        mutateActiveScene { scene ->
            scene.copy(
                layers = scene.layers.map { l ->
                    l.copy(
                        sceneTexts = l.sceneTexts.map { t ->
                            if (t.id in textIds) {
                                t.copy(
                                    text = newText?.takeIf { it.isNotBlank() } ?: t.text,
                                    colorHex = colorHex ?: t.colorHex,
                                    animationStyle = animStyle ?: t.animationStyle
                                )
                            } else t
                        }
                    )
                }
            )
        }
    }

    fun groupSelection(name: String = "") {
        val state = _uiState.value
        if (state.selectionCount < 2) {
            showToast("Select 2 or more elements to group")
            return
        }
        val sceneSnapshot = state.activeScene ?: return
        val shapeIds = state.selectedShapeIds
        val textIds = state.selectedTextIds
        val strokeIds = state.selectedStrokeIds

        val existingGroupCount = sceneSnapshot.layers.sumOf { it.groups.size }
        val groupId = "group-${UUID.randomUUID().toString().take(8)}"
        val groupName = name.trim().ifBlank { "Group ${existingGroupCount + 1}" }

        // Determine host layer from first selected item or active layer
        val hostLayerId = sceneSnapshot.orderedLayers().firstOrNull { l ->
            l.sceneShapes.any { it.id in shapeIds } ||
                l.sceneStrokes.any { it.id in strokeIds } ||
                l.sceneTexts.any { it.id in textIds }
        }?.id ?: state.selectedLayerId

        val newGroup = ElementGroup(
            id = groupId,
            name = groupName
        )

        mutateActiveScene { sc ->
            val updatedLayers = sc.layers.map { l ->
                val newShapes = l.sceneShapes.map { if (it.id in shapeIds) it.copy(groupId = groupId) else it }
                val newTexts = l.sceneTexts.map { if (it.id in textIds) it.copy(groupId = groupId) else it }
                val newStrokes = l.sceneStrokes.map { if (it.id in strokeIds) it.copy(groupId = groupId) else it }
                val referencedHere = (newShapes.mapNotNull { it.groupId } +
                    newTexts.mapNotNull { it.groupId } +
                    newStrokes.mapNotNull { it.groupId }).toSet()
                val baseGroups = l.groups.filter { it.id in referencedHere }
                val finalGroups = if (l.id == hostLayerId) baseGroups + newGroup else baseGroups
                l.copy(
                    sceneShapes = newShapes,
                    sceneTexts = newTexts,
                    sceneStrokes = newStrokes,
                    groups = finalGroups
                )
            }
            sc.copy(layers = updatedLayers)
        }
        _uiState.update { it.copy(multiSelectMode = false, snackbarMessage = "Grouped ${state.selectionCount} elements as '$groupName'") }
    }

    fun ungroupSelection() {
        val state = _uiState.value
        val sceneSnapshot = state.activeScene ?: return
        val shapeIds = state.selectedShapeIds
        val textIds = state.selectedTextIds
        val strokeIds = state.selectedStrokeIds
        val groupIdsToRemove = buildSet {
            sceneSnapshot.layers.forEach { layer ->
                layer.sceneShapes.filter { it.id in shapeIds && it.groupId != null }.forEach { add(it.groupId!!) }
                layer.sceneTexts.filter { it.id in textIds && it.groupId != null }.forEach { add(it.groupId!!) }
                layer.sceneStrokes.filter { it.id in strokeIds && it.groupId != null }.forEach { add(it.groupId!!) }
            }
        }
        if (groupIdsToRemove.isEmpty()) return

        mutateActiveScene { sc ->
            sc.copy(
                layers = sc.layers.map { l ->
                    l.copy(
                        sceneShapes = l.sceneShapes.map { if (it.groupId in groupIdsToRemove) it.copy(groupId = null) else it },
                        sceneTexts = l.sceneTexts.map { if (it.groupId in groupIdsToRemove) it.copy(groupId = null) else it },
                        sceneStrokes = l.sceneStrokes.map { if (it.groupId in groupIdsToRemove) it.copy(groupId = null) else it },
                        groups = l.groups.filterNot { it.id in groupIdsToRemove }
                    )
                }
            )
        }
        showToast("Ungrouped elements")
    }

    fun deleteSelection() {
        val state = _uiState.value
        val shapeIds = state.selectedShapeIds
        val textIds = state.selectedTextIds
        val strokeIds = state.selectedStrokeIds
        if (shapeIds.isEmpty() && textIds.isEmpty() && strokeIds.isEmpty()) return
        mutateActiveScene { scene ->
            scene.copy(
                layers = scene.layers.map { l ->
                    val newShapes = l.sceneShapes.filterNot { it.id in shapeIds }
                    val newTexts = l.sceneTexts.filterNot { it.id in textIds }
                    val newStrokes = l.sceneStrokes.filterNot { it.id in strokeIds }
                    val usedGroupIds = (newShapes.mapNotNull { it.groupId } +
                        newTexts.mapNotNull { it.groupId } +
                        newStrokes.mapNotNull { it.groupId }).toSet()
                    l.copy(
                        sceneShapes = newShapes,
                        sceneTexts = newTexts,
                        sceneStrokes = newStrokes,
                        groups = l.groups.filter { it.id in usedGroupIds }
                    )
                }
            )
        }
        _uiState.update {
            it.copy(
                selectedShapeIds = emptySet(),
                selectedTextIds = emptySet(),
                selectedStrokeIds = emptySet(),
                multiSelectMode = false
            )
        }
    }

    fun clearActiveLayerOrFrame() {
        val state = _uiState.value
        val layer = state.selectedLayer ?: return
        if (layer.locked) return
        mutateActiveScene { scene ->
            if (state.activeMode == StudioMode.DRAW_FRAMES) {
                val frameId = state.selectedFrame?.id
                scene.copy(
                    frames = scene.frames.map { f ->
                        if (f.id == frameId) f.copy(layerDrawings = f.layerDrawings - layer.id, layerShapes = f.layerShapes - layer.id)
                        else f
                    }
                )
            } else {
                scene.copy(
                    layers = scene.layers.map { l ->
                        if (l.id == layer.id) {
                            l.copy(
                                sceneStrokes = emptyList(),
                                sceneShapes = emptyList(),
                                sceneTexts = emptyList(),
                                groups = emptyList()
                            )
                        } else l
                    }
                )
            }
        }
    }

    fun clearAllCanvasDrawings() {
        val scene = _uiState.value.activeScene ?: return
        val unlockedIds = scene.layers.filterNot { it.locked }.map { it.id }.toSet()
        val hasContent = scene.layers.any {
            it.id in unlockedIds && (it.sceneStrokes.isNotEmpty() || it.sceneShapes.isNotEmpty() || it.sceneTexts.isNotEmpty())
        } || scene.frames.any { f ->
            f.layerDrawings.any { (id, list) -> id in unlockedIds && list.isNotEmpty() } ||
                f.layerShapes.any { (id, list) -> id in unlockedIds && list.isNotEmpty() }
        }
        if (!hasContent) {
            showToast("Canvas is already clear")
            return
        }
        mutateActiveScene(recordUndo = true) { sc ->
            sc.copy(
                layers = sc.layers.map { l ->
                    if (l.id in unlockedIds) {
                        l.copy(
                            sceneStrokes = emptyList(),
                            sceneShapes = emptyList(),
                            sceneTexts = emptyList(),
                            groups = emptyList()
                        )
                    } else l
                },
                frames = sc.frames.map { f ->
                    f.copy(
                        layerDrawings = f.layerDrawings.filterKeys { it !in unlockedIds },
                        layerShapes = f.layerShapes.filterKeys { it !in unlockedIds }
                    )
                }
            )
        }
        _uiState.update {
            it.copy(
                selectedShapeIds = emptySet(),
                selectedTextIds = emptySet(),
                selectedStrokeIds = emptySet(),
                multiSelectMode = false,
                snackbarMessage = "Cleared all drawings from canvas"
            )
        }
    }

    // --- Layer Management ---

    fun selectLayer(layerId: String) {
        _uiState.update { it.copy(selectedLayerId = layerId) }
    }

    fun createLayer(name: String, category: LayerCategory) {
        val newLayer = Layer(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { category.label },
            category = category,
            orderIndex = (_uiState.value.activeScene?.layers?.maxOfOrNull { it.orderIndex } ?: -1) + 1,
            skeletonPose = if (category == LayerCategory.CHARACTER) {
                SkeletonPose(name = "Starter Pose", joints = defaultStickmanJoints(540f, 960f))
            } else null
        )
        mutateActiveScene { scene ->
            val updated = (scene.layers + newLayer).mapIndexed { i, l -> l.copy(orderIndex = i) }
            scene.copy(layers = updated)
        }
        _uiState.update { it.copy(selectedLayerId = newLayer.id) }
    }

    fun renameLayer(layerId: String, name: String) {
        if (name.isBlank()) return
        mutateActiveScene { scene ->
            scene.copy(layers = scene.layers.map { if (it.id == layerId) it.copy(name = name.trim()) else it })
        }
    }

    fun duplicateLayer(layerId: String) {
        mutateActiveScene { scene ->
            val ordered = scene.orderedLayers().toMutableList()
            val idx = ordered.indexOfFirst { it.id == layerId }
            if (idx < 0) return@mutateActiveScene scene
            val src = ordered[idx]
            val newId = UUID.randomUUID().toString()
            val copy = src.copy(
                id = newId,
                name = "${src.name} Copy",
                sceneStrokes = src.sceneStrokes.map { it.copy(id = UUID.randomUUID().toString(), layerId = newId) },
                sceneShapes = src.sceneShapes.map { it.copy(id = UUID.randomUUID().toString(), layerId = newId) },
                sceneTexts = src.sceneTexts.map { it.copy(id = UUID.randomUUID().toString(), layerId = newId) }
            )
            ordered.add(idx + 1, copy)
            scene.copy(layers = ordered.mapIndexed { i, l -> l.copy(orderIndex = i) })
        }
    }

    fun deleteLayer(layerId: String) {
        val scene = _uiState.value.activeScene ?: return
        if (scene.layers.size <= 1) {
            showToast("At least one layer is required")
            return
        }
        mutateActiveScene { sc ->
            val remaining = sc.orderedLayers().filterNot { it.id == layerId }
                .mapIndexed { i, l -> l.copy(orderIndex = i) }
            sc.copy(layers = remaining)
        }
        val nextId = _uiState.value.activeScene?.orderedLayers()?.lastOrNull()?.id.orEmpty()
        _uiState.update { it.copy(selectedLayerId = nextId) }
    }

    fun moveLayer(layerId: String, delta: Int) {
        mutateActiveScene { scene ->
            val ordered = scene.orderedLayers().toMutableList()
            val idx = ordered.indexOfFirst { it.id == layerId }
            val target = (idx + delta).coerceIn(0, ordered.lastIndex)
            if (idx < 0 || idx == target) return@mutateActiveScene scene
            val item = ordered.removeAt(idx)
            ordered.add(target, item)
            scene.copy(layers = ordered.mapIndexed { i, l -> l.copy(orderIndex = i) })
        }
    }

    fun toggleLayerVisible(layerId: String) {
        mutateActiveScene { scene ->
            scene.copy(layers = scene.layers.map { if (it.id == layerId) it.copy(visible = !it.visible) else it })
        }
    }

    fun toggleLayerLocked(layerId: String) {
        mutateActiveScene { scene ->
            scene.copy(layers = scene.layers.map { if (it.id == layerId) it.copy(locked = !it.locked) else it })
        }
    }

    fun setLayerOpacity(layerId: String, opacity: Float) {
        mutateActiveScene { scene ->
            scene.copy(layers = scene.layers.map { if (it.id == layerId) it.copy(opacity = opacity.coerceIn(0.05f, 1f)) else it })
        }
    }

    fun updateBackground(spec: BackgroundSpec) {
        mutateActiveScene { scene -> scene.copy(background = spec) }
    }

    fun updateLayerSemantic(layerId: String, semantic: SemanticInfo) {
        mutateActiveScene { scene ->
            scene.copy(layers = scene.layers.map { if (it.id == layerId) it.copy(semantic = semantic) else it })
        }
    }

    fun setPoseEditWholeBody(wholeBody: Boolean) {
        _uiState.update { it.copy(poseEditWholeBody = wholeBody) }
    }

    fun selectPoseJoint(jointName: String) {
        _uiState.update { it.copy(selectedPoseJoint = jointName) }
    }

    fun updateLayerPoseJoint(
        layerId: String,
        jointName: String,
        x: Float,
        y: Float,
        poseNote: String? = null,
        recordUndo: Boolean = true
    ) {
        val st = _uiState.value
        val proj = st.project ?: return
        val marginX = proj.canvasWidth * 0.25f
        val marginY = proj.canvasHeight * 0.25f
        val clampedX = x.coerceIn(-marginX, proj.canvasWidth + marginX)
        val clampedY = y.coerceIn(-marginY, proj.canvasHeight + marginY)
        mutateLayerOrFramePose(layerId, recordUndo = recordUndo) { basePose ->
            basePose.copy(
                note = poseNote ?: basePose.note,
                joints = basePose.joints + (jointName to JointPoint(clampedX, clampedY))
            )
        }
    }

    fun moveLayerPoseBy(
        layerId: String,
        dx: Float,
        dy: Float,
        recordUndo: Boolean = true
    ) {
        if (abs(dx) < 0.01f && abs(dy) < 0.01f) return
        val proj = _uiState.value.project ?: return
        val marginX = proj.canvasWidth * 0.25f
        val marginY = proj.canvasHeight * 0.25f
        mutateLayerOrFramePose(layerId, recordUndo = recordUndo) { basePose ->
            if (basePose.joints.isEmpty()) return@mutateLayerOrFramePose basePose
            val minX = basePose.joints.values.minOf { it.x }
            val maxX = basePose.joints.values.maxOf { it.x }
            val minY = basePose.joints.values.minOf { it.y }
            val maxY = basePose.joints.values.maxOf { it.y }
            val curCx = (minX + maxX) / 2f
            val curCy = (minY + maxY) / 2f
            val targetCx = (curCx + dx).coerceIn(-marginX, proj.canvasWidth + marginX)
            val targetCy = (curCy + dy).coerceIn(-marginY, proj.canvasHeight + marginY)
            val clampedDx = targetCx - curCx
            val clampedDy = targetCy - curCy
            basePose.copy(
                joints = basePose.joints.mapValues { (_, pt) ->
                    JointPoint(pt.x + clampedDx, pt.y + clampedDy)
                }
            )
        }
    }

    fun setLayerPosePosition(
        layerId: String,
        targetCenterX: Float,
        targetCenterY: Float,
        recordUndo: Boolean = true
    ) {
        val st = _uiState.value
        val layer = st.activeScene?.layers?.find { it.id == layerId } ?: return
        val pose = st.activePoseFor(layer) ?: return
        if (pose.joints.isEmpty()) return
        val minX = pose.joints.values.minOf { it.x }
        val maxX = pose.joints.values.maxOf { it.x }
        val minY = pose.joints.values.minOf { it.y }
        val maxY = pose.joints.values.maxOf { it.y }
        val curCx = (minX + maxX) / 2f
        val curCy = (minY + maxY) / 2f
        moveLayerPoseBy(layerId, targetCenterX - curCx, targetCenterY - curCy, recordUndo = recordUndo)
    }

    fun setLayerPoseScale(
        layerId: String,
        targetScale: Float,
        recordUndo: Boolean = true
    ) {
        val clampedScale = targetScale.coerceIn(0.25f, 3.0f)
        val anchor = ensurePoseTransformAnchor(layerId) ?: return
        val updatedAnchor = anchor.copy(currentScale = clampedScale)
        poseTransformAnchor = updatedAnchor
        applyPoseTransformAnchor(updatedAnchor, recordUndo = recordUndo)
    }

    fun setLayerPoseAngle(
        layerId: String,
        targetAngleDeg: Float,
        recordUndo: Boolean = true
    ) {
        val normalizedAngle = PoseGeometry.normalizeAngleDeg(targetAngleDeg)
        val anchor = ensurePoseTransformAnchor(layerId) ?: return
        val updatedAnchor = anchor.copy(currentAngleDeg = normalizedAngle)
        poseTransformAnchor = updatedAnchor
        applyPoseTransformAnchor(updatedAnchor, recordUndo = recordUndo)
    }

    fun finishPoseTransformSession() {
        poseTransformAnchor = null
    }

    private fun ensurePoseTransformAnchor(layerId: String): PoseTransformAnchor? {
        val st = _uiState.value
        val frameHasPoseOverride = st.selectedFrame?.layerPoses?.containsKey(layerId) == true
        val frameKey = if (st.activeMode == StudioMode.DRAW_FRAMES || frameHasPoseOverride) st.selectedFrame?.id else null
        val existing = poseTransformAnchor
        if (existing != null && existing.layerId == layerId && existing.frameId == frameKey) {
            return existing
        }
        val layer = st.activeScene?.layers?.find { it.id == layerId } ?: return null
        val pose = st.activePoseFor(layer) ?: return null
        if (pose.joints.isEmpty()) return null
        val (cx, cy) = PoseGeometry.computeCenter(pose.joints)
        val scale = PoseGeometry.computeScale(pose.joints)
        val angle = PoseGeometry.computeAngleDeg(pose.joints)
        return PoseTransformAnchor(
            layerId = layerId,
            frameId = frameKey,
            baseJoints = pose.joints,
            centerX = cx,
            centerY = cy,
            baseScale = scale,
            baseAngleDeg = angle
        ).also { poseTransformAnchor = it }
    }

    private fun applyPoseTransformAnchor(anchor: PoseTransformAnchor, recordUndo: Boolean) {
        val scaleRatio = (anchor.currentScale / anchor.baseScale.coerceAtLeast(0.05f)).coerceIn(0.05f, 12f)
        val deltaAngle = anchor.currentAngleDeg - anchor.baseAngleDeg
        val transformed = PoseGeometry.transformJointsFromAnchor(
            anchorJoints = anchor.baseJoints,
            centerX = anchor.centerX,
            centerY = anchor.centerY,
            scaleRatio = scaleRatio,
            deltaAngleDeg = deltaAngle
        )
        mutateLayerOrFramePose(anchor.layerId, recordUndo = recordUndo) { basePose ->
            basePose.copy(joints = transformed)
        }
    }

    private fun mutateLayerOrFramePose(
        layerId: String,
        recordUndo: Boolean,
        transformPose: (SkeletonPose) -> SkeletonPose
    ) {
        val st = _uiState.value
        val proj = st.project ?: return
        val defaultJoints = defaultStickmanJoints(proj.canvasWidth / 2f, proj.canvasHeight / 2f)
        val targetFrameId = st.selectedFrame?.id
        val frameHasPoseOverride = st.selectedFrame?.layerPoses?.containsKey(layerId) == true
        val editFramePose = targetFrameId != null && (st.activeMode == StudioMode.DRAW_FRAMES || frameHasPoseOverride)

        mutateActiveScene(recordUndo = recordUndo) { scene ->
            val layerBasePose = scene.layers.find { it.id == layerId }?.skeletonPose
                ?: SkeletonPose(name = "Starter Pose", joints = defaultJoints)
            if (editFramePose) {
                scene.copy(
                    frames = scene.frames.map { frame ->
                        if (frame.id == targetFrameId) {
                            val currentPose = frame.layerPoses[layerId] ?: layerBasePose
                            frame.copy(layerPoses = frame.layerPoses + (layerId to transformPose(currentPose)))
                        } else frame
                    }
                )
            } else {
                scene.copy(
                    layers = scene.layers.map { l ->
                        if (l.id == layerId) {
                            l.copy(skeletonPose = transformPose(l.skeletonPose ?: layerBasePose))
                        } else l
                    }
                )
            }
        }
    }

    fun resetLayerPoseToDefault(layerId: String) {
        poseTransformAnchor = null
        val proj = _uiState.value.project ?: return
        val centerX = proj.canvasWidth / 2f
        val centerY = proj.canvasHeight / 2f
        val defaultJoints = defaultStickmanJoints(centerX, centerY)
        mutateLayerOrFramePose(layerId, recordUndo = true) { basePose ->
            basePose.copy(joints = defaultJoints)
        }
        showToast("2D pose reset to default")
    }

    fun ensureSkeletonRigOnLayer(layerId: String) {
        val st = _uiState.value
        val proj = st.project ?: return
        val scene = st.activeScene ?: return
        val layer = scene.layers.find { it.id == layerId } ?: return
        if (layer.skeletonPose != null) return
        val bounds = SpatialMapGenerator.computeLayerBounds(layer, proj.canvasWidth, proj.canvasHeight)
        val cx = bounds?.centerX ?: (proj.canvasWidth / 2f)
        val cy = bounds?.centerY ?: (proj.canvasHeight / 2f)
        val joints = defaultStickmanJoints(cx, cy)
        val pose = SkeletonPose(name = "${layer.name} Rig", joints = joints, note = "2D character skeleton rig")
        mutateActiveScene(recordUndo = true) { sc ->
            sc.copy(
                layers = sc.layers.map { l ->
                    if (l.id == layerId) l.copy(skeletonPose = pose) else l
                }
            )
        }
    }

    fun openPoseEditorQuick() {
        val st = _uiState.value
        val scene = st.activeScene ?: return
        val currentLayer = st.selectedLayer
        val targetLayer = when {
            currentLayer?.skeletonPose != null -> currentLayer
            else -> scene.orderedLayers().lastOrNull { it.skeletonPose != null }
                ?: currentLayer
                ?: scene.orderedLayers().lastOrNull()
        } ?: return

        if (targetLayer.skeletonPose == null) {
            ensureSkeletonRigOnLayer(targetLayer.id)
        }
        _uiState.update {
            it.copy(
                selectedLayerId = targetLayer.id,
                activeSheet = StudioSheet.POSE_EDITOR
            )
        }
    }

    fun attachSelectionToRig(preset: RigBonePreset, targetRigLayerId: String) {
        val state = _uiState.value
        val proj = state.project ?: return
        val sceneSnapshot = state.activeScene ?: return
        val shapeIds = state.selectedShapeIds
        val textIds = state.selectedTextIds
        val strokeIds = state.selectedStrokeIds
        if (shapeIds.isEmpty() && textIds.isEmpty() && strokeIds.isEmpty()) {
            showToast("Select drawn shapes, strokes, or text first")
            return
        }

        // Compute existing poses BEFORE adding rig so we can bake any old attachment seamlessly
        val activePosesBefore = RigEngine.buildActivePosesByLayer(sceneSnapshot, state.selectedFrame)
        val rigLayerBefore = sceneSnapshot.layers.find { it.id == targetRigLayerId } ?: return
        val existingPose = state.selectedFrame?.layerPoses?.get(targetRigLayerId) ?: rigLayerBefore.skeletonPose

        val (resolvedPose, needsNewRig) = if (existingPose != null) {
            existingPose to false
        } else {
            val bounds = SpatialMapGenerator.computeLayerBounds(rigLayerBefore, proj.canvasWidth, proj.canvasHeight)
            val cx = bounds?.centerX ?: (proj.canvasWidth / 2f)
            val cy = bounds?.centerY ?: (proj.canvasHeight / 2f)
            SkeletonPose(name = "${rigLayerBefore.name} Rig", joints = defaultStickmanJoints(cx, cy)) to true
        }

        val attachment = RigEngine.createAttachment(preset, resolvedPose, targetRigLayerId)
        val touchedGroupIds = mutableSetOf<String>()
        var attachedCount = 0

        mutateActiveScene(recordUndo = true) { sc ->
            sc.copy(
                layers = sc.layers.map { l ->
                    val withPose = if (needsNewRig && l.id == targetRigLayerId) l.copy(skeletonPose = resolvedPose) else l
                    val updatedShapes = withPose.sceneShapes.map { sh ->
                        if (sh.id in shapeIds) {
                            attachedCount++
                            sh.groupId?.let { touchedGroupIds += it }
                            val baked = if (sh.rigAttachment != null) {
                                RigEngine.deformShape(
                                    sh,
                                    RigEngine.resolveTargetPose(sh.rigAttachment, l.id, activePosesBefore)
                                )
                            } else sh
                            baked.copy(rigAttachment = attachment)
                        } else sh
                    }
                    val updatedTexts = withPose.sceneTexts.map { tx ->
                        if (tx.id in textIds) {
                            attachedCount++
                            tx.groupId?.let { touchedGroupIds += it }
                            val baked = if (tx.rigAttachment != null) {
                                RigEngine.deformText(
                                    tx,
                                    RigEngine.resolveTargetPose(tx.rigAttachment, l.id, activePosesBefore)
                                )
                            } else tx
                            baked.copy(rigAttachment = attachment)
                        } else tx
                    }
                    val updatedStrokes = withPose.sceneStrokes.map { st ->
                        if (st.id in strokeIds) {
                            attachedCount++
                            st.groupId?.let { touchedGroupIds += it }
                            val baked = if (st.rigAttachment != null) {
                                RigEngine.deformStroke(
                                    st,
                                    RigEngine.resolveTargetPose(st.rigAttachment, l.id, activePosesBefore),
                                    proj.canvasWidth,
                                    proj.canvasHeight
                                )
                            } else st
                            baked.copy(rigAttachment = attachment)
                        } else st
                    }
                    withPose.copy(
                        sceneShapes = updatedShapes,
                        sceneTexts = updatedTexts,
                        sceneStrokes = updatedStrokes,
                        groups = withPose.groups.map { g ->
                            if (g.id in touchedGroupIds) g.copy(rigAttachment = attachment) else g
                        }
                    )
                }
            )
        }
        showToast("Attached $attachedCount element(s) to ${preset.label}")
    }

    fun detachSelectionFromRig() {
        val state = _uiState.value
        val proj = state.project ?: return
        val sceneSnapshot = state.activeScene ?: return
        val shapeIds = state.selectedShapeIds
        val textIds = state.selectedTextIds
        val strokeIds = state.selectedStrokeIds
        if (shapeIds.isEmpty() && textIds.isEmpty() && strokeIds.isEmpty()) return

        val activePoses = RigEngine.buildActivePosesByLayer(sceneSnapshot, state.selectedFrame)
        val detachedGroupIds = mutableSetOf<String>()
        var detachedCount = 0
        mutateActiveScene(recordUndo = true) { sc ->
            sc.copy(
                layers = sc.layers.map { l ->
                    l.copy(
                        sceneShapes = l.sceneShapes.map { sh ->
                            if (sh.id in shapeIds && sh.rigAttachment != null) {
                                detachedCount++
                                sh.groupId?.let { detachedGroupIds += it }
                                RigEngine.deformShape(
                                    sh,
                                    RigEngine.resolveTargetPose(sh.rigAttachment, l.id, activePoses)
                                ).copy(rigAttachment = null)
                            } else sh
                        },
                        sceneTexts = l.sceneTexts.map { tx ->
                            if (tx.id in textIds && tx.rigAttachment != null) {
                                detachedCount++
                                tx.groupId?.let { detachedGroupIds += it }
                                RigEngine.deformText(
                                    tx,
                                    RigEngine.resolveTargetPose(tx.rigAttachment, l.id, activePoses)
                                ).copy(rigAttachment = null)
                            } else tx
                        },
                        sceneStrokes = l.sceneStrokes.map { st ->
                            if (st.id in strokeIds && st.rigAttachment != null) {
                                detachedCount++
                                st.groupId?.let { detachedGroupIds += it }
                                RigEngine.deformStroke(
                                    st,
                                    RigEngine.resolveTargetPose(st.rigAttachment, l.id, activePoses),
                                    proj.canvasWidth,
                                    proj.canvasHeight
                                ).copy(rigAttachment = null)
                            } else st
                        },
                        groups = l.groups.map { g ->
                            if (g.id in detachedGroupIds) g.copy(rigAttachment = null) else g
                        }
                    )
                }
            )
        }
        if (detachedCount > 0) {
            showToast("Detached $detachedCount element(s) from rig")
        }
    }

    fun autoBindLayerToRig(layerId: String) {
        val state = _uiState.value
        val proj = state.project ?: return
        val sceneSnapshot = state.activeScene ?: return
        val layer = sceneSnapshot.layers.find { it.id == layerId } ?: return
        val activePose = state.selectedFrame?.layerPoses?.get(layerId)
            ?: layer.skeletonPose
            ?: SkeletonPose(
                name = "${layer.name} Rig",
                joints = defaultStickmanJoints(proj.canvasWidth / 2f, proj.canvasHeight / 2f)
            )
        val (boundLayer, count) = RigEngine.autoBindLayerElements(
            layer = if (layer.skeletonPose == null) layer.copy(skeletonPose = activePose) else layer,
            rigPose = activePose,
            rigLayerId = layerId
        )
        if (count == 0 && layer.skeletonPose != null) {
            showToast("All shapes & strokes on '${layer.name}' are already rigged")
            return
        }
        mutateActiveScene(recordUndo = true) { sc ->
            sc.copy(layers = sc.layers.map { if (it.id == layerId) boundLayer else it })
        }
        showToast("Attached $count element(s) to nearest skeleton bones")
    }

    // --- Frame-by-Frame Timeline Operations (Single Chronological Source of Truth) ---

    private fun frameStartTimeMs(scene: Scene, fps: Int, frameId: String): Long {
        val safeFps = fps.coerceAtLeast(1)
        val ordered = scene.orderedFrames()
        var ticksBefore = 0
        for (f in ordered) {
            if (f.id == frameId) break
            ticksBefore += f.exposureFrames.coerceAtLeast(1)
        }
        return (ticksBefore * 1000L / safeFps).coerceIn(0L, scene.effectiveDurationMs(safeFps))
    }

    fun toggleConnectedScenesMode() {
        _uiState.update { it.copy(connectedScenesMode = !it.connectedScenesMode) }
    }

    fun selectFrame(frameId: String) {
        playbackJob?.cancel()
        val state = _uiState.value
        val scene = state.activeScene
        val fps = state.project?.fps ?: 12
        val targetMs = if (scene != null) frameStartTimeMs(scene, fps, frameId) else state.playheadTimeMs
        _uiState.update {
            it.copy(
                selectedFrameId = frameId,
                playheadTimeMs = targetMs,
                isPlaying = false
            )
        }
    }

    fun stepFrame(delta: Int) {
        playbackJob?.cancel()
        val state = _uiState.value
        val proj = state.project ?: return
        val scene = state.activeScene ?: return
        val fps = proj.fps.coerceAtLeast(1)
        val frames = scene.orderedFrames()
        if (frames.isEmpty()) return
        val curIdx = frames.indexOfFirst { it.id == state.selectedFrameId }.let { if (it < 0) 0 else it }
        val nextIdx = curIdx + delta

        if (nextIdx in frames.indices) {
            val nextFrame = frames[nextIdx]
            _uiState.update {
                it.copy(
                    selectedFrameId = nextFrame.id,
                    playheadTimeMs = frameStartTimeMs(scene, fps, nextFrame.id),
                    isPlaying = false
                )
            }
            return
        }

        // Cross scene boundaries cleanly when stepping in Whole Reel mode
        if (state.connectedScenesMode) {
            val orderedScenes = proj.scenes.sortedBy { it.orderIndex }
            val sceneIdx = orderedScenes.indexOfFirst { it.id == scene.id }
            val targetScene = when {
                nextIdx > frames.lastIndex && sceneIdx in 0 until orderedScenes.lastIndex -> orderedScenes[sceneIdx + 1]
                nextIdx < 0 && sceneIdx > 0 -> orderedScenes[sceneIdx - 1]
                else -> null
            }
            if (targetScene != null) {
                val targetFrames = targetScene.orderedFrames()
                val targetFrame = if (delta > 0) targetFrames.firstOrNull() else targetFrames.lastOrNull()
                val targetMs = if (targetFrame != null) frameStartTimeMs(targetScene, fps, targetFrame.id) else 0L
                _uiState.update {
                    it.copy(
                        activeSceneId = targetScene.id,
                        selectedLayerId = preferredLayerForScene(targetScene)?.id.orEmpty(),
                        selectedFrameId = targetFrame?.id.orEmpty(),
                        selectedShapeIds = emptySet(),
                        selectedTextIds = emptySet(),
                        selectedStrokeIds = emptySet(),
                        multiSelectMode = false,
                        playheadTimeMs = targetMs,
                        isPlaying = false
                    )
                }
                return
            }
        }

        val clampedIdx = nextIdx.coerceIn(0, frames.lastIndex)
        val clampedFrame = frames[clampedIdx]
        _uiState.update {
            it.copy(
                selectedFrameId = clampedFrame.id,
                playheadTimeMs = frameStartTimeMs(scene, fps, clampedFrame.id),
                isPlaying = false
            )
        }
    }

    fun addBlankFrameAfterSelected() {
        val state = _uiState.value
        val scene = state.activeScene ?: return
        val res = TimelineEngine.addBlankFrameAfter(scene.frames, state.selectedFrameId)
        mutateActiveScene { it.copy(frames = res.frames) }
        _uiState.update { it.copy(selectedFrameId = res.selectedFrameId) }
    }

    fun duplicateSelectedFrame() {
        val state = _uiState.value
        val scene = state.activeScene ?: return
        val res = TimelineEngine.duplicateFrameAfter(scene.frames, state.selectedFrameId)
        mutateActiveScene { it.copy(frames = res.frames) }
        _uiState.update { it.copy(selectedFrameId = res.selectedFrameId) }
    }

    fun deleteSelectedFrame() {
        val state = _uiState.value
        val scene = state.activeScene ?: return
        val res = TimelineEngine.deleteFrame(scene.frames, state.selectedFrameId)
        mutateActiveScene { it.copy(frames = res.frames) }
        _uiState.update { it.copy(selectedFrameId = res.selectedFrameId) }
    }

    fun reorderFrame(fromIndex: Int, toIndex: Int) {
        mutateActiveScene { sc ->
            sc.copy(frames = TimelineEngine.reorderFrame(sc.frames, fromIndex, toIndex))
        }
    }

    fun setSelectedFrameExposure(exposure: Int) {
        val frameId = _uiState.value.selectedFrame?.id ?: return
        mutateActiveScene { sc ->
            sc.copy(frames = TimelineEngine.setExposure(sc.frames, frameId, exposure))
        }
    }

    fun setSelectedFrameNote(note: String) {
        val frameId = _uiState.value.selectedFrame?.id ?: return
        mutateActiveScene { sc ->
            sc.copy(frames = TimelineEngine.setFrameNote(sc.frames, frameId, note))
        }
    }

    // --- Playback ---

    fun togglePlayPause() {
        if (_uiState.value.isPlaying) {
            pausePlayback()
        } else {
            startPlayback()
        }
    }

    fun pausePlayback() {
        playbackJob?.cancel()
        _uiState.update { it.copy(isPlaying = false) }
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        val state = _uiState.value
        val orderedScenes = state.orderedScenes()
        val targetScene = if (state.connectedScenesMode) {
            orderedScenes.firstOrNull() ?: state.activeScene
        } else {
            state.activeScene
        }
        val firstFrameId = targetScene?.orderedFrames()?.firstOrNull()?.id.orEmpty()
        _uiState.update {
            it.copy(
                isPlaying = false,
                activeSceneId = targetScene?.id ?: it.activeSceneId,
                selectedLayerId = if (targetScene != null && targetScene.id != it.activeSceneId) {
                    preferredLayerForScene(targetScene)?.id.orEmpty()
                } else it.selectedLayerId,
                selectedFrameId = firstFrameId.ifBlank { it.selectedFrameId },
                playheadTimeMs = 0L
            )
        }
    }

    fun toggleLoop() {
        _uiState.update { it.copy(loopPlayback = !it.loopPlayback) }
    }

    fun toggleReversePlayback() {
        _uiState.update { it.copy(reversePlayback = !it.reversePlayback) }
    }

    fun seekTimeMs(ms: Long) {
        val state = _uiState.value
        val proj = state.project ?: return
        val scene = state.activeScene ?: return
        val clamped = ms.coerceIn(0L, scene.effectiveDurationMs(proj.fps))
        val tick = TimelineEngine.frameAtTimeMs(scene, proj.fps, clamped, reverse = false)
        _uiState.update {
            it.copy(
                playheadTimeMs = clamped,
                selectedFrameId = tick?.frame?.id ?: it.selectedFrameId
            )
        }
    }

    fun seekGlobalTimeMs(globalMs: Long) {
        val state = _uiState.value
        val proj = state.project ?: return
        val fps = proj.fps.coerceAtLeast(1)
        val ordered = proj.scenes.sortedBy { it.orderIndex }
        if (ordered.isEmpty()) return
        val totalReelMs = ordered.sumOf { it.effectiveDurationMs(fps) }.coerceAtLeast(1L)
        val clampedGlobal = globalMs.coerceIn(0L, totalReelMs)
        var acc = 0L
        var targetScene = ordered.last()
        var localMs = 0L
        for (sc in ordered) {
            val dur = sc.effectiveDurationMs(fps).coerceAtLeast(1L)
            if (clampedGlobal <= acc + dur || sc == ordered.last()) {
                targetScene = sc
                localMs = (clampedGlobal - acc).coerceIn(0L, dur)
                break
            }
            acc += dur
        }
        val tick = TimelineEngine.frameAtTimeMs(targetScene, fps, localMs, reverse = false)
        _uiState.update {
            it.copy(
                activeSceneId = targetScene.id,
                selectedLayerId = if (targetScene.id != it.activeSceneId) {
                    preferredLayerForScene(targetScene)?.id.orEmpty()
                } else it.selectedLayerId,
                selectedFrameId = tick?.frame?.id ?: targetScene.orderedFrames().firstOrNull()?.id ?: it.selectedFrameId,
                playheadTimeMs = localMs
            )
        }
    }

    private fun startPlayback() {
        val state = _uiState.value
        val proj = state.project ?: return
        val initialScene = state.activeScene ?: return
        val initFps = proj.fps.coerceAtLeast(1)
        val initDuration = initialScene.effectiveDurationMs(initFps).coerceAtLeast(16L)

        var startCursor = state.playheadTimeMs.coerceIn(0L, initDuration)
        if (!state.reversePlayback && startCursor >= initDuration - 16L) {
            startCursor = 0L
        } else if (state.reversePlayback && startCursor <= 16L) {
            startCursor = initDuration
        }

        playbackJob?.cancel()
        _uiState.update { it.copy(isPlaying = true, playheadTimeMs = startCursor) }
        playbackJob = viewModelScope.launch {
            var cursor = startCursor
            var lastWallMs = System.currentTimeMillis()
            while (isActive) {
                val curState = _uiState.value
                val curProj = curState.project ?: break
                val orderedScenes = curProj.scenes.sortedBy { it.orderIndex }
                val curScene = curState.activeScene ?: break
                val fps = curProj.fps.coerceAtLeast(1)
                val totalDuration = curScene.effectiveDurationMs(fps).coerceAtLeast(16L)
                val safeCursor = cursor.coerceIn(0L, totalDuration)
                val ticks = TimelineEngine.buildPlaybackTicks(curScene.frames, reverse = false)
                val framesDur = curScene.framesDurationMs(fps)
                val tick = if (ticks.isNotEmpty() && framesDur > 0) {
                    val idx = ((safeCursor % framesDur) * ticks.size / framesDur).toInt().coerceIn(0, ticks.lastIndex)
                    ticks[idx]
                } else null

                _uiState.update {
                    it.copy(
                        playheadTimeMs = safeCursor,
                        selectedFrameId = tick?.frame?.id ?: it.selectedFrameId
                    )
                }

                delay(16L)
                val nowWallMs = System.currentTimeMillis()
                val dt = (nowWallMs - lastWallMs).coerceIn(8L, 64L)
                lastWallMs = nowWallMs
                val isReverse = _uiState.value.reversePlayback

                if (!isReverse) {
                    cursor += dt
                    if (cursor >= totalDuration) {
                        val currentIdx = orderedScenes.indexOfFirst { it.id == curScene.id }
                        val hasNextScene = currentIdx >= 0 && currentIdx < orderedScenes.lastIndex
                        if (hasNextScene) {
                            val nextScene = orderedScenes[currentIdx + 1]
                            cursor = 0L
                            _uiState.update {
                                it.copy(
                                    activeSceneId = nextScene.id,
                                    selectedLayerId = preferredLayerForScene(nextScene)?.id.orEmpty(),
                                    selectedFrameId = nextScene.orderedFrames().firstOrNull()?.id.orEmpty(),
                                    playheadTimeMs = 0L
                                )
                            }
                        } else if (_uiState.value.loopPlayback) {
                            val firstScene = orderedScenes.firstOrNull() ?: curScene
                            cursor = 0L
                            _uiState.update {
                                it.copy(
                                    activeSceneId = firstScene.id,
                                    selectedLayerId = preferredLayerForScene(firstScene)?.id.orEmpty(),
                                    selectedFrameId = firstScene.orderedFrames().firstOrNull()?.id.orEmpty(),
                                    playheadTimeMs = 0L
                                )
                            }
                        } else {
                            _uiState.update { it.copy(isPlaying = false, playheadTimeMs = totalDuration) }
                            break
                        }
                    }
                } else {
                    cursor -= dt
                    if (cursor < 0L) {
                        val currentIdx = orderedScenes.indexOfFirst { it.id == curScene.id }
                        val hasPrevScene = currentIdx > 0
                        if (hasPrevScene) {
                            val prevScene = orderedScenes[currentIdx - 1]
                            val prevDur = prevScene.effectiveDurationMs(fps).coerceAtLeast(16L)
                            cursor = prevDur
                            _uiState.update {
                                it.copy(
                                    activeSceneId = prevScene.id,
                                    selectedLayerId = preferredLayerForScene(prevScene)?.id.orEmpty(),
                                    selectedFrameId = prevScene.orderedFrames().lastOrNull()?.id.orEmpty(),
                                    playheadTimeMs = prevDur
                                )
                            }
                        } else if (_uiState.value.loopPlayback) {
                            val lastScene = orderedScenes.lastOrNull() ?: curScene
                            val lastDur = lastScene.effectiveDurationMs(fps).coerceAtLeast(16L)
                            cursor = lastDur
                            _uiState.update {
                                it.copy(
                                    activeSceneId = lastScene.id,
                                    selectedLayerId = preferredLayerForScene(lastScene)?.id.orEmpty(),
                                    selectedFrameId = lastScene.orderedFrames().lastOrNull()?.id.orEmpty(),
                                    playheadTimeMs = lastDur
                                )
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    isPlaying = false,
                                    playheadTimeMs = 0L,
                                    selectedFrameId = curScene.orderedFrames().firstOrNull()?.id ?: it.selectedFrameId
                                )
                            }
                            break
                        }
                    }
                }
            }
        }
    }

    // --- Motion Graphics & Camera ---

    fun addOrUpdateMotionTrack(track: MotionTrack) {
        mutateActiveScene { scene ->
            val exists = scene.motionTracks.any { it.id == track.id }
            val updated = if (exists) {
                scene.motionTracks.map { if (it.id == track.id) track else it }
            } else {
                scene.motionTracks + track
            }
            scene.copy(motionTracks = updated)
        }
        _uiState.update { it.copy(selectedMotionTrackId = track.id) }
    }

    fun deleteMotionTrack(trackId: String) {
        mutateActiveScene { scene ->
            scene.copy(motionTracks = scene.motionTracks.filterNot { it.id == trackId })
        }
        _uiState.update {
            if (it.selectedMotionTrackId == trackId) it.copy(selectedMotionTrackId = null) else it
        }
    }

    fun selectMotionTrack(trackId: String?) {
        _uiState.update { it.copy(selectedMotionTrackId = trackId) }
    }

    fun startRecordingPathForTrack(trackId: String) {
        _uiState.update {
            it.copy(
                isRecordingPathForTrackId = trackId,
                activeTool = DrawingTool.PEN,
                activeSheet = StudioSheet.NONE,
                snackbarMessage = "Draw the movement path directly on the canvas!"
            )
        }
    }

    fun addCameraZoomPreset(targetZoom: Float, summary: String) {
        val scene = _uiState.value.activeScene ?: return
        val proj = _uiState.value.project ?: return
        val dur = scene.effectiveDurationMs(proj.fps)
        mutateActiveScene { sc ->
            sc.copy(
                cameraTrack = sc.cameraTrack.copy(
                    keyframes = listOf(
                        CameraKeyframe(timeMs = 0L, zoom = 1f, note = "Start framing"),
                        CameraKeyframe(timeMs = dur, zoom = targetZoom, note = summary)
                    ),
                    directionSummary = summary
                )
            )
        }
    }

    // --- Multi-Scene Reel Planner & Connected Scene Customization ---

    fun selectScene(sceneId: String) {
        val scene = _uiState.value.project?.scenes?.find { it.id == sceneId } ?: return
        playbackJob?.cancel()
        _uiState.update {
            it.copy(
                activeSceneId = scene.id,
                selectedLayerId = preferredLayerForScene(scene)?.id.orEmpty(),
                selectedFrameId = scene.orderedFrames().firstOrNull()?.id.orEmpty(),
                selectedShapeIds = emptySet(),
                selectedTextIds = emptySet(),
                selectedStrokeIds = emptySet(),
                multiSelectMode = false,
                playheadTimeMs = 0L,
                isPlaying = false
            )
        }
    }

    fun addNewScene(name: String, durationMs: Long) {
        val proj = _uiState.value.project ?: return
        val ordered = proj.scenes.sortedBy { it.orderIndex }
        val newScene = Scene(
            id = UUID.randomUUID().toString(),
            orderIndex = ordered.size,
            name = name.ifBlank { "Scene ${ordered.size + 1}" },
            durationMs = durationMs.coerceAtLeast(1000L),
            layers = listOf(
                Layer(name = "Background", category = LayerCategory.BACKGROUND, orderIndex = 0),
                Layer(name = "Main Subject", category = LayerCategory.CHARACTER, orderIndex = 1)
            ),
            frames = listOf(AnimationFrame(orderIndex = 0, exposureFrames = 1)),
            transitionOut = SceneTransition(SceneTransitionType.CUT, 0L)
        )
        mutateProject { p ->
            val updated = (p.scenes.sortedBy { it.orderIndex } + newScene).mapIndexed { i, sc -> sc.copy(orderIndex = i) }
            p.copy(scenes = updated)
        }
        selectScene(newScene.id)
    }

    fun insertBlankSceneAfter(
        afterSceneId: String,
        name: String = "Manual Cut",
        durationMs: Long = 1500L
    ) {
        val proj = _uiState.value.project ?: return
        val ordered = proj.scenes.sortedBy { it.orderIndex }.toMutableList()
        val idx = ordered.indexOfFirst { it.id == afterSceneId }
        val insertIdx = if (idx >= 0) idx + 1 else ordered.size
        val newScene = Scene(
            id = UUID.randomUUID().toString(),
            orderIndex = insertIdx,
            name = name.ifBlank { "Manual Cut ${ordered.size + 1}" },
            durationMs = durationMs.coerceAtLeast(500L),
            layers = listOf(
                Layer(name = "Background", category = LayerCategory.BACKGROUND, orderIndex = 0),
                Layer(name = "Transition Art", category = LayerCategory.CHARACTER, orderIndex = 1)
            ),
            frames = listOf(
                AnimationFrame(orderIndex = 0, exposureFrames = 2, note = "Manual cut frame 1"),
                AnimationFrame(orderIndex = 1, exposureFrames = 2, note = "Manual cut frame 2")
            ),
            transitionOut = SceneTransition(SceneTransitionType.CUT, 0L),
            notes = "Manual drawn cut/transition beat (hard cut)."
        )
        ordered.add(insertIdx, newScene)
        val normalized = ordered.mapIndexed { i, sc -> sc.copy(orderIndex = i) }
        mutateProject { p -> p.copy(scenes = normalized) }
        selectScene(newScene.id)
        showToast("Inserted '${newScene.name}'")
    }

    fun moveScene(sceneId: String, delta: Int) {
        val proj = _uiState.value.project ?: return
        val ordered = proj.scenes.sortedBy { it.orderIndex }.toMutableList()
        val idx = ordered.indexOfFirst { it.id == sceneId }
        val target = (idx + delta).coerceIn(0, ordered.lastIndex)
        if (idx < 0 || idx == target) return
        swapScenes(idx, target)
    }

    fun swapScenes(indexA: Int, indexB: Int) {
        val proj = _uiState.value.project ?: return
        val ordered = proj.scenes.sortedBy { it.orderIndex }.toMutableList()
        if (indexA !in ordered.indices || indexB !in ordered.indices || indexA == indexB) return
        val tmp = ordered[indexA]
        ordered[indexA] = ordered[indexB]
        ordered[indexB] = tmp
        val normalized = ordered.mapIndexed { i, sc -> sc.copy(orderIndex = i) }
        mutateProject { p -> p.copy(scenes = normalized) }
        showToast("Updated scene order")
    }

    fun duplicateScene(sceneId: String) {
        val proj = _uiState.value.project ?: return
        val ordered = proj.scenes.sortedBy { it.orderIndex }.toMutableList()
        val idx = ordered.indexOfFirst { it.id == sceneId }
        if (idx < 0) return
        val src = ordered[idx]
        val layerIdMap = src.layers.associate { it.id to UUID.randomUUID().toString() }
        val dupLayers = src.layers.map { l ->
            val newLid = layerIdMap[l.id] ?: UUID.randomUUID().toString()
            l.copy(
                id = newLid,
                sceneStrokes = l.sceneStrokes.map { it.copy(id = UUID.randomUUID().toString(), layerId = newLid) },
                sceneShapes = l.sceneShapes.map { it.copy(id = UUID.randomUUID().toString(), layerId = newLid) },
                sceneTexts = l.sceneTexts.map { it.copy(id = UUID.randomUUID().toString(), layerId = newLid) },
                skeletonPose = l.skeletonPose?.copy(id = UUID.randomUUID().toString())
            )
        }
        val dupFrames = src.frames.map { f ->
            f.copy(
                id = UUID.randomUUID().toString(),
                layerDrawings = f.layerDrawings.mapKeys { (lid, _) -> layerIdMap[lid] ?: lid },
                layerShapes = f.layerShapes.mapKeys { (lid, _) -> layerIdMap[lid] ?: lid },
                layerPoses = f.layerPoses.mapKeys { (lid, _) -> layerIdMap[lid] ?: lid }
            )
        }
        val dupTracks = src.motionTracks.map { tr ->
            tr.copy(
                id = UUID.randomUUID().toString(),
                targetId = layerIdMap[tr.targetId] ?: tr.targetId,
                orbitConfig = tr.orbitConfig?.let { oc -> oc.copy(targetId = layerIdMap[oc.targetId] ?: oc.targetId) }
            )
        }
        val dupScene = src.copy(
            id = UUID.randomUUID().toString(),
            name = "${src.name} Copy",
            layers = dupLayers,
            frames = dupFrames,
            motionTracks = dupTracks,
            transitionOut = SceneTransition(SceneTransitionType.CUT, 0L)
        )
        ordered.add(idx + 1, dupScene)
        val normalized = ordered.mapIndexed { i, sc -> sc.copy(orderIndex = i) }
        mutateProject { p -> p.copy(scenes = normalized) }
        selectScene(dupScene.id)
        showToast("Duplicated '${src.name}'")
    }

    fun deleteScene(sceneId: String) {
        val proj = _uiState.value.project ?: return
        val ordered = proj.scenes.sortedBy { it.orderIndex }
        if (ordered.size <= 1) {
            showToast("At least one scene is required")
            return
        }
        val idx = ordered.indexOfFirst { it.id == sceneId }
        val remaining = ordered.filterNot { it.id == sceneId }.mapIndexed { i, sc -> sc.copy(orderIndex = i) }
        mutateProject { p -> p.copy(scenes = remaining) }
        if (_uiState.value.activeSceneId == sceneId) {
            val nextScene = remaining[idx.coerceAtMost(remaining.lastIndex)]
            selectScene(nextScene.id)
        }
        showToast("Removed scene")
    }

    fun addSceneFadeEffect(fadeIn: Boolean, fadeOut: Boolean, durationMs: Long = 500L) {
        val state = _uiState.value
        val proj = state.project ?: return
        val scene = state.activeScene ?: return
        val sceneDur = scene.effectiveDurationMs(proj.fps).coerceAtLeast(600L)
        val fadeDur = durationMs.coerceIn(200L, sceneDur / 2L)
        val foregroundLayers = scene.orderedLayers().filter { it.category != LayerCategory.BACKGROUND && it.visible }
            .ifEmpty { scene.orderedLayers().takeLast(1) }
        if (foregroundLayers.isEmpty()) return

        mutateActiveScene(recordUndo = true) { sc ->
            var updatedTracks = sc.motionTracks.filterNot {
                it.actionType == MotionActionType.FADE && (it.name.startsWith("Fade In") || it.name.startsWith("Fade Out"))
            }
            foregroundLayers.forEach { layer ->
                if (fadeIn) {
                    updatedTracks = updatedTracks + MotionTrack(
                        name = "Fade In (${layer.name})",
                        targetType = MotionTargetType.LAYER,
                        targetId = layer.id,
                        actionType = MotionActionType.FADE,
                        startTimeMs = 0L,
                        durationMs = fadeDur,
                        easing = EasingType.EASE_OUT,
                        keyframes = listOf(
                            MotionKeyframe(timeMs = 0L, opacity = 0f),
                            MotionKeyframe(timeMs = fadeDur, opacity = 1f)
                        ),
                        description = "Smooth manual fade in over ${fadeDur}ms"
                    )
                }
                if (fadeOut) {
                    val startMs = (sceneDur - fadeDur).coerceAtLeast(0L)
                    updatedTracks = updatedTracks + MotionTrack(
                        name = "Fade Out (${layer.name})",
                        targetType = MotionTargetType.LAYER,
                        targetId = layer.id,
                        actionType = MotionActionType.FADE,
                        startTimeMs = startMs,
                        durationMs = fadeDur,
                        easing = EasingType.EASE_IN,
                        keyframes = listOf(
                            MotionKeyframe(timeMs = 0L, opacity = 1f),
                            MotionKeyframe(timeMs = fadeDur, opacity = 0f)
                        ),
                        description = "Smooth manual fade out over last ${fadeDur}ms"
                    )
                }
            }
            sc.copy(motionTracks = updatedTracks)
        }
        val label = when {
            fadeIn && fadeOut -> "Fade In + Out"
            fadeIn -> "Fade In"
            else -> "Fade Out"
        }
        showToast("Added $label effect")
    }

    fun mergeSceneWithNext(sceneId: String) {
        val proj = _uiState.value.project ?: return
        val ordered = proj.scenes.sortedBy { it.orderIndex }
        val idx = ordered.indexOfFirst { it.id == sceneId }
        if (idx < 0 || idx >= ordered.lastIndex) {
            showToast("No next scene to merge with")
            return
        }
        val merged = mergeSceneSequence(
            scenesToMerge = listOf(ordered[idx], ordered[idx + 1]),
            mergedName = "${ordered[idx].name} + ${ordered[idx + 1].name}",
            fps = proj.fps
        )
        val newList = buildList {
            addAll(ordered.subList(0, idx))
            add(merged)
            addAll(ordered.subList(idx + 2, ordered.size))
        }.mapIndexed { i, sc -> sc.copy(orderIndex = i) }
        mutateProject { p -> p.copy(scenes = newList) }
        selectScene(merged.id)
        showToast("Merged scenes into '${merged.name}'")
    }

    fun combineAllScenesIntoOne() {
        val proj = _uiState.value.project ?: return
        val ordered = proj.scenes.sortedBy { it.orderIndex }
        if (ordered.size <= 1) {
            showToast("All scenes are already combined into one continuous scene")
            return
        }
        val merged = mergeSceneSequence(
            scenesToMerge = ordered,
            mergedName = "Whole Reel (Connected)",
            fps = proj.fps
        )
        mutateProject { p -> p.copy(scenes = listOf(merged.copy(orderIndex = 0))) }
        selectScene(merged.id)
        _uiState.update { it.copy(connectedScenesMode = true) }
        showToast("Combined ${ordered.size} scenes into one continuous editable scene")
    }

    private fun mergeSceneSequence(
        scenesToMerge: List<Scene>,
        mergedName: String,
        fps: Int
    ): Scene {
        val safeFps = fps.coerceAtLeast(1)
        val mergedLayers = mutableListOf<Layer>()
        val mergedFrames = mutableListOf<AnimationFrame>()
        val mergedTracks = mutableListOf<MotionTrack>()
        val mergedCamKeyframes = mutableListOf<CameraKeyframe>()
        val voiceoverParts = mutableListOf<String>()
        val captionParts = mutableListOf<String>()
        val musicParts = mutableListOf<String>()
        val noteParts = mutableListOf<String>()
        var offsetMs = 0L

        scenesToMerge.forEachIndexed { scIdx, sc ->
            val scDur = sc.effectiveDurationMs(safeFps)
            val layerIdMap = sc.layers.associate { it.id to "merged_${scIdx}_${it.id}" }

            // Create clean unified layers preserving poses & groups, and push sceneStrokes/Shapes into this scene's frames
            val orderedSrcLayers = sc.orderedLayers()
            val mappedLayers = orderedSrcLayers.map { l ->
                val newLid = layerIdMap[l.id]!!
                l.copy(
                    id = newLid,
                    name = if (scenesToMerge.size > 1) "${sc.orderIndex + 1}·${l.name}" else l.name,
                    orderIndex = mergedLayers.size,
                    sceneStrokes = emptyList(),
                    sceneShapes = emptyList(),
                    sceneTexts = l.sceneTexts.map { tx ->
                        tx.copy(
                            id = UUID.randomUUID().toString(),
                            layerId = newLid,
                            rigAttachment = tx.rigAttachment?.copy(
                                rigLayerId = tx.rigAttachment.rigLayerId?.let { layerIdMap[it] ?: it }
                            )
                        )
                    },
                    skeletonPose = l.skeletonPose?.copy(id = UUID.randomUUID().toString())
                )
            }
            mergedLayers.addAll(mappedLayers)

            val srcFrames = sc.orderedFrames().ifEmpty { listOf(AnimationFrame(orderIndex = 0, exposureFrames = 1)) }
            val remappedFrames = srcFrames.mapIndexed { fIdx, f ->
                val drawingsMap = mutableMapOf<String, List<DrawingStroke>>()
                val shapesMap = mutableMapOf<String, List<SceneShape>>()
                orderedSrcLayers.forEach { srcLayer ->
                    val newLid = layerIdMap[srcLayer.id]!!
                    val combinedStrokes = (srcLayer.sceneStrokes + f.layerDrawings[srcLayer.id].orEmpty()).map { st ->
                        st.copy(
                            id = UUID.randomUUID().toString(),
                            layerId = newLid,
                            rigAttachment = st.rigAttachment?.copy(
                                rigLayerId = st.rigAttachment.rigLayerId?.let { layerIdMap[it] ?: it }
                            )
                        )
                    }
                    if (combinedStrokes.isNotEmpty()) drawingsMap[newLid] = combinedStrokes

                    val combinedShapes = (srcLayer.sceneShapes + f.layerShapes[srcLayer.id].orEmpty()).map { sh ->
                        sh.copy(
                            id = UUID.randomUUID().toString(),
                            layerId = newLid,
                            rigAttachment = sh.rigAttachment?.copy(
                                rigLayerId = sh.rigAttachment.rigLayerId?.let { layerIdMap[it] ?: it }
                            )
                        )
                    }
                    if (combinedShapes.isNotEmpty()) shapesMap[newLid] = combinedShapes
                }
                val posesMap = buildMap {
                    orderedSrcLayers.forEach { srcLayer ->
                        val newLid = layerIdMap[srcLayer.id]!!
                        val pose = f.layerPoses[srcLayer.id] ?: srcLayer.skeletonPose
                        if (pose != null) put(newLid, pose.copy(id = UUID.randomUUID().toString()))
                    }
                }
                f.copy(
                    id = UUID.randomUUID().toString(),
                    orderIndex = mergedFrames.size + fIdx,
                    note = f.note.ifBlank { sc.name },
                    layerDrawings = drawingsMap,
                    layerShapes = shapesMap,
                    layerPoses = posesMap
                )
            }
            mergedFrames.addAll(remappedFrames)

            sc.motionTracks.forEach { tr ->
                val mappedTarget = layerIdMap[tr.targetId] ?: tr.targetId
                mergedTracks += tr.copy(
                    id = UUID.randomUUID().toString(),
                    targetId = mappedTarget,
                    startTimeMs = offsetMs + tr.startTimeMs,
                    orbitConfig = tr.orbitConfig?.let { oc -> oc.copy(targetId = layerIdMap[oc.targetId] ?: oc.targetId) },
                    parentTargetId = tr.parentTargetId?.let { layerIdMap[it] ?: it }
                )
            }

            // Scope SceneTexts from each source scene to that scene's time window using visibility tracks
            orderedSrcLayers.filter { it.sceneTexts.isNotEmpty() }.forEach { srcLayer ->
                val newLid = layerIdMap[srcLayer.id]!!
                val kfs = buildList {
                    if (offsetMs > 0L) {
                        add(MotionKeyframe(timeMs = 0L, opacity = 0f))
                        add(MotionKeyframe(timeMs = (offsetMs - 1L).coerceAtLeast(0L), opacity = 0f))
                    }
                    add(MotionKeyframe(timeMs = offsetMs, opacity = 1f))
                    add(MotionKeyframe(timeMs = offsetMs + scDur - 1L, opacity = 1f))
                    if (scIdx < scenesToMerge.lastIndex) {
                        add(MotionKeyframe(timeMs = offsetMs + scDur, opacity = 0f))
                    }
                }
                mergedTracks += MotionTrack(
                    name = "${sc.name} Caption Visibility",
                    targetType = MotionTargetType.LAYER,
                    targetId = newLid,
                    actionType = MotionActionType.FADE,
                    startTimeMs = 0L,
                    durationMs = offsetMs + scDur,
                    keyframes = kfs,
                    description = "Shows ${sc.name} text during ${offsetMs}ms..${offsetMs + scDur}ms"
                )
            }

            sc.cameraTrack.keyframes.forEach { kf ->
                mergedCamKeyframes += kf.copy(
                    id = UUID.randomUUID().toString(),
                    timeMs = offsetMs + kf.timeMs
                )
            }

            if (sc.audioPlan.voiceoverText.isNotBlank()) voiceoverParts += "[${sc.name}] ${sc.audioPlan.voiceoverText}"
            if (sc.audioPlan.captionsText.isNotBlank()) captionParts += sc.audioPlan.captionsText
            if (sc.audioPlan.musicTrackNote.isNotBlank()) musicParts += sc.audioPlan.musicTrackNote
            if (sc.notes.isNotBlank()) noteParts += "${sc.name}: ${sc.notes}"

            offsetMs += scDur
        }

        val normalizedLayers = mergedLayers.mapIndexed { i, l -> l.copy(orderIndex = i) }
        val normalizedFrames = mergedFrames.mapIndexed { i, f -> f.copy(orderIndex = i) }
        val firstBg = scenesToMerge.first().background
        return Scene(
            id = UUID.randomUUID().toString(),
            orderIndex = scenesToMerge.first().orderIndex,
            name = mergedName,
            durationMs = offsetMs.coerceAtLeast(1000L),
            background = firstBg,
            layers = normalizedLayers,
            frames = normalizedFrames,
            motionTracks = mergedTracks,
            cameraTrack = com.example.domain.model.CameraTrack(
                keyframes = mergedCamKeyframes.sortedBy { it.timeMs },
                directionSummary = "Combined camera timeline across ${scenesToMerge.size} connected scenes"
            ),
            audioPlan = AudioPlan(
                voiceoverText = voiceoverParts.joinToString(" "),
                captionsText = captionParts.joinToString(" | "),
                musicTrackNote = musicParts.distinct().joinToString(" + ")
            ),
            transitionOut = SceneTransition(SceneTransitionType.CUT, 0L),
            notes = noteParts.joinToString(" | ")
        )
    }

    fun updateSceneReelDetails(
        durationMs: Long,
        audioPlan: AudioPlan,
        notes: String
    ) {
        mutateActiveScene { sc ->
            sc.copy(
                durationMs = durationMs.coerceAtLeast(500L),
                audioPlan = audioPlan,
                transitionOut = SceneTransition(SceneTransitionType.CUT, 0L),
                notes = notes
            )
        }
    }

    // --- Tutorial & Toast ---

    fun startTutorial() {
        _uiState.update { it.copy(tutorialStep = 0) }
    }

    fun advanceTutorial() {
        val cur = _uiState.value.tutorialStep ?: return
        if (cur >= 9) {
            dismissTutorial()
        } else {
            _uiState.update { it.copy(tutorialStep = cur + 1) }
        }
    }

    fun dismissTutorial() {
        _uiState.update { it.copy(tutorialStep = null) }
        mutateProject(recordUndo = false) { it.copy(tutorialCompleted = true) }
        viewModelScope.launch { store.setTutorialCompleted(true) }
    }

    fun showToast(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun unlabelledWarningCount(): Int {
        val proj = _uiState.value.project ?: return 0
        return SpatialMapGenerator.collectWarnings(proj).size
    }

    override fun onCleared() {
        autosaveJob?.cancel()
        playbackJob?.cancel()
        quickShapeBadgeJob?.cancel()
        super.onCleared()
    }
}
