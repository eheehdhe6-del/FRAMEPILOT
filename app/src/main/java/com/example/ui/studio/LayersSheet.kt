package com.example.ui.studio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.BackgroundType
import com.example.domain.model.Layer
import com.example.domain.model.LayerCategory
import com.example.ui.components.parseHexColor
import com.example.ui.theme.InkCharcoal
import com.example.ui.theme.InkSlate
import com.example.ui.theme.PaperBorder
import com.example.ui.theme.PaperCream
import com.example.ui.theme.PaperLinen
import com.example.ui.theme.PaperSheet
import com.example.ui.theme.StudioCoral
import com.example.ui.theme.StudioCoralSoft
import com.example.ui.theme.StudioLavender
import com.example.ui.theme.StudioMint
import com.example.ui.theme.StudioSky
import com.example.ui.theme.StudioSkySoft
import com.example.ui.theme.StudioSwatches

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LayersSheet(
    state: StudioUiState,
    viewModel: StudioViewModel,
    onDismiss: () -> Unit
) {
    val scene = state.activeScene ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showAddLayerDialog by remember { mutableStateOf(false) }
    var renameLayerTarget by remember { mutableStateOf<Layer?>(null) }

    // Display top-most layer first in panel (standard drawing app convention!)
    val layersTopFirst = remember(scene.layers) { scene.orderedLayers().reversed() }

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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Layers & Environment", style = MaterialTheme.typography.headlineSmall)
                    Text("Top layer sits in front of layers below.", style = MaterialTheme.typography.bodySmall, color = InkSlate)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.openSheet(StudioSheet.BACKGROUND) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Landscape, contentDescription = null, modifier = Modifier.size(16.dp), tint = StudioSky)
                        Spacer(Modifier.width(4.dp))
                        Text("Backdrop")
                    }
                    Button(
                        onClick = { showAddLayerDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = StudioCoral),
                        modifier = Modifier.testTag("add_layer_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Layer")
                    }
                }
            }

            // Opacity control for selected layer
            state.selectedLayer?.let { sel ->
                val riggedCount = sel.sceneShapes.count { it.rigAttachment != null } +
                    sel.sceneStrokes.count { it.rigAttachment != null }
                val unboundCount = sel.sceneShapes.count { it.rigAttachment == null } +
                    sel.sceneStrokes.count { it.rigAttachment == null }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = PaperSheet,
                    border = BorderStroke(1.dp, PaperBorder)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Selected: ${sel.name}", style = MaterialTheme.typography.titleSmall)
                            Text("${(sel.opacity * 100).toInt()}% opacity", style = MaterialTheme.typography.labelSmall, color = InkSlate)
                        }
                        if (sel.groups.isNotEmpty() || riggedCount > 0) {
                            Text(
                                "${sel.groups.size} group(s) · $riggedCount rigged element(s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = StudioLavender
                            )
                        }
                        Slider(
                            value = sel.opacity,
                            onValueChange = { viewModel.setLayerOpacity(sel.id, it) },
                            valueRange = 0.05f..1f
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            TextButton(onClick = { viewModel.openSheet(StudioSheet.AI_INSPECTOR) }) {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = StudioLavender)
                                Spacer(Modifier.width(4.dp))
                                Text("Describe for AI")
                            }
                            if (sel.category == LayerCategory.CHARACTER || sel.skeletonPose != null) {
                                TextButton(
                                    onClick = { viewModel.openSheet(StudioSheet.POSE_EDITOR) },
                                    modifier = Modifier.testTag("edit_2d_pose_button")
                                ) {
                                    Text("Edit 2D Pose", color = StudioMint)
                                }
                                if (unboundCount > 0) {
                                    TextButton(
                                        onClick = { viewModel.autoBindLayerToRig(sel.id) },
                                        modifier = Modifier.testTag("layers_auto_rig_button")
                                    ) {
                                        Text("Auto-Attach ($unboundCount)", color = StudioLavender)
                                    }
                                }
                            } else {
                                TextButton(
                                    onClick = {
                                        viewModel.ensureSkeletonRigOnLayer(sel.id)
                                        viewModel.openSheet(StudioSheet.POSE_EDITOR)
                                    },
                                    modifier = Modifier.testTag("add_2d_rig_button")
                                ) {
                                    Text("Add 2D Rig", color = StudioMint)
                                }
                            }
                        }
                    }
                }
            }

            // Layers list
            layersTopFirst.forEach { layer ->
                val isSelected = layer.id == state.selectedLayerId
                val catColor = parseHexColor(layer.category.colorHex, StudioCoral)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) StudioCoralSoft.copy(alpha = 0.5f) else PaperSheet,
                    border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) StudioCoral else PaperBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.selectLayer(layer.id) }
                        .testTag("layer_row_${layer.id}")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(catColor)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(layer.name, style = MaterialTheme.typography.titleSmall, color = InkCharcoal)
                                val labelText = layer.semantic.displayLabel("unlabelled")
                                Text(
                                    "${layer.category.label} · $labelText",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkSlate
                                )
                            }
                            IconButton(onClick = { viewModel.toggleLayerVisible(layer.id) }, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    if (layer.visible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                    contentDescription = "Toggle visibility",
                                    tint = if (layer.visible) InkCharcoal else InkSlate.copy(alpha = 0.4f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(onClick = { viewModel.toggleLayerLocked(layer.id) }, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    if (layer.locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                                    contentDescription = "Toggle lock",
                                    tint = if (layer.locked) StudioCoral else InkSlate,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { viewModel.moveLayer(layer.id, +1) }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move layer forward", modifier = Modifier.size(17.dp))
                            }
                            IconButton(onClick = { viewModel.moveLayer(layer.id, -1) }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move layer backward", modifier = Modifier.size(17.dp))
                            }
                            IconButton(onClick = { renameLayerTarget = layer }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = "Rename layer", modifier = Modifier.size(17.dp))
                            }
                            IconButton(onClick = { viewModel.duplicateLayer(layer.id) }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = "Duplicate layer", modifier = Modifier.size(17.dp))
                            }
                            IconButton(onClick = { viewModel.deleteLayer(layer.id) }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete layer", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(17.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddLayerDialog) {
        var newName by remember { mutableStateOf("") }
        var category by remember { mutableStateOf(LayerCategory.PROPS) }
        AlertDialog(
            onDismissRequest = { showAddLayerDialog = false },
            title = { Text("New Layer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Layer name") },
                        placeholder = { Text("e.g., Hero, Butterfly, Trees") },
                        singleLine = true
                    )
                    Text("Category", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LayerCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(cat.label) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.createLayer(newName, category)
                    showAddLayerDialog = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showAddLayerDialog = false }) { Text("Cancel") }
            }
        )
    }

    renameLayerTarget?.let { layer ->
        var text by remember(layer.id) { mutableStateOf(layer.name) }
        AlertDialog(
            onDismissRequest = { renameLayerTarget = null },
            title = { Text("Rename Layer") },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameLayer(layer.id, text)
                    renameLayerTarget = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameLayerTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundEditorSheet(
    state: StudioUiState,
    viewModel: StudioViewModel,
    onDismiss: () -> Unit
) {
    val scene = state.activeScene ?: return
    val bg = scene.background
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var type by remember(bg) { mutableStateOf(bg.type) }
    var primaryHex by remember(bg) { mutableStateOf(bg.primaryColorHex) }
    var secondaryHex by remember(bg) { mutableStateOf(bg.secondaryColorHex) }
    var envDesc by remember(bg) { mutableStateOf(bg.environmentDescription) }
    var elementsText by remember(bg) { mutableStateOf(bg.elementsDescription.joinToString("\n")) }
    var textureNote by remember(bg) { mutableStateOf(bg.textureNote) }
    var imageUri by remember(bg) { mutableStateOf(bg.imageUri) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            imageUri = uri.toString()
            type = BackgroundType.IMAGE
        }
    }

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
            Text("Background & Environment", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Describe the world behind your characters so OpenCode understands sky, floor, trees, and light.",
                style = MaterialTheme.typography.bodySmall,
                color = InkSlate
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BackgroundType.entries.forEach { bt ->
                    FilterChip(
                        selected = type == bt,
                        onClick = { type = bt },
                        label = { Text(bt.label) }
                    )
                }
            }

            Text("Backdrop Colors", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("#FFFDF9", "#DFF2FF", "#FFE7E3", "#EEEBFF", "#DDF7EE", "#FFF5D6", "#1F2430").forEach { hex ->
                    Surface(
                        shape = CircleShape,
                        color = parseHexColor(hex),
                        border = BorderStroke(if (primaryHex == hex) 3.dp else 1.dp, if (primaryHex == hex) StudioCoral else PaperBorder),
                        modifier = Modifier
                            .size(36.dp)
                            .clickable { primaryHex = hex }
                    ) {}
                }
            }

            if (type == BackgroundType.GRADIENT) {
                Text("Gradient Bottom Color", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("#FFF7E4", "#E4F1FF", "#FFD15C", "#FF6B5B", "#7E6BFF", "#2EC49A").forEach { hex ->
                        Surface(
                            shape = CircleShape,
                            color = parseHexColor(hex),
                            border = BorderStroke(if (secondaryHex == hex) 3.dp else 1.dp, if (secondaryHex == hex) StudioLavender else PaperBorder),
                            modifier = Modifier
                                .size(34.dp)
                                .clickable { secondaryHex = hex }
                        ) {}
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Rounded.Image, contentDescription = null, tint = StudioSky)
                Spacer(Modifier.width(6.dp))
                Text(if (imageUri == null) "Import Background / Reference Photo" else "Change Reference Photo ✓")
            }

            OutlinedTextField(
                value = envDesc,
                onValueChange = { envDesc = it },
                label = { Text("What is this environment? (for AI)") },
                placeholder = { Text("e.g., Sunny park with blue sky, distant mountains, grass floor") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = elementsText,
                onValueChange = { elementsText = it },
                label = { Text("Background elements (one per line)") },
                placeholder = { Text("Sky: soft blue gradient\nMountains: distant purple ridge\nFloor: green grass") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = textureNote,
                onValueChange = { textureNote = it },
                label = { Text("Lighting / Texture mood") },
                placeholder = { Text("e.g., Warm morning paper grain, soft shadows") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    viewModel.updateBackground(
                        bg.copy(
                            type = type,
                            primaryColorHex = primaryHex,
                            secondaryColorHex = secondaryHex,
                            imageUri = imageUri,
                            textureNote = textureNote,
                            environmentDescription = envDesc.trim(),
                            elementsDescription = elementsText.lines().map { it.trim() }.filter { it.isNotBlank() }
                        )
                    )
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = StudioCoral)
            ) {
                Text("Save Backdrop")
            }
        }
    }
}
