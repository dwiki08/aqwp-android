package froztt13.python.aqw.core.eclipse

import android.util.Log
import froztt13.python.aqw.core.engine.AqwEvent
import froztt13.python.aqw.core.engine.AqwSession
import froztt13.python.aqw.data.EclipseConfig
import froztt13.python.aqw.data.EclipseTauntInfo
import froztt13.python.aqw.data.MonsterTelemetry
import froztt13.python.aqw.data.PartyStats
import froztt13.python.aqw.data.SlotConfig
import froztt13.python.aqw.data.SlotTelemetry
import froztt13.python.aqw.data.TaunterTargetInfo
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
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

object NativeEclipseBot {

    private const val TAG = "NativeEclipseBot"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var coordinatorJob: Job? = null
    private val activeSessions = ConcurrentHashMap<String, AqwSession>()
    private var currentConfig: EclipseConfig? = null

    private val _status = MutableStateFlow<Map<String, SlotTelemetry>>(emptyMap())
    val status: StateFlow<Map<String, SlotTelemetry>> = _status.asStateFlow()

    private val _stats = MutableStateFlow(PartyStats())
    val stats: StateFlow<PartyStats> = _stats.asStateFlow()

    private val _tauntInfo = MutableStateFlow(EclipseTauntInfo())
    val tauntInfo: StateFlow<EclipseTauntInfo> = _tauntInfo.asStateFlow()

    private var stopRequested = false
    private var startTimeMillis = 0L
    private var clearedRuns = 0

    internal val sunsetKnightCount = AtomicInteger(0)
    internal val moonHazeCount = AtomicInteger(0)
    internal val lightGatherCount = AtomicInteger(0)
    internal var lastSunsetKnightTime = 0L
    internal var lastMoonHazeTime = 0L
    internal var lastLightGatherTime = 0L
    internal var latestAnimMsg: String = ""
    internal var latestAnimMsgTime: Long = 0L
    private var animClearJob: Job? = null
    private val tauntLock = Any()
    internal val pendingTauntTargets = ConcurrentHashMap<String, String>()
    private var lightGatherTaunterSlots = listOf("slot2", "slot3", "slot4")

    internal fun refreshTauntInfo() {
        val nextSunSlot = if ((sunsetKnightCount.get() + 1) % 2 == 1) "slot1" else "slot2"
        val nextMoonSlot = if ((moonHazeCount.get() + 1) % 2 == 1) "slot3" else "slot4"
        val taunters = lightGatherTaunterSlots.ifEmpty { listOf("slot2", "slot3", "slot4") }
        val nextGatherSlot = taunters[lightGatherCount.get() % taunters.size]

        fun resolveUsername(slotKey: String): String {
            val sessionUser =
                activeSessions[slotKey]?.playerState?.username?.takeIf { it.isNotBlank() }
            if (sessionUser != null) return sessionUser
            val configUser =
                currentConfig?.slots?.get(slotKey)?.username?.takeIf { it.isNotBlank() }
            if (configUser != null) return configUser
            return when (slotKey) {
                "slot1" -> "P1"
                "slot2" -> "P2"
                "slot3" -> "P3"
                "slot4" -> "P4"
                else -> slotKey
            }
        }

        var pendingSunSlot: String? = null
        var pendingMoonSlot: String? = null
        var pendingGatherSlot: String? = null

        for ((slot, target) in pendingTauntTargets) {
            when (target) {
                "Sunset Knight" -> pendingSunSlot = slot
                "Moon Haze" -> pendingMoonSlot = slot
                "Suffocated Light" -> pendingGatherSlot = slot
            }
        }

        _tauntInfo.value = EclipseTauntInfo(
            sunSide = TaunterTargetInfo(
                nextSlot = nextSunSlot,
                nextUsername = resolveUsername(nextSunSlot),
                pendingSlot = pendingSunSlot,
                pendingUsername = pendingSunSlot?.let { resolveUsername(it) },
                waveCount = sunsetKnightCount.get()
            ),
            moonSide = TaunterTargetInfo(
                nextSlot = nextMoonSlot,
                nextUsername = resolveUsername(nextMoonSlot),
                pendingSlot = pendingMoonSlot,
                pendingUsername = pendingMoonSlot?.let { resolveUsername(it) },
                waveCount = moonHazeCount.get()
            ),
            lightGather = TaunterTargetInfo(
                nextSlot = nextGatherSlot,
                nextUsername = resolveUsername(nextGatherSlot),
                pendingSlot = pendingGatherSlot,
                pendingUsername = pendingGatherSlot?.let { resolveUsername(it) },
                waveCount = lightGatherCount.get()
            ),
            latestAnimMsg = latestAnimMsg,
            animMsgTimestamp = latestAnimMsgTime
        )
    }

