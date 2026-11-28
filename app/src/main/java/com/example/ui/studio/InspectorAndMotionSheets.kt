package com.example.ui.studio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MergeType
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MergeType
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.model.EasingType
import com.example.domain.model.MotionActionType
import com.example.domain.model.MotionKeyframe
import com.example.domain.model.MotionTargetType
import com.example.domain.model.MotionTrack
import com.example.domain.model.OrbitConfig
import com.example.domain.model.OrbitDirection
import com.example.domain.model.ParticleConfig
import com.example.domain.model.SemanticInfo
import com.example.domain.model.ShapeMorphConfig
import com.example.domain.model.ShapeType
import com.example.domain.model.defaultStickmanJoints
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
import java.util.UUID
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiInspectorSheet(
    state: StudioUiState,
    viewModel: StudioViewModel,
    onDismiss: () -> Unit
) {
    val layer = state.selectedLayer ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sem = layer.semantic

    var whatIsIt by remember(layer.id) { mutableStateOf(sem.whatIsIt) }
    var represents by remember(layer.id) { mutableStateOf(sem.represents) }
    var whatDoing by remember(layer.id) { mutableStateOf(sem.whatIsItDoing) }
    var whereLocated by remember(layer.id) { mutableStateOf(sem.whereLocated) }
    var inFrontOf by remember(layer.id) { mutableStateOf(sem.inFrontOf) }
    var behind by remember(layer.id) { mutableStateOf(sem.behind) }
    var howMove by remember(layer.id) { mutableStateOf(sem.howShouldItMove) }
    var whatNext by remember(layer.id) { mutableStateOf(sem.whatShouldHappenNext) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PaperCream
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = StudioLavender)
                Column {
                    Text("Describe '${layer.name}' for AI", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Plain-language descriptions let text-only OpenCode models understand rough sketches without vision.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSlate
                    )
                }
            }

            if (whatIsIt.isBlank() && represents.isBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = StudioButterSoft,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Currently marked as 'unlabelled' in AI exports until you add a description below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkCharcoal,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            OutlinedTextField(
                value = whatIsIt,
                onValueChange = { whatIsIt = it },
                label = { Text("What is this?") },
                placeholder = { Text("e.g., A stickman hero, a butterfly, a basketball") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_what_is_it_input")
            )
            OutlinedTextField(
                value = represents,
                onValueChange = { represents = it },
                label = { Text("What does it represent?") },
                placeholder = { Text("e.g., Main character enjoying a morning walk") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = whatDoing,
                onValueChange = { whatDoing = it },
                label = { Text("What is it doing?") },
                placeholder = { Text("e.g., Standing and waving up at the sky") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = howMove,
                onValueChange = { howMove = it },
                label = { Text("How should it move?") },
                placeholder = { Text("e.g., Bounce gently and wave right arm") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = whatNext,
                onValueChange = { whatNext = it },
                label = { Text("What should happen next?") },
                placeholder = { Text("e.g., Watches the butterfly circle overhead") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = whereLocated,
                    onValueChange = { whereLocated = it },
                    label = { Text("Where located?") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = behind,
                    onValueChange = { behind = it },
                    label = { Text("Behind it?") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = inFrontOf,
                onValueChange = { inFrontOf = it },
                label = { Text("What is in front of it?") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    viewModel.updateLayerSemantic(
                        layer.id,
                        SemanticInfo(
                            whatIsIt = whatIsIt.trim(),
                            represents = represents.trim(),
                            whatIsItDoing = whatDoing.trim(),
                            whereLocated = whereLocated.trim(),
                            inFrontOf = inFrontOf.trim(),
                            behind = behind.trim(),
                            howShouldItMove = howMove.trim(),
                            whatShouldHappenNext = whatNext.trim()
                        )
                    )
                    viewModel.showToast("Saved AI description")
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_ai_description_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = StudioLavender)
            ) {
                Text("Save Description")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MotionActionSheet(
    state: StudioUiState,
    viewModel: StudioViewModel,
    onDismiss: () -> Unit
) {
    val scene = state.activeScene ?: return
    val project = state.project ?: return
    val existing = state.selectedMotionTrack
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var trackName by remember(existing?.id) {
        mutableStateOf(existing?.name ?: "${state.selectedLayer?.name ?: "Object"} Motion")
    }
    var targetLayerId by remember(existing?.id) {
        mutableStateOf(existing?.targetId ?: state.selectedLayerId)
    }
    var actionType by remember(existing?.id) {
        mutableStateOf(existing?.actionType ?: MotionActionType.ORBIT)
    }
    var startMs by remember(existing?.id) { mutableFloatStateOf((existing?.startTimeMs ?: 0L).toFloat()) }
    var durationMs by remember(existing?.id) { mutableFloatStateOf((existing?.durationMs ?: 2500L).toFloat()) }
    var repeatCount by remember(existing?.id) { mutableIntStateOf(existing?.repeatCount ?: 1) }
    var pingPong by remember(existing?.id) { mutableStateOf(existing?.pingPong ?: false) }
    var easing by remember(existing?.id) { mutableStateOf(existing?.easing ?: EasingType.EASE_IN_OUT) }
    var description by remember(existing?.id) { mutableStateOf(existing?.description ?: "") }

    // Orbit simple parameters
    val defaultOrbitTarget = scene.orderedLayers().firstOrNull { it.id != targetLayerId }?.id ?: targetLayerId
    var orbitTargetId by remember(existing?.id) {
        mutableStateOf(existing?.orbitConfig?.targetId ?: defaultOrbitTarget)
    }
    var orbitDir by remember(existing?.id) {
        mutableStateOf(existing?.orbitConfig?.direction ?: OrbitDirection.CLOCKWISE)
    }
    var orbitRadius by remember(existing?.id) {
        mutableFloatStateOf(existing?.orbitConfig?.radiusPx ?: 200f)
    }
    var orientToPath by remember(existing?.id) { mutableStateOf(existing?.orientToPath ?: false) }

    // Morph parameters
    var morphFrom by remember(existing?.id) {
        mutableStateOf(existing?.morphConfig?.sourceShape ?: ShapeType.ELLIPSE)
    }
    var morphTo by remember(existing?.id) {
        mutableStateOf(existing?.morphConfig?.targetShape ?: ShapeType.RECTANGLE)
    }

    var showAdvanced by remember(state.advancedMode) { mutableStateOf(state.advancedMode) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PaperCream
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(if (existing == null) "Add Movement" else "Edit Movement", style = MaterialTheme.typography.headlineSmall)
                    Text("Choose what moves and how it behaves.", style = MaterialTheme.typography.bodySmall, color = InkSlate)
                }
                if (existing != null) {
                    TextButton(onClick = {
                        viewModel.deleteMotionTrack(existing.id)
                        onDismiss()
                    }) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            OutlinedTextField(
                value = trackName,
                onValueChange = { trackName = it },
                label = { Text("Motion label") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // What moves?
            Text("Which layer moves?", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                scene.orderedLayers().forEach { l ->
                    FilterChip(
                        selected = targetLayerId == l.id,
                        onClick = { targetLayerId = l.id },
                        label = { Text(l.name) }
                    )
                }
            }

            // Simple beginner action picker
            Text("What should it do?", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MotionActionType.entries.forEach { act ->
                    FilterChip(
                        selected = actionType == act,
                        onClick = { actionType = act },
                        label = { Text(act.label) },
                        modifier = Modifier.testTag("motion_action_${act.name.lowercase()}")
                    )
                }
            }

            // Contextual friendly parameters
            when (actionType) {
                MotionActionType.ORBIT -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = StudioLavenderSoft.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, PaperBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Orbit Around Target", style = MaterialTheme.typography.titleSmall)
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                scene.orderedLayers().forEach { l ->
                                    FilterChip(
                                        selected = orbitTargetId == l.id,
                                        onClick = { orbitTargetId = l.id },
                                        label = { Text(l.name) }
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OrbitDirection.entries.forEach { dir ->
                                    FilterChip(
                                        selected = orbitDir == dir,
                                        onClick = { orbitDir = dir },
                                        label = { Text(dir.label) }
                                    )
                                }
                            }
                            Text("Orbit Distance: ${orbitRadius.toInt()} px", style = MaterialTheme.typography.labelMedium)
                            Slider(value = orbitRadius, onValueChange = { orbitRadius = it }, valueRange = 60f..500f)
                        }
                    }
                }
                MotionActionType.FOLLOW_PATH, MotionActionType.MOVE -> {
                    OutlinedButton(
                        onClick = {
                            val trackId = existing?.id ?: UUID.randomUUID().toString()
                            val draft = MotionTrack(
                                id = trackId,
                                name = trackName.ifBlank { "Path Motion" },
                                targetType = MotionTargetType.LAYER,
                                targetId = targetLayerId,
                                actionType = actionType,
                                startTimeMs = startMs.toLong(),
                                durationMs = durationMs.toLong().coerceAtLeast(300L),
                                repeatCount = repeatCount,
                                pingPong = pingPong,
                                easing = easing,
                                orientToPath = orientToPath,
                                pathPoints = existing?.pathPoints.orEmpty(),
                                description = description.ifBlank { "Follows drawn path across the scene" }
                            )
                            viewModel.addOrUpdateMotionTrack(draft)
                            viewModel.startRecordingPathForTrack(trackId)
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Draw, contentDescription = null, tint = StudioLavender)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (existing?.pathPoints?.isNotEmpty() == true)
                                "Redraw Path on Canvas (${existing.pathPoints.size} pts)"
                            else
                                "Draw Movement Path on Canvas"
                        )
                    }
                }
                MotionActionType.MORPH -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = StudioMintSoft.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, PaperBorder)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Shape Transformation", style = MaterialTheme.typography.titleSmall)
                            Text("From shape:", style = MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(ShapeType.ELLIPSE, ShapeType.RECTANGLE, ShapeType.LINE, ShapeType.POLYGON).forEach { sh ->
                                    FilterChip(selected = morphFrom == sh, onClick = { morphFrom = sh }, label = { Text(sh.label) })
                                }
                            }
                            Text("To shape:", style = MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(ShapeType.RECTANGLE, ShapeType.ELLIPSE, ShapeType.POLYGON, ShapeType.LINE).forEach { sh ->
                                    FilterChip(selected = morphTo == sh, onClick = { morphTo = sh }, label = { Text(sh.label) })
                                }
                            }
                            Text(
                                "Uses 32-point perimeter polyline resampling for smooth visual preview & AI export.",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSlate
                            )
                        }
                    }
                }
                else -> {}
            }

            // Simple Duration & Timing
            Text("Duration: ${String.format("%.1fs", durationMs / 1000f)} (starts at ${String.format("%.1fs", startMs / 1000f)})", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = durationMs,
                onValueChange = { durationMs = it },
                valueRange = 300f..(scene.effectiveDurationMs(project.fps).coerceAtLeast(3000L)).toFloat()
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Describe the movement in plain words (for AI)") },
                placeholder = { Text("e.g., Make the butterfly fly around the character") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Expandable Advanced Motion Controls (hidden by default for beginners!)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = PaperLinen,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAdvanced = !showAdvanced }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Advanced (Easing, Start Time, Repeat, Keyframes)", style = MaterialTheme.typography.titleSmall)
                    Icon(if (showAdvanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
                }
            }

            AnimatedVisibility(visible = showAdvanced) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Start Time: ${startMs.toLong()} ms", style = MaterialTheme.typography.labelMedium)
                    Slider(value = startMs, onValueChange = { startMs = it }, valueRange = 0f..5000f)

                    Text("Easing Curve", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        EasingType.entries.forEach { e ->
                            FilterChip(selected = easing == e, onClick = { easing = e }, label = { Text(e.label) })
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Repeat: ${repeatCount}x", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { if (repeatCount > 1) repeatCount-- }) { Text("−") }
                            OutlinedButton(onClick = { repeatCount++ }) { Text("+") }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Ping-Pong (back & forth)", style = MaterialTheme.typography.labelMedium)
                        Switch(checked = pingPong, onCheckedChange = { pingPong = it })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Orient along path direction", style = MaterialTheme.typography.labelMedium)
                        Switch(checked = orientToPath, onCheckedChange = { orientToPath = it })
                    }
                }
            }

            Button(
                onClick = {
                    val targetLayerName = scene.layers.find { it.id == orbitTargetId }?.name.orEmpty()
                    val track = MotionTrack(
                        id = existing?.id ?: UUID.randomUUID().toString(),
                        name = trackName.trim().ifBlank { actionType.label },
                        targetType = if (actionType == MotionActionType.PARTICLES) MotionTargetType.PARTICLES else MotionTargetType.LAYER,
                        targetId = targetLayerId,
                        actionType = actionType,
                        startTimeMs = startMs.toLong(),
                        durationMs = durationMs.toLong().coerceAtLeast(300L),
                        repeatCount = repeatCount,
                        pingPong = pingPong,
                        easing = easing,
                        orientToPath = orientToPath,
                        orbitConfig = if (actionType == MotionActionType.ORBIT) {
                            OrbitConfig(
                                targetId = orbitTargetId,
                                targetName = targetLayerName,
                                direction = orbitDir,
                                radiusPx = orbitRadius
                            )
                        } else null,
                        pathPoints = existing?.pathPoints.orEmpty(),
                        morphConfig = if (actionType == MotionActionType.MORPH) {
                            ShapeMorphConfig(
                                sourceShape = morphFrom,
                                targetShape = morphTo,
                                centerX = project.canvasWidth / 2f,
                                centerY = project.canvasHeight / 2f
                            )
                        } else null,
                        particleConfig = if (actionType == MotionActionType.PARTICLES) {
                            ParticleConfig(
                                originX = project.canvasWidth / 2f,
                                originY = project.canvasHeight / 2f
                            )
                        } else null,
                        keyframes = existing?.keyframes.orEmpty().ifEmpty {
                            listOf(
                                MotionKeyframe(timeMs = 0L),
                                MotionKeyframe(timeMs = durationMs.toLong().coerceAtLeast(300L))
                            )
                        },
                        description = description.trim().ifBlank { "${actionType.label} animation" }
                    )
                    viewModel.addOrUpdateMotionTrack(track)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_motion_track_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = StudioCoral)
            ) {
                Text("Save Animation", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PoseEditorPanel(
    state: StudioUiState,
    viewModel: StudioViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    maxPanelHeight: Dp = 200.dp
) {
    val project = state.project ?: return
    val layer = state.selectedLayer ?: return
    val pose = state.activePoseFor(layer)
    val defaultJoints = remember(project.canvasWidth, project.canvasHeight) {
        defaultStickmanJoints(project.canvasWidth / 2f, project.canvasHeight / 2f)
    }
    val joints = pose?.joints?.takeIf { it.isNotEmpty() } ?: defaultJoints
    val selectedJoint = state.selectedPoseJoint.takeIf { it in joints } ?: joints.keys.first()
    val pt = joints[selectedJoint] ?: joints.values.first()

    val (wholeCenterX, wholeCenterY) = PoseGeometry.computeCenter(joints)
    val wholeScale = PoseGeometry.computeScale(joints)
    val wholeAngleDeg = PoseGeometry.computeAngleDeg(joints)

    var sliderUndoRecorded by remember(state.poseEditWholeBody, selectedJoint, state.selectedFrameId) {
        mutableStateOf(false)
    }

    val canvasW = project.canvasWidth.toFloat().coerceAtLeast(100f)
    val canvasH = project.canvasHeight.toFloat().coerceAtLeast(100f)
    val isEditingFramePose = state.activeMode == com.example.domain.model.StudioMode.DRAW_FRAMES ||
        state.selectedFrame?.layerPoses?.containsKey(layer.id) == true
    val frameSubtitle = if (isEditingFramePose) {
        val frames = state.activeScene?.orderedFrames().orEmpty()
        val fIdx = frames.indexOfFirst { it.id == state.selectedFrameId }.coerceAtLeast(0)
        " · Frame ${fIdx + 1}"
    } else ""
    val unboundArtCount = layer.sceneShapes.count { it.rigAttachment == null } +
        layer.sceneStrokes.count { it.rigAttachment == null }

    Surface(
        color = PaperSheet,
        border = BorderStroke(1.dp, PaperBorder),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("pose_editor_panel")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxPanelHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Roomy Header: Title + Reset + Done
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "2D Pose${frameSubtitle}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${layer.name} · Drag canvas or handles",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSlate,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = { viewModel.resetLayerPoseToDefault(layer.id) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("pose_reset_default_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reset", style = MaterialTheme.typography.labelMedium)
                }

                Button(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = StudioCoral),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("pose_editor_done_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Done", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }

            if (unboundArtCount > 0) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = StudioLavenderSoft,
                    border = BorderStroke(1.dp, StudioLavender.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.autoBindLayerToRig(layer.id) }
                        .testTag("pose_auto_bind_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "$unboundArtCount shape/stroke(s) on '${layer.name}' not rigged yet",
                            style = MaterialTheme.typography.labelMedium,
                            color = InkCharcoal,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Auto-Attach Art",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = StudioLavender
                        )
                    }
                }
            }

            // Spacious Segmented Mode Switch: Whole Body vs Single Joint
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = PaperLinen,
                border = BorderStroke(1.dp, PaperBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (state.poseEditWholeBody) StudioLavenderSoft else PaperSheet.copy(alpha = 0f),
                        border = if (state.poseEditWholeBody) BorderStroke(1.dp, StudioLavender.copy(alpha = 0.45f)) else null,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.setPoseEditWholeBody(true) }
                            .testTag("pose_mode_whole_body")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Rounded.OpenWith,
                                contentDescription = null,
                                tint = if (state.poseEditWholeBody) StudioLavender else InkSlate,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Whole Body",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (state.poseEditWholeBody) FontWeight.Bold else FontWeight.Medium,
                                color = if (state.poseEditWholeBody) StudioLavender else InkSlate,
                                maxLines = 1
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (!state.poseEditWholeBody) StudioCoralSoft else PaperSheet.copy(alpha = 0f),
                        border = if (!state.poseEditWholeBody) BorderStroke(1.dp, StudioCoral.copy(alpha = 0.45f)) else null,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.setPoseEditWholeBody(false) }
                            .testTag("pose_mode_single_joint")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Rounded.AccessibilityNew,
                                contentDescription = null,
                                tint = if (!state.poseEditWholeBody) StudioCoral else InkSlate,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Single Joint",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (!state.poseEditWholeBody) FontWeight.Bold else FontWeight.Medium,
                                color = if (!state.poseEditWholeBody) StudioCoral else InkSlate,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            if (state.poseEditWholeBody) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = PaperLinen.copy(alpha = 0.72f),
                    border = BorderStroke(1.dp, PaperBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Position X & Y
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Character X: ${wholeCenterX.toInt()} px",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    modifier = Modifier.testTag("pose_whole_x_label")
                                )
                                Slider(
                                    value = wholeCenterX.coerceIn(0f, canvasW),
                                    onValueChange = { newCx ->
                                        viewModel.setLayerPosePosition(
                                            layerId = layer.id,
                                            targetCenterX = newCx,
                                            targetCenterY = wholeCenterY,
                                            recordUndo = !sliderUndoRecorded
                                        )
                                        sliderUndoRecorded = true
                                    },
                                    onValueChangeFinished = { sliderUndoRecorded = false },
                                    valueRange = 0f..canvasW,
                                    modifier = Modifier
                                        .height(34.dp)
                                        .testTag("pose_whole_x_slider")
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Character Y: ${wholeCenterY.toInt()} px",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    modifier = Modifier.testTag("pose_whole_y_label")
                                )
                                Slider(
                                    value = wholeCenterY.coerceIn(0f, canvasH),
                                    onValueChange = { newCy ->
                                        viewModel.setLayerPosePosition(
                                            layerId = layer.id,
                                            targetCenterX = wholeCenterX,
                                            targetCenterY = newCy,
                                            recordUndo = !sliderUndoRecorded
                                        )
                                        sliderUndoRecorded = true
                                    },
                                    onValueChangeFinished = { sliderUndoRecorded = false },
                                    valueRange = 0f..canvasH,
                                    modifier = Modifier
                                        .height(34.dp)
                                        .testTag("pose_whole_y_slider")
                                )
                            }
                        }

                        // Whole Skeleton Size (Scale)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Skeleton Size: ${(wholeScale * 100f).roundToInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.testTag("pose_whole_scale_label")
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = PaperSheet,
                                    border = BorderStroke(1.dp, PaperBorder),
                                    modifier = Modifier
                                        .clickable {
                                            viewModel.finishPoseTransformSession()
                                            viewModel.setLayerPoseScale(layer.id, 1f, recordUndo = true)
                                            viewModel.finishPoseTransformSession()
                                        }
                                        .testTag("pose_whole_scale_reset")
                                ) {
                                    Text(
                                        "100%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = StudioCoral,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Slider(
                                value = wholeScale.coerceIn(0.25f, 3.0f),
                                onValueChange = { newScale ->
                                    viewModel.setLayerPoseScale(
                                        layerId = layer.id,
                                        targetScale = newScale,
                                        recordUndo = !sliderUndoRecorded
                                    )
                                    sliderUndoRecorded = true
                                },
                                onValueChangeFinished = {
                                    sliderUndoRecorded = false
                                    viewModel.finishPoseTransformSession()
                                },
                                valueRange = 0.25f..3.0f,
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("pose_whole_scale_slider")
                            )
                        }

                        // Whole Skeleton Angle (Rotation)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Skeleton Angle: ${wholeAngleDeg.roundToInt()}°",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.testTag("pose_whole_angle_label")
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = PaperSheet,
                                    border = BorderStroke(1.dp, PaperBorder),
                                    modifier = Modifier
                                        .clickable {
                                            viewModel.finishPoseTransformSession()
                                            viewModel.setLayerPoseAngle(layer.id, 0f, recordUndo = true)
                                            viewModel.finishPoseTransformSession()
                                        }
                                        .testTag("pose_whole_angle_reset")
                                ) {
                                    Text(
                                        "0°",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = StudioLavender,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Slider(
                                value = wholeAngleDeg.coerceIn(-180f, 180f),
                                onValueChange = { newAngle ->
                                    viewModel.setLayerPoseAngle(
                                        layerId = layer.id,
                                        targetAngleDeg = newAngle,
                                        recordUndo = !sliderUndoRecorded
                                    )
                                    sliderUndoRecorded = true
                                },
                                onValueChangeFinished = {
                                    sliderUndoRecorded = false
                                    viewModel.finishPoseTransformSession()
                                },
                                valueRange = -180f..180f,
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("pose_whole_angle_slider")
                            )
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = PaperLinen.copy(alpha = 0.72f),
                    border = BorderStroke(1.dp, PaperBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            joints.keys.sorted().forEach { name ->
                                val isJointSelected = selectedJoint == name
                                Surface(
                                    shape = CircleShape,
                                    color = if (isJointSelected) StudioCoralSoft else PaperSheet,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isJointSelected) StudioCoral else PaperBorder
                                    ),
                                    modifier = Modifier
                                        .clickable { viewModel.selectPoseJoint(name) }
                                        .testTag("pose_joint_chip_$name")
                                ) {
                                    Text(
                                        name,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isJointSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isJointSelected) StudioCoral else InkCharcoal,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "$selectedJoint X: ${pt.x.toInt()} px",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                modifier = Modifier.testTag("pose_joint_x_label")
                            )
                            Slider(
                                value = pt.x.coerceIn(0f, canvasW),
                                onValueChange = { newX ->
                                    viewModel.updateLayerPoseJoint(
                                        layerId = layer.id,
                                        jointName = selectedJoint,
                                        x = newX,
                                        y = pt.y,
                                        recordUndo = !sliderUndoRecorded
                                    )
                                    sliderUndoRecorded = true
                                },
                                onValueChangeFinished = { sliderUndoRecorded = false },
                                valueRange = 0f..canvasW,
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("pose_joint_x_slider")
                            )
                        }

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "$selectedJoint Y: ${pt.y.toInt()} px",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                modifier = Modifier.testTag("pose_joint_y_label")
                            )
                            Slider(
                                value = pt.y.coerceIn(0f, canvasH),
                                onValueChange = { newY ->
                                    viewModel.updateLayerPoseJoint(
                                        layerId = layer.id,
                                        jointName = selectedJoint,
                                        x = pt.x,
                                        y = newY,
                                        recordUndo = !sliderUndoRecorded
                                    )
                                    sliderUndoRecorded = true
                                },
                                onValueChangeFinished = { sliderUndoRecorded = false },
                                valueRange = 0f..canvasH,
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("pose_joint_y_slider")
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScenePlannerSheet(
    state: StudioUiState,
    viewModel: StudioViewModel,
    onDismiss: () -> Unit
) {
    val project = state.project ?: return
    val scene = state.activeScene ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val orderedScenes = project.scenes.sortedBy { it.orderIndex }

    var durationSec by remember(scene.id) { mutableFloatStateOf(scene.durationMs / 1000f) }
    var voiceover by remember(scene.id) { mutableStateOf(scene.audioPlan.voiceoverText) }
    var captions by remember(scene.id) { mutableStateOf(scene.audioPlan.captionsText) }
    var music by remember(scene.id) { mutableStateOf(scene.audioPlan.musicTrackNote) }
    var pauses by remember(scene.id) { mutableStateOf(scene.audioPlan.pausesAndSilenceNote) }
    var notes by remember(scene.id) { mutableStateOf(scene.notes) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PaperCream
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Reel Scenes & Audio Plan", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Total Reel Duration: ${String.format("%.1fs", project.totalDurationMs / 1000f)} across ${project.scenes.size} scene(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = StudioLavender
                    )
                }
                OutlinedButton(
                    onClick = { viewModel.addNewScene("Scene ${orderedScenes.size + 1}", 3000L) },
                    modifier = Modifier.testTag("planner_add_scene_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Scene")
                }
            }

            // Customizable Scene Sequence Organizer (Swap / Duplicate / Insert Cut / Delete)
            Text("Scene Sequence (Hard Cuts)", style = MaterialTheme.typography.titleSmall)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                orderedScenes.forEachIndexed { idx, sc ->
                    val isSelected = sc.id == scene.id
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) StudioCoralSoft else PaperSheet,
                        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) StudioCoral else PaperBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { viewModel.selectScene(sc.id) }
                                    .testTag("scene_chip_${sc.id}")
                            ) {
                                Text(
                                    "${idx + 1}. ${sc.name}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = InkCharcoal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${String.format("%.1fs", sc.effectiveDurationMs(project.fps) / 1000f)} · ${sc.frames.size} frame(s) · Hard Cut",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkSlate
                                )
                            }

                            IconButton(
                                onClick = { viewModel.moveScene(sc.id, -1) },
                                enabled = idx > 0,
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("scene_move_up_${sc.id}")
                            ) {
                                Icon(
                                    Icons.Rounded.ArrowUpward,
                                    contentDescription = "Move scene earlier",
                                    tint = if (idx > 0) InkCharcoal else InkSlate.copy(alpha = 0.3f),
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.moveScene(sc.id, 1) },
                                enabled = idx < orderedScenes.lastIndex,
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("scene_move_down_${sc.id}")
                            ) {
                                Icon(
                                    Icons.Rounded.ArrowDownward,
                                    contentDescription = "Move scene later",
                                    tint = if (idx < orderedScenes.lastIndex) InkCharcoal else InkSlate.copy(alpha = 0.3f),
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.duplicateScene(sc.id) },
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("scene_duplicate_${sc.id}")
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = "Duplicate scene", tint = StudioLavender, modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = { viewModel.insertBlankSceneAfter(sc.id) },
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("scene_insert_after_${sc.id}")
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = "Insert manual cut after", tint = StudioMint, modifier = Modifier.size(17.dp))
                            }
                            if (orderedScenes.size > 1) {
                                IconButton(
                                    onClick = { viewModel.deleteScene(sc.id) },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .testTag("scene_delete_${sc.id}")
                                ) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete scene", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(17.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Connected Whole-Scene Workflow & Manual Fade / Merge Tools
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = StudioMintSoft.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, StudioMint.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Whole Connected Scenes", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Scrub the full reel, ghost adjacent scene cuts, or merge scenes into one continuous editable timeline.",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSlate
                            )
                        }
                        Switch(
                            checked = state.connectedScenesMode,
                            onCheckedChange = { viewModel.toggleConnectedScenesMode() },
                            modifier = Modifier.testTag("planner_connected_mode_toggle")
                        )
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.addSceneFadeEffect(fadeIn = true, fadeOut = false) },
                            modifier = Modifier.testTag("planner_add_fade_in_button")
                        ) {
                            Text("+ Fade In (0.5s)")
                        }
                        OutlinedButton(
                            onClick = { viewModel.addSceneFadeEffect(fadeIn = false, fadeOut = true) },
                            modifier = Modifier.testTag("planner_add_fade_out_button")
                        ) {
                            Text("+ Fade Out (0.5s)")
                        }
                        if (orderedScenes.indexOfFirst { it.id == scene.id } in 0 until orderedScenes.lastIndex) {
                            OutlinedButton(
                                onClick = { viewModel.mergeSceneWithNext(scene.id) },
                                modifier = Modifier.testTag("planner_merge_next_button")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.MergeType, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Merge Next")
                            }
                        }
                        if (orderedScenes.size > 1) {
                            OutlinedButton(
                                onClick = { viewModel.combineAllScenesIntoOne() },
                                modifier = Modifier.testTag("planner_combine_all_scenes_button")
                            ) {
                                Text("Combine All (${orderedScenes.size})")
                            }
                        }
                    }
                }
            }

            Text("Scene Duration: ${String.format("%.1fs", durationSec)}", style = MaterialTheme.typography.labelMedium)
            Slider(value = durationSec, onValueChange = { durationSec = it }, valueRange = 1f..20f)

            OutlinedTextField(
                value = voiceover,
                onValueChange = { voiceover = it },
                label = { Text("Voiceover / Narration script") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = captions,
                onValueChange = { captions = it },
                label = { Text("On-screen Captions") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = music,
                onValueChange = { music = it },
                label = { Text("Background Music & SFX direction") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = pauses,
                onValueChange = { pauses = it },
                label = { Text("Pauses / Silence timing") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Scene direction notes") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    viewModel.updateSceneReelDetails(
                        durationMs = (durationSec * 1000f).toLong(),
                        audioPlan = scene.audioPlan.copy(
                            voiceoverText = voiceover.trim(),
                            captionsText = captions.trim(),
                            musicTrackNote = music.trim(),
                            pausesAndSilenceNote = pauses.trim()
                        ),
                        notes = notes.trim()
                    )
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = StudioCoral)
            ) {
                Text("Save Scene Plan")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorAndBrushSheet(
    state: StudioUiState,
    viewModel: StudioViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var hexInput by remember(state.brushColorHex) { mutableStateOf(state.brushColorHex) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PaperCream
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Brush & Color Palette", style = MaterialTheme.typography.headlineSmall)

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StudioSwatches.forEach { hex ->
                    val isSelected = state.brushColorHex.equals(hex, ignoreCase = true)
                    Surface(
                        shape = CircleShape,
                        color = parseHexColor(hex),
                        border = BorderStroke(if (isSelected) 3.dp else 1.5.dp, if (isSelected) StudioCoral else PaperBorder),
                        modifier = Modifier
                            .size(40.dp)
                            .clickable {
                                hexInput = hex
                                viewModel.setBrushColor(hex)
                            }
                    ) {}
                }
            }

            OutlinedTextField(
                value = hexInput,
                onValueChange = {
                    hexInput = it
                    if (Regex("^#[0-9A-Fa-f]{6}$").matches(it.trim())) {
                        viewModel.setBrushColor(it.trim().uppercase())
                    }
                },
                label = { Text("Custom Hex Color (#RRGGBB)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Stroke Size: ${state.brushSize.toInt()} px", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = state.brushSize,
                onValueChange = { viewModel.setBrushSize(it) },
                valueRange = 2f..60f
            )

            Text("Brush Opacity: ${(state.brushOpacity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = state.brushOpacity,
                onValueChange = { viewModel.setBrushOpacity(it) },
                valueRange = 0.1f..1f
            )

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = StudioCoral)
            ) {
                Text("Done")
            }
        }
    }
}
