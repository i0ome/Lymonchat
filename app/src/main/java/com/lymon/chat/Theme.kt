package com.lymon.chat

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LymonGreen = Color(0xFF1E6B34)
val LymonLemon = Color(0xFFF4D31B)
val LogoBg = Color(0xFFF1F1F1)
val BlueBadge = Color(0xFF1DA1F2)
val GoldBadge = Color(0xFFFFB800)

@Composable
fun LymonTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = LymonGreen,
            onPrimary = Color.White,
            secondary = LymonLemon,
            onSecondary = Color.Black,
            primaryContainer = Color(0xFFDDEFD9),
            background = Color.White,
            surface = Color.White
        ),
        content = content
    )
}

/** علامة التوثيق: ذهبية للإدارة، زرقاء للموثقين */
@Composable
fun BadgeIcon(profile: UserProfile?, size: Dp = 16.dp) {
    val color = when (profile?.badge) {
        "gold" -> GoldBadge
        "blue" -> BlueBadge
        else -> return
    }
    Icon(Icons.Filled.Verified, contentDescription = "موثّق", tint = color, modifier = Modifier.size(size))
}
