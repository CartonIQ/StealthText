package com.cartoniq.stealthtext

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RevealScreen(initialUri: Uri?, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var cover by remember { mutableStateOf<Cover?>(null) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var item by remember { mutableStateOf<Payload.Item?>(null) }
    var playing by remember { mutableStateOf(false) }
    var burnNote by remember { mutableStateOf(false) }
    var keyName by remember { mutableStateOf("") }
    var keyBytes by remember { mutableStateOf<ByteArray?>(null) }
    val player = remember { AudioPlayer() }
    DisposableEffect(Unit) { onDispose { player.stop() } }
    LaunchedEffect(initialUri) {
        if (initialUri != null) cover = loadCover(ctx, initialUri)
    }
    val pick = rememberImagePicker { u ->
        scope.launch {
            cover = loadCover(ctx, u)
            item = null
            status = ""
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        ScreenHeader(tr("Reveal"), onBack)
        PreviewBox(cover?.preview, busy, tr("Tap to choose the PNG that holds a secret")) { pick() }
        Spacer(Modifier.height(12.dp))
        PasswordField(tr("Password"), password) { password = it }
        KeyFilePicker(keyName) { n, b ->
            keyName = n
            keyBytes = b
        }
        Spacer(Modifier.height(16.dp))
        GradientButton(if (busy) tr("Scanning...") else tr("Reveal"), cover != null && password.isNotEmpty() && !busy) {
            val c = cover
            val pw = Crypto.mixKey(password, keyBytes)
            if (c != null) {
                scope.launch {
                    busy = true
                    status = ""
                    item = null
                    burnNote = false
                    val res = withContext(Dispatchers.Default) {
                        runCatching {
                            val bmp = ImageIO.loadBitmap(ctx, c.uri, false) ?: throw StegoException("Could not open this image.")
                            try {
                                val got = Payload.unpack(Stego.decode(bmp, pw))
                                if (got.burn) {
                                    if (Prefs.isBurned(ctx, got.id)) {
                                        throw StegoException(tr("This message was set to burn after reading and was already opened."))
                                    }
                                    Prefs.markBurned(ctx, got.id)
                                }
                                got
                            } finally {
                                bmp.recycle()
                            }
                        }
                    }
                    busy = false
                    res.onSuccess {
                        item = it
                        burnNote = it.burn
                        val fp = c.uri.path
                        if (it.burn && c.uri.scheme == "file" && fp != null) File(fp).delete()
                    }.onFailure { status = it.message ?: tr("Something went wrong.") }
                }
            }
        }
        if (status.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(status, color = Warn, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        if (burnNote) {
            Spacer(Modifier.height(10.dp))
            Text(tr("Burn after reading: this was the only time it can be opened."), color = Warn, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        val shown = item
        if (shown != null) {
            Spacer(Modifier.height(16.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    when (shown.type) {
                        Payload.TEXT, Payload.MARK -> {
                            val text = String(shown.data, Charsets.UTF_8)
                            Text(if (shown.type == Payload.MARK) tr("Hidden watermark") else tr("Secret message"), color = Sub, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text(text, color = Ink, fontSize = 17.sp, lineHeight = 25.sp)
                            Spacer(Modifier.height(12.dp))
                            GradientButton(tr("Copy (clipboard clears in 30 s)")) { copySecret(ctx, text) }
                        }
                        Payload.FILE -> {
                            Text(tr("Hidden file"), color = Sub, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text("${shown.name}  (${shown.data.size / 1024} KB)", color = Ink, fontSize = 16.sp)
                            Spacer(Modifier.height(12.dp))
                            GradientButton(tr("Save to Downloads/StealthText")) {
                                val lower = shown.name.lowercase()
                                val mime = if (lower.endsWith(".pdf")) "application/pdf" else if (lower.endsWith(".txt")) "text/plain" else "application/octet-stream"
                                val ok = ImageIO.saveToDownloads(ctx, shown.name.ifEmpty { "hidden_file" }, mime, shown.data)
                                status = if (ok) "Saved to Downloads/StealthText." else "Could not save the file."
                            }
                        }
                        Payload.AUDIO -> {
                            Text(tr("Hidden voice note"), color = Sub, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text("${shown.data.size / 1024} KB", color = Ink, fontSize = 16.sp)
                            Spacer(Modifier.height(12.dp))
                            GradientButton(if (playing) tr("Stop") else tr("Play voice note")) {
                                if (playing) {
                                    player.stop()
                                    playing = false
                                } else {
                                    val f = File(ctx.cacheDir, "play_voice.m4a")
                                    f.writeBytes(shown.data)
                                    playing = player.play(f.absolutePath) { playing = false }
                                }
                            }
                        }
                        else -> Text("Unknown hidden data.", color = Ink, fontSize = 16.sp)
                    }
                    TextButton(onClick = {
                        player.stop()
                        playing = false
                        item = null
                    }) { Text(tr("Hide result"), color = Sub, fontSize = 15.sp) }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun SplitScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var join by remember { mutableStateOf(false) }
    var coverA by remember { mutableStateOf<Cover?>(null) }
    var coverB by remember { mutableStateOf<Cover?>(null) }
    var secret by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var bits by remember { mutableFloatStateOf(1f) }
    var vaultOnly by remember { mutableStateOf(false) }
    var keyName by remember { mutableStateOf("") }
    var keyBytes by remember { mutableStateOf<ByteArray?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var statusOk by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val pickA = rememberImagePicker { u -> scope.launch { coverA = loadCover(ctx, u); status = "" } }
    val pickB = rememberImagePicker { u -> scope.launch { coverB = loadCover(ctx, u); status = "" } }

    val k = bits.roundToInt()
    val a = coverA
    val b = coverB
    val cap = if (a != null && b != null) minOf(Stego.capacity(a.w, a.h, k, false), Stego.capacity(b.w, b.h, k, false)) else 0
    val size = secret.toByteArray(Charsets.UTF_8).size
    val tooBig = !join && a != null && b != null && size + 40 > cap
    val ready = a != null && b != null && password.length >= Stego.MIN_PASSWORD && !busy &&
        (join || (size > 0 && !tooBig))

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        ScreenHeader(tr("Split Secret"), onBack)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { join = false; result = null; status = "" }) {
                Text(tr("Split"), color = if (!join) Ink else Sub, fontWeight = if (!join) FontWeight.ExtraBold else FontWeight.Normal)
            }
            TextButton(onClick = { join = true; result = null; status = "" }) {
                Text(tr("Join"), color = if (join) Ink else Sub, fontWeight = if (join) FontWeight.ExtraBold else FontWeight.Normal)
            }
        }
        Text(
            if (join) "Pick both images and enter the password to reveal the secret."
            else "The secret is split into two images. You need both to read it.",
            color = Sub, fontSize = 14.sp
        )
        Spacer(Modifier.height(12.dp))
        PreviewBox(coverA?.preview, busy, "Image 1: tap to choose") { pickA() }
        Spacer(Modifier.height(10.dp))
        PreviewBox(coverB?.preview, busy, "Image 2: tap to choose") { pickB() }
        Spacer(Modifier.height(12.dp))
        if (!join) {
            OutlinedTextField(
                value = secret,
                onValueChange = { secret = it },
                label = { Text(tr("Secret message")) },
                minLines = 3,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
        }
        PasswordField(tr("Password (6+ characters)"), password) { password = it }
        KeyFilePicker(keyName) { n, b ->
            keyName = n
            keyBytes = b
        }
        if (!join) {
            Spacer(Modifier.height(12.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Bits per colour: $k", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Slider(value = bits, onValueChange = { bits = it }, valueRange = 1f..3f, steps = 1)
                    Text(
                        if (a == null || b == null) "Choose both photos to see capacity."
                        else if (tooBig) "Too big for these photos (limit ${cap / 1024} KB)."
                        else "Fits. Limit ${cap / 1024} KB.",
                        color = if (tooBig) Warn else Sub, fontSize = 12.sp
                    )
                }
            }
            SwitchRow(tr("Private vault only"), "Keep both images out of the gallery.", vaultOnly) { vaultOnly = it }
        }
        Spacer(Modifier.height(12.dp))
        GradientButton(if (busy) tr("Working...") else if (join) tr("Join and reveal") else tr("Split into two images"), ready) {
            val ca = coverA
            val cb = coverB
            val pw = Crypto.mixKey(password, keyBytes)
            val depth = k
            val text = secret
            val vOnly = vaultOnly
            if (ca != null && cb != null) {
                scope.launch {
                    busy = true
                    status = ""
                    result = null
                    if (join) {
                        val res = withContext(Dispatchers.Default) {
                            runCatching {
                                val b1 = ImageIO.loadBitmap(ctx, ca.uri, false) ?: throw StegoException("Could not open image 1.")
                                val p1 = try { Stego.decode(b1, pw) } finally { b1.recycle() }
                                val b2 = ImageIO.loadBitmap(ctx, cb.uri, false) ?: throw StegoException("Could not open image 2.")
                                val p2 = try { Stego.decode(b2, pw) } finally { b2.recycle() }
                                val item = Payload.unpack(Payload.xor(p1, p2))
                                String(item.data, Charsets.UTF_8)
                            }
                        }
                        busy = false
                        res.onSuccess { result = it }.onFailure {
                            statusOk = false
                            status = it.message ?: tr("Something went wrong.")
                        }
                    } else {
                        val res = withContext(Dispatchers.Default) {
                            runCatching {
                                val payload = Payload.pack(Payload.TEXT, "", text.toByteArray(Charsets.UTF_8))
                                val shareA = Stego.randomBytes(payload.size)
                                val shareB = Payload.xor(payload, shareA)
                                val saved = ArrayList<Uri>()
                                val pairs = listOf(Pair(ca, shareA), Pair(cb, shareB))
                                for ((i, pr) in pairs.withIndex()) {
                                    val bmp = ImageIO.loadBitmap(ctx, pr.first.uri, true) ?: throw StegoException("Could not open image ${i + 1}.")
                                    val out = try { Stego.encode(bmp, pr.second, pw, null, null, depth) } finally { bmp.recycle() }
                                    val u = try {
                                        (if (vOnly) ImageIO.saveToVault(ctx, out, "_part${i + 1}") else ImageIO.savePng(ctx, out, "_part${i + 1}"))
                                            ?: throw StegoException("Could not save image ${i + 1}.")
                                    } finally {
                                        out.recycle()
                                    }
                                    saved.add(u)
                                }
                                saved.size
                            }
                        }
                        busy = false
                        res.onSuccess {
                            statusOk = true
                            status = if (vOnly) "Both parts saved in your private vault." else "Both parts saved to Pictures/StealthText."
                        }.onFailure {
                            statusOk = false
                            status = it.message ?: tr("Something went wrong.")
                        }
                    }
                }
            }
        }
        if (status.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(status, color = if (statusOk) Good else Warn, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        val shownText = result
        if (shownText != null) {
            Spacer(Modifier.height(16.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text(tr("Secret message"), color = Sub, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(shownText, color = Ink, fontSize = 17.sp, lineHeight = 25.sp)
                    TextButton(onClick = { result = null }) { Text(tr("Hide result"), color = Sub, fontSize = 15.sp) }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
