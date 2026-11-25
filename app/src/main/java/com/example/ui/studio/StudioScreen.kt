package com.example.ui.studio

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixNormal
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Create
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.DrawingTool
import com.example.domain.model.StudioMode
import com.example.ui.components.parseHexColor
import com.example.ui.theme.InkCharcoal
import com.example.ui.theme.InkSlate
import com.example.ui.theme.PaperBorder
import com.example.ui.theme.PaperCream
import com.example.ui.theme.PaperLinen
import com.example.ui.theme.PaperSheet
import com.example.ui.theme.StudioButterSoft
import com.example.ui.theme.StudioCoral
import com.example.ui.theme.StudioCoralSoft
import com.example.ui.theme.StudioLavender
import com.example.ui.theme.StudioLavenderSoft
import com.example.ui.theme.StudioMint
import com.example.ui.theme.StudioMintSoft
import com.example.ui.theme.StudioSwatches

private val TUTORIAL_TIPS = listOf(
    "1/10 · Welcome! Pick a color dot below and draw directly on the warm paper canvas.",
    "2/10 · Sketch a simple character or stick figure using Pen or Brush.",
    "3/10 · Give your scene a world: tap More -> Backdrop to set sky, ground, or photo.",
    "4/10 · Tap Layers in the bottom bar to separate Characters, Props, and Backgrounds.",
    "5/10 · Switch to 'Draw Frames' above, then tap 'Blank +' to add your next cel.",
    "6/10 · Tap 'Duplicate' on a frame to copy your drawing and adjust the pose.",
    "7/10 · Tap Play to preview frames left-to-right (with Onion Skin ghosting!).",
    "8/10 · Switch to 'Motion Studio' -> '+ Motion' to orbit, bounce, morph, or draw a path.",
    "9/10 · Tap the sparkles icon (AI Describe) so text-only OpenCode understands your art.",
    "10/10 · Tap Export in the top bar to generate your complete OpenCode package!"
)

