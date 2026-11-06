package com.example.data

import android.content.Context
import android.content.SharedPreferences

interface LegacySaveStorage {
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean
    fun getInt(key: String, defaultValue: Int = 0): Int
    fun getString(key: String, defaultValue: String = ""): String?
    fun edit(block: LegacySaveEditor.() -> Unit)
}

class LegacySaveEditor internal constructor(private val editor: SharedPreferences.Editor) {
    fun putBoolean(key: String, value: Boolean) { editor.putBoolean(key, value) }
    fun putInt(key: String, value: Int) { editor.putInt(key, value) }
    fun putString(key: String, value: String) { editor.putString(key, value) }
    internal fun apply() { editor.apply() }
}

class AndroidLegacySaveStorage(
    context: Context,
    private val preferencesName: String = "netcrawler_save_prefs"
) : LegacySaveStorage {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = preferences.getBoolean(key, defaultValue)
    override fun getInt(key: String, defaultValue: Int): Int = preferences.getInt(key, defaultValue)
    override fun getString(key: String, defaultValue: String): String? = preferences.getString(key, defaultValue)

    override fun edit(block: LegacySaveEditor.() -> Unit) {
        val editor = LegacySaveEditor(preferences.edit())
        editor.block()
        editor.apply()
    }
}