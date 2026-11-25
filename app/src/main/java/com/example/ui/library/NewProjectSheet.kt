package com.example.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.model.AspectRatioPreset
import com.example.ui.theme.InkCharcoal
import com.example.ui.theme.InkSlate
import com.example.ui.theme.PaperBorder
import com.example.ui.theme.PaperCream
import com.example.ui.theme.PaperLinen
import com.example.ui.theme.StudioCoral
import com.example.ui.theme.StudioCoralSoft
import com.example.ui.theme.StudioLavender

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewProjectSheet(
    onDismiss: () -> Unit,
    onCreate: (
        name: String,
        preset: AspectRatioPreset,
        customW: Int,
        customH: Int,
        fps: Int,
        startTutorial: Boolean
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf("My Reel Story") }
    var selectedPreset by remember { mutableStateOf(AspectRatioPreset.REEL_9_16) }
    var customWidth by remember { mutableStateOf("1080") }
    var customHeight by remember { mutableStateOf("1920") }
    var selectedFps by remember { mutableIntStateOf(24) }
    var startTutorial by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PaperCream
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = StudioCoralSoft,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Brush, contentDescription = null, tint = StudioCoral)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("New Animation Project", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Pick a canvas size and start drawing right away.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSlate
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Project Name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("new_project_name_input"),
                shape = RoundedCornerShape(14.dp)
            )

            Text("Canvas Size & Aspect Ratio", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AspectRatioPreset.entries.forEach { preset ->
                    val isSelected = selectedPreset == preset
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) StudioCoralSoft else PaperLinen,
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) StudioCoral else PaperBorder
                        ),
                        modifier = Modifier
                            .clickable { selectedPreset = preset }
                            .testTag("preset_${preset.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val ratioW = when (preset) {
                                AspectRatioPreset.REEL_9_16 -> 10.dp
                                AspectRatioPreset.YOUTUBE_16_9 -> 20.dp
                                AspectRatioPreset.SQUARE_1_1 -> 14.dp
                                AspectRatioPreset.PORTRAIT_4_5 -> 12.dp
                                AspectRatioPreset.CUSTOM -> 16.dp
                            }
                            val ratioH = when (preset) {
                                AspectRatioPreset.REEL_9_16 -> 18.dp
                                AspectRatioPreset.YOUTUBE_16_9 -> 12.dp
                                AspectRatioPreset.SQUARE_1_1 -> 14.dp
                                AspectRatioPreset.PORTRAIT_4_5 -> 15.dp
                                AspectRatioPreset.CUSTOM -> 16.dp
                            }
                            Box(
                                modifier = Modifier
                                    .size(width = ratioW, height = ratioH)
                                    .background(
                                        if (isSelected) StudioCoral else InkSlate.copy(alpha = 0.35f),
                                        RoundedCornerShape(3.dp)
                                    )
                            )
                            Column {
                                Text(
                                    preset.shortCode,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = InkCharcoal
                                )
                                Text(
                                    if (preset == AspectRatioPreset.CUSTOM) "Custom px"
                                    else "${preset.width}×${preset.height}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkSlate
                                )
                            }
                        }
                    }
                }
            }

            if (selectedPreset == AspectRatioPreset.CUSTOM) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = customWidth,
                        onValueChange = { customWidth = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Width (px)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = customHeight,
                        onValueChange = { customHeight = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Height (px)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Text("Frame Rate (FPS)", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(12 to "12 FPS · Sketch", 24 to "24 FPS · Smooth", 30 to "30 FPS · Reels").forEach { (fps, label) ->
                    FilterChip(
                        selected = selectedFps == fps,
                        onClick = { selectedFps = fps },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = StudioLavender.copy(alpha = 0.16f),
                            selectedLabelColor = InkCharcoal
                        ),
                        modifier = Modifier.testTag("fps_chip_$fps")
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PaperLinen, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = StudioLavender)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Show quick studio tips", style = MaterialTheme.typography.titleSmall)
                        Text("Short interactive hints while you draw", style = MaterialTheme.typography.bodySmall, color = InkSlate)
                    }
                }
                Switch(checked = startTutorial, onCheckedChange = { startTutorial = it })
            }

            Button(
                onClick = {
                    onCreate(
                        name.trim().ifBlank { "Untitled Studio" },
                        selectedPreset,
                        customWidth.toIntOrNull() ?: 1080,
                        customHeight.toIntOrNull() ?: 1920,
                        selectedFps,
                        startTutorial
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("start_creating_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = StudioCoral)
            ) {
                Text("Start Creating", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}
