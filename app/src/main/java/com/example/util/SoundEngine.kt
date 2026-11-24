package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import com.example.data.VoicePreset
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
    ERROR,
    NOTIFICATION
}

class SoundEngine(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    @Volatile private var pendingMessage: String? = null
    @Volatile private var pendingVoiceModelId: String = VoicePreset.CLEAR.id
    @Volatile var currentVoiceModelId: String = VoicePreset.CLEAR.id
    @Volatile var enabled: Boolean = true

    init {
        try {
            tts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ttsReady = true
                    applyVoiceModel(currentVoiceModelId)
                    val queued = pendingMessage
                    val queuedModel = pendingVoiceModelId
                    pendingMessage = null
                    if (!queued.isNullOrBlank()) {
                        speakEnglish(queued, queuedModel)
                    }
                }
            }
        } catch (_: Exception) {
            ttsReady = false
        }
    }

    fun availableSystemVoices(): List<Pair<String, String>> {
        val voices = runCatching { tts?.voices }.getOrNull().orEmpty()
        return voices
            .filter { v ->
                !v.isNetworkConnectionRequired &&
                    v.locale.language.equals("en", ignoreCase = true)
            }
            .distinctBy { it.name }
            .sortedBy { it.name }
            .take(12)
            .map { v ->
                val country = v.locale.country.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
                val friendly = "Device Voice: ${v.name.substringAfterLast('#').substringAfterLast('-').uppercase(Locale.US)}$country"
                v.name to friendly
            }
    }

    fun applyVoiceModel(modelId: String) {
        val targetId = modelId.ifBlank { VoicePreset.CLEAR.id }
        currentVoiceModelId = targetId
        val engine = tts ?: return
        if (!ttsReady) return

        try {
            val enIn = Locale("en", "IN")
            val res = engine.setLanguage(enIn)
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                val usRes = engine.setLanguage(Locale.US)
                if (usRes == TextToSpeech.LANG_MISSING_DATA || usRes == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine.setLanguage(Locale.ENGLISH)
                }
            }

            val preset = VoicePreset.entries.firstOrNull { it.id == targetId }
            if (preset != null) {
                engine.setPitch(preset.pitch)
                engine.setSpeechRate(preset.rate)
            } else {
                val matchedVoice = runCatching { engine.voices }.getOrNull()
                    ?.firstOrNull { it.name == targetId }
                if (matchedVoice != null) {
                    engine.voice = matchedVoice
                }
                engine.setPitch(1.0f)
                engine.setSpeechRate(0.96f)
            }
        } catch (_: Exception) {
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
                SoundCue.NOTIFICATION -> {
                    playTone(880.0, 80, 0.25)
                    playTone(1174.66, 120, 0.28)
                }
            }
        }
    }

    fun speak(message: String, voiceModelId: String = currentVoiceModelId) {
        speakEnglish(message, voiceModelId)
    }

    fun speakEnglish(message: String, voiceModelId: String = currentVoiceModelId) {
        if (!enabled || message.isBlank()) return
        if (!ttsReady) {
            pendingMessage = message
            pendingVoiceModelId = voiceModelId
            return
        }
        try {
            applyVoiceModel(voiceModelId)
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "tse_en_${System.currentTimeMillis()}")
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
                samples[i] = (sample * Short.MAX_VALUE)
                    .toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
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
