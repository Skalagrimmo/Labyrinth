package com.example.data

/**
 * ContentRegistry — the runtime conversion layer for markdown-defined mods.
 *
 * Given a mod document (see [ContentModParser] for the format), it converts the parsed
 * [ContentModParser.ModEntity] blocks into the concrete model types the game already uses:
 *
 *  - enemies  -> [GameEngine.EnemyArchetype]  (fed into [GameEngine.registerEnemyArchetypes])
 *  - items    -> [GameItem]                   (merged into [GameItemRegistry])
 *  - programs -> [Program]                    (available via [programs])
 *
 * The registry is a pure object (no Android dependencies) so it can be unit-tested and
 * reused anywhere. Loading from an actual Android asset is done via the
 * [ContentModLoader] helper.
 *
 * Field reference (all keys case-insensitive):
 *
 * Enemy:
 *   tier    -> min tier/depth (Int, default 1)
 *   hp      -> HP multiplier (Float)
 *   shield  -> Shield multiplier (Float)
 *   damage  -> Damage multiplier (Float)
 *   armor   -> flat armor bonus (Int)
 *   bounty  -> Bounty multiplier (Float)
 *   status  -> comma-separated `STATUS:turnCount` starting status effects
 *   desc    -> short description
 *   portrait -> fenced ASCII-art block
 *
 * Item:
 *   category -> CONSUMABLE|EQUIPMENT|PROGRAM|KEY_ITEM|RESOURCE
 *   rarity   -> COMMON|UNCOMMON|RARE|EPIC|LEGENDARY
 *   icon     -> emoji/glyph
 *   value    -> credit value
 *   slot     -> WEAPON|ARMOR|CYBERWARE|UTILITY (for equipment)
 *   dmg/def/ram/integrity -> equippable stat bonuses
 *   heal/credits/xp       -> consumable grants
 *   status -> `STATUS:turnCount:self` consumable status effect
 *
 * Program:
 *   ram      -> RAM cost
 *   damage   -> payload damage
 *   shield   -> shield restore
 *   heal     -> integrity restore
 *   cooldown -> cooldown turns
 *   pierce   -> true/false (pierces defense)
 *   status   -> `STATUS:turnCount:self` status effect
 */
object ContentRegistry {

    /** Number of content blocks successfully converted, for debug/logging. */
    var loadedCount: Int = 0
        private set

    // NOTE: the built-in registries (GameEngine/GameItemRegistry/PersistenceManager)
    // remain the source of truth for core content. Mod registries are merged *on top*
    // of them so a mod never needs to redefine a base entry.

    private val parsedEntities: MutableList<ContentModParser.ModEntity> = mutableListOf()
    private val enemyArchetypes = mutableListOf<GameEngine.EnemyArchetype>()
    private val modItems = mutableListOf<GameItem>()
    private val modPrograms = mutableListOf<Program>()

    fun enemySpecs(): List<GameEngine.EnemyArchetype> = enemyArchetypes.toList()
    fun itemSpecs(): List<GameItem> = modItems.toList()
    fun programSpecs(): List<Program> = modPrograms.toList()

    /**
     * Loads and registers a mod document. Parses, converts, and merges registered content
     * into the engine's live registries. Safe to call multiple times (each call re-parses
     * from scratch, so re-loading a file is idempotent for that file).
     */
    fun loadMod(markdown: String) {
        val entities = ContentModParser.parse(markdown)
        synchronized(this) {
            parsedEntities.addAll(entities)
            var converted = 0
            for (e in entities) {
                when (e.kind.lowercase()) {
                    "enemy" -> { val a = toArchetype(e); if (a != null) { enemyArchetypes.add(a); converted++ } }
                    "item" -> { val i = toItem(e); if (i != null) { modItems.add(i); converted++ } }
                    "program" -> { val p = toProgram(e); if (p != null) { modPrograms.add(p); converted++ } }
                }
            }
            loadedCount = converted

            // Merge into live registries.
            GameEngine.registerEnemyArchetypes(enemyArchetypes)
            GameItemRegistry.registerModItems(modItems)
        }
    }

    /** Converts a generic [GameItem] into a [GameItem] using the parsed fields. */
    private fun toItem(e: ContentModParser.ModEntity): GameItem? {
        val f = e.fields
        val id = idOf(e)
        val name = e.name.ifBlank { id }
        val category = enumOf<InventoryCategory>(f["category"]) ?: InventoryCategory.RESOURCE
        val rarity = enumOf<ItemRarity>(f["rarity"]) ?: ItemRarity.COMMON
        val icon = f["icon"] ?: "📦"
        val value = f["value"]?.toIntOrNull() ?: 50
        val isEquip = category == InventoryCategory.EQUIPMENT
        val slot = if (isEquip) enumOf<EquipmentSlot>(f["slot"]) else null

        val statusField = f["status"]
        val status = parseStatus(statusField)
        return GameItem(
            id = id,
            name = name,
            description = e.body.ifBlank { e.fields["desc"] ?: "Custom mod item." },
            category = category,
            rarity = rarity,
            icon = icon,
            valueCredits = value,
            isConsumable = category == InventoryCategory.CONSUMABLE,
            isEquippable = isEquip,
            equipmentSlot = slot,
            damageBonus = f["dmg"]?.toIntOrNull() ?: 0,
            defenseBonus = f["def"]?.toIntOrNull() ?: 0,
            ramBonus = f["ram"]?.toIntOrNull() ?: 0,
            integrityBonus = f["integrity"]?.toIntOrNull() ?: 0,
            healIntegrity = f["heal"]?.toIntOrNull() ?: 0,
            restoreRam = f["ramrestore"]?.toIntOrNull() ?: 0,
            grantCredits = f["credits"]?.toIntOrNull() ?: 0,
            grantXp = f["xp"]?.toIntOrNull() ?: 0,
            statusEffectToApply = status?.type,
            statusEffectTurns = status?.turns ?: 0,
            targetSelf = status?.self ?: true
        )
    }

