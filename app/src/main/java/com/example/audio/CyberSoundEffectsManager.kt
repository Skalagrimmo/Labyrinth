package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Ambient Audio & Sound Effects Manager
 * Provides procedural industrial, synth-heavy background music synthesis (AudioTrack PCM)
 * and dynamic sound effects for hacking, combat, movement, and system interface.
 */
class CyberSoundEffectsManager private constructor(context: Context) {

    enum class MusicMode(val displayName: String, val bpm: Int) {
        EXPLORATION("Dark Cyber Drone", 85),
        COMBAT("Overclocked Synthwave", 130),
        HACKING("Matrix Data Stream", 110),
        OFF("Audio Muted", 0)
    }

    private var isMuted: Boolean = false
    private var isBgmMuted: Boolean = false
    private var bgmVolume: Float = 0.45f
    private var currentMusicMode: MusicMode = MusicMode.EXPLORATION

    private val scope = CoroutineScope(Dispatchers.Default)
    private var bgmJob: Job? = null
    private var audioTrack: AudioTrack? = null

    init {
        startBgmSynthEngine()
    }

    // ----------------------------------------------------
    // BGM Controls & State
    // ----------------------------------------------------

    fun setMuted(muted: Boolean) {
        isMuted = muted
        if (muted) {
            setBgmMuted(true)
        }
    }

    fun isMuted(): Boolean = isMuted

    fun setBgmMuted(muted: Boolean) {
        isBgmMuted = muted
    }

    fun isBgmMuted(): Boolean = isBgmMuted

    fun setBgmVolume(volume: Float) {
        bgmVolume = volume.coerceIn(0f, 1f)
    }

    fun getBgmVolume(): Float = bgmVolume

    fun setMusicMode(mode: MusicMode) {
        if (currentMusicMode != mode) {
            currentMusicMode = mode
            Log.d("CyberSoundEffects", "BGM Music Mode changed to: ${mode.displayName}")
        }
    }

    fun getMusicMode(): MusicMode = currentMusicMode

    // ----------------------------------------------------
    // Procedural Synth BGM Engine (AudioTrack PCM)
    // ----------------------------------------------------

    private fun startBgmSynthEngine() {
        bgmJob?.cancel()
        bgmJob = scope.launch(Dispatchers.Default) {
            val sampleRate = 22050
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(2048)

            try {
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack?.play()

                val bufferChunkSize = 512
                val buffer = ShortArray(bufferChunkSize)

                var phaseBass = 0.0
                var phaseLead = 0.0
                var stepIndex = 0
                var sampleCounter = 0

                // Notes for synth tracks (Frequencies in Hz)
                val explorationBassSequence = doubleArrayOf(55.0, 55.0, 65.41, 58.27, 55.0, 55.0, 73.42, 65.41) // A1, C2, A#1, D2
                val combatBassSequence = doubleArrayOf(65.41, 65.41, 77.78, 87.31, 98.00, 87.31, 77.78, 65.41) // C2, D#2, F2, G2
                val hackingArpSequence = doubleArrayOf(261.63, 311.13, 392.00, 523.25, 622.25, 783.99, 523.25, 392.00) // C4, D#4, G4, C5, D#5, G5

                while (isActive) {
                    if (isMuted || isBgmMuted || currentMusicMode == MusicMode.OFF || bgmVolume <= 0.01f) {
                        // Silent buffer to keep stream alive smoothly
                        buffer.fill(0)
                        audioTrack?.write(buffer, 0, bufferChunkSize)
                        delay(20)
                        continue
                    }

                    val bpm = currentMusicMode.bpm
                    val samplesPerStep = (sampleRate * 60) / (bpm * 4)

                    for (i in 0 until bufferChunkSize) {
                        sampleCounter++
                        if (sampleCounter >= samplesPerStep) {
                            sampleCounter = 0
                            stepIndex = (stepIndex + 1) % 8
                        }

                        val mode = currentMusicMode
                        val volumeMultiplier = bgmVolume * 0.25f

                        val bassFreq = when (mode) {
                            MusicMode.EXPLORATION -> explorationBassSequence[stepIndex]
                            MusicMode.COMBAT -> combatBassSequence[stepIndex]
                            MusicMode.HACKING -> explorationBassSequence[stepIndex % 4]
                            MusicMode.OFF -> 0.0
                        }

                        val leadFreq = when (mode) {
                            MusicMode.EXPLORATION -> if (stepIndex % 2 == 0) 110.0 else 0.0
                            MusicMode.COMBAT -> combatBassSequence[(stepIndex + 2) % 8] * 2.0
                            MusicMode.HACKING -> hackingArpSequence[stepIndex]
                            MusicMode.OFF -> 0.0
                        }

                        phaseBass += 2.0 * PI * bassFreq / sampleRate
                        if (phaseBass > 2.0 * PI) phaseBass -= 2.0 * PI

                        phaseLead += 2.0 * PI * leadFreq / sampleRate
                        if (phaseLead > 2.0 * PI) phaseLead -= 2.0 * PI

                        // Sawtooth wave for deep bass synth
                        val sawBass = (phaseBass / PI) - 1.0

                        // Sine wave for lead synth
                        val sineLead = sin(leadFreq)

                        // Industrial rhythm noise burst on step beats
                        val isBeat = (sampleCounter < (sampleRate * 0.02) && (stepIndex % 2 == 0))
                        val noise = if (isBeat && mode == MusicMode.COMBAT) (Random.nextDouble() * 2.0 - 1.0) * 0.4 else 0.0

                        // Mix synthesis signal
                        val mixedSample = (sawBass * 0.5 + sineLead * 0.3 + noise) * volumeMultiplier
                        val pcmValue = (mixedSample.coerceIn(-1.0, 1.0) * 32767).toInt().toShort()
                        buffer[i] = pcmValue
                    }

                    audioTrack?.write(buffer, 0, bufferChunkSize)
                }
            } catch (e: Exception) {
                Log.e("CyberSoundEffects", "Error in BGM synth engine: ${e.message}")
            }
        }
    }

