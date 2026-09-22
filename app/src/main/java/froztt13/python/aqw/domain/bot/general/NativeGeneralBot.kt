package froztt13.python.aqw.domain.bot.general

import android.util.Log
import froztt13.python.aqw.data.engine.AqwEvent
import froztt13.python.aqw.data.engine.AqwSession
import froztt13.python.aqw.data.model.GeneralBotConfig
import froztt13.python.aqw.data.model.GeneralBotTelemetry
import froztt13.python.aqw.data.model.GeneralSubModuleInfo
import froztt13.python.aqw.data.model.GeneralTaskInfo
import froztt13.python.aqw.data.model.QuestRequirementTelemetry
import froztt13.python.aqw.helper.BotHelper
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
import kotlin.time.Duration.Companion.milliseconds

/**
 * Native General Bot coordinator and facade.
 * Aggregates and delegates execution to specialized bots:
 * - [NativeLegionRevenantBot] (Legion Revenant & Dage Items)
 * - [NativeNulgathBot] (Nulgath Larva & Nation Items)
 * - [NativeVoidAuraBot] (Void Auras & Boss Essences)
 */
object NativeGeneralBot {

    private const val TAG = "NativeGeneralBot"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var runnerJob: Job? = null
    private var currentSession: AqwSession? = null

    private val _telemetry = MutableStateFlow(GeneralBotTelemetry())
    val telemetry: StateFlow<GeneralBotTelemetry> = _telemetry.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private var stopRequested = false
    private var startTimeMillis = 0L
    private var pausedAtMillis = 0L

    val isRunning: Boolean
        get() = _telemetry.value.running