    internal fun onSunWarmthDetected(sourceSlot: String, delayMs: Long = 5000L) {
        val targetSlot: String
        val waveCount: Int
        synchronized(tauntLock) {
            val now = System.currentTimeMillis()
            if (now - lastSunsetKnightTime <= 8000L) {
                return
            }
            lastSunsetKnightTime = now
            waveCount = sunsetKnightCount.incrementAndGet()
            targetSlot = if (waveCount % 2 == 1) "slot1" else "slot2"
        }

        refreshTauntInfo()

        val targetSession = activeSessions[targetSlot]
        val username = targetSession?.playerState?.username ?: targetSlot
        BotHelper.dispatchLog(
            "eclipse",
            "System",
            "Sun's Warmth #$waveCount (detected by $sourceSlot) -> Assigned taunt to $targetSlot ($username)"
        )
        queueTaunt(targetSlot, "Sunset Knight", delayMs)
    }

    internal fun onMoonGazeDetected(sourceSlot: String, delayMs: Long = 5000L) {
        val targetSlot: String
        val waveCount: Int
        synchronized(tauntLock) {
            val now = System.currentTimeMillis()
            if (now - lastMoonHazeTime <= 8000L) {
                return
            }
            lastMoonHazeTime = now
            waveCount = moonHazeCount.incrementAndGet()
            targetSlot = if (waveCount % 2 == 1) "slot3" else "slot4"
        }

        refreshTauntInfo()

        val targetSession = activeSessions[targetSlot]
        val username = targetSession?.playerState?.username ?: targetSlot
        BotHelper.dispatchLog(
            "eclipse",
            "System",
            "Moonlight Gaze #$waveCount (detected by $sourceSlot) -> Assigned taunt to $targetSlot ($username)"
        )
        queueTaunt(targetSlot, "Moon Haze", delayMs)
    }

    internal fun onLightGatherDetected(sourceSlot: String, delayMs: Long = 0L) {
        val targetSlot: String
        val waveCount: Int
        synchronized(tauntLock) {
            val now = System.currentTimeMillis()
            if (now - lastLightGatherTime <= 6000L) {
                return
            }
            lastLightGatherTime = now
            waveCount = lightGatherCount.incrementAndGet()
            val taunters = lightGatherTaunterSlots.ifEmpty { listOf("slot2", "slot3", "slot4") }
            targetSlot = taunters[(waveCount - 1) % taunters.size]
        }

        refreshTauntInfo()

        val targetSession = activeSessions[targetSlot]
        val username = targetSession?.playerState?.username ?: targetSlot
        BotHelper.dispatchLog(
            "eclipse",
            "System",
            "Light Gather #$waveCount (detected by $sourceSlot) -> Assigned taunt to $targetSlot ($username)"
        )
        // Immediate taunt (no 5s delay, as in core_eclipse.py)
        queueTaunt(targetSlot, "Suffocated Light", delayMs)
    }

    private fun queueTaunt(slotKey: String, targetMonster: String, delayMs: Long = 5000L) {
        scope.launch(Dispatchers.IO) {
            if (delayMs > 0) {
                delay(delayMs.milliseconds)
            }
            if (stopRequested) return@launch
            val session = activeSessions[slotKey]
            if (session != null && session.isConnected.value && session.hasAliveMonsters()) {
                val username = session.playerState.username
                BotHelper.dispatchLog(
                    "eclipse",
                    username,
                    "[$slotKey] Queuing taunt for $targetMonster..."
                )
                pendingTauntTargets[slotKey] = targetMonster
                refreshTauntInfo()
            }
        }
    }

