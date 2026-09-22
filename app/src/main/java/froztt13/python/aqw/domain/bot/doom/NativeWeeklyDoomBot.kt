package froztt13.python.aqw.domain.bot.doom

import froztt13.python.aqw.data.engine.AqwEvent
import froztt13.python.aqw.data.engine.AqwSession
import froztt13.python.aqw.data.model.DoomAccount
import froztt13.python.aqw.data.model.DoomAccountTelemetry
import froztt13.python.aqw.data.model.LogEntry
import froztt13.python.aqw.data.model.LogEntryType
import froztt13.python.aqw.data.model.WeeklyDoomConfig
import froztt13.python.aqw.data.model.WeeklyDoomTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

object NativeWeeklyDoomBot {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var runnerJob: Job? = null
    private var currentSession: AqwSession? = null

    private val _telemetry = MutableStateFlow(WeeklyDoomTelemetry())
    val telemetry: StateFlow<WeeklyDoomTelemetry> = _telemetry.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private var stopRequested = false
    private var startTimeMillis: Long = 0L

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun start(config: WeeklyDoomConfig): Pair<Boolean, String?> {
        if (_telemetry.value.running) {
            return Pair(false, "Weekly Doom Bot is already running")
        }

        val enabledAccounts = config.accounts.filter { it.enabled && it.username.isNotBlank() }
        if (enabledAccounts.isEmpty()) {
            return Pair(false, "No enabled accounts configured with a username")
        }

        stopRequested = false
        startTimeMillis = System.currentTimeMillis()

        val initialStatuses = mutableMapOf<String, DoomAccountTelemetry>()
        for ((id, username, _, enabled) in config.accounts) {
            val isEnabled = enabled && username.isNotBlank()
            initialStatuses[id] = DoomAccountTelemetry(
                id = id,
                username = username,
                status = if (isEnabled) "Pending" else "Disabled",
                message = if (isEnabled) "Waiting in queue..." else "Disabled"
            )
        }

        _telemetry.value = WeeklyDoomTelemetry(
            running = true,
            totalAccounts = enabledAccounts.size,
            completedAccounts = 0,
            currentIndex = 0,
            currentUsername = "",
            accounts = initialStatuses
        )

        runnerJob?.cancel()
        runnerJob = scope.launch(Dispatchers.IO) {
            runAccounts(config.server, enabledAccounts)
        }

        return Pair(true, null)
    }

    fun stop() {
        stopRequested = true
        currentSession?.log("Weekly Doom stopped by user", LogEntryType.WARNING)
        currentSession?.stop()
        runnerJob?.cancel()
        _telemetry.update {
            it.copy(
                running = false,
                currentUsername = "",
                timeRunning = (System.currentTimeMillis() - startTimeMillis) / 1000L
            )
        }
    }

    private suspend fun runAccounts(server: String, accounts: List<DoomAccount>) {
        try {
            for (idx in accounts.indices) {
                if (stopRequested) break

                val acc = accounts[idx]
                val username = acc.username.trim()
                val accId = acc.id

                _telemetry.update { current ->
                    val accMap = current.accounts.toMutableMap()
                    accMap[accId] = accMap[accId]?.copy(
                        status = "Running",
                        message = "Logging in..."
                    ) ?: DoomAccountTelemetry(
                        id = accId,
                        username = username,
                        status = "Running",
                        message = "Logging in..."
                    )

                    current.copy(
                        currentIndex = idx + 1,
                        currentUsername = username,
                        accounts = accMap
                    )
                }

                val session = AqwSession()
                currentSession = session
                session.socketClient.tag = username
                session.playerState.username = username
                val wheelDrops = mutableListOf<String>()

                val logJob = scope.launch(Dispatchers.IO) {
                    session.logs.collect { sessionLogs ->
                        _logs.update { current ->
                            (current + sessionLogs).distinctBy { it.id }.takeLast(300)
                        }
                    }
                }

                val eventJob = scope.launch(Dispatchers.IO) {
                    session.events.collect { event ->
                        when (event) {
                            is AqwEvent.WheelSpun -> {
                                wheelDrops.addAll(event.dropNames)
                                session.log(
                                    "Wheel spun drops: ${event.dropNames.joinToString(", ")}",
                                    LogEntryType.INFO
                                )
                            }

                            else -> {}
                        }
                    }
                }

                var accountResultMsg: String
                var accountResultStatus: String
                var hasEioda = false

                try {
                    val result =
                        processAccount(session, server, acc, idx, accounts.size, wheelDrops)
                    accountResultStatus = result.status
                    accountResultMsg = result.message
                    hasEioda = result.hasEioda
                } catch (e: Exception) {
                    accountResultStatus = "Error"
                    accountResultMsg = e.message ?: "Unknown error"
                    session.log("Error: $accountResultMsg", LogEntryType.ERROR)
                } finally {
                    logJob.cancel()
                    eventJob.cancel()
                    session.stop()
                    currentSession = null
                }

                _telemetry.update { current ->
                    val accMap = current.accounts.toMutableMap()
                    accMap[accId] = accMap[accId]?.copy(
                        status = if (stopRequested) "Stopped" else accountResultStatus,
                        message = if (stopRequested) "Stopped by user" else accountResultMsg,
                        hasEioda = hasEioda,
                        wheelDrops = wheelDrops
                    ) ?: DoomAccountTelemetry(
                        id = accId,
                        username = username,
                        status = accountResultStatus,
                        message = accountResultMsg,
                        hasEioda = hasEioda,
                        wheelDrops = wheelDrops
                    )

                    current.copy(
                        completedAccounts = current.completedAccounts + 1,
                        timeRunning = (System.currentTimeMillis() - startTimeMillis) / 1000L,
                        accounts = accMap
                    )
                }

                session.log(
                    "=== [Weekly Doom] Account $username result: $accountResultStatus ($accountResultMsg) ===",
                    LogEntryType.INFO
                )

                if (!stopRequested && idx < accounts.size - 1) {
                    session.log("Waiting 3 seconds before next account...", LogEntryType.INFO)
                    delay(3000.milliseconds)
                }
            }
        } finally {
            _telemetry.update {
                it.copy(
                    running = false,
                    currentUsername = "",
                    timeRunning = (System.currentTimeMillis() - startTimeMillis) / 1000L
                )
            }
        }
    }

