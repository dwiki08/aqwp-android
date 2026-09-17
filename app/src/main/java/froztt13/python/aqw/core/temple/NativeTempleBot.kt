package froztt13.python.aqw.core.temple

import android.util.Log
import froztt13.python.aqw.core.engine.AqwEvent
import froztt13.python.aqw.core.engine.AqwSession
import froztt13.python.aqw.data.MonsterTelemetry
import froztt13.python.aqw.data.PartyStats
import froztt13.python.aqw.data.SlotConfig
import froztt13.python.aqw.data.SlotTelemetry
import froztt13.python.aqw.data.TempleConfig
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
import kotlin.time.Duration.Companion.milliseconds

object NativeTempleBot {

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

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var coordinatorJob: Job? = null
    private val activeSessions = ConcurrentHashMap<String, AqwSession>()

    private val tauntCoordinator = NativeTauntCoordinator()

    private val _status = MutableStateFlow<Map<String, SlotTelemetry>>(emptyMap())
    val status: StateFlow<Map<String, SlotTelemetry>> = _status.asStateFlow()

    private val _stats = MutableStateFlow(PartyStats())
    val stats: StateFlow<PartyStats> = _stats.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private var stopRequested = false
    private var startTimeMillis = 0L
    private var pausedAtMillis = 0L
    private var clearedRuns = 0
    private var currentConfig: TempleConfig? = null

    val isRunning: Boolean
        get() = _status.value.values.any { it.running }

