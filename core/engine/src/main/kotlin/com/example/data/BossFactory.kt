package com.example.data

/** Creates boss combat models without owning game-session state. */
object BossFactory {
    fun create(bossType: BossType, level: Int, idTimeMillis: Long = System.currentTimeMillis()): Enemy =
        when (bossType) {
            BossType.FIREWALL_SENTINEL -> Enemy(
                id = "boss_sentinel_$idTimeMillis",
                name = "Firewall Sentinel",
                maxIntegrity = 250 + level * 20,
                integrity = 250 + level * 20,
                maxShield = 100 + level * 15,
                shield = 100 + level * 15,
                damage = 22 + level * 3,
                armor = 8 + level,
                iconAscii = "   _______\n  | SENT |\n  |inel._|\n  |_______|\n  /||\\ ||\\\n / ||\\ || \\",
                bountyCredits = 400 + level * 60,
                description = "Ancient defensive sub-routine guarding the deepest corporate firewalls. Regenerates shields and locks down systems.",
                isBoss = true,
                bossType = bossType
            )
            BossType.DAEMON_OVERLORD -> Enemy(
                id = "boss_overlord_$idTimeMillis",
                name = "Daemon Overlord",
                maxIntegrity = 350 + level * 25,
                integrity = 350 + level * 25,
                maxShield = 80 + level * 10,
                shield = 80 + level * 10,
                damage = 30 + level * 4,
                armor = 6 + level,
                iconAscii = "   .d8888.\n  d88' '88b\n  88     88\n  Y8b   d8P\n   Y8888P'\n    '||'\n    [OVERLORD]",
                bountyCredits = 600 + level * 80,
                description = "Supreme daemon ruling the collector sub-grid. Summons lesser daemons and drains neural resources.",
                isBoss = true,
                bossType = bossType
            )
            BossType.BLACK_ICE_COLOSSUS -> Enemy(
                id = "boss_colossus_$idTimeMillis",
                name = "Black ICE Colossus",
                maxIntegrity = 500 + level * 30,
                integrity = 500 + level * 30,
                maxShield = 150 + level * 20,
                shield = 150 + level * 20,
                damage = 42 + level * 5,
                armor = 12 + level * 2,
                iconAscii = "  _________\n |  BLACK  |\n |   ICE   |\n | COLOSSUS|\n |_________|\n  |||   |||\n  |||   |||\n  ===   ===",
                bountyCredits = 1000 + level * 100,
                description = "Apex security construct of the Metro Core. Adapts defenses and unleashes devastating neural storms.",
                isBoss = true,
                bossType = bossType
            )
        }
}
