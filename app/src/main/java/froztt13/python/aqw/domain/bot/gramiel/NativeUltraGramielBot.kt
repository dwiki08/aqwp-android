package froztt13.python.aqw.domain.bot.gramiel

import android.util.Log
import froztt13.python.aqw.data.engine.AqwEvent
import froztt13.python.aqw.data.engine.AqwSession
import froztt13.python.aqw.data.model.LogEntryType
import froztt13.python.aqw.data.model.MonsterTelemetry
import froztt13.python.aqw.data.model.PartyStats
import froztt13.python.aqw.data.model.SlotConfig
import froztt13.python.aqw.data.model.SlotTelemetry
import froztt13.python.aqw.data.model.UltraBossConfig
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

object NativeUltraGramielBot : BasePartyCoordinator("NativeUltraGramielBot") {

    @Suppress("PropertyName")
    val Config = NativeUltraGramielConfig

    private const val TAG = "NativeUltraGramielBot"

    // Internal Combat Constants
    private const val MAP_DUNGEON = "ultragramiel"
    private const val MAP_ASSEMBLY = "yulgar"
    private const val CELL_ENTER = "Enter"
    private const val PAD_SPAWN = "Spawn"

    private const val TEMP_ITEM_GRAMIEL_VANQUISHED = "Gramiel the Graceful Vanquished"
    private const val ITEM_SCROLL_OF_ENRAGE = "Scroll of Enrage"
    private val DROP_WHITELIST = setOf("Gramiel Insignia", "Gramiel's Shard", "Celestial Offering")
    private val DEFAULT_SKILL_ROTATION = listOf(3, 0, 1, 0, 2, 0, 3, 0, 4)

    private const val MONSTER_ID_GRAMIEL = "1"
    private const val MONSTER_ID_SLOT_1_2 = "2"
    private const val MONSTER_ID_SLOT_3_4 = "3"

    private const val ANIM_SHATTERING = "shattering"

    private val _isFinished = MutableStateFlow(false)
    val isFinished: StateFlow<Boolean> = _isFinished.asStateFlow()

    private val bossDefeatedLock = Any()

    @Volatile
    private var isBossDefeatedHandled = false

    fun resetFinishedState() {
        _isFinished.value = false
    }

    private fun onBossDefeated() {
        synchronized(bossDefeatedLock) {
            if (isBossDefeatedHandled) return
            isBossDefeatedHandled = true

            scope.launch(Dispatchers.IO) {
                logToAllSessions(
                    "🏆 Ultra Gramiel defeated! Completing Quest ID 10301...",
                    LogEntryType.INFO
                )

                for ((slotKey, session) in activeSessions) {
                    try {
                        session.log("[$slotKey] Completing Quest ID 10301...", LogEntryType.INFO)
                        session.quest.ensureTurnInQuest(10301)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed turning in quest 10301 for $slotKey: ${e.message}")
                    }
                }

                clearedRuns++
                _stats.update { it.copy(clearedCount = clearedRuns) }

                delay(1000.milliseconds)

                _isFinished.value = true
                logToAllSessions("=== Ultra Gramiel Bot Completed ===", LogEntryType.INFO)
                stop()
            }
        }
    }

    private var currentConfig: UltraBossConfig? = null

    private val _animMsg = MutableStateFlow("")
    val animMsg: StateFlow<String> = _animMsg.asStateFlow()

    internal val shatteringCount = AtomicInteger(0)
    internal var lastShatteringTime = 0L
    internal var latestAnimMsg: String = ""
    internal var latestAnimMsgTime: Long = 0L
    private val tauntLock = Any()
    internal val pendingTauntTargets = ConcurrentHashMap<String, String>()

    internal fun isGramielVanquished(): Boolean {
        return activeSessions.values.any { session ->
            session.playerState.getItemTemp(TEMP_ITEM_GRAMIEL_VANQUISHED) != null
        }
    }