    private suspend fun processAccount(
        session: AqwSession,
        server: String,
        acc: DoomAccount,
        idx: Int,
        totalAccounts: Int,
        wheelDrops: List<String>
    ): AccountResult {
        val username = acc.username.trim()
        val password = acc.password.trim()

        session.log(
            "=== [Weekly Doom] Starting account ${idx + 1}/$totalAccounts: $username ===",
            LogEntryType.INFO
        )

        val connected = session.start(
            username = username,
            password = password,
            preferredServer = server
        )

        if (!connected) {
            session.log("Login failed / check credentials.", LogEntryType.ERROR)
            return AccountResult(status = "Failed", message = "Login failed / check credentials")
        }

        var waitCount = 0
        while (!session.isCharLoaded.value && waitCount < 150 && !stopRequested) {
            delay(100.milliseconds)
            waitCount++
        }

        if (!session.isCharLoaded.value) {
            session.log("Character data load timeout.", LogEntryType.ERROR)
            return AccountResult(status = "Timeout", message = "Character data load timeout")
        }

        val hasEioda = session.playerState.inventory.any {
            it.name.equals("Epic Item of Digital Awesomeness", ignoreCase = true)
        }
        if (hasEioda) {
            session.log(
                "*** EPIC ITEM OF DIGITAL AWESOMENESS IN INVENTORY! ***",
                LogEntryType.WARNING
            )
        }

        val hasInBank = session.playerState.bank.any {
            it.name.equals("Gear of Doom", ignoreCase = true)
        }
        if (hasInBank) {
            session.log("Moving Gear of Doom from bank to inventory...", LogEntryType.INFO)
            session.item.bankToInv("Gear of Doom")
            delay(1200.milliseconds)
        }

        val doomGears = session.playerState.inventory.firstOrNull {
            it.name.equals("Gear of Doom", ignoreCase = true)
        }
        val gearQty = doomGears?.qty ?: 0

        if (gearQty < 3) {
            val msg = "Not enough Gear of Doom ($gearQty < 3)"
            session.log(msg, LogEntryType.WARNING)
            return AccountResult(status = "Not enough Gear", message = msg, hasEioda = hasEioda)
        }

        session.log("Has $gearQty Gear of Doom. Joining map 'doom'...", LogEntryType.INFO)
        session.map.joinMap("doom")
        delay(2000.milliseconds)

        session.log("Accepting quest 3076 (Wheel of Doom)...", LogEntryType.INFO)
        session.quest.acceptQuest(3076)
        delay(1500.milliseconds)

        session.log("Turning in quest 3076 (Spinning Wheel)...", LogEntryType.INFO)
        session.quest.turnInQuest(3076)
        delay(3000.milliseconds)

        session.log("Spin completed!", LogEntryType.INFO)
        val resultMsg = if (wheelDrops.isNotEmpty()) {
            "Finished: ${wheelDrops.joinToString(", ")}"
        } else {
            "Completed successfully"
        }
        return AccountResult(
            status = "Finished",
            message = resultMsg,
            hasEioda = hasEioda,
            wheelDrops = wheelDrops
        )
    }

    private data class AccountResult(
        val status: String,
        val message: String,
        val hasEioda: Boolean = false,
        val wheelDrops: List<String> = emptyList()
    )
}