    fun pause() {
        if (!isRunning || _isPaused.value) return
        _isPaused.value = true
        pausedAtMillis = System.currentTimeMillis()

        scope.launch(Dispatchers.IO) {
            BotHelper.dispatchLog(
                "general",
                "System",
                "Pausing General Bot: leaving combat and jumping to current cell..."
            )
            val session = currentSession
            if (session != null) {
                try {
                    val currentCell = session.playerState.cell.ifBlank { "Enter" }
                    val currentPad = session.playerState.pad.ifBlank { "Spawn" }
                    session.map.jumpCell(currentCell, currentPad)
                    delay(200.milliseconds)
                    session.combat.rest()
                    session.playerState.isInCombat = false
                    BotHelper.dispatchLog(
                        "general",
                        session.playerState.username,
                        "Left combat, jumped to $currentCell [$currentPad]"
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error leaving combat on pause: ${e.message}")
                }
            }
            _telemetry.update {
                it.copy(
                    isPaused = true,
                    status = "PAUSED",
                    message = "General Bot is paused"
                )
            }
            BotHelper.dispatchLog("general", "System", "=== General Bot PAUSED ===")
        }
    }

    fun resume() {
        if (!isRunning || !_isPaused.value) return
        if (pausedAtMillis > 0) {
            startTimeMillis += (System.currentTimeMillis() - pausedAtMillis)
            pausedAtMillis = 0L
        }
        _isPaused.value = false
        _telemetry.update {
            it.copy(
                isPaused = false,
                status = "Farming",
                message = "Resuming ${it.taskName}..."
            )
        }
        currentSession?.quest?.triggerAutoQuestCheck()
        BotHelper.dispatchLog("general", "System", "=== General Bot RESUMED ===")
    }

    val availableSubModules: List<GeneralSubModuleInfo> by lazy {
        listOf(
            NativeLegionRevenantBot.subModuleInfo,
            NativeNulgathBot.subModuleInfo,
            NativeVoidAuraBot.subModuleInfo
        )
    }

    fun start(config: GeneralBotConfig): Pair<Boolean, String?> {
        if (isRunning) {
            return Pair(false, "General Bot is already running")
        }

        val username = config.username.trim()
        val password = config.password.trim()
        if (username.isBlank() || password.isBlank()) {
            return Pair(false, "Username and password are required.")
        }

        val subModule = availableSubModules.find { it.id == config.subModule }
            ?: return Pair(false, "Unknown sub-module: ${config.subModule}")

        val task = subModule.tasks.find { it.id == config.task }
            ?: return Pair(false, "Unknown task: ${config.task}")

        stopRequested = false
        _isPaused.value = false
        pausedAtMillis = 0L
        startTimeMillis = System.currentTimeMillis()

        _telemetry.value = GeneralBotTelemetry(
            running = true,
            username = username,
            subModule = config.subModule,
            subModuleName = subModule.name,
            task = config.task,
            taskName = task.name,
            trackedItem = task.trackedItem,
            currentQty = 0,
            targetQty = config.targetQty,
            status = "Starting",
            message = "Initializing ${task.name}...",
            timeRunning = 0L
        )

        runnerJob?.cancel()
        runnerJob = scope.launch(Dispatchers.IO) {
            runGeneralBot(config, task)
        }

        return Pair(true, null)
    }

    fun stop() {
        stopRequested = true
        _isPaused.value = false
        pausedAtMillis = 0L
        currentSession?.stop()
        runnerJob?.cancel()

        _telemetry.update {
            it.copy(
                running = false,
                isConnected = false,
                isPaused = false,
                status = "Stopped",
                message = "Stopped by user",
                timeRunning = (System.currentTimeMillis() - startTimeMillis) / 1000L
            )
        }
        BotHelper.dispatchLog("general", "System", "=== General Bot stopped by user ===")
    }

    fun resetState() {
        stop()
        _isPaused.value = false
        pausedAtMillis = 0L
        _telemetry.value = GeneralBotTelemetry()
    }

    private suspend fun runGeneralBot(config: GeneralBotConfig, task: GeneralTaskInfo) {
        val username = config.username.trim()
        val password = config.password.trim()
        val server = config.server.ifBlank { "Alteon" }

        val session = AqwSession()
        session.isPaused = { _isPaused.value }
        currentSession = session

        val targetQty = config.targetQty
        val trackedItem = task.trackedItem

        val timerJob = scope.launch(Dispatchers.IO) {
            while (isActive && !stopRequested) {
                if (!_isPaused.value) {
                    val elapsed = (System.currentTimeMillis() - startTimeMillis) / 1000L
                    val currentCds = session.getCooldowns()
                    val p = session.playerState
                    val questReqs = resolveQuestRequirements(session, config, task)
                    _telemetry.update {
                        it.copy(
                            timeRunning = elapsed,
                            isPaused = _isPaused.value,
                            cooldowns = currentCds,
                            hp = p.currentHp,
                            maxHp = p.maxHp,
                            mp = p.mp,
                            maxMp = p.maxMp,
                            map = p.mapName.ifBlank { it.map },
                            cell = p.cell.ifBlank { it.cell },
                            pad = p.pad.ifBlank { it.pad },
                            isDead = p.isDead,
                            isConnected = session.isConnected.value,
                            questRequirements = questReqs
                        )
                    }
                } else {
                    _telemetry.update {
                        it.copy(
                            isPaused = true,
                            isConnected = session.isConnected.value
                        )
                    }
                }
                delay(150.milliseconds)
            }
        }

        val eventJob = scope.launch(Dispatchers.IO) {
            session.events.collect { event ->
                if (_isPaused.value) return@collect
                when (event) {
                    is AqwEvent.ItemDropped -> {
                        val isWhitelisted = when (config.subModule) {
                            "nulgath" -> NativeNulgathBot.isDropWhitelisted(
                                event.itemName,
                                trackedItem
                            )

                            "lr" -> NativeLegionRevenantBot.isDropWhitelisted(
                                event.itemName,
                                trackedItem
                            )

                            else -> true
                        }
                        if (isWhitelisted) {
                            BotHelper.dispatchLog(
                                "general",
                                username,
                                "Picking up drop: ${event.itemName} x${event.qty}"
                            )
                            session.item.getItemDrop(event.itemId)
                        } else {
                            BotHelper.dispatchLog(
                                "general",
                                username,
                                "Ignoring drop (not in whitelist): ${event.itemName}"
                            )
                        }
                    }

                    else -> {}
                }
            }
        }

        try {
            BotHelper.dispatchLog("general", username, "Logging in to $server...")
            _telemetry.update {
                it.copy(
                    status = "Connecting",
                    message = "Connecting to $server..."
                )
            }

            val connected = session.start(
                username = username,
                password = password,
                preferredServer = server,
                onLog = { msg -> BotHelper.dispatchLog("general", username, msg) }
            )

            if (!connected) {
                BotHelper.dispatchLog("general", username, "Login failed / check credentials.")
                _telemetry.update { it.copy(status = "Failed", message = "Login failed.") }
                return
            }

            var loadWait = 0
            while (!session.isCharLoaded.value && loadWait < 150 && !stopRequested) {
                delay(100.milliseconds)
                loadWait++
            }

            if (!session.isCharLoaded.value) {
                BotHelper.dispatchLog("general", username, "Character load timed out.")
                return
            }

            // Equip farm class
            if (config.farmClass.isNotBlank()) {
                val classItem = session.playerState.inventory.firstOrNull {
                    it.name.equals(config.farmClass, ignoreCase = true)
                }
                if (classItem != null) {
                    session.item.equipItem(classItem.itemId)
                    delay(1000.milliseconds)
                }
            }

            // Initial tracked item count
            val initialQty = if (trackedItem.isNotBlank()) {
                session.playerState.inventory.firstOrNull {
                    it.name.equals(
                        trackedItem,
                        ignoreCase = true
                    )
                }?.qty ?: 0
            } else 0

            _telemetry.update {
                it.copy(
                    isConnected = true,
                    status = "Farming",
                    message = "Farming ${task.name} ($initialQty / $targetQty)",
                    currentQty = initialQty,
                    questRequirements = resolveQuestRequirements(session, config, task)
                )
            }

            // Delegate to specialized sub-bot
            when (config.subModule) {
                "lr" -> {
                    NativeLegionRevenantBot.execute(
                        session = session,
                        config = config,
                        task = task,
                        isStopRequested = { stopRequested || !scope.isActive }
                    ) { currentQty ->
                        updateTelemetryState(session, config, task, currentQty)
                    }
                }

                "nulgath" -> {
                    NativeNulgathBot.execute(
                        session = session,
                        config = config,
                        task = task,
                        isStopRequested = { stopRequested || !scope.isActive }
                    ) { currentQty ->
                        updateTelemetryState(session, config, task, currentQty)
                    }
                }

                "va" -> {
                    NativeVoidAuraBot.execute(
                        session = session,
                        config = config,
                        task = task,
                        isStopRequested = { stopRequested || !scope.isActive }
                    ) { currentQty ->
                        updateTelemetryState(session, config, task, currentQty)
                    }
                }

                else -> {
                    NativeLegionRevenantBot.execute(
                        session = session,
                        config = config,
                        task = task,
                        isStopRequested = { stopRequested || !scope.isActive }
                    ) { currentQty ->
                        updateTelemetryState(session, config, task, currentQty)
                    }
                }
            }

            val finalQty = if (trackedItem.isNotBlank()) {
                session.playerState.inventory.firstOrNull {
                    it.name.equals(
                        trackedItem,
                        ignoreCase = true
                    )
                }?.qty ?: 0
            } else 0

            _telemetry.update {
                it.copy(
                    status = if (targetQty in 1..finalQty) "Finished" else "Done",
                    message = "Task completed: $finalQty / $targetQty $trackedItem",
                    currentQty = finalQty,
                    questRequirements = resolveQuestRequirements(session, config, task)
                )
            }

        } catch (e: Exception) {
            BotHelper.dispatchLog("general", username, "Runner error: ${e.message}")
            Log.e(TAG, "runGeneralBot error", e)
        } finally {
            eventJob.cancel()
            timerJob.cancel()
            session.stop()
            currentSession = null
            _telemetry.update {
                it.copy(
                    running = false,
                    isConnected = false,
                    timeRunning = (System.currentTimeMillis() - startTimeMillis) / 1000L
                )
            }
        }
    }

    private fun updateTelemetryState(
        session: AqwSession,
        config: GeneralBotConfig,
        task: GeneralTaskInfo,
        currentQty: Int
    ) {
        val p = session.playerState
        val currentCds = session.getCooldowns()
        val questReqs = resolveQuestRequirements(session, config, task)
        _telemetry.update {
            it.copy(
                running = true,
                isConnected = session.isConnected.value,
                map = p.mapName.ifBlank { "-" },
                cell = p.cell.ifBlank { "-" },
                pad = p.pad.ifBlank { "-" },
                hp = p.currentHp,
                maxHp = p.maxHp,
                mp = p.mp,
                maxMp = p.maxMp,
                isDead = p.isDead,
                cooldowns = currentCds,
                currentQty = currentQty,
                questRequirements = questReqs
            )
        }
    }

    private fun resolveQuestRequirements(
        session: AqwSession,
        config: GeneralBotConfig,
        task: GeneralTaskInfo
    ): List<QuestRequirementTelemetry> {
        val result = mutableListOf<QuestRequirementTelemetry>()

        // 1. Check known predefined task requirements for General Bot
        when (task.id) {
            "spellscroll" -> {
                listOf(
                    "Aeacus Empowered" to 50,
                    "Tethered Soul" to 300,
                    "Darkened Essence" to 500,
                    "Dracolich Contract" to 1000
                ).forEach { (name, reqQty) ->
                    val cur =
                        session.item.getItemQty(name, false) + session.item.getItemQty(
                            name,
                            true
                        )
                    result.add(
                        QuestRequirementTelemetry(
                            itemName = name,
                            currentQty = cur,
                            requiredQty = reqQty
                        )
                    )
                }
                return result
            }

            "conquest_wreath" -> {
                listOf(
                    "Ancient Cohort Conquered" to 400,
                    "Grim Cohort Conquered" to 400,
                    "Pirate Cohort Conquered" to 400,
                    "Battleon Cohort Conquered" to 400,
                    "Mirror Cohort Conquered" to 400,
                    "Darkblood Cohort Conquered" to 400,
                    "Vampire Cohort Conquered" to 400,
                    "Spirit Cohort Conquered" to 400,
                    "Dragon Cohort Conquered" to 400,
                    "Doomwood Cohort Conquered" to 400
                ).forEach { (name, reqQty) ->
                    val cur =
                        session.item.getItemQty(name, false) + session.item.getItemQty(
                            name,
                            true
                        )
                    result.add(
                        QuestRequirementTelemetry(
                            itemName = name,
                            currentQty = cur,
                            requiredQty = reqQty
                        )
                    )
                }
                return result
            }

            "exalted_crown" -> {
                listOf(
                    "Hooded Legion Cowl" to 1,
                    "Legion Token" to 4000,
                    "Dage's Favor" to 300,
                    "Emblem of Dage" to 1,
                    "Diamond Token of Dage" to 30,
                    "Dark Token" to 100
                ).forEach { (name, reqQty) ->
                    val cur =
                        session.item.getItemQty(name, false) + session.item.getItemQty(
                            name,
                            true
                        )
                    result.add(
                        QuestRequirementTelemetry(
                            itemName = name,
                            currentQty = cur,
                            requiredQty = reqQty
                        )
                    )
                }
                return result
            }

            "emblem_of_dage" -> {
                listOf(
                    "Legion Seal" to 25,
                    "Gem of Mastery" to 1
                ).forEach { (name, reqQty) ->
                    val cur =
                        session.item.getItemQty(name, false) + session.item.getItemQty(
                            name,
                            true
                        )
                    result.add(
                        QuestRequirementTelemetry(
                            itemName = name,
                            currentQty = cur,
                            requiredQty = reqQty
                        )
                    )
                }
                return result
            }

            "diamond_token" -> {
                listOf(
                    "Defeated Makai" to 25,
                    "Carnax Eye" to 1,
                    "Red Dragon's Fang" to 1,
                    "Kathool Tentacle" to 1,
                    "Fluffy's Bones" to 1,
                    "Blood Titan's Blade" to 1
                ).forEach { (name, reqQty) ->
                    val cur =
                        session.item.getItemQty(name, false) + session.item.getItemQty(
                            name,
                            true
                        )
                    result.add(
                        QuestRequirementTelemetry(
                            itemName = name,
                            currentQty = cur,
                            requiredQty = reqQty
                        )
                    )
                }
                return result
            }

            "dark_token" -> {
                listOf(
                    "Seraphic Medals" to 5,
                    "Mega Seraphic Medals" to 3
                ).forEach { (name, reqQty) ->
                    val cur =
                        session.item.getItemQty(name, false) + session.item.getItemQty(
                            name,
                            true
                        )
                    result.add(
                        QuestRequirementTelemetry(
                            itemName = name,
                            currentQty = cur,
                            requiredQty = reqQty
                        )
                    )
                }
                return result
            }

            "larvae" -> {
                listOf(
                    "Mana Energy for Nulgath" to 1,
                    "Charged Mana Energy for Nulgath" to 5
                ).forEach { (name, reqQty) ->
                    val cur =
                        session.item.getItemQty(name, false) + session.item.getItemQty(
                            name,
                            true
                        )
                    result.add(
                        QuestRequirementTelemetry(
                            itemName = name,
                            currentQty = cur,
                            requiredQty = reqQty
                        )
                    )
                }
                return result
            }

            "retrieve_va" -> {
                listOf(
                    "Astral Ephemerite Essence" to 100,
                    "Belrot the Fiend Essence" to 100,
                    "Black Knight Essence" to 100,
                    "Tiger Leech Essence" to 100,
                    "Carnax Essence" to 100,
                    "Chaos Vordred Essence" to 100,
                    "Dai Tengu Essence" to 100,
                    "Unending Avatar Essence" to 100,
                    "Void Dragon Essence" to 100,
                    "Creature Creation Essence" to 100
                ).forEach { (name, reqQty) ->
                    val cur =
                        session.item.getItemQty(name, false) + session.item.getItemQty(
                            name,
                            true
                        )
                    result.add(
                        QuestRequirementTelemetry(
                            itemName = name,
                            currentQty = cur,
                            requiredQty = reqQty
                        )
                    )
                }
                return result
            }
        }

        // 2. Fallback to loaded quests from server
        if (task.questId > 0) {
            val q = session.playerState.loadedQuests.firstOrNull { it.questId == task.questId }
            if (q != null && q.turnInItems.isNotEmpty()) {
                q.turnInItems.forEach { req ->
                    val name = req.name.ifBlank {
                        session.playerState.getItemInventoryById(req.itemId)?.name
                            ?: session.playerState.getItemTempById(req.itemId)?.name
                            ?: session.playerState.getItemBankById(req.itemId)?.name
                            ?: "Item #${req.itemId}"
                    }
                    val cur = session.item.getItemQty(
                        req.itemId,
                        false
                    ) + session.item.getItemQty(req.itemId, true)
                    result.add(
                        QuestRequirementTelemetry(
                            itemId = req.itemId,
                            itemName = name,
                            currentQty = cur,
                            requiredQty = req.qty
                        )
                    )
                }
            }
        }

        return result
    }
}
