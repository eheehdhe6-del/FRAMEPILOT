package com.example.ui.studio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.AnimationFrame
import com.example.domain.model.FramePilotProject
import com.example.domain.model.MotionActionType
import com.example.domain.model.Scene
import com.example.domain.model.StudioMode
import com.example.engine.TimelineEngine
import com.example.ui.components.renderSceneDocument
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
import com.example.ui.theme.StudioSky
import com.example.ui.theme.StudioSkySoft
import kotlin.math.min

@Composable
fun TimelinePanel(
    state: StudioUiState,
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier
) {
    val project = state.project ?: return
    val scene = state.activeScene ?: return
    val orderedFrames = remember(scene.frames) { TimelineEngine.normalizeOrder(scene.frames) }
    val selectedIdx = orderedFrames.indexOfFirst { it.id == state.selectedFrameId }.coerceAtLeast(0)
    val selectedFrame = orderedFrames.getOrNull(selectedIdx)

    var showTracksTab by remember(state.activeMode) {
        mutableStateOf(state.activeMode == StudioMode.MOTION_STUDIO)
    }
    var showFrameNoteDialog by remember { mutableStateOf(false) }

    Surface(
        color = PaperSheet,
        border = BorderStroke(1.dp, PaperBorder),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Compact Playback & Collapse Bar (Always visible)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.stepFrame(-1) },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("timeline_prev_frame")
                    ) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous frame", tint = InkCharcoal)
                    }

                    Surface(
                        shape = CircleShape,
                        color = if (state.isPlaying) StudioLavender else StudioCoral,
                        modifier = Modifier
                            .size(38.dp)
                            .clickable { viewModel.togglePlayPause() }
                            .testTag("timeline_play_pause")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
                                tint = PaperSheet
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.stopPlayback() },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("timeline_stop")
                    ) {
                        Icon(Icons.Rounded.Stop, contentDescription = "Stop playback", tint = InkCharcoal, modifier = Modifier.size(19.dp))
                    }

                    IconButton(
                        onClick = { viewModel.stepFrame(1) },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("timeline_next_frame")
                    ) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Next frame", tint = InkCharcoal)
                    }

                    IconButton(
                        onClick = { viewModel.toggleReversePlayback() },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("timeline_reverse_toggle")
                    ) {
                        Icon(
                            Icons.Rounded.FastRewind,
                            contentDescription = "Reverse playback",
                            tint = if (state.reversePlayback) StudioCoral else InkSlate,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.toggleLoop() },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("timeline_loop_toggle")
                    ) {
                        Icon(
                            Icons.Rounded.Loop,
                            contentDescription = "Loop playback",
                            tint = if (state.loopPlayback) StudioMint else InkSlate,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    val displayText = if (state.connectedScenesMode) {
                        val curGlobal = state.globalPlayheadTimeMs() / 1000f
                        val totGlobal = state.totalReelDurationMs() / 1000f
                        String.format("%.1fs/%.1fs", curGlobal, totGlobal)
                    } else if (state.isPlaying || showTracksTab) {
                        val totalSec = scene.effectiveDurationMs(project.fps) / 1000f
                        val curSec = state.playheadTimeMs / 1000f
                        String.format("%.1fs/%.1fs", curSec, totalSec)
                    } else {
                        "F${selectedIdx + 1}/${orderedFrames.size}"
                    }
                    Text(
                        displayText,
                        style = MaterialTheme.typography.labelSmall,
                        color = InkCharcoal,
                        maxLines = 1,
                        modifier = Modifier
                            .padding(start = 2.dp, end = 4.dp)
                            .testTag("timeline_status_label")
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Toggle Single Scene vs Connected Whole Reel editing
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (state.connectedScenesMode) StudioMintSoft else PaperLinen,
                        border = if (state.connectedScenesMode) BorderStroke(1.dp, StudioMint.copy(alpha = 0.5f)) else null,
                        modifier = Modifier
                            .clickable { viewModel.toggleConnectedScenesMode() }
                            .testTag("timeline_connected_mode_toggle")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                Icons.Rounded.ViewCarousel,
                                contentDescription = "Whole Reel connected mode",
                                tint = if (state.connectedScenesMode) StudioMint else InkSlate,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                if (state.connectedScenesMode) "Reel" else "Scene",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (state.connectedScenesMode) StudioMint else InkSlate
                            )
                        }
                    }

                    // Toggle between Frames & Unified Tracks
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = PaperLinen,
                        modifier = Modifier
                            .clickable {
                                showTracksTab = !showTracksTab
                                if (!state.timelineExpanded) viewModel.toggleTimelineExpanded()
                            }
                            .testTag("timeline_view_toggle")
                    ) {
                        Text(
                            if (showTracksTab) "Tracks" else "Frames",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (showTracksTab) StudioLavender else StudioCoral,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.openSheet(StudioSheet.LAYERS) },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("timeline_layers_button")
                    ) {
                        Icon(Icons.Rounded.Layers, contentDescription = "Layers", tint = StudioSky, modifier = Modifier.size(19.dp))
                    }

                    IconButton(
                        onClick = { viewModel.toggleTimelineExpanded() },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("timeline_collapse_toggle")
                    ) {
                        Icon(
                            if (state.timelineExpanded) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                            contentDescription = "Expand or collapse timeline",
                            tint = InkCharcoal
                        )
                    }
                }
            }

            // Expandable Workspace
            AnimatedVisibility(visible = state.timelineExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PaperCream)
                        .padding(bottom = 4.dp)
                ) {
                    if (state.connectedScenesMode) {
                        ConnectedReelSequenceBar(state = state, viewModel = viewModel)
                    }
                    if (!showTracksTab) {
                        // Frame Operations Strip
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 10.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TimelineActionChip(
                                label = "Blank +",
                                icon = Icons.Rounded.Add,
                                containerColor = StudioCoralSoft,
                                contentColor = StudioCoral,
                                testTag = "frame_add_blank",
                                onClick = { viewModel.addBlankFrameAfterSelected() }
                            )
                            TimelineActionChip(
                                label = "Duplicate",
                                icon = Icons.Rounded.ContentCopy,
                                containerColor = StudioLavenderSoft,
                                contentColor = StudioLavender,
                                testTag = "frame_duplicate",
                                onClick = { viewModel.duplicateSelectedFrame() }
                            )
                            if (selectedFrame != null) {
                                // Exposure duration selector
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = PaperSheet,
                                    border = BorderStroke(1.dp, PaperBorder)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "−",
                                            style = MaterialTheme.typography.titleSmall,
                                            modifier = Modifier
                                                .clickable {
                                                    viewModel.setSelectedFrameExposure((selectedFrame.exposureFrames - 1).coerceAtLeast(1))
                                                }
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                        Text(
                                            "Hold ${selectedFrame.exposureFrames}f",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = InkCharcoal
                                        )
                                        Text(
                                            "+",
                                            style = MaterialTheme.typography.titleSmall,
                                            modifier = Modifier
                                                .clickable {
                                                    viewModel.setSelectedFrameExposure(selectedFrame.exposureFrames + 1)
                                                }
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }

                                // Move Left / Right reorder
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = PaperSheet,
                                    border = BorderStroke(1.dp, PaperBorder)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                            contentDescription = "Move frame left",
                                            modifier = Modifier
                                                .clickable(enabled = selectedIdx > 0) {
                                                    viewModel.reorderFrame(selectedIdx, selectedIdx - 1)
                                                }
                                                .padding(6.dp)
                                                .size(18.dp)
                                                .testTag("frame_move_left"),
                                            tint = if (selectedIdx > 0) InkCharcoal else InkSlate.copy(alpha = 0.3f)
                                        )
                                        Icon(
                                            Icons.Rounded.SwapHoriz,
                                            contentDescription = null,
                                            tint = InkSlate,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Icon(
                                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                            contentDescription = "Move frame right",
                                            modifier = Modifier
                                                .clickable(enabled = selectedIdx < orderedFrames.lastIndex) {
                                                    viewModel.reorderFrame(selectedIdx, selectedIdx + 1)
                                                }
                                                .padding(6.dp)
                                                .size(18.dp)
                                                .testTag("frame_move_right"),
                                            tint = if (selectedIdx < orderedFrames.lastIndex) InkCharcoal else InkSlate.copy(alpha = 0.3f)
                                        )
                                    }
                                }

                                TimelineActionChip(
                                    label = if (selectedFrame.note.isBlank()) "Note" else "Note ✓",
                                    icon = Icons.Rounded.EditNote,
                                    containerColor = StudioButterSoft,
                                    contentColor = InkCharcoal,
                                    testTag = "frame_note_button",
                                    onClick = { showFrameNoteDialog = true }
                                )

                                TimelineActionChip(
                                    label = "Onion",
                                    icon = Icons.Rounded.Flip,
                                    containerColor = if (state.onionSkinEnabled) StudioMintSoft else PaperLinen,
                                    contentColor = if (state.onionSkinEnabled) StudioMint else InkSlate,
                                    testTag = "frame_onion_toggle",
                                    onClick = { viewModel.toggleOnionSkin() }
                                )

                                TimelineActionChip(
                                    label = "Delete",
                                    icon = Icons.Rounded.DeleteOutline,
                                    containerColor = PaperSheet,
                                    contentColor = MaterialTheme.colorScheme.error,
                                    testTag = "frame_delete",
                                    onClick = { viewModel.deleteSelectedFrame() }
                                )
                            }
                        }

                        // Chronological Frame Thumbnail Strip (Left -> Right: 1, 2, 3...)
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("frame_timeline_strip")
                        ) {
                            itemsIndexed(orderedFrames, key = { _, f -> f.id }) { idx, frame ->
                                val visibleNumber = idx + 1
                                val isSelected = frame.id == state.selectedFrameId
                                FrameThumbnailCard(
                                    project = project,
                                    scene = scene,
                                    frame = frame,
                                    visibleNumber = visibleNumber,
                                    isSelected = isSelected,
                                    onClick = { viewModel.selectFrame(frame.id) }
                                )
                            }
                        }
                    } else {
                        // Unified Multi-Track Timeline (same timebase!)
                        UnifiedTracksView(
                            project = project,
                            scene = scene,
                            playheadMs = state.playheadTimeMs,
                            selectedTrackId = state.selectedMotionTrackId,
                            onSeek = { viewModel.seekTimeMs(it) },
                            onSelectTrack = { trackId ->
                                viewModel.selectMotionTrack(trackId)
                                viewModel.openSheet(StudioSheet.MOTION_ACTION)
                            },
                            onAddMotion = {
                                viewModel.selectMotionTrack(null)
                                viewModel.openSheet(StudioSheet.MOTION_ACTION)
                            },
                            onQuickCameraZoom = {
                                viewModel.addCameraZoomPreset(1.18f, "Smooth 18% zoom-in")
                                viewModel.showToast("Added camera zoom keyframe")
                            }
                        )
                    }
                }
            }
        }
    }

    if (showFrameNoteDialog && selectedFrame != null) {
        var noteText by remember(selectedFrame.id) { mutableStateOf(selectedFrame.note) }
        AlertDialog(
            onDismissRequest = { showFrameNoteDialog = false },
            title = { Text("Frame ${selectedIdx + 1} Note") },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Action or timing note for AI & yourself") },
                    placeholder = { Text("e.g., Faster bouncing on rebound!") },
                    minLines = 2
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setSelectedFrameNote(noteText)
                    showFrameNoteDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showFrameNoteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun TimelineActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    contentColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.dp, PaperBorder),
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = contentColor)
        }
    }
}

