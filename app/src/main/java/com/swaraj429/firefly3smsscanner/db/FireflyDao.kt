package com.swaraj429.firefly3smsscanner.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface FireflyDao {
    // --- Accounts ---
    @Query("SELECT * FROM accounts WHERE type = :type ORDER BY name ASC")
    suspend fun getAccountsByType(type: String): List<CachedAccount>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccounts(accounts: List<CachedAccount>)

    @Query("DELETE FROM accounts WHERE type = :type")
    suspend fun deleteAccountsByType(type: String)

    @Transaction
    suspend fun replaceAccounts(type: String, accounts: List<CachedAccount>) {
        deleteAccountsByType(type)
        insertAccounts(accounts)
    }

    // --- Categories ---
    @Query("SELECT * FROM categories ORDER BY name ASC")
    suspend fun getCategories(): List<CachedCategory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CachedCategory>)

    @Query("DELETE FROM categories")
    suspend fun deleteCategories()

    @Transaction
    suspend fun replaceCategories(categories: List<CachedCategory>) {
        deleteCategories()
        insertCategories(categories)
    }

    // --- Tags ---
    @Query("SELECT * FROM tags ORDER BY name ASC")
    suspend fun getTags(): List<CachedTag>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTags(tags: List<CachedTag>)

    @Query("DELETE FROM tags")
    suspend fun deleteTags()

    @Transaction
    suspend fun replaceTags(tags: List<CachedTag>) {
        deleteTags()
        insertTags(tags)
    }

    // --- Budgets ---
    @Query("SELECT * FROM budgets ORDER BY name ASC")
    suspend fun getBudgets(): List<CachedBudget>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudgets(budgets: List<CachedBudget>)

    @Query("DELETE FROM budgets")
    suspend fun deleteBudgets()

    @Transaction
    suspend fun replaceBudgets(budgets: List<CachedBudget>) {
        deleteBudgets()
        insertBudgets(budgets)
    }
}
