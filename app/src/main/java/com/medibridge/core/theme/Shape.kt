package com.medibridge.core.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val MediBridgeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),   // 16dp cards
    large = RoundedCornerShape(24.dp),    // 24dp sheets
    extraLarge = CircleShape              // full-round chips and buttons
)

val CardShape = RoundedCornerShape(16.dp)
val SheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
val ButtonShape = RoundedCornerShape(14.dp)
val ChipShape = RoundedCornerShape(14.dp)
val FullRoundShape = CircleShape
