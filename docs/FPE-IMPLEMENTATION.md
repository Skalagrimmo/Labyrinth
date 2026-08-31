# FPE Implementation (Netcrawler / Kotlin) — Slices 1–3

This document formalizes the port of the **Fractal Point Engine (FPE)** ideas from the
architectural spec (`Quantum.md`, kept outside the repo as the reference) into Netcrawler
as a working Kotlin + Compose implementation. It records what was built, the design
decisions, the `Quantum.md` sections that map to each piece, and the remaining gaps.

> Status: **Slides 1–3 complete and live in production.** All four FPE layers are wired
> into the running game. 45 host-JVM tests green, debug build green.

---

## 1. Scope & Boundary

The FPE render/mutation ideas are being built natively in **Kotlin** for Netcrawler, which
uses a 2D Compose `Canvas` first-person view and a topological `SparseVoxelDag`. There is
**zero** JNI/native code in this project. GLES30 is used only for the cyber-matrix /
character cinematic effects, called directly from Kotlin.

A separate **Rust + wgpu** engine was *considered* only as a future, separate
multiplatform prototype — **no** Rust code exists in this repository and none is planned
here.

---

## 2. The core idea from `Quantum.md` (recap)

The FPE is a point/voxel engine with three pillars:

1. **World Rules (immutable)** — a fixed formula table (`formula_id`) that never changes
   for a session (§5/§6).
2. **World State (mutable)** — sparse per-voxel data that *is* allowed to change (§2):
   destruction, damage, collapse — but **never** touches the structural representation.
3. **Store formula + seed, not result** (§7) — geometry/state is *derived* on demand, not
   materialized, keeping the structure deduplicated and tiny.

Plus three render pillars:

- **Probabilistic collapse (decoherence, §7/§6)** — a voxel exists this frame only if a
  deterministic hash drops below its `probability`.
- **Eclectic render languages (§15/§16)** — one structure, several visual languages
  (SOLID / point-cloud / ASCII-glyph decay) chosen by *state*, not randomness.
- **Render-as-narrative-state (§17)** — what you see is the game state; damage is visible.

---

## 3. Architecture: layering over the existing SVDAG

The existing `SparseVoxelDag` (topological: children + dominant `VoxelType`, deduplicated)
is left **unchanged and authoritative** for structure. Everything dynamic lives in a thin
sparse overlay. This honours `Quantum.md` §7/§17: mutate World State, never re-touch the DAG,
so dedup is never broken.

```
SparseVoxelDag (topology, deduplicated)  <-- immutable authority
        ^
        | reads (materialise on demand)
SvdagWorldState (sparse mutable overlay: weight/jitter/bleed/probability/damageMask)
        ^
        | reads
FpeProbabilityField (deterministic, seeded; "is it materialised this frame")
        ^
        | reads (mode = f(weight, damage), never random)
FpeRenderStylist (SOLID / POINT_CLOUD / ASCII_DECAY / VOID)
```

### Files

| File | Purpose | `Quantum.md` |
|------|---------|--------------|
| `data/svdag/FpeWorldRules.kt` | `FormulaId`, `VoxelState`, `Point3i`, `SvdagWorldState`, `WorldRules` | §2, §5, §6, §7, §8, §17 |
| `data/svdag/FpeProbabilityField.kt` | seeded collapse / materialisation field | §6, §7 |
| `data/svdag/FpeRenderMode.kt` | `FpeRenderMode`, `FpeRenderStylist` | §15, §16 |

---

## 4. Slice 1 — World Rules + World State (`FpeWorldRules.kt`)

### 4.1 `FormulaId` (World Rules table, §5/§6)
An enum binding a material to a formula: `VOID, SOLID, FLUID, GAS, FIRE, HAND_DRAWN,
RUST, WET, DAMAGED, PLAYER_AGENT`, with `fromId` round-trip. `VoxelType.defaultFormula()`
maps each existing maze material (SOLID_WALL → SOLID, BLACK_ICE → FIRE, etc.).

### 4.2 `VoxelState` (World State, §2/§7)
A single mutable voxel's dynamic data:
- `weight` ∈ [0,1] — density / how "solid" it reads.
- `probability` ∈ [0,1] — collapse probability; `probability <= 0` ⇒ `collapsed`.
- `damageMask` ∈ 8 bits — fine-grained structural integrity; `integrity = 1 - popcount/8`.
- `jitter`, `bleed` — procedural variation/conductivity (§5).
- `formulaId` — immutable link to the rules table.

### 4.3 `SvdagWorldState` (the overlay, §2/§7)
A `HashMap<Point3i, VoxelState>` over the DAG:
- Reads fall through to `dag.getVoxel(...).defaultState()` when no override exists.
- Writes only mutate the map — **never the DAG** ⇒ dedup preserved.
Kept "shallow" (only explicitly-changed voxels) to honour *data minimal, variation computed*.

### 4.4 `WorldRules` (the laws, §8/§17)
Pure, deterministic transforms:
- `tick(state, solidNeighbours)` — per-formula law (FLUID drains, FIRE consumes, RUST
  corrodes neighbours, DAMAGED self-fragments, HAND_DRAWN scratches, ...).
