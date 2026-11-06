package com.example.audio

/**
 * Pure-JVM audio port (hexagonal architecture).
 *
 * Game managers depend on this interface instead of the concrete Android
 * CyberSoundEffectsManager synth, so gameplay modules stay JVM-only and
 * unit-testable. The app module wires the real implementation through an
 * adapter; JVM tests can substitute [SilentAudioPlayer].
 */
interface GameAudioPlayer {
    fun playCombatHitSound()
    fun playCombatCritSound()
    fun playHackingErrorSound()
    fun playHackingSuccessSound()
    fun playNodeBreachSound()
    fun playBufferShiftSound()
    fun playTerminalKeyPressSound()
    fun playTerminalCommandSound()
    fun playLootCollectionSound()
    fun playCyberwareInstallSound()
    fun playStepSound()
    fun playScannerPingSound()
    fun playScannerDetectionSound(itemCount: Int, hasSecrets: Boolean = false, hasBypass: Boolean = false)
}

/**
 * No-op [GameAudioPlayer] for previews, JVM unit tests and headless environments.
 */
object SilentAudioPlayer : GameAudioPlayer {
    override fun playCombatHitSound() {}
    override fun playCombatCritSound() {}
    override fun playHackingErrorSound() {}
    override fun playHackingSuccessSound() {}
    override fun playNodeBreachSound() {}
    override fun playBufferShiftSound() {}
    override fun playTerminalKeyPressSound() {}
    override fun playTerminalCommandSound() {}
    override fun playLootCollectionSound() {}
    override fun playCyberwareInstallSound() {}
    override fun playStepSound() {}
    override fun playScannerPingSound() {}
    override fun playScannerDetectionSound(itemCount: Int, hasSecrets: Boolean, hasBypass: Boolean) {}
}
