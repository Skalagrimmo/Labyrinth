package com.example.data

import java.util.Base64

/**
 * Stable envelope for portable save payloads.
 *
 * JSON field semantics stay owned by PersistenceManager for now; this class isolates
 * the versioned prefix and Base64 transport so they can move to core:persistence.
 */
object PortableSaveEnvelope {
    const val VERSION = 1

    fun encodeJson(json: String): String =
        SaveDataCodec.PORTABLE_SAVE_PREFIX +
            Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))

    fun decodeJson(encoded: String): String {
        require(encoded.startsWith(SaveDataCodec.PORTABLE_SAVE_PREFIX)) {
            "Unsupported portable save prefix"
        }
        val payload = encoded.removePrefix(SaveDataCodec.PORTABLE_SAVE_PREFIX)
        return String(Base64.getDecoder().decode(payload), Charsets.UTF_8)
    }

    fun hasSupportedPrefix(encoded: String): Boolean =
        encoded.startsWith(SaveDataCodec.PORTABLE_SAVE_PREFIX)
}