    private suspend fun waitForSlavesInCell(targetCell: String, maxWaitMs: Long = 4000L) {
        val start = System.currentTimeMillis()
        val slaveKeys = listOf("slot2", "slot3", "slot4")
        while (!stopRequested && (System.currentTimeMillis() - start) < maxWaitMs) {
            val allArrived = slaveKeys.all { key ->
                val s = activeSessions[key]
                s == null || !s.isConnected.value || s.playerState.cell.equals(
                    targetCell,
                    ignoreCase = true
                )
            }
            if (allArrived) break
            delay(200.milliseconds)
        }
    }

    val isRunning: Boolean
        get() = _status.value.values.any { it.running }

    fun start(config: EclipseConfig): Pair<Boolean, String?> {
        if (isRunning) {
            return Pair(false, "Eclipse Shrine Party is already running!")
        }

        val slots = config.slots
        val slot1 = slots["slot1"]
        if (slot1 == null || slot1.username.isBlank() || slot1.password.isBlank()) {
            return Pair(false, "Master account (slot1) credentials must be filled.")
        }

        val filledSlots =
            slots.filter { it.value.username.isNotBlank() && it.value.password.isNotBlank() }
        if (filledSlots.size < 4) {
            return Pair(false, "Please configure credentials for all 4 slots.")
        }

        val configuredGather = if (config.lightGatherMode == "slot4_only") {
            listOf("slot4")
        } else {
            val fromSlots = config.slots.filter { it.value.lightGatherTaunter }.keys.sorted()
            if (fromSlots.isNotEmpty()) fromSlots else listOf("slot2", "slot3", "slot4")
        }
        lightGatherTaunterSlots = configuredGather

        stopRequested = false
        startTimeMillis = System.currentTimeMillis()
        clearedRuns = 0
        currentConfig = config
        sunsetKnightCount.set(0)
        moonHazeCount.set(0)
        lightGatherCount.set(0)
        lastSunsetKnightTime = 0L
        lastMoonHazeTime = 0L
        lastLightGatherTime = 0L
        latestAnimMsg = ""
        latestAnimMsgTime = 0L
        animClearJob?.cancel()
        animClearJob = null
        pendingTauntTargets.clear()
        refreshTauntInfo()

        val initialStatuses = mutableMapOf<String, SlotTelemetry>()
        for (key in listOf("slot1", "slot2", "slot3", "slot4")) {
            initialStatuses[key] = SlotTelemetry(
                running = true,
                isNextTaunter = key in listOf("slot1", "slot3")
            )
        }
        _status.value = initialStatuses
        _stats.value = PartyStats(timeRunning = 0L, clearedCount = 0)

        coordinatorJob?.cancel()
        coordinatorJob = scope.launch(Dispatchers.IO) {
            runParty(config)
        }

        return Pair(true, null)
    }

    fun stop() {
        stopRequested = true
        for ((_, session) in activeSessions) {
            try {
                session.stop()
            } catch (_: Exception) {
            }
        }
        activeSessions.clear()
        pendingTauntTargets.clear()
        latestAnimMsg = ""
        latestAnimMsgTime = 0L
        animClearJob?.cancel()
        animClearJob = null
        refreshTauntInfo()
        coordinatorJob?.cancel()

        _status.update { current ->
            current.mapValues { (_, tele) -> tele.copy(running = false, isConnected = false) }
        }
        _stats.update { it.copy(timeRunning = (System.currentTimeMillis() - startTimeMillis) / 1000L) }
        BotHelper.dispatchLog("eclipse", "System", "=== Eclipse Shrine Party stopped by user ===")
    }

