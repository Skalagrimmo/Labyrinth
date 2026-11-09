package com.example.ui

/**
 * d8 slice 6: KOTOR combat openings - how the fight began decides the
 * hostile's starting combat status (see CombatManager.triggerCombat).
 *
 * The classification lives at the call site that knows how the engagement
 * started: the stealth guard-detection callback always reports CAUGHT (the
 * ICE traced the runner before the first blow was struck), player-initiated
 * node strikes report AMBUSHED while the silent protocol is engaged on a
 * calm floor, and every other entry point (phantom echo re-arm, threat-scan
 * adjacency, SVDAG intercept) reports STANDARD.
 */
enum class CombatOpening {
    /** Head-on engagement: the hostile opens the fight with a flat status. */
    STANDARD,

    /** Shadow strike: the hostile is caught mid-patrol and opens STUNNED (surprise round). */
    AMBUSHED,

    /** Trace lock: the ICE traced the runner first and opens OVERCLOCKED (protocols pre-loaded). */
    CAUGHT
}