    internal fun isMonsterAlive(monMapId: String): Boolean {
        if (activeSessions.isEmpty()) return true
        return activeSessions.values.any { session ->
            session.map.allMonsters.value.any {
                it.monMapId == monMapId && it.isAlive && it.currentHp > 0
            }
        }
    }

    internal fun onShatteringDetected(sourceSlot: String) {
        synchronized(tauntLock) {
            val now = System.currentTimeMillis()
            if (now - lastShatteringTime <= 4000L) {
                return
            }
            lastShatteringTime = now
            val wave = shatteringCount.incrementAndGet()

            val isMon1Alive = isMonsterAlive(MONSTER_ID_GRAMIEL)
            val isMon2Alive = isMonsterAlive(MONSTER_ID_SLOT_1_2)
            val isMon3Alive = isMonsterAlive(MONSTER_ID_SLOT_3_4)

            logToAllSessions(
                "💥 Shattering attack #$wave detected (from $sourceSlot)!",
                LogEntryType.INFO
            )

            // Phase 2: Both side monsters (2 & 3) are dead, only Gramiel (1) remains.
            // Taunt on Gramiel is executed directly when Skill 5 is ready for each slot.
            if (!isMon2Alive && !isMon3Alive && isMon1Alive) {
                return
            }

            // Phase 1 & 2: Odd-even taunt rotation for Mon 2 and Mon 3
            val taunterSlot12 = if (wave % 2 != 0) "slot1" else "slot2"
            val taunterSlot34 = if (wave % 2 != 0) "slot3" else "slot4"

            if (isMon2Alive) {
                logToAllSessions(
                    "[$taunterSlot12] Queuing taunt for Monster ID $MONSTER_ID_SLOT_1_2",
                    LogEntryType.INFO
                )
                queueTaunt(taunterSlot12, MONSTER_ID_SLOT_1_2)
            } else {
                pendingTauntTargets.remove("slot1")
                pendingTauntTargets.remove("slot2")
                logToAllSessions(
                    "Monster ID $MONSTER_ID_SLOT_1_2 is dead! Taunt for Slots 1 & 2 disabled.",
                    LogEntryType.INFO
                )
            }

            if (isMon3Alive) {
                logToAllSessions(
                    "[$taunterSlot34] Queuing taunt for Monster ID $MONSTER_ID_SLOT_3_4",
                    LogEntryType.INFO
                )
                queueTaunt(taunterSlot34, MONSTER_ID_SLOT_3_4)
            } else {
                pendingTauntTargets.remove("slot3")
                pendingTauntTargets.remove("slot4")
                logToAllSessions(
                    "Monster ID $MONSTER_ID_SLOT_3_4 is dead! Taunt for Slots 3 & 4 disabled.",
                    LogEntryType.INFO
                )
            }
        }
    }

    private fun queueTaunt(slotKey: String, targetMonsterId: String) {
        pendingTauntTargets[slotKey] = targetMonsterId
        scope.launch(Dispatchers.IO) {
            val session = activeSessions[slotKey]
            if (session != null && session.isConnected.value) {
                session.log(
                    "[$slotKey] Queuing taunt for Monster ID $targetMonsterId (shattering trigger)",
                    LogEntryType.INFO
                )
            }
        }
    }

