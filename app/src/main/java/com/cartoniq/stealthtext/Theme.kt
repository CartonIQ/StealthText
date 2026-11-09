package com.cartoniq.stealthtext

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Bg = Color(0xFFF8F9FA)
val Cyan = Color(0xFF00BFFF)
val Magenta = Color(0xFFFF4FA3)
val Lavender = Color(0xFFA78BFA)
val Sun = Color(0xFFFFD700)
val Ink = Color(0xFF182333)
val Sub = Color(0xFF687386)
val Warn = Color(0xFFC2185B)
val Good = Color(0xFF0B7A55)

fun Modifier.prismBackground(): Modifier = this.background(Bg).drawBehind {
    drawRect(
        Brush.radialGradient(
            listOf(Cyan.copy(alpha = 0.30f), Color.Transparent),
            center = Offset(0f, 0f),
            radius = size.width * 0.9f
        )
    )
    drawRect(
        Brush.radialGradient(
            listOf(Magenta.copy(alpha = 0.28f), Color.Transparent),
            center = Offset(size.width, size.height * 0.3f),
            radius = size.width * 0.9f
        )
    )
    drawRect(
        Brush.radialGradient(
            listOf(Lavender.copy(alpha = 0.32f), Color.Transparent),
            center = Offset(size.width * 0.2f, size.height),
            radius = size.width
        )
    )
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.66f))
            .border(1.5.dp, Color.White, shape)
    ) {
        content()
    }
}

@Composable
fun GradientButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.horizontalGradient(listOf(Cyan, Magenta)))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}
