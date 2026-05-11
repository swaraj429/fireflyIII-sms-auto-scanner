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
     * Lookup a single record by its hash.
     */
    @Query("SELECT * FROM sms_records WHERE smsHash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): SmsRecordEntity?

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
