package com.example.data.svdag

import kotlin.math.abs

/**
 * FPE — §6: Probabilistic collapse / decoherence.
 *
 * Port of the "probabilistic rendering" section of the Fractal Point Engine spec.
 *
 * Traditional rendering stores and draws everything. Here only the *observed* is
 * materialised: every voxel has a [VoxelState.probability] of existing. When observed,
 * a *deterministic* per-voxel pseudo-random value (seeded by coordinates + a stable
 * phase) is compared against that probability; equal-or-above materialises the voxel,
 * below leaves it absent — and the ray/camera simply continues (§6.2).
 *
 * Determinism is the whole point: noise must be reproducible frame-to-frame when the
 * observer hasn't moved (no unnecessary flicker), but *changes* with the observer's
 * phase (camera position). This is why the noise seeds include a [phase], and why we
 * never use a true RNG per frame.
 *
 * The field only *reads* [SvdagWorldState] — it never mutates it. State (weight,
 * damage, ...) stays the single source of truth; this layer is purely the
 * "is it materialised right now" decision, i.e. §6's collapse.
 */
class FpeProbabilityField(
    private val world: SvdagWorldState,
    private val seed: Long = 0xC0FFEE5EEDL
) {

    /**
     * Deterministic value in [0, 1) for a voxel, derived only from its coordinates,
     * the field seed, and the observer phase. Same inputs -> same output.
     */
    fun noise(x: Int, y: Int, z: Int, phase: Long = 0L): Float {
        val h = mix64(
            mix64(seed) xor
                (x.toLong() * 0x9E3779B97F4A7C15u.toLong()) xor
                (y.toLong() * 0xBF58476D1CE4E5B9u.toLong()) xor
                (z.toLong() * 0x94D049BB133111EBu.toLong()) xor
                (phase * 0x27D4EB2F165667C5u.toLong())
        )
        // fold hash to [0,1) — top 52 bits are well-mixed
        return (h ushr 12).toDouble().let { it / 4503599627370496.0 }.toFloat()
    }

    /**
     * §6.2 materialisation collapse. Returns true when the observed voxel exists this
     * phase (noise below its [VoxelState.probability]). Already-collapsed voxels are
     * always absent.
     */
    fun isMaterialised(x: Int, y: Int, z: Int, phase: Long = 0L): Boolean {
        val st = world.stateAt(x, y, z)
        if (st.collapsed) return false
        return noise(x, y, z, phase) < st.probability
    }

    /**
     * §6.3 decoherence: an observed voxel's *exact* state — only meaningful once it is
     * materialised. For un-materialised voxels returns null (exact geometry is not
     * computed; "forgotten" until the next collapse).
     */
    fun exactState(x: Int, y: Int, z: Int, phase: Long = 0L): VoxelState? =
        if (isMaterialised(x, y, z, phase)) world.stateAt(x, y, z) else null

    /** A camera looking from the given point: voxels beyond this radius are not observed. */
    fun materialisedInBox(
        x0: Int, x1: Int,
        y0: Int, y1: Int,
        z0: Int, z1: Int,
        phase: Long = 0L
    ): List<Point3i> {
        val out = ArrayList<Point3i>()
        for (x in x0..x1) {
            for (y in y0..y1) {
                for (z in z0..z1) {
                    if (isMaterialised(x, y, z, phase)) out.add(Point3i(x, y, z))
                }
            }
        }
        return out
    }

    /** Iterating a whole region materialised-voxel set as a count (cheap telemetry). */
    fun countMaterialisedInBox(
        x0: Int, x1: Int,
        y0: Int, y1: Int,
        z0: Int, z1: Int,
        phase: Long = 0L
    ): Int {
        var n = 0
        for (x in x0..x1) {
            for (y in y0..y1) {
                for (z in z0..z1) {
                    if (isMaterialised(x, y, z, phase)) n++
                }
            }
        }
        return n
    }

    private fun mix64(v: Long): Long {
        var z = v
        z = (z xor (z ushr 30)) * 0xBF58476D1CE4E5B9u.toLong()
        z = (z xor (z ushr 27)) * 0x94D049BB133111EBu.toLong()
        return z xor (z ushr 31)
    }
}
