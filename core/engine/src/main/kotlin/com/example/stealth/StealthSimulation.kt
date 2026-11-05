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
    NOISE_HEARD
}

data class StealthEvent(
    val type: StealthEventType,
    val guardId: String,
    val position: StealthPosition,
    val previousState: StealthGuardState? = null,
    val currentState: StealthGuardState? = null
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

    init {
        require(guards.map { it.id }.distinct().size == guards.size) {
            "Guard ids must be unique within a simulation"
        }
    }

    /**
     * Advances the simulation by deltaTimeSeconds. Large frame gaps are capped at 0.25 s
     * to avoid teleport-like movement after an app pause or a slow frame.
     */
    fun update(
        deltaTimeSeconds: Float,
        player: StealthPlayer,
        noisePulses: List<NoisePulse> = emptyList()
    ): List<StealthEvent> {
        if (!deltaTimeSeconds.isFinite() || deltaTimeSeconds <= 0f) return emptyList()

        val dt = min(deltaTimeSeconds, MAX_DELTA_SECONDS)
        val events = mutableListOf<StealthEvent>()

        for (guard in guards) {
            if (guard.state == StealthGuardState.DISABLED) continue
            if (guard.position.levelId != world.levelId) continue

            val canSeePlayer = canSee(guard, player)
            if (canSeePlayer) {
                guard.lastKnownPlayerPosition = player.position
                guard.investigationTarget = player.position

                if (guard.state == StealthGuardState.SEARCHING) {
                    changeState(guard, StealthGuardState.INVESTIGATING, events)
                }

                val effectiveRange = effectiveViewRange(guard, player)
                val distanceFactor =
                    (1f - 0.6f * (guard.position.distanceTo(player.position) / effectiveRange))
                        .coerceIn(0.25f, 1f)
                val stealthFactor =
                    (if (player.isSneaking) 0.45f else 1f) *
                    (if (player.isInCover) 0.55f else 1f)
                guard.suspicion = (guard.suspicion +
                    guard.detectionRatePerSecond * distanceFactor * stealthFactor * dt)
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
                hearNoise(guard, noisePulses, events)
            }

            advanceGuard(guard, player, canSeePlayer, dt, events)
        }

        return events
    }

    /** Call after a wall/door changes state so guards don't reuse stale routes. */
    fun invalidatePaths() {
        guards.forEach { it.clearPath() }
    }

    private fun canSee(guard: StealthGuard, player: StealthPlayer): Boolean {
        if (player.isInvisible || player.position.levelId != world.levelId) return false

        val distance = guard.position.distanceTo(player.position)
        val range = effectiveViewRange(guard, player)
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

    private fun effectiveViewRange(guard: StealthGuard, player: StealthPlayer): Float {
        val stanceFactor = (if (player.isSneaking) 0.72f else 1f) *
            (if (player.isInCover) 0.65f else 1f)
        return max(0.1f, guard.viewRange * stanceFactor)
    }

    private fun hearNoise(
        guard: StealthGuard,
        pulses: List<NoisePulse>,
        events: MutableList<StealthEvent>
    ) {
        for (pulse in pulses) {
            if (pulse.position.levelId != guard.position.levelId) continue
            val effectiveRadius = max(0f, pulse.radius) * guard.hearingMultiplier
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

    private fun advanceGuard(
        guard: StealthGuard,
        player: StealthPlayer,
        canSeePlayer: Boolean,
        dt: Float,
        events: MutableList<StealthEvent>
    ) {
        when (guard.state) {
            StealthGuardState.PATROLLING -> advancePatrol(guard, dt)

            StealthGuardState.INVESTIGATING -> {
                val target = guard.investigationTarget ?: guard.lastKnownPlayerPosition
                if (target == null) {
                    beginSearch(guard, events)
                } else if (guard.position.distanceTo(target) <= ARRIVAL_DISTANCE) {
                    beginSearch(guard, events)
                } else if (!moveTowards(guard, target, guard.investigationSpeed, dt)) {
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
                } else if (!moveTowards(guard, target, guard.chaseSpeed, dt)) {
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

    private fun advancePatrol(guard: StealthGuard, dt: Float) {
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
            if (!moveTowards(guard, target, guard.patrolSpeed, dt)) {
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
    }
}
