package froztt13.python.aqw.domain.bot.eclipse

import android.util.Log
import froztt13.python.aqw.data.engine.AqwEvent
import froztt13.python.aqw.data.engine.AqwSession
import froztt13.python.aqw.data.model.EclipseConfig
import froztt13.python.aqw.data.model.EclipseTauntInfo
import froztt13.python.aqw.data.model.LogEntryType
import froztt13.python.aqw.data.model.MonsterTelemetry
import froztt13.python.aqw.data.model.PartyStats
import froztt13.python.aqw.data.model.SlotConfig
import froztt13.python.aqw.data.model.SlotTelemetry
import froztt13.python.aqw.data.model.TaunterTargetInfo
import froztt13.python.aqw.domain.coordinator.BasePartyCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

object NativeEclipseBot : BasePartyCoordinator("NativeEclipseBot") {

    @Suppress("PropertyName")
    val Config = NativeEclipseConfig

    private const val TAG = "NativeEclipseBot"

    // Internal Combat Logic Constants
    private const val MAP_DUNGEON = "ascendeclipse"
    private const val MAP_ASSEMBLY = "yulgar"
    private const val MAP_RESET = "templeshrine"
    private const val CELL_ENTER = "Enter"
    private const val CELL_R1 = "r1"
    private const val CELL_R2 = "r2"
    private const val CELL_R3 = "r3"
    private const val PAD_LEFT = "Left"
    private const val PAD_SPAWN = "Spawn"

    private const val ITEM_SCROLL_OF_ENRAGE = "Scroll of Enrage"
    private val DROP_WHITELIST =
        setOf("Sliver of Moonlight", "Sliver of Sunlight", "Ecliptic Offering")
    private val DEFAULT_SKILL_ROTATION = listOf(3, 0, 1, 2, 0, 3, 4)

    private const val MONSTER_SUNSET_KNIGHT = "Sunset Knight"
    private const val MONSTER_MOON_HAZE = "Moon Haze"
    private const val MONSTER_SUFFOCATED_LIGHT = "Suffocated Light"
    private const val MONSTER_ASCENDED_SOLSTICE = "Ascended Solstice"
    private const val MONSTER_ASCENDED_MIDNIGHT = "Ascended Midnight"
    private const val MONSTER_BLESSLESS_DEER = "Blessless Deer"

    private const val AURA_SUNS_WARMTH = "Sun's Warmth"
    private const val AURA_MOONLIGHT_GAZE = "Moonlight Gaze"
    private const val AURA_SOLAR_FLARE = "Solar Flare"
    private const val AURA_SUNS_HEAT = "Sun's Heat"

    private const val ANIM_MSG_CLEAR_DELAY_MS = 4000L
    private const val ANIM_MSG_EXPIRY_THRESHOLD_MS = 3900L

    private var currentConfig: EclipseConfig? = null

    private val _tauntInfo = MutableStateFlow(EclipseTauntInfo())
    val tauntInfo: StateFlow<EclipseTauntInfo> = _tauntInfo.asStateFlow()

    internal val sunsetKnightCount = AtomicInteger(0)
    internal val moonHazeCount = AtomicInteger(0)
    internal val lightGatherCount = AtomicInteger(0)
    internal val sunConvergeCount = AtomicInteger(0)
    internal val moonConvergeCount = AtomicInteger(0)
    internal var lastSunsetKnightTime = 0L
    internal var lastMoonHazeTime = 0L
    internal var lastLightGatherTime = 0L
    internal var lastSunConvergeTime = 0L
    internal var lastMoonConvergeTime = 0L
    internal var latestAnimMsg: String = ""
    internal var latestAnimMsgTime: Long = 0L
    private var animClearJob: Job? = null
    private val tauntLock = Any()
    internal val pendingTauntTargets = ConcurrentHashMap<String, String>()
    private var lightGatherTaunterSlots = NativeEclipseConfig.DEFAULT_LIGHT_GATHER_SLOTS

    private fun resolveAlternatingSlot(count: Int, oddSlot: String, evenSlot: String): String =
        if ((count + 1) % 2 == 1) oddSlot else evenSlot

    private fun resolveLightGatherSlots(config: EclipseConfig): List<String> {
        val fromSlots = config.slots
            .filter { it.key != NativeEclipseConfig.MASTER_SLOT_KEY && it.value.lightGatherTaunter }
            .keys
            .sorted()
        return fromSlots.ifEmpty { NativeEclipseConfig.DEFAULT_LIGHT_GATHER_SLOTS }
    }

