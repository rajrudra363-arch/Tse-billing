package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

enum class SoundCue {
    CLICK,
    ADD,
    SAVE,
    DELETE,
    ERROR
}

class SoundEngine(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    @Volatile var enabled: Boolean = true

    init {
        try {
            tts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val hiLocale = Locale("hi", "IN")
                    val res = tts?.setLanguage(hiLocale)
                    if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.setLanguage(Locale.ENGLISH)
                    }
                    tts?.setSpeechRate(0.98f)
                    ttsReady = true
                }
            }
        } catch (_: Exception) {
            ttsReady = false
        }
    }

    fun play(cue: SoundCue) {
        if (!enabled) return
        scope.launch {
            when (cue) {
                SoundCue.CLICK -> playTone(640.0, 55, 0.22)
                SoundCue.ADD -> {
                    playTone(523.25, 70, 0.24)
                    playTone(659.25, 95, 0.24)
                }
                SoundCue.SAVE -> {
                    playTone(523.25, 75, 0.24)
                    playTone(659.25, 75, 0.24)
                    playTone(783.99, 130, 0.28)
                }
                SoundCue.DELETE -> playTone(320.0, 100, 0.24)
                SoundCue.ERROR -> {
                    playTone(230.0, 110, 0.28)
                    playTone(175.0, 140, 0.28)
                }
            }
        }
    }

    fun speak(message: String) {
        if (!enabled || !ttsReady || message.isBlank()) return
        try {
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "tse_${System.currentTimeMillis()}")
        } catch (_: Exception) {
        }
    }

    private fun playTone(freqHz: Double, durationMs: Int, volume: Double) {
        try {
            val sampleRate = 22050
            val count = (sampleRate * durationMs) / 1000
            if (count <= 0) return
            val samples = ShortArray(count)
            for (i in 0 until count) {
                val t = i.toDouble() / sampleRate
                val env = exp(-4.5 * t / (durationMs / 1000.0))
                val sample = sin(2.0 * PI * freqHz * t) * env * volume
                samples[i] = (sample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufSize = maxOf(minBuf, samples.size * 2)
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(samples, 0, samples.size)
            track.play()
            Thread.sleep(durationMs.toLong() + 15L)
            track.stop()
            track.release()
        } catch (_: Exception) {
        }
    }

    fun release() {
        scope.cancel()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        ttsReady = false
    }
}
