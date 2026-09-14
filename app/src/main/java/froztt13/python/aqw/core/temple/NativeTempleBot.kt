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

    private const val TAG = "NativeTempleBot"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var coordinatorJob: Job? = null
    private val activeSessions = ConcurrentHashMap<String, AqwSession>()

    private val tauntCoordinator = NativeTauntCoordinator()

    private val _status = MutableStateFlow<Map<String, SlotTelemetry>>(emptyMap())
    val status: StateFlow<Map<String, SlotTelemetry>> = _status.asStateFlow()

    private val _stats = MutableStateFlow(PartyStats())
    val stats: StateFlow<PartyStats> = _stats.asStateFlow()

    private var stopRequested = false
    private var startTimeMillis = 0L
    private var clearedRuns = 0

    val isRunning: Boolean
        get() = _status.value.values.any { it.running }

    fun start(config: TempleConfig): Pair<Boolean, String?> {
        if (isRunning) {
            return Pair(false, "Temple Shrine Party is already running!")
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

        stopRequested = false
        startTimeMillis = System.currentTimeMillis()
        clearedRuns = 0
        tauntCoordinator.reset()

        val initialStatuses = mutableMapOf<String, SlotTelemetry>()
        for (key in listOf("slot1", "slot2", "slot3", "slot4")) {
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
        _stats.update { it.copy(timeRunning = (System.currentTimeMillis() - startTimeMillis) / 1000L) }
        BotHelper.dispatchLog("temple", "System", "=== Temple Shrine Party stopped by user ===")
    }

    private suspend fun runParty(config: TempleConfig) {
        val server = config.server.ifBlank { "Alteon" }
        val botType = config.templeBotType
        val dungeonMap = if (botType.equals(
                "SolsticeMoonBot",
                ignoreCase = true
            )
        ) "solsticemoon" else "midnightsun"
        val defaultTargetMonsters =
            if (dungeonMap == "solsticemoon") "Lunar Haze" else "Dying Light,Dawn Knight"
        val dropWhitelist =
            setOf("Fragment of Midnight", "Fragment of Sunlight", "Ecliptic Offering")

        val masterSlot = config.slots["slot1"] ?: return
        val masterUsername = masterSlot.username.trim()

        val slaveSlots = listOfNotNull(
            config.slots["slot2"]?.takeIf { it.username.isNotBlank() },
            config.slots["slot3"]?.takeIf { it.username.isNotBlank() },
            config.slots["slot4"]?.takeIf { it.username.isNotBlank() }
        )
        val slaveUsernames = slaveSlots.map { it.username.trim() }

        val slotJobs = mutableListOf<Job>()

        // Launch telemetry timer loop
        val statsTimerJob = scope.launch(Dispatchers.IO) {
            while (isActive && !stopRequested) {
                val elapsed = (System.currentTimeMillis() - startTimeMillis) / 1000L
                _stats.update { it.copy(timeRunning = elapsed, clearedCount = clearedRuns) }
                delay(1000.milliseconds)
            }
        }

        try {
            // Launch each slot
            for (slotKey in listOf("slot1", "slot2", "slot3", "slot4")) {
                val slotConf = config.slots[slotKey] ?: continue
                val isMaster = (slotKey == "slot1")
                val job = scope.launch(Dispatchers.IO) {
                    runSlotWorker(
                        slotKey = slotKey,
                        slotConfig = slotConf,
                        isMaster = isMaster,
                        server = server,
                        dungeonMap = dungeonMap,
                        masterUsername = masterUsername,
                        slaveUsernames = slaveUsernames,
                        defaultTargetMonsters = defaultTargetMonsters,
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
        activeSessions[slotKey] = session

        val cooldowns = ConcurrentHashMap<Int, Double>()
        for (i in 0..5) cooldowns[i] = 0.0

        var doTaunt = false
        var targetMonstersOverride: String? = null
        var isAttacking = false

        val eventJob = scope.launch(Dispatchers.IO) {
            session.events.collect { event ->
                when (event) {
                    is AqwEvent.CombatTick -> {
                        // Check anims messages for taunt cues
                        for (msg in event.animMsgs) {
                            val lower = msg.lowercase()
                            if (lower.contains("gather")) {
                                targetMonstersOverride = "Dying Light"
                                doTaunt = true
                            } else if (lower.contains("converges")) {
                                doTaunt = true
                            }
                        }

                        // Check auras on player
                        for ((auraName, targetInf) in event.auras) {
                            if (targetInf.contains(session.playerState.userId.toString()) || targetInf.contains(
                                    username,
                                    ignoreCase = true
                                )
                            ) {
                                if (auraName.equals("Sun's Warmth", ignoreCase = true)) {
                                    scope.launch(Dispatchers.IO) {
                                        delay(5000.milliseconds)
                                        targetMonstersOverride = "Dawn Knight"
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
                    it.name.equals("Scroll of Enrage", ignoreCase = true)
                }
                soeQty = soeItem?.qty ?: 0
                if (soeQty <= 0) {
                    val err =
                        "Taunter '$username' does not have Scroll of Enrage (SoE). Minimum 1 Scroll of Enrage is required."
                    BotHelper.dispatchLog("temple", username, err)
                    stop()
                    return
                }
                BotHelper.dispatchLog(
                    "temple",
                    username,
                    "Equipping Scroll of Enrage (Qty: $soeQty)..."
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
                    "Master joining yulgar-999999 to assemble party..."
                )
                session.commands.joinMap("yulgar", 999999)
                delay(3500.milliseconds)

                // Wait until all slave sessions are connected
                BotHelper.dispatchLog(
                    "temple",
                    username,
                    "Waiting for party members to be online..."
                )
                var partyWait = 0
                while (activeSessions.size < 4 && partyWait < 60 && !stopRequested) {
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
            val skillRotation = listOf(0, 1, 2, 0, 3, 4)
            var skillIdx = 0

            while (scope.isActive && !stopRequested && session.isConnected.value) {
                if (session.playerState.isDead) {
                    delay(500.milliseconds)
                    continue
                }

                val currentCell = session.playerState.cell
                val currentMap = session.playerState.mapName

                // Update Telemetry
                val soeItemNow = session.playerState.inventory.firstOrNull {
                    it.name.equals("Scroll of Enrage", ignoreCase = true)
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
                            "Enter" -> {
                                BotHelper.dispatchLog(
                                    "temple",
                                    username,
                                    "Cell cleared. Moving to r1..."
                                )
                                session.commands.jumpCell("r1", "Left")
                                delay(1200.milliseconds)
                            }

                            "r1" -> {
                                BotHelper.dispatchLog(
                                    "temple",
                                    username,
                                    "Cell cleared. Moving to r2..."
                                )
                                session.commands.jumpCell("r2", "Left")
                                delay(1200.milliseconds)
                            }

                            "r2" -> {
                                BotHelper.dispatchLog(
                                    "temple",
                                    username,
                                    "Cell cleared. Moving to r3..."
                                )
                                session.commands.jumpCell("r3", "Left")
                                delay(1200.milliseconds)
                            }

                            "r3" -> {
                                clearedRuns++
                                BotHelper.dispatchLog(
                                    "temple",
                                    username,
                                    "=== Dungeon cleared $clearedRuns times! ==="
                                )
                                session.commands.sendChat("Dungeon cleared $clearedRuns times.")
                                delay(1000.milliseconds)
                                session.commands.joinMap("templeshrine", 999999)
                                delay(2500.milliseconds)
                                session.commands.dungeonQueue(dungeonMap)
                                delay(2000.milliseconds)
                            }
                        }
                    }
                } else {
                    // Slave: follow master if not in the same cell
                    val masterSession = activeSessions["slot1"]
                    val masterCell = masterSession?.playerState?.cell
                    if (masterCell != null && !masterCell.equals(currentCell, ignoreCase = true)) {
                        session.commands.gotoPlayer(masterUsername)
                        delay(1000.milliseconds)
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
                                "Ran out of Scroll of Enrage (SoE)!"
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
                    val hasSunsHeat =
                        session.playerState.auras.any { it.equals("Sun's Heat", ignoreCase = true) }
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

                delay(220.milliseconds)
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
                isAlive = it.isAlive
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
