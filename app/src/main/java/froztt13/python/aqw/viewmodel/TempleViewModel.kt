package froztt13.python.aqw.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import froztt13.python.aqw.data.model.LogEntry
import froztt13.python.aqw.data.model.PartyStats
import froztt13.python.aqw.data.model.SlotConfig
import froztt13.python.aqw.data.model.SlotTelemetry
import froztt13.python.aqw.data.model.TempleConfig
import froztt13.python.aqw.data.repository.ConfigRepositoryImpl
import froztt13.python.aqw.domain.bot.temple.NativeTempleBot
import froztt13.python.aqw.domain.repository.ConfigRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TempleViewModel(
    private val configRepository: ConfigRepository = ConfigRepositoryImpl.instance
) : ViewModel() {

    private val _templeConfig = MutableStateFlow(TempleConfig())
    val templeConfig: StateFlow<TempleConfig> = _templeConfig.asStateFlow()

    val templeStatus: StateFlow<Map<String, SlotTelemetry>> = NativeTempleBot.status

    val partyStats: StateFlow<PartyStats> = NativeTempleBot.stats

    val isRunning: StateFlow<Boolean> = NativeTempleBot.status
        .map { map -> map.values.any { it.running } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isPaused: StateFlow<Boolean> = NativeTempleBot.isPaused
    val latestAnimMsg: StateFlow<String> = NativeTempleBot.latestAnimMsg

    val slotLogs: StateFlow<Map<String, List<LogEntry>>> = NativeTempleBot.slotLogs

    val templeLogs: StateFlow<List<LogEntry>> = NativeTempleBot.slotLogs
        .map { map ->
            map.values.flatten().sortedBy { it.timestamp }.takeLast(250)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Load saved configs
        loadConfig()
    }

    private fun loadConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = configRepository.loadTempleConfig()
            if (loaded != null) {
                _templeConfig.value = loaded
            }
        }
    }

    fun updateTempleSettings(server: String, roomNumber: Int, botType: String) {
        _templeConfig.update {
            it.copy(
                server = server,
                roomNumber = roomNumber,
                templeBotType = botType
            )
        }
        saveTempleConfig()
    }

    fun updateTempleSlot(slotKey: String, slotConfig: SlotConfig) {
        _templeConfig.update {
            val newSlots = it.slots.toMutableMap()
            newSlots[slotKey] = slotConfig
            it.copy(slots = newSlots)
        }
        saveTempleConfig()
    }

    fun saveTempleConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            configRepository.saveTempleConfig(_templeConfig.value)
        }
    }

    fun resetTempleConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            _templeConfig.value = configRepository.resetTempleConfig()
            saveTempleConfig()
        }
    }

    fun startTemple(onResult: (Boolean, String?) -> Unit) {
        val (success, error) = NativeTempleBot.start(_templeConfig.value)
        viewModelScope.launch(Dispatchers.Main) {
            onResult(success, error)
        }
    }

    fun stopTemple() {
        NativeTempleBot.stop()
    }

    fun pauseTemple() {
        NativeTempleBot.pause()
    }

    fun resumeTemple() {
        NativeTempleBot.resume()
    }

    fun clearLogs(slotKey: String? = null) {
        NativeTempleBot.clearLogs(slotKey)
    }
}
