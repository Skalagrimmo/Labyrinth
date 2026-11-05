package com.example.data

enum class ImplantBodySlot(val displayName: String, val icon: String, val bodyPart: String) {
    NEURAL_CORTEX("Neural Cortex", "🧠", "Head / Brain"),
    OCULAR_ARRAY("Ocular Array", "👁️", "Eyes / Vision"),
    SUBDERMAL_CHASSIS("Subdermal Chassis", "🛡️", "Torso / Skin"),
    SYNTH_HEART("Synthetic Heart", "🫀", "Chest / Circulatory"),
    CYBER_ACTUATORS("Limb Actuators", "🦾", "Arms & Legs")
}

enum class ImplantAbility(
    val title: String,
    val icon: String,
    val description: String
) {
    EMERGENCY_REBOOT("Emergency Reboot", "⚡", "Auto-revives system to 25% integrity upon lethal damage (Once per run)."),
    RAM_RECYCLER("RAM Recycler", "🔋", "Restores +1 RAM immediately whenever a hostile process is neutralized."),
    CRIT_TARGETING("Targeting Matrix", "🎯", "Grants +20% bonus chance to deal critical payload damage."),
    KINETIC_BARRIER("Kinetic Shield", "🛡️", "Completely absorbs the first incoming attack in every combat encounter."),
    SYNAPTIC_OVERCLOCK("Synaptic Overclock", "🔥", "Amplifies payload damage by +25% when available RAM is under 3."),
    NANITE_REGEN("Nanite Weaver", "💊", "Regenerates +3 System Integrity every 5 exploration steps.")
}

data class CyberwareImplant(
    val id: String,
    val name: String,
    val slot: ImplantBodySlot,
    val description: String,
    val cost: Int = 300,
    val rarity: ItemRarity = ItemRarity.COMMON,
    val icon: String = "🔌",
    // Stat Modifiers
    val integrityBonus: Int = 0,
    val ramBonus: Int = 0,
    val recoveryBonus: Int = 0,
    val damageBonus: Int = 0,
    val defenseBonus: Int = 0,
    // Unique Ability
    val passiveAbility: ImplantAbility? = null
)
