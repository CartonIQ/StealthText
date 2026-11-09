package com.cartoniq.stealthtext

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class HideMode { Text, File, Voice, Mark }

@Composable
fun HideScreen(mode: HideMode, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var cover by remember { mutableStateOf<Cover?>(null) }
    var message by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var useDecoy by remember { mutableStateOf(false) }
    var smart by remember { mutableStateOf(false) }
    var burn by remember { mutableStateOf(false) }
    var generating by remember { mutableStateOf(false) }
    var detect by remember { mutableStateOf<HideOut?>(null) }
    var keyName by remember { mutableStateOf("") }
    var keyBytes by remember { mutableStateOf<ByteArray?>(null) }
    var decoyMsg by remember { mutableStateOf("") }
    var decoyPw by remember { mutableStateOf("") }
    var bits by remember { mutableFloatStateOf(1f) }
    var vaultOnly by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var statusOk by remember { mutableStateOf(false) }
    var savedUri by remember { mutableStateOf<Uri?>(null) }
    var savedInVault by remember { mutableStateOf(false) }
    var fileName by remember { mutableStateOf("") }
    var fileBytes by remember { mutableStateOf<ByteArray?>(null) }
    var voice by remember { mutableStateOf<File?>(null) }
    var voiceSize by remember { mutableIntStateOf(0) }
    var recording by remember { mutableStateOf(false) }
    var elapsed by remember { mutableIntStateOf(0) }
    var recStart by remember { mutableStateOf(0L) }
    var playing by remember { mutableStateOf(false) }

    val recorder = remember { VoiceRecorder(ctx) }
    val player = remember { AudioPlayer() }
    DisposableEffect(Unit) {
        onDispose {
            recorder.release()
            player.stop()
        }
    }

    val k = bits.roundToInt()
    val dual = useDecoy && mode == HideMode.Text
    val rawCap = cover?.let { Stego.capacity(it.w, it.h, k, dual) } ?: 0
    val cap = if (smart) rawCap * 4 / 10 else rawCap
    val maxSec = if (cap > 0) minOf(cap / 3200, 120) else 60
    val sourceSize = when (mode) {
        HideMode.Text, HideMode.Mark -> message.toByteArray(Charsets.UTF_8).size
        HideMode.File -> fileBytes?.size ?: 0
        HideMode.Voice -> voiceSize
    }
    val tooBig = cover != null && sourceSize + 40 > cap
    val fraction = if (cap > 0) ((sourceSize + 40).toFloat() / cap).coerceIn(0f, 1f) else 0f
    val decoyOk = !dual || (decoyMsg.isNotEmpty() && decoyPw.length >= Stego.MIN_PASSWORD && decoyPw != password)
    val canEncode = cover != null && sourceSize > 0 && password.length >= Stego.MIN_PASSWORD &&
        !tooBig && decoyOk && !busy && !recording

    val pickCover = rememberImagePicker { u ->
        scope.launch {
            cover = loadCover(ctx, u)
            savedUri = null
            status = ""
        }
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) {
            scope.launch {
                val r = withContext(Dispatchers.IO) { ImageIO.readFile(ctx, u) }
                if (r == null) {
                    statusOk = false
                    status = "Could not read this file (max 20 MB)."
                } else {
                    fileName = r.first
                    fileBytes = r.second
                    status = ""
                }
            }
        }
    }

    val startRecording: () -> Unit = {
        if (recorder.start()) {
            voice = null
            voiceSize = 0
            recStart = SystemClock.elapsedRealtime()
            elapsed = 0
            recording = true
            status = ""
        } else {
            statusOk = false
            status = "Could not start recording."
        }
    }
    val stopRecording: () -> Unit = {
        recording = false
        val f = recorder.stop()
        voice = f
        voiceSize = f?.length()?.toInt() ?: 0
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            startRecording()
        } else {
            statusOk = false
            status = tr("Microphone permission is needed to record.")
        }
    }
    LaunchedEffect(recording) {
        while (recording) {
            elapsed = ((SystemClock.elapsedRealtime() - recStart) / 1000L).toInt()
            if (elapsed >= maxSec) {
                stopRecording()
            }
            delay(250)
        }
    }

    val title = when (mode) {
        HideMode.Text -> tr("Encode Text")
        HideMode.File -> tr("Hide a File")
        HideMode.Voice -> tr("Voice Studio")
        HideMode.Mark -> tr("Stealth Watermark")
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        ScreenHeader(title, onBack)
        PreviewBox(cover?.preview, busy, tr("Tap to choose a cover photo\nBusy, textured photos hide data best")) { pickCover() }
        TextButton(
            onClick = {
                val need = sourceSize + 120
                val kk = k
                val sm = smart
                val du = dual
                scope.launch {
                    generating = true
                    val made = withContext(Dispatchers.Default) {
                        var size = 900
                        for (s in intArrayOf(900, 1200, 1600, 2000, 2600)) {
                            size = s
                            val raw = Stego.capacity(s, s, kk, du)
                            val cp = if (sm) raw * 4 / 10 else raw
                            if (cp >= need) break
                        }
                        val bmp = CoverGen.make(size, System.nanoTime().toInt())
                        val u = ImageIO.saveToCache(ctx, bmp)
                        bmp.recycle()
                        u
                    }
                    if (made != null) {
                        cover = loadCover(ctx, made)
                        savedUri = null
                        status = ""
                    } else {
                        statusOk = false
                        status = "Could not create the cover photo."
                    }
                    generating = false
                }
            },
            enabled = !busy && !generating && !recording
        ) { Text(if (generating) tr("Generating...") else tr("Generate a cover photo"), color = Ink, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(8.dp))

        when (mode) {
            HideMode.Text, HideMode.Mark -> OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text(if (mode == HideMode.Text) tr("Secret message") else "Copyright tag, e.g. (c) Your Name 2026") },
                minLines = if (mode == HideMode.Text) 4 else 2,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            )
            HideMode.File -> GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        if (fileBytes == null) "No file chosen (PDF or TXT, max 20 MB)" else "$fileName  (${sourceSize / 1024} KB)",
                        color = Ink, fontSize = 14.sp
                    )
                    TextButton(onClick = { pickFile.launch(arrayOf("application/pdf", "text/plain")) }) {
                        Text(tr("Choose file"), color = Ink, fontWeight = FontWeight.Bold)
                    }
                }
            }
            HideMode.Voice -> GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    val shownSec = elapsed
                    Text(
                        "%02d:%02d  (max %d s for this photo)".format(shownSec / 60, shownSec % 60, maxSec),
                        color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    GradientButton(if (recording) tr("Stop recording") else tr("Record voice note"), !busy) {
                        if (recording) {
                            stopRecording()
                        } else if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            startRecording()
                        } else {
                            micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                    val vf = voice
                    if (vf != null && !recording) {
                        Spacer(Modifier.height(8.dp))
                        Text("Recorded: ${voiceSize / 1024} KB", color = Sub, fontSize = 13.sp)
                        TextButton(onClick = {
                            if (playing) {
                                player.stop()
                                playing = false
                            } else {
                                playing = player.play(vf.absolutePath) { playing = false }
                            }
                        }) { Text(if (playing) tr("Stop playback") else tr("Play"), color = Ink, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        PasswordField(tr("Password (6+ characters)"), password) { password = it }
        KeyFilePicker(keyName) { n, b ->
            keyName = n
            keyBytes = b
        }

        if (mode == HideMode.Text) {
            Spacer(Modifier.height(8.dp))
            SwitchRow(tr("Decoy password"), tr("A second password reveals a fake message instead"), useDecoy) { useDecoy = it }
            if (useDecoy) {
                OutlinedTextField(
                    value = decoyMsg,
                    onValueChange = { decoyMsg = it },
                    label = { Text(tr("Fake message")) },
                    minLines = 2,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                PasswordField(tr("Decoy password (different)"), decoyPw) { decoyPw = it }
            }
        }

        Spacer(Modifier.height(12.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(tr("Capacity"), color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(
                        when (k) {
                            1 -> tr("1 bit: invisible")
                            2 -> tr("2 bits: balanced")
                            else -> tr("3 bits: high capacity")
                        },
                        color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold
                    )
                }
                Slider(value = bits, onValueChange = { bits = it }, valueRange = 1f..3f, steps = 1)
                CapacityMeter(fraction, tooBig)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (cover == null) tr("Choose a photo to see how much fits.")
                    else if (tooBig) "Too big: ${sourceSize / 1024} KB, this photo holds ${cap / 1024} KB at this setting."
                    else "${sourceSize / 1024} KB used of ${cap / 1024} KB. Saves as PNG.",
                    color = if (tooBig) Warn else Sub, fontSize = 12.sp
                )
            }
        }
        SwitchRow(tr("Smart hiding"), tr("Writes only in busy areas. Harder to detect. Photos up to 8 MP."), smart) { smart = it }
        SwitchRow(tr("Burn after reading"), tr("Can be opened once in this app. Not a guarantee."), burn) { burn = it }
        SwitchRow(tr("Private vault only"), "Keep it out of the gallery. Open it from the Vault tab.", vaultOnly) { vaultOnly = it }

        Spacer(Modifier.height(12.dp))
        GradientButton(if (busy) tr("Encrypting...") else tr("Hide and save PNG"), canEncode) {
            val c = cover
            val msg = message
            val pw = Crypto.mixKey(password, keyBytes)
            val smartOn = smart
            val burnOn = burn
            val depth = k
            val vOnly = vaultOnly
            val fName = fileName
            val fBytes = fileBytes
            val vFile = voice
            val dMsg = decoyMsg
            val dPw = Crypto.mixKey(decoyPw, keyBytes)
            val useD = dual
            if (c != null) {
                scope.launch {
                    busy = true
                    status = ""
                    savedUri = null
                    detect = null
                    val res = withContext(Dispatchers.Default) {
                        runCatching {
                            val payload = when (mode) {
                                HideMode.Text -> Payload.pack(Payload.TEXT, "", msg.toByteArray(Charsets.UTF_8), burnOn)
                                HideMode.Mark -> Payload.pack(Payload.MARK, "", msg.toByteArray(Charsets.UTF_8), burnOn)
                                HideMode.File -> Payload.pack(Payload.FILE, fName, fBytes ?: ByteArray(0), burnOn)
                                HideMode.Voice -> Payload.pack(Payload.AUDIO, "voice.m4a", vFile?.readBytes() ?: ByteArray(0), burnOn)
                            }
                            val decoyPayload = if (useD) Payload.pack(Payload.TEXT, "", dMsg.toByteArray(Charsets.UTF_8)) else null
                            val bmp = ImageIO.loadBitmap(ctx, c.uri, true) ?: throw StegoException("Could not open this photo.")
                            val out = try {
                                Stego.encode(bmp, payload, pw, decoyPayload, if (useD) dPw else null, depth, smartOn)
                            } finally {
                                bmp.recycle()
                            }
                            val bw = out.width
                            val bh = out.height
                            val savedAt = try {
                                (if (vOnly) ImageIO.saveToVault(ctx, out) else ImageIO.savePng(ctx, out))
                                    ?: throw StegoException("Could not save the image.")
                            } finally {
                                out.recycle()
                            }
                            val dt = Analyzer.detectability(payload.size, bw, bh, depth, smartOn)
                            HideOut(savedAt, dt.first, dt.second)
                        }
                    }
                    busy = false
                    res.onSuccess {
                        savedUri = it.uri
                        detect = it
                        savedInVault = vOnly
                        statusOk = true
                        status = if (vOnly) tr("Saved in your private vault.") else tr("Saved to Pictures/StealthText as PNG.")
                        if (mode == HideMode.Voice) {
                            vFile?.delete()
                            voice = null
                            voiceSize = 0
                        }
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
        val dt = detect
        if (dt != null) {
            val levelName = when (dt.level) {
                0 -> tr("Low")
                1 -> tr("Medium")
                else -> tr("High")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                tr("Detectability") + ": " + levelName + "  (" + "%.1f".format(dt.pct) + "%)",
                color = if (dt.level == 2) Warn else Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
            )
            if (dt.level == 2) Text(tr("Tip: use a bigger photo, fewer bits or Smart hiding."), color = Sub, fontSize = 12.sp)
        }
        val shareUri = savedUri
        if (shareUri != null && !savedInVault) {
            Spacer(Modifier.height(12.dp))
            GradientButton(tr("Share the PNG")) {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                ctx.startActivity(Intent.createChooser(send, "Send the PNG"))
            }
            Spacer(Modifier.height(8.dp))
            Text("In WhatsApp or Telegram, pick Document or File so the image is not compressed.", color = Sub, fontSize = 12.sp)
        }
        Spacer(Modifier.height(24.dp))
    }
}
