package froztt13.python.aqw.domain.bot.temple

import android.util.Log
import froztt13.python.aqw.data.engine.AqwEvent
import froztt13.python.aqw.data.engine.AqwSession
import froztt13.python.aqw.data.model.LogEntryType
import froztt13.python.aqw.data.model.MonsterTelemetry
import froztt13.python.aqw.data.model.PartyStats
import froztt13.python.aqw.data.model.SlotConfig
import froztt13.python.aqw.data.model.SlotTelemetry
import froztt13.python.aqw.data.model.TempleConfig
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

object NativeTempleBot : BasePartyCoordinator("NativeTempleBot") {

    @Suppress("PropertyName")
    val Config = NativeTempleConfig

    private const val TAG = "NativeTempleBot"

    // Internal Combat & Map Constants
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
        setOf("Fragment of Midnight", "Fragment of Sunlight", "Ecliptic Offering")
    private val DEFAULT_SKILL_ROTATION = listOf(0, 1, 2, 0, 3, 4)

    private const val AURA_SUNS_WARMTH = "Sun's Warmth"
    private const val AURA_SUNS_HEAT = "Sun's Heat"

    private const val MONSTER_DYING_LIGHT = "Dying Light"
    private const val MONSTER_DAWN_KNIGHT = "Dawn Knight"
    private const val MONSTER_LUNAR_HAZE = "Lunar Haze"

    private val tauntCoordinator = NativeTauntCoordinator()

    private const val ANIM_MSG_CLEAR_DELAY_MS = 4000L
    private const val ANIM_MSG_EXPIRY_THRESHOLD_MS = 3900L

    private val _latestAnimMsg = MutableStateFlow("")
    val latestAnimMsg: StateFlow<String> = _latestAnimMsg.asStateFlow()
    private var latestAnimMsgTime = 0L
    private var animClearJob: Job? = null
    private val animLock = Any()

    private var currentConfig: TempleConfig? = null

