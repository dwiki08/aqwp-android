package froztt13.python.aqw.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import froztt13.python.aqw.data.model.EclipseConfig
import froztt13.python.aqw.data.model.EclipseTauntInfo
import froztt13.python.aqw.data.model.LogEntry
import froztt13.python.aqw.data.model.PartyStats
import froztt13.python.aqw.data.model.SlotConfig
import froztt13.python.aqw.data.model.SlotTelemetry
import froztt13.python.aqw.data.repository.ConfigRepositoryImpl
import froztt13.python.aqw.domain.bot.eclipse.NativeEclipseBot
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

class EclipseViewModel(
    private val configRepository: ConfigRepository = ConfigRepositoryImpl.instance
) : ViewModel() {

    private val _eclipseConfig = MutableStateFlow(EclipseConfig())
    val eclipseConfig: StateFlow<EclipseConfig> = _eclipseConfig.asStateFlow()

    val eclipseStatus: StateFlow<Map<String, SlotTelemetry>> = NativeEclipseBot.status

    val partyStats: StateFlow<PartyStats> = NativeEclipseBot.stats

    val tauntInfo: StateFlow<EclipseTauntInfo> = NativeEclipseBot.tauntInfo

    val isRunning: StateFlow<Boolean> = NativeEclipseBot.status
        .map { map -> map.values.any { it.running } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isPaused: StateFlow<Boolean> = NativeEclipseBot.isPaused

    val slotLogs: StateFlow<Map<String, List<LogEntry>>> = NativeEclipseBot.slotLogs

    val eclipseLogs: StateFlow<List<LogEntry>> = NativeEclipseBot.slotLogs
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
            val loaded = configRepository.loadEclipseConfig()
            if (loaded != null) {
                _eclipseConfig.value = loaded.enforceFixedRoles()
            }
        }
    }

    fun updateEclipseSettings(
        server: String,
        roomNumber: Int
    ) {
        _eclipseConfig.update {
            it.copy(
                server = server,
                roomNumber = roomNumber
            ).enforceFixedRoles()
        }
        if (NativeEclipseBot.isRunning) {
            NativeEclipseBot.updateRuntimeConfig(_eclipseConfig.value)
        }
        saveEclipseConfig()
    }

    fun toggleLightGatherSlot(slotKey: String) {
        _eclipseConfig.update { it.withToggledLightGather(slotKey) }
        if (NativeEclipseBot.isRunning) {
            NativeEclipseBot.updateRuntimeConfig(_eclipseConfig.value)
        }
        saveEclipseConfig()
    }

    fun updateEclipseSlot(slotKey: String, slotConfig: SlotConfig) {
        _eclipseConfig.update { it.withUpdatedSlot(slotKey, slotConfig) }
        if (NativeEclipseBot.isRunning) {
            NativeEclipseBot.updateRuntimeConfig(_eclipseConfig.value)
        }
        saveEclipseConfig()
    }

    fun saveEclipseConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            configRepository.saveEclipseConfig(_eclipseConfig.value)
        }
    }

    fun resetEclipseConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            _eclipseConfig.value = configRepository.resetEclipseConfig().enforceFixedRoles()
            saveEclipseConfig()
        }
    }

    fun startEclipse(onResult: (Boolean, String?) -> Unit) {
        val (success, error) = NativeEclipseBot.start(_eclipseConfig.value)
        viewModelScope.launch(Dispatchers.Main) {
            onResult(success, error)
        }
    }

    fun stopEclipse() {
        NativeEclipseBot.stop()
    }

    fun pauseEclipse() {
        NativeEclipseBot.pause()
    }

    fun resumeEclipse() {
        NativeEclipseBot.resume()
    }

    fun clearLogs(slotKey: String? = null) {
        NativeEclipseBot.clearLogs(slotKey)
    }

    fun clearSlotLogs(slotKey: String) {
        NativeEclipseBot.clearLogs(slotKey)
    }
}
