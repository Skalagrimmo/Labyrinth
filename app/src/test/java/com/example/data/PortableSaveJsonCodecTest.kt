package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PortableSaveJsonCodecTest {
    @Test
    fun `json codec preserves legacy field names and round trips payload`() {
        val payload = PortableSavePayload(
            runnerName = "Runner", runnerClass = "TECHIE", level = 7, integrity = 61,
            direction = "NORTH", currentZone = "CITY", inventory = listOf("medkit", "key"),
            installedProgramIds = listOf("ping", "sandbox"), mazeData = "SAFE_ZONE,PATH",
            exploredCellsCsv = "1,1;2,1", unlockedSkillsCsv = "scan,repair", levelSeed = 424242L
        )
        val json = PortableSaveJsonCodec.encode(payload)
        val decoded = PortableSaveJsonCodec.decode(json)

        assertTrue(json.contains("\"runnerName\""))
        assertTrue(json.contains("\"installedPrograms\""))
        assertTrue(json.contains("\"mazeData\""))
        assertEquals(payload, decoded)
    }

    @Test
    fun `decoder preserves legacy import defaults`() {
        val decoded = PortableSaveJsonCodec.decode("""{"version":1,"mazeData":"SAFE_ZONE"}""")

        assertEquals("", decoded.runnerName)
        assertEquals("CODE_SLASHER", decoded.runnerClass)
        assertEquals(100, decoded.integrity)
        assertEquals(10, decoded.playerShield)
        assertEquals(100, decoded.credits)
        assertEquals("EAST", decoded.direction)
        assertEquals("BUILDING", decoded.currentZone)
        assertEquals("CLEAR", decoded.activeWeather)
        assertEquals("EXPLORATION", decoded.gameStateName)
    }
}
