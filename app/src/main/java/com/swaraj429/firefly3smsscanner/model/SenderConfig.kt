package com.swaraj429.firefly3smsscanner.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "sender_configs")
data class SenderConfig(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val senderPattern: String,
    val accountId: String,
    val transactionType: String, // "withdrawal", "deposit", "transfer"
    val category: String,
    val tags: List<String>,
    val descriptionTemplate: String,
    val currency: String,
    val isActive: Boolean = true
)
