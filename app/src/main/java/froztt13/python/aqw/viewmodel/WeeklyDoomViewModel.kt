package froztt13.python.aqw.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import froztt13.python.aqw.data.model.DoomAccount
import froztt13.python.aqw.data.model.LogEntry
import froztt13.python.aqw.data.model.WeeklyDoomConfig
import froztt13.python.aqw.data.model.WeeklyDoomTelemetry
import froztt13.python.aqw.data.repository.ConfigRepositoryImpl
import froztt13.python.aqw.data.util.DoomAccountJsonParser
import froztt13.python.aqw.domain.bot.doom.NativeWeeklyDoomBot
import froztt13.python.aqw.domain.repository.ConfigRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WeeklyDoomViewModel(
    private val configRepository: ConfigRepository = ConfigRepositoryImpl.instance
) : ViewModel() {

    private val _doomConfig = MutableStateFlow(WeeklyDoomConfig())
    val doomConfig: StateFlow<WeeklyDoomConfig> = _doomConfig.asStateFlow()

    val doomStatus: StateFlow<WeeklyDoomTelemetry> = NativeWeeklyDoomBot.telemetry

    val isRunning: StateFlow<Boolean> = doomStatus
        .map { it.running }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val doomLogs: StateFlow<List<LogEntry>> = NativeWeeklyDoomBot.logs

    private val _errorMessage = MutableSharedFlow<String>()
    val errorMessage: SharedFlow<String> = _errorMessage.asSharedFlow()

    init {
        // Load saved configs
        loadConfig()
    }

    private fun loadConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = configRepository.loadDoomConfig()
            if (loaded != null) {
                _doomConfig.value = loaded
            }
        }
    }

    fun updateServer(server: String) {
        _doomConfig.update { it.copy(server = server) }
        saveDoomConfig()
    }

    fun addAccount(username: String = "", password: String = "") {
        _doomConfig.update {
            val currentAccounts =
                it.accounts.filter { acc -> acc.username.isNotBlank() }.toMutableList()
            currentAccounts.add(DoomAccount(username = username, password = password))
            it.copy(accounts = currentAccounts)
        }
        saveDoomConfig()
    }

    fun removeAccount(id: String) {
        _doomConfig.update {
            val currentAccounts = it.accounts.filter { acc -> acc.id != id }
            it.copy(accounts = currentAccounts)
        }
        saveDoomConfig()
    }

    fun updateAccount(id: String, username: String, password: String) {
        _doomConfig.update {
            val currentAccounts = it.accounts.map { acc ->
                if (acc.id == id) {
                    acc.copy(username = username, password = password)
                } else {
                    acc
                }
            }
            it.copy(accounts = currentAccounts)
        }
        saveDoomConfig()
    }

    fun toggleAccount(id: String, enabled: Boolean) {
        _doomConfig.update {
            val currentAccounts = it.accounts.map { acc ->
                if (acc.id == id) {
                    acc.copy(enabled = enabled)
                } else {
                    acc
                }
            }
            it.copy(accounts = currentAccounts)
        }
        saveDoomConfig()
    }

    fun updateAccountsOrder(newAccounts: List<DoomAccount>) {
        _doomConfig.update { it.copy(accounts = newAccounts) }
        saveDoomConfig()
    }

    fun moveAccount(fromIndex: Int, toIndex: Int) {
        _doomConfig.update { config ->
            if (fromIndex !in config.accounts.indices || toIndex !in config.accounts.indices || fromIndex == toIndex) {
                return@update config
            }
            val currentAccounts = config.accounts.toMutableList()
            val item = currentAccounts.removeAt(fromIndex)
            currentAccounts.add(toIndex, item)
            config.copy(accounts = currentAccounts)
        }
        saveDoomConfig()
    }

    fun saveDoomConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            configRepository.saveDoomConfig(_doomConfig.value)
        }
    }

    fun resetDoomConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            _doomConfig.value = configRepository.resetDoomConfig()
            saveDoomConfig()
        }
    }

    fun exportAccountsJson(): String =
        DoomAccountJsonParser.exportAccountsJson(_doomConfig.value.accounts)

    fun parseAccountsJson(jsonStr: String): Pair<List<DoomAccount>?, String?> =
        DoomAccountJsonParser.parseAccountsJson(jsonStr)

    fun importAccounts(importedList: List<DoomAccount>, replaceExisting: Boolean) {
        _doomConfig.update { config ->
            val updatedAccounts = if (replaceExisting) {
                importedList.toMutableList()
            } else {
                val current = config.accounts.filter { it.username.isNotBlank() }.toMutableList()
                for (newAcc in importedList) {
                    val existingIndex = current.indexOfFirst {
                        it.username.equals(
                            newAcc.username,
                            ignoreCase = true
                        )
                    }
                    if (existingIndex >= 0) {
                        current[existingIndex] = newAcc
                    } else {
                        current.add(newAcc)
                    }
                }
                current
            }
            if (updatedAccounts.isEmpty()) {
                updatedAccounts.add(DoomAccount())
            }
            config.copy(accounts = updatedAccounts)
        }
        saveDoomConfig()
    }

    fun startDoom(onResult: (Boolean, String?) -> Unit) {
        val enabledWithUser = _doomConfig.value.accounts.filter {
            it.enabled && it.username.isNotBlank()
        }
        if (enabledWithUser.isEmpty()) {
            val err = "Please configure at least one enabled account with a valid username"
            viewModelScope.launch {
                _errorMessage.emit(err)
            }
            onResult(false, err)
            return
        }

        val (success, error) = NativeWeeklyDoomBot.start(_doomConfig.value)
        if (!success && error != null) {
            viewModelScope.launch {
                _errorMessage.emit(error)
            }
        }
        onResult(success, error)
    }

    fun stopDoom() {
        NativeWeeklyDoomBot.stop()
    }

    fun clearLogs() {
        NativeWeeklyDoomBot.clearLogs()
    }
}
