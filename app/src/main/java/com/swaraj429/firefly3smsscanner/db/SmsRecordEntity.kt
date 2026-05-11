package com.swaraj429.firefly3smsscanner.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted record of every SMS transaction the app has seen.
 *
 * The [smsHash] is a SHA-256 digest of (sender + body) so duplicate SMS
 * messages are never inserted twice. Records are kept for 30 days; the
 * DAO exposes a cleanup query that deletes anything older.
 *
 * [syncStatus] tracks whether the transaction was sent to the Firefly III
 * backend:
 *   - PENDING   — parsed but not yet sent
 *   - SENT      — successfully posted to Firefly
 *   - FAILED    — the POST returned an error
 */
@Entity(
    tableName = "sms_records",
    indices = [Index(value = ["smsHash"], unique = true)]
)
data class SmsRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** SHA-256 hash of (sender + body) — unique constraint prevents duplicates */
    val smsHash: String,

    /** Original SMS sender address (e.g. "VD-HDFCBK") */
    val sender: String,

    /** Full SMS body text */
    val body: String,

    /** Timestamp of the original SMS (millis since epoch) */
    val smsTimestamp: Long,

    /** Parsed transaction amount (0.0 if unparseable) */
    val amount: Double,

    /** "WITHDRAWAL", "DEPOSIT", or "TRANSFER" */
    val transactionType: String,

    /** User-editable description (may be empty) */
    val description: String = "",

    // --- Mapped Metadata ---
    val sourceAccountId: String? = null,
    val sourceAccountName: String? = null,
    val destinationAccountId: String? = null,
    val destinationAccountName: String? = null,
    val categoryName: String? = null,
    val budgetId: String? = null,
    val budgetName: String? = null,
    val selectedTagsCommaSeparated: String = "", // Room doesn't natively do lists without TypeConverters

    // --- Sync Status ---
    /** PENDING | SENT | FAILED */
    val syncStatus: String = "PENDING",

    /** Firefly III transaction ID returned after a successful POST */
    val fireflyTransactionId: String? = null,

    /** When this record was first created (millis) */
    val createdAt: Long = System.currentTimeMillis(),

    /** Last time syncStatus changed (millis) */
    val updatedAt: Long = System.currentTimeMillis()
)
