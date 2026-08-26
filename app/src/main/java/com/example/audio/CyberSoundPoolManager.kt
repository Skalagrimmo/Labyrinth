package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Environmental Audio & Sound Effect Manager using Android's SoundPool.
 * Manages low-latency playback of environmental audio cues like terminal beeps,
 * footstep movement on varied surfaces, combat hits, door pneumatics, and hacking feedback.
 */
class CyberSoundPoolManager private constructor(context: Context) {

    enum class SoundCue {
        TERMINAL_BEEP,
        TERMINAL_KEYPRESS,
        FOOTSTEP_CONCRETE,
        FOOTSTEP_METAL,
        FOOTSTEP_SEWER,
        COMBAT_FOOTSTEP,
        COMBAT_HIT,
        COMBAT_CRIT,
        DOOR_SLIDE,
        ELEVATOR_TRANSIT,
        SCANNER_PING,
        HACK_SUCCESS,
        HACK_ERROR
    }

    enum class SurfaceMaterial {
        CONCRETE,
        METAL_GRATE,
        SEWER_WATER,
        TACTICAL_DECK
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.IO)

    private val soundPool: SoundPool
    private val soundMap = mutableMapOf<SoundCue, Int>()
    private val loadedSounds = mutableSetOf<Int>()

    private var isMuted = false
    private var sfxVolume = 1.0f

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(12)
            .setAudioAttributes(audioAttributes)
            .build()

        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                loadedSounds.add(sampleId)
            } else {
                Log.w("CyberSoundPoolManager", "Failed to load sampleId: $sampleId, status: $status")
            }
        }

        // Generate and preload audio samples into SoundPool asynchronously
        scope.launch {
            preloadAudioSamples()
        }
    }

    private fun preloadAudioSamples() {
        val cacheDir = File(appContext.cacheDir, "cyber_sfx")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        for (cue in SoundCue.entries) {
            try {
                val wavFile = File(cacheDir, "${cue.name.lowercase()}.wav")
                if (!wavFile.exists() || wavFile.length() < 44) {
                    generateWavForCue(cue, wavFile)
                }

                if (wavFile.exists()) {
                    val soundId = soundPool.load(wavFile.absolutePath, 1)
                    soundMap[cue] = soundId
                }
            } catch (e: Exception) {
                Log.e("CyberSoundPoolManager", "Error generating or loading WAV for $cue", e)
            }
        }
    }

    private fun generateWavForCue(cue: SoundCue, outputFile: File) {
        val sampleRate = 22050
        val pcmData: ShortArray = when (cue) {
            SoundCue.TERMINAL_BEEP -> generateTone(880.0, 70, sampleRate, 0.6f)
            SoundCue.TERMINAL_KEYPRESS -> generateClick(30, sampleRate, 0.4f)
            SoundCue.FOOTSTEP_CONCRETE -> generateFootstepConcrete(sampleRate)
            SoundCue.FOOTSTEP_METAL -> generateFootstepMetal(sampleRate)
            SoundCue.FOOTSTEP_SEWER -> generateFootstepSewer(sampleRate)
            SoundCue.COMBAT_FOOTSTEP -> generateCombatStomp(sampleRate)
            SoundCue.COMBAT_HIT -> generateImpactThud(120, sampleRate, 0.8f)
            SoundCue.COMBAT_CRIT -> generatePlasmaSlash(140, sampleRate)
            SoundCue.DOOR_SLIDE -> generatePneumaticHiss(180, sampleRate)
            SoundCue.ELEVATOR_TRANSIT -> generateSweepTone(220.0, 440.0, 200, sampleRate)
            SoundCue.SCANNER_PING -> generateSweepTone(1400.0, 800.0, 110, sampleRate)
            SoundCue.HACK_SUCCESS -> generateArpeggio(doubleArrayOf(523.25, 659.25, 783.99, 1046.50), 60, sampleRate)
            SoundCue.HACK_ERROR -> generateErrorBuzz(160, sampleRate)
        }

        writeWavFile(outputFile, pcmData, sampleRate)
    }

    /**
     * Plays a preloaded audio cue via SoundPool with volume and rate (pitch) adjustment.
     */
    fun playCue(cue: SoundCue, volumeMultiplier: Float = 1.0f, pitchRate: Float = 1.0f): Int {
        if (isMuted) return 0
        val soundId = soundMap[cue] ?: return 0
        val vol = (sfxVolume * volumeMultiplier).coerceIn(0.0f, 1.0f)
        val rate = pitchRate.coerceIn(0.5f, 2.0f)

        return if (loadedSounds.contains(soundId)) {
            soundPool.play(soundId, vol, vol, 1, 0, rate)
        } else {
            0
        }
    }

    // ----------------------------------------------------
    // Convenient Environmental & Interactive Audio Methods
    // ----------------------------------------------------

    fun playTerminalBeep(volume: Float = 0.8f) {
        playCue(SoundCue.TERMINAL_BEEP, volume)
    }

    fun playTerminalKeyPress(volume: Float = 0.5f) {
        // Slight pitch variation for typing feedback
        val rate = Random.nextDouble(0.95, 1.05).toFloat()
        playCue(SoundCue.TERMINAL_KEYPRESS, volume, rate)
    }

    fun playFootstep(material: SurfaceMaterial = SurfaceMaterial.CONCRETE, volume: Float = 0.7f) {
        val cue = when (material) {
            SurfaceMaterial.CONCRETE -> SoundCue.FOOTSTEP_CONCRETE
            SurfaceMaterial.METAL_GRATE -> SoundCue.FOOTSTEP_METAL
            SurfaceMaterial.SEWER_WATER -> SoundCue.FOOTSTEP_SEWER
            SurfaceMaterial.TACTICAL_DECK -> SoundCue.COMBAT_FOOTSTEP
        }
        val randomPitch = Random.nextDouble(0.92, 1.08).toFloat()
        playCue(cue, volume, randomPitch)
    }

    fun playCombatFootstep(volume: Float = 0.8f) {
        playFootstep(SurfaceMaterial.TACTICAL_DECK, volume)
    }

    fun playCombatHit(volume: Float = 1.0f) {
        playCue(SoundCue.COMBAT_HIT, volume)
    }

    fun playCombatCrit(volume: Float = 1.0f) {
        playCue(SoundCue.COMBAT_CRIT, volume)
    }

    fun playDoorSound(volume: Float = 0.8f) {
        playCue(SoundCue.DOOR_SLIDE, volume)
    }

    fun playElevatorSound(volume: Float = 0.8f) {
        playCue(SoundCue.ELEVATOR_TRANSIT, volume)
    }

    fun playScannerPing(volume: Float = 0.9f) {
        playCue(SoundCue.SCANNER_PING, volume)
    }

    fun playHackSuccess(volume: Float = 1.0f) {
        playCue(SoundCue.HACK_SUCCESS, volume)
    }

    fun playHackError(volume: Float = 1.0f) {
        playCue(SoundCue.HACK_ERROR, volume)
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
    }

    fun isMuted(): Boolean = isMuted

    fun setSfxVolume(volume: Float) {
        sfxVolume = volume.coerceIn(0.0f, 1.0f)
    }

    fun getSfxVolume(): Float = sfxVolume

    fun release() {
        soundPool.release()
        soundMap.clear()
        loadedSounds.clear()
    }

    // ----------------------------------------------------
    // PCM Synthesis Helpers for SoundPool WAV Generation
    // ----------------------------------------------------

    private fun generateTone(freqHz: Double, durationMs: Int, sampleRate: Int, volume: Float): ShortArray {
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt().coerceAtLeast(100)
        val buffer = ShortArray(numSamples)
        var phase = 0.0
        val phaseInc = 2.0 * PI * freqHz / sampleRate

        for (i in 0 until numSamples) {
            val env = when {
                i < 60 -> i / 60.0
                i > numSamples - 60 -> (numSamples - i) / 60.0
                else -> 1.0
            }
            buffer[i] = (sin(phase) * 32767 * volume * env).toInt().coerceIn(-32768, 32767).toShort()
            phase += phaseInc
        }
        return buffer
    }

    private fun generateClick(durationMs: Int, sampleRate: Int, volume: Float): ShortArray {
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt().coerceAtLeast(50)
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val decay = 1.0 - (i.toDouble() / numSamples)
            val noise = Random.nextDouble(-1.0, 1.0) * decay
            buffer[i] = (noise * 32767 * volume).toInt().coerceIn(-32768, 32767).toShort()
        }
        return buffer
    }

    private fun generateFootstepConcrete(sampleRate: Int): ShortArray {
        val durationMs = 80
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val buffer = ShortArray(numSamples)
        var phase = 0.0
        val lowThudFreq = 110.0

        for (i in 0 until numSamples) {
            val env = 1.0 - (i.toDouble() / numSamples)
            val thud = sin(phase) * env
            val grit = Random.nextDouble(-0.3, 0.3) * env * env
            buffer[i] = ((thud * 0.7 + grit * 0.3) * 32767 * 0.6).toInt().coerceIn(-32768, 32767).toShort()
            phase += 2.0 * PI * lowThudFreq / sampleRate
        }
        return buffer
    }

    private fun generateFootstepMetal(sampleRate: Int): ShortArray {
        val durationMs = 90
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val buffer = ShortArray(numSamples)
        var phase1 = 0.0
        var phase2 = 0.0

        for (i in 0 until numSamples) {
            val env = 1.0 - (i.toDouble() / numSamples)
            val ring = sin(phase1) * 0.5 + sin(phase2) * 0.3
            val click = Random.nextDouble(-0.2, 0.2) * (1.0 - i.toDouble() / 100.0).coerceAtLeast(0.0)
            buffer[i] = ((ring + click) * env * 32767 * 0.5).toInt().coerceIn(-32768, 32767).toShort()

            phase1 += 2.0 * PI * 1200.0 / sampleRate
            phase2 += 2.0 * PI * 2400.0 / sampleRate
        }
        return buffer
    }

    private fun generateFootstepSewer(sampleRate: Int): ShortArray {
        val durationMs = 110
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val buffer = ShortArray(numSamples)
        var phase = 0.0

        for (i in 0 until numSamples) {
            val env = 1.0 - (i.toDouble() / numSamples)
            val splashNoise = Random.nextDouble(-0.6, 0.6) * env
            val lowGurgle = sin(phase) * 0.4 * env
            buffer[i] = ((splashNoise + lowGurgle) * 32767 * 0.55).toInt().coerceIn(-32768, 32767).toShort()
            phase += 2.0 * PI * 160.0 / sampleRate
        }
        return buffer
    }

    private fun generateCombatStomp(sampleRate: Int): ShortArray {
        val durationMs = 100
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val buffer = ShortArray(numSamples)
        var phase = 0.0

        for (i in 0 until numSamples) {
            val env = 1.0 - (i.toDouble() / numSamples)
            val heavyBass = sin(phase) * env
            val armorClatter = Random.nextDouble(-0.25, 0.25) * env * env
            buffer[i] = ((heavyBass * 0.8 + armorClatter * 0.2) * 32767 * 0.75).toInt().coerceIn(-32768, 32767).toShort()
            phase += 2.0 * PI * 85.0 / sampleRate
        }
        return buffer
    }

    private fun generateImpactThud(durationMs: Int, sampleRate: Int, volume: Float): ShortArray {
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val buffer = ShortArray(numSamples)
        var phase = 0.0

        for (i in 0 until numSamples) {
            val env = (1.0 - (i.toDouble() / numSamples))
            val freq = 200.0 - (120.0 * (i.toDouble() / numSamples))
            val sample = sin(phase) * env * volume
            buffer[i] = (sample * 32767).toInt().coerceIn(-32768, 32767).toShort()
            phase += 2.0 * PI * freq / sampleRate
        }
        return buffer
    }

    private fun generatePlasmaSlash(durationMs: Int, sampleRate: Int): ShortArray {
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            val env = sin(progress * PI)
            val noise = Random.nextDouble(-0.7, 0.7) * env
            buffer[i] = (noise * 32767 * 0.7).toInt().coerceIn(-32768, 32767).toShort()
        }
        return buffer
    }

    private fun generatePneumaticHiss(durationMs: Int, sampleRate: Int): ShortArray {
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val env = 1.0 - (i.toDouble() / numSamples)
            val hiss = Random.nextDouble(-0.4, 0.4) * env
            buffer[i] = (hiss * 32767 * 0.5).toInt().coerceIn(-32768, 32767).toShort()
        }
        return buffer
    }

    private fun generateSweepTone(startFreq: Double, endFreq: Double, durationMs: Int, sampleRate: Int): ShortArray {
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val buffer = ShortArray(numSamples)
        var phase = 0.0

        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            val freq = startFreq + (endFreq - startFreq) * progress
            val env = sin(progress * PI)
            buffer[i] = (sin(phase) * 32767 * 0.5 * env).toInt().coerceIn(-32768, 32767).toShort()
            phase += 2.0 * PI * freq / sampleRate
        }
        return buffer
    }

    private fun generateArpeggio(notes: DoubleArray, stepMs: Int, sampleRate: Int): ShortArray {
        val stepSamples = (sampleRate * (stepMs / 1000.0)).toInt()
        val totalSamples = stepSamples * notes.size
        val buffer = ShortArray(totalSamples)

        for (nIdx in notes.indices) {
            val noteFreq = notes[nIdx]
            var phase = 0.0
            val phaseInc = 2.0 * PI * noteFreq / sampleRate

            for (i in 0 until stepSamples) {
                val globalIdx = nIdx * stepSamples + i
                val env = 1.0 - (i.toDouble() / stepSamples)
                buffer[globalIdx] = (sin(phase) * 32767 * 0.5 * env).toInt().coerceIn(-32768, 32767).toShort()
                phase += phaseInc
            }
        }
        return buffer
    }

    private fun generateErrorBuzz(durationMs: Int, sampleRate: Int): ShortArray {
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val buffer = ShortArray(numSamples)
        var phase = 0.0

        for (i in 0 until numSamples) {
            val env = 1.0 - (i.toDouble() / numSamples)
            val square = if (sin(phase) >= 0) 1.0 else -1.0
            buffer[i] = (square * 32767 * 0.45 * env).toInt().coerceIn(-32768, 32767).toShort()
            phase += 2.0 * PI * 140.0 / sampleRate
        }
        return buffer
    }

    private fun writeWavFile(file: File, pcmData: ShortArray, sampleRate: Int) {
        val dataSize = pcmData.size * 2
        val totalSize = 36 + dataSize
        val byteRate = sampleRate * 2

        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(totalSize)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(1)
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(2)
        buffer.putShort(16)
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataSize)

        for (sample in pcmData) {
            buffer.putShort(sample)
        }

        file.writeBytes(buffer.array())
    }

    companion object {
        @Volatile
        private var instance: CyberSoundPoolManager? = null

        fun getInstance(context: Context): CyberSoundPoolManager {
            return instance ?: synchronized(this) {
                val appContext = context.applicationContext
                instance ?: CyberSoundPoolManager(appContext).also { instance = it }
            }
        }
    }
}
