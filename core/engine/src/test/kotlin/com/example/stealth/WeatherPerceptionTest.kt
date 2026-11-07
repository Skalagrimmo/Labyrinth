package com.example.stealth

import com.example.core.model.CyberWeather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherPerceptionTest {

    @Test
    fun clearAndFragmentationKeepBaselinePerception() {
        assertEquals(WeatherPerception.NONE, WeatherPerception.forWeather(CyberWeather.CLEAR))
        assertEquals(WeatherPerception.NONE, WeatherPerception.forWeather(CyberWeather.FRAGMENTATION))
    }

    @Test
    fun dataStormDegradesIceOptics() {
        val perception = WeatherPerception.forWeather(CyberWeather.DATA_STORM)
        assertEquals(0.75f, perception.viewRangeFactor, 0.0001f)
        assertEquals(0.9f, perception.detectionFactor, 0.0001f)
        assertEquals(1f, perception.hearingFactor, 0.0001f)
        assertEquals(1f, perception.speedFactor, 0.0001f)
    }

    @Test
    fun apexStormOverdrivesEveryPerceptionChannel() {
        val perception = WeatherPerception.forWeather(CyberWeather.APEX_STORM)
        assertEquals(1.2f, perception.viewRangeFactor, 0.0001f)
        assertEquals(1.25f, perception.detectionFactor, 0.0001f)
        assertEquals(1.15f, perception.hearingFactor, 0.0001f)
        assertEquals(1.1f, perception.speedFactor, 0.0001f)
    }

    @Test
    fun ghostProtocolMasksTheRunner() {
        val perception = WeatherPerception.forWeather(CyberWeather.GHOST_PROTOCOL)
        assertEquals(0.7f, perception.viewRangeFactor, 0.0001f)
        assertEquals(0.65f, perception.detectionFactor, 0.0001f)
        assertEquals(0.85f, perception.hearingFactor, 0.0001f)
        assertEquals(1f, perception.speedFactor, 0.0001f)
    }

    @Test
    fun coldSpotFreezesIceSpeedAndDampensSensors() {
        val perception = WeatherPerception.forWeather(CyberWeather.COLD_SPOT)
        assertEquals(1f, perception.viewRangeFactor, 0.0001f)
        assertEquals(0.85f, perception.detectionFactor, 0.0001f)
        assertEquals(0.9f, perception.hearingFactor, 0.0001f)
        assertEquals(0.75f, perception.speedFactor, 0.0001f)
    }

    @Test
    fun hotNodeOverclocksIce() {
        val perception = WeatherPerception.forWeather(CyberWeather.HOT_NODE)
        assertEquals(1f, perception.viewRangeFactor, 0.0001f)
        assertEquals(1.1f, perception.detectionFactor, 0.0001f)
        assertEquals(1f, perception.hearingFactor, 0.0001f)
        assertEquals(1.15f, perception.speedFactor, 0.0001f)
    }

    @Test
    fun echoesHeightenHearingOnly() {
        val perception = WeatherPerception.forWeather(CyberWeather.ECHOES)
        assertEquals(1f, perception.viewRangeFactor, 0.0001f)
        assertEquals(1f, perception.detectionFactor, 0.0001f)
        assertEquals(1.25f, perception.hearingFactor, 0.0001f)
        assertEquals(1f, perception.speedFactor, 0.0001f)
    }

    @Test
    fun everyWeatherHasASaneMapping() {
        CyberWeather.VALUES.forEach { weather ->
            val perception = WeatherPerception.forWeather(weather)
            assertTrue("viewRange factor must stay positive: " + weather, perception.viewRangeFactor > 0f)
            assertTrue("detection factor must stay positive: " + weather, perception.detectionFactor > 0f)
            assertTrue("hearing factor must stay positive: " + weather, perception.hearingFactor > 0f)
            assertTrue("speed factor must stay positive: " + weather, perception.speedFactor > 0f)
            assertTrue("viewRange factor must stay under double baseline: " + weather, perception.viewRangeFactor <= 2f)
        }
        assertNotEquals(
            WeatherPerception.forWeather(CyberWeather.DATA_STORM),
            WeatherPerception.forWeather(CyberWeather.APEX_STORM)
        )
    }
}