    private fun resetTauntState(resetAll: Boolean = true, clearAnimMsg: Boolean = false) {
        if (resetAll) {
            sunsetKnightCount.set(0)
            moonHazeCount.set(0)
            lastSunsetKnightTime = 0L
            lastMoonHazeTime = 0L
        }
        lightGatherCount.set(0)
        sunConvergeCount.set(0)
        moonConvergeCount.set(0)
        lastLightGatherTime = 0L
        lastSunConvergeTime = 0L
        lastMoonConvergeTime = 0L
        pendingTauntTargets.clear()
        if (clearAnimMsg) {
            latestAnimMsg = ""
            latestAnimMsgTime = 0L
            animClearJob?.cancel()
            animClearJob = null
        }
        refreshTauntInfo()
    }

    internal fun refreshTauntInfo() {
        val nextSunSlot = resolveAlternatingSlot(sunsetKnightCount.get(), "slot1", "slot2")
        val nextMoonSlot = resolveAlternatingSlot(moonHazeCount.get(), "slot3", "slot4")
        val taunters =
            lightGatherTaunterSlots.ifEmpty { NativeEclipseConfig.DEFAULT_LIGHT_GATHER_SLOTS }
        val nextGatherSlot = taunters[lightGatherCount.get() % taunters.size]
        val nextSunConvergeSlot = resolveAlternatingSlot(sunConvergeCount.get(), "slot1", "slot2")
        val nextMoonConvergeSlot = resolveAlternatingSlot(moonConvergeCount.get(), "slot3", "slot4")

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

        fun findPendingSlot(targetMonster: String): String? =
            pendingTauntTargets.entries.firstOrNull {
                it.value.equals(
                    targetMonster,
                    ignoreCase = true
                )
            }?.key

        fun createTaunterInfo(
            nextSlot: String,
            targetMonster: String,
            waveCount: Int
        ): TaunterTargetInfo {
            val pendingSlot = findPendingSlot(targetMonster)
            return TaunterTargetInfo(
                nextSlot = nextSlot,
                nextUsername = resolveUsername(nextSlot),
                pendingSlot = pendingSlot,
                pendingUsername = pendingSlot?.let { resolveUsername(it) },
                waveCount = waveCount
            )
        }

        _tauntInfo.value = EclipseTauntInfo(
            sunSide = createTaunterInfo(
                nextSunSlot,
                MONSTER_SUNSET_KNIGHT,
                sunsetKnightCount.get()
            ),
            moonSide = createTaunterInfo(nextMoonSlot, MONSTER_MOON_HAZE, moonHazeCount.get()),
            lightGather = createTaunterInfo(
                nextGatherSlot,
                MONSTER_SUFFOCATED_LIGHT,
                lightGatherCount.get()
            ),
            sunConverge = createTaunterInfo(
                nextSunConvergeSlot,
                MONSTER_ASCENDED_SOLSTICE,
                sunConvergeCount.get()
            ),
            moonConverge = createTaunterInfo(
                nextMoonConvergeSlot,
                MONSTER_ASCENDED_MIDNIGHT,
                moonConvergeCount.get()
            ),
            latestAnimMsg = latestAnimMsg,
            animMsgTimestamp = latestAnimMsgTime
        )
    }

    private fun handleTauntDetection(
        sourceSlot: String,
        monsterName: String,
        delayMs: Long,
        eventName: String,
        lastTimeGetter: () -> Long,
        lastTimeSetter: (Long) -> Unit,
        counter: AtomicInteger,
        targetSlotResolver: (Int) -> String
    ) {
        val targetSlot: String
        val waveCount: Int
        synchronized(tauntLock) {
            val now = System.currentTimeMillis()
            if (now - lastTimeGetter() <= 5000L) {
                return
            }
            lastTimeSetter(now)
            waveCount = counter.incrementAndGet()
            targetSlot = targetSlotResolver(waveCount)
        }

        refreshTauntInfo()

        val targetSession = activeSessions[targetSlot]
        val username = targetSession?.playerState?.username ?: targetSlot
        targetSession?.log(
            "$eventName #$waveCount (from $sourceSlot) -> Assigned taunt to $targetSlot ($username)",
            LogEntryType.INFO
        )
        queueTaunt(targetSlot, monsterName, delayMs)
    }

