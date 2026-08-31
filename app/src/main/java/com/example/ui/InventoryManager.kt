package com.example.ui

import com.example.audio.CyberSoundEffectsManager
import com.example.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class InventoryManager(
    private val _uiState: MutableStateFlow<GameViewModel.GameUiState>,
    private val soundManager: CyberSoundEffectsManager,
    private val repository: GameRepository,
    private val scope: CoroutineScope,
    private val onLog: (String, LogType) -> Unit,
    private val onAddExperience: (Int) -> Unit,
    private val onSave: () -> Unit,
    private val onCombatAction: (() -> Unit)? = null,
    private val onRecordPlayerAction: ((actionType: CombatActionType, summary: String, healAmount: Int, statusApplied: String?) -> Unit)? = null,
    private val onApplyStatusEffectToPlayer: ((type: StatusEffectType, turns: Int, source: String) -> Unit)? = null,
    private val onApplyStatusEffectToEnemy: ((type: StatusEffectType, turns: Int, source: String) -> Unit)? = null
) {
    private val uiState get() = _uiState.value

    // ----------------------------------------------------
    // Inventory Architecture & Equipment System
    // ----------------------------------------------------

    fun getStructuredInventorySlots(): List<InventorySlot> {
        val state = uiState
        val itemsMap = state.inventory.groupingBy { it }.eachCount()

        var slots = itemsMap.map { (name, count) ->
            val gameItem = GameItemRegistry.getItemByName(name)
            val isEquipped = when (gameItem.equipmentSlot) {
                EquipmentSlot.WEAPON -> state.equippedWeaponItem?.name == name || state.equippedWeaponName == name
                EquipmentSlot.ARMOR -> state.equippedArmorItem?.name == name || state.equippedArmorName == name
                EquipmentSlot.UTILITY, EquipmentSlot.CYBERWARE -> state.equippedUtilityItem?.name == name || state.equippedUtilityName == name
                null -> false
            }
            InventorySlot(
                item = gameItem,
                quantity = count,
                isEquipped = isEquipped
            )
        }

        val categoryFilter = state.selectedInventoryCategoryFilter
        if (categoryFilter != null) {
            slots = slots.filter { it.item.category == categoryFilter }
        }

        return when (state.inventorySortOption) {
            InventorySortOption.NAME -> slots.sortedBy { it.item.name }
            InventorySortOption.CATEGORY -> slots.sortedWith(compareBy({ it.item.category.ordinal }, { it.item.name }))
            InventorySortOption.RARITY -> slots.sortedWith(compareByDescending<InventorySlot> { it.item.rarity.ordinal }.thenBy { it.item.name })
            InventorySortOption.QUANTITY -> slots.sortedByDescending { it.quantity }
        }
    }

    fun addItemToInventory(itemName: String, quantity: Int = 1): Boolean {
        if (quantity <= 0 || itemName.isBlank()) return false
        val state = uiState
        val newItems = List(quantity) { itemName }
        val updatedInventory = state.inventory + newItems

        val itemData = GameItemRegistry.getItemByName(itemName)

        _uiState.update { stateNow ->
            stateNow.copy(inventory = updatedInventory)
        }

        soundManager.playLootCollectionSound()
        onLog("${itemData.icon} ACQUIRED [${itemData.rarity.displayName.uppercase()}]: $itemName x$quantity (${itemData.category.displayName}) - ${itemData.description}", LogType.SUCCESS)

        scope.launch(Dispatchers.IO) {
            val entity = InventoryItemEntity(
                saveSlotId = "current_save",
                itemName = itemName,
                itemType = itemData.category.name,
                quantity = quantity,
                description = itemData.description
            )
            repository.insertInventoryItem(entity)
        }

        onSave()
        return true
    }

    fun removeItemFromInventory(itemName: String, quantity: Int = 1): Boolean {
        val state = uiState
        val currentCount = state.inventory.count { it.equals(itemName, ignoreCase = true) }
        if (currentCount < quantity) return false

        val updatedInventory = state.inventory.toMutableList()
        var removed = 0
        val iterator = updatedInventory.iterator()
        while (iterator.hasNext() && removed < quantity) {
            if (iterator.next().equals(itemName, ignoreCase = true)) {
                iterator.remove()
                removed++
            }
        }

        _uiState.update { it.copy(inventory = updatedInventory) }
        onSave()
        return true
    }

    fun hasItemInInventory(itemName: String, quantity: Int = 1): Boolean {
        val count = uiState.inventory.count { it.equals(itemName, ignoreCase = true) }
        return count >= quantity
    }

    fun equipItem(itemName: String): Boolean {
        val state = uiState
        val actualItemName = state.inventory.firstOrNull { it.equals(itemName, ignoreCase = true) }
        if (actualItemName == null) {
            onLog("EQUIP FAILED: Item '$itemName' not found in inventory core.", LogType.ERROR)
            return false
        }

        val gameItem = GameItemRegistry.getItemByName(actualItemName)
        if (!gameItem.isEquippable || gameItem.equipmentSlot == null) {
            onLog("EQUIP FAILED: '$actualItemName' is not an equippable weapon or armor module.", LogType.ERROR)
            return false
        }

        when (gameItem.equipmentSlot) {
            EquipmentSlot.WEAPON -> {
                val oldWeapon = state.equippedWeaponItem
                var dmgBonusAcc = state.damageBonus + gameItem.damageBonus
                if (oldWeapon != null) {
                    dmgBonusAcc -= oldWeapon.damageBonus
                }
                _uiState.update { stateNow ->
                    stateNow.copy(
                        equippedWeaponItem = gameItem,
                        equippedWeaponName = gameItem.name,
                        damageBonus = maxOf(0, dmgBonusAcc)
                    )
                }
                onLog("⚔️ WEAPON EQUIPPED: ${gameItem.name} (+${gameItem.damageBonus} Attack Bonus).", LogType.SUCCESS)
            }
            EquipmentSlot.ARMOR -> {
                val oldArmor = state.equippedArmorItem
                var defBonusAcc = state.defenseBonus + gameItem.defenseBonus
                var hpBonusAcc = state.maxIntegrity + gameItem.integrityBonus
                if (oldArmor != null) {
                    defBonusAcc -= oldArmor.defenseBonus
                    hpBonusAcc -= oldArmor.integrityBonus
                }
                _uiState.update { stateNow ->
                    stateNow.copy(
                        equippedArmorItem = gameItem,
                        equippedArmorName = gameItem.name,
                        defenseBonus = maxOf(0, defBonusAcc),
                        maxIntegrity = maxOf(50, hpBonusAcc),
                        integrity = minOf(stateNow.integrity, maxOf(50, hpBonusAcc))
                    )
                }
                onLog("🛡️ ARMOR EQUIPPED: ${gameItem.name} (+${gameItem.defenseBonus} Defense, +${gameItem.integrityBonus} Integrity).", LogType.SUCCESS)
            }
            EquipmentSlot.UTILITY, EquipmentSlot.CYBERWARE -> {
                val oldUtil = state.equippedUtilityItem
                var ramBonusAcc = state.maxRam + gameItem.ramBonus
                var dmgBonusAcc = state.damageBonus + gameItem.damageBonus
                if (oldUtil != null) {
                    ramBonusAcc -= oldUtil.ramBonus
                    dmgBonusAcc -= oldUtil.damageBonus
                }
                _uiState.update { stateNow ->
                    stateNow.copy(
                        equippedUtilityItem = gameItem,
                        equippedUtilityName = gameItem.name,
                        maxRam = maxOf(4, ramBonusAcc),
                        damageBonus = maxOf(0, dmgBonusAcc)
                    )
                }
                onLog("🔌 UTILITY MODULE MOUNTED: ${gameItem.name} (+${gameItem.ramBonus} RAM, +${gameItem.damageBonus} Dmg).", LogType.SUCCESS)
            }
        }
        return true
    }

    fun unequipItemSlot(slot: EquipmentSlot): Boolean {
        val state = uiState
        when (slot) {
            EquipmentSlot.WEAPON -> {
                val weapon = state.equippedWeaponItem ?: return false
                val newDmg = maxOf(0, state.damageBonus - weapon.damageBonus)
                _uiState.update { it.copy(equippedWeaponItem = null, equippedWeaponName = "Sparksteel Dagger", damageBonus = newDmg) }
                onLog("⚔️ UNEQUIPPED WEAPON: ${weapon.name}.", LogType.INFO)
            }
            EquipmentSlot.ARMOR -> {
                val armor = state.equippedArmorItem ?: return false
                val newDef = maxOf(0, state.defenseBonus - armor.defenseBonus)
                val newHp = maxOf(50, state.maxIntegrity - armor.integrityBonus)
                _uiState.update { it.copy(equippedArmorItem = null, equippedArmorName = "Basic Firewall Mesh", defenseBonus = newDef, maxIntegrity = newHp, integrity = minOf(it.integrity, newHp)) }
                onLog("🛡️ UNEQUIPPED ARMOR: ${armor.name}.", LogType.INFO)
            }
            EquipmentSlot.UTILITY, EquipmentSlot.CYBERWARE -> {
                val util = state.equippedUtilityItem ?: return false
                val newRam = maxOf(4, state.maxRam - util.ramBonus)
                val newDmg = maxOf(0, state.damageBonus - util.damageBonus)
                _uiState.update { it.copy(equippedUtilityItem = null, equippedUtilityName = "None", maxRam = newRam, damageBonus = newDmg) }
                onLog("🔌 UNEQUIPPED UTILITY MODULE: ${util.name}.", LogType.INFO)
            }
        }
        return true
    }

    fun scavengeCurrentCell() {
        val state = uiState
        if (state.screen != ActiveScreen.EXPLORATION) {
            onLog("SCAVENGE ERROR: Must be exploring grid sector to search cache.", LogType.ERROR)
            return
        }

        val dropItem = GameItemRegistry.getRandomExplorationDrop(state.level)
        val bonusCredits = (kotlin.random.Random.nextInt(20, 80) * (1f + state.level * 0.2f)).toInt()

        _uiState.update { stateNow ->
            stateNow.copy(
                credits = stateNow.credits + bonusCredits,
                totalCreditsEarned = stateNow.totalCreditsEarned + bonusCredits
            )
        }
        addItemToInventory(dropItem.name)
        onAddExperience(25 + state.level * 10)
        onLog("🔎 SCAVENGE SUCCESSFUL: Extracted +$bonusCredits Credits & found ${dropItem.icon} ${dropItem.name}!", LogType.SUCCESS)
    }

    fun setInventoryCategoryFilter(category: InventoryCategory?) {
        _uiState.update { it.copy(selectedInventoryCategoryFilter = category) }
        val catName = category?.displayName ?: "All Categories"
        onLog("FILTER APPLIED: Inventory viewing [$catName].", LogType.INFO)
    }

    fun setInventorySortOption(option: InventorySortOption) {
        _uiState.update { it.copy(inventorySortOption = option) }
        onLog("SORT APPLIED: Inventory ordered by [${option.displayName}].", LogType.INFO)
    }

    fun discardInventoryItem(itemName: String) {
        if (removeItemFromInventory(itemName, 1)) {
            onLog("🗑️ DISCARDED: 1x $itemName purged from memory bank.", LogType.INFO)
        } else {
            onLog("DISCARD FAILED: Item '$itemName' not found in inventory.", LogType.ERROR)
        }
    }

    fun useInventoryItem(itemName: String) {
        val state = uiState
        val actualItemName = state.inventory.firstOrNull { it.equals(itemName, ignoreCase = true) }
        if (actualItemName == null) return

        val gameItem = GameItemRegistry.getItemByName(actualItemName)

        if (gameItem.isEquippable) {
            equipItem(actualItemName)
            return
        }

        val updatedInventory = state.inventory.toMutableList()
        updatedInventory.remove(actualItemName)

        var logText = ""

        _uiState.update { stateNow ->
            var newIntegrity = stateNow.integrity
            var newRam = stateNow.ram
            var newCredits = stateNow.credits
            var newTotCredits = stateNow.totalCreditsEarned
            var newDmg = stateNow.damageBonus
            var newDef = stateNow.defenseBonus
            var newPredWeather = stateNow.predictedWeather

            if (gameItem.healIntegrity > 0) {
                val healed = minOf(stateNow.maxIntegrity - stateNow.integrity, gameItem.healIntegrity)
                newIntegrity += healed
                logText += "Restored $healed HP Integrity. "
            }
            if (gameItem.restoreRam > 0) {
                val boosted = minOf(stateNow.maxRam - stateNow.ram, gameItem.restoreRam)
                newRam += boosted
                logText += "Allocated $boosted MB RAM. "
            }
            if (gameItem.grantCredits > 0) {
                newCredits += gameItem.grantCredits
                newTotCredits += gameItem.grantCredits
                logText += "Extracted +${gameItem.grantCredits} MB Credits. "
            }
            if (gameItem.damageBonus > 0) {
                newDmg += gameItem.damageBonus
                logText += "Overclocked Attack (+${gameItem.damageBonus} Dmg). "
            }
            if (gameItem.defenseBonus > 0) {
                newDef += gameItem.defenseBonus
                logText += "Fortified Defense (+${gameItem.defenseBonus} Def). "
            }

            when (actualItemName) {
                "GibsonForecast.sys" -> {
                    val stepsRemaining = (stateNow.nextEventSteps - stateNow.stepsSinceLastEvent).coerceAtLeast(1)
                    val nextWeather = stateNow.predictedWeather ?: CyberWeather.VALUES.filter { it != CyberWeather.CLEAR }.random()
                    newPredWeather = nextWeather
                    logText += "Next weather [${nextWeather.title}] in $stepsRemaining steps. "
                }
                "AntiVirus.sys" -> {
                    logText += "Purged all debuffs. "
                }
            }

            stateNow.copy(
                integrity = newIntegrity,
                ram = newRam,
                credits = newCredits,
                totalCreditsEarned = newTotCredits,
                damageBonus = newDmg,
                defenseBonus = newDef,
                predictedWeather = newPredWeather,
                inventory = updatedInventory,
                playerStatusEffects = if (actualItemName == "AntiVirus.sys") stateNow.playerStatusEffects.filter { !it.type.isDebuff } else stateNow.playerStatusEffects
            )
        }

        if (gameItem.grantXp > 0) {
            onAddExperience(gameItem.grantXp)
        }

        if (logText.isEmpty()) {
            logText = "COMPILED $actualItemName utility."
        } else {
            logText = "COMPILED $actualItemName: " + logText.trim()
        }

        onLog(logText, LogType.SUCCESS)

        if (gameItem.statusEffectToApply != null) {
            if (gameItem.targetSelf) {
                onApplyStatusEffectToPlayer?.invoke(gameItem.statusEffectToApply, gameItem.statusEffectTurns, actualItemName)
            } else {
                onApplyStatusEffectToEnemy?.invoke(gameItem.statusEffectToApply, gameItem.statusEffectTurns, actualItemName)
            }
        }

        val gs = uiState.gameState
        if (gs == GameState.PLAYER_TURN || gs == GameState.COMBAT_START) {
            onRecordPlayerAction?.invoke(
                CombatActionType.USE_ITEM,
                "Compiled $actualItemName utility",
                gameItem.healIntegrity,
                gameItem.statusEffectToApply?.displayName
            )
            onCombatAction?.invoke()
        }
    }

    // ----------------------------------------------------
    // Shop & Upgrades Console
    // ----------------------------------------------------

    fun enterShop() {
        _uiState.update { it.copy(screen = ActiveScreen.UPGRADE_STORE) }
        onLog("CONNECTING TO BLACK-MARKET CYBERNET WORKSTATION...", LogType.INFO)
    }

    fun exitShop() {
        _uiState.update { it.copy(screen = ActiveScreen.EXPLORATION) }
        onLog("DISCONNECTED FROM SHOP SERVER. TELEMETRY RESUMED.", LogType.INFO)
    }

    fun purchaseCyberware(cyberware: Cyberware) {
        val state = uiState
        if (state.credits < cyberware.cost) {
            onLog("PURCHASE DECLINED: INSIGNIFICANT CREDITS BLOCK.", LogType.ERROR)
            return
        }

        if (state.installedCyberware.any { it.id == cyberware.id }) {
            onLog("INSTALLATION BLOCKED: HARDWARE MODULE ALREADY MOUNTED.", LogType.ERROR)
            return
        }

        val updatedCyberware = state.installedCyberware.toMutableList()
        updatedCyberware.add(cyberware)

        _uiState.update { stateNow ->
            stateNow.copy(
                credits = stateNow.credits - cyberware.cost,
                installedCyberware = updatedCyberware,
                maxIntegrity = stateNow.maxIntegrity + cyberware.integrityBonus,
                integrity = stateNow.integrity + cyberware.integrityBonus,
                maxRam = stateNow.maxRam + cyberware.ramBonus,
                ram = stateNow.ram + cyberware.ramBonus,
                ramRecoveryRate = stateNow.ramRecoveryRate + cyberware.recoveryBonus,
                damageBonus = stateNow.damageBonus + cyberware.damageBonus,
                defenseBonus = stateNow.defenseBonus + cyberware.defenseBonus
            )
        }

        onLog("HARDWARE INTEGRATION SUCCESS: ${cyberware.name} INSTALLED.", LogType.SUCCESS)
        onLog("MODULE SPECS: ${cyberware.description}", LogType.INFO)
    }

    fun purchaseConsumable(name: String, cost: Int) {
        val state = uiState
        if (state.credits < cost) {
            onLog("PURCHASE DECLINED: INSUFFICIENT MEMORY.", LogType.ERROR)
            return
        }

        val updatedInventory = state.inventory.toMutableList()
        updatedInventory.add(name)

        _uiState.update { stateNow ->
            stateNow.copy(
                credits = stateNow.credits - cost,
                inventory = updatedInventory
            )
        }
        onLog("DOWNLOAD COMPLETE: $name retrieved to virtual storage.", LogType.SUCCESS)
    }

    // ----------------------------------------------------
    // Cybernetic Implants & Surgery System
    // ----------------------------------------------------

    fun selectStartingImplant(implant: CyberwareImplant) {
        _uiState.update { it.copy(selectedStartingImplant = implant) }
    }

    fun openCyberwareClinic() {
        _uiState.update { it.copy(screen = ActiveScreen.CYBERWARE_CLINIC) }
    }

    fun closeCyberwareClinic() {
        _uiState.update { it.copy(screen = ActiveScreen.EXPLORATION) }
    }

    fun installImplant(implant: CyberwareImplant): Boolean {
        val state = uiState
        val currentSlotImplant = state.installedImplants[implant.slot]

        val newMaxHp = (state.maxIntegrity - (currentSlotImplant?.integrityBonus ?: 0) + implant.integrityBonus).coerceAtLeast(10)
        val newHp = (state.integrity + implant.integrityBonus).coerceIn(1, newMaxHp)
        val newMaxRam = (state.maxRam - (currentSlotImplant?.ramBonus ?: 0) + implant.ramBonus).coerceAtLeast(2)
        val newRam = (state.ram + implant.ramBonus).coerceIn(1, newMaxRam)
        val newRecovery = (state.ramRecoveryRate - (currentSlotImplant?.recoveryBonus ?: 0) + implant.recoveryBonus).coerceAtLeast(1)
        val newDmg = (state.damageBonus - (currentSlotImplant?.damageBonus ?: 0) + implant.damageBonus).coerceAtLeast(0)
        val newDef = (state.defenseBonus - (currentSlotImplant?.defenseBonus ?: 0) + implant.defenseBonus).coerceAtLeast(0)

        val updatedImplants = state.installedImplants.toMutableMap()
        updatedImplants[implant.slot] = implant

        _uiState.update {
            it.copy(
                installedImplants = updatedImplants,
                maxIntegrity = newMaxHp,
                integrity = newHp,
                maxRam = newMaxRam,
                ram = newRam,
                ramRecoveryRate = newRecovery,
                damageBonus = newDmg,
                defenseBonus = newDef
            )
        }

        onLog("${implant.icon} IMPLANT SURGERY SUCCESS: Installed ${implant.name} into [${implant.slot.displayName.uppercase()}].", LogType.SUCCESS)
        if (implant.passiveAbility != null) {
            onLog("  └ PASSIVE ABILITY ACTIVATED: ${implant.passiveAbility.title} - ${implant.passiveAbility.description}", LogType.INFO)
        }
        soundManager.playCyberwareInstallSound()
        onSave()
        return true
    }

    fun uninstallImplant(slot: ImplantBodySlot): Boolean {
        val state = uiState
        val implant = state.installedImplants[slot] ?: return false

        val newMaxHp = (state.maxIntegrity - implant.integrityBonus).coerceAtLeast(10)
        val newHp = state.integrity.coerceAtMost(newMaxHp)
        val newMaxRam = (state.maxRam - implant.ramBonus).coerceAtLeast(2)
        val newRam = state.ram.coerceAtMost(newMaxRam)
        val newRecovery = (state.ramRecoveryRate - implant.recoveryBonus).coerceAtLeast(1)
        val newDmg = (state.damageBonus - implant.damageBonus).coerceAtLeast(0)
        val newDef = (state.defenseBonus - implant.defenseBonus).coerceAtLeast(0)

        val updatedImplants = state.installedImplants.toMutableMap()
        updatedImplants.remove(slot)

        _uiState.update {
            it.copy(
                installedImplants = updatedImplants,
                maxIntegrity = newMaxHp,
                integrity = newHp,
                maxRam = newMaxRam,
                ram = newRam,
                ramRecoveryRate = newRecovery,
                damageBonus = newDmg,
                defenseBonus = newDef
            )
        }

        onLog("🔌 CYBERWARE REMOVED: Uninstalled ${implant.name} from [${slot.displayName.uppercase()}].", LogType.ALERT)
        onSave()
        return true
    }

    fun toggleCyberwareInventoryOverlay(show: Boolean? = null) {
        _uiState.update { state ->
            val next = show ?: !state.showCyberwareInventoryOverlay
            state.copy(showCyberwareInventoryOverlay = next)
        }
    }

    fun setSelectedOverlayTab(tab: String) {
        _uiState.update { it.copy(selectedOverlayTab = tab) }
    }

    fun setSelectedOverlaySlotFilter(slot: ImplantBodySlot?) {
        _uiState.update { it.copy(selectedOverlaySlotFilter = slot) }
    }

    fun equipImplantFromInventory(implant: CyberwareImplant): Boolean {
        val state = uiState
        val storedList = state.storedImplants.toMutableList()
        val index = storedList.indexOfFirst { it.id == implant.id }
        if (index != -1) {
            storedList.removeAt(index)
        }

        val currentSlotImplant = state.installedImplants[implant.slot]
        if (currentSlotImplant != null) {
            storedList.add(currentSlotImplant)
            onLog("🔄 SWAPPED: Returned ${currentSlotImplant.name} to storage core.", LogType.INFO)
        }

        val newMaxHp = (state.maxIntegrity - (currentSlotImplant?.integrityBonus ?: 0) + implant.integrityBonus).coerceAtLeast(10)
        val newHp = (state.integrity + implant.integrityBonus).coerceIn(1, newMaxHp)
        val newMaxRam = (state.maxRam - (currentSlotImplant?.ramBonus ?: 0) + implant.ramBonus).coerceAtLeast(2)
        val newRam = (state.ram + implant.ramBonus).coerceIn(1, newMaxRam)
        val newRecovery = (state.ramRecoveryRate - (currentSlotImplant?.recoveryBonus ?: 0) + implant.recoveryBonus).coerceAtLeast(1)
        val newDmg = (state.damageBonus - (currentSlotImplant?.damageBonus ?: 0) + implant.damageBonus).coerceAtLeast(0)
        val newDef = (state.defenseBonus - (currentSlotImplant?.defenseBonus ?: 0) + implant.defenseBonus).coerceAtLeast(0)

        val updatedInstalled = state.installedImplants.toMutableMap()
        updatedInstalled[implant.slot] = implant

        _uiState.update {
            it.copy(
                installedImplants = updatedInstalled,
                storedImplants = storedList,
                maxIntegrity = newMaxHp,
                integrity = newHp,
                maxRam = newMaxRam,
                ram = newRam,
                ramRecoveryRate = newRecovery,
                damageBonus = newDmg,
                defenseBonus = newDef
            )
        }

        onLog("🦾 EQUIPPED: Fitted ${implant.name} into [${implant.slot.displayName.uppercase()}].", LogType.SUCCESS)
        if (implant.passiveAbility != null) {
            onLog("  └ PASSIVE ONLINE: ${implant.passiveAbility.title} - ${implant.passiveAbility.description}", LogType.INFO)
        }
        soundManager.playCyberwareInstallSound()
        onSave()
        return true
    }

    fun unequipImplantToInventory(slot: ImplantBodySlot): Boolean {
        val state = uiState
        val implant = state.installedImplants[slot] ?: return false

        val newMaxHp = (state.maxIntegrity - implant.integrityBonus).coerceAtLeast(10)
        val newHp = state.integrity.coerceAtMost(newMaxHp)
        val newMaxRam = (state.maxRam - implant.ramBonus).coerceAtLeast(2)
        val newRam = state.ram.coerceAtMost(newMaxRam)
        val newRecovery = (state.ramRecoveryRate - implant.recoveryBonus).coerceAtLeast(1)
        val newDmg = (state.damageBonus - implant.damageBonus).coerceAtLeast(0)
        val newDef = (state.defenseBonus - implant.defenseBonus).coerceAtLeast(0)

        val updatedInstalled = state.installedImplants.toMutableMap()
        updatedInstalled.remove(slot)

        val updatedStored = state.storedImplants.toMutableList()
        updatedStored.add(implant)

        _uiState.update {
            it.copy(
                installedImplants = updatedInstalled,
                storedImplants = updatedStored,
                maxIntegrity = newMaxHp,
                integrity = newHp,
                maxRam = newMaxRam,
                ram = newRam,
                ramRecoveryRate = newRecovery,
                damageBonus = newDmg,
                defenseBonus = newDef
            )
        }

        onLog("📦 STORED: Unfitted ${implant.name} from [${slot.displayName.uppercase()}] to storage core.", LogType.ALERT)
        onSave()
        return true
    }

    fun scavengeSampleImplant() {
        val all = CyberwareImplantRegistry.ALL_IMPLANTS
        val randomImplant = all.random()
        val updatedStored = uiState.storedImplants + randomImplant
        _uiState.update { it.copy(storedImplants = updatedStored) }
        onLog("🎁 SCAVENGED CYBERWARE: Acquired ${randomImplant.name} [${randomImplant.rarity.displayName}].", LogType.SUCCESS)
        onSave()
    }

    // ----------------------------------------------------
    // Terminal Command Handler
    // ----------------------------------------------------

    fun runTerminalCommand(parts: List<String>, state: GameViewModel.GameUiState): Boolean {
        if (parts.isEmpty()) return false

        val command = parts[0].lowercase()
        return when (command) {
            "inventory", "inv" -> {
                val slots = getStructuredInventorySlots()
                onLog("═══ INVENTORY CORE [${slots.size} unique items] ═══", LogType.INFO)
                for (slot in slots) {
                    val equippedTag = if (slot.isEquipped) " [EQUIPPED]" else ""
                    onLog("  ${slot.item.icon} ${slot.item.name} x${slot.quantity}${equippedTag} (${slot.item.rarity.displayName})", LogType.INFO)
                }
                true
            }
            "equip" -> {
                val itemName = parts.drop(1).joinToString(" ")
                if (itemName.isBlank()) {
                    onLog("USAGE: equip <item_name>", LogType.ERROR)
                    true
                } else {
                    equipItem(itemName)
                    true
                }
            }
            "unequip" -> {
                val slotName = parts.getOrElse(1) { "" }.uppercase()
                val slot = try {
                    EquipmentSlot.valueOf(slotName)
                } catch (_: Exception) {
                    null
                }
                if (slot == null) {
                    onLog("USAGE: unequip <WEAPON|ARMOR|UTILITY>", LogType.ERROR)
                } else {
                    unequipItemSlot(slot)
                }
                true
            }
            "use" -> {
                val itemName = parts.drop(1).joinToString(" ")
                if (itemName.isBlank()) {
                    onLog("USAGE: use <item_name>", LogType.ERROR)
                } else {
                    useInventoryItem(itemName)
                }
                true
            }
            "scavenge" -> {
                scavengeCurrentCell()
                true
            }
            "shop" -> {
                val subCommand = parts.getOrElse(1) { "" }.lowercase()
                when (subCommand) {
                    "enter", "open" -> enterShop()
                    "exit", "close" -> exitShop()
                    else -> onLog("USAGE: shop <enter|exit>", LogType.ERROR)
                }
                true
            }
            "buy" -> {
                val itemName = parts.drop(1).joinToString(" ")
                if (itemName.isBlank()) {
                    onLog("USAGE: buy <item_name>", LogType.ERROR)
                } else {
                    val item = GameItemRegistry.getItemByName(itemName)
                    val cost = item.valueCredits
                    if (cost > 0) {
                        purchaseConsumable(itemName, cost)
                    } else {
                        onLog("PURCHASE FAILED: '$itemName' not available for direct purchase. Use the shop UI for cyberware.", LogType.ERROR)
                    }
                }
                true
            }
            "install" -> {
                val implantName = parts.drop(1).joinToString(" ")
                if (implantName.isBlank()) {
                    onLog("USAGE: install <implant_name>", LogType.ERROR)
                } else {
                    val implant = CyberwareImplantRegistry.ALL_IMPLANTS.firstOrNull {
                        it.name.equals(implantName, ignoreCase = true)
                    }
                    if (implant != null) {
                        installImplant(implant)
                    } else {
                        onLog("INSTALL FAILED: Implant '$implantName' not found in registry.", LogType.ERROR)
                    }
                }
                true
            }
            "uninstall" -> {
                val slotName = parts.getOrElse(1) { "" }.uppercase()
                val slot = try {
                    ImplantBodySlot.valueOf(slotName)
                } catch (_: Exception) {
                    null
                }
                if (slot == null) {
                    onLog("USAGE: uninstall <implant_slot>", LogType.ERROR)
                } else {
                    uninstallImplant(slot)
                }
                true
            }
            else -> false
        }
    }
}
