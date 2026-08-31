package com.example.data.svdag

/**
 * FPE — §15: Eclectic render-language selector.
 *
 * One SVDAG structure, three visual "languages" chosen per node by its *state*, not by
 * randomness. This is the mapping between [VoxelState] and how the node should be drawn
 * (SOLID polygon / point-cloud / ASCII-glyph decay), plus the "gone" state.
 *
 * Faithful to the spec's rule: mode choice is a pure function of `weight` (density) and
 * `damage` (structural integrity), and [VoxelState.probability] drives *fading*, not the
 * language itself — so the mode is predictable for the player, not aesthetic noise.
 */
enum class FpeRenderMode {
    /** Void / collapsed — not drawn. */
    VOID,

    /** Stable, dense, low damage — cheap solid geometry (polygon language). */
    SOLID,

    /** Mid / unstable state — dissolves into a particle/point-cloud language. */
    POINT_CLOUD,

    /** Low weight or high damage — the object "decays into raw data" (ASCII glyphs). */
    ASCII_DECAY
}

/**
 * Pure, deterministic classification of a [VoxelState] into an [FpeRenderMode].
 *
 * Thresholds mirror the prototype's weight/damage split and are constant so the mapping
 * is stable and unit-testable.
 */
object FpeRenderStylist {

    /** weight at or above this, with decent integrity, reads as robust geometry. */
    const val SOLID_WEIGHT = 0.65f

    /** integrity (1 - damage fraction) above which a wall still reads solid. */
    const val SOLID_INTEGRITY = 0.75f

    /** weight below (or integrity below) this reads as collapsed-into-data decay. */
    const val DECAY_WEIGHT = 0.25f
    const val DECAY_INTEGRITY = 0.25f

    /**
     * §15: modes are a function of weight/damage, never chance.
     *
     *  - collapsed / empty              -> VOID
     *  - high weight AND high integrity -> SOLID
     *  - low weight OR low integrity    -> ASCII_DECAY
     *  - otherwise (unstable middle)    -> POINT_CLOUD
     */
    fun modeFor(state: VoxelState): FpeRenderMode {
        if (state.collapsed || state.weight <= 0f) return FpeRenderMode.VOID
        if (state.weight >= SOLID_WEIGHT && state.integrity >= SOLID_INTEGRITY) {
            return FpeRenderMode.SOLID
        }
        if (state.weight < DECAY_WEIGHT || state.integrity < DECAY_INTEGRITY) {
            return FpeRenderMode.ASCII_DECAY
        }
        return FpeRenderMode.POINT_CLOUD
    }

    /**
     * Probabilistic opacity (how strongly the node contributes this frame), driven by
     * [VoxelState.probability] and a deterministic collapse decision supplied by the
     * caller (e.g. [FpeProbabilityField.isMaterialised]). Returns alpha in 0..1 where
     * 0 means "not materialised here".
     */
    fun opacityFor(state: VoxelState, materialised: Boolean): Float {
        if (!materialised || state.collapsed) return 0f
        // Higher weight = brighter/opaque; decayed nodes fade toward the glyph language.
        return (0.35f + 0.65f * state.weight).coerceIn(0f, 1f)
    }

    /**
     * Colour-language hint for the renderer: whether this node should use the "decay"
     * palette (glyph green) instead of the material's primary colour.
     */
    fun isDecayTone(mode: FpeRenderMode): Boolean = mode == FpeRenderMode.ASCII_DECAY
}