    private suspend fun runParty(config: EclipseConfig) {
        val server = config.server.ifBlank { "Alteon" }
        val dungeonMap = "ascendeclipse"
        val dropWhitelist = setOf(
            "Eclipse General",
            "Fragment of the Sun",
            "Fragment of the Moon",
            "Soleil",
            "Lunette",
            "Midnight Sun",
            "Solstice Moon",
            "Ascended Fragment",
            "Fragment of Midnight",
            "Fragment of Sunlight",
            "Ecliptic Offering"
        )

        val masterSlot = config.slots["slot1"] ?: return
        val masterUsername = masterSlot.username.trim()

        val slaveSlots = listOfNotNull(
            config.slots["slot2"]?.takeIf { it.username.isNotBlank() },
            config.slots["slot3"]?.takeIf { it.username.isNotBlank() },
            config.slots["slot4"]?.takeIf { it.username.isNotBlank() }
        )
        val slaveUsernames = slaveSlots.map { it.username.trim() }

        val slotJobs = mutableListOf<Job>()

        val statsTimerJob = scope.launch(Dispatchers.IO) {
            while (isActive && !stopRequested) {
                val elapsed = (System.currentTimeMillis() - startTimeMillis) / 1000L
                _stats.update { it.copy(timeRunning = elapsed, clearedCount = clearedRuns) }
                delay(1000.milliseconds)
            }
        }

        try {
            for (slotKey in listOf("slot1", "slot2", "slot3", "slot4")) {
                val slotConf = config.slots[slotKey] ?: continue
                val isMaster = (slotKey == "slot1")
                val defaultTarget = when (slotKey) {
                    "slot1" -> slotConf.defaultTarget.ifBlank { "Ascended Solstice,Blessless Deer" }
                    "slot2" -> slotConf.defaultTarget.ifBlank { "Ascended Solstice" }
                    "slot3" -> slotConf.defaultTarget.ifBlank { "Ascended Midnight" }
                    "slot4" -> slotConf.defaultTarget.ifBlank { "Ascended Midnight" }
                    else -> "Ascended Solstice"
                }

                val job = scope.launch(Dispatchers.IO) {
                    runSlotWorker(
                        slotKey = slotKey,
                        slotConfig = slotConf,
                        isMaster = isMaster,
                        server = server,
                        dungeonMap = dungeonMap,
                        masterUsername = masterUsername,
                        slaveUsernames = slaveUsernames,
                        defaultTarget = defaultTarget,
                        dropWhitelist = dropWhitelist
                    )
                }
                slotJobs.add(job)
            }

            slotJobs.joinAll()
        } catch (e: Exception) {
            Log.e(TAG, "Error in Eclipse Party run: ${e.message}", e)
            BotHelper.dispatchLog("eclipse", "System", "Error in Eclipse Party run: ${e.message}")
        } finally {
            statsTimerJob.cancel()
            stop()
        }
    }