    internal fun onSunWarmthDetected(sourceSlot: String) {
        handleTauntDetection(
            sourceSlot = sourceSlot,
            monsterName = MONSTER_SUNSET_KNIGHT,
            delayMs = 4000L,
            eventName = "Sun's Warmth",
            lastTimeGetter = { lastSunsetKnightTime },
            lastTimeSetter = { lastSunsetKnightTime = it },
            counter = sunsetKnightCount,
            targetSlotResolver = { if (it % 2 == 1) "slot1" else "slot2" }
        )
    }

    internal fun onMoonGazeDetected(sourceSlot: String) {
        handleTauntDetection(
            sourceSlot = sourceSlot,
            monsterName = MONSTER_MOON_HAZE,
            delayMs = 4000L,
            eventName = "Moonlight Gaze",
            lastTimeGetter = { lastMoonHazeTime },
            lastTimeSetter = { lastMoonHazeTime = it },
            counter = moonHazeCount,
            targetSlotResolver = { if (it % 2 == 1) "slot3" else "slot4" }
        )
    }

    internal fun onLightGatherDetected(sourceSlot: String) {
        handleTauntDetection(
            sourceSlot = sourceSlot,
            monsterName = MONSTER_SUFFOCATED_LIGHT,
            delayMs = 0L,
            eventName = "Light Gather",
            lastTimeGetter = { lastLightGatherTime },
            lastTimeSetter = { lastLightGatherTime = it },
            counter = lightGatherCount,
            targetSlotResolver = { count ->
                val taunters =
                    lightGatherTaunterSlots.ifEmpty { NativeEclipseConfig.DEFAULT_LIGHT_GATHER_SLOTS }
                taunters[(count - 1) % taunters.size]
            }
        )
    }

    internal fun onSunConvergeDetected(sourceSlot: String) {
        handleTauntDetection(
            sourceSlot = sourceSlot,
            monsterName = MONSTER_ASCENDED_SOLSTICE,
            delayMs = 0L,
            eventName = "Sun Converge",
            lastTimeGetter = { lastSunConvergeTime },
            lastTimeSetter = { lastSunConvergeTime = it },
            counter = sunConvergeCount,
            targetSlotResolver = { if (it % 2 == 1) "slot1" else "slot2" }
        )
    }

    internal fun onMoonConvergeDetected(sourceSlot: String) {
        handleTauntDetection(
            sourceSlot = sourceSlot,
            monsterName = MONSTER_ASCENDED_MIDNIGHT,
            delayMs = 0L,
            eventName = "Moon Converge",
            lastTimeGetter = { lastMoonConvergeTime },
            lastTimeSetter = { lastMoonConvergeTime = it },
            counter = moonConvergeCount,
            targetSlotResolver = { if (it % 2 == 1) "slot3" else "slot4" }
        )
    }

    private fun queueTaunt(
        slotKey: String,
        targetMonster: String,
        delayMs: Long
    ) {
        scope.launch(Dispatchers.IO) {
            if (delayMs > 0) {
                delay(delayMs.milliseconds)
            }
            if (stopRequested) return@launch
            val session = activeSessions[slotKey]
            if (session != null && session.isConnected.value && session.map.hasAliveMonsters()) {
                session.log(
                    "[$slotKey] Queuing taunt for $targetMonster...",
                    LogEntryType.INFO
                )
                pendingTauntTargets[slotKey] = targetMonster
                refreshTauntInfo()
            }
        }
    }

