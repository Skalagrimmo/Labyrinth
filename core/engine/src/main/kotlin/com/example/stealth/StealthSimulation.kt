package com.example.stealth

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.hypot

enum class StealthGuardState {
    PATROLLING,
    INVESTIGATING,
    SEARCHING,
    ALERTED,
    DISABLED
}

data class StealthPlayer(
    val position: StealthPosition,
    val isSneaking: Boolean = false,
    val isInvisible: Boolean = false,
    val isInCover: Boolean = false
)

/** One-shot sound stimulus. Pass newly emitted sounds to the next simulation update. */
data class NoisePulse(
    val position: StealthPosition,
    val radius: Float,
    val intensity: Float = 1f
)

enum class StealthEventType {
    GUARD_STATE_CHANGED,
    PLAYER_DETECTED,
    NOISE_HEARD,
    ICE_ALERT_RELAYED,
    ICE_NET_ESCALATED,
    ICE_NET_DEESCALATED
}

data class StealthEvent(
    val type: StealthEventType,
    val guardId: String,
    val position: StealthPosition,
    val previousState: StealthGuardState? = null,
    val currentState: StealthGuardState? = null,
    /** d8 slice 9: the net-wide alert level carried by the ICE_NET_* events. */
    val netAlertLevel: IceNetAlertLevel? = null
)

/**
 * Mutable runtime agent. All timers are expressed in seconds, not frames.
 * Patrol routes use the same integer cells as the Labyrinth map adapter.
 */
class StealthGuard(
    val id: String,
    position: StealthPosition,
    facingAngleRadians: Float = 0f,
    val patrolRoute: List<GridCell> = emptyList(),
    val viewRange: Float = 5.5f,
    val fieldOfViewRadians: Float = PI.toFloat() / 3f,
    val hearingMultiplier: Float = 1f,
    val detectionRatePerSecond: Float = 60f,
    val suspicionRecoveryPerSecond: Float = 15f,
    val patrolSpeed: Float = 0.75f,
    val investigationSpeed: Float = 1.25f,
    val chaseSpeed: Float = 1.8f,
    val suspicionThreshold: Float = 25f,
    val searchDurationSeconds: Float = 4f
) {
    var position: StealthPosition = position
        internal set
    var facingAngleRadians: Float = facingAngleRadians
        internal set
    var state: StealthGuardState = StealthGuardState.PATROLLING
        internal set
    var suspicion: Float = 0f
        internal set
    var patrolIndex: Int = 0
        internal set
    var lastKnownPlayerPosition: StealthPosition? = null
        internal set
    var investigationTarget: StealthPosition? = null
        internal set
    var searchSecondsRemaining: Float = 0f
        internal set
    /** d8 slice 4: seconds of forced reboot left; counts down inside update() while DISABLED. */
    var disableSecondsRemaining: Float = 0f
        internal set

    internal var cachedPathTarget: GridCell? = null
    internal var cachedPath: List<GridCell>? = null
    internal var cachedPathIndex: Int = 0

    init {
        require(id.isNotBlank()) { "Guard id must not be blank" }
        require(viewRange > 0f)
        require(fieldOfViewRadians > 0f && fieldOfViewRadians <= 2f * PI.toFloat())
        require(hearingMultiplier >= 0f)
        require(detectionRatePerSecond >= 0f)
        require(patrolSpeed >= 0f && investigationSpeed >= 0f && chaseSpeed >= 0f)
        require(searchDurationSeconds >= 0f)
    }

    internal fun clearPath() {
        cachedPathTarget = null
        cachedPath = null
        cachedPathIndex = 0
    }
}

/**
 * Deterministic, rendering-independent stealth simulation for a single map layer.
 *
 * It owns no UI or database state. The caller provides the player's current snapshot,
 * updates it from input, and handles emitted events (HUD alerts, sound, combat, mission logic).
 */