    fun pause() {
        if (!isRunning || _isPaused.value) return
        _isPaused.value = true
        pausedAtMillis = System.currentTimeMillis()

        scope.launch(Dispatchers.IO) {
            BotHelper.dispatchLog(
                "temple",
                "System",
                "Pausing Temple Party: all slots leaving combat and jumping to current cell..."
            )
            val jobs = activeSessions.map { (slotKey, session) ->
                launch {
                    try {
                        val currentCell = session.playerState.cell.ifBlank { CELL_ENTER }
                        val currentPad = session.playerState.pad.ifBlank { PAD_SPAWN }
                        session.commands.jumpCell(currentCell, currentPad)
                        delay(200.milliseconds)
                        session.commands.rest()
                        session.playerState.isInCombat = false
                        BotHelper.dispatchLog(
                            "temple",
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
            BotHelper.dispatchLog("temple", "System", "=== Temple Shrine Party PAUSED ===")
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
            session.commands.quest.triggerAutoQuestCheck()
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
        BotHelper.dispatchLog("temple", "System", "=== Temple Shrine Party RESUMED ===")
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
        for ((_, session) in activeSessions) {
            try {
                session.stop()
            } catch (_: Exception) {
            }
        }
        activeSessions.clear()
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
        BotHelper.dispatchLog("temple", "System", "=== Temple Shrine Party stopped by user ===")
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
            BotHelper.dispatchLog("temple", "System", "Error in Temple Party run: ${e.message}")
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
        val password = slotConfig.password.trim()
        val isTaunter = slotConfig.isTaunter
        val targetMonsters = slotConfig.defaultTarget.ifBlank { defaultTargetMonsters }

        val session = AqwSession()
        session.isPaused = { _isPaused.value }
        session.socketClient.tag = "$slotKey ($username)"
        activeSessions[slotKey] = session

        val cooldowns = ConcurrentHashMap<Int, Double>()
        for (i in 0..5) cooldowns[i] = 0.0

        var doTaunt = false
        var targetMonstersOverride: String? = null
        var isAttacking = false

        val eventJob = scope.launch(Dispatchers.IO) {
            session.events.collect { event ->
                if (_isPaused.value) return@collect
                when (event) {
                    is AqwEvent.CombatTick -> {
                        // Check anims messages for taunt cues
                        for (msg in event.animMsgs) {
                            val lower = msg.lowercase()
                            if (lower.contains("gather")) {
                                targetMonstersOverride = MONSTER_DYING_LIGHT
                                doTaunt = true
                            } else if (lower.contains("converges")) {
                                doTaunt = true
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
                                        targetMonstersOverride = MONSTER_DAWN_KNIGHT
                                        doTaunt = true
                                    }
                                }
                            }
                        }
                    }

                    is AqwEvent.ItemDropped -> {
                        if (dropWhitelist.any { it.equals(event.itemName, ignoreCase = true) }) {
                            BotHelper.dispatchLog(
                                "temple",
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
            BotHelper.dispatchLog("temple", username, "[$slotKey] Logging in to $server...")
            val connected = session.start(
                username = username,
                password = password,
                preferredServer = server,
                onLog = { msg -> BotHelper.dispatchLog("temple", username, msg) }
            )

            if (!connected) {
                BotHelper.dispatchLog("temple", username, "[$slotKey] Failed to connect / login.")
                updateTelemetry(slotKey, session, targetMonsters, isRunning = false)
                return
            }

            // Wait for inventory and character to load
            var loadWait = 0
            while (!session.isCharLoaded.value && loadWait < 150 && !stopRequested) {
                delay(100.milliseconds)
                loadWait++
            }

            if (!session.isCharLoaded.value) {
                BotHelper.dispatchLog("temple", username, "[$slotKey] Character load timed out.")
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

            // Prepare Scroll of Enrage if taunter
            var soeQty: Int
            if (isTaunter) {
                val soeItem = session.playerState.inventory.firstOrNull {
                    it.name.equals(ITEM_SCROLL_OF_ENRAGE, ignoreCase = true)
                }
                soeQty = soeItem?.qty ?: 0
                if (soeQty <= 0) {
                    val err =
                        "Taunter '$username' does not have $ITEM_SCROLL_OF_ENRAGE (SoE). Minimum 1 $ITEM_SCROLL_OF_ENRAGE is required."
                    BotHelper.dispatchLog("temple", username, err)
                    stop()
                    return
                }
                BotHelper.dispatchLog(
                    "temple",
                    username,
                    "Equipping $ITEM_SCROLL_OF_ENRAGE (Qty: $soeQty)..."
                )
                session.commands.equipScroll(soeItem!!.itemId, soeItem.sMeta)
                tauntCoordinator.registerTaunter(username)
                delay(1500.milliseconds)
            }

            if (isMaster) {
                // Master: Join yulgar, wait for party, invite slaves, queue dungeon
                BotHelper.dispatchLog(
                    "temple",
                    username,
                    "Master joining $MAP_ASSEMBLY-$MAP_ASSEMBLY_ROOM to assemble party..."
                )
                session.commands.joinMap(MAP_ASSEMBLY, MAP_ASSEMBLY_ROOM)
                delay(3500.milliseconds)

                // Wait until all slave sessions are connected
                BotHelper.dispatchLog(
                    "temple",
                    username,
                    "Waiting for party members to be online..."
                )
                var partyWait = 0
                while (activeSessions.size < NativeTempleConfig.ALL_SLOTS.size && partyWait < 60 && !stopRequested) {
                    delay(500.milliseconds)
                    partyWait++
                }

                // Send party invites
                for (slaveName in slaveUsernames) {
                    BotHelper.dispatchLog(
                        "temple",
                        username,
                        "Sending party invite to $slaveName..."
                    )
                    session.commands.partyInvite(slaveName)
                    delay(600.milliseconds)
                }

                delay(1000.milliseconds)
                BotHelper.dispatchLog("temple", username, "Queueing dungeon '$dungeonMap'...")
                session.commands.dungeonQueue(dungeonMap)
            } else {
                // Slave: wait for party invite and accept
                BotHelper.dispatchLog("temple", username, "Slave waiting for party invitation...")
                var inviteWait = 0
                while (session.latestPartyId == null && inviteWait < 120 && !stopRequested) {
                    session.commands.gotoPlayer(masterUsername)
                    delay(1000.milliseconds)
                    inviteWait++
                }

                val pid = session.latestPartyId
                if (pid != null) {
                    BotHelper.dispatchLog(
                        "temple",
                        username,
                        "Accepting party invite (PID: $pid)..."
                    )
                    session.commands.partyAccept(pid)
                    delay(1200.milliseconds)
                }
            }

            // Main Combat & Cell Navigation Loop
            val skillRotation = DEFAULT_SKILL_ROTATION
            var skillIdx = 0

            while (scope.isActive && !stopRequested && session.isConnected.value) {
                if (_isPaused.value) {
                    val soeItemNow = session.playerState.inventory.firstOrNull {
                        it.name.equals(ITEM_SCROLL_OF_ENRAGE, ignoreCase = true)
                    }
                    soeQty = soeItemNow?.qty ?: 0
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

                // Update Telemetry
                val soeItemNow = session.playerState.inventory.firstOrNull {
                    it.name.equals(ITEM_SCROLL_OF_ENRAGE, ignoreCase = true)
                }
                soeQty = soeItemNow?.qty ?: 0
                updateTelemetry(
                    slotKey,
                    session,
                    targetMonstersOverride ?: targetMonsters,
                    isRunning = true,
                    soeQty = soeQty
                )

                if (isMaster) {
                    // Cell progression for master
                    val hasMonsters = session.hasAliveMonsters(currentCell)
                    if (!hasMonsters && currentMap.contains(dungeonMap, ignoreCase = true)) {
                        when (currentCell) {
                            CELL_ENTER -> {
                                BotHelper.dispatchLog(
                                    "temple",
                                    username,
                                    "Cell cleared. Moving to $CELL_R1..."
                                )
                                session.commands.jumpCell(CELL_R1, PAD_LEFT)
                                delay(1200.milliseconds)
                                waitForSlavesInCell(CELL_R1)
                            }

                            CELL_R1 -> {
                                BotHelper.dispatchLog(
                                    "temple",
                                    username,
                                    "Cell cleared. Moving to $CELL_R2..."
                                )
                                session.commands.jumpCell(CELL_R2, PAD_LEFT)
                                delay(1200.milliseconds)
                                waitForSlavesInCell(CELL_R2)
                            }

                            CELL_R2 -> {
                                BotHelper.dispatchLog(
                                    "temple",
                                    username,
                                    "Cell cleared. Moving to $CELL_R3..."
                                )
                                session.commands.jumpCell(CELL_R3, PAD_LEFT)
                                delay(1200.milliseconds)
                                waitForSlavesInCell(CELL_R3)
                            }

                            CELL_R3 -> {
                                clearedRuns++
                                BotHelper.dispatchLog(
                                    "temple",
                                    username,
                                    "=== Dungeon cleared $clearedRuns times! ==="
                                )
                                session.commands.sendChat("Dungeon cleared $clearedRuns times.")
                                delay(1000.milliseconds)
                                session.commands.joinMap(MAP_RESET, MAP_RESET_ROOM)
                                delay(2500.milliseconds)
                                session.commands.dungeonQueue(dungeonMap)
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
                        BotHelper.dispatchLog(
                            "temple",
                            username,
                            "[$slotKey] Master is in $masterMap:$masterCell (current: $currentMap:$currentCell). Moving to master..."
                        )
                        val inDungeonMap = currentMap.contains(dungeonMap, ignoreCase = true) ||
                                masterMap.contains(dungeonMap, ignoreCase = true)

                        if (inDungeonMap && masterCell != null) {
                            val masterPad =
                                masterSession?.playerState?.pad?.ifBlank { PAD_LEFT } ?: PAD_LEFT
                            session.commands.jumpCell(masterCell, masterPad)
                        } else if (!isDifferentMap && masterCell != null) {
                            val masterPad =
                                masterSession?.playerState?.pad?.ifBlank { PAD_LEFT } ?: PAD_LEFT
                            session.commands.jumpCell(masterCell, masterPad)
                        } else {
                            session.commands.gotoPlayer(masterUsername)
                        }
                        delay(1200.milliseconds)
                        continue
                    }
                }

                // Attack Monsters in Current Cell
                val aliveMonsters =
                    session.getCellMonsters(currentCell).filter { it.isAlive && it.currentHp > 0 }
                if (aliveMonsters.isNotEmpty()) {
                    if (!isAttacking) {
                        isAttacking = true
                    }

                    // Determine target monster
                    val activeTargetStr = targetMonstersOverride ?: targetMonsters
                    val prioritized = activeTargetStr.split(",").map { it.trim().lowercase() }
                    val targetMonster = aliveMonsters.firstOrNull { mon ->
                        prioritized.any { p -> mon.name.lowercase().contains(p) }
                    } ?: aliveMonsters.first()

                    // Taunt handling
                    if (doTaunt && isTaunter) {
                        if (soeQty <= 0) {
                            BotHelper.dispatchLog(
                                "temple",
                                username,
                                "Ran out of $ITEM_SCROLL_OF_ENRAGE (SoE)!"
                            )
                            stop()
                            break
                        }

                        if (tauntCoordinator.requestTaunt(username)) {
                            BotHelper.dispatchLog(
                                "temple",
                                username,
                                "Executing Taunt on ${targetMonster.name}!"
                            )
                            session.commands.taunt(targetMonster.monMapId)
                            doTaunt = false
                            targetMonstersOverride = null
                            delay(400.milliseconds)
                            continue
                        } else {
                            doTaunt = false
                            targetMonstersOverride = null
                        }
                    }

                    // Skill usage
                    val nextSkill = skillRotation[skillIdx]
                    skillIdx = (skillIdx + 1) % skillRotation.size

                    // Check inverted damage debuff "Sun's Heat"
                    val hasSunsHeat = session.playerState.hasAura(AURA_SUNS_HEAT)
                    if (hasSunsHeat && (nextSkill == 2 || nextSkill == 3)) {
                        // Skip heal skills when debuffed with Sun's Heat
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
            BotHelper.dispatchLog("temple", username, "Worker error: ${e.message}")
        } finally {
            eventJob.cancel()
            session.stop()
            activeSessions.remove(slotKey)
            if (isTaunter) tauntCoordinator.unregisterTaunter(username)
            updateTelemetry(slotKey, session, targetMonsters, isRunning = false)
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
