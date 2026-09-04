package com.swaraj429.firefly3smsscanner.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SmsRecordDao {

    // ── Insert / upsert ─────────────────────────────────────────────────────

    /**
     * Insert a new record. IGNORE strategy means a duplicate smsHash
     * silently does nothing — exactly the dedup behaviour we need.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecord(record: SmsRecordEntity): Long

    /**
     * Batch insert. Duplicates (by smsHash) are silently ignored.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecords(records: List<SmsRecordEntity>)

    // ── Queries ──────────────────────────────────────────────────────────────

    /**
     * All records within the last [daysAgo] days, newest first.
     */
    @Query("""
        SELECT * FROM sms_records 
        WHERE smsTimestamp >= :cutoffMillis 
        ORDER BY smsTimestamp DESC
    """)
    suspend fun getRecordsSince(cutoffMillis: Long): List<SmsRecordEntity>

    /**
     * Quick check: does a record with this hash already exist?
     */
    @Query("SELECT COUNT(*) FROM sms_records WHERE smsHash = :hash")
    suspend fun existsByHash(hash: String): Int

    /**
     * Count records by sync status (for summary badges).
     */
    @Query("SELECT COUNT(*) FROM sms_records WHERE syncStatus = :status AND smsTimestamp >= :cutoffMillis")
    suspend fun countByStatus(status: String, cutoffMillis: Long): Int

    // ── Updates ──────────────────────────────────────────────────────────────

    /**
     * Mark a record as SENT and store the Firefly transaction ID.
     */
    @Query("""
        UPDATE sms_records 
        SET syncStatus = 'SENT', 
            fireflyTransactionId = :fireflyId,
            updatedAt = :now
        WHERE smsHash = :hash
    """)
    suspend fun markSent(hash: String, fireflyId: String, now: Long = System.currentTimeMillis())

    /**
     * Mark a record as SENT with all user-edited metadata.
     */
    @Query("""
        UPDATE sms_records 
        SET syncStatus = 'SENT', 
            fireflyTransactionId = :fireflyId,
            amount = :amount,
            transactionType = :transactionType,
            description = :description,
            categoryName = :categoryName,
            selectedTagsCommaSeparated = :tags,
            sourceAccountId = :sourceAccountId,
            sourceAccountName = :sourceAccountName,
            destinationAccountId = :destinationAccountId,
            destinationAccountName = :destinationAccountName,
            budgetId = :budgetId,
            budgetName = :budgetName,
            lastSyncedAt = :now,
            updatedAt = :now
        WHERE smsHash = :hash
    """)
    suspend fun markSentWithMetadata(
        hash: String,
        fireflyId: String,
        amount: Double,
        transactionType: String,
        description: String,
        categoryName: String?,
        tags: String,
        sourceAccountId: String?,
        sourceAccountName: String?,
        destinationAccountId: String?,
        destinationAccountName: String?,
        budgetId: String?,
        budgetName: String?,
        now: Long = System.currentTimeMillis()
    )

    /**
     * Mark a record as FAILED.
     */
    @Query("""
        UPDATE sms_records 
        SET syncStatus = 'FAILED', 
            updatedAt = :now
        WHERE smsHash = :hash
    """)
    suspend fun markFailed(hash: String, now: Long = System.currentTimeMillis())

    /**
     * Reset a FAILED record back to PENDING so the user can retry.
     */
    @Query("""
        UPDATE sms_records 
        SET syncStatus = 'PENDING', 
            updatedAt = :now
        WHERE smsHash = :hash
    """)
    suspend fun markPending(hash: String, now: Long = System.currentTimeMillis())

    /**
     * Mark a record as DISMISSED with a specific reason.
     */
    @Query("""
        UPDATE sms_records 
        SET syncStatus = 'DISMISSED', 
            dismissReason = :reason,
            dismissedAt = :now,
            updatedAt = :now
        WHERE smsHash = :hash
    """)
    suspend fun markDismissed(hash: String, reason: String, now: Long = System.currentTimeMillis())

    /**
     * Restore a DISMISSED record back to PENDING (e.g. Undo action).
     */
    @Query("""
        UPDATE sms_records 
        SET syncStatus = 'PENDING', 
            dismissReason = NULL,
            dismissedAt = NULL,
            updatedAt = :now
        WHERE smsHash = :hash
    """)
    suspend fun restoreDismissed(hash: String, now: Long = System.currentTimeMillis())

    // ── Reconciliation Queries ──────────────────────────────────

    /**
     * Get all SENT records that have a fireflyTransactionId.
     * Used by the reconciliation engine to find records that need syncing.
     */
    @Query("""
        SELECT * FROM sms_records 
        WHERE syncStatus = 'SENT' 
        AND fireflyTransactionId IS NOT NULL
        AND smsTimestamp >= :cutoffMillis
        ORDER BY smsTimestamp DESC
    """)
    suspend fun getSentRecordsWithFireflyId(cutoffMillis: Long): List<SmsRecordEntity>

    /**
     * Get all PENDING records (for reinstall reconciliation — need to check if they exist in Firefly).
     */
    @Query("""
        SELECT * FROM sms_records 
        WHERE syncStatus = 'PENDING'
        AND smsTimestamp >= :cutoffMillis
        ORDER BY smsTimestamp DESC
    """)
    suspend fun getPendingRecords(cutoffMillis: Long): List<SmsRecordEntity>

    /**
     * Find a record by its SMS hash.
     */
    @Query("SELECT * FROM sms_records WHERE smsHash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): SmsRecordEntity?

    /**
     * Update all transaction details from Firefly (reconciliation).
     * Firefly is the source of truth — updates description, tags, category,
     * destination account, source account, budget, transaction type, and IDs.
     */
    @Query("""
        UPDATE sms_records 
        SET syncStatus = 'SENT',
            fireflyTransactionId = :fireflyGroupId,
            fireflyTransactionJournalId = :fireflyJournalId,
            remoteDescription = :remoteDescription,
            remoteTags = :remoteTags,
            remoteCategory = :remoteCategory,
            description = COALESCE(:remoteDescription, description),
            selectedTagsCommaSeparated = COALESCE(:remoteTags, selectedTagsCommaSeparated),
            categoryName = COALESCE(:remoteCategory, categoryName),
            sourceAccountId = COALESCE(:sourceAccountId, sourceAccountId),
            sourceAccountName = COALESCE(:sourceAccountName, sourceAccountName),
            destinationAccountId = COALESCE(:destinationAccountId, destinationAccountId),
            destinationAccountName = COALESCE(:destinationAccountName, destinationAccountName),
            budgetId = COALESCE(:budgetId, budgetId),
            budgetName = COALESCE(:budgetName, budgetName),
            transactionType = COALESCE(:transactionType, transactionType),
            lastSyncedAt = :now,
            updatedAt = :now
        WHERE smsHash = :hash
    """)
    suspend fun updateFromFirefly(
        hash: String,
        fireflyGroupId: String,
        fireflyJournalId: String,
        remoteDescription: String?,
        remoteTags: String?,
        remoteCategory: String?,
        sourceAccountId: String?,
        sourceAccountName: String?,
        destinationAccountId: String?,
        destinationAccountName: String?,
        budgetId: String?,
        budgetName: String?,
        transactionType: String?,
        now: Long = System.currentTimeMillis()
    )

    /**
     * Reconcile a PENDING record to SENT (discovered during reinstall reconciliation).
     * Overwrites local fields with Firefly's canonical values.
     */
    suspend fun reconcilePendingToSent(
        hash: String,
        fireflyGroupId: String,
        fireflyJournalId: String,
        remoteDescription: String?,
        remoteTags: String?,
        remoteCategory: String?,
        sourceAccountId: String?,
        sourceAccountName: String?,
        destinationAccountId: String?,
        destinationAccountName: String?,
        budgetId: String?,
        budgetName: String?,
        transactionType: String?,
        now: Long = System.currentTimeMillis()
    ) = updateFromFirefly(
        hash, fireflyGroupId, fireflyJournalId,
        remoteDescription, remoteTags, remoteCategory,
        sourceAccountId, sourceAccountName,
        destinationAccountId, destinationAccountName,
        budgetId, budgetName, transactionType, now
    )

    /**
     * Get the latest lastSyncedAt timestamp across all records.
     * Used to determine when the last reconciliation ran.
     */
    @Query("SELECT MAX(lastSyncedAt) FROM sms_records")
    suspend fun getLastSyncTimestamp(): Long?

    // ── Cleanup ──────────────────────────────────────────────────────────────

    /**
     * Delete records whose SMS timestamp is older than [cutoffMillis].
     * Called periodically to enforce 30-day retention.
     */
    @Query("DELETE FROM sms_records WHERE smsTimestamp < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long): Int

    /**
     * Total record count (for debug/stats).
     */
    @Query("SELECT COUNT(*) FROM sms_records")
    suspend fun totalCount(): Int

    /**
     * Get ALL records (for debug database viewer), newest first.
     */
    @Query("SELECT * FROM sms_records ORDER BY smsTimestamp DESC")
    suspend fun getAllRecords(): List<SmsRecordEntity>

    /**
     * Delete ALL records (for "Clear Database" action).
     */
    @Query("DELETE FROM sms_records")
    suspend fun deleteAll()
}
