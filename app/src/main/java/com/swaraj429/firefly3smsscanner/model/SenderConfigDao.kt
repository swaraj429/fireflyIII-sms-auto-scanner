package com.swaraj429.firefly3smsscanner.model

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SenderConfigDao {
    @Query("SELECT * FROM sender_configs")
    fun getAllConfigs(): Flow<List<SenderConfig>>

    @Query("SELECT * FROM sender_configs WHERE isActive = 1")
    suspend fun getActiveConfigs(): List<SenderConfig>

    @Query("SELECT * FROM sender_configs WHERE id = :id")
    suspend fun getConfigById(id: String): SenderConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: SenderConfig)

    @Update
    suspend fun updateConfig(config: SenderConfig)

    @Delete
    suspend fun deleteConfig(config: SenderConfig)
}
