package froztt13.python.aqw.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import froztt13.python.aqw.data.model.LogEntry
import froztt13.python.aqw.data.model.LogEntryType
import froztt13.python.aqw.data.model.PartyStats
import froztt13.python.aqw.data.model.SlotConfig
import froztt13.python.aqw.data.model.SlotTelemetry
import froztt13.python.aqw.data.model.UltraBossConfig
import froztt13.python.aqw.data.model.UltraBossData
import froztt13.python.aqw.data.model.UltraBossType
import froztt13.python.aqw.data.repository.ConfigRepositoryImpl
import froztt13.python.aqw.domain.repository.ConfigRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UltraBossViewModel(
    private val configRepository: ConfigRepository = ConfigRepositoryImpl.instance
) : ViewModel() {

    private val _ultraBossConfig = MutableStateFlow(UltraBossConfig())
    val ultraBossConfig: StateFlow<UltraBossConfig> = _ultraBossConfig.asStateFlow()

    private val _selectedBossTab = MutableStateFlow(UltraBossType.GRAMIEL)
    val selectedBossTab: StateFlow<UltraBossType> = _selectedBossTab.asStateFlow()

    private val _telemetryMap = MutableStateFlow<Map<String, SlotTelemetry>>(
        mapOf(
            "slot1" to SlotTelemetry(),
            "slot2" to SlotTelemetry(),
            "slot3" to SlotTelemetry(),
            "slot4" to SlotTelemetry()
        )
    )
    val telemetryMap: StateFlow<Map<String, SlotTelemetry>> = _telemetryMap.asStateFlow()

    private val _partyStats = MutableStateFlow(PartyStats())
    val partyStats: StateFlow<PartyStats> = _partyStats.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(
        listOf(
            LogEntry(
                botType = LogEntryType.INFO,
                username = "UltraBossHub",
                message = "Ultra Boss Hub initialized. Select boss module & configure 4-player party."
            )
        )
    )
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    init {
        loadConfig()
    }

    private fun loadConfig() {
        viewModelScope.launch {
            val loaded = configRepository.loadUltraBossConfig()
            if (loaded != null) {
                _ultraBossConfig.value = loaded
                _selectedBossTab.value = loaded.selectedBoss
            }
        }
    }

    fun selectBossTab(type: UltraBossType) {
        _selectedBossTab.value = type
        val currentCfg = _ultraBossConfig.value
        val updatedCfg = currentCfg.copy(selectedBoss = type)
        _ultraBossConfig.value = updatedCfg

        // Update default target on slots
        val target = type.defaultTarget
        val updatedSlots = updatedCfg.slots.mapValues { (_, slot) ->
            slot.copy(defaultTarget = target)
        }
        val finalCfg = updatedCfg.copy(slots = updatedSlots)
        _ultraBossConfig.value = finalCfg

        viewModelScope.launch {
            configRepository.saveUltraBossConfig(finalCfg)
        }

        addLog(
            "Switched active boss to ${type.displayName} (Map: /join ${type.mapName})",
            LogEntryType.SYSTEM
        )
    }

    fun updateSettings(
        server: String,
        roomNumber: Int,
        autoPotions: Boolean,
        useScrollOfEnrage: Boolean
    ) {
        val updated = _ultraBossConfig.value.copy(
            server = server,
            roomNumber = roomNumber,
            autoPotions = autoPotions,
            useScrollOfEnrage = useScrollOfEnrage
        )
        _ultraBossConfig.value = updated
        viewModelScope.launch {
            configRepository.saveUltraBossConfig(updated)
        }
        addLog(
            "Updated Ultra Boss settings: Server=$server, Room=$roomNumber, Potions=$autoPotions",
            LogEntryType.INFO
        )
    }

    fun updateSlot(slotKey: String, slotConfig: SlotConfig) {
        val current = _ultraBossConfig.value
        val newSlots = current.slots.toMutableMap()
        newSlots[slotKey] = slotConfig
        val updated = current.copy(slots = newSlots)
        _ultraBossConfig.value = updated

        viewModelScope.launch {
            configRepository.saveUltraBossConfig(updated)
        }
        addLog(
            "Updated $slotKey (${slotConfig.username}) -> Class: ${slotConfig.charClass}",
            LogEntryType.INFO
        )
    }

    fun startBot() {
        if (_isRunning.value) return
        _isRunning.value = true
        _isPaused.value = false

        val bossInfo = UltraBossData.getInfo(_selectedBossTab.value)
        addLog(
            "🚀 Starting Ultra Boss Party Bot for ${bossInfo.title} in /join ${bossInfo.mapName}-${_ultraBossConfig.value.roomNumber}",
            LogEntryType.SYSTEM
        )

        // Update telemetry slots running state
        val updatedMap = _telemetryMap.value.mapValues { (key, tele) ->
            tele.copy(
                running = true,
                map = "${bossInfo.mapName}-${_ultraBossConfig.value.roomNumber}",
                hp = 2500,
                maxHp = 2500,
                mp = 500,
                maxMp = 500,
                isInCombat = true,
                isDead = false
            )
        }
        _telemetryMap.value = updatedMap
    }

    fun stopBot() {
        if (!_isRunning.value) return
        _isRunning.value = false
        _isPaused.value = false

        addLog("⏹️ Stopped Ultra Boss Party Bot session.", LogEntryType.WARNING)

        val updatedMap = _telemetryMap.value.mapValues { (_, tele) ->
            tele.copy(
                running = false,
                isInCombat = false
            )
        }
        _telemetryMap.value = updatedMap
    }

    fun togglePause() {
        val nextState = !_isPaused.value
        _isPaused.value = nextState
        if (nextState) {
            addLog("⏸️ Party Bot Paused.", LogEntryType.WARNING)
        } else {
            addLog("▶️ Party Bot Resumed.", LogEntryType.INFO)
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    private fun addLog(message: String, type: LogEntryType = LogEntryType.INFO) {
        val currentList = _logs.value.toMutableList()
        currentList.add(
            LogEntry(
                botType = type,
                username = "UltraBossHub",
                message = message
            )
        )
        if (currentList.size > 200) {
            currentList.removeAt(0)
        }
        _logs.value = currentList
    }
}