    private suspend fun runSlotWorker(
        slotKey: String,
        slotConfig: SlotConfig,
        isMaster: Boolean,
        server: String,
        dungeonMap: String,
        masterUsername: String,
        slaveUsernames: List<String>,
        defaultTarget: String,
        dropWhitelist: Set<String>
    ) {
        val username = slotConfig.username.trim()
        val password = slotConfig.password.trim()

        val session = AqwSession()
        session.socketClient.tag = "$slotKey ($username)"
        activeSessions[slotKey] = session

        val cooldowns = ConcurrentHashMap<Int, Double>()
        for (i in 0..5) cooldowns[i] = 0.0

        var doTaunt = false
        var tauntTarget: String? = null
        var isAttacking = false

        val eventJob = scope.launch(Dispatchers.IO) {
            session.events.collect { event ->
                when (event) {
                    is AqwEvent.CombatTick -> {
                        var hasSunWarmth = false
                        var hasMoonGaze = false

                        for ((auraName, _) in event.auras) {
                            if (auraName.equals("Sun's Warmth", ignoreCase = true)) {
                                hasSunWarmth = true
                            } else if (auraName.equals("Moonlight Gaze", ignoreCase = true)) {
                                hasMoonGaze = true
                            }
                        }

                        if (event.animMsgs.isNotEmpty()) {
                            val combined = event.animMsgs.joinToString(" | ")
                            latestAnimMsg = combined
                            latestAnimMsgTime = System.currentTimeMillis()
                            refreshTauntInfo()

                            animClearJob?.cancel()
                            animClearJob = scope.launch(Dispatchers.IO) {
                                delay(4000.milliseconds)
                                synchronized(tauntLock) {
                                    if (System.currentTimeMillis() - latestAnimMsgTime >= 3900L) {
                                        latestAnimMsg = ""
                                        latestAnimMsgTime = 0L
                                        refreshTauntInfo()
                                    }
                                }
                            }
                        }

                        for (msg in event.animMsgs) {
                            val lower = msg.lowercase()
                            if (lower.contains("gather")) {
                                onLightGatherDetected(slotKey)
                            } else if (lower.contains("warmth") || lower.contains("sunset")) {
                                hasSunWarmth = true
                            } else if (lower.contains("gaze") || lower.contains("moon haze")) {
                                hasMoonGaze = true
                            }
                        }

                        if (hasSunWarmth) {
                            onSunWarmthDetected(slotKey)
                        }
                        if (hasMoonGaze) {
                            onMoonGazeDetected(slotKey)
                        }
                    }

                    is AqwEvent.ItemDropped -> {
                        if (dropWhitelist.any { it.equals(event.itemName, ignoreCase = true) }) {
                            BotHelper.dispatchLog(
                                "eclipse",
                                username,
                                "Picking up drop: ${event.itemName} x${event.qty}"
                            )
                            session.commands.getItemDrop(event.itemId)
                        }
                    }

                    else -> {}
                }
            }
        }

        try {
            BotHelper.dispatchLog("eclipse", username, "[$slotKey] Logging in to $server...")
            val connected = session.start(
                username = username,
                password = password,
                preferredServer = server,
                onLog = { msg -> BotHelper.dispatchLog("eclipse", username, msg) }
            )

            if (!connected) {
                BotHelper.dispatchLog("eclipse", username, "[$slotKey] Failed to connect / login.")
                updateTelemetry(slotKey, session, defaultTarget, isRunning = false)
                return
            }

            var loadWait = 0
            while (!session.isCharLoaded.value && loadWait < 150 && !stopRequested) {
                delay(100.milliseconds)
                loadWait++
            }

            if (!session.isCharLoaded.value) {
                BotHelper.dispatchLog("eclipse", username, "[$slotKey] Character load timed out.")
                return
            }

            // Equip farm class if specified
            if (slotConfig.charClass.isNotBlank()) {
                val classItem = session.playerState.inventory.firstOrNull {
                    it.name.equals(slotConfig.charClass, ignoreCase = true)
                }
                if (classItem != null) {
                    session.commands.equipItem(classItem.itemId)
                    delay(1000.milliseconds)
                }
            }

            // Equip Scroll of Enrage (all 4 slots taunt in Eclipse)
            val soeItem = session.playerState.inventory.firstOrNull {
                it.name.equals("Scroll of Enrage", ignoreCase = true)
            }
            var soeQty = soeItem?.qty ?: 0
            if (soeQty <= 0) {
                val err =
                    "Account '$username' ($slotKey) does not have Scroll of Enrage (SoE). Minimum 1 is required."
                BotHelper.dispatchLog("eclipse", username, err)
                stop()
                return
            }
            BotHelper.dispatchLog(
                "eclipse",
                username,
                "Equipping Scroll of Enrage (Qty: $soeQty)..."
            )
            session.commands.equipScroll(soeItem!!.itemId, soeItem.sMeta)
            delay(1500.milliseconds)

            if (isMaster) {
                BotHelper.dispatchLog(
                    "eclipse",
                    username,
                    "Master joining yulgar-999999 to assemble party..."
                )
                session.commands.joinMap("yulgar", 999999)
                delay(3500.milliseconds)

                BotHelper.dispatchLog(
                    "eclipse",
                    username,
                    "Waiting for party members to be online..."
                )
                var partyWait = 0
                while (activeSessions.size < 4 && partyWait < 60 && !stopRequested) {
                    delay(500.milliseconds)
                    partyWait++
                }

                for (slaveName in slaveUsernames) {
                    BotHelper.dispatchLog(
                        "eclipse",
                        username,
                        "Sending party invite to $slaveName..."
                    )
                    session.commands.partyInvite(slaveName)
                    delay(600.milliseconds)
                }

                delay(1000.milliseconds)
                BotHelper.dispatchLog("eclipse", username, "Queueing dungeon '$dungeonMap'...")
                session.commands.dungeonQueue(dungeonMap)
            } else {
                BotHelper.dispatchLog("eclipse", username, "Slave waiting for party invitation...")
                var inviteWait = 0
                while (session.latestPartyId == null && inviteWait < 120 && !stopRequested) {
                    session.commands.gotoPlayer(masterUsername)
                    delay(1000.milliseconds)
                    inviteWait++
                }

                val pid = session.latestPartyId
                if (pid != null) {
                    BotHelper.dispatchLog(
                        "eclipse",
                        username,
                        "Accepting party invite (PID: $pid)..."
                    )
                    session.commands.partyAccept(pid)
                    delay(1200.milliseconds)
                }
            }

            val skillRotation = listOf(0, 1, 2, 0, 3, 4)
            var skillIdx = 0

            while (scope.isActive && !stopRequested && session.isConnected.value) {
                if (session.playerState.isDead) {
                    delay(500.milliseconds)
                    continue
                }

                val currentCell = session.playerState.cell
                val currentMap = session.playerState.mapName

                if (!currentCell.equals("r1", ignoreCase = true) &&
                    tauntTarget?.equals("Suffocated Light", ignoreCase = true) == true
                ) {
                    doTaunt = false
                    tauntTarget = null
                }

                val queuedTaunt = pendingTauntTargets.remove(slotKey)
                if (queuedTaunt != null) {
                    doTaunt = true
                    tauntTarget = queuedTaunt
                    refreshTauntInfo()
                }

                val soeItemNow = session.playerState.inventory.firstOrNull {
                    it.name.equals("Scroll of Enrage", ignoreCase = true)
                }
                soeQty = soeItemNow?.qty ?: 0
                val activeTargetDisplay = tauntTarget ?: defaultTarget
                updateTelemetry(
                    slotKey,
                    session,
                    activeTargetDisplay,
                    isRunning = true,
                    soeQty = soeQty
                )

                if (isMaster) {
                    val hasMonsters = session.hasAliveMonsters(currentCell)
                    if (!hasMonsters && currentMap.contains(dungeonMap, ignoreCase = true)) {
                        when (currentCell) {
                            "Enter" -> {
                                lightGatherCount.set(0)
                                lastLightGatherTime = 0L
                                pendingTauntTargets.clear()
                                refreshTauntInfo()
                                BotHelper.dispatchLog(
                                    "eclipse",
                                    username,
                                    "Enter cleared. Moving to r1..."
                                )
                                session.commands.jumpCell("r1", "Left")
                                delay(1200.milliseconds)
                                waitForSlavesInCell("r1")
                            }

                            "r1" -> {
                                lightGatherCount.set(0)
                                lastLightGatherTime = 0L
                                pendingTauntTargets.clear()
                                refreshTauntInfo()
                                BotHelper.dispatchLog(
                                    "eclipse",
                                    username,
                                    "r1 cleared. Moving to r2..."
                                )
                                session.commands.jumpCell("r2", "Left")
                                delay(1200.milliseconds)
                                waitForSlavesInCell("r2")
                            }

                            "r2" -> {
                                sunsetKnightCount.set(0)
                                moonHazeCount.set(0)
                                lightGatherCount.set(0)
                                lastSunsetKnightTime = 0L
                                lastMoonHazeTime = 0L
                                lastLightGatherTime = 0L
                                pendingTauntTargets.clear()
                                refreshTauntInfo()
                                BotHelper.dispatchLog(
                                    "eclipse",
                                    username,
                                    "r2 cleared. Moving to r3 (Boss)..."
                                )
                                session.commands.jumpCell("r3", "Left")
                                delay(1200.milliseconds)
                                waitForSlavesInCell("r3")
                            }

                            "r3" -> {
                                clearedRuns++
                                sunsetKnightCount.set(0)
                                moonHazeCount.set(0)
                                lightGatherCount.set(0)
                                lastSunsetKnightTime = 0L
                                lastMoonHazeTime = 0L
                                lastLightGatherTime = 0L
                                pendingTauntTargets.clear()
                                refreshTauntInfo()
                                BotHelper.dispatchLog(
                                    "eclipse",
                                    username,
                                    "=== Ascend Eclipse cleared $clearedRuns times! ==="
                                )
                                session.commands.sendChat("Ascend Eclipse cleared $clearedRuns times.")
                                delay(1000.milliseconds)
                                session.commands.joinMap("templeshrine", 999999)
                                delay(2500.milliseconds)
                                session.commands.dungeonQueue(dungeonMap)
                                delay(2000.milliseconds)
                            }
                        }
                    }
                } else {
                    val masterSession = activeSessions["slot1"]
                    val masterCell = masterSession?.playerState?.cell
                    val masterMap = masterSession?.playerState?.mapName ?: ""
                    val isDifferentMap =
                        masterMap.isNotBlank() && !currentMap.equals(masterMap, ignoreCase = true)
                    val isDifferentCell =
                        masterCell != null && !masterCell.equals(currentCell, ignoreCase = true)

                    if (isDifferentMap || isDifferentCell) {
                        BotHelper.dispatchLog(
                            "eclipse",
                            username,
                            "[$slotKey] Master is in $masterMap:$masterCell (current: $currentMap:$currentCell). Moving to master..."
                        )
                        val inEclipseMap = currentMap.contains("eclipse", ignoreCase = true) ||
                                masterMap.contains("eclipse", ignoreCase = true) ||
                                currentMap.contains(dungeonMap, ignoreCase = true) ||
                                masterMap.contains(dungeonMap, ignoreCase = true)

                        if (inEclipseMap && masterCell != null) {
                            // Di map ascendedeclipse tidak perlu goto, cukup gunakan jumpCell biasa
                            val masterPad =
                                masterSession?.playerState?.pad?.ifBlank { "Left" } ?: "Left"
                            session.commands.jumpCell(masterCell, masterPad)
                        } else if (!isDifferentMap && masterCell != null) {
                            val masterPad =
                                masterSession?.playerState?.pad?.ifBlank { "Left" } ?: "Left"
                            session.commands.jumpCell(masterCell, masterPad)
                        } else {
                            session.commands.gotoPlayer(masterUsername)
                        }
                        delay(1200.milliseconds)
                        continue
                    }
                }

                // Attack monsters in current cell
                val aliveMonsters =
                    session.getCellMonsters(currentCell).filter { it.isAlive && it.currentHp > 0 }
                if (aliveMonsters.isNotEmpty()) {
                    if (!isAttacking) {
                        isAttacking = true
                    }

                    // Check Solar Flare debuff
                    val hasSolarFlare = session.playerState.auras.any {
                        it.equals(
                            "Solar Flare",
                            ignoreCase = true
                        )
                    }
                    val currentPrioritized = if (hasSolarFlare) {
                        listOf("blessless deer")
                    } else {
                        (tauntTarget ?: defaultTarget).split(",").map { it.trim().lowercase() }
                    }

                    val targetMonster = aliveMonsters.firstOrNull { mon ->
                        currentPrioritized.any { p -> mon.name.lowercase().contains(p) }
                    } ?: aliveMonsters.first()

                    // Taunt handling
                    if (doTaunt) {
                        if (soeQty <= 0) {
                            BotHelper.dispatchLog(
                                "eclipse",
                                username,
                                "Ran out of Scroll of Enrage (SoE)!"
                            )
                            stop()
                            break
                        }

                        val targetToTaunt = tauntTarget?.let { tt ->
                            aliveMonsters.firstOrNull {
                                it.name.lowercase().contains(tt.lowercase())
                            }
                        } ?: targetMonster

                        if (session.commands.canUseSkill(5)) {
                            BotHelper.dispatchLog(
                                "eclipse",
                                username,
                                "Executing Taunt on ${targetToTaunt.name}!"
                            )
                            delay(500.milliseconds)
                            val sent = session.commands.taunt(targetToTaunt.monMapId)
                            if (sent) {
                                val isSuffocatedLightAlive = aliveMonsters.any {
                                    it.name.contains(
                                        "Suffocated Light",
                                        ignoreCase = true
                                    ) && it.isAlive && it.currentHp > 0
                                }
                                if (targetToTaunt.name.contains(
                                        "Suffocated Light",
                                        ignoreCase = true
                                    ) && isSuffocatedLightAlive
                                ) {
                                    // Loop taunt: keep doTaunt = true and tauntTarget as is (as in core_eclipse.py)
                                    doTaunt = true
                                    tauntTarget = "Suffocated Light"
                                } else {
                                    doTaunt = false
                                    tauntTarget = null
                                }
                                delay(400.milliseconds)
                                continue
                            }
                        } else if (targetToTaunt.name.contains(
                                "Suffocated Light",
                                ignoreCase = true
                            )
                        ) {
                            val isSuffocatedLightAlive = aliveMonsters.any {
                                it.name.contains(
                                    "Suffocated Light",
                                    ignoreCase = true
                                ) && it.isAlive && it.currentHp > 0
                            }
                            if (!isSuffocatedLightAlive) {
                                doTaunt = false
                                tauntTarget = null
                            }
                        }
                    }

                    val nextSkill = skillRotation[skillIdx]
                    skillIdx = (skillIdx + 1) % skillRotation.size

                    val hasSunsHeat =
                        session.playerState.auras.any { it.equals("Sun's Heat", ignoreCase = true) }
                    if (hasSunsHeat && (nextSkill == 2 || nextSkill == 3)) {
                        session.commands.attack(targetMonster.monMapId)
                    } else if (nextSkill == 0) {
                        session.commands.attack(targetMonster.monMapId)
                    } else {
                        session.commands.useSkill(nextSkill, targetMonster.monMapId)
                    }
                } else {
                    isAttacking = false
                }

                delay(500.milliseconds)
            }
        } catch (e: Exception) {
            BotHelper.dispatchLog("eclipse", username, "Worker error: ${e.message}")
        } finally {
            eventJob.cancel()
            session.stop()
            activeSessions.remove(slotKey)
            updateTelemetry(slotKey, session, defaultTarget, isRunning = false)
        }
    }

