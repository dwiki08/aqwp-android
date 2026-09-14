package froztt13.python.aqw.core.doom

import froztt13.python.aqw.core.engine.AqwEvent
import froztt13.python.aqw.core.engine.AqwSession
import froztt13.python.aqw.data.DoomAccount
import froztt13.python.aqw.data.DoomAccountTelemetry
import froztt13.python.aqw.data.LogEntry
import froztt13.python.aqw.data.WeeklyDoomConfig
import froztt13.python.aqw.data.WeeklyDoomTelemetry
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
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.milliseconds

object NativeWeeklyDoomBot {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var runnerJob: Job? = null
    private var currentSession: AqwSession? = null

    private val _telemetry = MutableStateFlow(WeeklyDoomTelemetry())
    val telemetry: StateFlow<WeeklyDoomTelemetry> = _telemetry.asStateFlow()

    private val logListeners = CopyOnWriteArrayList<(LogEntry) -> Unit>()

    private var stopRequested = false
    private var startTimeMillis: Long = 0L

    fun registerLogListener(onLog: (LogEntry) -> Unit): () -> Unit {
        logListeners.add(onLog)
        return { logListeners.remove(onLog) }
    }

    private fun dispatchLog(username: String, message: String) {
        froztt13.python.aqw.helper.BotHelper.dispatchLog("doom", username, message)
        val entry = LogEntry(botType = "doom", username = username, message = message)
        for (listener in logListeners) {
            try {
                listener(entry)
            } catch (_: Exception) {
            }
        }
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
        for (acc in config.accounts) {
            val isEnabled = acc.enabled && acc.username.isNotBlank()
            initialStatuses[acc.id] = DoomAccountTelemetry(
                id = acc.id,
                username = acc.username,
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
                val password = acc.password.trim()
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

                dispatchLog(
                    username,
                    "=== [Weekly Doom] Starting account ${idx + 1}/${accounts.size}: $username ==="
                )

                val session = AqwSession()
                currentSession = session
                val wheelDrops = mutableListOf<String>()

                val eventJob = scope.launch(Dispatchers.IO) {
                    session.events.collect { event ->
                        when (event) {
                            is AqwEvent.WheelSpun -> {
                                wheelDrops.addAll(event.dropNames)
                                dispatchLog(
                                    username,
                                    "Wheel spun drops: ${event.dropNames.joinToString(", ")}"
                                )
                            }

                            is AqwEvent.ItemDropped -> {
                                dispatchLog(
                                    username,
                                    "Item dropped: ${event.itemName} x${event.qty}"
                                )
                            }

                            is AqwEvent.Warning -> {
                                dispatchLog(username, "Warning: ${event.message}")
                            }

                            else -> {}
                        }
                    }
                }

                var accountResultMsg = "Completed"
                var accountResultStatus = "Finished"
                var hasEioda = false

                try {
                    val connected = session.start(
                        username = username,
                        password = password,
                        preferredServer = server,
                        onLog = { msg -> dispatchLog(username, msg) }
                    )

                    if (!connected) {
                        accountResultStatus = "Failed"
                        accountResultMsg = "Login failed / check credentials"
                    } else {
                        var waitCount = 0
                        while (!session.isCharLoaded.value && waitCount < 150 && !stopRequested) {
                            delay(100.milliseconds)
                            waitCount++
                        }

                        if (!session.isCharLoaded.value) {
                            accountResultStatus = "Timeout"
                            accountResultMsg = "Character data load timeout"
                        } else {
                            hasEioda = session.playerState.inventory.any {
                                it.name.equals(
                                    "Epic Item of Digital Awesomeness",
                                    ignoreCase = true
                                )
                            }
                            if (hasEioda) {
                                dispatchLog(
                                    username,
                                    "*** EPIC ITEM OF DIGITAL AWESOMENESS IN INVENTORY! ***"
                                )
                            }

                            val hasInBank = session.playerState.bank.any {
                                it.name.equals("Gear of Doom", ignoreCase = true)
                            }
                            if (hasInBank) {
                                dispatchLog(
                                    username,
                                    "Moving Gear of Doom from bank to inventory..."
                                )
                                session.commands.bankToInv("Gear of Doom")
                                delay(1200.milliseconds)
                            }

                            val doomGears = session.playerState.inventory.firstOrNull {
                                it.name.equals("Gear of Doom", ignoreCase = true)
                            }
                            val gearQty = doomGears?.qty ?: 0

                            if (gearQty < 3) {
                                accountResultStatus = "Not enough Gear"
                                accountResultMsg = "Not enough Gear of Doom ($gearQty < 3)"
                                dispatchLog(username, accountResultMsg)
                            } else {
                                dispatchLog(
                                    username,
                                    "Has $gearQty Gear of Doom. Joining map 'doom'..."
                                )
                                session.commands.joinMap("doom")
                                delay(2000.milliseconds)

                                dispatchLog(username, "Accepting quest 3076 (Wheel of Doom)...")
                                session.commands.acceptQuest(3076)
                                delay(1500.milliseconds)

                                dispatchLog(username, "Turning in quest 3076 (Spinning Wheel)...")
                                session.commands.turnInQuest(3076)
                                delay(3000.milliseconds)

                                accountResultStatus = "Finished"
                                accountResultMsg = if (wheelDrops.isNotEmpty()) {
                                    "Finished: ${wheelDrops.joinToString(", ")}"
                                } else {
                                    "Completed successfully"
                                }
                                dispatchLog(username, "Spin completed!")
                            }
                        }
                    }
                } catch (e: Exception) {
                    accountResultStatus = "Error"
                    accountResultMsg = e.message ?: "Unknown error"
                    dispatchLog(username, "Error: $accountResultMsg")
                } finally {
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

                dispatchLog(
                    username,
                    "=== [Weekly Doom] Account $username result: $accountResultStatus ($accountResultMsg) ==="
                )

                if (!stopRequested && idx < accounts.size - 1) {
                    dispatchLog(username, "Waiting 3 seconds before next account...")
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
            dispatchLog("System", "=== [Weekly Doom] All accounts processed. Runner finished. ===")
        }
    }
}
