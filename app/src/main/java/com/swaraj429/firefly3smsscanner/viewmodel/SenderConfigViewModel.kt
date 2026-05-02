package com.swaraj429.firefly3smsscanner.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.swaraj429.firefly3smsscanner.model.AppDatabase
import com.swaraj429.firefly3smsscanner.model.SenderConfig
import com.swaraj429.firefly3smsscanner.model.SenderConfigRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SenderConfigViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: SenderConfigRepository

    private val _configs = MutableStateFlow<List<SenderConfig>>(emptyList())
    val configs: StateFlow<List<SenderConfig>> = _configs

    init {
        val dao = AppDatabase.getDatabase(application).senderConfigDao()
        repository = SenderConfigRepository(dao)

        viewModelScope.launch {
            repository.allConfigs.collect { list ->
                _configs.value = list
            }
        }
    }

    fun saveConfig(config: SenderConfig) {
        viewModelScope.launch {
            repository.insertConfig(config)
        }
    }

    fun deleteConfig(config: SenderConfig) {
        viewModelScope.launch {
            repository.deleteConfig(config)
        }
    }

    fun toggleConfigActive(config: SenderConfig) {
        viewModelScope.launch {
            repository.updateConfig(config.copy(isActive = !config.isActive))
        }
    }
}