    private fun updateTelemetry(
        slotKey: String,
        session: AqwSession,
        targetMonsters: String,
        isRunning: Boolean,
        soeQty: Int = 0
    ) {
        val p = session.playerState
        val cooldowns = session.getCooldowns()
        val cellMonsters = session.getCellMonsters(p.cell).map {
            MonsterTelemetry(
                monMapId = it.monMapId,
                monName = it.name,
                hp = it.currentHp,
                maxHp = it.maxHp,
                isAlive = it.isAlive
            )
        }

        val nextSun = if ((sunsetKnightCount.get() + 1) % 2 == 1) "slot1" else "slot2"
        val nextMoon = if ((moonHazeCount.get() + 1) % 2 == 1) "slot3" else "slot4"
        val isNext = when (slotKey) {
            "slot1", "slot2" -> slotKey == nextSun
            "slot3", "slot4" -> slotKey == nextMoon
            else -> false
        }
        val isPending = pendingTauntTargets.containsKey(slotKey)

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
                isNextTaunter = isNext,
                isPendingTaunt = isPending,
                cooldowns = cooldowns,
                soeQty = soeQty,
                monsters = cellMonsters,
                targetMonsters = targetMonsters,
                targetedMonster = session.lastTargetMonster,
                auras = p.auras.toList()
            )
            mutable
        }
    }
}
