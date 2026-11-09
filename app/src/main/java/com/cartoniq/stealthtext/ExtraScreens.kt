package com.cartoniq.stealthtext

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun KeyFilePicker(keyName: String, onPicked: (String, ByteArray?) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) {
            scope.launch {
                val r = withContext(Dispatchers.IO) { ImageIO.readFile(ctx, u) }
                if (r != null) onPicked(r.first, r.second)
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(tr("Key file (optional)"), color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(if (keyName.isEmpty()) tr("Password plus a file you choose") else keyName, color = Sub, fontSize = 12.sp)
        }
        TextButton(onClick = { pick.launch(arrayOf("*/*")) }) {
            Text(if (keyName.isEmpty()) tr("Choose") else tr("Change"), color = Ink, fontWeight = FontWeight.Bold)
        }
        if (keyName.isNotEmpty()) {
            TextButton(onClick = { onPicked("", null) }) { Text(tr("Remove"), color = Warn, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun InvisibleTextScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var reveal by remember { mutableStateOf(false) }
    var cover by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var pasted by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var keyName by remember { mutableStateOf("") }
    var keyBytes by remember { mutableStateOf<ByteArray?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var output by remember { mutableStateOf<String?>(null) }

    val ready = password.length >= Stego.MIN_PASSWORD && !busy &&
        (if (reveal) pasted.isNotEmpty() else cover.isNotEmpty() && secret.isNotEmpty())

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        ScreenHeader(tr("Invisible Text"), onBack)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { reveal = false; output = null; status = "" }) {
                Text(tr("Hide"), color = if (!reveal) Ink else Sub, fontWeight = if (!reveal) FontWeight.ExtraBold else FontWeight.Normal)
            }
            TextButton(onClick = { reveal = true; output = null; status = "" }) {
                Text(tr("Reveal"), color = if (reveal) Ink else Sub, fontWeight = if (reveal) FontWeight.ExtraBold else FontWeight.Normal)
            }
        }
        Text(
            if (reveal) "Paste the text you received."
            else "Your secret is locked and mixed into normal-looking text with invisible characters. Paste it in any chat. Some apps remove invisible characters, so test first.",
            color = Sub, fontSize = 14.sp
        )
        Spacer(Modifier.height(12.dp))
        if (reveal) {
            OutlinedTextField(
                value = pasted,
                onValueChange = { pasted = it },
                label = { Text(tr("Pasted text")) },
                minLines = 4,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            OutlinedTextField(
                value = cover,
                onValueChange = { cover = it },
                label = { Text(tr("Normal-looking text")) },
                minLines = 2,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = secret,
                onValueChange = { secret = it },
                label = { Text(tr("Secret message")) },
                minLines = 3,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(12.dp))
        PasswordField(tr("Password (6+ characters)"), password) { password = it }
        KeyFilePicker(keyName) { n, b ->
            keyName = n
            keyBytes = b
        }
        Spacer(Modifier.height(12.dp))
        GradientButton(if (busy) tr("Working...") else if (reveal) tr("Reveal text") else tr("Hide in text"), ready) {
            val pw = Crypto.mixKey(password, keyBytes)
            val cv = cover
            val sc = secret
            val pt = pasted
            val rv = reveal
            scope.launch {
                busy = true
                status = ""
                output = null
                val res = withContext(Dispatchers.Default) {
                    runCatching { if (rv) ZeroWidth.reveal(pt, pw) else ZeroWidth.hide(cv, sc, pw) }
                }
                busy = false
                res.onSuccess { output = it }.onFailure { status = it.message ?: tr("Something went wrong.") }
            }
        }
        if (status.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(status, color = Warn, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        val out = output
        if (out != null) {
            Spacer(Modifier.height(16.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text(if (reveal) tr("Secret message") else "Your text (looks normal)", color = Sub, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(out, color = Ink, fontSize = 17.sp, lineHeight = 25.sp)
                    Spacer(Modifier.height(12.dp))
                    GradientButton(tr("Copy (clipboard clears in 30 s)")) { copySecret(ctx, out) }
                    if (!reveal) {
                        TextButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, out)
                            }
                            ctx.startActivity(Intent.createChooser(send, tr("Share text")))
                        }) { Text(tr("Share text"), color = Ink, fontWeight = FontWeight.Bold) }
                    }
                    TextButton(onClick = { output = null }) { Text(tr("Hide result"), color = Sub, fontSize = 15.sp) }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun ShamirScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var join by remember { mutableStateOf(false) }
    val covers = remember { mutableStateListOf<Cover>() }
    var secret by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var keyName by remember { mutableStateOf("") }
    var keyBytes by remember { mutableStateOf<ByteArray?>(null) }
    var kf by remember { mutableFloatStateOf(2f) }
    var bits by remember { mutableFloatStateOf(1f) }
    var vaultOnly by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var statusOk by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val pick = rememberImagePicker { u ->
        scope.launch {
            val c = loadCover(ctx, u)
            if (covers.size < 5) covers.add(c)
            status = ""
        }
    }

    val n = covers.size
    val depth = bits.roundToInt()
    val threshold = kf.roundToInt().coerceIn(2, maxOf(2, n))
    val size = secret.toByteArray(Charsets.UTF_8).size
    var minCap = Int.MAX_VALUE
    for (c in covers) minCap = minOf(minCap, Stego.capacity(c.w, c.h, depth, false))
    val tooBig = !join && n >= 3 && size + 50 > minCap
    val ready = password.length >= Stego.MIN_PASSWORD && !busy &&
        (if (join) n >= 2 else n >= 3 && size > 0 && !tooBig)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        ScreenHeader(tr("Shamir Split"), onBack)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { join = false; result = null; status = "" }) {
                Text(tr("Split"), color = if (!join) Ink else Sub, fontWeight = if (!join) FontWeight.ExtraBold else FontWeight.Normal)
            }
            TextButton(onClick = { join = true; result = null; status = "" }) {
                Text(tr("Join"), color = if (join) Ink else Sub, fontWeight = if (join) FontWeight.ExtraBold else FontWeight.Normal)
            }
        }
        Text(
            if (join) "Add the images you have (any order), then enter the password."
            else "Add 3 to 5 photos. Any chosen number of them can rebuild the secret, fewer cannot.",
            color = Sub, fontSize = 14.sp
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            for (c in covers) {
                val p = c.preview
                if (p != null) {
                    Image(bitmap = p, contentDescription = "Chosen photo", contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)))
                }
            }
        }
        Row {
            TextButton(onClick = { pick() }, enabled = n < 5) { Text("Add image ($n/5)", color = Ink, fontWeight = FontWeight.Bold) }
            if (n > 0) TextButton(onClick = { covers.clear(); result = null }) { Text(tr("Clear"), color = Warn, fontWeight = FontWeight.Bold) }
        }
        if (!join) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = secret,
                onValueChange = { secret = it },
                label = { Text(tr("Secret message")) },
                minLines = 3,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            )
            if (n >= 3) {
                Spacer(Modifier.height(8.dp))
                Text("Images needed to open it: $threshold of $n", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Slider(value = kf.coerceIn(2f, n.toFloat()), onValueChange = { kf = it }, valueRange = 2f..n.toFloat(), steps = n - 3)
            }
        }
        Spacer(Modifier.height(8.dp))
        PasswordField(tr("Password (6+ characters)"), password) { password = it }
        KeyFilePicker(keyName) { nm, b ->
            keyName = nm
            keyBytes = b
        }
        if (!join) {
            Text("Bits per colour: $depth", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Slider(value = bits, onValueChange = { bits = it }, valueRange = 1f..3f, steps = 1)
            if (tooBig) Text("Too big for these photos (limit ${minCap / 1024} KB).", color = Warn, fontSize = 12.sp)
            SwitchRow(tr("Private vault only"), "Keep all images out of the gallery.", vaultOnly) { vaultOnly = it }
        }
        Spacer(Modifier.height(12.dp))
        GradientButton(if (busy) tr("Working...") else if (join) tr("Join and reveal") else "Split into $n images", ready) {
            val list = covers.toList()
            val pw = Crypto.mixKey(password, keyBytes)
            val text = secret
            val vOnly = vaultOnly
            val th = threshold
            val d = depth
            scope.launch {
                busy = true
                status = ""
                result = null
                if (join) {
                    val res = withContext(Dispatchers.Default) {
                        runCatching {
                            val shares = ArrayList<ByteArray>()
                            for ((i, c) in list.withIndex()) {
                                val bmp = ImageIO.loadBitmap(ctx, c.uri, false) ?: throw StegoException("Could not open image ${i + 1}.")
                                shares.add(try { Stego.decode(bmp, pw) } finally { bmp.recycle() })
                            }
                            String(Payload.unpack(Shamir.combine(shares)).data, Charsets.UTF_8)
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
                            val shares = Shamir.split(payload, th, list.size)
                            for ((i, c) in list.withIndex()) {
                                val bmp = ImageIO.loadBitmap(ctx, c.uri, true) ?: throw StegoException("Could not open image ${i + 1}.")
                                val out = try { Stego.encode(bmp, shares[i], pw, null, null, d) } finally { bmp.recycle() }
                                try {
                                    (if (vOnly) ImageIO.saveToVault(ctx, out, "_s${i + 1}of${list.size}") else ImageIO.savePng(ctx, out, "_s${i + 1}of${list.size}"))
                                        ?: throw StegoException("Could not save image ${i + 1}.")
                                } finally {
                                    out.recycle()
                                }
                            }
                            list.size
                        }
                    }
                    busy = false
                    res.onSuccess {
                        statusOk = true
                        status = if (vOnly) "All parts saved in your private vault." else "All parts saved to Pictures/StealthText."
                    }.onFailure {
                        statusOk = false
                        status = it.message ?: tr("Something went wrong.")
                    }
                }
            }
        }
        if (status.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(status, color = if (statusOk) Good else Warn, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        val shown = result
        if (shown != null) {
            Spacer(Modifier.height(16.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text(tr("Secret message"), color = Sub, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(shown, color = Ink, fontSize = 17.sp, lineHeight = 25.sp)
                    TextButton(onClick = { result = null }) { Text(tr("Hide result"), color = Sub, fontSize = 15.sp) }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun ScanScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var cover by remember { mutableStateOf<Cover?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<Analyzer.Scan?>(null) }
    val pick = rememberImagePicker { u ->
        scope.launch {
            cover = loadCover(ctx, u)
            result = null
            status = ""
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        ScreenHeader(tr("Scan Image"), onBack)
        PreviewBox(cover?.preview, busy, tr("Tap to choose a photo to check")) { pick() }
        Spacer(Modifier.height(12.dp))
        GradientButton(if (busy) tr("Scanning...") else tr("Scan"), cover != null && !busy) {
            val c = cover
            if (c != null) {
                scope.launch {
                    busy = true
                    status = ""
                    result = null
                    val res = withContext(Dispatchers.Default) {
                        runCatching {
                            val bmp = ImageIO.loadBitmap(ctx, c.uri, false) ?: throw StegoException("Could not open this image.")
                            try {
                                Analyzer.scan(bmp)
                            } finally {
                                bmp.recycle()
                            }
                        }
                    }
                    busy = false
                    res.onSuccess { result = it }.onFailure { status = it.message ?: tr("Something went wrong.") }
                }
            }
        }
        if (status.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(status, color = Warn, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        val r = result
        if (r != null) {
            Spacer(Modifier.height(16.dp))
            val verdict = if (r.stealth) tr("StealthText data found") else if (r.p >= 0.8) tr("Suspicious") else if (r.p >= 0.3) tr("Unclear") else tr("Looks normal")
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text(verdict, color = if (r.stealth || r.p >= 0.8) Warn else Good, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(8.dp))
                    Text("LSB pattern: " + (r.p * 100).roundToInt() + "%", color = Ink, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Estimate only. It reliably spots StealthText images, but other hiding tools or small messages can be missed.",
                        color = Sub, fontSize = 12.sp, lineHeight = 18.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
