package com.example.ui

import com.example.audio.CyberSoundEffectsManager
import com.example.data.*
import com.example.data.svdag.IceAlertLevel
import com.example.data.svdag.SvdagIcePathfinder
import com.example.data.svdag.SvdagScannerService
import com.example.data.svdag.SvdagWorldBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

class CombatManager(
    private val _uiState: MutableStateFlow<GameViewModel.GameUiState>,
    private val soundManager: CyberSoundEffectsManager,
    private val scope: CoroutineScope,
    private val onLog: (String, LogType) -> Unit,
    private val onSave: () -> Unit,
    private val onAddExperience: (Int) -> Unit,
    private val onVictoryCleanup: (Enemy) -> Unit,
    private val onGameOver: (String) -> Unit
) {
    private val uiState get() = _uiState.value
    private var combatHackTimerJob: Job? = null

    fun triggerCombat(targetX: Int, targetY: Int) {
        if (uiState.gameState != GameState.EXPLORATION) {
            onLog("⚠️ ALREADY IN COMBAT: Cannot initiate new engagement.", LogType.ALERT)
            return
        }
        val level = uiState.level
        val enemy = GameEngine.spawnEnemy(level)

        _uiState.update { state ->
            val baseCombatShield = if (state.runnerClass == NetrunnerClass.CYBER_SHIELD) {
                minOf(state.playerMaxShield, state.playerShield + 30)
            } else {
                state.playerShield
            }
            state.copy(
                gameState = GameState.COMBAT_START,
                activeEnemy = enemy,
                playerShield = baseCombatShield,
                targetNodeX = targetX,
                targetNodeY = targetY,
                enemyCombatAction = "",
                combatTurn = CombatTurn.PLAYER,
                combatRound = 1,
                turnPhase = TurnPhase.PLAYER_INPUT,
                playerActionHistory = emptyList(),
                enemyTurnHistory = emptyList(),
                allTurnActions = emptyList(),
                lastPlayerActionRecord = null,
                lastEnemyActionRecord = null,
                totalPlayerActionsCount = 0,
                totalEnemyTurnsCount = 0,
                showCombatBanner = "⚔️ SYSTEM OVERLOAD INTRUSION",
                isCombatInputEnabled = false,
                combatFlashEnemy = false,
                combatFlashPlayer = false,
                combatScreenShake = false,
                playerDamagePopup = null,
                enemyDamagePopup = null,
                showShieldEffect = false,
                enemyAttackCharge = 0f,
                activeFirewallTimeLeft = 0,
                playerStatusEffects = emptyList(),
                enemyStatusEffects = enemy.statusEffects.toList(),
                defendCooldown = 0,
                attackCooldown = 0,
                programCooldowns = emptyMap()
            )
        }

        onLog("==========================================", LogType.ERROR)
        onLog("⚠️ SECURITY INTRUSION THREAT TRIGGERED: ${enemy.name}!", LogType.ERROR)
        onLog("DESCRIPTION: ${enemy.description}", LogType.ALERT)
        onLog("SYSTEM DETECTED RECALIBRATION: INITIATING TURN-BASED COMBAT.", LogType.INFO)

        if (uiState.runnerClass == NetrunnerClass.CYBER_SHIELD) {
            onLog("SENTINEL PROTOCOL: +30 Shield initialized.", LogType.SUCCESS)
        }

        scope.launch {
            delay(1200)
            _uiState.update { it.copy(showCombatBanner = null, isCombatInputEnabled = true, gameState = GameState.PLAYER_TURN, turnPhase = TurnPhase.PLAYER_INPUT) }
        }
    }

    fun combatAttack() {
        if (!uiState.isCombatInputEnabled) return
        val state = uiState
        val enemy = state.activeEnemy ?: return

        if (processPlayerTurnStatusEffects()) {
            onLog("⚡ TURN SKIPPED: Player is STUNNED!", LogType.ALERT)
            recordPlayerAction(actionType = CombatActionType.PASS, summary = "Turn skipped due to STUN effect")
            executeEnemyCombatTurn()
            return
        }

        scope.launch {
            _uiState.update { it.copy(weaponSwingProgress = 0.2f, weaponSwingType = "Strike") }
            delay(60)
            _uiState.update { it.copy(weaponSwingProgress = 0.7f) }
            delay(60)
            _uiState.update { it.copy(weaponSwingProgress = 1.0f) }
            delay(100)
            _uiState.update { it.copy(weaponSwingProgress = 0.5f) }
            delay(60)
            _uiState.update { it.copy(weaponSwingProgress = 0f) }
        }

        scope.launch {
            onLog("> Striking with ${state.equippedWeaponName}...", LogType.INFO)

            val baseHitChance = 75
            val hitBonus = (state.level * 2) + (state.ram * 1)
            val finalHitChance = (baseHitChance + hitBonus).coerceIn(20, 95)
            val roll = Random.nextInt(100)

            if (roll >= finalHitChance) {
                onLog("⚔️ MISS! Your weapon swung wide. [Rolled: $roll vs Chance: $finalHitChance%]", LogType.ALERT)
                recordPlayerAction(actionType = CombatActionType.STRIKE, summary = "Strike swung wide and missed", isMiss = true)
                _uiState.update { it.copy(enemyDamagePopup = "MISS", combatFlashEnemy = false) }
                delay(400)
                _uiState.update { it.copy(enemyDamagePopup = null) }
                executeEnemyCombatTurn()
                return@launch
            }

            val baseDmg = 18
            val statPower = (state.level * 2) + state.damageBonus
            var rawPlayerDamage = baseDmg + statPower

            val isOverclocked = state.playerStatusEffects.any { it.type == StatusEffectType.BUFFED }
            val isGlitched = state.playerStatusEffects.any { it.type == StatusEffectType.WEAKENED }
            if (isOverclocked) { rawPlayerDamage = (rawPlayerDamage * 1.5f).toInt(); onLog("🔥 OVERCLOCKED: Attack payload amplified by 50%!", LogType.SUCCESS) }
            if (isGlitched) { rawPlayerDamage = (rawPlayerDamage * 0.5f).toInt(); onLog("🌀 GLITCHED: Attack output reduced by 50%!", LogType.ALERT) }

            val hasSynapticOverclock = state.installedImplants.values.any { it?.passiveAbility == ImplantAbility.SYNAPTIC_OVERCLOCK }
            if (hasSynapticOverclock && state.ram < 3) {
                rawPlayerDamage = (rawPlayerDamage * 1.25f).toInt()
                onLog("🔥 SYNAPTIC OVERCLOCK IMPLANT: +25% payload damage boosted by low RAM threshold!", LogType.SUCCESS)
            }

            val standCell = state.maze.getOrNull(state.gridY)?.getOrNull(state.gridX)
            if (standCell == CellType.ELEVATED_BALCONY) {
                rawPlayerDamage = (rawPlayerDamage * 1.25f).toInt()
                onLog("✨ BALCONY VANTAGE ACTIVE: Swing amplified from balcony overlooking!", LogType.SUCCESS)
            }

            var isCrit = false
            val critRate = 10 + (state.ram * 2)
            val hasCritTargeting = state.installedImplants.values.any { it?.passiveAbility == ImplantAbility.CRIT_TARGETING }
            val implantCritBonus = if (hasCritTargeting) 20 else 0
            val finalCritRate = (if (state.runnerClass == NetrunnerClass.CODE_SLASHER) critRate + 25 else critRate) + implantCritBonus
            if (Random.nextInt(100) < finalCritRate) {
                isCrit = true
                val critMultiplier = if (state.runnerClass == NetrunnerClass.CODE_SLASHER) 2.0f else 1.5f
                rawPlayerDamage = (rawPlayerDamage * critMultiplier).toInt()
            }

            val enemyArmor = enemy.armor
            val effectiveArmor = if (isCrit) (enemyArmor * 0.5f).toInt() else enemyArmor
            var finalDmg = maxOf(3, rawPlayerDamage - effectiveArmor)

            val isEnemyFortified = enemy.statusEffects.any { it.type == StatusEffectType.FORTIFIED }
            if (isEnemyFortified) { finalDmg = (finalDmg * 0.5f).toInt().coerceAtLeast(1); onLog("🛡️ HOSTILE FORTIFIED: Damage absorbed by enemy defense grid (-50%).", LogType.ALERT) }

            val enemyRemShield = maxOf(0, enemy.shield - finalDmg)
            val shieldDmg = enemy.shield - enemyRemShield
            val bodyDmg = finalDmg - shieldDmg
            val enemyRemIntegrity = maxOf(0, enemy.integrity - bodyDmg)
            enemy.shield = enemyRemShield
            enemy.integrity = enemyRemIntegrity

            recordPlayerAction(actionType = CombatActionType.STRIKE, summary = "Strike dealt $finalDmg damage (Shield: -$shieldDmg, HP: -$bodyDmg)", damageDealt = finalDmg, shieldAbsorbed = shieldDmg, isCrit = isCrit)

            if (isCrit) { soundManager.playCombatCritSound(); onLog("💥 CRITICAL HIT! Double damage bypassed $effectiveArmor hostile armor!", LogType.SUCCESS) }
            else { soundManager.playCombatHitSound() }
            onLog("⚔️ HIT! Dealt $finalDmg damage to ${enemy.name} (Shield: -$shieldDmg, HP: -$bodyDmg) [Roll: $roll vs Chance: $finalHitChance%]", LogType.SUCCESS)

            _uiState.update { it.copy(combatFlashEnemy = true, enemyDamagePopup = "-$finalDmg HP") }
            delay(400)
            _uiState.update { it.copy(combatFlashEnemy = false, enemyDamagePopup = null) }

            if (enemy.integrity <= 0) {
                _uiState.update { it.copy(showCombatBanner = "🏆 VICTORY", isCombatInputEnabled = false, turnPhase = TurnPhase.COMBAT_VICTORY) }
                delay(1200)
                handleCombatVictory(enemy)
                _uiState.update { it.copy(showCombatBanner = null, isCombatInputEnabled = true) }
            } else {
                executeEnemyCombatTurn()
            }
        }
    }

    fun combatDefend() {
        if (!uiState.isCombatInputEnabled) return
        if (processPlayerTurnStatusEffects()) {
            onLog("⚡ TURN SKIPPED: Player is STUNNED!", LogType.ALERT)
            recordPlayerAction(actionType = CombatActionType.PASS, summary = "Turn skipped due to STUN effect")
            executeEnemyCombatTurn()
            return
        }

        val state = uiState
        val shieldHeal = 15 + (state.level * 3)
        _uiState.update { it.copy(playerShield = minOf(it.playerMaxShield, it.playerShield + shieldHeal), activeFirewallTimeLeft = 1, showShieldEffect = true) }
        applyStatusEffectToPlayer(StatusEffectType.FORTIFIED, turns = 1, source = "Defensive Firewall")
        onLog("🛡️ ACTIVE FIREWALL INITIATED: Damage incoming in the next turn reduced by 75%!", LogType.SUCCESS)
        recordPlayerAction(actionType = CombatActionType.DEFEND, summary = "Active Firewall initiated (+$shieldHeal Shield, Fortified)", shieldAbsorbed = shieldHeal, statusApplied = "Fortified")

        scope.launch {
            delay(600)
            _uiState.update { it.copy(showShieldEffect = false) }
            executeEnemyCombatTurn()
        }
    }

    fun combatHack() {
        if (!uiState.isCombatInputEnabled) return
        val state = uiState
        val enemy = state.activeEnemy ?: return
        if (state.activeCombatHack != null) return

        if (processPlayerTurnStatusEffects()) {
            onLog("⚡ TURN SKIPPED: Player is STUNNED!", LogType.ALERT)
            executeEnemyCombatTurn()
            return
        }
        if (state.ram < 3) { onLog("HACK PROTOCOL ABORTED: Needs 3 MB RAM.", LogType.ERROR); soundManager.playHackingErrorSound(); return }

        _uiState.update { it.copy(ram = it.ram - 3) }
        val symbolPool = listOf("1C", "E9", "55", "7A", "BD", "FF", "30", "A3", "2D", "0F")
        val patternLength = minOf(3 + (state.level / 2), 5)
        val shuffledPool = symbolPool.shuffled()
        val targetPattern = shuffledPool.take(patternLength)
        val keypadPool = (targetPattern + shuffledPool.drop(patternLength)).distinct().take(8).shuffled()
        val potentialDamage = 32 + (state.level * 5) + state.damageBonus
        val maxTime = maxOf(8, 14 - (state.level / 2))

        val hackState = CombatHackingPatternState(targetPattern = targetPattern, userSequence = emptyList(), availablePool = keypadPool, timeRemainingSeconds = maxTime, maxTimeSeconds = maxTime, attemptsRemaining = 3, maxAttempts = 3, enemyName = enemy.name, potentialDamage = potentialDamage)
        _uiState.update { it.copy(activeCombatHack = hackState) }
        soundManager.playNodeBreachSound()
        onLog("--- BREACH PROTOCOL INITIATED ---", LogType.ALERT)
        onLog("MATCH TARGET HEX SEQUENCE TO OVERRIDE ${enemy.name.uppercase()} FIREWALL!", LogType.INFO)
        startCombatHackTimer()
    }

    private fun startCombatHackTimer() {
        combatHackTimerJob?.cancel()
        combatHackTimerJob = scope.launch {
            while (isActive) {
                delay(1000)
                val currentHack = _uiState.value.activeCombatHack ?: break
                val newTime = currentHack.timeRemainingSeconds - 1
                if (newTime <= 0) {
                    _uiState.update { it.copy(activeCombatHack = currentHack.copy(timeRemainingSeconds = 0)) }
                    handleCombatHackFailure(currentHack, "EXPLOIT TIMED OUT! Firewall trace completed.")
                    break
                } else {
                    _uiState.update { it.copy(activeCombatHack = currentHack.copy(timeRemainingSeconds = newTime)) }
                }
            }
        }
    }

    fun selectCombatHackSymbol(symbol: String) {
        val currentHack = _uiState.value.activeCombatHack ?: return
        val updatedSeq = currentHack.userSequence + symbol.uppercase()
        soundManager.playBufferShiftSound()
        val isPrefixMatch = currentHack.targetPattern.take(updatedSeq.size) == updatedSeq
        if (isPrefixMatch) {
            if (updatedSeq.size == currentHack.targetPattern.size) {
                combatHackTimerJob?.cancel()
                _uiState.update { it.copy(activeCombatHack = currentHack.copy(userSequence = updatedSeq)) }
                handleCombatHackSuccess(currentHack)
            } else {
                _uiState.update { it.copy(activeCombatHack = currentHack.copy(userSequence = updatedSeq)) }
            }
        } else {
            soundManager.playHackingErrorSound()
            val newAttempts = currentHack.attemptsRemaining - 1
            onLog("SECURITY REJECT: Mismatched symbol '$symbol'! Attempts left: $newAttempts", LogType.ERROR)
            if (newAttempts <= 0) { combatHackTimerJob?.cancel(); handleCombatHackFailure(currentHack, "BREACH COUNTERMEASURES TRIGGERED! Out of attempts.") }
            else { _uiState.update { it.copy(activeCombatHack = currentHack.copy(userSequence = emptyList(), attemptsRemaining = newAttempts)) } }
        }
    }

    fun clearCombatHackBuffer() {
        val currentHack = _uiState.value.activeCombatHack ?: return
        soundManager.playTerminalKeyPressSound()
        _uiState.update { it.copy(activeCombatHack = currentHack.copy(userSequence = emptyList())) }
        onLog("HACK BUFFER CLEARED.", LogType.INFO)
    }

    fun abortCombatHack() {
        val currentHack = _uiState.value.activeCombatHack ?: return
        combatHackTimerJob?.cancel()
        soundManager.playHackingErrorSound()
        handleCombatHackFailure(currentHack, "EXPLOIT ABORTED BY OPERATOR.")
    }

    private fun handleCombatHackSuccess(hackState: CombatHackingPatternState) {
        val enemy = _uiState.value.activeEnemy ?: return
        scope.launch {
            soundManager.playHackingSuccessSound()
            val hackDmg = hackState.potentialDamage
            enemy.integrity = maxOf(0, enemy.integrity - hackDmg)
            _uiState.update { it.copy(activeCombatHack = null, combatFlashEnemy = true, enemyDamagePopup = "-$hackDmg HP (CRIT EXPLOIT)") }
            onLog("PATTERNS MATCHED PERFECTLY! FIREWALL OVERRIDDEN!", LogType.SUCCESS)
            onLog("Dealt $hackDmg system-penetrating exploit damage & STUNNED ${enemy.name}!", LogType.SUCCESS)
            applyStatusEffectToEnemy(StatusEffectType.STUNNED, turns = 1, source = "Breach Protocol")
            recordPlayerAction(actionType = CombatActionType.QUICK_HACK, summary = "Breach exploit overrode firewall dealing $hackDmg damage & Stun", damageDealt = hackDmg, isCrit = true, statusApplied = "Stunned")
            delay(500)
            _uiState.update { it.copy(combatFlashEnemy = false, enemyDamagePopup = null) }
            if (enemy.integrity <= 0) {
                _uiState.update { it.copy(showCombatBanner = "🏆 VICTORY", isCombatInputEnabled = false, turnPhase = TurnPhase.COMBAT_VICTORY) }
                delay(1200); handleCombatVictory(enemy); _uiState.update { it.copy(showCombatBanner = null, isCombatInputEnabled = true) }
            } else { executeEnemyCombatTurn() }
        }
    }

    private fun handleCombatHackFailure(hackState: CombatHackingPatternState, reason: String) {
        val enemy = _uiState.value.activeEnemy
        scope.launch {
            soundManager.playHackingErrorSound()
            onLog(reason, LogType.ALERT)
            if (enemy != null) {
                val fallbackDmg = maxOf(5, hackState.potentialDamage / 3)
                enemy.integrity = maxOf(0, enemy.integrity - fallbackDmg)
                _uiState.update { it.copy(activeCombatHack = null, combatFlashEnemy = true, enemyDamagePopup = "-$fallbackDmg HP (PARTIAL)") }
                onLog("Partial feedback breach dealt $fallbackDmg damage to ${enemy.name}.", LogType.ALERT)
                recordPlayerAction(actionType = CombatActionType.QUICK_HACK, summary = "Partial exploit feedback dealt $fallbackDmg damage", damageDealt = fallbackDmg)
                delay(500)
                _uiState.update { it.copy(combatFlashEnemy = false, enemyDamagePopup = null) }
                if (enemy.integrity <= 0) {
                    _uiState.update { it.copy(showCombatBanner = "🏆 VICTORY", isCombatInputEnabled = false, turnPhase = TurnPhase.COMBAT_VICTORY) }
                    delay(1200); handleCombatVictory(enemy); _uiState.update { it.copy(showCombatBanner = null, isCombatInputEnabled = true) }
                } else { executeEnemyCombatTurn() }
            } else { _uiState.update { it.copy(activeCombatHack = null) } }
        }
    }

    fun combatScan() {
        if (!uiState.isCombatInputEnabled) return
        val state = uiState
        val enemy = state.activeEnemy ?: return
        onLog("--- SCANNING TARGET PROCESS DATA ---", LogType.ALERT)
        onLog("NAME: ${enemy.name} | CLASS: Cyber-Entity Layer ${state.level}", LogType.INFO)
        onLog("FIREWALL SHELL: ${enemy.shield}/${enemy.maxShield} (Armor Rating: ${enemy.armor})", LogType.INFO)
        onLog("CORE DATA: ${enemy.integrity}/${enemy.maxIntegrity} | ATK MODULE: ${enemy.damage}", LogType.INFO)
        onLog("ANALYSIS COMPLETE: Signal feedback scrambled enemy telemetry! Target Glitched.", LogType.SUCCESS)
        applyStatusEffectToEnemy(StatusEffectType.WEAKENED, turns = 2, source = "Deep Telemetry Scan")
        recordPlayerAction(actionType = CombatActionType.SCAN, summary = "Deep Telemetry Scan weakened ${enemy.name}", statusApplied = "Weakened")
        scope.launch {
            _uiState.update { it.copy(enemyCombatAction = "Scan complete. Hostile systems recalibrating.", combatFlashEnemy = true) }
            delay(400)
            _uiState.update { it.copy(combatFlashEnemy = false) }
            executeEnemyCombatTurn(isScanStunned = true)
        }
    }

    fun endTurn() {
        if (!uiState.isCombatInputEnabled) return
        if (uiState.activeEnemy == null) return
        onLog("PASSING TURN: Player manually terminated their phase.", LogType.INFO)
        recordPlayerAction(actionType = CombatActionType.PASS, summary = "Manually terminated turn phase")
        executeEnemyCombatTurn()
    }

    fun executeCombatProgram(program: Program) {
        if (!uiState.isCombatInputEnabled) return
        val state = uiState
        val enemy = state.activeEnemy ?: return
        if (processPlayerTurnStatusEffects()) {
            onLog("⚡ TURN SKIPPED: Player is STUNNED!", LogType.ALERT)
            recordPlayerAction(actionType = CombatActionType.PASS, summary = "Turn skipped due to STUN effect")
            executeEnemyCombatTurn()
            return
        }
        if (state.ram < program.ramCost) { onLog("INSUFFICIENT RAM: Requires ${program.ramCost}MB, but only ${state.ram}MB allocated.", LogType.ERROR); return }
        _uiState.update { it.copy(ram = it.ram - program.ramCost) }

        scope.launch {
            onLog("> RUNNING ${program.name}...", LogType.INFO)
            val baseDmg = program.damage
            val statPower = (state.level * 2) + state.damageBonus
            var rawPlayerDamage = baseDmg + statPower

            val isOverclocked = state.playerStatusEffects.any { it.type == StatusEffectType.BUFFED }
            val isGlitched = state.playerStatusEffects.any { it.type == StatusEffectType.WEAKENED }
            if (isOverclocked) { rawPlayerDamage = (rawPlayerDamage * 1.5f).toInt(); onLog("🔥 OVERCLOCKED: Program damage amplified by 50%!", LogType.SUCCESS) }
            if (isGlitched) { rawPlayerDamage = (rawPlayerDamage * 0.5f).toInt(); onLog("🌀 GLITCHED: Program payload reduced by 50%!", LogType.ALERT) }

            val standCell = state.maze.getOrNull(state.gridY)?.getOrNull(state.gridX)
            if (standCell == CellType.ELEVATED_BALCONY) { rawPlayerDamage = (rawPlayerDamage * 1.25f).toInt(); onLog("✨ BALCONY VANTAGE ACTIVE: Attack payload magnified by 25% from high-level gallery overlook!", LogType.SUCCESS) }

            var isCrit = false
            val critRate = 10 + (state.ram * 2)
            val finalCritRate = if (state.runnerClass == NetrunnerClass.CODE_SLASHER) critRate + 25 else critRate
            if (Random.nextInt(100) < finalCritRate) { isCrit = true; val critMultiplier = if (state.runnerClass == NetrunnerClass.CODE_SLASHER) 2.0f else 1.5f; rawPlayerDamage = (rawPlayerDamage * critMultiplier).toInt() }
            if (state.runnerClass == NetrunnerClass.BUFFER_OVERFLOW) { val mult = 1.0f + (state.ram * 0.03f); rawPlayerDamage = (rawPlayerDamage * mult).toInt() }

            val enemyArmor = enemy.armor
            val effectiveArmor = if (isCrit) (enemyArmor * 0.5f).toInt() else enemyArmor
            val finalDmg = if (program.piercesDefense) rawPlayerDamage else maxOf(2, rawPlayerDamage - effectiveArmor)
            val isEnemyFortified = enemy.statusEffects.any { it.type == StatusEffectType.FORTIFIED }
            val actualDmg = if (isEnemyFortified) { onLog("🛡️ HOSTILE FORTIFIED: Damage absorbed by enemy defense grid (-50%).", LogType.ALERT); (finalDmg * 0.5f).toInt().coerceAtLeast(1) } else finalDmg

            val enemyRemShield = maxOf(0, enemy.shield - actualDmg)
            val shieldDmg = enemy.shield - enemyRemShield
            val bodyDmg = actualDmg - shieldDmg
            enemy.shield = enemyRemShield
            enemy.integrity = maxOf(0, enemy.integrity - bodyDmg)

            recordPlayerAction(actionType = CombatActionType.PROGRAM, summary = "Executed ${program.name} dealing $actualDmg damage (Shield: -$shieldDmg, HP: -$bodyDmg)", damageDealt = actualDmg, shieldAbsorbed = shieldDmg, healAmount = program.heal, isCrit = isCrit, statusApplied = program.statusEffectToApply?.displayName)
            onLog("[CALC]: Base:${baseDmg} + Stats:${statPower} = Raw:${baseDmg + statPower}", LogType.INFO)
            if (isCrit) onLog("CRITICAL HIT! [x${if (state.runnerClass == NetrunnerClass.CODE_SLASHER) "2.0" else "1.5"}] Armor bypassed: $effectiveArmor/$enemyArmor", LogType.SUCCESS)
            if (actualDmg > 0) onLog("Dealt $actualDmg damage to ${enemy.name} (Shield: -$shieldDmg, Core: -$bodyDmg) [Hostile Armor: $enemyArmor]", LogType.SUCCESS)

            if (program.heal > 0) { val healed = minOf(state.maxIntegrity - state.integrity, program.heal); _uiState.update { it.copy(integrity = it.integrity + healed) }; onLog("System integrity patch compiled: +$healed% Integrity.", LogType.SUCCESS) }
            if (program.shield > 0) { val shieldHealed = minOf(state.playerMaxShield - state.playerShield, program.shield); _uiState.update { it.copy(playerShield = it.playerShield + shieldHealed) }; onLog("Temporary firewalls reinforced: +$shieldHealed Shield Barrier.", LogType.SUCCESS) }

            if (program.statusEffectToApply != null) {
                val turns = if (program.statusEffectTurns > 0) program.statusEffectTurns else 2
                if (program.statusEffectTargetSelf) applyStatusEffectToPlayer(program.statusEffectToApply, turns, program.statusEffectMagnitude, program.name)
                else applyStatusEffectToEnemy(program.statusEffectToApply, turns, program.statusEffectMagnitude, program.name)
            }

            _uiState.update { it.copy(combatFlashEnemy = actualDmg > 0, enemyDamagePopup = if (actualDmg > 0) "-$actualDmg HP" else null, showShieldEffect = program.heal > 0 || program.shield > 0) }
            delay(400)
            _uiState.update { it.copy(combatFlashEnemy = false, enemyDamagePopup = null, showShieldEffect = false) }

            if (enemy.integrity <= 0) {
                _uiState.update { it.copy(showCombatBanner = "🏆 VICTORY", isCombatInputEnabled = false, turnPhase = TurnPhase.COMBAT_VICTORY) }
                delay(1200); handleCombatVictory(enemy); _uiState.update { it.copy(showCombatBanner = null, isCombatInputEnabled = true) }
            } else { executeEnemyCombatTurn() }
        }
    }

    private fun executeEnemyCombatTurn(isScanStunned: Boolean = false) {
        scope.launch {
            _uiState.update { it.copy(combatTurn = CombatTurn.ENEMY, turnPhase = TurnPhase.ENEMY_RESOLVING) }
            val state = _uiState.value
            val enemy = state.activeEnemy ?: return@launch

            val activeEffects = enemy.statusEffects
            var isEnemyStunned = isScanStunned
            val remainingEnemyEffects = mutableListOf<ActiveStatusEffect>()
            for (effect in activeEffects) {
                when (effect.type) {
                    StatusEffectType.POISONED -> {
                        val dotDamage = if (effect.magnitude > 0) effect.magnitude else 8
                        enemy.integrity = maxOf(0, enemy.integrity - dotDamage)
                        _uiState.update { it.copy(enemyDamagePopup = "-$dotDamage HP (Corroded)", combatFlashEnemy = true, enemyStatusEffects = enemy.statusEffects.toList()) }
                        onLog("🧪 CORROSION TICK: ${enemy.name} took $dotDamage corrosion damage!", LogType.SUCCESS)
                        delay(300); _uiState.update { it.copy(enemyDamagePopup = null, combatFlashEnemy = false) }
                        if (enemy.integrity <= 0) { _uiState.update { it.copy(showCombatBanner = "🏆 VICTORY", isCombatInputEnabled = false, turnPhase = TurnPhase.COMBAT_VICTORY) }; delay(1000); handleCombatVictory(enemy); _uiState.update { it.copy(showCombatBanner = null, isCombatInputEnabled = true) }; return@launch }
                    }
                    StatusEffectType.STUNNED -> { isEnemyStunned = true; onLog("⚡ HOSTILE STUNNED: ${enemy.name} circuit frozen by electrical charge!", LogType.SUCCESS) }
                    else -> {}
                }
                val nextTurns = effect.turnsRemaining - 1
                if (nextTurns > 0) remainingEnemyEffects.add(effect.copy(turnsRemaining = nextTurns))
                else onLog("✨ EXPIRED STATUS: ${effect.type.displayName} effect on ${enemy.name} faded.", LogType.INFO)
            }
            enemy.statusEffects = remainingEnemyEffects
            _uiState.update { it.copy(enemyStatusEffects = remainingEnemyEffects.toList()) }

            if (isEnemyStunned) {
                onLog("⚡ ENEMY TURN SKIPPED: ${enemy.name} is paralyzed by STUN status!", LogType.SUCCESS)
                recordEnemyAction(actionType = CombatActionType.PASS, summary = "${enemy.name} was stunned and skipped turn")
                delay(600); processTurnMaintenance(); return@launch
            }

            var baseEnemyDmg = enemy.damage + Random.nextInt(-2, 3)
            if (baseEnemyDmg < 2) baseEnemyDmg = 2
            val isEnemyBuffed = activeEffects.any { it.type == StatusEffectType.BUFFED }
            val isEnemyWeakened = activeEffects.any { it.type == StatusEffectType.WEAKENED }
            val isPlayerFortified = state.playerStatusEffects.any { it.type == StatusEffectType.FORTIFIED }
            if (isEnemyBuffed) { baseEnemyDmg = (baseEnemyDmg * 1.5f).toInt(); onLog("🔥 HOSTILE OVERCLOCKED: Enemy damage boosted by 50%!", LogType.ERROR) }
            if (isEnemyWeakened) { baseEnemyDmg = (baseEnemyDmg * 0.5f).toInt().coerceAtLeast(1); onLog("🌀 HOSTILE GLITCHED: Enemy damage reduced by 50%!", LogType.SUCCESS) }
            if (isPlayerFortified) { baseEnemyDmg = (baseEnemyDmg * 0.5f).toInt().coerceAtLeast(1); onLog("🛡️ PLAYER FORTIFIED: Incoming attack damage halved by active barrier!", LogType.SUCCESS) }

            val standCell = state.maze.getOrNull(state.gridY)?.getOrNull(state.gridX)
            if (standCell == CellType.GRAVITY_SLOPE) { baseEnemyDmg = (baseEnemyDmg * 0.70f).toInt().coerceAtLeast(1); onLog("✨ GRAVITY EVASION: Magnetic slope rapid momentum absorbed 30% of incoming packet force!", LogType.SUCCESS) }

            var finalEnemyDmg = maxOf(1, baseEnemyDmg - state.defenseBonus)
            if (state.kineticShieldActiveThisCombat) { finalEnemyDmg = 0; _uiState.update { it.copy(kineticShieldActiveThisCombat = false) }; onLog("🛡️ KINETIC SHIELD IMPLANT: Subdermal kinetic barrier completely absorbed incoming attack!", LogType.SUCCESS) }

            val actions = listOf("Trojan injection stream", "Rootkit port scan exploit", "Distributed Denial-of-Service packets", "Logic logicbomb payload")
            val selectedAction = actions[Random.nextInt(actions.size)]
            val shieldDamage = minOf(state.playerShield, finalEnemyDmg)
            val integrityDamage = finalEnemyDmg - shieldDamage
            val newPlayerIntegrity = maxOf(0, state.integrity - integrityDamage)

            soundManager.playCombatHitSound()
            _uiState.update { it.copy(integrity = newPlayerIntegrity, playerShield = state.playerShield - shieldDamage, enemyCombatAction = "${enemy.name} ran $selectedAction: Dealt $finalEnemyDmg damage. (Shield absorbed: $shieldDamage, Core hit: $integrityDamage)", combatFlashPlayer = true, combatScreenShake = true, playerDamagePopup = if (finalEnemyDmg > 0) "-$finalEnemyDmg HP" else "ABSORBED") }
            recordEnemyAction(actionType = CombatActionType.STRIKE, summary = "${enemy.name} ran $selectedAction dealing $finalEnemyDmg damage", damageDealt = finalEnemyDmg, shieldAbsorbed = shieldDamage)
            onLog("${enemy.name} executes $selectedAction...", LogType.ERROR)
            if (shieldDamage > 0) onLog("Player Shield absorbed $shieldDamage damage.", LogType.ALERT)
            if (integrityDamage > 0) onLog("System Integrity degraded by $integrityDamage%.", LogType.ERROR)

            if (Random.nextInt(100) < 25) {
                val debuff = listOf(StatusEffectType.POISONED, StatusEffectType.WEAKENED, StatusEffectType.STUNNED).random()
                val turns = if (debuff == StatusEffectType.STUNNED) 1 else 2
                applyStatusEffectToPlayer(debuff, turns, 8, enemy.name)
            }

            delay(600); _uiState.update { it.copy(combatFlashPlayer = false, combatScreenShake = false, playerDamagePopup = null) }

            if (newPlayerIntegrity <= 0) {
                val hasEmergencyReboot = state.installedImplants.values.any { it?.passiveAbility == ImplantAbility.EMERGENCY_REBOOT }
                if (hasEmergencyReboot && !state.hasUsedEmergencyRebootThisRun) {
                    val revivedHp = (state.maxIntegrity * 0.25f).toInt().coerceAtLeast(15)
                    _uiState.update { it.copy(integrity = revivedHp, hasUsedEmergencyRebootThisRun = true, showCombatBanner = "⚡ EMERGENCY REBOOT") }
                    onLog("⚡ EMERGENCY REBOOT ACTIVATED! Synthetic Heart Nanites restarted runner core at $revivedHp HP!", LogType.SUCCESS)
                    soundManager.playLootCollectionSound(); delay(1000); _uiState.update { it.copy(showCombatBanner = null) }; processTurnMaintenance(); return@launch
                }
                _uiState.update { it.copy(showCombatBanner = "💀 DEFEAT", turnPhase = TurnPhase.COMBAT_DEFEAT) }
                delay(1200); onGameOver("Destroyed by security process ${enemy.name}"); _uiState.update { it.copy(showCombatBanner = null) }; return@launch
            }
            processTurnMaintenance()
        }
    }

    private fun handleCombatVictory(enemy: Enemy) {
        soundManager.playLootCollectionSound()
        val state = _uiState.value
        val lootDrop = CombatLootDropSystem.generateLootDrop(enemy.name, state.level, enemy.bountyCredits)
        onVictoryCleanup(enemy)
        val updatedInventory = state.inventory.toMutableList(); updatedInventory.add(lootDrop.itemName)
        _uiState.update { it.copy(gameState = GameState.EXPLORATION, credits = it.credits + lootDrop.totalCreditsEarned, totalCreditsEarned = it.totalCreditsEarned + lootDrop.totalCreditsEarned, inventory = updatedInventory, activeEnemy = null) }
        onLog("CRITICAL SUCCESS: PROCESS ${enemy.name} TERMINATED.", LogType.SUCCESS)
        onLog("Bounty extraction: +${lootDrop.totalCreditsEarned} MB credits compiled.", LogType.SUCCESS)
        onLog(lootDrop.logMessage, LogType.SUCCESS)
        onAddExperience(lootDrop.xpEarned)
    }

    private fun processTurnMaintenance() {
        _uiState.update { it.copy(combatRound = it.combatRound + 1, ram = minOf(it.maxRam, it.ram + it.ramRecoveryRate), defenseBonus = 0, activeFirewallTimeLeft = 0, turnPhase = TurnPhase.PLAYER_INPUT, combatTurn = CombatTurn.PLAYER, isCombatInputEnabled = true, gameState = GameState.PLAYER_TURN) }
    }

    fun applyStatusEffectToPlayer(type: StatusEffectType, turns: Int, magnitude: Int = 0, source: String = "") {
        val newEffect = ActiveStatusEffect(type = type, turnsRemaining = turns, magnitude = magnitude, sourceName = source)
        _uiState.update { state -> val updated = state.playerStatusEffects.filter { it.type != type }.toMutableList(); updated.add(newEffect); state.copy(playerStatusEffects = updated) }
        onLog("${type.icon} STATUS EFFECT INFLICTED ON PLAYER: ${type.displayName} ($turns turns)! ${type.description}", LogType.ALERT)
    }

    fun applyStatusEffectToEnemy(type: StatusEffectType, turns: Int, magnitude: Int = 0, source: String = "") {
        val enemy = _uiState.value.activeEnemy ?: return
        val newEffect = ActiveStatusEffect(type = type, turnsRemaining = turns, magnitude = magnitude, sourceName = source)
        val updatedEffects = enemy.statusEffects.filter { it.type != type }.toMutableList(); updatedEffects.add(newEffect)
        enemy.statusEffects = updatedEffects
        _uiState.update { it.copy(enemyStatusEffects = updatedEffects.toList()) }
        onLog("${type.icon} STATUS EFFECT APPLIED TO ${enemy.name.uppercase()}: ${type.displayName} ($turns turns)! ${type.description}", LogType.SUCCESS)
    }

    private fun processPlayerTurnStatusEffects(): Boolean {
        val state = uiState; val activeEffects = state.playerStatusEffects; if (activeEffects.isEmpty()) return false
        var isPlayerStunned = false; val remainingEffects = mutableListOf<ActiveStatusEffect>()
        for (effect in activeEffects) {
            when (effect.type) {
                StatusEffectType.POISONED -> { val dotDamage = if (effect.magnitude > 0) effect.magnitude else 8; val newIntegrity = maxOf(0, state.integrity - dotDamage); _uiState.update { it.copy(integrity = newIntegrity, playerDamagePopup = "-$dotDamage HP (Corroded)") }; onLog("🧪 CORROSION TICK: System integrity damaged by $dotDamage points!", LogType.ERROR) }
                StatusEffectType.STUNNED -> { isPlayerStunned = true; onLog("⚡ SYSTEM STUNNED: Circuit overload paralyzes action controls!", LogType.ALERT) }
                else -> {}
            }
            val nextTurns = effect.turnsRemaining - 1; if (nextTurns > 0) remainingEffects.add(effect.copy(turnsRemaining = nextTurns)) else onLog("✨ EXPIRED STATUS: ${effect.type.displayName} effect on player faded.", LogType.INFO)
        }
        _uiState.update { it.copy(playerStatusEffects = remainingEffects) }; return isPlayerStunned
    }

    fun addExperience(amount: Int) = onAddExperience(amount)

    fun fleeCombat() {
        val state = uiState; if (state.gameState == GameState.EXPLORATION) return
        val penalty = 20; val newCredits = maxOf(0, state.credits - penalty)
        _uiState.update { it.copy(screen = ActiveScreen.EXPLORATION, gameState = GameState.EXPLORATION, credits = newCredits, activeEnemy = null, gridX = 1, gridY = 1) }
        onLog("EMERGENCY ESCAPE ROUTE FLOODED. RETREATED TO PORT SECURE.", LogType.ALERT)
        onLog("Bypassed connection telemetry fees: -$penalty Credits.", LogType.ALERT)
    }

    private fun recordPlayerAction(actionType: CombatActionType, summary: String, damageDealt: Int = 0, shieldAbsorbed: Int = 0, healAmount: Int = 0, isCrit: Boolean = false, isMiss: Boolean = false, statusApplied: String? = null): TurnActionRecord {
        val currentRound = _uiState.value.combatRound
        val record = TurnActionRecord(roundNumber = currentRound, actorName = _uiState.value.runnerName.ifEmpty { "Player" }, isPlayer = true, actionType = actionType, summary = summary, damageDealt = damageDealt, shieldAbsorbed = shieldAbsorbed, healAmount = healAmount, isCrit = isCrit, isMiss = isMiss, statusApplied = statusApplied)
        _uiState.update { it.copy(playerActionHistory = it.playerActionHistory + record, allTurnActions = it.allTurnActions + record, lastPlayerActionRecord = record, totalPlayerActionsCount = it.totalPlayerActionsCount + 1, turnPhase = TurnPhase.PLAYER_RESOLVING) }
        return record
    }

    private fun recordEnemyAction(actionType: CombatActionType, summary: String, damageDealt: Int = 0, shieldAbsorbed: Int = 0, healAmount: Int = 0, isCrit: Boolean = false, isMiss: Boolean = false, statusApplied: String? = null): TurnActionRecord {
        val currentRound = _uiState.value.combatRound
        val enemyName = _uiState.value.activeEnemy?.name ?: "Hostile Entity"
        val record = TurnActionRecord(roundNumber = currentRound, actorName = enemyName, isPlayer = false, actionType = actionType, summary = summary, damageDealt = damageDealt, shieldAbsorbed = shieldAbsorbed, healAmount = healAmount, isCrit = isCrit, isMiss = isMiss, statusApplied = statusApplied)
        _uiState.update { it.copy(enemyTurnHistory = it.enemyTurnHistory + record, allTurnActions = it.allTurnActions + record, lastEnemyActionRecord = record, totalEnemyTurnsCount = it.totalEnemyTurnsCount + 1, turnPhase = TurnPhase.ENEMY_RESOLVING) }
        return record
    }

    fun runTerminalCommand(parts: List<String>, state: GameViewModel.GameUiState): Boolean {
        val cmd = parts[0].lowercase()
        if (state.activeCombatHack != null) {
            when (cmd) {
                "clear" -> { clearCombatHackBuffer(); return true }
                "abort", "exit", "cancel" -> { abortCombatHack(); return true }
                else -> {
                    val symbolInput = if (cmd == "hack" && parts.size > 1) parts[1].uppercase() else cmd.uppercase()
                    if (state.activeCombatHack.availablePool.contains(symbolInput)) { selectCombatHackSymbol(symbolInput); return true }
                    else { onLog("HACK PATTERN COMMAND: Enter a valid symbol e.g. '${state.activeCombatHack.availablePool.first()}', 'clear', or 'abort'", LogType.ALERT); return true }
                }
            }
        }
        return when (cmd) {
            "attack", "hit", "fight", "swing", "strike" -> { if (state.gameState != GameState.EXPLORATION) { combatAttack(); true } else { onLog("ERROR: Combat actions are only valid during active hostile combat.", LogType.ERROR); true } }
            "defend", "block", "shield" -> { if (state.gameState != GameState.EXPLORATION) { combatDefend(); true } else { onLog("ERROR: Combat actions are only valid during active hostile combat.", LogType.ERROR); true } }
            "flee", "run", "escape" -> { if (state.gameState != GameState.EXPLORATION) { fleeCombat(); true } else { onLog("ERROR: Combat actions are only valid during active hostile combat.", LogType.ERROR); true } }
            "scan", "radar", "sonar" -> { if (state.gameState != GameState.EXPLORATION) { combatScan(); true } else { false } }
            "hack" -> { if (state.screen == ActiveScreen.HACKING_MINIGAME) { val r = parts.getOrNull(1)?.toIntOrNull(); val c = parts.getOrNull(2)?.toIntOrNull(); if (r != null && c != null) { onLog("HACK: Use hacking minigame UI.", LogType.INFO); true } else { onLog("HACK: Please specify cell indices. E.g. 'hack 2 3'", LogType.ALERT); true } } else false }
            "stance", "style" -> { onLog("COMBAT STANCE: Single unified Strike stance active.", LogType.INFO); true }
            else -> false
        }
    }
}