    private suspend fun waitForSlavesInCell(targetCell: String, maxWaitMs: Long = 4000L) {
        val start = System.currentTimeMillis()
        val slaveKeys = NativeEclipseConfig.SLAVE_SLOT_KEYS
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

    fun pause() {
        if (!isRunning || _isPaused.value) return
        _isPaused.value = true
        pausedAtMillis = System.currentTimeMillis()
        pendingTauntTargets.clear()
        latestAnimMsg = ""
        latestAnimMsgTime = 0L
        refreshTauntInfo()

        scope.launch(Dispatchers.IO) {
            logToAllSessions(
                "Pausing Eclipse Party: all slots leaving combat and jumping to current cell...",
                LogEntryType.WARNING
            )
            val jobs = activeSessions.map { (slotKey, session) ->
                launch {
                    try {
                        val currentCell = session.playerState.cell.ifBlank { CELL_ENTER }
                        val currentPad = session.playerState.pad.ifBlank { PAD_SPAWN }
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
                    targetMonsters = "PAUSED",
                    isRunning = true,
                )
            }
            logToAllSessions("=== Eclipse Shrine Party PAUSED ===")
        }
    }

    fun resume() {
        if (!isRunning || !_isPaused.value) return
        if (pausedAtMillis > 0) {
            startTimeMillis += (System.currentTimeMillis() - pausedAtMillis)
            pausedAtMillis = 0L
        }
        _isPaused.value = false
        for ((slotKey, session) in activeSessions) {
            session.quest.triggerAutoQuestCheck()
            val slotConf = currentConfig?.slots?.get(slotKey)
            val defaultTarget =
                NativeEclipseConfig.getDefaultTarget(slotKey, slotConf?.defaultTarget)
            updateTelemetry(
                slotKey = slotKey,
                session = session,
                targetMonsters = defaultTarget,
                isRunning = true
            )
        }
        logToAllSessions("=== Eclipse Shrine Party RESUMED ===")
    }

    fun updateRuntimeConfig(config: EclipseConfig) {
        currentConfig = config
        lightGatherTaunterSlots = resolveLightGatherSlots(config)
        refreshTauntInfo()

        if (isRunning) {
            _status.update { currentMap ->
                currentMap.mapValues { (slotKey, tele) ->
                    val slotConf = config.slots[slotKey]
                    val resolvedTarget =
                        NativeEclipseConfig.getDefaultTarget(slotKey, slotConf?.defaultTarget)
                    val pendingTaunt = pendingTauntTargets[slotKey]
                    tele.copy(targetMonsters = pendingTaunt ?: resolvedTarget)
                }
            }
        }
    }

    fun start(config: EclipseConfig): Pair<Boolean, String?> {
        if (isRunning) {
            return Pair(false, "Eclipse Shrine Party is already running!")
        }

        val slots = config.slots
        val masterSlot = slots[NativeEclipseConfig.MASTER_SLOT_KEY]
        if (masterSlot == null || masterSlot.username.isBlank() || masterSlot.password.isBlank()) {
            return Pair(
                false,
                "Master account (${NativeEclipseConfig.MASTER_SLOT_KEY}) credentials must be filled."
            )
        }

        val filledSlots =
            slots.filter { it.value.username.isNotBlank() && it.value.password.isNotBlank() }
        if (filledSlots.size < NativeEclipseConfig.ALL_SLOTS.size) {
            return Pair(
                false,
                "Please configure credentials for all ${NativeEclipseConfig.ALL_SLOTS.size} slots."
            )
        }

        lightGatherTaunterSlots = resolveLightGatherSlots(config)

        stopRequested = false
        _isPaused.value = false
        pausedAtMillis = 0L
        startTimeMillis = System.currentTimeMillis()
        clearedRuns = 0
        currentConfig = config
        resetTauntState(resetAll = true, clearAnimMsg = true)

        val initialStatuses = mutableMapOf<String, SlotTelemetry>()
        for (key in NativeEclipseConfig.ALL_SLOTS) {
            initialStatuses[key] = SlotTelemetry(
                running = true,
                isNextTaunter = key == "slot1" || key == "slot3"
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
        _isPaused.value = false
        pausedAtMillis = 0L
        logToAllSessions("=== Eclipse Shrine Party stopped by user ===")
        stopTimer()
        stopAllSessions()
        resetTauntState(resetAll = true, clearAnimMsg = true)
        coordinatorJob?.cancel()

        _status.update { current ->
            current.mapValues { (_, tele) ->
                tele.copy(
                    running = false,
                    isConnected = false,
                    isPaused = false
                )
            }
        }
        _stats.update { it.copy(timeRunning = (System.currentTimeMillis() - startTimeMillis) / 1000L) }
    }

    private suspend fun runParty(config: EclipseConfig) {
        val server = config.server.ifBlank { NativeEclipseConfig.DEFAULT_SERVER }
        val masterSlot = config.slots[NativeEclipseConfig.MASTER_SLOT_KEY] ?: return
        val masterUsername = masterSlot.username.trim()

        val slaveSlots = NativeEclipseConfig.SLAVE_SLOT_KEYS.mapNotNull { key ->
            config.slots[key]?.takeIf { it.username.isNotBlank() }
        }
        val slaveUsernames = slaveSlots.map { it.username.trim() }
        val slotJobs = mutableListOf<Job>()

        startTimer()

        try {
            for (slotKey in NativeEclipseConfig.ALL_SLOTS) {
                val slotConf = config.slots[slotKey] ?: continue
                val isMaster = NativeEclipseConfig.isMasterSlot(slotKey)
                val defaultTarget =
                    NativeEclipseConfig.getDefaultTarget(slotKey, slotConf.defaultTarget)

                val job = scope.launch(Dispatchers.IO) {
                    runSlotWorker(
                        slotKey = slotKey,
                        slotConfig = slotConf,
                        isMaster = isMaster,
                        server = server,
                        dungeonMap = MAP_DUNGEON,
                        masterUsername = masterUsername,
                        slaveUsernames = slaveUsernames,
                        defaultTarget = defaultTarget,
                        dropWhitelist = DROP_WHITELIST
                    )
                }
                slotJobs.add(job)
            }

            slotJobs.joinAll()
        } catch (e: Exception) {
            Log.e(TAG, "Error in Eclipse Party run: ${e.message}", e)
            logToAllSessions("Error in Eclipse Party run: ${e.message}", LogEntryType.ERROR)
        } finally {
            stopTimer()
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

        val session = AqwSession()
        session.slotKey = slotKey
        session.isPaused = { _isPaused.value }
        session.socketClient.tag = "$slotKey ($username)"
        activeSessions[slotKey] = session

        val logJob = scope.launch(Dispatchers.IO) {
            session.logs.collect { sessionLogs ->
                _slotLogs.update { current ->
                    current + (slotKey to sessionLogs)
                }
            }
        }

        val eventJob = scope.launch(Dispatchers.IO) {
            session.events.collect { event ->
                when (event) {
                    is AqwEvent.CombatTick -> {
                        if (_isPaused.value) return@collect
                        var hasSunWarmth = false
                        var hasMoonGaze = false

                        for ((aura, _) in event.auras) {
                            if (aura.name.equals(AURA_SUNS_WARMTH, ignoreCase = true)) {
                                hasSunWarmth = true
                            } else if (aura.name.equals(AURA_MOONLIGHT_GAZE, ignoreCase = true)) {
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
                                delay(ANIM_MSG_CLEAR_DELAY_MS.milliseconds)
                                synchronized(tauntLock) {
                                    if (System.currentTimeMillis() - latestAnimMsgTime >= ANIM_MSG_EXPIRY_THRESHOLD_MS) {
                                        latestAnimMsg = ""
                                        latestAnimMsgTime = 0L
                                        refreshTauntInfo()
                                    }
                                }
                            }
                        }

                        if (slotKey == "slot1") {
                            for (msg in event.animMsgs) {
                                val lower = msg.lowercase()
                                if (lower.contains("sun converge")) {
                                    onSunConvergeDetected(slotKey)
                                } else if (lower.contains("moon converge")) {
                                    onMoonConvergeDetected(slotKey)
                                } else if (lower.contains("gather")) {
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
                    }

                    is AqwEvent.ItemDropped -> {
                        if (dropWhitelist.any { it.equals(event.itemName, ignoreCase = true) }) {
                            session.log(
                                "Picking up drop: ${event.itemName} x${event.qty}",
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
            if (!preparingCharacter(
                    session = session,
                    slotKey = slotKey,
                    slotConfig = slotConfig,
                    server = server,
                    defaultTarget = defaultTarget
                )
            ) return
            if (!preparingParty(
                    session = session,
                    isMaster = isMaster,
                    masterUsername = masterUsername,
                    slaveUsernames = slaveUsernames,
                    dungeonMap = dungeonMap
                )
            ) return
            doCombat(
                session = session,
                slotKey = slotKey,
                isMaster = isMaster,
                masterUsername = masterUsername,
                defaultTarget = defaultTarget,
                dungeonMap = dungeonMap
            )
        } catch (e: Exception) {
            session.log("Worker error: ${e.message}")
        } finally {
            logJob.cancel()
            eventJob.cancel()
            session.stop()
            activeSessions.remove(slotKey)
            updateTelemetry(slotKey, session, defaultTarget, isRunning = false)
        }
    }

    private suspend fun preparingCharacter(
        session: AqwSession,
        slotKey: String,
        slotConfig: SlotConfig,
        server: String,
        defaultTarget: String
    ): Boolean {
        val username = slotConfig.username.trim()
        val password = slotConfig.password.trim()

        session.log("[$slotKey] Logging in to $server...")
        val connected = session.start(
            username = username,
            password = password,
            preferredServer = server
        )

        if (!connected) {
            session.log("[$slotKey] Failed to connect / login.")
            updateTelemetry(slotKey, session, defaultTarget, isRunning = false)
            return false
        }

        var loadWait = 0
        while (!session.isCharLoaded.value && loadWait < 150 && !stopRequested) {
            delay(100.milliseconds)
            loadWait++
        }

        if (!session.isCharLoaded.value) {
            session.log("[$slotKey] Character load timed out.")
            return false
        }

        // Equip farm class if specified, or fallback to default class
        val targetClass =
            slotConfig.charClass.ifBlank { NativeEclipseConfig.getDefaultClass(slotKey) }
        if (targetClass.isNotBlank()) {
            val classItem = session.playerState.inventory.firstOrNull {
                it.name.equals(targetClass, ignoreCase = true)
            }
            if (classItem != null) {
                session.item.equipItem(classItem.itemId)
                delay(1000.milliseconds)
            }
        }

        // Equip Scroll of Enrage (all 4 slots taunt in Eclipse)
        val soeItem = session.playerState.inventory.firstOrNull {
            it.name.equals(ITEM_SCROLL_OF_ENRAGE, ignoreCase = true)
        }
        if (soeItem == null || soeItem.qty <= 0) {
            val err =
                "Account '$username' ($slotKey) does not have Scroll of Enrage (SoE). Minimum 1 is required."
            session.log(err, LogEntryType.ERROR)
            stop()
            return false
        }
        session.log(
            "Equipping Scroll of Enrage (Qty: ${soeItem.qty})...",
            LogEntryType.INFO
        )
        session.item.equipScroll(soeItem.itemId, soeItem.sMeta)
        delay(1500.milliseconds)

        return true
    }

    private suspend fun preparingParty(
        session: AqwSession,
        isMaster: Boolean,
        masterUsername: String,
        slaveUsernames: List<String>,
        dungeonMap: String
    ): Boolean {
        if (stopRequested) return false

        if (isMaster) {
            session.log(
                "Master joining $MAP_ASSEMBLY to assemble party...",
                LogEntryType.INFO
            )
            session.map.joinMap(MAP_ASSEMBLY, 999999)
            delay(3500.milliseconds)

            session.log(
                "Waiting for party members to be online...",
                LogEntryType.INFO
            )
            var partyWait = 0
            while (activeSessions.size < NativeEclipseConfig.ALL_SLOTS.size && partyWait < 60 && !stopRequested) {
                delay(500.milliseconds)
                partyWait++
            }

            if (stopRequested) return false

            for (slaveName in slaveUsernames) {
                session.log(
                    "Sending party invite to $slaveName...",
                    LogEntryType.INFO
                )
                session.social.partyInvite(slaveName)
                delay(600.milliseconds)
            }

            delay(1000.milliseconds)
            session.log(
                "Queueing dungeon '$dungeonMap'...",
                LogEntryType.INFO
            )
            session.social.dungeonQueue(dungeonMap)
        } else {
            session.log(
                "Slave waiting for party invitation...",
                LogEntryType.INFO
            )
            var inviteWait = 0
            while (session.latestPartyId == null && inviteWait < 120 && !stopRequested) {
                session.map.gotoPlayer(masterUsername)
                delay(1000.milliseconds)
                inviteWait++
            }

            if (stopRequested) return false

            val pid = session.latestPartyId
            if (pid != null) {
                session.log(
                    "Accepting party invite (PID: $pid)...",
                    LogEntryType.INFO
                )
                session.social.partyAccept(pid)
                delay(1200.milliseconds)
            }
        }
        return !stopRequested
    }

    private fun isTauntTargetValidForCell(cell: String, target: String?): Boolean {
        if (target == null) return true
        return when {
            target.equals(MONSTER_SUFFOCATED_LIGHT, ignoreCase = true) -> cell == CELL_R1

            target.equals(MONSTER_SUNSET_KNIGHT, ignoreCase = true) ||
                    target.equals(MONSTER_MOON_HAZE, ignoreCase = true) -> cell == CELL_R2

            target.equals(MONSTER_ASCENDED_SOLSTICE, ignoreCase = true) ||
                    target.equals(MONSTER_ASCENDED_MIDNIGHT, ignoreCase = true) -> cell == CELL_R3

            else -> true
        }
    }

    private suspend fun doCombat(
        session: AqwSession,
        slotKey: String,
        isMaster: Boolean,
        masterUsername: String,
        defaultTarget: String,
        dungeonMap: String
    ) {
        var skillIdx = 0
        var doTaunt = false
        var tauntTarget: String? = null

        while (scope.isActive && !stopRequested && session.isConnected.value) {
            if (_isPaused.value) {
                doTaunt = false
                tauntTarget = null
                delay(500.milliseconds)
                continue
            }

            if (session.playerState.isDead) {
                delay(1000.milliseconds)
                session.log(
                    "DEAD...",
                    LogEntryType.WARNING
                )
                continue
            }

            val currentCell = session.playerState.cell
            val currentMap = session.playerState.mapName
            val isInCombat = session.playerState.isInCombat

            if (!isTauntTargetValidForCell(currentCell, tauntTarget)) {
                doTaunt = false
                tauntTarget = null
            }

            val queuedTaunt = pendingTauntTargets.remove(slotKey)
            if (queuedTaunt != null) {
                doTaunt = true
                tauntTarget = queuedTaunt
                refreshTauntInfo()
            }
            val currentDefaultTarget = currentConfig?.slots?.get(slotKey)?.let {
                NativeEclipseConfig.getDefaultTarget(slotKey, it.defaultTarget)
            } ?: defaultTarget
            val activeTargetDisplay = tauntTarget ?: currentDefaultTarget
            val soeQty = session.playerState.getScrollOfEnrageCount()

            updateTelemetry(
                slotKey,
                session,
                activeTargetDisplay,
                isRunning = true,
                soeQty = soeQty
            )

            if (isMaster) {
                val hasMonsters = session.map.hasAliveMonsters(currentCell)
                if (!hasMonsters && currentMap.contains(dungeonMap, ignoreCase = true)) {
                    when (currentCell) {
                        CELL_ENTER -> {
                            resetTauntState(resetAll = false)
                            session.log(
                                "$CELL_ENTER cleared. Moving to $CELL_R1...",
                                LogEntryType.INFO
                            )
                            session.map.jumpCell(CELL_R1, PAD_LEFT)
//                            waitForSlavesInCell(CELL_R1)
                        }

                        CELL_R1 -> {
                            resetTauntState(resetAll = false)
                            session.log(
                                "$CELL_R1 cleared. Moving to $CELL_R2...",
                                LogEntryType.INFO
                            )
                            session.map.jumpCell(CELL_R2, PAD_LEFT)
//                            waitForSlavesInCell(CELL_R2)
                        }

                        CELL_R2 -> {
                            resetTauntState(resetAll = true)
                            session.log(
                                "$CELL_R2 cleared. Moving to $CELL_R3 (Boss)...",
                                LogEntryType.INFO
                            )
                            session.map.jumpCell(CELL_R3, PAD_LEFT)
                            waitForSlavesInCell(CELL_R3)
                        }

                        CELL_R3 -> {
                            clearedRuns++
                            resetTauntState(resetAll = true)
                            session.log(
                                "=== Ascend Eclipse cleared $clearedRuns times! ===",
                                LogEntryType.INFO
                            )
                            session.social.sendChat("Ascend Eclipse cleared $clearedRuns times.")
                            delay(1000.milliseconds)
                            session.map.joinMap(MAP_RESET, 999999)
                            delay(2500.milliseconds)
                            session.social.dungeonQueue(dungeonMap)
                            delay(2000.milliseconds)
                        }
                    }
                }
            } else {
                val masterSession = activeSessions[NativeEclipseConfig.MASTER_SLOT_KEY]
                val masterCell = masterSession?.playerState?.cell ?: CELL_ENTER
                val masterPad = masterSession?.playerState?.pad ?: PAD_SPAWN
                val masterMap = masterSession?.playerState?.mapName ?: ""
                val isDifferentMap =
                    masterMap.isNotBlank() && !currentMap.equals(masterMap, ignoreCase = true)
                val isDifferentCell =
                    !masterCell.equals(currentCell, ignoreCase = true)

                if (isDifferentMap || isDifferentCell) {
                    if (!isDifferentMap) {
                        if (!isInCombat || currentCell == CELL_R3) {
                            session.log(
                                "[$slotKey] Master is in $masterMap:$masterCell (current: $currentMap:$currentCell). `Jump cell` to master...",
                                LogEntryType.INFO
                            )
                            session.map.jumpCell(masterCell, masterPad)
                        }
                    } else {
                        session.log(
                            "[$slotKey] Master is in $masterMap:$masterCell (current: $currentMap:$currentCell). `Goto` to master...",
                            LogEntryType.INFO
                        )
                        session.map.gotoPlayer(masterUsername)
                        delay(1200.milliseconds)
                    }
                }
            }

            val aliveMonsters = session.map.getMonsters(currentCell)
            if (aliveMonsters.isNotEmpty()) {
                // Check Solar Flare debuff
                val hasSolarFlare = session.playerState.hasAura(AURA_SOLAR_FLARE)
                val currentPrioritized = if (hasSolarFlare) {
                    listOf(MONSTER_BLESSLESS_DEER.lowercase())
                } else {
                    (tauntTarget ?: currentDefaultTarget).split(",").map { it.trim().lowercase() }
                }

                val targetMonster = aliveMonsters.firstOrNull { mon ->
                    currentPrioritized.any { p -> mon.name.lowercase().contains(p) }
                } ?: aliveMonsters.first()

                // Taunt handling
                if (doTaunt) {
                    if (soeQty <= 0) {
                        session.log(
                            "Ran out of Scroll of Enrage (SoE)!",
                            LogEntryType.ERROR
                        )
                        stop()
                        break
                    }

                    val currentTauntTarget = tauntTarget
                    val targetToTaunt = if (currentTauntTarget != null) {
                        aliveMonsters.firstOrNull {
                            it.name.contains(
                                currentTauntTarget,
                                ignoreCase = true
                            )
                        } ?: run {
                            doTaunt = false
                            tauntTarget = null
                            null
                        }
                    } else {
                        targetMonster
                    }

                    if (targetToTaunt != null && session.combat.canUseSkill(5)) {
                        session.log(
                            "Executing Taunt on ${targetToTaunt.name}!",
                            LogEntryType.INFO
                        )
                        delay(500.milliseconds)
                        if (session.combat.taunt(targetToTaunt.monMapId)) {
                            doTaunt = false
                            tauntTarget = null
                            delay(400.milliseconds)
                            continue
                        }
                    }
                }

                val nextSkill = DEFAULT_SKILL_ROTATION[skillIdx]
                skillIdx = (skillIdx + 1) % DEFAULT_SKILL_ROTATION.size

                val hasSunsHeat = session.playerState.hasAura(AURA_SUNS_HEAT)
                if (hasSunsHeat) {
                    session.combat.useBuff(nextSkill)
                } else {
                    session.combat.useSkill(nextSkill, targetMonster.monMapId)
                }
            }

            delay(200.milliseconds)
        }
    }

    private fun updateTelemetry(
        slotKey: String,
        session: AqwSession,
        targetMonsters: String,
        isRunning: Boolean,
        soeQty: Int? = null
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

        val nextSun = resolveAlternatingSlot(sunsetKnightCount.get(), "slot1", "slot2")
        val nextMoon = resolveAlternatingSlot(moonHazeCount.get(), "slot3", "slot4")
        val isNext = when (slotKey) {
            "slot1", "slot2" -> slotKey == nextSun
            "slot3", "slot4" -> slotKey == nextMoon
            else -> false
        }
        val isPending = pendingTauntTargets.containsKey(slotKey)

        _status.update { current ->
            current + (slotKey to SlotTelemetry(
                running = isRunning,
                isConnected = session.isConnected.value,
                isPaused = _isPaused.value,
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
                soeQty = soeQty ?: session.playerState.getScrollOfEnrageCount(),
                monsters = cellMonsters,
                targetMonsters = targetMonsters,
                targetedMonster = session.lastTargetMonster,
                auras = p.auras.toList()
            ))
        }
    }
}
