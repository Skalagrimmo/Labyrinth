package com.example.data

import android.content.Context
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LegacySaveStorageTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `storage preserves typed values and defaults`() {
        val storage = AndroidLegacySaveStorage(context, "legacy_save_storage_test")
        assertFalse(storage.getBoolean("missing_bool"))
        assertEquals(42, storage.getInt("missing_int", 42))
        assertEquals("fallback", storage.getString("missing_string", "fallback"))
        storage.edit {
            putBoolean("has_saved_game", true)
            putInt("level", 7)
            putString("runnerName", "V-Netrunner")
        }
        assertTrue(storage.getBoolean("has_saved_game"))
        assertEquals(7, storage.getInt("level"))
        assertEquals("V-Netrunner", storage.getString("runnerName"))
    }
}