class StealthSimulation(
    private val world: StealthWorld,
    guards: List<StealthGuard>
) {
    val guards: List<StealthGuard> = guards.toList()

    /** d8 slice 9: the floor-wide ICE net alert level and its decay timer. */
    var netAlertLevel: IceNetAlertLevel = IceNetAlertLevel.CALM
        internal set
    private var netAlertDecaySecondsRemaining: Float = 0f

    init {
        require(guards.map { it.id }.distinct().size == guards.size) {
            "Guard ids must be unique within a simulation"
        }
    }

    /**
     * Advances the simulation by deltaTimeSeconds. Large frame gaps are capped at 0.25 s
     * to avoid teleport-like movement after an app pause or a slow frame.
     *
     * d8 slice 3 (ICE coordination): a guard that crosses into ALERTED broadcasts the
     * runner's last known position once, in the same update. Every other unit within
     * the relay radius (scaled by the weather's hearingFactor) converges on the
     * detection spot - see [relayAlerts].
     */
    fun update(
        deltaTimeSeconds: Float,
        player: StealthPlayer,
        noisePulses: List<NoisePulse> = emptyList(),
        weather: WeatherPerception = WeatherPerception.NONE
    ): List<StealthEvent> {
        if (!deltaTimeSeconds.isFinite() || deltaTimeSeconds <= 0f) return emptyList()

        val dt = min(deltaTimeSeconds, MAX_DELTA_SECONDS)
        val alert = IceNetAlertProfile.forLevel(netAlertLevel)
        val events = mutableListOf<StealthEvent>()
        val newlyAlerted = mutableListOf<StealthGuard>()

        for (guard in guards) {
            if (guard.state == StealthGuardState.DISABLED) {
                // d8 slice 4: count down the forced reboot; the unit wakes
                // into a clean PATROLLING slate when the spike expires.
                guard.disableSecondsRemaining = max(0f, guard.disableSecondsRemaining - dt)
                if (guard.disableSecondsRemaining <= 0f) {
                    guard.disableSecondsRemaining = 0f
                    changeState(guard, StealthGuardState.PATROLLING, events)
                }
                continue
            }
            if (guard.position.levelId != world.levelId) continue
            val wasAlerted = guard.state == StealthGuardState.ALERTED

            val canSeePlayer = canSee(guard, player, weather, alert)
            if (canSeePlayer) {
                guard.lastKnownPlayerPosition = player.position
                guard.investigationTarget = player.position

                if (guard.state == StealthGuardState.SEARCHING) {
                    changeState(guard, StealthGuardState.INVESTIGATING, events)
                }

                val effectiveRange = effectiveViewRange(guard, player, weather, alert)
                val distanceFactor =
                    (1f - 0.6f * (guard.position.distanceTo(player.position) / effectiveRange))
                        .coerceIn(0.25f, 1f)
                val stealthFactor =
                    (if (player.isSneaking) 0.45f else 1f) *
                    (if (player.isInCover) 0.55f else 1f)
                guard.suspicion = (guard.suspicion +
                    guard.detectionRatePerSecond * distanceFactor * stealthFactor *
                    weather.detectionFactor * alert.detectionFactor * dt)
                    .coerceIn(0f, 100f)

                if (guard.suspicion >= 100f) {
                    changeState(guard, StealthGuardState.ALERTED, events)
                } else if (
                    guard.state == StealthGuardState.PATROLLING &&
                    guard.suspicion >= guard.suspicionThreshold
                ) {
                    changeState(guard, StealthGuardState.INVESTIGATING, events)
                }
            } else if (guard.state == StealthGuardState.PATROLLING) {
                guard.suspicion =
                    max(0f, guard.suspicion - guard.suspicionRecoveryPerSecond * dt)
            }

            if (!canSeePlayer && guard.state != StealthGuardState.ALERTED) {
                hearNoise(guard, noisePulses, weather, events)
            }

            advanceGuard(guard, player, canSeePlayer, dt, weather, alert, events)
            if (!wasAlerted && guard.state == StealthGuardState.ALERTED) {
                newlyAlerted.add(guard)
            }
        }

        if (newlyAlerted.isNotEmpty()) {
            relayAlerts(newlyAlerted, weather, alert, events)
        }

        updateNetAlert(newlyAlerted, dt, events)

        return events
    }

    /** Call after a wall/door changes state so guards don't reuse stale routes. */
    fun invalidatePaths() {
        guards.forEach { it.clearPath() }
    }

    /**
     * d8 slice 4: ICE neutralization - the runner's overclocked node spike.
     *
     * Forces the identified unit into DISABLED for durationSeconds: while
     * down it cannot see, hear, move, chase or relay the alarm, and every
     * hunt memory (last known position, investigation target, suspicion)
     * is wiped. When the spike expires the unit reboots into PATROLLING
     * with a clean slate. Returns the emitted state-change events, or an
     * empty list when the id is unknown, the duration is not positive, or
     * the unit is already disabled.
     */
    fun disableGuard(guardId: String, durationSeconds: Float): List<StealthEvent> {
        if (!durationSeconds.isFinite() || durationSeconds <= 0f) return emptyList()
        val guard = guards.firstOrNull { it.id == guardId } ?: return emptyList()
        if (guard.state == StealthGuardState.DISABLED) return emptyList()
        guard.suspicion = 0f
        guard.lastKnownPlayerPosition = null
        guard.investigationTarget = null
        guard.searchSecondsRemaining = 0f
        guard.disableSecondsRemaining = durationSeconds
        guard.clearPath()
        val events = mutableListOf<StealthEvent>()
        changeState(guard, StealthGuardState.DISABLED, events)
        return events
    }

    /**
     * Post-combat de-escalation (KOTOR-style memory policy).
     *
     * Called when a concluded combat returns the world to exploration: ALERTED
     * guards drop out of the alert chase but keep the player's last known
     * position and walk to it (INVESTIGATING), sweep the area (SEARCHING),
     * and only then return to their patrol. Guards in other states and
     * DISABLED guards are untouched.
     *
     * Returns the emitted GUARD_STATE_CHANGED events.
     */
    fun deescalateAfterCombat(): List<StealthEvent> {
        val events = mutableListOf<StealthEvent>()
        for (guard in guards) {
            if (guard.state != StealthGuardState.ALERTED) continue
            val target = guard.lastKnownPlayerPosition
            if (target != null) {
                guard.investigationTarget = target
                changeState(guard, StealthGuardState.INVESTIGATING, events)
            } else {
                beginSearch(guard, events)
            }
        }
        return events
    }

    private fun canSee(
        guard: StealthGuard,
        player: StealthPlayer,
        weather: WeatherPerception,
        alert: IceNetAlertProfile
    ): Boolean {
        if (player.isInvisible || player.position.levelId != world.levelId) return false

        val distance = guard.position.distanceTo(player.position)
        val range = effectiveViewRange(guard, player, weather, alert)
        if (distance > range) return false
        if (distance <= 0.05f) return true

        val angleToPlayer = atan2(
            player.position.y - guard.position.y,
            player.position.x - guard.position.x
        )
        val angularDifference = abs(normalizeAngle(angleToPlayer - guard.facingAngleRadians))
        if (angularDifference > guard.fieldOfViewRadians / 2f) return false

        return world.hasLineOfSight(guard.position, player.position)
    }

    private fun effectiveViewRange(
        guard: StealthGuard,
        player: StealthPlayer,
        weather: WeatherPerception,
        alert: IceNetAlertProfile
    ): Float {
        val stanceFactor = (if (player.isSneaking) 0.72f else 1f) *
            (if (player.isInCover) 0.65f else 1f)
        return max(
            0.1f,
            guard.viewRange * stanceFactor * weather.viewRangeFactor * alert.viewRangeFactor
        )
    }

    private fun hearNoise(
        guard: StealthGuard,
        pulses: List<NoisePulse>,
        weather: WeatherPerception,
        events: MutableList<StealthEvent>
    ) {
        for (pulse in pulses) {
            if (pulse.position.levelId != guard.position.levelId) continue
            val effectiveRadius =
                max(0f, pulse.radius) * guard.hearingMultiplier * weather.hearingFactor
            if (guard.position.distanceTo(pulse.position) > effectiveRadius) continue

            guard.lastKnownPlayerPosition = pulse.position
            guard.investigationTarget = pulse.position
            guard.suspicion = max(
                guard.suspicion,
                (40f * pulse.intensity.coerceIn(0f, 2f)).coerceAtMost(85f)
            )
            if (guard.state != StealthGuardState.INVESTIGATING) {
                changeState(guard, StealthGuardState.INVESTIGATING, events)
            }
            events.add(
                StealthEvent(
                    type = StealthEventType.NOISE_HEARD,
                    guardId = guard.id,
                    position = pulse.position,
                    currentState = guard.state
                )
            )
            return
        }
    }

    /**
     * d8 slice 3: ICE coordination - the alert relay.
     *
     * A guard that crossed into ALERTED this update broadcasts the runner's
     * last known position once (edge-triggered, never a continuous magnet).
     * Every other unit on the floor within the relay radius - scaled by the
     * weather's hearingFactor, the same medium that carries noise - receives
     * the detection spot: it adopts it as its investigation target, primes to
     * the coordination suspicion floor and converges. Walls do not block the
     * call (ICE chatter rides the network, not the light), ALERTED and
     * DISABLED units ignore it, and a unit is relayed at most once per update
     * even when several guards raise the alarm in the same tick.
     */
    private fun relayAlerts(
        alerted: List<StealthGuard>,
        weather: WeatherPerception,
        alert: IceNetAlertProfile,
        events: MutableList<StealthEvent>
    ) {
        val relayed = mutableSetOf<String>()
        val effectiveRadius =
            ALERT_COORDINATION_RADIUS_CELLS * weather.hearingFactor * alert.relayFactor
        for (alerter in alerted) {
            val alertPosition = alerter.lastKnownPlayerPosition ?: alerter.position
            for (guard in guards) {
                if (guard === alerter) continue
                if (guard.id in relayed) continue
                if (guard.state == StealthGuardState.DISABLED) continue
                if (guard.state == StealthGuardState.ALERTED) continue
                if (guard.position.levelId != alertPosition.levelId) continue
                if (guard.position.distanceTo(alertPosition) > effectiveRadius) continue

                relayed.add(guard.id)
                val previousState = guard.state
                guard.lastKnownPlayerPosition = alertPosition
                guard.investigationTarget = alertPosition
                guard.suspicion = max(guard.suspicion, ALERT_COORDINATION_SUSPICION)
                if (guard.state != StealthGuardState.INVESTIGATING) {
                    changeState(guard, StealthGuardState.INVESTIGATING, events)
                }
                events.add(
                    StealthEvent(
                        type = StealthEventType.ICE_ALERT_RELAYED,
                        guardId = guard.id,
                        position = alertPosition,
                        previousState = previousState,
                        currentState = guard.state
                    )
                )
            }
        }
    }

    /**
     * d8 slice 9: the ICE net remembers. A positive identification escalates
     * the floor-wide alert (a detection while already HUNTING - or two units
     * alarming in the same update - goes straight to LOCKDOWN); a stirred
     * but unidentified net holds at SUSPICIOUS. Once every unit is back on
     * patrol (or spiked), the level decays one step per quiet period, each
     * transition announced as an event so the HUD and the log can follow.
     */
    private fun updateNetAlert(
        newlyAlerted: List<StealthGuard>,
        dt: Float,
        events: MutableList<StealthEvent>
    ) {
        if (newlyAlerted.isNotEmpty()) {
            val target =
                if (netAlertLevel >= IceNetAlertLevel.HUNTING || newlyAlerted.size >= 2) {
                    IceNetAlertLevel.LOCKDOWN
                } else {
                    IceNetAlertLevel.HUNTING
                }
            if (target != netAlertLevel) {
                changeNetAlertLevel(target, newlyAlerted.first(), events, deescalating = false)
            }
            netAlertDecaySecondsRemaining = NET_ALERT_DECAY_SECONDS
            return
        }
        val stirred = guards.firstOrNull {
            it.state != StealthGuardState.DISABLED && it.state != StealthGuardState.PATROLLING
        }
        if (stirred != null) {
            if (netAlertLevel < IceNetAlertLevel.SUSPICIOUS) {
                changeNetAlertLevel(IceNetAlertLevel.SUSPICIOUS, stirred, events, deescalating = false)
            }
            netAlertDecaySecondsRemaining = NET_ALERT_DECAY_SECONDS
            return
        }
        if (netAlertLevel > IceNetAlertLevel.CALM) {
            netAlertDecaySecondsRemaining -= dt
            if (netAlertDecaySecondsRemaining <= 0f) {
                val lowered = IceNetAlertLevel.entries[netAlertLevel.ordinal - 1]
                changeNetAlertLevel(lowered, guards.firstOrNull(), events, deescalating = true)
            }
        }
    }

    private fun changeNetAlertLevel(
        target: IceNetAlertLevel,
        trigger: StealthGuard?,
        events: MutableList<StealthEvent>,
        deescalating: Boolean
    ) {
        netAlertLevel = target
        netAlertDecaySecondsRemaining = NET_ALERT_DECAY_SECONDS
        events.add(
            StealthEvent(
                type = if (deescalating) {
                    StealthEventType.ICE_NET_DEESCALATED
                } else {
                    StealthEventType.ICE_NET_ESCALATED
                },
                guardId = NET_ALERT_EVENT_SOURCE,
                position = trigger?.lastKnownPlayerPosition
                    ?: trigger?.position
                    ?: StealthPosition(0.5f, 0.5f, world.levelId),
                netAlertLevel = target
            )
        )
    }

    private fun advanceGuard(
        guard: StealthGuard,
        player: StealthPlayer,
        canSeePlayer: Boolean,
        dt: Float,
        weather: WeatherPerception,
        events: MutableList<StealthEvent>
    ) {
        when (guard.state) {
            StealthGuardState.PATROLLING -> advancePatrol(guard, dt, weather, alert)

            StealthGuardState.INVESTIGATING -> {
                val target = guard.investigationTarget ?: guard.lastKnownPlayerPosition
                if (target == null) {
                    beginSearch(guard, events)
                } else if (guard.position.distanceTo(target) <= ARRIVAL_DISTANCE) {
                    beginSearch(guard, events)
                } else if (!moveTowards(
                        guard, target,
                        guard.investigationSpeed * weather.speedFactor * alert.speedFactor, dt
                    )
                ) {
                    beginSearch(guard, events)
                }
            }

            StealthGuardState.ALERTED -> {
                val target = if (canSeePlayer) player.position else guard.lastKnownPlayerPosition
                if (target == null) {
                    beginSearch(guard, events)
                } else if (!canSeePlayer &&
                    guard.position.distanceTo(target) <= ARRIVAL_DISTANCE
                ) {
                    beginSearch(guard, events)
                } else if (!moveTowards(
                        guard, target,
                        guard.chaseSpeed * weather.speedFactor * alert.speedFactor, dt
                    )
                ) {
                    beginSearch(guard, events)
                }
            }

            StealthGuardState.SEARCHING -> {
                guard.searchSecondsRemaining =
                    max(0f, guard.searchSecondsRemaining - dt)
                guard.facingAngleRadians = normalizeAngle(
                    guard.facingAngleRadians + SEARCH_TURN_RATE * dt
                )
                if (guard.searchSecondsRemaining <= 0f) {
                    guard.suspicion = 0f
                    guard.lastKnownPlayerPosition = null
                    guard.investigationTarget = null
                    changeState(guard, StealthGuardState.PATROLLING, events)
                }
            }

            StealthGuardState.DISABLED -> Unit
        }
    }

    private fun advancePatrol(
        guard: StealthGuard,
        dt: Float,
        weather: WeatherPerception,
        alert: IceNetAlertProfile
    ) {
        if (guard.patrolRoute.isEmpty()) {
            guard.facingAngleRadians = normalizeAngle(
                guard.facingAngleRadians + SEARCH_TURN_RATE * 0.25f * dt
            )
            return
        }

        repeat(guard.patrolRoute.size) {
            val waypoint = guard.patrolRoute[guard.patrolIndex % guard.patrolRoute.size]
            if (!world.isWalkable(waypoint)) {
                guard.patrolIndex = (guard.patrolIndex + 1) % guard.patrolRoute.size
                guard.clearPath()
                return@repeat
            }

            val target = waypoint.center()
            if (guard.position.distanceTo(target) <= ARRIVAL_DISTANCE) {
                guard.patrolIndex = (guard.patrolIndex + 1) % guard.patrolRoute.size
                guard.clearPath()
                return
            }
            if (!moveTowards(
                    guard, target,
                    guard.patrolSpeed * weather.speedFactor * alert.speedFactor, dt
                )
            ) {
                guard.patrolIndex = (guard.patrolIndex + 1) % guard.patrolRoute.size
                guard.clearPath()
            }
            return
        }
    }

    /**
     * Moves cell-by-cell along an A* path. It never walks directly through a wall.
     * Returns false if the destination is on another layer or cannot be reached.
     */
    private fun moveTowards(
        guard: StealthGuard,
        target: StealthPosition,
        speed: Float,
        dt: Float
    ): Boolean {
        if (target.levelId != world.levelId) return false
        val targetCell = target.cell()
        if (!world.isWalkable(targetCell)) return false

        val currentCell = guard.position.cell()
        val waypoint: StealthPosition
        val movingAlongPath: Boolean

        if (currentCell == targetCell) {
            guard.clearPath()
            waypoint = target
            movingAlongPath = false
        } else {
            if (guard.cachedPathTarget != targetCell || guard.cachedPath == null ||
                guard.cachedPathIndex >= (guard.cachedPath?.size ?: 0)
            ) {
                guard.cachedPathTarget = targetCell
                guard.cachedPath = world.findPath(currentCell, targetCell) ?: emptyList()
                guard.cachedPathIndex = 0
            }

            val path = guard.cachedPath ?: return false
            val nextCell = path.getOrNull(guard.cachedPathIndex) ?: return false
            waypoint = nextCell.center()
            movingAlongPath = true
        }

        val dx = waypoint.x - guard.position.x
        val dy = waypoint.y - guard.position.y
        val distance = hypot(dx, dy)
        guard.facingAngleRadians = normalizeAngle(atan2(dy, dx))
        if (distance <= 0.001f) return true

        val step = speed * dt
        if (distance <= step) {
            guard.position = waypoint
            if (movingAlongPath) guard.cachedPathIndex++
        } else if (step > 0f) {
            guard.position = StealthPosition(
                x = guard.position.x + dx / distance * step,
                y = guard.position.y + dy / distance * step,
                levelId = guard.position.levelId
            )
        }
        return true
    }

    private fun beginSearch(
        guard: StealthGuard,
        events: MutableList<StealthEvent>
    ) {
        guard.searchSecondsRemaining = guard.searchDurationSeconds
        changeState(guard, StealthGuardState.SEARCHING, events)
    }

    private fun changeState(
        guard: StealthGuard,
        newState: StealthGuardState,
        events: MutableList<StealthEvent>
    ) {
        if (guard.state == newState) return
        val oldState = guard.state
        guard.state = newState
        guard.clearPath()
        events.add(
            StealthEvent(
                type = StealthEventType.GUARD_STATE_CHANGED,
                guardId = guard.id,
                position = guard.position,
                previousState = oldState,
                currentState = newState
            )
        )
        if (newState == StealthGuardState.ALERTED) {
            events.add(
                StealthEvent(
                    type = StealthEventType.PLAYER_DETECTED,
                    guardId = guard.id,
                    position = guard.lastKnownPlayerPosition ?: guard.position,
                    previousState = oldState,
                    currentState = newState
                )
            )
        }
    }

    private fun normalizeAngle(angle: Float): Float =
        atan2(sin(angle), cos(angle))

    private companion object {
        const val MAX_DELTA_SECONDS = 0.25f
        const val ARRIVAL_DISTANCE = 0.22f
        const val SEARCH_TURN_RATE = 1.4f

        /** d8 slice 3: how far (cells) an ALERTED unit's alarm call reaches, scaled by the weather's hearingFactor. */
        const val ALERT_COORDINATION_RADIUS_CELLS: Float = 8f

        /** d8 slice 3: suspicion floor a coordinated unit starts the convergence with (a full noise pulse's worth). */
        const val ALERT_COORDINATION_SUSPICION: Float = 40f

        /** d8 slice 9: seconds of quiet (every unit back on patrol) per net-alert de-escalation step. */
        const val NET_ALERT_DECAY_SECONDS: Float = 12f

        /** d8 slice 9: pseudo-id the ICE_NET_* events report (no single unit owns them). */
        const val NET_ALERT_EVENT_SOURCE: String = "ice_net"
    }
}