    fun pause() {
        if (!isRunning || _isPaused.value) return
        _isPaused.value = true
        pausedAtMillis = System.currentTimeMillis()

        scope.launch(Dispatchers.IO) {
            logToAllSessions(
                "Pausing Temple Party: all slots leaving combat and jumping to current cell...",
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
                    isRunning = true
                )
            }
            logToAllSessions("=== Temple Shrine Party PAUSED ===", LogEntryType.WARNING)
        }
    }

    fun resume() {
        if (!isRunning || !_isPaused.value) return
        if (pausedAtMillis > 0) {
            startTimeMillis += (System.currentTimeMillis() - pausedAtMillis)
            pausedAtMillis = 0L
        }
        _isPaused.value = false
        val dungeonMap = NativeTempleConfig.getDungeonMap(
            currentConfig?.templeBotType ?: NativeTempleConfig.DEFAULT_BOT_TYPE
        )
        val defaultTargetMonsters = NativeTempleConfig.getDefaultTargetMonsters(dungeonMap)
        for ((slotKey, session) in activeSessions) {
            session.quest.triggerAutoQuestCheck()
            val slotConf = currentConfig?.slots?.get(slotKey)
            val defaultTarget =
                slotConf?.defaultTarget?.ifBlank { defaultTargetMonsters } ?: defaultTargetMonsters
            updateTelemetry(
                slotKey = slotKey,
                session = session,
                targetMonsters = defaultTarget,
                isRunning = true
            )
        }
        logToAllSessions("=== Temple Shrine Party RESUMED ===", LogEntryType.INFO)
    }

    fun start(config: TempleConfig): Pair<Boolean, String?> {
        if (isRunning) {
            return Pair(false, "Temple Shrine Party is already running!")
        }

        val slots = config.slots
        val masterSlot = slots[NativeTempleConfig.MASTER_SLOT_KEY]
        if (masterSlot == null || masterSlot.username.isBlank() || masterSlot.password.isBlank()) {
            return Pair(
                false,
                "Master account (${NativeTempleConfig.MASTER_SLOT_KEY}) credentials must be filled."
            )
        }

        val filledSlots =
            slots.filter { it.value.username.isNotBlank() && it.value.password.isNotBlank() }
        if (filledSlots.size < NativeTempleConfig.ALL_SLOTS.size) {
            return Pair(
                false,
                "Please configure credentials for all ${NativeTempleConfig.ALL_SLOTS.size} slots."
            )
        }

        currentConfig = config
        stopRequested = false
        _isPaused.value = false
        pausedAtMillis = 0L
        startTimeMillis = System.currentTimeMillis()
        clearedRuns = 0
        tauntCoordinator.reset()
        _latestAnimMsg.value = ""
        latestAnimMsgTime = 0L
        animClearJob?.cancel()
        animClearJob = null

        val initialStatuses = mutableMapOf<String, SlotTelemetry>()
        for (key in NativeTempleConfig.ALL_SLOTS) {
            initialStatuses[key] = SlotTelemetry(running = true)
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
        logToAllSessions("=== Temple Shrine Party stopped by user ===", LogEntryType.INFO)
        stopAllSessions()
        coordinatorJob?.cancel()
        _latestAnimMsg.value = ""
        latestAnimMsgTime = 0L
        animClearJob?.cancel()
        animClearJob = null

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

    private suspend fun waitForSlavesInCell(targetCell: String, maxWaitMs: Long = 4000L) {
        val start = System.currentTimeMillis()
        val slaveKeys = NativeTempleConfig.SLAVE_SLOT_KEYS
        while (!stopRequested && (System.currentTimeMillis() - start) < maxWaitMs) {
            if (_isPaused.value) {
                delay(500.milliseconds)
                continue
            }
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

    private suspend fun runParty(config: TempleConfig) {
        val server = config.server.ifBlank { NativeTempleConfig.DEFAULT_SERVER }
        val botType = config.templeBotType
        val dungeonMap = NativeTempleConfig.getDungeonMap(botType)
        val defaultTargetMonsters = NativeTempleConfig.getDefaultTargetMonsters(dungeonMap)
        val dropWhitelist = DROP_WHITELIST

        val masterSlot = config.slots[NativeTempleConfig.MASTER_SLOT_KEY] ?: return
        val masterUsername = masterSlot.username.trim()

        val slaveSlots = NativeTempleConfig.SLAVE_SLOT_KEYS.mapNotNull { key ->
            config.slots[key]?.takeIf { it.username.isNotBlank() }
        }
        val slaveUsernames = slaveSlots.map { it.username.trim() }

        val slotJobs = mutableListOf<Job>()

        // Launch telemetry timer loop
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
            // Launch each slot
            for (slotKey in NativeTempleConfig.ALL_SLOTS) {
                val slotConf = config.slots[slotKey] ?: continue
                val isMaster = NativeTempleConfig.isMasterSlot(slotKey)
                val defaultTarget =
                    NativeTempleConfig.getDefaultTarget(slotKey, slotConf.defaultTarget, botType)
                val job = scope.launch(Dispatchers.IO) {
                    runSlotWorker(
                        slotKey = slotKey,
                        slotConfig = slotConf,
                        isMaster = isMaster,
                        server = server,
                        dungeonMap = dungeonMap,
                        masterUsername = masterUsername,
                        slaveUsernames = slaveUsernames,
                        defaultTargetMonsters = defaultTarget,
                        dropWhitelist = dropWhitelist
                    )
                }
                slotJobs.add(job)
            }

            slotJobs.joinAll()
        } catch (e: Exception) {
            Log.e(TAG, "Error in Temple Party run: ${e.message}", e)
            logToAllSessions("Error in Temple Party run: ${e.message}", LogEntryType.ERROR)
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
        defaultTargetMonsters: String,
        dropWhitelist: Set<String>
    ) {
        val username = slotConfig.username.trim()
        val isTaunter = slotConfig.isTaunter
        val targetMonsters = slotConfig.defaultTarget.ifBlank { defaultTargetMonsters }

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

        val doTauntRef = AtomicBoolean(false)
        val targetMonstersOverrideRef = AtomicReference<String?>(null)

        val eventJob = scope.launch(Dispatchers.IO) {
            session.events.collect { event ->
                if (_isPaused.value) return@collect
                when (event) {
                    is AqwEvent.CombatTick -> {
                        if (event.animMsgs.isNotEmpty()) {
                            val combined = event.animMsgs.joinToString(" | ")
                            _latestAnimMsg.value = combined
                            latestAnimMsgTime = System.currentTimeMillis()

                            animClearJob?.cancel()
                            animClearJob = scope.launch(Dispatchers.IO) {
                                delay(ANIM_MSG_CLEAR_DELAY_MS.milliseconds)
                                synchronized(animLock) {
                                    if (System.currentTimeMillis() - latestAnimMsgTime >= ANIM_MSG_EXPIRY_THRESHOLD_MS) {
                                        _latestAnimMsg.value = ""
                                        latestAnimMsgTime = 0L
                                    }
                                }
                            }
                        }

                        // Check anims messages for taunt cues
                        for (msg in event.animMsgs) {
                            val lower = msg.lowercase()
                            if (lower.contains("gather")) {
                                targetMonstersOverrideRef.set(MONSTER_DYING_LIGHT)
                                doTauntRef.set(true)
                            } else if (lower.contains("converges")) {
                                doTauntRef.set(true)
                            }
                        }

                        // Check auras on player
                        for ((aura, targetInf) in event.auras) {
                            if (targetInf.contains(session.playerState.roomUserId.toString()) || targetInf.contains(
                                    username,
                                    ignoreCase = true
                                )
                            ) {
                                if (aura.name.equals(AURA_SUNS_WARMTH, ignoreCase = true)) {
                                    scope.launch(Dispatchers.IO) {
                                        delay(5000.milliseconds)
                                        targetMonstersOverrideRef.set(MONSTER_DAWN_KNIGHT)
                                        doTauntRef.set(true)
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

                    else -> {}
                }
            }
        }

        try {
            if (!preparingCharacter(session, slotKey, slotConfig, server, targetMonsters)) return
            if (!preparingParty(
                    session,
                    isMaster,
                    masterUsername,
                    slaveUsernames,
                    dungeonMap
                )
            ) return
            doCombat(
                session = session,
                slotKey = slotKey,
                isMaster = isMaster,
                isTaunter = isTaunter,
                username = username,
                masterUsername = masterUsername,
                dungeonMap = dungeonMap,
                targetMonsters = targetMonsters,
                doTauntRef = doTauntRef,
                targetMonstersOverrideRef = targetMonstersOverrideRef
            )
        } catch (e: Exception) {
            session.log("Worker error: ${e.message}", LogEntryType.ERROR)
        } finally {
            eventJob.cancel()
            logJob.cancel()
            session.stop()
            activeSessions.remove(slotKey)
            if (isTaunter) tauntCoordinator.unregisterTaunter(username)
            updateTelemetry(slotKey, session, targetMonsters, isRunning = false)
        }
    }

    private suspend fun preparingCharacter(
        session: AqwSession,
        slotKey: String,
        slotConfig: SlotConfig,
        server: String,
        targetMonsters: String
    ): Boolean {
        val username = slotConfig.username.trim()
        val password = slotConfig.password.trim()
        val isTaunter = slotConfig.isTaunter

        session.log("[$slotKey] Logging in to $server...", LogEntryType.INFO)
        val connected = session.start(
            username = username,
            password = password,
            preferredServer = server
        )

        if (!connected) {
            session.log("[$slotKey] Failed to connect / login.", LogEntryType.ERROR)
            updateTelemetry(slotKey, session, targetMonsters, isRunning = false)
            return false
        }

        // Wait for inventory and character to load
        var loadWait = 0
        while (!session.isCharLoaded.value && loadWait < 150 && !stopRequested) {
            delay(100.milliseconds)
            loadWait++
        }

        if (!session.isCharLoaded.value) {
            session.log("[$slotKey] Character load timed out.", LogEntryType.ERROR)
            return false
        }

        // Equip farm class if specified
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
        if (isTaunter) {
            val soeItem = session.playerState.inventory.firstOrNull {
                it.name.equals(ITEM_SCROLL_OF_ENRAGE, ignoreCase = true)
            }
            val soeQty = soeItem?.qty ?: 0
            if (soeQty <= 0) {
                val err =
                    "Taunter '$username' does not have $ITEM_SCROLL_OF_ENRAGE (SoE). Minimum 1 $ITEM_SCROLL_OF_ENRAGE is required."
                session.log(err, LogEntryType.ERROR)
                stop()
                return false
            }
            session.log(
                "Equipping $ITEM_SCROLL_OF_ENRAGE (Qty: $soeQty)...",
                LogEntryType.INFO
            )
            session.item.equipScroll(soeItem!!.itemId, soeItem.sMeta)
            tauntCoordinator.registerTaunter(username)
            delay(1500.milliseconds)
        }

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
            // Master: Join yulgar, wait for party, invite slaves, queue dungeon
            session.log(
                "Master joining $MAP_ASSEMBLY-$MAP_ASSEMBLY_ROOM to assemble party...",
                LogEntryType.INFO
            )
            session.map.joinMap(MAP_ASSEMBLY, MAP_ASSEMBLY_ROOM)
            delay(3500.milliseconds)

            // Wait until all slave sessions are connected
            session.log(
                "Waiting for party members to be online...",
                LogEntryType.INFO
            )
            var partyWait = 0
            while (activeSessions.size < NativeTempleConfig.ALL_SLOTS.size && partyWait < 60 && !stopRequested) {
                delay(500.milliseconds)
                partyWait++
            }

            if (stopRequested) return false

            // Send party invites
            for (slaveName in slaveUsernames) {
                session.log(
                    "Sending party invite to $slaveName...",
                    LogEntryType.INFO
                )
                session.social.partyInvite(slaveName)
                delay(600.milliseconds)
            }

            delay(1000.milliseconds)
            session.log("Queueing dungeon '$dungeonMap'...", LogEntryType.INFO)
            session.social.dungeonQueue(dungeonMap)
        } else {
            // Slave: wait for party invite and accept
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

    private suspend fun doCombat(
        session: AqwSession,
        slotKey: String,
        isMaster: Boolean,
        isTaunter: Boolean,
        username: String,
        masterUsername: String,
        dungeonMap: String,
        targetMonsters: String,
        doTauntRef: AtomicBoolean,
        targetMonstersOverrideRef: AtomicReference<String?>
    ) {
        val skillRotation = DEFAULT_SKILL_ROTATION
        var skillIdx = 0
        var isAttacking = false

        while (scope.isActive && !stopRequested && session.isConnected.value) {
            val soeItemNow = session.playerState.inventory.firstOrNull {
                it.name.equals(ITEM_SCROLL_OF_ENRAGE, ignoreCase = true)
            }
            val soeQty = soeItemNow?.qty ?: 0

            if (_isPaused.value) {
                updateTelemetry(
                    slotKey,
                    session,
                    "PAUSED",
                    isRunning = true,
                    soeQty = soeQty
                )
                delay(500.milliseconds)
                continue
            }

            if (session.playerState.isDead) {
                delay(500.milliseconds)
                continue
            }

            val currentCell = session.playerState.cell
            val currentMap = session.playerState.mapName
            val currentOverride = targetMonstersOverrideRef.get()

            // Update Telemetry
            updateTelemetry(
                slotKey,
                session,
                currentOverride ?: targetMonsters,
                isRunning = true,
                soeQty = soeQty
            )

            if (isMaster) {
                // Cell progression for master
                val hasMonsters = session.map.hasAliveMonsters(currentCell)
                if (!hasMonsters && currentMap.contains(dungeonMap, ignoreCase = true)) {
                    when (currentCell) {
                        CELL_ENTER -> {
                            session.log(
                                "Cell cleared. Moving to $CELL_R1...",
                                LogEntryType.INFO
                            )
                            session.map.jumpCell(CELL_R1, PAD_LEFT)
                            delay(1200.milliseconds)
                            waitForSlavesInCell(CELL_R1)
                        }

                        CELL_R1 -> {
                            session.log(
                                "Cell cleared. Moving to $CELL_R2...",
                                LogEntryType.INFO
                            )
                            session.map.jumpCell(CELL_R2, PAD_LEFT)
                            delay(1200.milliseconds)
                            waitForSlavesInCell(CELL_R2)
                        }

                        CELL_R2 -> {
                            session.log(
                                "Cell cleared. Moving to $CELL_R3...",
                                LogEntryType.INFO
                            )
                            session.map.jumpCell(CELL_R3, PAD_LEFT)
                            delay(1200.milliseconds)
                            waitForSlavesInCell(CELL_R3)
                        }

                        CELL_R3 -> {
                            clearedRuns++
                            session.log(
                                "=== Dungeon cleared $clearedRuns times! ===",
                                LogEntryType.INFO
                            )
                            session.social.sendChat("Dungeon cleared $clearedRuns times.")
                            delay(1000.milliseconds)
                            session.map.joinMap(MAP_RESET, MAP_RESET_ROOM)
                            delay(2500.milliseconds)
                            session.social.dungeonQueue(dungeonMap)
                            delay(2000.milliseconds)
                        }
                    }
                }
            } else {
                // Slave: follow master if not in the same cell or map
                val masterSession = activeSessions[NativeTempleConfig.MASTER_SLOT_KEY]
                val masterCell = masterSession?.playerState?.cell
                val masterMap = masterSession?.playerState?.mapName ?: ""
                val isDifferentMap =
                    masterMap.isNotBlank() && !currentMap.equals(masterMap, ignoreCase = true)
                val isDifferentCell =
                    masterCell != null && !masterCell.equals(currentCell, ignoreCase = true)

                if (isDifferentMap || isDifferentCell) {
                    session.log(
                        "[$slotKey] Master is in $masterMap:$masterCell (current: $currentMap:$currentCell). Moving to master...",
                        LogEntryType.INFO
                    )
                    val inDungeonMap = currentMap.contains(dungeonMap, ignoreCase = true) ||
                            masterMap.contains(dungeonMap, ignoreCase = true)

                    if (inDungeonMap && masterCell != null) {
                        val masterPad = masterSession.playerState.pad.ifBlank { PAD_LEFT }
                        session.map.jumpCell(masterCell, masterPad)
                    } else if (!isDifferentMap && masterCell != null) {
                        val masterPad = masterSession.playerState.pad.ifBlank { PAD_LEFT }
                        session.map.jumpCell(masterCell, masterPad)
                    } else {
                        session.map.gotoPlayer(masterUsername)
                    }
                    delay(1200.milliseconds)
                    continue
                }
            }

            // Attack Monsters in Current Cell
            val aliveMonsters = session.map.getMonsters(currentCell)
            if (aliveMonsters.isNotEmpty()) {
                if (!isAttacking) {
                    isAttacking = true
                }

                // Determine target monster
                val activeTargetStr = currentOverride ?: targetMonsters
                val prioritized = activeTargetStr.split(",").map { it.trim().lowercase() }
                val targetMonster = aliveMonsters.firstOrNull { mon ->
                    prioritized.any { p -> mon.name.lowercase().contains(p) }
                } ?: aliveMonsters.first()

                // Taunt handling
                val doTaunt = doTauntRef.get()
                if (doTaunt && isTaunter) {
                    if (soeQty <= 0) {
                        session.log(
                            "Ran out of $ITEM_SCROLL_OF_ENRAGE (SoE)!",
                            LogEntryType.ERROR
                        )
                        stop()
                        break
                    }

                    if (tauntCoordinator.requestTaunt(username)) {
                        session.log(
                            "Executing Taunt on ${targetMonster.name}!",
                            LogEntryType.INFO
                        )
                        session.combat.taunt(targetMonster.monMapId)
                        doTauntRef.set(false)
                        targetMonstersOverrideRef.set(null)
                        delay(400.milliseconds)
                        continue
                    } else {
                        doTauntRef.set(false)
                        targetMonstersOverrideRef.set(null)
                    }
                }

                // Skill usage
                val nextSkill = skillRotation[skillIdx]
                skillIdx = (skillIdx + 1) % skillRotation.size

                // Check inverted damage debuff "Sun's Heat"
                val hasSunsHeat = session.playerState.hasAura(AURA_SUNS_HEAT)
                if (hasSunsHeat && (nextSkill == 2 || nextSkill == 3)) {
                    // Skip heal skills when debuffed with Sun's Heat
                    session.combat.attack(targetMonster.monMapId)
                } else if (nextSkill == 0) {
                    session.combat.attack(targetMonster.monMapId)
                } else {
                    session.combat.useSkill(nextSkill, targetMonster.monMapId)
                }
            } else {
                isAttacking = false
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

        _status.update { current ->
            val mutable = current.toMutableMap()
            mutable[slotKey] = SlotTelemetry(
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
