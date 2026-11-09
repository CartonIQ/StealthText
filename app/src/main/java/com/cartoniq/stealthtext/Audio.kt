package com.cartoniq.stealthtext

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class VoiceRecorder(private val ctx: Context) {
    private var rec: MediaRecorder? = null
    private var out: File? = null

    @Suppress("DEPRECATION")
    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()

    fun start(): Boolean {
        release()
        var r: MediaRecorder? = null
        return try {
            val f = File(ctx.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
            val m = newRecorder()
            r = m
            m.setAudioSource(MediaRecorder.AudioSource.MIC)
            m.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            m.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            m.setAudioChannels(1)
            m.setAudioSamplingRate(16000)
            m.setAudioEncodingBitRate(24000)
            m.setOutputFile(f.absolutePath)
            m.prepare()
            m.start()
            rec = m
            out = f
            true
        } catch (e: Exception) {
            try {
                r?.release()
            } catch (e2: Exception) {
            }
            false
        }
    }

    fun stop(): File? {
        val r = rec ?: return null
        val f = out
        rec = null
        out = null
        return try {
            r.stop()
            f
        } catch (e: RuntimeException) {
            f?.delete()
            null
        } finally {
            r.release()
        }
    }

    fun release() {
        val r = rec
        if (r != null) {
            try {
                r.stop()
            } catch (e: RuntimeException) {
            }
            r.release()
        }
        rec = null
        out?.delete()
        out = null
    }
}

class AudioPlayer {
    private var mp: MediaPlayer? = null

    fun play(path: String, onDone: () -> Unit): Boolean {
        stop()
        val p = MediaPlayer()
        return try {
            p.setDataSource(path)
            p.setOnCompletionListener {
                stop()
                onDone()
            }
            p.prepare()
            p.start()
            mp = p
            true
        } catch (e: Exception) {
            p.release()
            false
        }
    }

    fun stop() {
        val p = mp
        mp = null
        if (p != null) {
            try {
                p.stop()
            } catch (e: IllegalStateException) {
            }
            p.release()
        }
    }
}
