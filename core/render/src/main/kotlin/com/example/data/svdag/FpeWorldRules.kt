package com.example.data.svdag

import kotlin.math.max

/**
 * FPE (Fractal Point Engine) — World Rules / World State layer.
 *
 * Port of the "Fractal Point Engine" spec (Quantum.md) §2, §4, §5, §7 into Kotlin,
 * expressed as a thin, testable layer over the existing [SparseVoxelDag].
 *
 * The SVDAG stays purely topological (structure + material, deduplicated). Per-voxel
 * *state* — weight, jitter, bleed, probability, damage — lives in a separate sparse
 * overlay (see [SvdagWorldState]). This honours §7's rule "store the formula + seed,
 * not the result": mutating world state never re-touches the DAG, so DAG dedup is
 * never broken, and the "universe" (World Rules) stays immutable while only the
 * local, mutable World State changes (§2).
 */

/**
 * §5: World Rules — the immutable table of laws. A voxel is bound to exactly one
 * formula (its "formula_id"); formulas are fixed for a session and are not mutable
 * by the game or the player.
 */
enum class FormulaId(
    val id: Int,
    val displayName: String
) {
    VOID(0, "Void"),
    SOLID(1, "Solid"),
    FLUID(2, "Fluid"),
    GAS(3, "Gas"),
    FIRE(4, "Fire"),
    HAND_DRAWN(5, "Hand-Drawn Line"),
    RUST(6, "Rust"),
    WET(7, "Wet"),
    DAMAGED(8, "Damaged"),
    PLAYER_AGENT(9, "Player Agent");

    companion object {
        val VALUES = entries.toTypedArray()

        fun fromId(id: Int): FormulaId = VALUES.getOrElse(id) { VOID }
    }
}

/**
 * §5: a VoxelType is bound to a default World Rule (the formula its origin material
 * "belongs" to). Solid/interactive matter maps to SOLID; decay-prone materials map
 * to the formula that best describes their behaviour.
 */
fun VoxelType.defaultFormula(): FormulaId = when (this) {
    VoxelType.EMPTY -> FormulaId.VOID
    VoxelType.SOLID_WALL -> FormulaId.SOLID
    VoxelType.PATH -> FormulaId.GAS
    VoxelType.DATA_CORE -> FormulaId.HAND_DRAWN
    VoxelType.BLACK_ICE -> FormulaId.FIRE
    VoxelType.ENCRYPTED_PORTAL -> FormulaId.FLUID
    VoxelType.SAFE_ZONE -> FormulaId.WET
    VoxelType.LOOT_CACHE -> FormulaId.SOLID
    VoxelType.GRAND_HALL -> FormulaId.SOLID
    VoxelType.STAIRS -> FormulaId.SOLID
    VoxelType.GRAVITY_SLOPE -> FormulaId.GAS
    VoxelType.ELEVATOR -> FormulaId.FLUID
    VoxelType.VENT_TUNNEL -> FormulaId.GAS
    VoxelType.ELEVATED_BALCONY -> FormulaId.SOLID
    VoxelType.SECRET_WALL -> FormulaId.HAND_DRAWN
    VoxelType.HACKABLE_TERMINAL -> FormulaId.DAMAGED
    VoxelType.TERMINAL_DOOR -> FormulaId.DAMAGED
    VoxelType.SCAN_CACHE -> FormulaId.SOLID
    VoxelType.ALTERNATIVE_VENT -> FormulaId.GAS
    VoxelType.ICE_PATROL -> FormulaId.FIRE
}

/**
 * §4: World State — the dynamic, per-voxel state of matter. This is the only thing
 * the player (or the game) actually mutates. All fields are normalised to 0..1 where
 * meaningful (except [damageMask], which is an integer bitmask of structural integrity).
 *
 * The four float fields mirror the FPE prototype's `weight` / `jitter` / `bleed` /
 * `probability`; [damageMask] is a richer integrity field than the spec's single u8 so
 * that persistent, fine-grained destruction (§2) is representable.
 */
