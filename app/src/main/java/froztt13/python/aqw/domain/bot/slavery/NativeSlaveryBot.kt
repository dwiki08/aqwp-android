package froztt13.python.aqw.domain.bot.slavery

import android.util.Log
import froztt13.python.aqw.data.engine.AqwEvent
import froztt13.python.aqw.data.engine.AqwSession
import froztt13.python.aqw.data.model.LogEntry
import froztt13.python.aqw.data.model.LogEntryType
import froztt13.python.aqw.data.model.MonsterTelemetry
import froztt13.python.aqw.data.model.PartyStats
import froztt13.python.aqw.data.model.Skill
import froztt13.python.aqw.data.model.SlaveSlotConfig
import froztt13.python.aqw.data.model.SlaveryConfig
import froztt13.python.aqw.data.model.SlotTelemetry
import froztt13.python.aqw.data.model.ThresholdType
import froztt13.python.aqw.domain.bot.temple.NativeTauntCoordinator
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
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

object NativeSlaveryBot {

    private const val TAG = "NativeSlaveryBot"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var coordinatorJob: Job? = null
    private val activeSessions = ConcurrentHashMap<String, AqwSession>()

    private val tauntCoordinator = NativeTauntCoordinator()

    private val _status = MutableStateFlow<Map<String, SlotTelemetry>>(emptyMap())
    val status: StateFlow<Map<String, SlotTelemetry>> = _status.asStateFlow()

    private val _partyStats = MutableStateFlow(PartyStats())
    val partyStats: StateFlow<PartyStats> = _partyStats.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _slotLogs = MutableStateFlow<Map<String, List<LogEntry>>>(emptyMap())
    val slotLogs: StateFlow<Map<String, List<LogEntry>>> = _slotLogs.asStateFlow()

    private var stopRequested = false
    private var startTimeMillis = 0L
    private var pausedAtMillis = 0L

    val isRunning: Boolean
        get() = _status.value.values.any { it.running }

    fun start(config: SlaveryConfig): Pair<Boolean, String?> {
        if (isRunning) {
            return false to "Slavery Bot is already running!"
        }

        val followPlayer = config.followPlayer.trim()
        if (followPlayer.isEmpty()) {
            return false to "Please specify the Master Account to follow."
        }

        val enabledSlots = config.slots.filter {
            it.value.enabled && it.value.username.isNotBlank() && it.value.password.isNotBlank()
        }
        if (enabledSlots.isEmpty()) {
            return false to "Please configure and enable at least one slot with credentials."
        }

        stopRequested = false
        _isPaused.value = false
        startTimeMillis = System.currentTimeMillis()
        pausedAtMillis = 0L
        tauntCoordinator.reset()

        val initialStatuses = mutableMapOf<String, SlotTelemetry>()
        for (key in listOf("slot1", "slot2", "slot3", "slot4")) {
            val isEnabled =
                config.slots[key]?.enabled == true && config.slots[key]?.username?.isNotBlank() == true
            initialStatuses[key] = SlotTelemetry(running = isEnabled)
        }
        _status.value = initialStatuses
        _partyStats.value = PartyStats(timeRunning = 0L, clearedCount = 0)

        coordinatorJob?.cancel()
        coordinatorJob = scope.launch(Dispatchers.IO) {
            runSlavery(config, enabledSlots)
        }

        return true to null
    }

