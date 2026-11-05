package com.example.data

/**
 * Loads mod content from the Android `assets/` folder. Place `.md` files in
 * `app/src/main/assets/mods/` and call [loadFolderMods] once at startup.
 */
object ContentModLoader {
    /**
     * Reads every `*.md` file under `assets/mods/` and registers its content.
     * @return the names of successfully loaded mod files.
     */
    fun loadFolderMods(context: android.content.Context): List<String> {
        val loaded = mutableListOf<String>()
        try {
            val fileNames = context.assets.list("mods") ?: emptyArray()
            for (file in fileNames) {
                if (!file.endsWith(".md")) continue
                val text = context.assets.open("mods/$file").bufferedReader().use { it.readText() }
                ContentRegistry.loadMod(text)
                loaded.add(file)
            }
        } catch (_: Exception) {
            // assets/mods/ absent or unreadable — no mods loaded. Safe to ignore.
        }
        return loaded
    }
}
