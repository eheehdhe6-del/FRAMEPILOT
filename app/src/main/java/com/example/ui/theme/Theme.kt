package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val FramePilotLightColorScheme = lightColorScheme(
    primary = StudioCoral,
    onPrimary = PaperElevated,
    primaryContainer = StudioCoralSoft,
    onPrimaryContainer = InkCharcoal,
    secondary = StudioLavender,
    onSecondary = PaperElevated,
    secondaryContainer = StudioLavenderSoft,
    onSecondaryContainer = InkCharcoal,
    tertiary = StudioMint,
    onTertiary = PaperElevated,
    tertiaryContainer = StudioMintSoft,
    onTertiaryContainer = InkCharcoal,
    background = PaperCream,
    onBackground = InkCharcoal,
    surface = PaperSheet,
    onSurface = InkCharcoal,
    surfaceVariant = PaperLinen,
    onSurfaceVariant = InkSlate,
    outline = PaperBorder,
    outlineVariant = PaperBorder,
    error = StudioError,
    onError = PaperElevated,
    errorContainer = StudioErrorSoft,
    onErrorContainer = InkCharcoal
)

val FramePilotShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun FramePilotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FramePilotLightColorScheme,
        typography = FramePilotTypography,
        shapes = FramePilotShapes,
        content = content
    )
}
