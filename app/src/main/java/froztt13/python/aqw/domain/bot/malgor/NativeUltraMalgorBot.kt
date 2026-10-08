package froztt13.python.aqw.domain.bot.malgor

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

object NativeUltraMalgorBot : BasePartyCoordinator("NativeUltraMalgorBot") {

    @Suppress("PropertyName")
    val Config = NativeUltraMalgorConfig

    private const val TAG = "NativeUltraMalgorBot"

    // Internal Combat Constants
    private const val MAP_DUNGEON = "ultraspeaker"
    private const val MAP_ASSEMBLY = "yulgar"
    private const val CELL_ENTER = "Enter"
    private const val PAD_SPAWN = "Spawn"

    private const val TEMP_ITEM_MALGOR_VANQUISHED = "Ultra Speaker Vanquished"
    private const val TEMP_ITEM_MALGOR_VANQUISHED_ALT = "Malgor Vanquished"
    private const val ITEM_SCROLL_OF_ENRAGE = "Scroll of Enrage"
    private val DROP_WHITELIST =
        setOf("Malgor Insignia", "Speaker Insignia", "Stream of Consciousness")
    private val DEFAULT_SKILL_ROTATION = listOf(3, 0, 1, 0, 2, 0, 3, 0, 4)

    private const val MONSTER_ID_SPEAKER = "1"

