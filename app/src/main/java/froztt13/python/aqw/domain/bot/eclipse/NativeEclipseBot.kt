package froztt13.python.aqw.domain.bot.eclipse

import android.util.Log
import froztt13.python.aqw.data.engine.AqwEvent
import froztt13.python.aqw.data.engine.AqwSession
import froztt13.python.aqw.data.model.EclipseConfig
import froztt13.python.aqw.data.model.EclipseTauntInfo
import froztt13.python.aqw.data.model.MonsterTelemetry
import froztt13.python.aqw.data.model.PartyStats
import froztt13.python.aqw.data.model.SlotConfig
import froztt13.python.aqw.data.model.SlotTelemetry
import froztt13.python.aqw.data.model.TaunterTargetInfo
import froztt13.python.aqw.domain.coordinator.BasePartyCoordinator
import froztt13.python.aqw.helper.BotHelper
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

    // Internal Combat Logic Constants (not part of screen settings/editable inputs)
    private const val MAP_DUNGEON = "ascendeclipse"
    private const val MAP_ASSEMBLY = "yulgar"
    private const val MAP_ASSEMBLY_ROOM = 999999
    private const val MAP_RESET = "templeshrine"
    private const val MAP_RESET_ROOM = 999999

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

    private const val DEBOUNCE_SUN_WARMTH_MS = 8000L
    private const val DEBOUNCE_MOON_GAZE_MS = 8000L
    private const val DEBOUNCE_LIGHT_GATHER_MS = 6000L
    private const val DEBOUNCE_SUN_CONVERGE_MS = 6000L
    private const val DEBOUNCE_MOON_CONVERGE_MS = 6000L

    private const val DEFAULT_TAUNT_DELAY_MS = 5000L
    private const val IMMEDIATE_TAUNT_DELAY_MS = 0L
    private const val ANIM_MSG_CLEAR_DELAY_MS = 4000L
    private const val ANIM_MSG_EXPIRY_THRESHOLD_MS = 3900L

    private var currentConfig: EclipseConfig? = null

    private val _tauntInfo = MutableStateFlow(EclipseTauntInfo())
    val tauntInfo: StateFlow<EclipseTauntInfo> = _tauntInfo.asStateFlow()

    override fun logToSession(slotKey: String, message: String, botType: String) {
        super.logToSession(slotKey, message, if (botType == "System") "eclipse" else botType)
    }

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

    internal fun refreshTauntInfo() {
        val nextSunSlot = if ((sunsetKnightCount.get() + 1) % 2 == 1) "slot1" else "slot2"
        val nextMoonSlot = if ((moonHazeCount.get() + 1) % 2 == 1) "slot3" else "slot4"
        val taunters =
            lightGatherTaunterSlots.ifEmpty { NativeEclipseConfig.DEFAULT_LIGHT_GATHER_SLOTS }
        val nextGatherSlot = taunters[lightGatherCount.get() % taunters.size]
        val nextSunConvergeSlot = if ((sunConvergeCount.get() + 1) % 2 == 1) "slot1" else "slot2"
        val nextMoonConvergeSlot = if ((moonConvergeCount.get() + 1) % 2 == 1) "slot3" else "slot4"

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
        var pendingSunConvergeSlot: String? = null
        var pendingMoonConvergeSlot: String? = null

        for ((slot, target) in pendingTauntTargets) {
            when (target) {
                MONSTER_SUNSET_KNIGHT -> pendingSunSlot = slot
                MONSTER_MOON_HAZE -> pendingMoonSlot = slot
                MONSTER_SUFFOCATED_LIGHT -> pendingGatherSlot = slot
                MONSTER_ASCENDED_SOLSTICE -> pendingSunConvergeSlot = slot
                MONSTER_ASCENDED_MIDNIGHT -> pendingMoonConvergeSlot = slot
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
            sunConverge = TaunterTargetInfo(
                nextSlot = nextSunConvergeSlot,
                nextUsername = resolveUsername(nextSunConvergeSlot),
                pendingSlot = pendingSunConvergeSlot,
                pendingUsername = pendingSunConvergeSlot?.let { resolveUsername(it) },
                waveCount = sunConvergeCount.get()
            ),
            moonConverge = TaunterTargetInfo(
                nextSlot = nextMoonConvergeSlot,
                nextUsername = resolveUsername(nextMoonConvergeSlot),
                pendingSlot = pendingMoonConvergeSlot,
                pendingUsername = pendingMoonConvergeSlot?.let { resolveUsername(it) },
                waveCount = moonConvergeCount.get()
            ),
            latestAnimMsg = latestAnimMsg,
            animMsgTimestamp = latestAnimMsgTime
        )
    }

    internal fun onSunWarmthDetected(sourceSlot: String, delayMs: Long = DEFAULT_TAUNT_DELAY_MS) {
        val targetSlot: String
        val waveCount: Int
        synchronized(tauntLock) {
            val now = System.currentTimeMillis()
            if (now - lastSunsetKnightTime <= DEBOUNCE_SUN_WARMTH_MS) {
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
            "Sun's Warmth #$waveCount (from $sourceSlot) -> Assigned taunt to $targetSlot ($username)"
        )
        queueTaunt(targetSlot, MONSTER_SUNSET_KNIGHT, delayMs)
    }

    internal fun onMoonGazeDetected(sourceSlot: String, delayMs: Long = DEFAULT_TAUNT_DELAY_MS) {
        val targetSlot: String
        val waveCount: Int
        synchronized(tauntLock) {
            val now = System.currentTimeMillis()
            if (now - lastMoonHazeTime <= DEBOUNCE_MOON_GAZE_MS) {
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
            "Moonlight Gaze #$waveCount (from $sourceSlot) -> Assigned taunt to $targetSlot ($username)"
        )
        queueTaunt(targetSlot, MONSTER_MOON_HAZE, delayMs)
    }

    internal fun onLightGatherDetected(
        sourceSlot: String,
        delayMs: Long = IMMEDIATE_TAUNT_DELAY_MS
    ) {
        val targetSlot: String
        val waveCount: Int
        synchronized(tauntLock) {
            val now = System.currentTimeMillis()
            if (now - lastLightGatherTime <= DEBOUNCE_LIGHT_GATHER_MS) {
                return
            }
            lastLightGatherTime = now
            waveCount = lightGatherCount.incrementAndGet()
            val taunters =
                lightGatherTaunterSlots.ifEmpty { NativeEclipseConfig.DEFAULT_LIGHT_GATHER_SLOTS }
            targetSlot = taunters[(waveCount - 1) % taunters.size]
        }

        refreshTauntInfo()

        val targetSession = activeSessions[targetSlot]
        val username = targetSession?.playerState?.username ?: targetSlot
        BotHelper.dispatchLog(
            "eclipse",
            "System",
            "Light Gather #$waveCount (from $sourceSlot) -> Assigned taunt to $targetSlot ($username)"
        )
        // Immediate taunt (no 5s delay, as in core_eclipse.py)
        queueTaunt(targetSlot, MONSTER_SUFFOCATED_LIGHT, delayMs)
    }

    internal fun onSunConvergeDetected(
        sourceSlot: String,
        delayMs: Long = IMMEDIATE_TAUNT_DELAY_MS
    ) {
        val targetSlot: String
        val waveCount: Int
        synchronized(tauntLock) {
            val now = System.currentTimeMillis()
            if (now - lastSunConvergeTime <= DEBOUNCE_SUN_CONVERGE_MS) {
                return
            }
            lastSunConvergeTime = now
            waveCount = sunConvergeCount.incrementAndGet()
            targetSlot = if (waveCount % 2 == 1) "slot1" else "slot2"
        }

        refreshTauntInfo()

        val targetSession = activeSessions[targetSlot]
        val username = targetSession?.playerState?.username ?: targetSlot
        BotHelper.dispatchLog(
            "eclipse",
            "System",
            "Sun Converge #$waveCount (from $sourceSlot) -> Assigned taunt to $targetSlot ($username)"
        )
        queueTaunt(targetSlot, MONSTER_ASCENDED_SOLSTICE, delayMs)
    }

    internal fun onMoonConvergeDetected(
        sourceSlot: String,
        delayMs: Long = IMMEDIATE_TAUNT_DELAY_MS
    ) {
        val targetSlot: String
        val waveCount: Int
        synchronized(tauntLock) {
            val now = System.currentTimeMillis()
            if (now - lastMoonConvergeTime <= DEBOUNCE_MOON_CONVERGE_MS) {
                return
            }
            lastMoonConvergeTime = now
            waveCount = moonConvergeCount.incrementAndGet()
            targetSlot = if (waveCount % 2 == 1) "slot3" else "slot4"
        }

        refreshTauntInfo()

        val targetSession = activeSessions[targetSlot]
        val username = targetSession?.playerState?.username ?: targetSlot
        BotHelper.dispatchLog(
            "eclipse",
            "System",
            "Moon Converge #$waveCount (from $sourceSlot) -> Assigned taunt to $targetSlot ($username)"
        )
        queueTaunt(targetSlot, MONSTER_ASCENDED_MIDNIGHT, delayMs)
    }

    private fun queueTaunt(
        slotKey: String,
        targetMonster: String,
        delayMs: Long = DEFAULT_TAUNT_DELAY_MS
    ) {
        scope.launch(Dispatchers.IO) {
            if (delayMs > 0) {
                delay(delayMs.milliseconds)
            }
            if (stopRequested) return@launch
            val session = activeSessions[slotKey]
            if (session != null && session.isConnected.value && session.map.hasAliveMonsters()) {
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
            BotHelper.dispatchLog(
                "eclipse",
                "System",
                "Pausing Eclipse Party: all slots leaving combat and jumping to current cell..."
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
                        BotHelper.dispatchLog(
                            "eclipse",
                            session.playerState.username,
                            "[$slotKey] Left combat, jumped to $currentCell [$currentPad]"
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
                    isRunning = true
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
        val fromSlots =
            config.slots.filter { it.key != NativeEclipseConfig.MASTER_SLOT_KEY && it.value.lightGatherTaunter }.keys.sorted()
        val configuredGather = fromSlots.ifEmpty {
            NativeEclipseConfig.DEFAULT_LIGHT_GATHER_SLOTS
        }
        lightGatherTaunterSlots = configuredGather
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

        val fromSlots =
            config.slots.filter { it.key != NativeEclipseConfig.MASTER_SLOT_KEY && it.value.lightGatherTaunter }.keys.sorted()
        val configuredGather = fromSlots.ifEmpty {
            NativeEclipseConfig.DEFAULT_LIGHT_GATHER_SLOTS
        }
        lightGatherTaunterSlots = configuredGather

        stopRequested = false
        _isPaused.value = false
        pausedAtMillis = 0L
        startTimeMillis = System.currentTimeMillis()
        clearedRuns = 0
        currentConfig = config
        sunsetKnightCount.set(0)
        moonHazeCount.set(0)
        lightGatherCount.set(0)
        sunConvergeCount.set(0)
        moonConvergeCount.set(0)
        lastSunsetKnightTime = 0L
        lastMoonHazeTime = 0L
        lastLightGatherTime = 0L
        lastSunConvergeTime = 0L
        lastMoonConvergeTime = 0L
        latestAnimMsg = ""
        latestAnimMsgTime = 0L
        animClearJob?.cancel()
        animClearJob = null
        pendingTauntTargets.clear()
        refreshTauntInfo()

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
        stopAllSessions()
        pendingTauntTargets.clear()
        latestAnimMsg = ""
        latestAnimMsgTime = 0L
        animClearJob?.cancel()
        animClearJob = null
        refreshTauntInfo()
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
        val dungeonMap = MAP_DUNGEON
        val dropWhitelist = DROP_WHITELIST

        val masterSlot = config.slots[NativeEclipseConfig.MASTER_SLOT_KEY] ?: return
        val masterUsername = masterSlot.username.trim()

        val slaveSlots = NativeEclipseConfig.SLAVE_SLOT_KEYS.mapNotNull { key ->
            config.slots[key]?.takeIf { it.username.isNotBlank() }
        }
        val slaveUsernames = slaveSlots.map { it.username.trim() }

        val slotJobs = mutableListOf<Job>()

        val statsTimerJob = scope.launch(Dispatchers.IO) {
            while (isActive && !stopRequested) {
                if (!_isPaused.value) {
                    val elapsed = (System.currentTimeMillis() - startTimeMillis) / 1000L
                    _stats.update { it.copy(timeRunning = elapsed, clearedCount = clearedRuns) }
                }
                delay(1000.milliseconds)
            }
        }

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

                    is AqwEvent.ItemDropped -> {
                        if (dropWhitelist.any { it.equals(event.itemName, ignoreCase = true) }) {
                            BotHelper.dispatchLog(
                                "eclipse",
                                username,
                                "Picking up drop: ${event.itemName} x${event.qty}"
                            )
                            session.item.getItemDrop(event.itemId)
                        }
                    }

                    else -> {}
                }
            }
        }

        try {
            if (!preparingCharacter(session, slotKey, slotConfig, server, defaultTarget)) return
            if (!preparingParty(
                    session,
                    isMaster,
                    username,
                    masterUsername,
                    slaveUsernames,
                    dungeonMap
                )
            ) return
            doCombat(
                session,
                slotKey,
                isMaster,
                username,
                masterUsername,
                defaultTarget,
                dungeonMap
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
            BotHelper.dispatchLog("eclipse", username, err)
            stop()
            return false
        }
        BotHelper.dispatchLog(
            "eclipse",
            username,
            "Equipping Scroll of Enrage (Qty: ${soeItem.qty})..."
        )
        session.item.equipScroll(soeItem.itemId, soeItem.sMeta)
        delay(1500.milliseconds)

        return true
    }

    private suspend fun preparingParty(
        session: AqwSession,
        isMaster: Boolean,
        username: String,
        masterUsername: String,
        slaveUsernames: List<String>,
        dungeonMap: String
    ): Boolean {
        if (stopRequested) return false

        if (isMaster) {
            BotHelper.dispatchLog(
                "eclipse",
                username,
                "Master joining $MAP_ASSEMBLY-$MAP_ASSEMBLY_ROOM to assemble party..."
            )
            session.map.joinMap(MAP_ASSEMBLY, MAP_ASSEMBLY_ROOM)
            delay(3500.milliseconds)

            BotHelper.dispatchLog(
                "eclipse",
                username,
                "Waiting for party members to be online..."
            )
            var partyWait = 0
            while (activeSessions.size < NativeEclipseConfig.ALL_SLOTS.size && partyWait < 60 && !stopRequested) {
                delay(500.milliseconds)
                partyWait++
            }

            if (stopRequested) return false

            for (slaveName in slaveUsernames) {
                BotHelper.dispatchLog(
                    "eclipse",
                    username,
                    "Sending party invite to $slaveName..."
                )
                session.social.partyInvite(slaveName)
                delay(600.milliseconds)
            }

            delay(1000.milliseconds)
            BotHelper.dispatchLog("eclipse", username, "Queueing dungeon '$dungeonMap'...")
            session.social.dungeonQueue(dungeonMap)
        } else {
            BotHelper.dispatchLog("eclipse", username, "Slave waiting for party invitation...")
            var inviteWait = 0
            while (session.latestPartyId == null && inviteWait < 120 && !stopRequested) {
                session.map.gotoPlayer(masterUsername)
                delay(1000.milliseconds)
                inviteWait++
            }

            if (stopRequested) return false

            val pid = session.latestPartyId
            if (pid != null) {
                BotHelper.dispatchLog(
                    "eclipse",
                    username,
                    "Accepting party invite (PID: $pid)..."
                )
                session.social.partyAccept(pid)
                delay(1200.milliseconds)
            }
        }
        return !stopRequested
    }

    private suspend fun doCombat(
        session: AqwSession,
        slotKey: String,
        isMaster: Boolean,
        username: String,
        masterUsername: String,
        defaultTarget: String,
        dungeonMap: String
    ) {
        val skillRotation = DEFAULT_SKILL_ROTATION
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
                BotHelper.dispatchLog(
                    "eclipse",
                    username,
                    "DEAD..."
                )
                continue
            }

            val currentCell = session.playerState.cell
            val currentMap = session.playerState.mapName
            val isInCombat = session.playerState.isInCombat

            if (!currentCell.equals(CELL_R1, ignoreCase = true) &&
                tauntTarget?.equals(MONSTER_SUFFOCATED_LIGHT, ignoreCase = true) == true
            ) {
                doTaunt = false
                tauntTarget = null
            }
            if (!currentCell.equals(CELL_R2, ignoreCase = true) &&
                (tauntTarget?.equals(MONSTER_SUNSET_KNIGHT, ignoreCase = true) == true ||
                        tauntTarget?.equals(MONSTER_MOON_HAZE, ignoreCase = true) == true)
            ) {
                doTaunt = false
                tauntTarget = null
            }
            if (!currentCell.equals(CELL_R3, ignoreCase = true) &&
                (tauntTarget?.equals(MONSTER_ASCENDED_SOLSTICE, ignoreCase = true) == true ||
                        tauntTarget?.equals(MONSTER_ASCENDED_MIDNIGHT, ignoreCase = true) == true)
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
                it.name.equals(ITEM_SCROLL_OF_ENRAGE, ignoreCase = true)
            }
            val soeQty = soeItemNow?.qty ?: 0
            val currentDefaultTarget = currentConfig?.slots?.get(slotKey)?.let {
                NativeEclipseConfig.getDefaultTarget(slotKey, it.defaultTarget)
            } ?: defaultTarget
            val activeTargetDisplay = tauntTarget ?: currentDefaultTarget
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
                            lightGatherCount.set(0)
                            sunConvergeCount.set(0)
                            moonConvergeCount.set(0)
                            lastLightGatherTime = 0L
                            lastSunConvergeTime = 0L
                            lastMoonConvergeTime = 0L
                            pendingTauntTargets.clear()
                            refreshTauntInfo()
                            BotHelper.dispatchLog(
                                "eclipse",
                                username,
                                "$CELL_ENTER cleared. Moving to $CELL_R1..."
                            )
                            session.map.jumpCell(CELL_R1, PAD_LEFT)
                            delay(1200.milliseconds)
                            waitForSlavesInCell(CELL_R1)
                        }

                        CELL_R1 -> {
                            lightGatherCount.set(0)
                            sunConvergeCount.set(0)
                            moonConvergeCount.set(0)
                            lastLightGatherTime = 0L
                            lastSunConvergeTime = 0L
                            lastMoonConvergeTime = 0L
                            pendingTauntTargets.clear()
                            refreshTauntInfo()
                            BotHelper.dispatchLog(
                                "eclipse",
                                username,
                                "$CELL_R1 cleared. Moving to $CELL_R2..."
                            )
                            session.map.jumpCell(CELL_R2, PAD_LEFT)
                            delay(1200.milliseconds)
                            waitForSlavesInCell(CELL_R2)
                        }

                        CELL_R2 -> {
                            sunsetKnightCount.set(0)
                            moonHazeCount.set(0)
                            lightGatherCount.set(0)
                            sunConvergeCount.set(0)
                            moonConvergeCount.set(0)
                            lastSunsetKnightTime = 0L
                            lastMoonHazeTime = 0L
                            lastLightGatherTime = 0L
                            lastSunConvergeTime = 0L
                            lastMoonConvergeTime = 0L
                            pendingTauntTargets.clear()
                            refreshTauntInfo()
                            BotHelper.dispatchLog(
                                "eclipse",
                                username,
                                "$CELL_R2 cleared. Moving to $CELL_R3 (Boss)..."
                            )
                            session.map.jumpCell(CELL_R3, PAD_LEFT)
                            delay(1200.milliseconds)
                            waitForSlavesInCell(CELL_R3)
                        }

                        CELL_R3 -> {
                            clearedRuns++
                            sunsetKnightCount.set(0)
                            moonHazeCount.set(0)
                            lightGatherCount.set(0)
                            sunConvergeCount.set(0)
                            moonConvergeCount.set(0)
                            lastSunsetKnightTime = 0L
                            lastMoonHazeTime = 0L
                            lastLightGatherTime = 0L
                            lastSunConvergeTime = 0L
                            lastMoonConvergeTime = 0L
                            pendingTauntTargets.clear()
                            refreshTauntInfo()
                            BotHelper.dispatchLog(
                                "eclipse",
                                username,
                                "=== Ascend Eclipse cleared $clearedRuns times! ==="
                            )
                            session.social.sendChat("Ascend Eclipse cleared $clearedRuns times.")
                            delay(1000.milliseconds)
                            session.map.joinMap(MAP_RESET, MAP_RESET_ROOM)
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
                            BotHelper.dispatchLog(
                                "eclipse",
                                username,
                                "[$slotKey] Master is in $masterMap:$masterCell (current: $currentMap:$currentCell). Moving to master..."
                            )
                            session.map.jumpCell(masterCell, masterPad)
                        }
                    } else {
                        BotHelper.dispatchLog(
                            "eclipse",
                            username,
                            "[$slotKey] Master is in $masterMap:$masterCell (current: $currentMap:$currentCell). Moving to master..."
                        )
                        session.map.gotoPlayer(masterUsername)
                        delay(1200.milliseconds)
                    }
                    continue
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
                        BotHelper.dispatchLog(
                            "eclipse",
                            username,
                            "Ran out of Scroll of Enrage (SoE)!"
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
                        BotHelper.dispatchLog(
                            "eclipse",
                            username,
                            "Executing Taunt on ${targetToTaunt.name}!"
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

                val nextSkill = skillRotation[skillIdx]
                skillIdx = (skillIdx + 1) % skillRotation.size

                val hasSunsHeat = session.playerState.hasAura(AURA_SUNS_HEAT)
                if (hasSunsHeat) {
                    session.combat.useBuff(nextSkill)
                } else {
                    session.combat.useSkill(nextSkill, targetMonster.monMapId)
                }
            }

            delay(500.milliseconds)
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

        val nextSun = if ((sunsetKnightCount.get() + 1) % 2 == 1) "slot1" else "slot2"
        val nextMoon = if ((moonHazeCount.get() + 1) % 2 == 1) "slot3" else "slot4"
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
                soeQty = soeQty,
                monsters = cellMonsters,
                targetMonsters = targetMonsters,
                targetedMonster = session.lastTargetMonster,
                auras = p.auras.toList()
            ))
        }
    }
}