    // ----------------------------------------------------
    // Sound Effects (SFX) PCM Synthesizer
    // ----------------------------------------------------

    private fun playTone(freqHz: Double, durationMs: Int, volume: Float = 0.35f) {
        if (isMuted) return
        scope.launch(Dispatchers.Default) {
            try {
                val sampleRate = 22050
                val numSamples = (sampleRate * (durationMs / 1000.0)).toInt().coerceAtLeast(100)
                val buffer = ShortArray(numSamples)
                var phase = 0.0
                val phaseIncrement = 2.0 * Math.PI * freqHz / sampleRate

                for (i in 0 until numSamples) {
                    val env = when {
                        i < 80 -> i / 80.0
                        i > numSamples - 80 -> (numSamples - i) / 80.0
                        else -> 1.0
                    }
                    val sample = (Math.sin(phase) * 32767 * volume * env).toInt().coerceIn(-32768, 32767)
                    buffer[i] = sample.toShort()
                    phase += phaseIncrement
                }

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
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
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(buffer, 0, buffer.size)
                track.play()
                delay(durationMs.toLong() + 40)
                track.release()
            } catch (e: Exception) {
                Log.e("CyberSoundEffects", "Error playing PCM tone sound effect", e)
            }
        }
    }

    /**
     * Audio cue when executing terminal commands.
     */
    fun playTerminalCommandSound() {
        playTone(880.0, 80)
    }

    /**
     * Audio cue for terminal keyboard typing input.
     */
    fun playTerminalKeyPressSound() {
        playTone(1200.0, 30, 0.25f)
    }

    /**
     * Cybernetic step movement sound effect.
     */
    fun playStepSound() {
        playTone(180.0, 30, 0.2f)
    }

    /**
     * Door open/close pneumatic sound effect.
     */
    fun playDoorSound() {
        scope.launch {
            playTone(220.0, 60)
            delay(70)
            playTone(440.0, 50)
        }
    }

    /**
     * Elevator / sector lift transition sound.
     */
    fun playElevatorSound() {
        scope.launch {
            playTone(523.25, 80)
            delay(90)
            playTone(659.25, 100)
        }
    }

    /**
     * Audio cue for standard combat damage hit.
     */
    fun playCombatHitSound() {
        playTone(180.0, 120, 0.5f)
    }

    /**
     * Audio cue for critical combat impact or heavy attack.
     */
    fun playCombatCritSound() {
        scope.launch {
            playTone(300.0, 90, 0.5f)
            delay(90)
            playTone(800.0, 120, 0.6f)
        }
    }

    /**
     * Audio cue for cyber weapon plasma / melee blade slash.
     */
    fun playPlasmaSlashSound() {
        scope.launch {
            playTone(600.0, 40)
            delay(45)
            playTone(300.0, 60)
        }
    }

    /**
     * Audio cue when barrier / kinetic shield absorbs damage.
     */
    fun playShieldAbsorbSound() {
        scope.launch {
            playTone(200.0, 70)
            delay(60)
            playTone(900.0, 80)
        }
    }

    /**
     * Audio cue when collecting loot caches or bounties.
     */
    fun playLootCollectionSound() {
        scope.launch {
            playTone(700.0, 70)
            delay(80)
            playTone(1400.0, 110)
        }
    }

    /**
     * Audio cue for successful node breach or pattern match.
     */
    fun playHackingSuccessSound() {
        scope.launch {
            playTone(880.0, 90)
            delay(100)
            playTone(1760.0, 140)
        }
    }

    /**
     * Audio cue for failed hack or security error.
     */
    fun playHackingErrorSound() {
        playTone(150.0, 200, 0.5f)
    }

    /**
     * Audio cue when selecting or shifting buffer in hacking matrix.
     */
    fun playBufferShiftSound() {
        playTone(1000.0, 35, 0.25f)
    }

    /**
     * Audio cue when breaching a cyber security node.
     */
    fun playNodeBreachSound() {
        scope.launch {
            playTone(500.0, 60)
            delay(60)
            playTone(1000.0, 90)
        }
    }

    /**
     * Audio cue for installing cybernetic implants / surgery.
     */
    fun playCyberwareInstallSound() {
        scope.launch {
            playTone(400.0, 60)
            delay(70)
            playTone(1200.0, 120)
        }
    }

    /**
     * Release synth resources.
     */
    fun release() {
        bgmJob?.cancel()
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    companion object {
        @Volatile
        private var instance: CyberSoundEffectsManager? = null

        fun getInstance(context: Context): CyberSoundEffectsManager {
            return instance ?: synchronized(this) {
                val appContext = context.applicationContext
                instance ?: CyberSoundEffectsManager(appContext).also { instance = it }
            }
        }
    }
}
