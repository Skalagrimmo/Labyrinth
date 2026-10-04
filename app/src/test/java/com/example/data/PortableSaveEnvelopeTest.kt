package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortableSaveEnvelopeTest {
    @Test
    fun `portable envelope round trips utf8 json`() {
        val json = """{"version":1,"runnerName":"Венед","mazeData":"SAFE_ZONE,PATH"}"""
        val encoded = PortableSaveEnvelope.wrapBase64(
            java.util.Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))
        )

        assertTrue(encoded.startsWith("NETCRAWLER_SAVE_v1:"))
        assertEquals(
            json,
            String(
                java.util.Base64.getDecoder().decode(PortableSaveEnvelope.unwrapBase64(encoded)),
                Charsets.UTF_8
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decoder rejects unsupported prefix`() {
        PortableSaveEnvelope.unwrapBase64("LABYRINTH_SAVE_v2:AAAA")
    }

    @Test
    fun `prefix probe preserves legacy contract`() {
        assertTrue(PortableSaveEnvelope.hasSupportedPrefix("NETCRAWLER_SAVE_v1:e30="))
        assertFalse(PortableSaveEnvelope.hasSupportedPrefix("NETCRAWLER_SAVE_v2:e30="))
        assertEquals(1, PortableSaveEnvelope.VERSION)
    }
}
