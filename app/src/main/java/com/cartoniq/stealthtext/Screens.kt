package com.cartoniq.stealthtext

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class OnboardPage(val title: String, val body: String)

private val onboardPages = listOf(
    OnboardPage("Hide secrets in photos", "Pick a normal photo and write your secret. It looks unchanged, but your message rides inside its pixels."),
    OnboardPage("Locked with real encryption", "Everything is sealed with AES-256 and your password. Without it, the photo is just a photo."),
    OnboardPage("Text, files and voice", "Hide notes, PDFs and voice notes. Or split one secret across two images, so you need both."),
    OnboardPage("Offline. Panic ready.", "No internet, no accounts. Shake to lock, use a decoy password, or open as a calculator.")
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    BackHandler(enabled = page > 0) { page-- }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${page + 1} / 4", color = Sub, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onDone) { Text(tr("Skip"), color = Sub, fontSize = 15.sp) }
        }
        Spacer(Modifier.height(12.dp))
        GlassCard(Modifier.fillMaxWidth().height(280.dp)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Illustration(page) }
        }
        Spacer(Modifier.height(28.dp))
        Crossfade(targetState = page, label = "onboard") { p ->
            Column {
                Text(tr(onboardPages[p].title), color = Ink, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 36.sp)
                Spacer(Modifier.height(12.dp))
                Text(tr(onboardPages[p].body), color = Sub, fontSize = 17.sp, lineHeight = 25.sp)
            }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.Center) {
            repeat(4) { i ->
                Box(
                    Modifier.padding(horizontal = 4.dp).height(8.dp).width(if (i == page) 28.dp else 8.dp)
                        .clip(CircleShape).background(if (i == page) Magenta else Color(0xFFC9CFE0))
                )
            }
        }
        GradientButton(if (page == 3) tr("Get started") else tr("Next")) {
            if (page == 3) onDone() else page++
        }
    }
}

@Composable
private fun Illustration(page: Int) {
    Canvas(Modifier.size(240.dp, 200.dp)) {
        scale(size.width / 240f, Offset.Zero) {
            when (page) {
                0 -> {
                    drawRoundRect(Color.White, Offset(40f, 50f), Size(160f, 120f), CornerRadius(20f, 20f))
                    drawRoundRect(Cyan, Offset(40f, 50f), Size(160f, 120f), CornerRadius(20f, 20f), style = Stroke(4f))
                    drawCircle(Sun, 14f, Offset(85f, 90f))
                    val hill = Path().apply {
                        moveTo(50f, 160f); lineTo(95f, 120f); lineTo(125f, 145f); lineTo(155f, 115f); lineTo(190f, 160f); close()
                    }
                    drawPath(hill, Lavender)
                    drawRect(Magenta, Offset(150f, 62f), Size(8f, 8f))
                    drawRect(Magenta, Offset(168f, 76f), Size(8f, 8f))
                    drawRect(Magenta, Offset(156f, 92f), Size(8f, 8f))
                }
                1 -> {
                    val shield = Path().apply {
                        moveTo(120f, 28f); lineTo(192f, 54f); lineTo(192f, 110f)
                        cubicTo(192f, 150f, 162f, 178f, 120f, 194f)
                        cubicTo(78f, 178f, 48f, 150f, 48f, 110f)
                        lineTo(48f, 54f); close()
                    }
                    drawPath(shield, Color.White)
                    drawPath(shield, Magenta, style = Stroke(4f))
                    drawRoundRect(Cyan, Offset(92f, 98f), Size(56f, 44f), CornerRadius(10f, 10f))
                    drawArc(Ink, 180f, 180f, false, Offset(104f, 70f), Size(32f, 32f), style = Stroke(5f))
                    drawLine(Ink, Offset(104f, 86f), Offset(104f, 98f), 5f)
                    drawLine(Ink, Offset(136f, 86f), Offset(136f, 98f), 5f)
                    drawCircle(Sun, 6f, Offset(120f, 120f))
                }
                2 -> {
                    drawRoundRect(Color.White, Offset(30f, 60f), Size(86f, 100f), CornerRadius(16f, 16f))
                    drawRoundRect(Cyan, Offset(30f, 60f), Size(86f, 100f), CornerRadius(16f, 16f), style = Stroke(4f))
                    drawRoundRect(Color.White, Offset(124f, 60f), Size(86f, 100f), CornerRadius(16f, 16f))
                    drawRoundRect(Magenta, Offset(124f, 60f), Size(86f, 100f), CornerRadius(16f, 16f), style = Stroke(4f))
                    drawLine(Lavender, Offset(50f, 90f), Offset(96f, 90f), 6f, StrokeCap.Round)
                    drawLine(Lavender, Offset(50f, 110f), Offset(96f, 110f), 6f, StrokeCap.Round)
                    drawLine(Lavender, Offset(50f, 130f), Offset(80f, 130f), 6f, StrokeCap.Round)
                    drawRoundRect(Sun, Offset(146f, 92f), Size(10f, 36f), CornerRadius(5f, 5f))
                    drawRoundRect(Magenta, Offset(164f, 80f), Size(10f, 60f), CornerRadius(5f, 5f))
                    drawRoundRect(Cyan, Offset(182f, 100f), Size(10f, 20f), CornerRadius(5f, 5f))
                }
                else -> {
                    drawCircle(Color.White, 70f, Offset(120f, 100f))
                    drawCircle(Lavender, 70f, Offset(120f, 100f), style = Stroke(4f))
                    drawLine(Cyan, Offset(62f, 100f), Offset(178f, 100f), 4f)
                    drawOval(Cyan, Offset(88f, 30f), Size(64f, 140f), style = Stroke(4f))
                    drawLine(Magenta, Offset(52f, 52f), Offset(188f, 148f), 8f, StrokeCap.Round)
                    drawCircle(Sun, 14f, Offset(190f, 40f))
                }
            }
        }
    }
}

