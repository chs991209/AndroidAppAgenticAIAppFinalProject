package com.example.agenticaiapp.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import java.io.File

/**
 * Records voice prompts to an m4a (AAC) file and exposes a base64-encoded payload
 * suitable for sending to the backend.
 */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    val isRecording: Boolean
        get() = recorder != null

    fun start() {
        if (recorder != null) return

        val file = File(context.cacheDir, "voice_prompt_${System.currentTimeMillis()}.m4a")
        outputFile = file

        val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        newRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44_100)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = newRecorder
    }

    /**
     * Stops the recording and returns the captured audio as a base64-encoded string,
     * or null if nothing was recorded.
     */
    fun stop(): String? {
        val current = recorder ?: return null
        return try {
            current.stop()
            current.release()
            recorder = null
            val file = outputFile ?: return null
            val bytes = file.readBytes()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (t: Throwable) {
            current.runCatching { release() }
            recorder = null
            null
        } finally {
            outputFile?.delete()
            outputFile = null
        }
    }

    fun cancel() {
        recorder?.runCatching {
            stop()
            release()
        }
        recorder = null
        outputFile?.delete()
        outputFile = null
    }
}
