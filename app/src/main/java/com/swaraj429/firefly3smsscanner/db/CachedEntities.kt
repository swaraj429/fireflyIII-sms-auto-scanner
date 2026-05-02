package com.swaraj429.firefly3smsscanner.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entities for caching Firefly III metadata locally.
 * These mirror the simplified UI models but are stored in SQLite so data
 * is available instantly on launch — no network wait required.
 */

@Entity(tableName = "accounts")
data class CachedAccount(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,             // "asset", "expense", "revenue"
    val accountNumber: String?,
    val accountRole: String?,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "categories")
data class CachedCategory(
    @PrimaryKey val id: String,
    val name: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "tags")
data class CachedTag(
    @PrimaryKey val id: String,
    val name: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "budgets")
data class CachedBudget(
    @PrimaryKey val id: String,
    val name: String,
    val updatedAt: Long = System.currentTimeMillis()
)