- `applyImpulseDamage(state, impulse)` — erode one integrity bit (AND-style, §8).
- `playerTransform(state, strength)` — the player agent's limited local change: lowers
  weight **and** probability (§6 link) and sets damage bits; **never** changes the formula.

---

## 5. Slice 2 — Probabilistic collapse (`FpeProbabilityField.kt`, §6/§7)

A read-only, deterministic field over a `SvdagWorldState`:

- `noise(x,y,z,phase) ∈ [0,1)` — splitmix64-style hash of coordinates + seed + observer
  *phase*. Deterministic: same inputs ⇒ same output (no fake randomness per frame).
- `isMaterialised(x,y,z,phase)` — `!collapsed && noise < probability`.
- `exactState(...) ∈ VoxelState?` — the observed exact state, `null` when not materialised
  (decoherence: "forgotten until the next collapse", never stored — always re-derived).
- `materialisedInBox / countMaterialisedInBox` — frustum sweeps.

This is the engine's key trick made real: **unobserved voxels are never materialized**.

---

## 6. Slice 3 — Eclectic render-language selector (`FpeRenderMode.kt`, §15/§16)

`FpeRenderStylist.modeFor(state)` — pure, deterministic classification (never random):

```
collapsed OR weight<=0            -> VOID
weight>=0.65 AND integrity>=0.75  -> SOLID
weight<0.25  OR  integrity<0.25   -> ASCII_DECAY
else                              -> POINT_CLOUD
```

Also `opacityFor(state, materialised)` (probabilistic fade) and `isDecayTone(mode)`
(colour-language hint: decay-green glyph tone vs primary).

---

## 7. Production wiring (what's actually live)

| Layer | Where it's live |
|-------|-----------------|
| `SvdagWorldState` | Built alongside every DAG at all 3 creation sites (`ensureSvdagInitialized`, `initOrRegenerateSvdag`, zone transition). Stored in `GameUiState.svdagWorldState`. |
| World-State damage path | `ExplorationManager.modifySvdagVoxel` writes `playerTransform` into the overlay on destruction (`EMPTY`), clears the override on repair. The DAG structural write is kept so gameplay/pathfinding is unchanged. |
| `FpeRenderStylist` (§15) | **SVDAG inspector slice viewer**: with `FPE RENDER: ON`, each voxel renders by its mode (SOLID colour / POINT_CLOUD shrunk-dim / ASCII_DECAY decay-green / VOID gone). |
| `FpeProbabilityField` (collapsed) | Inspector animates an observer **phase** (140 ms cadence); decayed voxels shimmer/flicker out via `isMaterialised` + `opacityFor`. |
| FPP maze view (Candidate A) | `FirstPersonPerspectiveCanvas.wallStability` can be switched (`$ fpe on/off`, `GameUiState.useFpeInFppView`) to drive wall stability from the 3D overlay (integrity + §15 mode penalty) mapped `(cx,cy,depth) -> voxel`. Opt-in, default off ⇒ no visual regression. |

### User-facing controls
- **Inspector:** `FPE RENDER: ON/OFF` chip (renders the 3 dialect languages + flicker).
- **Terminal (FPP view):** `$ fpe on|off`.

---

## 8. Consistency notes / decisions (from the design review)

- **"Forgetting" is lossless.** Collapse decisions are re-derived from `(seed, coordinates,
  phase, probability)`; nothing structurally is deleted, so no data loss — which is exactly
  the §6/§7 invariant.
- **DAG stays the structural authority.** All dynamic damage goes to the overlay; this is
  why dedup (the engine's memory win) is never broken.
- **The 44-byte ("24-byte") node claim.** The spec's crude-size claim was recomputed: a
  hot/cold split into `SVDAGTopo` (8 B) + `SVDAGState` (24 B) storage buffers is the honest
  layout; a WGSL `u32`-packed skin maintains 24 B for a `SVDAGState` node. Not implemented
  here (no GPU compute in the Compose path) — recorded for the future Rust/wgpu prototype.
- **Why FPE render is opt-in in the FPP view.** `svdagWorld` is only populated after the
  player enters the SVDAG inspector; gating prevents a surprise visual shift mid-game.

---

## 9. Test coverage

`app/src/test/java/com/example/data/svdag/` — host-JVM tests (45 total):

| Suite | Count | Covers |
|-------|-------|--------|
| `FpeWorldRulesTest` | 18 | formula binding, tick, damage, playerTransform, probability link |
| `FpeProbabilityFieldTest` | 10 | determinism, collapse boundaries, materialisation rates, box sweep |
| `FpeRenderModeTest` | 12 | all mode bands, threshold edges, no-randomness, opacity, decay tone |
| `SvdagWorldStateTest` | 5 | live overlay: fall-through, mutation, DAG untouched, restore, collapse |

Run: `JAVA_HOME=<jdk17> ./gradlew :app:testDebugUnitTest --tests "com.example.data.svdag.*"`

---

## 10. Remaining / next steps

- **FPE in the GLES30 / wgpu path** — the probabilistic + dialect render currently uses
  Compose `Canvas`; a GPU storage-buffer version is future work (see §8 hot/cold split).
- **Formalize into a canonical spec doc** — this file is that write-up; fold back into the
  source `Quantum.md` if desired.
- Optional: expose `tick()` propagation (FLUID/FIRE/RUST impulses) into live World State so
  element drift (§5) is visible, not just damage.
