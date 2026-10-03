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
import froztt13.python.aqw.domain.bot.gramiel.NativeUltraGramielBot
import froztt13.python.aqw.domain.repository.ConfigRepository
import kotlinx.coroutines.Dispatchers
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

    val slotLogs: StateFlow<Map<String, List<LogEntry>>> = NativeUltraGramielBot.slotLogs

    val animMsg: StateFlow<String> = NativeUltraGramielBot.animMsg

    val isFinished: StateFlow<Boolean> = NativeUltraGramielBot.isFinished

    fun resetFinishedState() {
        NativeUltraGramielBot.resetFinishedState()
    }

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    @Volatile
    private var isConfigLoaded = false

    init {
        loadConfig()
        observeGramielBot()
    }

    private fun loadConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = configRepository.loadUltraBossConfig()
            if (loaded != null) {
                _ultraBossConfig.value = loaded
                _selectedBossTab.value = loaded.selectedBoss
            }
            isConfigLoaded = true
        }
    }

    private fun observeGramielBot() {
        viewModelScope.launch {
            NativeUltraGramielBot.status.collect { gramielStatus ->
                if (NativeUltraGramielBot.isRunning) {
                    _telemetryMap.value = gramielStatus
                    _isRunning.value = gramielStatus.values.any { it.running }
                }
            }
        }

        viewModelScope.launch {
            NativeUltraGramielBot.stats.collect { gramielStats ->
                if (NativeUltraGramielBot.isRunning) {
                    _partyStats.value = gramielStats
                }
            }
        }

        viewModelScope.launch {
            NativeUltraGramielBot.slotLogs.collect { slotLogsMap ->
                if (NativeUltraGramielBot.isRunning) {
                    val combinedLogs = slotLogsMap.values.flatten().sortedBy { it.timestamp }
                    _logs.value = combinedLogs.takeLast(200)
                }
            }
        }

        viewModelScope.launch {
            NativeUltraGramielBot.isPaused.collect { paused ->
                if (NativeUltraGramielBot.isRunning) {
                    _isPaused.value = paused
                }
            }
        }
    }

    fun selectBossTab(type: UltraBossType) {
        _selectedBossTab.value = type
        if (!isConfigLoaded) return

        val currentCfg = _ultraBossConfig.value
        if (currentCfg.selectedBoss == type) return

        val target = type.defaultTarget
        val updatedSlots = currentCfg.slots.mapValues { (_, slot) ->
            slot.copy(defaultTarget = target)
        }
        val finalCfg = currentCfg.copy(selectedBoss = type, slots = updatedSlots)
        _ultraBossConfig.value = finalCfg

        viewModelScope.launch(Dispatchers.IO) {
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
        saveConfig()
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
        saveConfig()

        addLog(
            "Updated $slotKey (${slotConfig.username}) -> Class: ${slotConfig.charClass}",
            LogEntryType.INFO
        )
    }

    private fun saveConfig() {
        if (!isConfigLoaded) return
        val current = _ultraBossConfig.value
        viewModelScope.launch(Dispatchers.IO) {
            configRepository.saveUltraBossConfig(current)
        }
    }

    fun startBot() {
        if (_isRunning.value) return

        val bossInfo = UltraBossData.getInfo(_selectedBossTab.value)
        addLog(
            "🚀 Starting Ultra Boss Party Bot for ${bossInfo.title} in /join ${bossInfo.mapName}-${_ultraBossConfig.value.roomNumber}",
            LogEntryType.SYSTEM
        )

        if (_selectedBossTab.value == UltraBossType.GRAMIEL) {
            val (started, errorMsg) = NativeUltraGramielBot.start(_ultraBossConfig.value)
            if (!started) {
                addLog("❌ Failed to start Native Ultra Gramiel Bot: $errorMsg", LogEntryType.ERROR)
                return
            }
            _isRunning.value = true
            _isPaused.value = false
        } else {
            _isRunning.value = true
            _isPaused.value = false
            val updatedMap = _telemetryMap.value.mapValues { (_, tele) ->
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
    }

    fun stopBot() {
        if (!_isRunning.value) return

        if (_selectedBossTab.value == UltraBossType.GRAMIEL && NativeUltraGramielBot.isRunning) {
            NativeUltraGramielBot.stop()
        }

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
        if (_selectedBossTab.value == UltraBossType.GRAMIEL && NativeUltraGramielBot.isRunning) {
            if (_isPaused.value) {
                NativeUltraGramielBot.resume()
            } else {
                NativeUltraGramielBot.pause()
            }
            _isPaused.value = !_isPaused.value
            return
        }

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

    fun clearSlotLogs(slotKey: String) {
        if (_selectedBossTab.value == UltraBossType.GRAMIEL) {
            NativeUltraGramielBot.clearLogs(slotKey)
        }
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