@Composable
private fun FrameThumbnailCard(
    project: FramePilotProject,
    scene: Scene,
    frame: AnimationFrame,
    visibleNumber: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val textMeasurer = rememberTextMeasurer()
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = PaperSheet,
        border = BorderStroke(if (isSelected) 2.5.dp else 1.dp, if (isSelected) StudioCoral else PaperBorder),
        shadowElevation = if (isSelected) 3.dp else 0.dp,
        modifier = Modifier
            .size(width = 66.dp, height = 74.dp)
            .clickable(onClick = onClick)
            .testTag("frame_card_$visibleNumber")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                val docW = project.canvasWidth.toFloat().coerceAtLeast(1f)
                val docH = project.canvasHeight.toFloat().coerceAtLeast(1f)
                val scale = min(size.width / docW, size.height / docH)
                val dx = (size.width - docW * scale) / 2f
                val dy = (size.height - docH * scale) / 2f
                withTransform({
                    translate(dx, dy)
                    scale(scale, scale, pivot = Offset.Zero)
                }) {
                    renderSceneDocument(
                        project = project,
                        scene = scene,
                        activeFrame = frame,
                        textMeasurer = textMeasurer,
                        showPoseSkeleton = false
                    )
                }
            }

            // Visible sequential number badge (1, 2, 3...)
            Surface(
                shape = RoundedCornerShape(bottomEnd = 8.dp),
                color = if (isSelected) StudioCoral else InkCharcoal.copy(alpha = 0.78f),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Text(
                    "$visibleNumber",
                    style = MaterialTheme.typography.labelSmall,
                    color = PaperSheet,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            // Exposure duration badge
            if (frame.exposureFrames > 1) {
                Surface(
                    shape = CircleShape,
                    color = StudioLavenderSoft,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                ) {
                    Text(
                        "${frame.exposureFrames}f",
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioLavender,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }

            if (frame.note.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(7.dp)
                        .background(StudioMint, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun UnifiedTracksView(
    project: FramePilotProject,
    scene: Scene,
    playheadMs: Long,
    selectedTrackId: String?,
    onSeek: (Long) -> Unit,
    onSelectTrack: (String) -> Unit,
    onAddMotion: () -> Unit,
    onQuickCameraZoom: () -> Unit
) {
    val totalMs = scene.effectiveDurationMs(project.fps).coerceAtLeast(500L)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Scrubber slider on shared timebase
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "${playheadMs}ms",
                style = MaterialTheme.typography.labelSmall,
                color = StudioLavender
            )
            Slider(
                value = playheadMs.toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..totalMs.toFloat(),
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
            )
            TimelineActionChip(
                label = "+ Motion",
                icon = Icons.Rounded.Add,
                containerColor = StudioLavenderSoft,
                contentColor = StudioLavender,
                testTag = "timeline_add_motion",
                onClick = onAddMotion
            )
        }

        // Track lanes
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Drawing Track pill
            TrackLanePill(
                title = "Drawing Cels",
                subtitle = "${scene.frames.size} frame(s) · ${scene.totalExposureTicks()} ticks",
                color = StudioCoralSoft,
                accent = StudioCoral,
                selected = false,
                onClick = {}
            )

            // Motion Tracks
            scene.motionTracks.forEach { track ->
                val color = when (track.actionType) {
                    MotionActionType.ORBIT, MotionActionType.FOLLOW_PATH -> StudioLavenderSoft to StudioLavender
                    MotionActionType.MORPH, MotionActionType.PARTICLES -> StudioMintSoft to StudioMint
                    else -> StudioSkySoft to StudioSky
                }
                TrackLanePill(
                    title = track.name,
                    subtitle = "${track.actionType.label} · ${track.startTimeMs / 1000f}s–${(track.startTimeMs + track.durationMs) / 1000f}s",
                    color = color.first,
                    accent = color.second,
                    selected = track.id == selectedTrackId,
                    onClick = { onSelectTrack(track.id) }
                )
            }

            // Camera Track pill
            TrackLanePill(
                title = "Camera",
                subtitle = if (scene.cameraTrack.keyframes.isEmpty()) "Tap to add zoom" else "${scene.cameraTrack.keyframes.size} keyframe(s)",
                color = StudioButterSoft,
                accent = InkCharcoal,
                icon = Icons.Rounded.Videocam,
                selected = false,
                onClick = onQuickCameraZoom
            )
        }
    }
}

@Composable
private fun TrackLanePill(
    title: String,
    subtitle: String,
    color: Color,
    accent: Color,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = color,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) accent else PaperBorder),
        modifier = Modifier
            .width(164.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            }
            Column {
                Text(title, style = MaterialTheme.typography.labelLarge, color = InkCharcoal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = InkSlate, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ConnectedReelSequenceBar(
    state: StudioUiState,
    viewModel: StudioViewModel
) {
    val project = state.project ?: return
    val activeScene = state.activeScene ?: return
    val orderedScenes = state.orderedScenes()
    val activeIdx = orderedScenes.indexOfFirst { it.id == activeScene.id }.coerceAtLeast(0)
    val totalGlobalMs = state.totalReelDurationMs().coerceAtLeast(500L)
    val curGlobalMs = state.globalPlayheadTimeMs().coerceIn(0L, totalGlobalMs)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(StudioMintSoft.copy(alpha = 0.45f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("connected_reel_sequence_bar"),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "Reel",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = StudioMint
            )
            Slider(
                value = curGlobalMs.toFloat(),
                onValueChange = { viewModel.seekGlobalTimeMs(it.toLong()) },
                valueRange = 0f..totalGlobalMs.toFloat(),
                modifier = Modifier
                    .weight(1f)
                    .height(24.dp)
                    .testTag("timeline_global_reel_slider")
            )
            Text(
                "${String.format("%.1fs", curGlobalMs / 1000f)}/${String.format("%.1fs", totalGlobalMs / 1000f)}",
                style = MaterialTheme.typography.labelSmall,
                color = InkCharcoal
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            orderedScenes.forEachIndexed { idx, sc ->
                val isSelected = sc.id == activeScene.id
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) StudioCoralSoft else PaperSheet,
                    border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) StudioCoral else PaperBorder),
                    modifier = Modifier
                        .clickable { viewModel.selectScene(sc.id) }
                        .testTag("timeline_connected_scene_${sc.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "${idx + 1}. ${sc.name}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) StudioCoral else InkCharcoal
                        )
                        if (isSelected && orderedScenes.size > 1) {
                            Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                contentDescription = "Move scene earlier",
                                tint = if (activeIdx > 0) InkCharcoal else InkSlate.copy(alpha = 0.3f),
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable(enabled = activeIdx > 0) {
                                        viewModel.moveScene(sc.id, -1)
                                    }
                                    .testTag("timeline_scene_swap_left")
                            )
                            Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = "Move scene later",
                                tint = if (activeIdx < orderedScenes.lastIndex) InkCharcoal else InkSlate.copy(alpha = 0.3f),
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable(enabled = activeIdx < orderedScenes.lastIndex) {
                                        viewModel.moveScene(sc.id, 1)
                                    }
                                    .testTag("timeline_scene_swap_right")
                            )
                        }
                    }
                }
            }

            TimelineActionChip(
                label = "+ Cut",
                icon = Icons.Rounded.Add,
                containerColor = PaperSheet,
                contentColor = StudioCoral,
                testTag = "timeline_insert_cut_scene",
                onClick = { viewModel.insertBlankSceneAfter(activeScene.id) }
            )
            TimelineActionChip(
                label = "Fade",
                icon = Icons.Rounded.Flip,
                containerColor = StudioLavenderSoft,
                contentColor = StudioLavender,
                testTag = "timeline_add_fade_button",
                onClick = { viewModel.addSceneFadeEffect(fadeIn = true, fadeOut = true) }
            )
            if (orderedScenes.size > 1) {
                TimelineActionChip(
                    label = "Merge All",
                    icon = Icons.Rounded.SwapHoriz,
                    containerColor = StudioButterSoft,
                    contentColor = InkCharcoal,
                    testTag = "timeline_merge_all_button",
                    onClick = { viewModel.combineAllScenesIntoOne() }
                )
            }
        }
    }
}

