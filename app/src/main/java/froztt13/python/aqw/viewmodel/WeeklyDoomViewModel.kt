package froztt13.python.aqw.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import froztt13.python.aqw.core.doom.NativeWeeklyDoomBot
import froztt13.python.aqw.data.DoomAccount
import froztt13.python.aqw.data.LogEntry
import froztt13.python.aqw.data.WeeklyDoomConfig
import froztt13.python.aqw.data.WeeklyDoomTelemetry
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
import org.json.JSONArray
import org.json.JSONObject

class WeeklyDoomViewModel : ViewModel() {

    private val _doomConfig = MutableStateFlow(WeeklyDoomConfig())
    val doomConfig: StateFlow<WeeklyDoomConfig> = _doomConfig.asStateFlow()

    private val _doomStatus = MutableStateFlow(WeeklyDoomTelemetry())
    val doomStatus: StateFlow<WeeklyDoomTelemetry> = _doomStatus.asStateFlow()

    val isRunning: StateFlow<Boolean> = _doomStatus
        .map { it.running }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _doomLogs = MutableStateFlow<List<LogEntry>>(emptyList())
    val doomLogs: StateFlow<List<LogEntry>> = _doomLogs.asStateFlow()

    private val _errorMessage = MutableSharedFlow<String>()
    val errorMessage: SharedFlow<String> = _errorMessage.asSharedFlow()

    private var unsubscribeLogs: (() -> Unit)? = null

    init {
        // Register log listener for Weekly Doom
        unsubscribeLogs = BotHelper.registerLogListener("doom") { entry ->
            _doomLogs.update { list -> (list + entry).takeLast(300) }
        }

        // Load saved configs and start polling status
        loadConfig()
        startStatusLoop()
    }

    private fun loadConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            val jsonStr = BotHelper.loadConfig("doom_load_config")
            if (jsonStr != null) {
                try {
                    _doomConfig.value = BotHelper.parseWeeklyDoomConfig(jsonStr)
                } catch (e: Exception) {
                    Log.e("WeeklyDoomViewModel", "Error parsing doom config: ${e.message}")
                }
            }
        }
    }

    private fun startStatusLoop() {
        viewModelScope.launch(Dispatchers.IO) {
            NativeWeeklyDoomBot.telemetry.collect { nativeStatus ->
                _doomStatus.value = nativeStatus
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
            val jsonStr = BotHelper.serializeWeeklyDoomConfig(_doomConfig.value)
            BotHelper.saveConfig("doom_save_config", jsonStr)
        }
    }

    fun resetDoomConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            val jsonStr = BotHelper.resetConfig("doom_reset_config")
            if (jsonStr != null) {
                try {
                    _doomConfig.value = BotHelper.parseWeeklyDoomConfig(jsonStr)
                } catch (e: Exception) {
                    _doomConfig.value = WeeklyDoomConfig()
                }
            } else {
                _doomConfig.value = WeeklyDoomConfig()
                saveDoomConfig()
            }
        }
    }

    fun exportAccountsJson(): String {
        val arr = JSONArray()
        for (acc in _doomConfig.value.accounts) {
            if (acc.username.isNotBlank()) {
                val obj = JSONObject()
                obj.put("username", acc.username)
                obj.put("password", acc.password)
                obj.put("enabled", acc.enabled)
                arr.put(obj)
            }
        }
        return arr.toString(2)
    }

    fun parseAccountsJson(jsonStr: String): Pair<List<DoomAccount>?, String?> {
        return try {
            val trimmed = jsonStr.trim()
            val importedList = mutableListOf<DoomAccount>()

            if (trimmed.startsWith("[")) {
                val arr = JSONArray(trimmed)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val user = obj.optString("username", "").trim()
                    val pass = obj.optString("password", "").trim()
                    val enabled = obj.optBoolean("enabled", true)
                    if (user.isNotEmpty()) {
                        importedList.add(
                            DoomAccount(
                                username = user,
                                password = pass,
                                enabled = enabled
                            )
                        )
                    }
                }
            } else if (trimmed.startsWith("{")) {
                val root = JSONObject(trimmed)
                if (root.has("accounts")) {
                    val arr = root.optJSONArray("accounts")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i) ?: continue
                            val user = obj.optString("username", "").trim()
                            val pass = obj.optString("password", "").trim()
                            val enabled = obj.optBoolean("enabled", true)
                            if (user.isNotEmpty()) {
                                importedList.add(
                                    DoomAccount(
                                        username = user,
                                        password = pass,
                                        enabled = enabled
                                    )
                                )
                            }
                        }
                    }
                } else {
                    // Key-value pairs: "username": "password"
                    val keys = root.keys()
                    while (keys.hasNext()) {
                        val user = keys.next().trim()
                        val pass = root.optString(user, "").trim()
                        if (user.isNotEmpty()) {
                            importedList.add(
                                DoomAccount(
                                    username = user,
                                    password = pass,
                                    enabled = true
                                )
                            )
                        }
                    }
                }
            } else {
                return Pair(null, "Invalid file format. Ensure the file is a JSON array or object.")
            }

            if (importedList.isEmpty()) {
                return Pair(null, "No valid accounts found in the JSON file.")
            }

            Pair(importedList, null)
        } catch (e: Exception) {
            Pair(null, "Failed to parse JSON: ${e.message}")
        }
    }

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
        _doomLogs.value = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        unsubscribeLogs?.invoke()
    }
}
