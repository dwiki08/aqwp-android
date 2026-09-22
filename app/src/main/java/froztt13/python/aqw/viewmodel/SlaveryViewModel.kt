package froztt13.python.aqw.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import froztt13.python.aqw.data.model.LogEntry
import froztt13.python.aqw.data.model.PartyStats
import froztt13.python.aqw.data.model.SlaveSlotConfig
import froztt13.python.aqw.data.model.SlaveryConfig
import froztt13.python.aqw.data.model.SlotTelemetry
import froztt13.python.aqw.data.repository.ConfigRepositoryImpl
import froztt13.python.aqw.domain.bot.slavery.NativeSlaveryBot
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

class SlaveryViewModel(
    private val configRepository: ConfigRepository = ConfigRepositoryImpl.instance
) : ViewModel() {

    private val _slaveryConfig = MutableStateFlow(SlaveryConfig())
    val slaveryConfig: StateFlow<SlaveryConfig> = _slaveryConfig.asStateFlow()

    val slaveryStatus: StateFlow<Map<String, SlotTelemetry>> = NativeSlaveryBot.status

    val partyStats: StateFlow<PartyStats> = NativeSlaveryBot.partyStats

    val isRunning: StateFlow<Boolean> = NativeSlaveryBot.status
        .map { map -> map.values.any { it.running } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val slaveryLogs: StateFlow<List<LogEntry>> = NativeSlaveryBot.slotLogs
        .map { map ->
            map.values.flatten().sortedBy { it.timestamp }.takeLast(250)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadConfig()
    }

    private fun loadConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = configRepository.loadSlaveryConfig()
            if (loaded != null) {
                _slaveryConfig.value = loaded
            }
        }
    }

    fun updateGlobalSettings(
        server: String,
        followPlayer: String,
        defaultRoomNumber: Int,
        copyWalk: Boolean,
        autoZone: String,
        targetsPriority: String,
        whitelist: String,
        lockedZones: List<String>
    ) {
        _slaveryConfig.update {
            it.copy(
                server = server,
                followPlayer = followPlayer,
                defaultRoomNumber = defaultRoomNumber,
                copyWalk = copyWalk,
                autoZone = autoZone,
                targetsPriority = targetsPriority,
                whitelist = whitelist,
                lockedZones = lockedZones
            )
        }
        saveSlaveryConfig()
    }

    fun updateSlot(slotKey: String, slotConfig: SlaveSlotConfig) {
        _slaveryConfig.update {
            val newSlots = it.slots.toMutableMap()
            newSlots[slotKey] = slotConfig
            it.copy(slots = newSlots)
        }
        saveSlaveryConfig()
    }

    fun saveSlaveryConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            configRepository.saveSlaveryConfig(_slaveryConfig.value)
        }
    }

    fun resetSlaveryConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            _slaveryConfig.value = configRepository.resetSlaveryConfig()
            saveSlaveryConfig()
        }
    }

    fun startSlavery(onResult: (Boolean, String?) -> Unit) {
        val (success, error) = NativeSlaveryBot.start(_slaveryConfig.value)
        viewModelScope.launch(Dispatchers.Main) {
            onResult(success, error)
        }
    }

    fun stopSlavery() {
        NativeSlaveryBot.stop()
    }

    fun clearLogs(slotKey: String? = null) {
        NativeSlaveryBot.clearLogs(slotKey)
    }
}
