package com.swaraj429.firefly3smsscanner.util

import java.security.MessageDigest

/**
 * Produces a deterministic SHA-256 hash for an SMS so we can deduplicate
 * records even if the same message is scanned multiple times.
 *
 * The hash is based on **sender + body** — two messages with different
 * timestamps but identical content are considered the same transaction.
 */
object SmsHasher {

    /**
     * Returns a lowercase hex-encoded SHA-256 digest of "sender|body".
     */
    fun hash(sender: String, body: String): String {
        val input = "${sender.trim()}|${body.trim()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
