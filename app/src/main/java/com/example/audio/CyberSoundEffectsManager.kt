package com.example.audio

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Sound Effects Manager class for playing distinct audio cues for:
 * - Terminal command execution & keypresses
 * - Combat strikes, critical hits, and defenses
 * - Loot and credit collection
 * - Hacking mini-game success and error alerts
 */
class CyberSoundEffectsManager private constructor(context: Context) {

    private var toneGenerator: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 85)
    } catch (e: Exception) {
        Log.e("CyberSoundEffects", "Failed to initialize ToneGenerator: ${e.message}")
        null
    }

    private var isMuted: Boolean = false
    private val scope = CoroutineScope(Dispatchers.Default)

    fun setMuted(muted: Boolean) {
        isMuted = muted
    }

    fun isMuted(): Boolean = isMuted

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
     * Release tone generator resources.
     */
    fun release() {
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
