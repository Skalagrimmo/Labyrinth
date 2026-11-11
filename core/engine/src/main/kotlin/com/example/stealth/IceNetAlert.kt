package com.example.stealth

/**
 * d8 slice 9: the floor-wide alert level of the ICE net.
 *
 * CALM       - business as usual: units patrol their routes.
 * SUSPICIOUS - something stirred: a unit is investigating or sweeping.
 * HUNTING    - a runner was positively identified: perception sharpens.
 * LOCKDOWN   - repeated detections: the whole net overdrives.
 */
enum class IceNetAlertLevel {
    CALM,
    SUSPICIOUS,
    HUNTING,
    LOCKDOWN
}

/**
 * Perception multipliers the net-wide alert level applies on top of the
 * weather profile (d8 slice 1). SUSPICIOUS is deliberately a pure marker:
 * the net is already stirred by definition, and boosting detection there
 * would double-punish the runner for a single noise pulse.
 */
data class IceNetAlertProfile(
    val detectionFactor: Float,
    val viewRangeFactor: Float,
    val speedFactor: Float,
    val relayFactor: Float
) {
    companion object {
        val CALM = IceNetAlertProfile(
            detectionFactor = 1f, viewRangeFactor = 1f, speedFactor = 1f, relayFactor = 1f
        )
        val SUSPICIOUS = IceNetAlertProfile(
            detectionFactor = 1f, viewRangeFactor = 1f, speedFactor = 1f, relayFactor = 1f
        )
        val HUNTING = IceNetAlertProfile(
            detectionFactor = 1.5f, viewRangeFactor = 1.15f, speedFactor = 1f, relayFactor = 1f
        )
        val LOCKDOWN = IceNetAlertProfile(
            detectionFactor = 2f, viewRangeFactor = 1.3f, speedFactor = 1.25f, relayFactor = 1.25f
        )

        fun forLevel(level: IceNetAlertLevel): IceNetAlertProfile = when (level) {
            IceNetAlertLevel.CALM -> CALM
            IceNetAlertLevel.SUSPICIOUS -> SUSPICIOUS
            IceNetAlertLevel.HUNTING -> HUNTING
            IceNetAlertLevel.LOCKDOWN -> LOCKDOWN
        }
    }
}
