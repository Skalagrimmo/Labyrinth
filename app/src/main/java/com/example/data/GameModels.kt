package com.example.data

import com.example.core.model.ActiveStatusEffect as CoreActiveStatusEffect
import com.example.core.model.BossType as CoreBossType
import com.example.core.model.CellType as CoreCellType
import com.example.core.model.CyberWeather as CoreCyberWeather
import com.example.core.model.Cyberware as CoreCyberware
import com.example.core.model.DigitalMutation as CoreDigitalMutation
import com.example.core.model.Direction as CoreDirection
import com.example.core.model.Enemy as CoreEnemy
import com.example.core.model.LogMessage as CoreLogMessage
import com.example.core.model.LogType as CoreLogType
import com.example.core.model.NetrunnerClass as CoreNetrunnerClass
import com.example.core.model.Program as CoreProgram
import com.example.core.model.StatusEffectType as CoreStatusEffectType
import com.example.core.model.Zone as CoreZone

/**
 * Compatibility aliases while the application is migrated from the legacy data package.
 * New domain code should depend on com.example.core.model directly.
 */
typealias NetrunnerClass = CoreNetrunnerClass
typealias Cyberware = CoreCyberware
typealias StatusEffectType = CoreStatusEffectType
typealias ActiveStatusEffect = CoreActiveStatusEffect
typealias Program = CoreProgram
typealias Direction = CoreDirection
typealias CellType = CoreCellType
typealias Zone = CoreZone
typealias CyberWeather = CoreCyberWeather
typealias DigitalMutation = CoreDigitalMutation
typealias Enemy = CoreEnemy
typealias BossType = CoreBossType
typealias LogMessage = CoreLogMessage
typealias LogType = CoreLogType
