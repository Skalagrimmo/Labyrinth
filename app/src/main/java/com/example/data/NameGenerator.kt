package com.example.data

import kotlin.random.Random

/**
 * Generates cyberpunk-flavoured runner aliases for the character creation screen.
 *
 * Pure Kotlin (no Android dependencies) so it can be unit-tested and reused anywhere.
 * Produces compound netrunner-style monikers such as "GhostRunner", "CipherWraith",
 * or "KaosJack".
 */
object NameGenerator {

    private val prefixes = listOf(
        "Null", "Cypher", "Ghost", "Kaos", "Byte", "Zero", "Shadow", "Hex", "Nyx",
        "Phantom", "Crimson", "Void", "Kilo", "Neuro", "Dark", "Synth", "Ice", "Pulse"
    )

    private val suffixes = listOf(
        "Runner", "Wraith", "Jack", "Hack", "Blade", "Rez", "Byte", "Ghost", "Ward",
        "Viper", "Cipher", "Edge", "Nova", "Wolf", "Shade", "Loop", "Dex", "Rex"
    )

    private val titles = listOf(
        "the_Phantom", "the_Zero", "Ghost_in_Wire", "the_Cold", "Burner", "the_Unseen",
        "Deep_Dweller", "the_Static", "Killer", "the_Outcast", "NETRUNNER_PRIME", "the_Grin"
    )

    /** Generates a random alias like "HexViper" (optionally suffixed with a title). */
    fun randomName(random: Random = Random.Default): String {
        val base = prefixes[random.nextInt(prefixes.size)] + suffixes[random.nextInt(suffixes.size)]
        return if (random.nextInt(100) < 25) "$base ${titles[random.nextInt(titles.size)]}" else base
    }

    /** Returns [count] distinct random names (for a suggestion list). */
    fun suggestions(count: Int = 5, random: Random = Random.Default): List<String> {
        val seen = mutableSetOf<String>()
        while (seen.size < count) {
            val name = randomName(random)
            if (seen.add(name)) { /* keep */ }
        }
        return seen.toList()
    }
}
