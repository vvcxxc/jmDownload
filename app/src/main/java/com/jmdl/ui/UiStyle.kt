package com.jmdl.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Shared alpha used by cards/top bars so the home background shows through. */
internal const val CARD_ALPHA = 0.8f

internal val DialogShape = RoundedCornerShape(28.dp)
internal val CardShape = RoundedCornerShape(12.dp)
internal val SmallShape = RoundedCornerShape(8.dp)
internal val TinyShape = RoundedCornerShape(6.dp)
internal val ChipShape = RoundedCornerShape(4.dp)

internal val OverlayScrim = Color(0xCC000000)
internal val TextMutedOnBlack = Color(0x88FFFFFF)
internal val TextSecondaryOnBlack = Color(0xBBFFFFFF)
internal val BorderSubtleOnBlack = Color(0x33FFFFFF)
internal val BorderSubtleDark = Color(0x33000000)

internal val ViewerThumbWidth = 46.dp
internal val ViewerThumbHeight = 62.dp

@Composable
internal fun appCardSurfaceColor(): Color =
    MaterialTheme.colorScheme.surface.copy(alpha = CARD_ALPHA)

@Composable
internal fun appCardColors() = CardDefaults.cardColors(
    containerColor = appCardSurfaceColor(),
)
