package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
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

    private var toneGenerator: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 85)
    } catch (e: Exception) {
        Log.e("CyberSoundEffects", "Failed to initialize ToneGenerator: ${e.message}")
        null
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
    // Sound Effects (SFX) Methods
    // ----------------------------------------------------

    /**
     * Audio cue when executing terminal commands.
     */
    fun playTerminalCommandSound() {
        if (isMuted) return
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
        } catch (e: Exception) {
            Log.e("CyberSoundEffects", "Error playing terminal command sound", e)
        }
    }

    /**
     * Audio cue for terminal keyboard typing input.
     */
    fun playTerminalKeyPressSound() {
        if (isMuted) return
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_PROMPT, 30)
        } catch (e: Exception) {
            Log.e("CyberSoundEffects", "Error playing keypress sound", e)
        }
    }

    /**
     * Cybernetic step movement sound effect.
     */
    fun playStepSound() {
        if (isMuted) return
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_KEYPAD_VOLUME_KEY_LITE, 25)
        } catch (e: Exception) {
            Log.e("CyberSoundEffects", "Error playing step sound", e)
        }
    }

    /**
     * Door open/close pneumatic sound effect.
     */
    fun playDoorSound() {
        if (isMuted) return
        scope.launch {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_SIGNAL_OFF, 60)
                delay(70)
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_PROMPT, 50)
            } catch (e: Exception) {
                Log.e("CyberSoundEffects", "Error playing door sound", e)
            }
        }
    }

    /**
     * Elevator / sector lift transition sound.
     */
    fun playElevatorSound() {
        if (isMuted) return
        scope.launch {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
                delay(90)
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 100)
            } catch (e: Exception) {
                Log.e("CyberSoundEffects", "Error playing elevator sound", e)
            }
        }
    }

    /**
     * Audio cue for standard combat damage hit.
     */
    fun playCombatHitSound() {
        if (isMuted) return
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ABBR_INTERCEPT, 120)
        } catch (e: Exception) {
            Log.e("CyberSoundEffects", "Error playing combat hit sound", e)
        }
    }

    /**
     * Audio cue for critical combat impact or heavy attack.
     */
    fun playCombatCritSound() {
        if (isMuted) return
        scope.launch {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 90)
                delay(90)
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 120)
            } catch (e: Exception) {
                Log.e("CyberSoundEffects", "Error playing combat crit sound", e)
            }
        }
    }

    /**
     * Audio cue for cyber weapon plasma / melee blade slash.
     */
    fun playPlasmaSlashSound() {
        if (isMuted) return
        scope.launch {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_HIGH_PBX_L, 40)
                delay(45)
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_MED_PBX_L, 60)
            } catch (e: Exception) {
                Log.e("CyberSoundEffects", "Error playing plasma slash sound", e)
            }
        }
    }

    /**
     * Audio cue when barrier / kinetic shield absorbs damage.
     */
    fun playShieldAbsorbSound() {
        if (isMuted) return
        scope.launch {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_NETWORK_USA, 70)
                delay(60)
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 80)
            } catch (e: Exception) {
                Log.e("CyberSoundEffects", "Error playing shield absorb sound", e)
            }
        }
    }

    /**
     * Audio cue when collecting loot caches or bounties.
     */
    fun playLootCollectionSound() {
        if (isMuted) return
        scope.launch {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 70)
                delay(80)
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 110)
            } catch (e: Exception) {
                Log.e("CyberSoundEffects", "Error playing loot collection sound", e)
            }
        }
    }

    /**
     * Audio cue for successful node breach or pattern match.
     */
    fun playHackingSuccessSound() {
        if (isMuted) return
        scope.launch {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 90)
                delay(100)
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 140)
            } catch (e: Exception) {
                Log.e("CyberSoundEffects", "Error playing hacking success sound", e)
            }
        }
    }

    /**
     * Audio cue for failed hack or security error.
     */
    fun playHackingErrorSound() {
        if (isMuted) return
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_SUP_ERROR, 200)
        } catch (e: Exception) {
            Log.e("CyberSoundEffects", "Error playing hacking error sound", e)
        }
    }

    /**
     * Audio cue when selecting or shifting buffer in hacking matrix.
     */
    fun playBufferShiftSound() {
        if (isMuted) return
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_KEYPAD_VOLUME_KEY_LITE, 35)
        } catch (e: Exception) {
            Log.e("CyberSoundEffects", "Error playing buffer shift sound", e)
        }
    }

    /**
     * Audio cue when breaching a cyber security node.
     */
    fun playNodeBreachSound() {
        if (isMuted) return
        scope.launch {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 60)
                delay(60)
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 90)
            } catch (e: Exception) {
                Log.e("CyberSoundEffects", "Error playing node breach sound", e)
            }
        }
    }

    /**
     * Audio cue for installing cybernetic implants / surgery.
     */
    fun playCyberwareInstallSound() {
        if (isMuted) return
        scope.launch {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_HIGH_PBX_L, 60)
                delay(70)
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 120)
            } catch (e: Exception) {
                Log.e("CyberSoundEffects", "Error playing cyberware install sound", e)
            }
        }
    }

    /**
     * Release tone generator and synth resources.
     */
    fun release() {
        bgmJob?.cancel()
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        toneGenerator?.release()
        toneGenerator = null
    }

    companion object {
        @Volatile
        private var instance: CyberSoundEffectsManager? = null

        fun getInstance(context: Context): CyberSoundEffectsManager {
            return instance ?: synchronized(this) {
                instance ?: CyberSoundEffectsManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
