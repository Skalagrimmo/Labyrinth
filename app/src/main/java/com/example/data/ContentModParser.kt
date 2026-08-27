package com.example.data

/**
 * ContentModParser — a single reusable, dependency-free Markdown parser that turns
 * human-readable `.md` mod files into typed game content (enemies, items, programs).
 *
 * It exists so that adding new content is a matter of editing a text file instead of
 * writing Kotlin. The parser is intentionally generic: every "document type" is
 * expressed as a Markdown `## Heading` whose **key: value** lines become a field map,
 * and whose body (description + optional fenced ASCII-art portrait) becomes strings.
 *
 * ## Markdown format
 *
 *     ## Enemy: Grinder.exe
 *     tier: 4
 *     hp: 1.5
 *     shield: 1.2
 *     damage: 1.6
 *     armor: 3
 *     bounty: 1.4
 *     status: POISONED:3, WEAKENED:2
 *     desc: A grinding daemon that chews through firewall plating.
 *     portrait:
 *     ```
 *       /\/\/\/\
 *      ( (0)(0) )
 *       \_|__|_/
 *     ```
 *
 * Supported headings: `## Enemy`, `## Item`, `## Program` (name may follow a colon).
 *
 * - `#`/`##` lines and `-` list markers are stripped.
 * - `key: value` lines populate [ModEntity.fields].
 * - A fenced block (```) is captured as the entity's `portrait` (used for enemies).
 * - Plain lines after the fields become the entity's `body` (fallback description).
 *
 * The parser itself knows nothing about game data. Use [ContentRegistry] to convert a
 * parsed [ModEntity] into concrete [Enemy], [GameItem] and [Program] instances.
 */
object ContentModParser {

    /** A single parsed block from the markdown document. */
    data class ModEntity(
        val kind: String,          // "Enemy", "Item", "Program"
        val name: String,          // from the heading, or "Untitled"
        val fields: Map<String, String>,   // lowercase-keyed key:value map
        val body: String,          // plain markdown text (fallback description)
        val portrait: String       // fenced ``` block (ASCII art for enemies)
    )

    /**
     * Parses an entire mod document into a list of [ModEntity].
     * Ignores the document preamble (title, intro) outside of content headings.
     */
    fun parse(markdown: String): List<ModEntity> {
        val entities = mutableListOf<ModEntity>()
        val lines = markdown.split("\n")

        var currentKind: String? = null
        var currentName: String? = null
        var inFence = false
        val fenceBuf = StringBuilder()
        val bodyBuf = StringBuilder()

        // Collect field lines for the current entity as a list of (key,value).
        var fieldLines = mutableListOf<Pair<String, String>>()

        fun flush() {
            val kind = currentKind ?: return
            val fields = fieldLines.toMap()
            val portrait = fenceBuf.toString().trim()
            val body = bodyBuf.toString().trim()
            entities.add(
                ModEntity(
                    kind = kind,
                    name = currentName ?: "Untitled",
                    fields = fields,
                    body = body,
                    portrait = portrait
                )
            )
            fieldLines = mutableListOf()
            fenceBuf.clear()
            bodyBuf.clear()
            currentKind = null
            currentName = null
        }

        for (raw in lines) {
            val line = raw.trimEnd()

            if (inFence) {
                if (line.trimStart().startsWith("```")) {
                    inFence = false
                } else {
                    fenceBuf.append(line).append('\n')
                }
                continue
            }

            if (line.trimStart().startsWith("```")) {
                inFence = true
                continue
            }

            val heading = parseHeading(line)
            if (heading != null) {
                flush()
                currentKind = heading.first
                currentName = heading.second
                continue
            }

            if (currentKind != null) {
                val kv = parseField(line)
                if (kv != null) {
                    fieldLines.add(kv)
                } else {
                    val text = stripMarkdown(line).trim()
                    if (text.isNotEmpty()) bodyBuf.append(text).append(' ')
                }
            }
        }
        flush()
        return entities
    }

    /** Recognizes `## Enemy`, `## Enemy: Name`, etc. Returns (Kind, Name) or null. */
    private fun parseHeading(line: String): Pair<String, String>? {
        val t = line.trimStart()
        if (!t.startsWith("## ")) return null
        var rest = t.substring(3).trim()
        var name: String? = null
        val colon = rest.indexOf(':')
        if (colon != -1) {
            name = rest.substring(colon + 1).trim()
            rest = rest.substring(0, colon).trim()
        }
        val kind = rest.split(' ').firstOrNull()?.trim() ?: return null
        if (kind.isEmpty()) return null
        return Pair(kind, name ?: "")
    }

    /** Recognizes `key: value`. Keys are lowercased/normalised. */
    private fun parseField(line: String): Pair<String, String>? {
        val t = line.trimStart()
        if (t.startsWith("#") || t.startsWith("-")) return null
        val colon = t.indexOf(':')
        if (colon <= 0) return null
        val key = t.substring(0, colon).trim().lowercase()
        if (key.contains(' ')) return null // skip prose sentences with colons
        val value = t.substring(colon + 1).trim()
        return Pair(key, value)
    }

    /** Strips Markdown emphasis/inline-code so body text stays clean for descriptions. */
    private fun stripMarkdown(line: String): String {
        var s = line
        s = s.replace("**", "")
        s = s.replace("__", "")
        s = s.replace("`", "")
        s = s.replace("*", "")
        return s
    }
}
