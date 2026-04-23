package com.chatfusion.app

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import java.io.IOException

class AmbientNoiseDetector(private val context: Context, private val onNoiseLevelChanged: (String) -> Unit) {
    private var mediaRecorder: MediaRecorder? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false

    private val checkNoiseRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            try {
                val amplitude = mediaRecorder?.maxAmplitude ?: 0
                val status = when {
                    amplitude > 20000 -> "🔊 Loud Environment"
                    amplitude > 5000 -> "🔉 Moderate Noise"
                    else -> "🤫 Quiet Environment"
                }
                onNoiseLevelChanged(status)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            handler.postDelayed(this, 3000)
        }
    }

    @SuppressLint("NewApi")
    fun start() {
        if (isRunning) return
        try {
            mediaRecorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(context.cacheDir.absolutePath + "/noise_detect.3gp")
                prepare()
                start()
            }
            isRunning = true
            handler.post(checkNoiseRunnable)
        } catch (e: Exception) {
            e.printStackTrace()
            stop()
        }
    }

    fun stop() {
        isRunning = false
        handler.removeCallbacks(checkNoiseRunnable)
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaRecorder = null
    }
}