    /** Converts a parsed enemy entity into a [GameEngine.EnemyArchetype]. */
    private fun toArchetype(e: ContentModParser.ModEntity): GameEngine.EnemyArchetype? {
        val f = e.fields
        val id = idOf(e)
        val name = e.name.ifBlank { id }
        val portrait = e.portrait.ifBlank { "  (o_o)\n  (>.<)\n   (=I=)" }
        val description = f["desc"] ?: "A mod-registered hostile data construct."

        val statusEffects = mutableListOf<Pair<StatusEffectType, Int>>()
        f["status"]?.split(",")?.forEach { s ->
            val parts = s.trim().split(":")
            val type = enumOf<StatusEffectType>(parts.getOrNull(0))
            val turns = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 1
            if (type != null) statusEffects.add(Pair(type, turns))
        }

        return GameEngine.EnemyArchetype(
            name = name,
            description = description,
            asciiArt = portrait,
            minTier = f["tier"]?.toIntOrNull() ?: 1,
            hpMult = f["hp"]?.toFloatOrNull() ?: 1.0f,
            shieldMult = f["shield"]?.toFloatOrNull() ?: 1.0f,
            dmgMult = f["damage"]?.toFloatOrNull() ?: 1.0f,
            armorBonus = f["armor"]?.toIntOrNull() ?: 0,
            bountyMult = f["bounty"]?.toFloatOrNull() ?: 1.0f,
            statusEffects = statusEffects
        )
    }

    /** Converts a parsed program entity into a [Program]. */
    private fun toProgram(e: ContentModParser.ModEntity): Program? {
        val f = e.fields
        val id = idOf(e)
        val name = e.name.ifBlank { id }
        val status = parseStatus(f["status"])
        return Program(
            id = id,
            name = name,
            description = e.body.ifBlank { f["desc"] ?: "Custom mod program." },
            ramCost = f["ram"]?.toIntOrNull() ?: 1,
            cooldownTurns = f["cooldown"]?.toIntOrNull() ?: 0,
            damage = f["damage"]?.toIntOrNull() ?: 0,
            shield = f["shield"]?.toIntOrNull() ?: 0,
            heal = f["heal"]?.toIntOrNull() ?: 0,
            piercesDefense = parseBool(f["pierce"]),
            statusEffectToApply = status?.type,
            statusEffectTurns = status?.turns ?: 0,
            statusEffectTargetSelf = status?.self ?: false,
            statusEffectMagnitude = f["magnitude"]?.toIntOrNull() ?: 0
        )
    }

    private fun parseBool(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        val t = raw.trim().lowercase()
        return t == "true" || t == "yes" || t == "1"
    }

    private fun idOf(e: ContentModParser.ModEntity): String {
        val raw = e.name.ifBlank { e.kind }
        return "mod_" + raw.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
    }

    private data class ParsedStatus(val type: StatusEffectType, val turns: Int, val self: Boolean)

    /** Parses `STATUS:turns:bool` (item/program status effect). */
    private fun parseStatus(raw: String?): ParsedStatus? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split(":")
        val type = enumOf<StatusEffectType>(parts.getOrNull(0)) ?: return null
        val turns = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 1
        val self = parseBool(parts.getOrNull(2))
        return ParsedStatus(type, turns, self)
    }

    private inline fun <reified T : Enum<T>> enumOf(raw: String?): T? {
        if (raw == null) return null
        return enumValues<T>().firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
    }
}

/**
 * Loads mod content from the Android `assets/` folder. Place `.md` files in
 * `app/src/main/assets/mods/` and call [loadFolderMods] once at startup.
 */
object ContentModLoader {
    /**
     * Reads every `*.md` file under `assets/mods/` and registers its content.
     * @return the names of successfully loaded mod files.
     */
    fun loadFolderMods(context: android.content.Context): List<String> {
        val loaded = mutableListOf<String>()
        try {
            val fileNames = context.assets.list("mods") ?: emptyArray()
            for (file in fileNames) {
                if (!file.endsWith(".md")) continue
                val text = context.assets.open("mods/$file").bufferedReader().use { it.readText() }
                ContentRegistry.loadMod(text)
                loaded.add(file)
            }
        } catch (_: Exception) {
            // assets/mods/ absent or unreadable — no mods loaded. Safe to ignore.
        }
        return loaded
    }
}