    fun pause() {
        if (!isRunning || _isPaused.value) return
        _isPaused.value = true
        pausedAtMillis = System.currentTimeMillis()
        pendingTauntTargets.clear()
        latestAnimMsg = ""
        latestAnimMsgTime = 0L

        scope.launch(Dispatchers.IO) {
            logToAllSessions(
                "Pausing Ultra Gramiel Party: all slots resting...",
                LogEntryType.WARNING
            )
            val jobs = activeSessions.map { (slotKey, session) ->
                launch {
                    try {
                        session.combat.rest()
                        session.playerState.isInCombat = false
                        session.log("[$slotKey] Resting on pause.", LogEntryType.INFO)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error resting on pause for $slotKey: ${e.message}")
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
            logToAllSessions("=== Ultra Gramiel Party PAUSED ===")
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
                NativeUltraGramielConfig.getDefaultTarget(slotKey, slotConf?.defaultTarget)
            updateTelemetry(
                slotKey = slotKey,
                session = session,
                targetMonsters = defaultTarget,
                isRunning = true
            )
        }
        logToAllSessions("=== Ultra Gramiel Party RESUMED ===")
    }

    fun start(config: UltraBossConfig): Pair<Boolean, String?> {
        if (isRunning) {
            return Pair(false, "Ultra Gramiel Party is already running!")
        }

        val slots = config.slots
        val masterSlot = slots[NativeUltraGramielConfig.MASTER_SLOT_KEY]
        if (masterSlot == null || masterSlot.username.isBlank() || masterSlot.password.isBlank()) {
            return Pair(
                false,
                "Master account (${NativeUltraGramielConfig.MASTER_SLOT_KEY}) credentials must be filled."
            )
        }

        val filledSlots =
            slots.filter { it.value.username.isNotBlank() && it.value.password.isNotBlank() }
        if (filledSlots.size < NativeUltraGramielConfig.ALL_SLOTS.size) {
            return Pair(
                false,
                "Please configure credentials for all ${NativeUltraGramielConfig.ALL_SLOTS.size} slots."
            )
        }

        stopRequested = false
        _isPaused.value = false
        _isFinished.value = false
        isBossDefeatedHandled = false
        pausedAtMillis = 0L
        startTimeMillis = System.currentTimeMillis()
        clearedRuns = 0
        currentConfig = config
        shatteringCount.set(0)
        lastShatteringTime = 0L
        pendingTauntTargets.clear()
        latestAnimMsg = ""
        latestAnimMsgTime = 0L
        _animMsg.value = ""

        val initialStatuses = mutableMapOf<String, SlotTelemetry>()
        for (key in NativeUltraGramielConfig.ALL_SLOTS) {
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
        logToAllSessions("=== Ultra Gramiel Party stopped by user ===")
        stopTimer()
        stopAllSessions()
        pendingTauntTargets.clear()
        latestAnimMsg = ""
        latestAnimMsgTime = 0L
        _animMsg.value = ""
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

    private suspend fun runParty(config: UltraBossConfig) {
        val server = config.server.ifBlank { NativeUltraGramielConfig.DEFAULT_SERVER }
        val masterSlot = config.slots[NativeUltraGramielConfig.MASTER_SLOT_KEY] ?: return
        val masterUsername = masterSlot.username.trim()

        val slaveSlots = NativeUltraGramielConfig.SLAVE_SLOT_KEYS.mapNotNull { key ->
            config.slots[key]?.takeIf { it.username.isNotBlank() }
        }
        val slaveUsernames = slaveSlots.map { it.username.trim() }
        val slotJobs = mutableListOf<Job>()

        startTimer()

        try {
            for (slotKey in NativeUltraGramielConfig.ALL_SLOTS) {
                val slotConf = config.slots[slotKey] ?: continue
                val isMaster = NativeUltraGramielConfig.isMasterSlot(slotKey)
                val defaultTarget =
                    NativeUltraGramielConfig.getDefaultTarget(slotKey, slotConf.defaultTarget)

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
            Log.e(TAG, "Error in Ultra Gramiel Party run: ${e.message}", e)
            logToAllSessions("Error in Ultra Gramiel Party run: ${e.message}", LogEntryType.ERROR)
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

                        if (event.animMsgs.isNotEmpty()) {
                            val combined = event.animMsgs.joinToString(" | ")
                            latestAnimMsg = combined
                            latestAnimMsgTime = System.currentTimeMillis()
                            _animMsg.value = combined

                            if (slotKey == "slot1") {
                                for (msg in event.animMsgs) {
                                    if (msg.contains(ANIM_SHATTERING, ignoreCase = true)) {
                                        onShatteringDetected(slotKey)
                                    }
                                }
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

                    is AqwEvent.ItemsAdded -> {
                        for (added in event.items) {
                            if (added.name.equals(
                                    TEMP_ITEM_GRAMIEL_VANQUISHED,
                                    ignoreCase = true
                                )
                            ) {
                                session.log(
                                    "Received temp item '${added.name}' - Gramiel defeated!",
                                    LogEntryType.INFO
                                )
                                onBossDefeated()
                            }
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
                    bossMap = dungeonMap
                )
            ) return
            doCombat(
                session = session,
                slotKey = slotKey,
                slotConfig = slotConfig,
                isMaster = isMaster,
                masterUsername = masterUsername,
                bossMap = dungeonMap
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

        val targetClass =
            slotConfig.charClass.ifBlank { NativeUltraGramielConfig.getDefaultClass(slotKey) }
        if (targetClass.isNotBlank()) {
            val classItem = session.playerState.inventory.firstOrNull {
                it.name.equals(targetClass, ignoreCase = true)
            }
            if (classItem != null) {
                session.item.equipItem(classItem.itemId)
                delay(1000.milliseconds)
            }
        }

        val soeItem = session.playerState.inventory.firstOrNull {
            it.name.equals(ITEM_SCROLL_OF_ENRAGE, ignoreCase = true)
        }
        if (soeItem == null || soeItem.qty <= 0) {
            val err = "Account '$username' ($slotKey) does not have Scroll of Enrage (SoE)."
            session.log(err, LogEntryType.ERROR)
            stop()
            return false
        }
        session.playerState.tempInventory.removeAll {
            it.name.equals(TEMP_ITEM_GRAMIEL_VANQUISHED, ignoreCase = true)
        }
        session.log("Accepting Quest ID 10301...", LogEntryType.INFO)
        session.quest.ensureAcceptQuest(10301)

        session.log("Equipping Scroll of Enrage (Qty: ${soeItem.qty})...", LogEntryType.INFO)
        session.item.equipScroll(soeItem.itemId, soeItem.sMeta)
        delay(1500.milliseconds)

        return true
    }

    private suspend fun preparingParty(
        session: AqwSession,
        isMaster: Boolean,
        masterUsername: String,
        slaveUsernames: List<String>,
        bossMap: String
    ): Boolean {
        if (stopRequested) return false
        val roomNumber = currentConfig?.roomNumber ?: 9099

        if (isMaster) {
            session.log("Master joining $MAP_ASSEMBLY to assemble party...", LogEntryType.INFO)
            session.map.joinMap(MAP_ASSEMBLY, 999999)
            delay(3500.milliseconds)

            session.log("Waiting for party members to be online...", LogEntryType.INFO)
            var partyWait = 0
            while (activeSessions.size < NativeUltraGramielConfig.ALL_SLOTS.size && partyWait < 60 && !stopRequested) {
                delay(500.milliseconds)
                partyWait++
            }

            if (stopRequested) return false

            for (slaveName in slaveUsernames) {
                session.log("Sending party invite to $slaveName...", LogEntryType.INFO)
                session.social.partyInvite(slaveName)
                delay(600.milliseconds)
            }

            delay(1000.milliseconds)
            session.log("Joining map '$bossMap-$roomNumber'...", LogEntryType.INFO)
            session.map.joinMap(bossMap, roomNumber)
            delay(2000.milliseconds)
        } else {
            session.log("Slave waiting for party invitation...", LogEntryType.INFO)
            var inviteWait = 0
            while (session.latestPartyId == null && inviteWait < 120 && !stopRequested) {
                session.map.gotoPlayer(masterUsername)
                delay(1000.milliseconds)
                inviteWait++
            }

            if (stopRequested) return false

            val pid = session.latestPartyId
            if (pid != null) {
                session.log("Accepting party invite (PID: $pid)...", LogEntryType.INFO)
                session.social.partyAccept(pid)
                delay(1200.milliseconds)
            }

            session.log("Joining map '$bossMap-$roomNumber'...", LogEntryType.INFO)
            session.map.joinMap(bossMap, roomNumber)
            delay(1500.milliseconds)
            session.map.gotoPlayer(masterUsername)
        }
        return !stopRequested
    }

    private suspend fun doCombat(
        session: AqwSession,
        slotKey: String,
        slotConfig: SlotConfig,
        isMaster: Boolean,
        masterUsername: String,
        bossMap: String
    ) {
        var skillIdx = 0
        var doTaunt = false
        val assignedTargetMonMapId =
            if (slotKey == "slot1" || slotKey == "slot2") MONSTER_ID_SLOT_1_2 else MONSTER_ID_SLOT_3_4
        val roomNumber = currentConfig?.roomNumber ?: 9099

        while (scope.isActive && !stopRequested && session.isConnected.value) {
            if (_isPaused.value) {
                doTaunt = false
                delay(500.milliseconds)
                continue
            }

            if (session.playerState.isDead) {
                delay(1000.milliseconds)
                session.log("DEAD...", LogEntryType.WARNING)
                continue
            }

            val currentCell = session.playerState.cell
            val currentMap = session.playerState.mapName

            if (!currentMap.equals(bossMap, ignoreCase = true)) {
                session.log("Joining map '$bossMap-$roomNumber'...", LogEntryType.INFO)
                session.map.joinMap(bossMap, roomNumber)
                delay(1500.milliseconds)
                continue
            }

            if (currentCell.equals(CELL_ENTER, ignoreCase = true)) {
                session.log("Moving to $bossMap room r2...", LogEntryType.INFO)
                session.map.jumpCell("r2", "Left")
                delay(800.milliseconds)
            }

            val aliveMonsters = session.map.getMonsters(currentCell)

            val isMon1Alive = aliveMonsters.any { it.monMapId == MONSTER_ID_GRAMIEL }
            val isMon2Alive = aliveMonsters.any { it.monMapId == MONSTER_ID_SLOT_1_2 }
            val isMon3Alive = aliveMonsters.any { it.monMapId == MONSTER_ID_SLOT_3_4 }
            val isOnlyGramielAlive = !isMon2Alive && !isMon3Alive && isMon1Alive

            val targetMon = aliveMonsters.firstOrNull { it.monMapId == assignedTargetMonMapId }
                ?: aliveMonsters.firstOrNull { it.monMapId == MONSTER_ID_SLOT_1_2 || it.monMapId == MONSTER_ID_SLOT_3_4 }
                ?: aliveMonsters.firstOrNull { it.monMapId == MONSTER_ID_GRAMIEL }
                ?: aliveMonsters.firstOrNull()


            var tauntTargetMonId: String? = null

            if (isOnlyGramielAlive) {
                // When only Gramiel (Monster ID 1) remains, taunt directly whenever Skill 5 is ready
                if (session.combat.canUseSkill(5)) {
                    doTaunt = true
                    tauntTargetMonId = MONSTER_ID_GRAMIEL
                } else {
                    doTaunt = false
                }
            } else {
                val queuedTaunt = pendingTauntTargets.remove(slotKey)
                if (queuedTaunt != null) {
                    val tauntMon = aliveMonsters.firstOrNull { it.monMapId == queuedTaunt }
                    if (tauntMon != null) {
                        doTaunt = true
                        tauntTargetMonId = queuedTaunt
                    } else {
                        doTaunt = false

                    }
                }
            }

            val activeTargetMonId = targetMon?.monMapId ?: assignedTargetMonMapId

            val soeQty = session.playerState.getScrollOfEnrageCount()
            updateTelemetry(
                slotKey,
                session,
                "Monster ID $activeTargetMonId",
                isRunning = true,
                soeQty = soeQty
            )

            if (!isMaster) {
                val masterSession = activeSessions[NativeUltraGramielConfig.MASTER_SLOT_KEY]
                val masterCell = masterSession?.playerState?.cell ?: CELL_ENTER
                val masterPad = masterSession?.playerState?.pad ?: PAD_SPAWN
                val masterMap = masterSession?.playerState?.mapName ?: ""
                val isDifferentMap =
                    masterMap.isNotBlank() && !currentMap.equals(masterMap, ignoreCase = true)
                val isDifferentCell = !masterCell.equals(currentCell, ignoreCase = true)

                if (isDifferentMap || isDifferentCell) {
                    if (!isDifferentMap) {
                        session.map.jumpCell(masterCell, masterPad)
                    } else {
                        session.map.gotoPlayer(masterUsername)
                        delay(1200.milliseconds)
                    }
                    continue
                }
            }

            if (targetMon != null) {
                if (doTaunt && tauntTargetMonId != null) {
                    if (soeQty <= 0) {
                        session.log("Ran out of Scroll of Enrage (SoE)!", LogEntryType.ERROR)
                        stop()
                        break
                    }

                    if (session.combat.canUseSkill(5)) {
                        val monToTaunt =
                            aliveMonsters.firstOrNull { it.monMapId == tauntTargetMonId }
                                ?: targetMon
                        session.log(
                            "Executing Taunt on Monster ID $tauntTargetMonId (${monToTaunt.name})!",
                            LogEntryType.INFO
                        )
                        if (session.combat.taunt(tauntTargetMonId)) {
                            doTaunt = false
                            tauntTargetMonId = null
                            delay(200.milliseconds)
                            continue
                        }
                    }
                }

                val rawSkill = DEFAULT_SKILL_ROTATION[skillIdx]
                skillIdx = (skillIdx + 1) % DEFAULT_SKILL_ROTATION.size

                val nextSkill = resolveHealSkillConditional(
                    session = session,
                    slotKey = slotKey,
                    slotConfig = slotConfig,
                    requestedSkillIndex = rawSkill
                )

                session.combat.useSkill(rawSkill, targetMon.monMapId)
            }

            delay(200.milliseconds)
        }
    }

    private fun resolveHealSkillConditional(
        session: AqwSession,
        slotKey: String,
        slotConfig: SlotConfig,
        requestedSkillIndex: Int
    ): Int {
        val className = slotConfig.charClass

        val isLightCasterHeal = requestedSkillIndex == 3 &&
                (className.contains("LightCaster", ignoreCase = true) ||
                        session.playerState.inventory.any {
                            it.isEquipped && it.name.contains(
                                "LightCaster",
                                ignoreCase = true
                            )
                        })

        val isArchPaladinHeal = requestedSkillIndex == 2 &&
                (className.contains("ArchPaladin", ignoreCase = true) ||
                        session.playerState.inventory.any {
                            it.isEquipped && it.name.contains(
                                "ArchPaladin",
                                ignoreCase = true
                            )
                        })

        if (!isLightCasterHeal && !isArchPaladinHeal) {
            return requestedSkillIndex
        }

        val partyMemberNeedsHeal = activeSessions.values.any { slotSession ->
            val p = slotSession.playerState
            p.maxHp > 0 && !p.isDead && (p.currentHp.toFloat() / p.maxHp.toFloat()) < 0.70f
        }

        if (partyMemberNeedsHeal) {
            return requestedSkillIndex
        }

        val fallbackSkills = if (isLightCasterHeal) listOf(1, 2, 4, 0) else listOf(1, 3, 4, 0)
        return fallbackSkills.firstOrNull { session.combat.canUseSkill(it) } ?: 0
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
        val isPending = pendingTauntTargets.containsKey(slotKey)
        val nextWave = shatteringCount.get() + 1
        val isMon1Alive = isMonsterAlive(MONSTER_ID_GRAMIEL)
        val isMon2Alive = isMonsterAlive(MONSTER_ID_SLOT_1_2)
        val isMon3Alive = isMonsterAlive(MONSTER_ID_SLOT_3_4)
        val isOnlyGramielAlive = !isMon2Alive && !isMon3Alive && isMon1Alive

        val isNext = if (isOnlyGramielAlive) {
            session.combat.canUseSkill(5)
        } else if (nextWave % 2 != 0) {
            (slotKey == "slot1" && isMon2Alive) || (slotKey == "slot3" && isMon3Alive)
        } else {
            (slotKey == "slot2" && isMon2Alive) || (slotKey == "slot4" && isMon3Alive)
        }

        _status.update { current ->
            val prev = current[slotKey] ?: SlotTelemetry()
            current + (slotKey to prev.copy(
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
