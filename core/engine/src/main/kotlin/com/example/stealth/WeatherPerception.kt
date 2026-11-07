package com.example.stealth

import com.example.core.model.CyberWeather

/**
 * Environmental perception modifiers for the stealth simulation
 * (d8 slice 1: weather -> ICE perception binding).
 *
 * Each CyberWeather maps to a deterministic set of factors that scale the
 * shared simulation only - no per-guard overrides, no randomness. The same
 * weather always produces the same factors, so seed determinism is preserved.
 *
 *  - viewRangeFactor scales how far a guard can see the player;
 *  - detectionFactor scales how fast suspicion builds while seen;
 *  - hearingFactor  scales the effective radius of heard noise pulses;
 *  - speedFactor     scales patrol/investigation/chase movement speed.
 */
data class WeatherPerception(
    val viewRangeFactor: Float = 1f,
    val detectionFactor: Float = 1f,
    val hearingFactor: Float = 1f,
    val speedFactor: Float = 1f
) {
    companion object {
        /** Baseline perception: identical to the pre-weather simulation. */
        val NONE = WeatherPerception()

        fun forWeather(weather: CyberWeather): WeatherPerception = when (weather) {
            // Clear bandwidth and memory fragmentation leave ICE perception
            // alone; fragmentation already mutates the maze itself.
            CyberWeather.CLEAR,
            CyberWeather.FRAGMENTATION -> NONE

            // Gibsonian data storm: telemetry static blinds optics - shorter
            // sight lines, slower suspicion. A window to cross guarded halls
            // (movement itself is already scrambled by ExplorationMovementRules).
            CyberWeather.DATA_STORM -> WeatherPerception(
                viewRangeFactor = 0.75f,
                detectionFactor = 0.9f
            )

            // Apex firewall surge: every perception channel cranked up.
            CyberWeather.APEX_STORM -> WeatherPerception(
                viewRangeFactor = 1.2f,
                detectionFactor = 1.25f,
                hearingFactor = 1.15f,
                speedFactor = 1.1f
            )

            // Ghost protocol: the runner phases out of spectrum (radar
            // sweeps are already blanked) - harder to see and to hear.
            CyberWeather.GHOST_PROTOCOL -> WeatherPerception(
                viewRangeFactor = 0.7f,
                detectionFactor = 0.65f,
                hearingFactor = 0.85f
            )

            // Frozen sector: sluggish ICE - slower to hunt, duller sensors.
            CyberWeather.COLD_SPOT -> WeatherPerception(
                detectionFactor = 0.85f,
                hearingFactor = 0.9f,
                speedFactor = 0.75f
            )

            // Overheated sub-grid: overclocked ICE moves and reacts faster.
            CyberWeather.HOT_NODE -> WeatherPerception(
                detectionFactor = 1.1f,
                speedFactor = 1.15f
            )

            // Spectral echoes: bleeding telemetry streams make ICE hear every
            // whisper; LabyrinthStealthSession also spawns phantom echo pulses.
            CyberWeather.ECHOES -> WeatherPerception(
                hearingFactor = 1.25f
            )
        }
    }
}
