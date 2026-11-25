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
import kotlin.math.hypot

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
    val selectedShapeId: String? = null,
    val selectedTextId: String? = null,
    val selectedStrokeIds: Set<String> = emptySet(),
    val selectedMotionTrackId: String? = null,
    val activeTool: DrawingTool = DrawingTool.PEN,
    val brushColorHex: String = "#22242C",
    val brushSize: Float = 10f,
    val brushOpacity: Float = 1f,
    val viewport: CanvasViewportState = CanvasViewportState(),
    val showGrid: Boolean = false,
    val showSafeArea: Boolean = true,
    val onionSkinEnabled: Boolean = true,
    val isPlaying: Boolean = false,
    val loopPlayback: Boolean = true,
    val reversePlayback: Boolean = false,
    val playheadTimeMs: Long = 0L,
    val timelineExpanded: Boolean = true,
    val advancedMode: Boolean = false,
    val isRecordingPathForTrackId: String? = null,
    val saveStatus: SaveStatus = SaveStatus.SAVED,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val activeSheet: StudioSheet = StudioSheet.NONE,
    val tutorialStep: Int? = null,
    val snackbarMessage: String? = null
) {
    val activeScene: Scene?
        get() = project?.scenes?.find { it.id == activeSceneId } ?: project?.scenes?.firstOrNull()

    val selectedLayer: Layer?
        get() = activeScene?.layers?.find { it.id == selectedLayerId } ?: activeScene?.orderedLayers()?.lastOrNull()

    val selectedFrame: AnimationFrame?
        get() = activeScene?.frames?.find { it.id == selectedFrameId } ?: activeScene?.orderedFrames()?.firstOrNull()

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
}

class StudioViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ProjectStore(application)
    private val undoRedo = UndoRedoManager()
    private var autosaveJob: Job? = null
    private var playbackJob: Job? = null

    private val _uiState = MutableStateFlow(StudioUiState())
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    fun loadProject(projectId: String) {
        viewModelScope.launch {
            undoRedo.clear()
            _uiState.update { it.copy(isLoading = true) }
            val loaded = store.loadProject(projectId)
                ?: SampleProjects.createParkButterflySample().also { store.saveProject(it) }
            val firstScene = loaded.scenes.sortedBy { it.orderIndex }.firstOrNull()
            val firstLayer = firstScene?.orderedLayers()?.lastOrNull()
            val firstFrame = firstScene?.orderedFrames()?.firstOrNull()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    project = loaded,
                    activeSceneId = firstScene?.id ?: "",
                    selectedLayerId = firstLayer?.id ?: "",
                    selectedFrameId = firstFrame?.id ?: "",
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

    fun undo() {
        val current = _uiState.value.project ?: return
        val prev = undoRedo.undo(current) ?: return
        restoreProjectSnapshot(prev)
    }

    fun redo() {
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
            ?: scene?.orderedLayers()?.lastOrNull()?.id.orEmpty()

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
        _uiState.update {
            it.copy(
                activeTool = tool,
                isRecordingPathForTrackId = null,
                selectedShapeId = if (tool == DrawingTool.SELECT) it.selectedShapeId else null,
                selectedTextId = if (tool == DrawingTool.SELECT) it.selectedTextId else null
            )
        }
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
        _uiState.update { it.copy(viewport = viewport) }
    }

    fun fitCanvasToViewport(viewWidth: Float, viewHeight: Float) {
        val proj = _uiState.value.project ?: return
        val fitted = CanvasTransformEngine.fitToViewport(
            docWidth = proj.canvasWidth,
            docHeight = proj.canvasHeight,
            viewportWidth = viewWidth,
            viewportHeight = viewHeight
        )
        _uiState.update { it.copy(viewport = fitted) }
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
        _uiState.update { it.copy(activeSheet = StudioSheet.NONE) }
    }

    fun renameProject(newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        mutateProject { it.copy(name = trimmed) }
    }

    // --- Drawing, Erasing, Shapes, Fill, Text, Select ---

    fun addCompletedStroke(rawPoints: List<StrokePoint>) {
        if (rawPoints.isEmpty()) return
        val state = _uiState.value
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

        val stroke = DrawingStroke(
            id = UUID.randomUUID().toString(),
            layerId = layer.id,
            tool = state.activeTool,
            colorHex = state.brushColorHex,
            strokeWidth = state.brushSize,
            opacity = state.brushOpacity,
            points = rawPoints
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
        _uiState.update { it.copy(selectedShapeId = shape.id, selectedTextId = null) }
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
        _uiState.update { it.copy(selectedTextId = item.id, selectedShapeId = null) }
    }

    fun handleFillTap(docX: Float, docY: Float) {
        val state = _uiState.value
        val scene = state.activeScene ?: return
        val fillColor = state.brushColorHex

        // 1. Hit-test shapes top-down
        val hitShape = scene.orderedLayers().reversed()
            .filter { it.visible && !it.locked }
            .flatMap { it.sceneShapes.reversed() }
            .firstOrNull { CanvasTransformEngine.hitTestShape(it, docX, docY) }

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
                if (stroke.points.size < 4) return@firstOrNull false
                val first = stroke.points.first()
                val last = stroke.points.last()
                val isClosed = hypot(first.x - last.x, first.y - last.y) < 120f
                isClosed && CanvasTransformEngine.pointInPolygon(docX, docY, stroke.points.map { it.x to it.y })
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
        val scene = _uiState.value.activeScene ?: return
        val layersTopDown = scene.orderedLayers().reversed().filter { it.visible }
        val hitShape = layersTopDown.flatMap { it.sceneShapes.reversed() }
            .firstOrNull { CanvasTransformEngine.hitTestShape(it, docX, docY) }
        val picked = when {
            hitShape != null -> hitShape.fillColorHex ?: hitShape.strokeColorHex
            else -> {
                val hitStroke = layersTopDown.flatMap { it.sceneStrokes.reversed() }
                    .firstOrNull { CanvasTransformEngine.hitTestStroke(it, docX, docY) }
                hitStroke?.colorHex ?: scene.background.primaryColorHex
            }
        }
        _uiState.update { it.copy(brushColorHex = picked, snackbarMessage = "Picked color $picked") }
    }

    fun handleSelectTap(docX: Float, docY: Float) {
        val scene = _uiState.value.activeScene ?: return
        val visibleLayers = scene.orderedLayers().reversed().filter { it.visible }

        val hitText = visibleLayers.flatMap { it.sceneTexts.reversed() }
            .firstOrNull { CanvasTransformEngine.hitTestText(it, docX, docY) }
        if (hitText != null) {
            _uiState.update {
                it.copy(
                    selectedTextId = hitText.id,
                    selectedShapeId = null,
                    selectedStrokeIds = emptySet(),
                    selectedLayerId = hitText.layerId
                )
            }
            return
        }

        val hitShape = visibleLayers.flatMap { it.sceneShapes.reversed() }
            .firstOrNull { CanvasTransformEngine.hitTestShape(it, docX, docY) }
        if (hitShape != null) {
            _uiState.update {
                it.copy(
                    selectedShapeId = hitShape.id,
                    selectedTextId = null,
                    selectedStrokeIds = emptySet(),
                    selectedLayerId = hitShape.layerId
                )
            }
            return
        }

        val hitStroke = visibleLayers.flatMap { it.sceneStrokes.reversed() }
            .firstOrNull { CanvasTransformEngine.hitTestStroke(it, docX, docY) }
        if (hitStroke != null) {
            _uiState.update {
                it.copy(
                    selectedStrokeIds = setOf(hitStroke.id),
                    selectedShapeId = null,
                    selectedTextId = null,
                    selectedLayerId = hitStroke.layerId
                )
            }
            return
        }

        _uiState.update {
            it.copy(selectedShapeId = null, selectedTextId = null, selectedStrokeIds = emptySet())
        }
    }

    fun selectByLasso(polygon: List<Pair<Float, Float>>) {
        if (polygon.size < 3) return
        val scene = _uiState.value.activeScene ?: return
        val activeLayer = _uiState.value.selectedLayer ?: return
        val matchedIds = activeLayer.sceneStrokes.filter { st ->
            st.points.any { pt -> CanvasTransformEngine.pointInPolygon(pt.x, pt.y, polygon) }
        }.map { it.id }.toSet()
        val matchedShape = activeLayer.sceneShapes.firstOrNull { sh ->
            CanvasTransformEngine.pointInPolygon(sh.x + sh.width / 2f, sh.y + sh.height / 2f, polygon)
        }
        _uiState.update {
            it.copy(
                selectedStrokeIds = matchedIds,
                selectedShapeId = matchedShape?.id,
                snackbarMessage = if (matchedIds.isNotEmpty() || matchedShape != null) "Selected ${matchedIds.size + if (matchedShape != null) 1 else 0} item(s)" else "Nothing inside lasso"
            )
        }
    }

    fun moveSelectedBy(dx: Float, dy: Float, recordUndo: Boolean = true) {
        val state = _uiState.value
        val shapeId = state.selectedShapeId
        val textId = state.selectedTextId
        val strokeIds = state.selectedStrokeIds
        val proj = state.project ?: return
        if (shapeId == null && textId == null && strokeIds.isEmpty()) return

        mutateActiveScene(recordUndo = recordUndo) { scene ->
            scene.copy(
                layers = scene.layers.map { l ->
                    if (l.locked) l else l.copy(
                        sceneShapes = l.sceneShapes.map { s ->
                            if (s.id == shapeId) s.copy(x = s.x + dx, y = s.y + dy) else s
                        },
                        sceneTexts = l.sceneTexts.map { t ->
                            if (t.id == textId) t.copy(x = t.x + dx, y = t.y + dy) else t
                        },
                        sceneStrokes = l.sceneStrokes.map { st ->
                            if (st.id in strokeIds) {
                                st.copy(points = st.points.map { p ->
                                    CanvasTransformEngine.makeNormalizedPoint(
                                        p.x + dx, p.y + dy, p.pressure, proj.canvasWidth, proj.canvasHeight
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
        val shapeId = _uiState.value.selectedShapeId ?: return
        mutateActiveScene { scene ->
            scene.copy(
                layers = scene.layers.map { l ->
                    l.copy(
                        sceneShapes = l.sceneShapes.map { s ->
                            if (s.id == shapeId) {
                                val sf = scaleFactor ?: 1f
                                val newW = (s.width * sf).coerceIn(20f, 1800f)
                                val newH = (s.height * sf).coerceIn(20f, 1800f)
                                s.copy(
                                    width = newW,
                                    height = newH,
                                    rotationDeg = (s.rotationDeg + (rotationDeltaDeg ?: 0f)) % 360f,
                                    strokeColorHex = colorHex ?: s.strokeColorHex,
                                    fillColorHex = if (colorHex != null && s.filled) colorHex else s.fillColorHex
                                )
                            } else s
                        }
                    )
                }
            )
        }
    }

    fun updateSelectedText(newText: String? = null, colorHex: String? = null, animStyle: TextAnimationStyle? = null) {
        val textId = _uiState.value.selectedTextId ?: return
        mutateActiveScene { scene ->
            scene.copy(
                layers = scene.layers.map { l ->
                    l.copy(
                        sceneTexts = l.sceneTexts.map { t ->
                            if (t.id == textId) {
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

    fun deleteSelection() {
        val state = _uiState.value
        val shapeId = state.selectedShapeId
        val textId = state.selectedTextId
        val strokeIds = state.selectedStrokeIds
        if (shapeId == null && textId == null && strokeIds.isEmpty()) return
        mutateActiveScene { scene ->
            scene.copy(
                layers = scene.layers.map { l ->
                    l.copy(
                        sceneShapes = l.sceneShapes.filterNot { it.id == shapeId },
                        sceneTexts = l.sceneTexts.filterNot { it.id == textId },
                        sceneStrokes = l.sceneStrokes.filterNot { it.id in strokeIds }
                    )
                }
            )
        }
        _uiState.update { it.copy(selectedShapeId = null, selectedTextId = null, selectedStrokeIds = emptySet()) }
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
                        if (l.id == layer.id) l.copy(sceneStrokes = emptyList(), sceneShapes = emptyList(), sceneTexts = emptyList()) else l
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
                        l.copy(sceneStrokes = emptyList(), sceneShapes = emptyList(), sceneTexts = emptyList())
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
                selectedShapeId = null,
                selectedTextId = null,
                selectedStrokeIds = emptySet(),
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

    fun updateLayerPoseJoint(layerId: String, jointName: String, x: Float, y: Float, poseNote: String? = null) {
        mutateActiveScene { scene ->
            scene.copy(
                layers = scene.layers.map { l ->
                    if (l.id == layerId) {
                        val basePose = l.skeletonPose ?: SkeletonPose(joints = defaultStickmanJoints(540f, 960f))
                        val updatedPose = basePose.copy(
                            note = poseNote ?: basePose.note,
                            joints = basePose.joints + (jointName to JointPoint(x, y))
                        )
                        l.copy(skeletonPose = updatedPose)
                    } else l
                }
            )
        }
    }

    fun resetLayerPoseToDefault(layerId: String) {
        val proj = _uiState.value.project ?: return
        val centerX = proj.canvasWidth / 2f
        val centerY = proj.canvasHeight / 2f
        val defaultJoints = defaultStickmanJoints(centerX, centerY)
        mutateActiveScene(recordUndo = true) { scene ->
            scene.copy(
                layers = scene.layers.map { l ->
                    if (l.id == layerId) {
                        val basePose = l.skeletonPose ?: SkeletonPose(name = "Starter Pose", joints = defaultJoints)
                        l.copy(skeletonPose = basePose.copy(joints = defaultJoints))
                    } else l
                }
            )
        }
        showToast("2D pose reset to default")
    }

    // --- Frame-by-Frame Timeline Operations (Single Chronological Source of Truth) ---

    fun selectFrame(frameId: String) {
        _uiState.update { it.copy(selectedFrameId = frameId, isPlaying = false) }
    }

    fun stepFrame(delta: Int) {
        val frames = _uiState.value.activeScene?.orderedFrames() ?: return
        val idx = frames.indexOfFirst { it.id == _uiState.value.selectedFrameId }
        val next = ((if (idx < 0) 0 else idx) + delta).coerceIn(0, frames.lastIndex)
        _uiState.update { it.copy(selectedFrameId = frames[next].id, isPlaying = false) }
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
        val tick = TimelineEngine.frameAtTimeMs(scene, proj.fps, clamped, state.reversePlayback)
        _uiState.update {
            it.copy(
                playheadTimeMs = clamped,
                selectedFrameId = tick?.frame?.id ?: it.selectedFrameId
            )
        }
    }

    private fun startPlayback() {
        val state = _uiState.value
        val proj = state.project ?: return
        val scene = state.activeScene ?: return
        val fps = proj.fps.coerceAtLeast(1)
        val totalDuration = scene.effectiveDurationMs(fps)
        val stepMs = (1000L / fps).coerceAtLeast(16L)

        playbackJob?.cancel()
        _uiState.update { it.copy(isPlaying = true, playheadTimeMs = 0L) }
        playbackJob = viewModelScope.launch {
            var cursor = 0L
            while (isActive) {
                val curState = _uiState.value
                val curScene = curState.activeScene ?: break
                val curProj = curState.project ?: break
                val ticks = TimelineEngine.buildPlaybackTicks(curScene.frames, curState.reversePlayback)
                val framesDur = curScene.framesDurationMs(curProj.fps)
                val tick = if (ticks.isNotEmpty() && framesDur > 0) {
                    val idx = ((cursor % framesDur) * ticks.size / framesDur).toInt().coerceIn(0, ticks.lastIndex)
                    ticks[idx]
                } else null

                _uiState.update {
                    it.copy(
                        playheadTimeMs = cursor,
                        selectedFrameId = tick?.frame?.id ?: it.selectedFrameId
                    )
                }

                delay(stepMs)
                cursor += stepMs
                if (cursor >= totalDuration) {
                    if (_uiState.value.loopPlayback) {
                        cursor = 0L
                    } else {
                        _uiState.update { it.copy(isPlaying = false, playheadTimeMs = 0L) }
                        break
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

    // --- Multi-Scene Reel Planner ---

    fun selectScene(sceneId: String) {
        val scene = _uiState.value.project?.scenes?.find { it.id == sceneId } ?: return
        _uiState.update {
            it.copy(
                activeSceneId = scene.id,
                selectedLayerId = scene.orderedLayers().lastOrNull()?.id.orEmpty(),
                selectedFrameId = scene.orderedFrames().firstOrNull()?.id.orEmpty(),
                playheadTimeMs = 0L,
                isPlaying = false
            )
        }
    }

    fun addNewScene(name: String, durationMs: Long) {
        val proj = _uiState.value.project ?: return
        val newScene = Scene(
            id = UUID.randomUUID().toString(),
            orderIndex = proj.scenes.size,
            name = name.ifBlank { "Scene ${proj.scenes.size + 1}" },
            durationMs = durationMs.coerceAtLeast(1000L),
            layers = listOf(
                Layer(name = "Background", category = LayerCategory.BACKGROUND, orderIndex = 0),
                Layer(name = "Main Subject", category = LayerCategory.CHARACTER, orderIndex = 1)
            ),
            frames = listOf(AnimationFrame(orderIndex = 0, exposureFrames = 1))
        )
        mutateProject { p -> p.copy(scenes = p.scenes + newScene) }
        selectScene(newScene.id)
    }

    fun updateSceneReelDetails(
        durationMs: Long,
        audioPlan: AudioPlan,
        transition: SceneTransition,
        notes: String
    ) {
        mutateActiveScene { sc ->
            sc.copy(
                durationMs = durationMs.coerceAtLeast(500L),
                audioPlan = audioPlan,
                transitionOut = transition,
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
}
