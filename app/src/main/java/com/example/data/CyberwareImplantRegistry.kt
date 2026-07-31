package com.example.data

object CyberwareImplantRegistry {

    val STARTER_IMPLANTS = listOf(
        CyberwareImplant(
            id = "starter_neural_link",
            name = "Neural Link Alpha",
            slot = ImplantBodySlot.NEURAL_CORTEX,
            description = "High-speed optical neural bridge accelerating memory allocation and process recycling.",
            cost = 0,
            rarity = ItemRarity.UNCOMMON,
            icon = "🧠",
            ramBonus = 2,
            passiveAbility = ImplantAbility.RAM_RECYCLER
        ),
        CyberwareImplant(
            id = "starter_ocular_hud",
            name = "Optical Combat HUD",
            slot = ImplantBodySlot.OCULAR_ARRAY,
            description = "Retinal projection overlay targeting weak vulnerabilities in enemy cyber-defenses.",
            cost = 0,
            rarity = ItemRarity.UNCOMMON,
            icon = "👁️",
            damageBonus = 3,
            passiveAbility = ImplantAbility.CRIT_TARGETING
        ),
        CyberwareImplant(
            id = "starter_subdermal",
            name = "Subdermal Weave v1",
            slot = ImplantBodySlot.SUBDERMAL_CHASSIS,
            description = "Subdermal carbon nanofiber weave absorbing kinetic and electrical impact surges.",
            cost = 0,
            rarity = ItemRarity.UNCOMMON,
            icon = "🛡️",
            integrityBonus = 25,
            defenseBonus = 2,
            passiveAbility = ImplantAbility.KINETIC_BARRIER
        ),
        CyberwareImplant(
            id = "starter_bio_pump",
            name = "Bio-Pump Reactor",
            slot = ImplantBodySlot.SYNTH_HEART,
            description = "Synthetic heart pumping nanite-enriched synthetic plasma to continuously repair system chassis.",
            cost = 0,
            rarity = ItemRarity.UNCOMMON,
            icon = "🫀",
            integrityBonus = 20,
            recoveryBonus = 1,
            passiveAbility = ImplantAbility.NANITE_REGEN
        ),
        CyberwareImplant(
            id = "starter_myomer_limbs",
            name = "Hydraulic Myomer",
            slot = ImplantBodySlot.CYBER_ACTUATORS,
            description = "Reinforced artificial limb servos maximizing offensive torque and barrier deflection.",
            cost = 0,
            rarity = ItemRarity.UNCOMMON,
            icon = "🦾",
            damageBonus = 4,
            defenseBonus = 3,
            passiveAbility = ImplantAbility.SYNAPTIC_OVERCLOCK
        )
    )

    val ALL_IMPLANTS = STARTER_IMPLANTS + listOf(
        CyberwareImplant(
            id = "imp_synaptic_v2",
            name = "Synaptic Co-Processor",
            slot = ImplantBodySlot.NEURAL_CORTEX,
            description = "Military-grade neural unit granting massive RAM overhead and overclocked combat scripts.",
            cost = 450,
            rarity = ItemRarity.RARE,
            icon = "🔮",
            ramBonus = 4,
            recoveryBonus = 2,
            passiveAbility = ImplantAbility.SYNAPTIC_OVERCLOCK
        ),
        CyberwareImplant(
            id = "imp_exo_ribs",
            name = "Titanium Exo-Ribs",
            slot = ImplantBodySlot.SUBDERMAL_CHASSIS,
            description = "Heavy titanium dermal plating turning incoming kinetic malware into harmless heat.",
            cost = 600,
            rarity = ItemRarity.EPIC,
            icon = "🥋",
            integrityBonus = 50,
            defenseBonus = 6,
            passiveAbility = ImplantAbility.KINETIC_BARRIER
        ),
        CyberwareImplant(
            id = "imp_nanite_heart",
            name = "Vascular Nanite Core",
            slot = ImplantBodySlot.SYNTH_HEART,
            description = "Autonomous nanite swarm heart capable of emergency system reboot upon fatal core collapse.",
            cost = 750,
            rarity = ItemRarity.LEGENDARY,
            icon = "❤️‍🔥",
            integrityBonus = 40,
            recoveryBonus = 2,
            passiveAbility = ImplantAbility.EMERGENCY_REBOOT
        ),
        CyberwareImplant(
            id = "imp_apex_claws",
            name = "Apex Cyber-Claws",
            slot = ImplantBodySlot.CYBER_ACTUATORS,
            description = "Retractable monomolecular claw assembly for razor-sharp physical breaches.",
            cost = 550,
            rarity = ItemRarity.RARE,
            icon = "🗡️",
            damageBonus = 8,
            passiveAbility = ImplantAbility.CRIT_TARGETING
        ),
        CyberwareImplant(
            id = "imp_kiroshi_lens",
            name = "Kiroshi Smart Retinal",
            slot = ImplantBodySlot.OCULAR_ARRAY,
            description = "High-precision spectral optical lens revealing hidden network vulnerabilities.",
            cost = 400,
            rarity = ItemRarity.RARE,
            icon = "🔭",
            damageBonus = 5,
            ramBonus = 2,
            passiveAbility = ImplantAbility.CRIT_TARGETING
        )
    )

    fun getImplantById(id: String): CyberwareImplant? {
        return ALL_IMPLANTS.firstOrNull { it.id == id }
    }

    fun getStarterImplantForClass(runnerClass: NetrunnerClass): CyberwareImplant {
        return when (runnerClass) {
            NetrunnerClass.CODE_SLASHER -> STARTER_IMPLANTS.first { it.id == "starter_ocular_hud" }
            NetrunnerClass.CYBER_SHIELD -> STARTER_IMPLANTS.first { it.id == "starter_subdermal" }
            NetrunnerClass.BUFFER_OVERFLOW -> STARTER_IMPLANTS.first { it.id == "starter_neural_link" }
            NetrunnerClass.SCRIPT_KIDDIE -> STARTER_IMPLANTS.first { it.id == "starter_bio_pump" }
        }
    }
}