data class VoxelState(
    val weight: Float = 1f,
    val jitter: Float = 0f,
    val bleed: Float = 0f,
    val probability: Float = 1f,
    val formulaId: FormulaId = FormulaId.VOID,
    val damageMask: Int = 0
) {
    init {
        require(weight in 0f..1f) { "weight must be in 0..1, was $weight" }
        require(probability in 0f..1f) { "probability must be in 0..1, was $probability" }
        require(damageMask in 0..0xFF) { "damageMask must fit in 8 bits, was $damageMask" }
    }

    /** True when the voxel has collapsed out of existence (probabilistic render, §6). */
    val collapsed: Boolean get() = probability <= 0f

    /** Structural fraction remaining: 1.0 = pristine, 0.0 = destroyed. */
    val integrity: Float get() = 1f - (popCount(damageMask) / 8f)

    companion object {
        private fun popCount(v: Int): Int = Integer.bitCount(v and 0xFF)
    }
}

/** Default VoxelState for an origin material (weight/probability from material). */
fun VoxelType.defaultState(): VoxelState = VoxelState(
    weight = if (this == VoxelType.EMPTY) 0f else 1f,
    probability = if (this == VoxelType.EMPTY) 0f else 1f,
    formulaId = defaultFormula()
)

/** Component-wise identity for a single voxel byte offset. */
@JvmInline
value class Point3i(val packed: Long) {
    constructor(x: Int, y: Int, z: Int) : this(pack(x, y, z))

    val x: Int get() = (packed and 0xFFFF).toInt()
    val y: Int get() = ((packed shr 16) and 0xFFFF).toInt()
    val z: Int get() = ((packed shr 32) and 0xFFFF).toInt()

    override fun toString(): String = "($x, $y, $z)"

    companion object {
        fun pack(x: Int, y: Int, z: Int): Long =
            (z.toLong() shl 32) or (y.toLong() shl 16) or (x.toLong() and 0xFFFF)
    }
}

/**
 * §2 + §7: the mutable World State overlay on top of a [SparseVoxelDag].
 *
 * - Reads fall through to the DAG's material and are materialised procedurally from
 *   [VoxelType.defaultState] when no explicit override exists (§7: derive, don't store).
 * - Writes only mutate this sparse map, never the DAG — so DAG structure/dedup is
 *   untouched by player/game damage (§7, §17).
 *
 * This is the single source of truth for dynamic per-voxel state; it is kept shallow
 * (only explicitly-changed voxels) to honour "data minimal, variation computed".
 */
class SvdagWorldState(
    private val dag: SparseVoxelDag
) {
    private val overrides = HashMap<Point3i, VoxelState>()

    val size: Int get() = overrides.size

    fun stateAt(x: Int, y: Int, z: Int): VoxelState =
        overrides[Point3i(x, y, z)] ?: dag.getVoxel(x, y, z).defaultState()

    fun override(x: Int, y: Int, z: Int, state: VoxelState) {
        overrides[Point3i(x, y, z)] = state
    }

    /** Apply a transformation to the current state of one voxel (create-on-write). */
    fun update(x: Int, y: Int, z: Int, transform: (VoxelState) -> VoxelState) {
        val key = Point3i(x, y, z)
        val current = overrides[key] ?: dag.getVoxel(x, y, z).defaultState()
        overrides[key] = transform(current)
    }

    fun clearOverride(x: Int, y: Int, z: Int) {
        overrides.remove(Point3i(x, y, z))
    }
}

/**
 * §5 + §8: World Rules engine — applies each formula's law to World State.
 *
 * This is deliberately a pure, side-effect-light, deterministic function so it is easy
 * to unit test and can later be mapped onto a GPU compute pass (in Kotlin via GLES).
 * The rules here never touch the DAG; they only transform [VoxelState].
 */
object WorldRules {

