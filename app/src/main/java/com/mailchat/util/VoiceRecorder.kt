package com.mailchat.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object VoiceRecorder {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    var isRecording: Boolean = false
        private set

    fun start(context: Context): File? {
        if (isRecording) return null
        return try {
            val dir = File(context.filesDir, "voice_recordings").apply { mkdirs() }
            val name = "voice_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.m4a"
            val file = File(dir, name)
            outputFile = file

            @Suppress("DEPRECATION")
            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }
            rec.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = rec
            isRecording = true
            file
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            isRecording = false
            null
        }
    }

    fun stop(): File? {
        if (!isRecording) return null
        return try {
            recorder?.stop()
            recorder?.release()
            recorder = null
            isRecording = false
            outputFile?.takeIf { it.exists() && it.length() > 0 }
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            isRecording = false
            outputFile?.delete()
            null
        }
    }

    fun cancel() {
        if (!isRecording) return
        try {
            recorder?.stop()
        } catch (_: Exception) {}
        recorder?.release()
        recorder = null
        isRecording = false
        outputFile?.delete()
        outputFile = null
    }
}
