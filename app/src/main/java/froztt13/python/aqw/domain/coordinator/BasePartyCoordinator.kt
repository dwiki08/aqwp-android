package froztt13.python.aqw.domain.coordinator

import android.util.Log
import froztt13.python.aqw.data.engine.AqwSession
import froztt13.python.aqw.data.model.LogEntry
import froztt13.python.aqw.data.model.PartyStats
import froztt13.python.aqw.data.model.SlotTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/**
 * Base coordinator for multi-slot party bots (e.g. Eclipse, Temple).
 * Encapsulates party lifecycle, slot session management, stats tracking, and state flows.
 */
abstract class BasePartyCoordinator(protected val tag: String) {

    protected val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    protected var coordinatorJob: Job? = null
    protected var timerJob: Job? = null

    val activeSessions = ConcurrentHashMap<String, AqwSession>()

    protected val _status = MutableStateFlow<Map<String, SlotTelemetry>>(emptyMap())
    val status: StateFlow<Map<String, SlotTelemetry>> = _status.asStateFlow()

    protected val _stats = MutableStateFlow(PartyStats())
    val stats: StateFlow<PartyStats> = _stats.asStateFlow()

    protected val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    protected val _slotLogs = MutableStateFlow<Map<String, List<LogEntry>>>(emptyMap())
    val slotLogs: StateFlow<Map<String, List<LogEntry>>> = _slotLogs.asStateFlow()

    @Volatile
    protected var stopRequested = false
    protected var startTimeMillis = 0L
    protected var pausedAtMillis = 0L
    protected var clearedRuns = 0

    val isRunning: Boolean
        get() = _status.value.values.any { it.running }

    fun getSession(slotKey: String): AqwSession? = activeSessions[slotKey]

    open fun logToSession(slotKey: String, message: String, botType: String = "System") {
        activeSessions[slotKey]?.log(message, botType = botType)
    }

    fun logToAllSessions(message: String) {
        activeSessions.values.forEach { it.log(message, botType = "System") }
    }

    fun clearLogs(slotKey: String? = null) {
        if (slotKey != null) {
            activeSessions[slotKey]?.clearLogs()
            _slotLogs.update { it + (slotKey to emptyList()) }
        } else {
            activeSessions.values.forEach { it.clearLogs() }
            _slotLogs.value = emptyMap()
        }
    }

    protected fun startTimer(onTick: ((Long) -> Unit)? = null) {
        timerJob?.cancel()
        timerJob = scope.launch(Dispatchers.IO) {
            while (isActive && !stopRequested) {
                delay(1.seconds)
                if (!_isPaused.value && startTimeMillis > 0) {
                    val elapsed = System.currentTimeMillis() - startTimeMillis
                    _stats.update { it.copy(timeRunning = elapsed, clearedCount = clearedRuns) }
                    onTick?.invoke(elapsed)
                }
            }
        }
    }

    protected fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    protected fun stopAllSessions() {
        for ((_, session) in activeSessions) {
            try {
                session.stop()
            } catch (e: Exception) {
                Log.e(tag, "Error stopping session: ${e.message}")
            }
        }
        activeSessions.clear()
    }
}