    fun pause() {
        if (!isRunning || _isPaused.value) return
        _isPaused.value = true
        pausedAtMillis = System.currentTimeMillis()

        scope.launch(Dispatchers.IO) {
            activeSessions.values.forEach { session ->
                session.log(
                    "Pausing Slavery Party: all slots leaving combat...",
                    LogEntryType.WARNING
                )
            }
            val jobs = activeSessions.map { (slotKey, session) ->
                launch {
                    try {
                        val currentCell = session.playerState.cell.ifBlank { "Enter" }
                        val currentPad = session.playerState.pad.ifBlank { "Spawn" }
                        session.map.jumpCell(currentCell, currentPad)
                        delay(200.milliseconds)
                        session.combat.rest()
                        session.playerState.isInCombat = false
                        session.log(
                            "[$slotKey] Left combat, jumped to $currentCell [$currentPad]",
                            LogEntryType.INFO
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error leaving combat on pause for $slotKey: ${e.message}")
                    }
                }
            }
            jobs.joinAll()
            for ((slotKey, session) in activeSessions) {
                updateTelemetry(
                    slotKey = slotKey,
                    session = session,
                    isRunning = true
                )
            }
            activeSessions.values.forEach {
                it.log(
                    "=== Slavery Bot PAUSED ===",
                    LogEntryType.WARNING
                )
            }
        }
    }

    fun resume() {
        if (!isRunning || !_isPaused.value) return
        if (pausedAtMillis > 0) {
            startTimeMillis += (System.currentTimeMillis() - pausedAtMillis)
            pausedAtMillis = 0L
        }
        _isPaused.value = false
        activeSessions.values.forEach { it.log("=== Slavery Bot RESUMED ===", LogEntryType.INFO) }
    }

    fun stop() {
        stopRequested = true
        _isPaused.value = false
        pausedAtMillis = 0L
        activeSessions.values.forEach {
            it.log(
                "=== Slavery Bot stopped by user ===",
                LogEntryType.INFO
            )
        }
        for ((_, session) in activeSessions) {
            try {
                session.stop()
            } catch (_: Exception) {
            }
        }
        activeSessions.clear()
        coordinatorJob?.cancel()

        _status.update { current ->
            current.mapValues { (_, tele) -> tele.copy(running = false, isConnected = false) }
        }
        _partyStats.update { it.copy(timeRunning = (System.currentTimeMillis() - startTimeMillis) / 1000L) }
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

    private suspend fun runSlavery(
        config: SlaveryConfig,
        activeSlots: Map<String, SlaveSlotConfig>
    ) {
        val server = config.server.ifBlank { "Gravelyn" }
        val followPlayer = config.followPlayer.trim()
        val roomNumber = config.defaultRoomNumber
        val targetsPriority = config.targetsPriority.split(",").map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
        val whitelistItems =
            config.whitelist.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val lockedZones = config.lockedZones.ifEmpty {
            listOf(
                "ultraezrajal",
                "ultrawarden",
                "ultraengineer",
                "doomvault",
                "doomvaultb",
                "championdrakath",
                "tercessuinotlim",
                "icestormunder"
            )
        }

        val slotJobs = mutableListOf<Job>()

        val timerJob = scope.launch(Dispatchers.IO) {
            while (isActive && !stopRequested) {
                delay(1000.milliseconds)
                if (!_isPaused.value && startTimeMillis > 0) {
                    val elapsed = (System.currentTimeMillis() - startTimeMillis) / 1000L
                    _partyStats.update { it.copy(timeRunning = elapsed) }
                }
            }
        }

        try {
            for ((slotKey, slotConf) in activeSlots) {
                val job = scope.launch(Dispatchers.IO) {
                    runSlaveWorker(
                        slotKey = slotKey,
                        slotConfig = slotConf,
                        server = server,
                        followPlayer = followPlayer,
                        roomNumber = roomNumber,
                        targetsPriority = targetsPriority,
                        whitelistItems = whitelistItems,
                        lockedZones = lockedZones,
                        copyWalk = config.copyWalk
                    )
                }
                slotJobs.add(job)
            }

            slotJobs.joinAll()
        } catch (e: Exception) {
            Log.e(TAG, "Error in Slavery run: ${e.message}", e)
            activeSessions.values.forEach {
                it.log(
                    "Error in Slavery run: ${e.message}",
                    LogEntryType.ERROR
                )
            }
        } finally {
            timerJob.cancel()
            stop()
        }
    }

    private suspend fun runSlaveWorker(
        slotKey: String,
        slotConfig: SlaveSlotConfig,
        server: String,
        followPlayer: String,
        roomNumber: Int,
        targetsPriority: List<String>,
        whitelistItems: List<String>,
        lockedZones: List<String>,
        copyWalk: Boolean
    ) {
        val username = slotConfig.username.trim()
        val password = slotConfig.password.trim()
        val isTaunter = slotConfig.isTaunter
        val skills = slotConfig.skills.ifEmpty {
            listOf(
                Skill(index = 0),
                Skill(index = 1),
                Skill(index = 2),
                Skill(index = 0),
                Skill(index = 3),
                Skill(index = 4)
            )
        }

        val session = AqwSession()
        session.slotKey = slotKey
        session.socketClient.tag = "$slotKey ($username)"
        session.playerState.followedPlayer = followPlayer
        activeSessions[slotKey] = session

        var lastWalkedX = -1
        var lastWalkedY = -1

        val logJob = scope.launch(Dispatchers.IO) {
            session.logs.collect { sessionLogs ->
                _slotLogs.update { current ->
                    current + (slotKey to sessionLogs)
                }
            }
        }

        val cooldowns = ConcurrentHashMap<Int, Double>()
        for (i in 0..5) cooldowns[i] = 0.0

        var isCheckingLockedZone = false

        val eventJob = scope.launch(Dispatchers.IO) {
            session.events.collect { event ->
                when (event) {
                    is AqwEvent.Warning -> {
                        if (event.message.contains("locked zone", ignoreCase = true)) {
                            isCheckingLockedZone = true
                        }
                    }

                    is AqwEvent.PlayerStateUpdated -> {
                        if (copyWalk && event.username.equals(followPlayer, ignoreCase = true)) {
                            val targetX = event.tx ?: event.x
                            val targetY = event.ty ?: event.y
                            if (targetX != null && targetY != null && (targetX != lastWalkedX || targetY != lastWalkedY)) {
                                val master =
                                    session.playerState.playersInMap[followPlayer.lowercase()]
                                if (master != null && master.cell.equals(
                                        session.playerState.cell,
                                        ignoreCase = true
                                    )
                                ) {
                                    session.map.walkTo(targetX, targetY, event.sp ?: 8)
                                    lastWalkedX = targetX
                                    lastWalkedY = targetY
                                }
                            }
                        }
                    }

                    is AqwEvent.PartyInviteReceived -> {
                        session.log(
                            "Received party invitation (PID: ${event.partyId}), accepting...",
                            LogEntryType.INFO
                        )
                        session.social.partyAccept(event.partyId)
                    }

                    is AqwEvent.ItemDropped -> {
                        if (whitelistItems.any { event.itemName.lowercase().contains(it) }) {
                            session.log(
                                "Picking up: ${event.itemName} x${event.qty}",
                                LogEntryType.INFO
                            )
                            session.item.getItemDrop(event.itemId)
                        }
                    }

                    else -> {}
                }
            }
        }

        try {
            session.log("[$slotKey] Logging in to $server...", LogEntryType.INFO)
            val connected = session.start(
                username = username,
                password = password,
                preferredServer = server
            )

            if (!connected) {
                session.log("[$slotKey] Login failed.", LogEntryType.ERROR)
                updateTelemetry(slotKey, session, isRunning = false)
                return
            }

            var loadWait = 0
            while (!session.isCharLoaded.value && loadWait < 150 && !stopRequested) {
                delay(100.milliseconds)
                loadWait++
            }

            if (!session.isCharLoaded.value) {
                session.log("[$slotKey] Character load timed out.", LogEntryType.ERROR)
                return
            }

            // Equip farm class
            if (slotConfig.charClass.isNotBlank()) {
                val classItem = session.playerState.inventory.firstOrNull {
                    it.name.equals(slotConfig.charClass, ignoreCase = true)
                }
                if (classItem != null) {
                    session.item.equipItem(classItem.itemId)
                    delay(1000.milliseconds)
                }
            }

            // Prepare Scroll of Enrage if taunter
            var soeQty = 0
            if (isTaunter) {
                val soeItem = session.playerState.inventory.firstOrNull {
                    it.name.equals("Scroll of Enrage", ignoreCase = true)
                }
                soeQty = soeItem?.qty ?: 0
                if (soeQty > 0) {
                    session.item.equipScroll(soeItem!!.itemId, soeItem.sMeta)
                    tauntCoordinator.registerTaunter(username)
                    delay(1000.milliseconds)
                }
            }

            var skillIdx = 0

            // Main Slavery Loop
            while (scope.isActive && !stopRequested && session.isConnected.value) {
                if (_isPaused.value) {
                    delay(500.milliseconds)
                    continue
                }

                if (session.playerState.isDead) {
                    delay(500.milliseconds)
                    continue
                }

                val currentCell = session.playerState.cell

                val soeItemNow = session.playerState.inventory.firstOrNull {
                    it.name.equals("Scroll of Enrage", ignoreCase = true)
                }
                soeQty = soeItemNow?.qty ?: 0
                updateTelemetry(slotKey, session, isRunning = true, soeQty = soeQty)

                // Locked zone resolution
                if (isCheckingLockedZone) {
                    session.log("Checking locked zones for $followPlayer...", LogEntryType.INFO)
                    for (zoneMap in lockedZones) {
                        if (stopRequested || !session.isConnected.value) break
                        session.log("Checking $zoneMap-$roomNumber...", LogEntryType.INFO)
                        session.map.joinMap(zoneMap, roomNumber)
                        delay(2000.milliseconds)
                        session.map.gotoPlayer(followPlayer)
                        delay(1200.milliseconds)
                        if (session.playerState.playersInMap.containsKey(followPlayer.lowercase())) {
                            session.log("Found $followPlayer in $zoneMap!", LogEntryType.INFO)
                            isCheckingLockedZone = false
                            break
                        }
                    }
                    isCheckingLockedZone = false
                }

                // Check Master presence in cell
                val masterPlayer = session.playerState.playersInMap[followPlayer.lowercase()]
                if (masterPlayer != null && !masterPlayer.cell.equals(
                        currentCell,
                        ignoreCase = true
                    )
                ) {
                    session.map.jumpCell(masterPlayer.cell, masterPlayer.pad)
                    lastWalkedX = -1
                    lastWalkedY = -1
                    delay(800.milliseconds)
                    continue
                } else if (masterPlayer == null && !isCheckingLockedZone) {
                    // Try jumping to master
                    session.map.jumpCell(session.playerState.cell, session.playerState.pad)
                    lastWalkedX = -1
                    lastWalkedY = -1
                    delay(1000.milliseconds)
                    session.map.gotoPlayer(followPlayer)
                    delay(1500.milliseconds)
                }

                // Copy walk if Master moved within current cell
                if (copyWalk && masterPlayer != null && masterPlayer.cell.equals(
                        currentCell,
                        ignoreCase = true
                    )
                ) {
                    val targetX = masterPlayer.tx
                    val targetY = masterPlayer.ty
                    if ((targetX != 0 || targetY != 0) && (targetX != lastWalkedX || targetY != lastWalkedY)) {
                        session.map.walkTo(
                            targetX,
                            targetY,
                            if (masterPlayer.sp > 0) masterPlayer.sp else 8
                        )
                        lastWalkedX = targetX
                        lastWalkedY = targetY
                    }
                }

                val aliveMonsters = session.map.getMonsters()
                if (aliveMonsters.isNotEmpty()) {
                    // Pick target based on priority
                    val targetMonster = if (targetsPriority.isNotEmpty()) {
                        aliveMonsters.firstOrNull { mon ->
                            targetsPriority.any { p -> mon.name.lowercase().contains(p) }
                        } ?: aliveMonsters.first()
                    } else {
                        aliveMonsters.first()
                    }

                    // Check boss Counter Attack debuff
                    val hasCounterAttack =
                        targetMonster.auras.any {
                            it.name.contains(
                                "Counter Attack",
                                ignoreCase = true
                            ) && !it.isExpired()
                        }

                    // Process Skill
                    if (skillIdx >= skills.size) skillIdx = 0
                    val currentSkill = skills[skillIdx]

                    val p = session.playerState
                    val hpPct =
                        if (p.maxHp > 0) (p.currentHp.toDouble() / p.maxHp.toDouble()) * 100.0 else 100.0
                    val mpPct =
                        if (p.maxMp > 0) (p.mp.toDouble() / p.maxMp.toDouble()) * 100.0 else 100.0

                    var canUseThisSkill = true
                    if (currentSkill.thresholdType == ThresholdType.HP && currentSkill.thresholdValue > 0) {
                        canUseThisSkill =
                            if (currentSkill.operator == "<") hpPct < currentSkill.thresholdValue else hpPct > currentSkill.thresholdValue
                    } else if (currentSkill.thresholdType == ThresholdType.MP && currentSkill.thresholdValue > 0) {
                        canUseThisSkill =
                            if (currentSkill.operator == "<") mpPct < currentSkill.thresholdValue else mpPct > currentSkill.thresholdValue
                    }

                    if (canUseThisSkill) {
                        if (currentSkill.index == 5 && isTaunter) {
                            // Taunt Handling
                            val hasForbiddenAuras =
                                p.hasAura("Elegy of Madness") || p.hasAura("Seed Planted")
                            if (!hasForbiddenAuras && soeQty > 0 && tauntCoordinator.requestTaunt(
                                    username
                                )
                            ) {
                                session.combat.taunt(targetMonster.monMapId)
                            }
                        } else if (!hasCounterAttack) {
                            if (currentSkill.index == 0) {
                                session.combat.attack(targetMonster.monMapId)
                            } else {
                                session.combat.useSkill(
                                    currentSkill.index,
                                    targetMonster.monMapId
                                )
                            }
                        }
                    }

                    skillIdx = (skillIdx + 1) % skills.size
                }

                delay(220.milliseconds)
            }
        } catch (e: Exception) {
            session.log("Worker error: ${e.message}", LogEntryType.ERROR)
        } finally {
            logJob.cancel()
            eventJob.cancel()
            session.stop()
            activeSessions.remove(slotKey)
            if (isTaunter) tauntCoordinator.unregisterTaunter(username)
            updateTelemetry(slotKey, session, isRunning = false)
        }
    }

    private fun updateTelemetry(
        slotKey: String,
        session: AqwSession,
        isRunning: Boolean,
        soeQty: Int = 0
    ) {
        val p = session.playerState
        val cooldowns = session.getCooldowns()
        val cellMonsters = session.map.getCellMonsters(p.cell).map {
            MonsterTelemetry(
                monMapId = it.monMapId,
                monName = it.name,
                hp = it.currentHp,
                maxHp = it.maxHp,
                isAlive = it.isAlive,
                auras = it.auras.toList()
            )
        }

        _status.update { current ->
            val mutable = current.toMutableMap()
            mutable[slotKey] = SlotTelemetry(
                running = isRunning,
                isConnected = session.isConnected.value,
                map = p.mapName.ifBlank { "-" },
                cell = p.cell.ifBlank { "-" },
                pad = p.pad.ifBlank { "-" },
                hp = p.currentHp,
                maxHp = p.maxHp,
                mp = p.mp,
                maxMp = p.maxMp,
                isDead = p.isDead,
                isInCombat = p.isInCombat,
                cooldowns = cooldowns,
                soeQty = soeQty,
                monsters = cellMonsters,
                targetMonsters = p.cell,
                targetedMonster = session.lastTargetMonster,
                auras = p.auras.toList()
            )
            mutable
        }
    }
}