@Composable
fun StudioScreen(
    projectId: String,
    viewModel: StudioViewModel,
    onBackToLibrary: () -> Unit,
    onOpenExport: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    var showMoreMenu by remember { mutableStateOf(false) }
    var showShapesMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
    }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.flushSave()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            viewModel.flushSave()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler(enabled = state.activeSheet != StudioSheet.NONE || state.isRecordingPathForTrackId != null) {
        if (state.activeSheet != StudioSheet.NONE) {
            viewModel.closeSheet()
        } else if (state.isRecordingPathForTrackId != null) {
            viewModel.setTool(DrawingTool.PEN)
        }
    }

    Scaffold(
        containerColor = PaperCream,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. Top Studio Toolbar
            StudioTopBar(
                state = state,
                onBack = {
                    viewModel.flushSave()
                    onBackToLibrary()
                },
                onOpenPlanner = { viewModel.openSheet(StudioSheet.SCENE_PLANNER) },
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                onExport = {
                    viewModel.flushSave()
                    state.project?.id?.let(onOpenExport)
                },
                showMoreMenu = showMoreMenu,
                onToggleMoreMenu = { showMoreMenu = it },
                onOpenAiDescribe = { viewModel.openSheet(StudioSheet.AI_INSPECTOR) },
                onOpenBackdrop = { viewModel.openSheet(StudioSheet.BACKGROUND) },
                onOpenMotion = { viewModel.openSheet(StudioSheet.MOTION_ACTION) },
                onToggleAdvanced = { viewModel.toggleAdvancedMode() },
                onClearLayer = { viewModel.clearActiveLayerOrFrame() },
                onRequestClearAll = { showClearConfirmDialog = true },
                onRenameProject = { showRenameDialog = true },
                onReplayTutorial = { viewModel.startTutorial() }
            )

            // 2. Mode Switcher (Scene Layout | Draw Frames | Motion Studio)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PaperCream)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = PaperLinen,
                    border = BorderStroke(1.dp, PaperBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        StudioMode.entries.forEach { mode ->
                            val selected = state.activeMode == mode
                            Surface(
                                shape = RoundedCornerShape(11.dp),
                                color = if (selected) PaperSheet else Color.Transparent,
                                shadowElevation = if (selected) 1.dp else 0.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { viewModel.setMode(mode) }
                                    .testTag("mode_tab_${mode.name.lowercase()}")
                            ) {
                                Text(
                                    mode.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) StudioCoral else InkSlate,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Quick AI Describe Sparkle pill
                Surface(
                    shape = CircleShape,
                    color = StudioLavenderSoft,
                    border = BorderStroke(1.dp, PaperBorder),
                    modifier = Modifier
                        .clickable { viewModel.openSheet(StudioSheet.AI_INSPECTOR) }
                        .testTag("studio_ai_describe_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = "Describe for AI", tint = StudioLavender, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("AI Note", style = MaterialTheme.typography.labelMedium, color = StudioLavender)
                    }
                }
            }

            // Optional Beginner Tutorial Strip (compact, skippable)
            AnimatedVisibility(visible = state.tutorialStep != null) {
                val step = state.tutorialStep ?: 0
                Surface(
                    color = StudioButterSoft,
                    border = BorderStroke(1.dp, PaperBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            TUTORIAL_TIPS.getOrElse(step) { TUTORIAL_TIPS.last() },
                            style = MaterialTheme.typography.bodySmall,
                            color = InkCharcoal,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.advanceTutorial() }) {
                            Text(if (step >= 9) "Finish" else "Next")
                        }
                        IconButton(onClick = { viewModel.dismissTutorial() }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Dismiss tutorial", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // 3. Adaptive Main Canvas + Floating Tool Dock
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val isWide = maxWidth >= 680.dp

                StudioCanvas(
                    state = state,
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )

                // Floating Tool Dock (Vertical rail on left in wide/landscape, compact bottom pill in portrait)
                if (isWide) {
                    ToolDockVertical(
                        state = state,
                        viewModel = viewModel,
                        showShapesMenu = showShapesMenu,
                        onToggleShapes = { showShapesMenu = it },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 10.dp)
                    )
                } else {
                    ToolDockHorizontal(
                        state = state,
                        viewModel = viewModel,
                        showShapesMenu = showShapesMenu,
                        onToggleShapes = { showShapesMenu = it },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                }
            }

            // 4. Collapsible Bottom Timeline & Tracks Workspace
            TimelinePanel(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.navigationBarsPadding()
            )
        }
    }

    // Active Contextual Bottom Sheets
    when (state.activeSheet) {
        StudioSheet.LAYERS -> LayersSheet(state, viewModel, onDismiss = { viewModel.closeSheet() })
        StudioSheet.BACKGROUND -> BackgroundEditorSheet(state, viewModel, onDismiss = { viewModel.closeSheet() })
        StudioSheet.AI_INSPECTOR -> AiInspectorSheet(state, viewModel, onDismiss = { viewModel.closeSheet() })
        StudioSheet.MOTION_ACTION -> MotionActionSheet(state, viewModel, onDismiss = { viewModel.closeSheet() })
        StudioSheet.SCENE_PLANNER -> ScenePlannerSheet(state, viewModel, onDismiss = { viewModel.closeSheet() })
        StudioSheet.COLOR_PICKER -> ColorAndBrushSheet(state, viewModel, onDismiss = { viewModel.closeSheet() })
        StudioSheet.POSE_EDITOR -> PoseEditorSheet(state, viewModel, onDismiss = { viewModel.closeSheet() })
        StudioSheet.NONE -> {}
    }

    if (showRenameDialog) {
        var nameInput by remember(state.project?.name) { mutableStateOf(state.project?.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Project") },
            text = {
                OutlinedTextField(value = nameInput, onValueChange = { nameInput = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameProject(nameInput)
                    showRenameDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear all drawings?") },
            text = {
                Text("This clears all strokes, shapes, and text from the current scene canvas. You can still undo this action.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllCanvasDrawings()
                        showClearConfirmDialog = false
                    },
                    modifier = Modifier.testTag("confirm_clear_canvas_button")
                ) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirmDialog = false },
                    modifier = Modifier.testTag("cancel_clear_canvas_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StudioTopBar(
    state: StudioUiState,
    onBack: () -> Unit,
    onOpenPlanner: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onExport: () -> Unit,
    showMoreMenu: Boolean,
    onToggleMoreMenu: (Boolean) -> Unit,
    onOpenAiDescribe: () -> Unit,
    onOpenBackdrop: () -> Unit,
    onOpenMotion: () -> Unit,
    onToggleAdvanced: () -> Unit,
    onClearLayer: () -> Unit,
    onRequestClearAll: () -> Unit,
    onRenameProject: () -> Unit,
    onReplayTutorial: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PaperSheet)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(40.dp)
                .testTag("studio_back_button")
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to library", tint = InkCharcoal)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpenPlanner)
                .padding(horizontal = 4.dp)
        ) {
            Text(
                state.project?.name ?: "FramePilot",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    state.activeScene?.name ?: "Scene 1",
                    style = MaterialTheme.typography.bodySmall,
                    color = StudioLavender,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(
                    shape = CircleShape,
                    color = if (state.saveStatus == SaveStatus.SAVED) StudioMintSoft else StudioButterSoft
                ) {
                    Text(
                        state.saveStatus.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state.saveStatus == SaveStatus.SAVED) StudioMint else InkCharcoal,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
        }

        IconButton(
            onClick = onUndo,
            enabled = state.canUndo,
            modifier = Modifier
                .size(40.dp)
                .testTag("studio_undo_button")
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.Undo,
                contentDescription = "Undo",
                tint = if (state.canUndo) InkCharcoal else InkSlate.copy(alpha = 0.3f)
            )
        }

        IconButton(
            onClick = onRedo,
            enabled = state.canRedo,
            modifier = Modifier
                .size(40.dp)
                .testTag("studio_redo_button")
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.Redo,
                contentDescription = "Redo",
                tint = if (state.canRedo) InkCharcoal else InkSlate.copy(alpha = 0.3f)
            )
        }

        IconButton(
            onClick = onRequestClearAll,
            modifier = Modifier
                .size(40.dp)
                .testTag("studio_clear_canvas_button")
        ) {
            Icon(
                Icons.Rounded.DeleteSweep,
                contentDescription = "Clear all drawings",
                tint = MaterialTheme.colorScheme.error
            )
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = StudioCoral,
            modifier = Modifier
                .padding(start = 2.dp)
                .clickable(onClick = onExport)
                .testTag("studio_export_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.IosShare, contentDescription = null, tint = PaperSheet, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Export", style = MaterialTheme.typography.labelMedium, color = PaperSheet, maxLines = 1)
            }
        }

        Box {
            IconButton(
                onClick = { onToggleMoreMenu(true) },
                modifier = Modifier
                    .size(40.dp)
                    .testTag("studio_more_button")
            ) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Studio options")
            }
            DropdownMenu(expanded = showMoreMenu, onDismissRequest = { onToggleMoreMenu(false) }) {
                DropdownMenuItem(
                    text = { Text("Backdrop & Environment") },
                    leadingIcon = { Icon(Icons.Rounded.Landscape, contentDescription = null) },
                    onClick = { onToggleMoreMenu(false); onOpenBackdrop() }
                )
                DropdownMenuItem(
                    text = { Text("Add / Edit Motion Track") },
                    leadingIcon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
                    onClick = { onToggleMoreMenu(false); onOpenMotion() }
                )
                DropdownMenuItem(
                    text = { Text("Describe Layer for AI") },
                    leadingIcon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = StudioLavender) },
                    onClick = { onToggleMoreMenu(false); onOpenAiDescribe() }
                )
                DropdownMenuItem(
                    text = { Text(if (state.advancedMode) "Switch to Simple Mode" else "Enable Advanced Controls") },
                    leadingIcon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
                    onClick = { onToggleMoreMenu(false); onToggleAdvanced() }
                )
                DropdownMenuItem(
                    text = { Text("Replay Beginner Tips") },
                    leadingIcon = { Icon(Icons.Rounded.HelpOutline, contentDescription = null) },
                    onClick = { onToggleMoreMenu(false); onReplayTutorial() }
                )
                DropdownMenuItem(
                    text = { Text("Rename Project") },
                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    onClick = { onToggleMoreMenu(false); onRenameProject() }
                )
                DropdownMenuItem(
                    text = { Text("Clear Active Layer / Frame", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { onToggleMoreMenu(false); onClearLayer() }
                )
            }
        }
    }
}

private data class ToolDockItem(
    val tool: DrawingTool,
    val icon: ImageVector,
    val label: String
)

private val PRIMARY_TOOLS = listOf(
    ToolDockItem(DrawingTool.PENCIL, Icons.Rounded.Create, "Pencil"),
    ToolDockItem(DrawingTool.PEN, Icons.Rounded.Edit, "Pen"),
    ToolDockItem(DrawingTool.BRUSH, Icons.Rounded.Brush, "Brush"),
    ToolDockItem(DrawingTool.ERASER, Icons.Rounded.AutoFixNormal, "Eraser"),
    ToolDockItem(DrawingTool.FILL, Icons.Rounded.FormatColorFill, "Fill"),
    ToolDockItem(DrawingTool.SELECT, Icons.Rounded.NearMe, "Select"),
    ToolDockItem(DrawingTool.LASSO, Icons.Rounded.Gesture, "Lasso"),
    ToolDockItem(DrawingTool.TEXT, Icons.Rounded.TextFields, "Text"),
    ToolDockItem(DrawingTool.EYEDROPPER, Icons.Rounded.Colorize, "Pick"),
    ToolDockItem(DrawingTool.HAND, Icons.Rounded.PanTool, "Hand")
)

@Composable
private fun ToolDockHorizontal(
    state: StudioUiState,
    viewModel: StudioViewModel,
    showShapesMenu: Boolean,
    onToggleShapes: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Quick swatch + size pill
        Surface(
            shape = CircleShape,
            color = PaperSheet.copy(alpha = 0.94f),
            border = BorderStroke(1.dp, PaperBorder),
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StudioSwatches.take(7).forEach { hex ->
                    val selected = state.brushColorHex.equals(hex, ignoreCase = true)
                    Surface(
                        shape = CircleShape,
                        color = parseHexColor(hex),
                        border = BorderStroke(if (selected) 2.5.dp else 1.dp, if (selected) StudioCoral else PaperBorder),
                        modifier = Modifier
                            .size(22.dp)
                            .clickable { viewModel.setBrushColor(hex) }
                    ) {}
                }
                Surface(
                    shape = CircleShape,
                    color = PaperLinen,
                    modifier = Modifier
                        .clickable { viewModel.openSheet(StudioSheet.COLOR_PICKER) }
                        .testTag("tool_color_size_button")
                ) {
                    Text(
                        "${state.brushSize.toInt()}px",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkCharcoal,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Compact floating tool dock
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = PaperSheet.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, PaperBorder),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                PRIMARY_TOOLS.take(5).forEach { item ->
                    ToolIconChip(
                        icon = item.icon,
                        label = item.label,
                        selected = state.activeTool == item.tool,
                        tag = "tool_${item.tool.name.lowercase()}",
                        onClick = { viewModel.setTool(item.tool) }
                    )
                }

                // Shapes menu anchor
                Box {
                    val isShapeSelected = state.activeTool in listOf(
                        DrawingTool.LINE, DrawingTool.RECTANGLE, DrawingTool.ELLIPSE, DrawingTool.ARROW
                    )
                    ToolIconChip(
                        icon = Icons.Rounded.Category,
                        label = "Shape",
                        selected = isShapeSelected,
                        tag = "tool_shapes_menu",
                        onClick = { onToggleShapes(true) }
                    )
                    ShapesDropdown(
                        expanded = showShapesMenu,
                        onDismiss = { onToggleShapes(false) },
                        onSelectTool = {
                            onToggleShapes(false)
                            viewModel.setTool(it)
                        }
                    )
                }

                PRIMARY_TOOLS.drop(5).forEach { item ->
                    ToolIconChip(
                        icon = item.icon,
                        label = item.label,
                        selected = state.activeTool == item.tool,
                        tag = "tool_${item.tool.name.lowercase()}",
                        onClick = { viewModel.setTool(item.tool) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolDockVertical(
    state: StudioUiState,
    viewModel: StudioViewModel,
    showShapesMenu: Boolean,
    onToggleShapes: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = PaperSheet.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, PaperBorder),
        shadowElevation = 4.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = parseHexColor(state.brushColorHex),
                border = BorderStroke(2.dp, PaperBorder),
                modifier = Modifier
                    .size(32.dp)
                    .clickable { viewModel.openSheet(StudioSheet.COLOR_PICKER) }
            ) {}
            PRIMARY_TOOLS.forEach { item ->
                ToolIconChip(
                    icon = item.icon,
                    label = item.label,
                    selected = state.activeTool == item.tool,
                    tag = "tool_${item.tool.name.lowercase()}",
                    onClick = { viewModel.setTool(item.tool) }
                )
            }
            Box {
                val isShapeSelected = state.activeTool in listOf(
                    DrawingTool.LINE, DrawingTool.RECTANGLE, DrawingTool.ELLIPSE, DrawingTool.ARROW
                )
                ToolIconChip(
                    icon = Icons.Rounded.Category,
                    label = "Shape",
                    selected = isShapeSelected,
                    tag = "tool_shapes_menu",
                    onClick = { onToggleShapes(true) }
                )
                ShapesDropdown(
                    expanded = showShapesMenu,
                    onDismiss = { onToggleShapes(false) },
                    onSelectTool = {
                        onToggleShapes(false)
                        viewModel.setTool(it)
                    }
                )
            }
        }
    }
}

@Composable
private fun ShapesDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSelectTool: (DrawingTool) -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Line") }, onClick = { onSelectTool(DrawingTool.LINE) })
        DropdownMenuItem(text = { Text("Rectangle") }, onClick = { onSelectTool(DrawingTool.RECTANGLE) })
        DropdownMenuItem(text = { Text("Ellipse / Circle") }, onClick = { onSelectTool(DrawingTool.ELLIPSE) })
        DropdownMenuItem(text = { Text("Arrow") }, onClick = { onSelectTool(DrawingTool.ARROW) })
    }
}

@Composable
private fun ToolIconChip(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) StudioCoralSoft else Color.Transparent,
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (selected) StudioCoral else InkCharcoal,
                modifier = Modifier.size(19.dp)
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) StudioCoral else InkSlate
            )
        }
    }
}
