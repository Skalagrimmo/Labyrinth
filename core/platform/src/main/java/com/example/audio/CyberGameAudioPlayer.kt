package com.example.audio

/**
 * App-side adapter (hexagonal port/adapter): bridges the pure-JVM
 * [GameAudioPlayer] port used by game managers to the Android
 * [CyberSoundEffectsManager] procedural synth and SoundPool engine.
 */
class CyberGameAudioPlayer(
    private val sfx: CyberSoundEffectsManager
) : GameAudioPlayer {
    override fun playCombatHitSound() = sfx.playCombatHitSound()
    override fun playCombatCritSound() = sfx.playCombatCritSound()
    override fun playHackingErrorSound() = sfx.playHackingErrorSound()
    override fun playHackingSuccessSound() = sfx.playHackingSuccessSound()
    override fun playNodeBreachSound() = sfx.playNodeBreachSound()
    override fun playBufferShiftSound() = sfx.playBufferShiftSound()
    override fun playTerminalKeyPressSound() = sfx.playTerminalKeyPressSound()
    override fun playTerminalCommandSound() = sfx.playTerminalCommandSound()
    override fun playLootCollectionSound() = sfx.playLootCollectionSound()
    override fun playCyberwareInstallSound() = sfx.playCyberwareInstallSound()
    override fun playStepSound() = sfx.playStepSound()
    override fun playScannerPingSound() = sfx.playScannerPingSound()
    override fun playScannerDetectionSound(itemCount: Int, hasSecrets: Boolean, hasBypass: Boolean) =
        sfx.playScannerDetectionSound(itemCount, hasSecrets, hasBypass)
}
