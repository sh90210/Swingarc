package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val SwingArcColorScheme = darkColorScheme(
    primary = ElectricEmerald,
    onPrimary = ObsidianBg,
    primaryContainer = EmeraldDeep,
    onPrimaryContainer = FollowThroughMint,
    secondary = BackswingCyan,
    onSecondary = ObsidianBg,
    secondaryContainer = CyanDeep,
    onSecondaryContainer = TurfWhite,
    tertiary = ImpactGold,
    onTertiary = ObsidianBg,
    tertiaryContainer = GoldDeep,
    onTertiaryContainer = TurfWhite,
    background = ObsidianBg,
    onBackground = TurfWhite,
    surface = FairwaySurface,
    onSurface = TurfWhite,
    surfaceVariant = FairwaySurfaceElevated,
    onSurfaceVariant = SageMuted,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = FairwaySurface,
    surfaceContainer = FairwaySurfaceElevated,
    surfaceContainerHigh = FairwaySurfaceHigh,
    outline = FairwayOutline,
    outlineVariant = FairwayOutlineSubtle,
    error = AlertCoral,
    onError = ObsidianBg,
    errorContainer = AlertDeep,
    onErrorContainer = TurfWhite
)

private val SwingArcShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun SwingArcTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SwingArcColorScheme,
        typography = SwingArcTypography,
        shapes = SwingArcShapes,
        content = content
    )
}
