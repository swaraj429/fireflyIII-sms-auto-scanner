package com.swaraj429.firefly3smsscanner.model

import kotlinx.coroutines.flow.Flow

class SenderConfigRepository(private val dao: SenderConfigDao) {
    val allConfigs: Flow<List<SenderConfig>> = dao.getAllConfigs()

    suspend fun getActiveConfigs(): List<SenderConfig> {
        return dao.getActiveConfigs()
    }

    suspend fun getConfigById(id: String): SenderConfig? {
        return dao.getConfigById(id)
    }

    suspend fun insertConfig(config: SenderConfig) {
        dao.insertConfig(config)
    }

    suspend fun updateConfig(config: SenderConfig) {
        dao.updateConfig(config)
    }

    suspend fun deleteConfig(config: SenderConfig) {
        dao.deleteConfig(config)
    }
}
