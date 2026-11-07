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
    private var isSfxMuted: Boolean = false
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

    fun setSfxMuted(muted: Boolean) {
        isSfxMuted = muted
    }

    fun isSfxMuted(): Boolean = isSfxMuted

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

                // --- Musical data: MIDI -> Hz lookup and 4-bar minor progressions ---
                val midiHz = DoubleArray(128) { 440.0 * Math.pow(2.0, (it - 69) / 12.0) }
                val explorationProgression = arrayOf(
                    intArrayOf(57, 60, 64),  // Am
                    intArrayOf(53, 57, 60),  // F
                    intArrayOf(48, 52, 55),  // C
                    intArrayOf(55, 59, 62)   // G
                )
                val combatProgression = arrayOf(
                    intArrayOf(52, 55, 59),  // Em
                    intArrayOf(48, 52, 55),  // C
                    intArrayOf(55, 59, 62),  // G
                    intArrayOf(50, 54, 57)   // D
                )
                val hackingProgression = arrayOf(
                    intArrayOf(48, 51, 55, 58),  // Cm7
                    intArrayOf(56, 60, 63),      // Ab
                    intArrayOf(58, 62, 65),      // Bb
                    intArrayOf(48, 51, 55, 58)   // Cm7
                )
                // Driving 16th-note semitone offsets from the bar root (synthwave bass)
                val combatBassPattern = intArrayOf(0, 0, 12, 0, 0, 12, 0, 7, 0, 0, 12, 0, 0, 12, 7, 12)

                // --- Synth voices & tempo-synced feedback echo ---
                val bassVoice = SynthVoice(sampleRate)
                val subVoice = SynthVoice(sampleRate)
                val arpVoice = SynthVoice(sampleRate)
                val leadVoice = SynthVoice(sampleRate)
                val kickVoice = SynthVoice(sampleRate)
                val snareVoice = SynthVoice(sampleRate)
                val hatVoice = SynthVoice(sampleRate)
                val padVoices = Array(4) { SynthVoice(sampleRate) }
                var echoDelaySamples = 1024
                var echoBuf = DoubleArray(echoDelaySamples)
                var echoPos = 0

                var stepIndex = 0
                var barIndex = 0
                var sampleCounter = 0

                while (isActive) {
                    if (isMuted || isBgmMuted || currentMusicMode == MusicMode.OFF || bgmVolume <= 0.01f) {
                        // Silent buffer to keep stream alive smoothly
                        buffer.fill(0)
                        audioTrack?.write(buffer, 0, bufferChunkSize)
                        delay(20)
                        continue
                    }

                    val bpm = currentMusicMode.bpm
                    val samplesPerStep = (sampleRate * 60.0) / (bpm * 4).coerceAtLeast(1)
                    val barDurSec = samplesPerStep * 16.0 / sampleRate
                    val echoTarget = (samplesPerStep * 3).toInt().coerceAtLeast(256)
                    if (echoDelaySamples != echoTarget) {
                        echoDelaySamples = echoTarget
                        echoBuf = DoubleArray(echoDelaySamples)
                        echoPos = 0
                    }

                    for (i in 0 until bufferChunkSize) {
                        sampleCounter++
                        if (sampleCounter >= samplesPerStep) {
                            sampleCounter = 0
                            stepIndex = (stepIndex + 1) % 16
                            if (stepIndex == 0) barIndex = (barIndex + 1) % 4

                            when (currentMusicMode) {
                                MusicMode.EXPLORATION -> {
                                    val chord = explorationProgression[barIndex]
                                    if (stepIndex == 0) {
                                        // Dark sub bass drone on the chord root
                                        bassVoice.trigger(midiHz[chord[0] - 24], amplitude = 0.34, decayTauSec = barDurSec * 0.95, waveType = SynthVoice.WAVE_SAW, attackSec = 0.06, detune = 0.002)
                                        // Slow analog pad on the chord tones
                                        padVoices.forEachIndexed { p, v ->
                                            v.trigger(midiHz[chord[p % chord.size]], amplitude = 0.10, decayTauSec = barDurSec, waveType = SynthVoice.WAVE_SAW, attackSec = 0.7, detune = 0.004)
                                        }
                                    }
                                    if (stepIndex == 8) {
                                        // Re-trigger the bass on the fifth for harmonic movement
                                        bassVoice.trigger(midiHz[chord[2] - 24], amplitude = 0.22, decayTauSec = barDurSec * 0.45, waveType = SynthVoice.WAVE_SAW, attackSec = 0.05, detune = 0.002)
                                    }
                                    // Sparse minor pluck arpeggio on offbeats
                                    if (stepIndex == 2 || stepIndex == 5 || stepIndex == 9 || stepIndex == 12) {
                                        val tone = when (stepIndex) { 2 -> 0; 5 -> 1; 9 -> 2; else -> 1 }
                                        arpVoice.trigger(midiHz[chord[tone] + 12], amplitude = 0.14, decayTauSec = 0.45, waveType = SynthVoice.WAVE_SINE)
                                    }
                                    // Soft industrial tick
                                    if (stepIndex == 4 || stepIndex == 12) {
                                        hatVoice.trigger(1000.0, amplitude = 0.05, decayTauSec = 0.025, waveType = SynthVoice.WAVE_NOISE)
                                    }
                                    // Data-blip accent mid-bar on alternating bars
                                    if (stepIndex == 7 && barIndex % 2 == 1) {
                                        leadVoice.trigger(midiHz[chord[1] + 24], amplitude = 0.06, decayTauSec = 0.3, waveType = SynthVoice.WAVE_SINE)
                                    }
                                }
                                MusicMode.COMBAT -> {
                                    val chord = combatProgression[barIndex]
                                    val rootMidi = chord[0] - 12
                                    // Drums
                                    if (stepIndex % 4 == 0) {
                                        kickVoice.trigger(130.0, amplitude = 0.5, decayTauSec = 0.09, waveType = SynthVoice.WAVE_SINE, glideToFreqHz = 45.0)
                                    }
                                    if (stepIndex == 4 || stepIndex == 12) {
                                        snareVoice.trigger(200.0, amplitude = 0.2, decayTauSec = 0.05, waveType = SynthVoice.WAVE_NOISE)
                                    }
                                    if (stepIndex % 2 == 1) {
                                        hatVoice.trigger(6000.0, amplitude = if (stepIndex % 4 == 1) 0.09 else 0.05, decayTauSec = if (stepIndex == 15) 0.09 else 0.02, waveType = SynthVoice.WAVE_NOISE)
                                    }
                                    // Driving synthwave bass line (16th notes)
                                    bassVoice.trigger(midiHz[rootMidi + combatBassPattern[stepIndex]], amplitude = 0.28, decayTauSec = 0.11, waveType = SynthVoice.WAVE_SAW, detune = 0.004)
                                    // Chord stabs on the downbeats
                                    if (stepIndex == 0 || stepIndex == 8) {
                                        padVoices.forEachIndexed { p, v ->
                                            v.trigger(midiHz[chord[p % chord.size]], amplitude = 0.085, decayTauSec = 0.2, waveType = SynthVoice.WAVE_SAW, attackSec = 0.01, detune = 0.006)
                                        }
                                    }
                                    // Overclocked lead arpeggio, breathing between bars
                                    if (barIndex % 2 == 0 || stepIndex < 8) {
                                        val leadTone = when (stepIndex % 4) { 0 -> 0; 1 -> 1; 2 -> 2; else -> 1 }
                                        leadVoice.trigger(midiHz[chord[leadTone] + 24], amplitude = 0.11, decayTauSec = 0.14, waveType = SynthVoice.WAVE_SAW, detune = 0.007)
                                    }
                                }
                                MusicMode.HACKING -> {
                                    val chord = hackingProgression[barIndex]
                                    if (stepIndex == 0) {
                                        // Sustained sub pulse under the data stream
                                        subVoice.trigger(midiHz[chord[0] - 24], amplitude = 0.24, decayTauSec = barDurSec, waveType = SynthVoice.WAVE_SINE, attackSec = 0.15)
                                        padVoices.forEachIndexed { p, v ->
                                            v.trigger(midiHz[chord[p % chord.size]], amplitude = 0.055, decayTauSec = barDurSec * 0.9, waveType = SynthVoice.WAVE_SAW, attackSec = 0.4, detune = 0.003)
                                        }
                                    }
                                    if (stepIndex % 8 == 0) {
                                        kickVoice.trigger(110.0, amplitude = 0.28, decayTauSec = 0.07, waveType = SynthVoice.WAVE_SINE, glideToFreqHz = 42.0)
                                    }
                                    if (stepIndex == 4 || stepIndex == 12) {
                                        hatVoice.trigger(8000.0, amplitude = 0.05, decayTauSec = 0.02, waveType = SynthVoice.WAVE_NOISE)
                                    }
                                    // Matrix arpeggio: chord tones across two octaves, ping-ponged
                                    val t = (if (stepIndex < 8) stepIndex else (14 - stepIndex)).coerceAtLeast(0)
                                    val baseTone = chord[t % chord.size]
                                    arpVoice.trigger(midiHz[baseTone + if (t >= chord.size) 12 else 0], amplitude = 0.12, decayTauSec = 0.13, waveType = SynthVoice.WAVE_SINE)
                                }
                                MusicMode.OFF -> { /* handled by the silent-buffer branch */ }
                            }
                        }

                        // Render voices + tempo-synced feedback echo on the arpeggio
                        val arp = arpVoice.process()
                        val echoOut = echoBuf[echoPos]
                        echoBuf[echoPos] = arp * 0.5 + echoOut * 0.42
                        echoPos = (echoPos + 1) % echoDelaySamples

                        val mixed = bassVoice.process() +
                            subVoice.process() +
                            arp +
                            leadVoice.process() +
                            kickVoice.process() +
                            snareVoice.process() +
                            hatVoice.process() +
                            padVoices.sumOf { it.process() } +
                            echoOut * 0.55

                        val master = mixed * (bgmVolume * 0.35f)
                        buffer[i] = (master.coerceIn(-1.0, 1.0) * 32767).toInt().toShort()
                    }

                    audioTrack?.write(buffer, 0, bufferChunkSize)
                }
            } catch (e: Exception) {
                Log.e("CyberSoundEffects", "Error in BGM synth engine: ${e.message}")
            }
        }
    }

    /**
     * Lightweight mono synth voice used by the BGM step sequencer:
     * selectable waveform, optional detuned second oscillator,
     * attack/decay exponential envelope and pitch glide.
     */
    private class SynthVoice(private val sampleRate: Int) {

        companion object {
            const val WAVE_SINE = 0
            const val WAVE_SAW = 1
            const val WAVE_SQUARE = 2
            const val WAVE_NOISE = 3
        }

        private var phase = 0.0
        private var detunedPhase = 0.0
        private var phaseInc = 0.0
        private var detunedPhaseInc = 0.0
        private var glidePerSample = 1.0
        private var env = 0.0
        private var amp = 0.0
        private var decayPerSample = 0.0
        private var attackPerSample = 0.0
        private var inAttack = false
        private var wave = WAVE_SINE

        fun trigger(
            freqHz: Double,
            amplitude: Double,
            decayTauSec: Double,
            waveType: Int = WAVE_SINE,
            attackSec: Double = 0.0,
            detune: Double = 0.0,
            glideToFreqHz: Double? = null
        ) {
            phaseInc = 2.0 * PI * freqHz / sampleRate
            detunedPhaseInc = if (detune != 0.0) 2.0 * PI * freqHz * (1.0 + detune) / sampleRate else 0.0
            glidePerSample = glideToFreqHz?.let { Math.pow(it / freqHz, 1.0 / (0.09 * sampleRate)) } ?: 1.0
            amp = amplitude
            decayPerSample = Math.exp(-1.0 / (decayTauSec.coerceAtLeast(0.01) * sampleRate))
            wave = waveType
            if (attackSec > 0.0) {
                env = 0.0
                attackPerSample = 1.0 / (attackSec * sampleRate)
                inAttack = true
            } else {
                env = 1.0
                inAttack = false
            }
        }

        fun process(): Double {
            if (env <= 0.0002) return 0.0
            phaseInc *= glidePerSample
            phase += phaseInc
            if (phase >= 2.0 * PI) phase -= 2.0 * PI
            var sample = when (wave) {
                WAVE_SINE -> sin(phase)
                WAVE_SAW -> phase / PI - 1.0
                WAVE_SQUARE -> if (phase < PI) 0.55 else -0.55
                else -> Random.nextDouble() * 2.0 - 1.0
            }
            if (detunedPhaseInc > 0.0) {
                detunedPhase += detunedPhaseInc
                if (detunedPhase >= 2.0 * PI) detunedPhase -= 2.0 * PI
                sample += (detunedPhase / PI - 1.0) * 0.5
            }
            if (inAttack) {
                env += attackPerSample
                if (env >= 1.0) {
                    env = 1.0
                    inAttack = false
                }
            } else {
                env *= decayPerSample
                if (env <= 0.0002) return 0.0
            }
            return sample * amp * env
        }
    }

    // ----------------------------------------------------
    // Sound Effects (SFX) PCM Synthesizer
    // ----------------------------------------------------

    private fun playTone(freqHz: Double, durationMs: Int, volume: Float = 0.35f) {
        if (isMuted || isSfxMuted) return
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
        if (isMuted || isSfxMuted) return
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
        if (isMuted || isSfxMuted) return
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
        if (isSfxMuted) return
        soundPoolManager.playTerminalBeep(0.8f)
    }

    /**
     * Audio cue for terminal keyboard typing input.
     */
    fun playTerminalKeyPressSound() {
        if (isSfxMuted) return
        soundPoolManager.playTerminalKeyPress(0.5f)
    }

    /**
     * Cybernetic step movement sound effect.
     */
    fun playStepSound() {
        if (isSfxMuted) return
        soundPoolManager.playFootstep(CyberSoundPoolManager.SurfaceMaterial.CONCRETE, 0.7f)
    }

    /**
     * Environmental footstep sound with surface material.
     */
    fun playFootstep(material: CyberSoundPoolManager.SurfaceMaterial = CyberSoundPoolManager.SurfaceMaterial.CONCRETE, volume: Float = 0.7f) {
        if (isSfxMuted) return
        soundPoolManager.playFootstep(material, volume)
    }

    /**
     * Door open/close pneumatic sound effect.
     */
    fun playDoorSound() {
        if (isSfxMuted) return
        soundPoolManager.playDoorSound(0.8f)
    }

    /**
     * Elevator / sector lift transition sound.
     */
    fun playElevatorSound() {
        if (isSfxMuted) return
        soundPoolManager.playElevatorSound(0.8f)
    }

    /**
     * Audio cue for standard combat damage hit.
     */
    fun playCombatHitSound() {
        if (isSfxMuted) return
        soundPoolManager.playCombatHit(1.0f)
    }

    /**
     * Audio cue for critical combat impact or heavy attack.
     */
    fun playCombatCritSound() {
        if (isSfxMuted) return
        soundPoolManager.playCombatCrit(1.0f)
    }

    /**
     * Audio cue for cyber weapon plasma / melee blade slash.
     */
    fun playPlasmaSlashSound() {
        scope.launch {
            playFrequencySweep(startFreqHz = 750.0, endFreqHz = 260.0, durationMs = 110, volume = 0.4f)
            delay(40)
            playTone(300.0, 60, 0.25f)
        }
    }

    /**
     * Audio cue when barrier / kinetic shield absorbs damage.
     */
    fun playShieldAbsorbSound() {
        scope.launch {
            playTone(140.0, 70)
            playTone(210.0, 70, 0.2f) // Low fifth body
            delay(60)
            playTone(880.0, 80)
            playTone(1320.0, 80, 0.22f) // Absorb chime fifth
        }
    }

    /**
     * Audio cue when collecting loot caches or bounties.
     */
    fun playLootCollectionSound() {
        scope.launch {
            playTone(700.0, 70)
            playTone(1050.0, 70, 0.18f) // Added fifth: richer pickup chime
            delay(80)
            playTone(1400.0, 110)
            playTone(2100.0, 110, 0.2f) // Added fifth
        }
    }

    /**
     * Audio cue when hacking a security node or breaching a system terminal succeeds.
     * Plays a triumphant multi-stage digital breach victory sequence.
     */
    fun playSecurityNodeHackSuccessSound() {
        if (isMuted || isSfxMuted) return
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

            // Stage 2: Sub-bass resonance + A major chime indicating access barrier override
            playTone(220.0, 180, 0.4f)
            playTone(277.18, 180, 0.3f)   // C#5 major third
            playTone(329.63, 180, 0.3f)   // E5 fifth
            playTone(1760.0, 220, 0.45f)  // High A6 harmonic shimmer
            playTone(2637.0, 220, 0.25f)  // E7 shimmer octave
        }
    }

    /**
     * Audio cue when hacking a security node or breaching a system terminal fails.
     * Plays a harsh electrical glitch alarm & descending ICE lockout sweep.
     */
    fun playSecurityNodeHackFailureSound() {
        if (isMuted || isSfxMuted) return
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
            playTone(750.0, 60, 0.2f) // Added fifth: power chord bite
            delay(60)
            playTone(1000.0, 90)
            playTone(1500.0, 90, 0.22f) // Added fifth shimmer
        }
    }

    /**
     * Audio cue for installing cybernetic implants / surgery.
     */
    fun playCyberwareInstallSound() {
        scope.launch {
            playFrequencySweep(startFreqHz = 300.0, endFreqHz = 900.0, durationMs = 90, volume = 0.3f)
            delay(80)
            playTone(1200.0, 120)
            playTone(1800.0, 120, 0.2f) // Harmonic sixth shimmer
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
