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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import com.example.domain.model.EasingType
import com.example.domain.model.MotionActionType
import com.example.domain.model.MotionKeyframe
import com.example.domain.model.MotionTargetType
import com.example.domain.model.MotionTrack
import com.example.domain.model.OrbitConfig
import com.example.domain.model.OrbitDirection
import com.example.domain.model.ParticleConfig
import com.example.domain.model.SceneTransitionType
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoseEditorSheet(
    state: StudioUiState,
    viewModel: StudioViewModel,
    onDismiss: () -> Unit
) {
    val layer = state.selectedLayer ?: return
    val pose = layer.skeletonPose
    val joints = pose?.joints ?: defaultStickmanJoints(540f, 960f)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedJoint by remember { mutableStateOf("rightHand") }
    val pt = joints[selectedJoint] ?: joints.values.first()

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
            Text("2D Character Pose Keyframe", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Named joint coordinates for stick-figure posing & AI motion plans (2D pose helper, not a skinned deformation rig).",
                style = MaterialTheme.typography.bodySmall,
                color = InkSlate
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                joints.keys.sorted().forEach { name ->
                    FilterChip(
                        selected = selectedJoint == name,
                        onClick = { selectedJoint = name },
                        label = { Text(name) }
                    )
                }
            }

            Text("$selectedJoint X: ${pt.x.toInt()} px", style = MaterialTheme.typography.labelMedium, modifier = Modifier.testTag("pose_joint_x_label"))
            Slider(
                value = pt.x,
                onValueChange = { newX -> viewModel.updateLayerPoseJoint(layer.id, selectedJoint, newX, pt.y) },
                valueRange = 50f..(state.project?.canvasWidth ?: 1080).toFloat(),
                modifier = Modifier.testTag("pose_joint_x_slider")
            )
            Text("$selectedJoint Y: ${pt.y.toInt()} px", style = MaterialTheme.typography.labelMedium, modifier = Modifier.testTag("pose_joint_y_label"))
            Slider(
                value = pt.y,
                onValueChange = { newY -> viewModel.updateLayerPoseJoint(layer.id, selectedJoint, pt.x, newY) },
                valueRange = 50f..(state.project?.canvasHeight ?: 1920).toFloat(),
                modifier = Modifier.testTag("pose_joint_y_slider")
            )

            OutlinedButton(
                onClick = { viewModel.resetLayerPoseToDefault(layer.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pose_reset_default_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Reset to Default")
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pose_editor_done_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Done")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScenePlannerSheet(
    state: StudioUiState,
    viewModel: StudioViewModel,
    onDismiss: () -> Unit
) {
    val project = state.project ?: return
    val scene = state.activeScene ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var durationSec by remember(scene.id) { mutableFloatStateOf(scene.durationMs / 1000f) }
    var voiceover by remember(scene.id) { mutableStateOf(scene.audioPlan.voiceoverText) }
    var captions by remember(scene.id) { mutableStateOf(scene.audioPlan.captionsText) }
    var music by remember(scene.id) { mutableStateOf(scene.audioPlan.musicTrackNote) }
    var pauses by remember(scene.id) { mutableStateOf(scene.audioPlan.pausesAndSilenceNote) }
    var transition by remember(scene.id) { mutableStateOf(scene.transitionOut.type) }
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
                Column {
                    Text("Reel Scenes & Audio Plan", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Total Reel Duration: ${String.format("%.1fs", project.totalDurationMs / 1000f)} across ${project.scenes.size} scene(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = StudioLavender
                    )
                }
                OutlinedButton(onClick = { viewModel.addNewScene("Scene ${project.scenes.size + 1}", 3000L) }) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Scene")
                }
            }

            // Scene switcher chips
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                project.scenes.sortedBy { it.orderIndex }.forEachIndexed { idx, sc ->
                    FilterChip(
                        selected = sc.id == scene.id,
                        onClick = { viewModel.selectScene(sc.id) },
                        label = { Text("${idx + 1}. ${sc.name} (${sc.effectiveDurationMs(project.fps) / 1000f}s)") }
                    )
                }
            }

            Text("Scene Duration: ${String.format("%.1fs", durationSec)}", style = MaterialTheme.typography.labelMedium)
            Slider(value = durationSec, onValueChange = { durationSec = it }, valueRange = 1f..20f)

            Text("Transition to next scene", style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SceneTransitionType.entries.forEach { tr ->
                    FilterChip(selected = transition == tr, onClick = { transition = tr }, label = { Text(tr.label) })
                }
            }

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
                        transition = scene.transitionOut.copy(type = transition),
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