    private const val ANIM_LISTEN = "listen"
    private const val ANIM_TRUTH = "truth"
    private const val AURA_MAGIA_BURN = "Magia Burn"

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
                    "🏆 Ultra Speaker / Malgor defeated! Completing Quest ID 9133...",
                    LogEntryType.INFO
                )

                for ((slotKey, session) in activeSessions) {
                    try {
                        session.log("[$slotKey] Completing Quest ID 9133...", LogEntryType.INFO)
                        session.quest.ensureTurnInQuest(9133)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed turning in quest 9133 for $slotKey: ${e.message}")
                    }
                }

                clearedRuns++
                _stats.update { it.copy(clearedCount = clearedRuns) }

                delay(1000.milliseconds)

                _isFinished.value = true
                logToAllSessions("=== Ultra Malgor Bot Completed ===", LogEntryType.INFO)
                stop()
            }
        }
    }

    private var currentConfig: UltraBossConfig? = null

    private val _animMsg = MutableStateFlow("")
    val animMsg: StateFlow<String> = _animMsg.asStateFlow()

    internal val calloutCount = AtomicInteger(0)
    internal var lastCalloutTime = 0L
    internal var latestAnimMsg: String = ""
    internal var latestAnimMsgTime: Long = 0L
    private val tauntLock = Any()
    internal val pendingTauntTargets = ConcurrentHashMap<String, String>()

    internal fun isSlotEligibleForTaunt(slotKey: String): Boolean {
        if (activeSessions.isEmpty()) return true
        val session = activeSessions[slotKey] ?: return true
        val p = session.playerState
        if (p.isDead || (p.maxHp > 0 && p.currentHp <= 0)) return false
        val hasMagiaBurn = p.auras.any { it.name.equals(AURA_MAGIA_BURN, ignoreCase = true) }
        return !hasMagiaBurn
    }

    internal fun onCalloutDetected(sourceSlot: String, triggerMsg: String) {
        synchronized(tauntLock) {
            val now = System.currentTimeMillis()
            if (now - lastCalloutTime <= 4000L) {
                return
            }
            lastCalloutTime = now
            val wave = calloutCount.incrementAndGet()

            val allSlots = NativeUltraMalgorConfig.ALL_SLOTS
            val initialIdx = (wave - 1) % allSlots.size

            var targetSlot: String? = null
            for (i in 0 until allSlots.size) {
                val candidate = allSlots[(initialIdx + i) % allSlots.size]
                if (isSlotEligibleForTaunt(candidate)) {
                    targetSlot = candidate
                    break
                }
            }

            if (targetSlot != null) {
                logToAllSessions(
                    "💥 Callout #$wave ('$triggerMsg') detected (from $sourceSlot)! [$targetSlot] Queuing taunt for Monster ID $MONSTER_ID_SPEAKER",
                    LogEntryType.INFO
                )
                queueTaunt(targetSlot, MONSTER_ID_SPEAKER)
            } else {
                logToAllSessions(
                    "💥 Callout #$wave detected, but all slots are dead or have '$AURA_MAGIA_BURN'!",
                    LogEntryType.WARNING
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
                    "[$slotKey] Queuing taunt for Monster ID $targetMonsterId (callout trigger)",
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
                "Pausing Ultra Malgor Party: all slots resting...",
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
            logToAllSessions("=== Ultra Malgor Party PAUSED ===")
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
                NativeUltraMalgorConfig.getDefaultTarget(slotKey, slotConf?.defaultTarget)
            updateTelemetry(
                slotKey = slotKey,
                session = session,
                targetMonsters = defaultTarget,
                isRunning = true
            )
        }
        logToAllSessions("=== Ultra Malgor Party RESUMED ===")
    }

    fun start(config: UltraBossConfig): Pair<Boolean, String?> {
        if (isRunning) {
            return Pair(false, "Ultra Malgor Party is already running!")
        }

        val slots = config.slots
        val masterSlot = slots[NativeUltraMalgorConfig.MASTER_SLOT_KEY]
        if (masterSlot == null || masterSlot.username.isBlank() || masterSlot.password.isBlank()) {
            return Pair(
                false,
                "Master account (${NativeUltraMalgorConfig.MASTER_SLOT_KEY}) credentials must be filled."
            )
        }

        val filledSlots =
            slots.filter { it.value.username.isNotBlank() && it.value.password.isNotBlank() }
        if (filledSlots.size < NativeUltraMalgorConfig.ALL_SLOTS.size) {
            return Pair(
                false,
                "Please configure credentials for all ${NativeUltraMalgorConfig.ALL_SLOTS.size} slots."
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
        calloutCount.set(0)
        lastCalloutTime = 0L
        pendingTauntTargets.clear()
        latestAnimMsg = ""
        latestAnimMsgTime = 0L
        _animMsg.value = ""

        val initialStatuses = mutableMapOf<String, SlotTelemetry>()
        for (key in NativeUltraMalgorConfig.ALL_SLOTS) {
            initialStatuses[key] = SlotTelemetry(
                running = true,
                isNextTaunter = key == "slot1"
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
        logToAllSessions("=== Ultra Malgor Party stopped by user ===")
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
        val server = config.server.ifBlank { NativeUltraMalgorConfig.DEFAULT_SERVER }
        val masterSlot = config.slots[NativeUltraMalgorConfig.MASTER_SLOT_KEY] ?: return
        val masterUsername = masterSlot.username.trim()

        val slaveSlots = NativeUltraMalgorConfig.SLAVE_SLOT_KEYS.mapNotNull { key ->
            config.slots[key]?.takeIf { it.username.isNotBlank() }
        }
        val slaveUsernames = slaveSlots.map { it.username.trim() }
        val slotJobs = mutableListOf<Job>()

        startTimer()

        try {
            for (slotKey in NativeUltraMalgorConfig.ALL_SLOTS) {
                val slotConf = config.slots[slotKey] ?: continue
                val isMaster = NativeUltraMalgorConfig.isMasterSlot(slotKey)
                val defaultTarget =
                    NativeUltraMalgorConfig.getDefaultTarget(slotKey, slotConf.defaultTarget)

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
            Log.e(TAG, "Error in Ultra Malgor Party run: ${e.message}", e)
            logToAllSessions("Error in Ultra Malgor Party run: ${e.message}", LogEntryType.ERROR)
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
                            val name = added.name
                            if (name.equals(TEMP_ITEM_MALGOR_VANQUISHED, ignoreCase = true) ||
                                name.equals(TEMP_ITEM_MALGOR_VANQUISHED_ALT, ignoreCase = true) ||
                                (name.contains(
                                    "Malgor",
                                    ignoreCase = true
                                ) && name.contains("Vanquished", ignoreCase = true)) ||
                                (name.contains(
                                    "Speaker",
                                    ignoreCase = true
                                ) && name.contains("Vanquished", ignoreCase = true))
                            ) {
                                session.log(
                                    "Received temp item '$name' - Ultra Speaker defeated!",
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
            slotConfig.charClass.ifBlank { NativeUltraMalgorConfig.getDefaultClass(slotKey) }
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
            it.name.contains("Vanquished", ignoreCase = true)
        }
        session.log("Accepting Quest ID 9133...", LogEntryType.INFO)
        session.quest.ensureAcceptQuest(9133)

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
        val roomNumber = currentConfig?.roomNumber ?: NativeUltraMalgorConfig.DEFAULT_ROOM_NUMBER

        if (isMaster) {
            session.log("Master joining $MAP_ASSEMBLY to assemble party...", LogEntryType.INFO)
            session.map.joinMap(MAP_ASSEMBLY, 999999)
            delay(3500.milliseconds)

            session.log("Waiting for party members to be online...", LogEntryType.INFO)
            var partyWait = 0
            while (activeSessions.size < NativeUltraMalgorConfig.ALL_SLOTS.size && partyWait < 60 && !stopRequested) {
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
        val roomNumber = currentConfig?.roomNumber ?: NativeUltraMalgorConfig.DEFAULT_ROOM_NUMBER

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
                session.log("Moving to $bossMap room Boss...", LogEntryType.INFO)
                session.map.jumpCell("Boss", "Left")
                delay(800.milliseconds)
            }

            if (session.playerState.tx != 50 || session.playerState.ty != 50) {
                session.map.walkTo(50, 50)
                delay(200.milliseconds)
            }

            val aliveMonsters = session.map.getMonsters(currentCell)

            val targetMon = aliveMonsters.firstOrNull { it.monMapId == MONSTER_ID_SPEAKER }
                ?: aliveMonsters.firstOrNull()

            var tauntTargetMonId: String? = null

            // Direct 4-slot taunt loop: taunt target monster whenever Skill 5 is ready and slot is eligible
            if (session.combat.canUseSkill(5) && isSlotEligibleForTaunt(slotKey)) {
                doTaunt = true
                tauntTargetMonId = targetMon?.monMapId ?: MONSTER_ID_SPEAKER
            } else {
                val queuedTaunt = pendingTauntTargets.remove(slotKey)
                if (queuedTaunt != null && isSlotEligibleForTaunt(slotKey)) {
                    val tauntMon =
                        aliveMonsters.firstOrNull { it.monMapId == queuedTaunt } ?: targetMon
                    if (tauntMon != null) {
                        doTaunt = true
                        tauntTargetMonId = tauntMon.monMapId
                    } else {
                        doTaunt = false
                    }
                } else {
                    doTaunt = false
                }
            }

            val activeTargetMonId = targetMon?.monMapId ?: MONSTER_ID_SPEAKER

            val soeQty = session.playerState.getScrollOfEnrageCount()
            updateTelemetry(
                slotKey,
                session,
                "Monster ID $activeTargetMonId",
                isRunning = true,
                soeQty = soeQty
            )

            if (!isMaster) {
                val masterSession = activeSessions[NativeUltraMalgorConfig.MASTER_SLOT_KEY]
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

                session.combat.useSkill(nextSkill, targetMon.monMapId)
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
        val isEligible = isSlotEligibleForTaunt(slotKey)
        val isNext = isEligible && session.combat.canUseSkill(5)

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
