package com.example.data

import com.example.stealth.LabyrinthStealthSession
import com.example.stealth.NoisePulse
import com.example.stealth.StealthEvent
import com.example.stealth.StealthEventType
import com.example.stealth.StealthSessionFrame
import kotlin.random.Random

/**
 * Modes of the real-time game session. This is the world-level state machine
 * that connects the real-time stealth simulation with the KOTOR-style
 * real-time-with-pause combat scheduler on one shared world clock.
 */
enum class GameSessionMode {
    EXPLORATION,
    COMBAT,
    COMBAT_ENDED
}

/** Result of one shared-clock tick. */
data class SessionTickResult(
    val mode: GameSessionMode,
    val stealthFrame: StealthSessionFrame?,
    val combatEvents: List<CombatRoundScheduler.RoundEvent>,
    val enteredCombat: Boolean,
    val detection: StealthEvent?,
    val combatEnded: Boolean
)

/**
 * Single shared world clock for stealth exploration and KOTOR-style combat.
 *
 * EXPLORATION advances the stealth simulation. When a guard raises the alarm
 * (a PLAYER_DETECTED event from the stealth simulation) the session
 * transitions to COMBAT and starts the combat round scheduler. By default the
 * combat clock starts PAUSED so the player can assess the situation and queue
 * actions, exactly like the KOTOR flow.
 *
 * COMBAT advances only the combat scheduler. When the underlying engine state
 * reports the encounter over, the session moves to COMBAT_ENDED and the caller
 * decides whether to return to exploration.
 *
 * Transition rule: combat starts on a new PLAYER_DETECTED event, not merely on
 * a lingering ALERTED guard, so a concluded encounter can flow back into
 * exploration without instantly retriggering combat. While the session is
 * paused no subsystem advances.
 */
class RealTimeGameSession(
    private val stealthSession: LabyrinthStealthSession,
    private val combatStartState: () -> CombatEngineState = { CombatEngineState() },
    private val pauseOnCombatEntry: Boolean = true
) {
    var mode: GameSessionMode = GameSessionMode.EXPLORATION
        private set
    var combat: CombatRoundScheduler.SchedulerState? = null
        private set
    var isPaused: Boolean = false
        private set

    private var combatEventCursor: Int = 0

    fun setPaused(paused: Boolean) {
        isPaused = paused
    }

    /**
     * Advances the shared world clock by deltaMillis. At most one combat round
     * resolves per tick (scheduler rule); the stealth simulation internally
     * caps large frame deltas.
     */
    fun tick(
        deltaMillis: Long,
        gridX: Int,
        gridY: Int,
        isSneaking: Boolean = false,
        isInvisible: Boolean = false,
        isInCover: Boolean = false,
        noisePulses: List<NoisePulse> = emptyList(),
        random: Random = Random.Default
    ): SessionTickResult {
        if (isPaused || deltaMillis <= 0L) {
            return unchangedResult()
        }

        if (mode == GameSessionMode.EXPLORATION) {
            val frame = stealthSession.update(
                deltaTimeSeconds = deltaMillis / 1000f,
                gridX = gridX,
                gridY = gridY,
                isSneaking = isSneaking,
                isInvisible = isInvisible,
                isInCover = isInCover,
                noisePulses = noisePulses
            )
            val detection = frame.events.firstOrNull { it.type == StealthEventType.PLAYER_DETECTED }
            if (detection != null) {
                return enterCombat(detection, frame)
            }
            return SessionTickResult(
                mode = mode,
                stealthFrame = frame,
                combatEvents = emptyList(),
                enteredCombat = false,
                detection = null,
                combatEnded = false
            )
        }

        if (mode == GameSessionMode.COMBAT) {
            val current = combat ?: return unchangedResult()
            val next = CombatRoundScheduler.tick(current, deltaMillis, random)
            combat = next
            val newEvents = next.events.drop(combatEventCursor)
            combatEventCursor = next.events.size
            val ended = next.engineState.isCombatOver
            if (ended) {
                mode = GameSessionMode.COMBAT_ENDED
            }
            return SessionTickResult(
                mode = mode,
                stealthFrame = null,
                combatEvents = newEvents,
                enteredCombat = false,
                detection = null,
                combatEnded = ended
            )
        }

        // COMBAT_ENDED: the world stands still until the caller decides.
        return unchangedResult()
    }

    /**
     * Returns to exploration after a concluded encounter. KOTOR-style
     * post-combat policy: ALERTED guards de-escalate to INVESTIGATING/
     * SEARCHING while keeping the player's last known position, so the world
     * resumes hot, but a new combat still requires a fresh PLAYER_DETECTED
     * event rather than a lingering guard state.
     *
     * Returns the stealth frame produced by the de-escalation (guard state
     * changes for the HUD/renderer), or null when no combat had concluded.
     */
    fun returnToExploration(): StealthSessionFrame? {
        if (mode == GameSessionMode.COMBAT_ENDED) {
            mode = GameSessionMode.EXPLORATION
            combat = null
            combatEventCursor = 0
            return stealthSession.deescalateAfterCombat()
        }
        return null
    }

    /** Queues a combat action while combat is active (works while paused). */
    fun enqueueCombatAction(action: PlayerCombatAction): Boolean {
        val current = combat ?: return false
        combat = CombatRoundScheduler.enqueue(current, action)
        return true
    }

    /** Resumes the combat clock (the player leaves the entry pause). */
    fun resumeCombat(): Boolean {
        val current = combat ?: return false
        val next = CombatRoundScheduler.resume(current)
        val changed = next !== current
        combat = next
        return changed
    }

    private fun enterCombat(detection: StealthEvent, frame: StealthSessionFrame): SessionTickResult {
        mode = GameSessionMode.COMBAT
        var scheduler = CombatRoundScheduler.start(combatStartState())
        if (pauseOnCombatEntry) {
            scheduler = CombatRoundScheduler.pause(scheduler)
        }
        combat = scheduler
        combatEventCursor = 0
        return SessionTickResult(
            mode = mode,
            stealthFrame = frame,
            combatEvents = emptyList(),
            enteredCombat = true,
            detection = detection,
            combatEnded = false
        )
    }

    private fun unchangedResult(): SessionTickResult = SessionTickResult(
        mode = mode,
        stealthFrame = null,
        combatEvents = emptyList(),
        enteredCombat = false,
        detection = null,
        combatEnded = false
    )
}
