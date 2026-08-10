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

    private val soundPoolManager = CyberSoundPoolManager.getInstance(context)

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

    private fun playFrequencySweep(
        startFreqHz: Double,
        endFreqHz: Double,
        durationMs: Int,
        volume: Float = 0.35f,
        isHarshSquare: Boolean = false
    ) {
        if (isMuted) return
        scope.launch(Dispatchers.Default) {
            try {
                val sampleRate = 22050
                val numSamples = (sampleRate * (durationMs / 1000.0)).toInt().coerceAtLeast(100)
                val buffer = ShortArray(numSamples)
                var phase = 0.0

                for (i in 0 until numSamples) {
                    val progress = i.toDouble() / numSamples
                    val currentFreq = startFreqHz + (endFreqHz - startFreqHz) * progress
                    val phaseIncrement = 2.0 * Math.PI * currentFreq / sampleRate

                    val env = when {
                        i < 80 -> i / 80.0
                        i > numSamples - 80 -> (numSamples - i) / 80.0
                        else -> 1.0
                    }

                    val rawWave = if (isHarshSquare) {
                        if (Math.sin(phase) >= 0) 1.0 else -1.0
                    } else {
                        Math.sin(phase)
                    }

                    val sample = (rawWave * 32767 * volume * env).toInt().coerceIn(-32768, 32767)
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
                Log.e("CyberSoundEffects", "Error playing PCM sweep sound effect", e)
            }
        }
    }

    /**
     * Audio cue when initiating a spatial sonar/radar map scan.
     */
    fun playScannerPingSound() {
        playFrequencySweep(startFreqHz = 1600.0, endFreqHz = 900.0, durationMs = 120, volume = 0.4f)
    }

    /**
     * Audio cue triggered when scanner detects interactive items, secrets, or bypass paths.
     * Plays ascending pitches proportional to item count and distinct chimes for secrets or bypass routes.
     */
    fun playScannerDetectionSound(itemCount: Int, hasSecrets: Boolean = false, hasBypass: Boolean = false) {
        if (isMuted) return
        scope.launch {
            if (itemCount <= 0) {
                // Low subtle ping for empty scan area
                playTone(320.0, 90, 0.2f)
                return@launch
            }

            // Ascending pitch sonar blips based on detected count
            val baseNotes = doubleArrayOf(523.25, 659.25, 783.99, 1046.50, 1318.51, 1567.98) // C5, E5, G5, C6, E6, G6
            val countToPlay = itemCount.coerceIn(1, baseNotes.size)

            for (idx in 0 until countToPlay) {
                playTone(baseNotes[idx], 60, 0.35f)
                delay(50)
            }

            // High-pitched gold shimmer chime for detected secrets
            if (hasSecrets) {
                delay(40)
                playTone(2093.0, 140, 0.45f) // C7 note
                delay(30)
                playTone(2637.0, 160, 0.4f)  // E7 note
            }

            // Emerald synth blip for bypass conduits / vents
            if (hasBypass) {
                delay(30)
                playFrequencySweep(startFreqHz = 1100.0, endFreqHz = 1750.0, durationMs = 90, volume = 0.35f)
            }
        }
    }

    /**
     * Audio cue when executing terminal commands.
     */
    fun playTerminalCommandSound() {
        soundPoolManager.playTerminalBeep(0.8f)
    }

    /**
     * Audio cue for terminal keyboard typing input.
     */
    fun playTerminalKeyPressSound() {
        soundPoolManager.playTerminalKeyPress(0.5f)
    }

    /**
     * Cybernetic step movement sound effect.
     */
    fun playStepSound() {
        soundPoolManager.playFootstep(CyberSoundPoolManager.SurfaceMaterial.CONCRETE, 0.7f)
    }

    /**
     * Environmental footstep sound with surface material.
     */
    fun playFootstep(material: CyberSoundPoolManager.SurfaceMaterial = CyberSoundPoolManager.SurfaceMaterial.CONCRETE, volume: Float = 0.7f) {
        soundPoolManager.playFootstep(material, volume)
    }

    /**
     * Door open/close pneumatic sound effect.
     */
    fun playDoorSound() {
        soundPoolManager.playDoorSound(0.8f)
    }

    /**
     * Elevator / sector lift transition sound.
     */
    fun playElevatorSound() {
        soundPoolManager.playElevatorSound(0.8f)
    }

    /**
     * Audio cue for standard combat damage hit.
     */
    fun playCombatHitSound() {
        soundPoolManager.playCombatHit(1.0f)
    }

    /**
     * Audio cue for critical combat impact or heavy attack.
     */
    fun playCombatCritSound() {
        soundPoolManager.playCombatCrit(1.0f)
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
     * Audio cue when hacking a security node or breaching a system terminal succeeds.
     * Plays a triumphant multi-stage digital breach victory sequence.
     */
    fun playSecurityNodeHackSuccessSound() {
        if (isMuted) return
        scope.launch {
            // Stage 1: Ascending cyber triad cascade
            playTone(659.25, 70, 0.35f)  // E5
            delay(65)
            playTone(880.0, 70, 0.4f)   // A5
            delay(65)
            playTone(1046.50, 80, 0.45f) // C6
            delay(75)
            playTone(1318.51, 110, 0.5f) // E6
            delay(80)

            // Stage 2: Sub-bass resonance chime indicating access barrier override
            playTone(220.0, 180, 0.4f)
            playTone(1760.0, 220, 0.45f) // High A6 harmonic shimmer
        }
    }

    /**
     * Audio cue when hacking a security node or breaching a system terminal fails.
     * Plays a harsh electrical glitch alarm & descending ICE lockout sweep.
     */
    fun playSecurityNodeHackFailureSound() {
        if (isMuted) return
        scope.launch {
            // Stage 1: Harsh 150Hz feedback error burst
            playFrequencySweep(startFreqHz = 180.0, endFreqHz = 120.0, durationMs = 150, volume = 0.5f, isHarshSquare = true)
            delay(130)

            // Stage 2: Descending dual alarm siren lockout
            playFrequencySweep(startFreqHz = 520.0, endFreqHz = 220.0, durationMs = 180, volume = 0.45f)
            delay(120)
            playTone(95.0, 250, 0.6f) // Deep hardware feedback impact
        }
    }

    /**
     * Audio cue for successful node breach or pattern match.
     */
    fun playHackingSuccessSound() {
        playSecurityNodeHackSuccessSound()
    }

    /**
     * Audio cue for failed hack or security error.
     */
    fun playHackingErrorSound() {
        playSecurityNodeHackFailureSound()
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
     * Release synth and SoundPool resources.
     */
    fun release() {
        soundPoolManager.release()
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
