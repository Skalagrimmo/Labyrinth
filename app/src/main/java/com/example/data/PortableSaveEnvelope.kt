package com.example.data

/**
 * Stable envelope for portable save payloads.
 *
 * The Android Base64 implementation remains in PersistenceManager where the
 * platform transport is currently owned. This class only owns the versioned
 * prefix and keeps the envelope API platform-neutral.
 */
object PortableSaveEnvelope {
    const val VERSION = 1

    fun wrapBase64(base64: String): String =
        SaveDataCodec.PORTABLE_SAVE_PREFIX + base64

    fun unwrapBase64(encoded: String): String {
        require(hasSupportedPrefix(encoded)) {
            "Unsupported portable save prefix"
        }
        return encoded.removePrefix(SaveDataCodec.PORTABLE_SAVE_PREFIX)
    }

    fun hasSupportedPrefix(encoded: String): Boolean =
        encoded.startsWith(SaveDataCodec.PORTABLE_SAVE_PREFIX)
}