    /**
     * Apply a single "tick" to one voxel's state using its current formula, given the
     * total number of solid neighbours (the local context the law may react to).
     *
     * @return the transformed state, and the "impulse" this voxel emits to neighbours
     *         (used by FLUID/FIRE/GAS to propagate, §5).
     */
    fun tick(
        current: VoxelState,
        solidNeighbours: Int
    ): Pair<VoxelState, Float> = when (current.formulaId) {
        FormulaId.VOID -> Pair(current, 0f)
        FormulaId.SOLID -> Pair(current, 0f)
        FormulaId.FLUID -> {
            // Preserves volume: weight flows to emptier neighbours, bleeding outward.
            val outImpulse = current.bleed * 0.2f
            Pair(
                current.copy(
                    weight = (current.weight - outImpulse).coerceIn(0f, 1f),
                    bleed = (current.bleed * 0.95f)
                ),
                outImpulse
            )
        }
        FormulaId.GAS -> {
            // Random-ish drift: expands unless boxed in by solids.
            val outImpulse = if (solidNeighbours >= 5) 0f else 0.06f
            Pair(
                current.copy(weight = (current.weight - outImpulse * 0.02f).coerceIn(0f, 1f), jitter = 0.8f),
                outImpulse
            )
        }
        FormulaId.FIRE -> {
            // Consumes matter (lowers weight) and emits sparks as impulse.
            Pair(
                current.copy(weight = (current.weight - 0.05f).coerceIn(0f, 1f), probability = current.probability),
                if (current.weight > 0.05f) 0.9f else 0f
            )
        }
        FormulaId.HAND_DRAWN -> {
            // "Living line": adds jitter + scratches over time (procedural, not stored).
            Pair(current.copy(jitter = (current.jitter + 0.05f).coerceIn(0f, 1f)), 0f)
        }
        FormulaId.RUST -> {
            // Corrodes: lowers weight of neighbours (impulse) and nothing of the voxel itself.
            Pair(current.copy(bleed = (current.bleed + 0.02f).coerceIn(0f, 1f)), 0.3f)
        }
        FormulaId.WET -> {
            // Increases bleed (conductivity/spread) but no out impulse beyond a little.
            Pair(current.copy(bleed = (current.bleed + 0.1f).coerceIn(0f, 1f)), 0.1f)
        }
        FormulaId.DAMAGED -> {
            // Fragile: its integrity decays further on any impulse (handled in tick impulse).
            val extraDamage = if (current.integrity <= 0.25f) 1 shl 7 else 0
            Pair(current.copy(damageMask = current.damageMask or extraDamage), 0f)
        }
        FormulaId.PLAYER_AGENT -> Pair(current, 0f)
    }

    /**
     * Apply structural damage: clears an integrity bit (§8: AND-style erosion on impulse).
     * Returns a new state with one bit of its integrity mask removed.
     */
    fun applyImpulseDamage(state: VoxelState, impulse: Float): VoxelState {
        if (impulse <= 0.1f || state.damageMask == 0xFF || state.formulaId == FormulaId.VOID) return state
        // Find the lowest clear bit and set it (marking one eighth of integrity lost).
        var bit = 1
        while (bit <= 0x80) {
            if (state.damageMask and bit == 0) {
                return state.copy(damageMask = state.damageMask or bit)
            }
            bit = bit shl 1
        }
        return state
    }

    /**
     * §2/§17: the player agent writes a limited, local change to neighbouring voxels —
     * it may lower weight/probability (destruction) but never changes a *formula*.
     */
    fun playerTransform(state: VoxelState, strength: Float = 0.5f): VoxelState {
        val dmg = (strength * 8).toInt().coerceIn(0, 8)
        val damageMask = state.damageMask or ((1 shl dmg) - 1)
        // Damage also lowers the collapse probability (§6), so destroyed matter starts
        // to "flicker out" probabilistically rather than vanishing all at once.
        val probability = (state.probability * (1f - strength * 0.4f)).coerceIn(0f, 1f)
        return state.copy(
            formulaId = state.formulaId, // rules are immutable (so must be identical)
            weight = (state.weight * (1f - strength * 0.3f)).coerceIn(0f, 1f),
            probability = probability,
            damageMask = damageMask and 0xFF
        )
    }
}
