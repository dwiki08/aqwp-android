package froztt13.python.aqw.helper

import android.util.Log
import froztt13.python.aqw.data.model.HubOverview
import froztt13.python.aqw.data.model.LogEntry
import froztt13.python.aqw.data.model.LogEntryType
import froztt13.python.aqw.data.repository.ConfigRepositoryImpl
import froztt13.python.aqw.utils.stripAnsi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Lightweight helper / legacy relay for global event logging and dashboard status aggregation.
 * Configuration operations are delegated to [ConfigRepositoryImpl].
 */
object BotHelper {
    private const val TAG = "BotHelper"

    private val logListeners = CopyOnWriteArrayList<(LogEntry) -> Unit>()

    fun init(filesDir: File) {
        ConfigRepositoryImpl.instance.init(filesDir)
    }

    fun stripAnsi(text: String): String {
        return text.stripAnsi()
    }

    fun dispatchLog(botType: LogEntryType, username: String = "System", message: String) {
        val cleanMsg = message.stripAnsi()
        if (cleanMsg.isEmpty()) return
        val entry = LogEntry(botType = botType, username = username, message = cleanMsg)
        for (listener in logListeners) {
            try {
                listener(entry)
            } catch (e: Exception) {
                Log.e(TAG, "Error in log listener: ${e.message}")
            }
        }
    }

    fun registerLogListener(filterBotType: LogEntryType? = null, onLog: (LogEntry) -> Unit): () -> Unit {
        val listener: (LogEntry) -> Unit = { entry ->
            if (filterBotType == null ||
                entry.botType == filterBotType ||
                entry.botType == LogEntryType.SYSTEM
            ) {
                onLog(entry)
            }
        }
        logListeners.add(listener)
        return {
            logListeners.remove(listener)
        }
    }

    fun registerLogListener(filterBotType: String?, onLog: (LogEntry) -> Unit): () -> Unit {
        val type = filterBotType?.let { LogEntryType.from(it) }
        return registerLogListener(type, onLog)
    }

    // --- Legacy Config Persistence Delegation ---
    suspend fun saveConfig(methodName: String, configJson: String): Boolean =
        ConfigRepositoryImpl.instance.saveRawConfig(methodName, configJson)

    suspend fun loadConfig(methodName: String): String? =
        ConfigRepositoryImpl.instance.loadRawConfig(methodName)

    suspend fun resetConfig(methodName: String): String? =
        ConfigRepositoryImpl.instance.resetRawConfig(methodName)

    suspend fun getHubStatus(): HubOverview = withContext(Dispatchers.IO) {
        froztt13.python.aqw.domain.coordinator.HubCoordinator.calculateOverview()
    }
}
