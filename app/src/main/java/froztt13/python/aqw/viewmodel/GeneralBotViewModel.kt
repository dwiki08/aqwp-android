package froztt13.python.aqw.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import froztt13.python.aqw.data.model.GeneralBotConfig
import froztt13.python.aqw.data.model.GeneralBotTelemetry
import froztt13.python.aqw.data.model.GeneralSubModuleInfo
import froztt13.python.aqw.data.model.LogEntry
import froztt13.python.aqw.data.repository.ConfigRepositoryImpl
import froztt13.python.aqw.domain.repository.ConfigRepository
import froztt13.python.aqw.helper.BotHelper
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

class GeneralBotViewModel(
    private val configRepository: ConfigRepository = ConfigRepositoryImpl.instance
) : ViewModel() {

    private val _config = MutableStateFlow(GeneralBotConfig())
    val config: StateFlow<GeneralBotConfig> = _config.asStateFlow()

    val telemetry: StateFlow<GeneralBotTelemetry> =
        froztt13.python.aqw.domain.bot.general.NativeGeneralBot.telemetry

    val isRunning: StateFlow<Boolean> = telemetry
        .map { it.running }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isPaused: StateFlow<Boolean> =
        froztt13.python.aqw.domain.bot.general.NativeGeneralBot.isPaused

    private val _subModules = MutableStateFlow<List<GeneralSubModuleInfo>>(emptyList())
    val subModules: StateFlow<List<GeneralSubModuleInfo>> = _subModules.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _errorMessage = MutableSharedFlow<String>()
    val errorMessage: SharedFlow<String> = _errorMessage.asSharedFlow()

    private var unsubscribeLogs: (() -> Unit)? = null

    init {
        // Register log listener for General Bot
        unsubscribeLogs = BotHelper.registerLogListener("general") { entry ->
            _logs.update { list -> (list + entry).takeLast(300) }
        }

        loadSubModules()
        loadConfig()
    }

    private fun loadSubModules() {
        // Native modules catalog
        _subModules.value =
            froztt13.python.aqw.domain.bot.general.NativeGeneralBot.availableSubModules
    }

    private fun loadConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = configRepository.loadGeneralConfig()
            if (loaded != null) {
                _config.value = loaded
            }
        }
    }

    fun updateServer(server: String) {
        _config.update { it.copy(server = server) }
        saveConfig()
    }

    fun updateRoomNumber(room: Int) {
        _config.update { it.copy(roomNumber = room) }
        saveConfig()
    }

    fun updateUsername(username: String) {
        _config.update { it.copy(username = username) }
        saveConfig()
    }

    fun updatePassword(password: String) {
        _config.update { it.copy(password = password) }
        saveConfig()
    }

    fun updateSubModule(subModuleId: String) {
        val sub = _subModules.value.find { it.id == subModuleId }
        val defaultTask = sub?.tasks?.firstOrNull()
        _config.update {
            it.copy(
                subModule = subModuleId,
                task = defaultTask?.id ?: it.task,
                targetQty = defaultTask?.defaultQty ?: it.targetQty
            )
        }
        saveConfig()
    }

    fun updateTask(taskId: String) {
        val currentSub = _subModules.value.find { it.id == _config.value.subModule }
        val task = currentSub?.tasks?.find { it.id == taskId }
        _config.update {
            it.copy(
                task = taskId,
                targetQty = task?.defaultQty ?: it.targetQty
            )
        }
        saveConfig()
    }

    fun updateTargetQty(qty: Int) {
        _config.update { it.copy(targetQty = qty.coerceAtLeast(1)) }
        saveConfig()
    }

    fun updateSoloClass(cls: String) {
        _config.update { it.copy(soloClass = cls) }
        saveConfig()
    }

    fun updateFarmClass(cls: String) {
        _config.update { it.copy(farmClass = cls) }
        saveConfig()
    }

    fun saveConfig() {
        val current = _config.value
        viewModelScope.launch(Dispatchers.IO) {
            configRepository.saveGeneralConfig(current)
        }
    }

    fun resetConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            _config.value = configRepository.resetGeneralConfig()
            saveConfig()
        }
    }

    fun resetState() {
        froztt13.python.aqw.domain.bot.general.NativeGeneralBot.resetState()
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun startBot(onStarted: () -> Unit = {}) {
        val current = _config.value
        if (current.username.isBlank()) {
            viewModelScope.launch {
                _errorMessage.emit("Username cannot be empty")
            }
            return
        }
        if (current.password.isBlank()) {
            viewModelScope.launch {
                _errorMessage.emit("Password cannot be empty")
            }
            return
        }

        froztt13.python.aqw.domain.bot.general.NativeGeneralBot.start(current)
        viewModelScope.launch {
            onStarted()
        }
    }

    fun stopBot() {
        froztt13.python.aqw.domain.bot.general.NativeGeneralBot.stop()
    }

    fun pauseBot() {
        froztt13.python.aqw.domain.bot.general.NativeGeneralBot.pause()
    }

    fun resumeBot() {
        froztt13.python.aqw.domain.bot.general.NativeGeneralBot.resume()
    }

    override fun onCleared() {
        super.onCleared()
        unsubscribeLogs?.invoke()
        unsubscribeLogs = null
    }
}
