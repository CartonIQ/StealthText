package com.cartoniq.stealthtext

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val pinRows = listOf(
    listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("C", "0", "⌫")
)

@Composable
fun PinLockScreen() {
    val ctx = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Text("StealthText", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text(tr("Enter your PIN"), color = Sub, fontSize = 15.sp)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(8) { i ->
                Box(Modifier.size(12.dp).clip(CircleShape).background(if (i < pin.length) Magenta else Color(0xFFC9CFE0)))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(msg, color = Warn, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.height(22.dp))
        Spacer(Modifier.height(10.dp))
        KeyGrid(pinRows) { key ->
            when (key) {
                "⌫" -> pin = pin.dropLast(1)
                "C" -> pin = ""
                else -> if (pin.length < 8) pin += key
            }
            Unit
        }
        Spacer(Modifier.height(16.dp))
        GradientButton(tr("Unlock"), pin.length >= 4) {
            if (Prefs.verifyPin(ctx, pin)) {
                Prefs.putFails(ctx, 0)
                AppState.locked = false
            } else if (Prefs.duressSet(ctx) && Prefs.verifyDuress(ctx, pin)) {
                Prefs.wipeVaultOnly(ctx)
                Prefs.putFails(ctx, 0)
                AppState.locked = false
            } else {
                val n = Prefs.fails(ctx) + 1
                Prefs.putFails(ctx, n)
                pin = ""
                if (n >= Prefs.limit(ctx)) {
                    Prefs.wipeAll(ctx)
                    Disguise.apply(ctx, false)
                    AppState.onboarded = false
                    AppState.locked = false
                } else if (n >= 3) {
                    AppState.crashed = true
                } else {
                    msg = "Wrong PIN ($n)"
                }
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
fun FakeCrashScreen(onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color(0xFFF1F3F8)).padding(24.dp)
        ) {
            Text(tr("StealthText keeps stopping"), color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(16.dp))
            Text(tr("App info"), color = Color(0xFF3B5BDB), fontSize = 16.sp, modifier = Modifier.padding(vertical = 10.dp))
            TextButton(onClick = onClose) { Text(tr("Close app"), color = Color(0xFF3B5BDB), fontSize = 16.sp) }
        }
    }
}

private val calcRows = listOf(
    listOf("C", "⌫", "÷", "×"),
    listOf("7", "8", "9", "-"),
    listOf("4", "5", "6", "+"),
    listOf("1", "2", "3", "="),
    listOf("0", ".", "", "")
)

private fun applyOp(a: Double, b: Double, op: String): Double = when (op) {
    "+" -> a + b
    "-" -> a - b
    "×" -> a * b
    else -> if (b == 0.0) Double.NaN else a / b
}

private fun fmt(d: Double): String =
    if (d.isNaN() || d.isInfinite()) "Error"
    else if (d == Math.floor(d) && Math.abs(d) < 1e12) d.toLong().toString()
    else d.toString()

@Composable
fun CalculatorScreen() {
    val ctx = LocalContext.current
    var cur by remember { mutableStateOf("") }
    var acc by remember { mutableStateOf<Double?>(null) }
    var op by remember { mutableStateOf<String?>(null) }
    var shown by remember { mutableStateOf("0") }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Bottom) {
        Text(shown, color = Ink, fontSize = 56.sp, fontWeight = FontWeight.Light, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(20.dp))
        KeyGrid(calcRows) { key ->
            when {
                key == "C" -> {
                    cur = ""
                    acc = null
                    op = null
                    shown = "0"
                }
                key == "⌫" -> {
                    cur = cur.dropLast(1)
                    shown = if (cur.isEmpty()) "0" else cur
                }
                key == "." -> if (!cur.contains('.') && cur.length < 12) {
                    cur = if (cur.isEmpty()) "0." else "$cur."
                    shown = cur
                }
                key.length == 1 && key[0].isDigit() -> if (cur.length < 12) {
                    cur += key
                    shown = cur
                }
                key == "=" -> {
                    val pendingOp = op
                    if (pendingOp == null && cur.length in 4..8 && cur.all { it.isDigit() } && Prefs.verifyPin(ctx, cur)) {
                        Prefs.putFails(ctx, 0)
                        AppState.locked = false
                    } else if (pendingOp == null && cur.length in 4..8 && cur.all { it.isDigit() } && Prefs.duressSet(ctx) && Prefs.verifyDuress(ctx, cur)) {
                        Prefs.wipeVaultOnly(ctx)
                        Prefs.putFails(ctx, 0)
                        AppState.locked = false
                    } else {
                        val v = cur.toDoubleOrNull()
                        val a = acc
                        if (pendingOp != null && a != null && v != null) {
                            val r = applyOp(a, v, pendingOp)
                            shown = fmt(r)
                            acc = if (r.isNaN()) null else r
                            cur = ""
                            op = null
                        }
                    }
                }
                else -> {
                    val v = cur.toDoubleOrNull()
                    val a = acc
                    val pendingOp = op
                    if (v != null) {
                        val r = if (a != null && pendingOp != null) applyOp(a, v, pendingOp) else v
                        acc = if (r.isNaN()) null else r
                        shown = fmt(r)
                    } else if (a == null) {
                        acc = 0.0
                    }
                    op = key
                    cur = ""
                }
            }
            Unit
        }
    }
}
