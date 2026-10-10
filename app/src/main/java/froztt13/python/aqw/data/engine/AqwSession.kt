package froztt13.python.aqw.data.engine

import android.util.Log
import froztt13.python.aqw.data.engine.commands.AqwCombatCommands
import froztt13.python.aqw.data.engine.commands.AqwItemCommands
import froztt13.python.aqw.data.engine.commands.AqwMapCommands
import froztt13.python.aqw.data.engine.commands.AqwQuestCommands
import froztt13.python.aqw.data.engine.commands.AqwSocialCommands
import froztt13.python.aqw.data.model.LogEntry
import froztt13.python.aqw.data.model.LogEntryType
import froztt13.python.aqw.data.network.AqwHttpApi
import froztt13.python.aqw.data.network.AqwSocketClient
import froztt13.python.aqw.domain.model.AqwItem
import froztt13.python.aqw.domain.model.AqwMonster
import froztt13.python.aqw.domain.model.AqwOtherPlayer
import froztt13.python.aqw.domain.model.AqwPlayerState
import froztt13.python.aqw.domain.model.AqwSkill
import froztt13.python.aqw.utils.stripAnsi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

class AqwSession {

    companion object {
        private const val TAG = "AqwSession"
        private val _activeSessions = java.util.concurrent.CopyOnWriteArrayList<AqwSession>()
        val activeSessions: List<AqwSession> get() = _activeSessions

        fun register(session: AqwSession) {
            _activeSessions.addIfAbsent(session)
        }

        fun unregister(session: AqwSession) {
            _activeSessions.remove(session)
        }

        fun getPrimaryPlayerState(): AqwPlayerState? {
            val live = _activeSessions.lastOrNull { it.isConnected.value }?.playerState
                ?: _activeSessions.lastOrNull()?.playerState
            return live
        }
    }

    private var logCallback: ((String) -> Unit)? = null

    var slotKey: String = ""
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    fun log(message: String, botType: LogEntryType = LogEntryType.INFO) {
        val clean = message.stripAnsi()
        val uname = if (slotKey.isNotBlank()) {
            val user = playerState.username.ifBlank { socketClient.tag }
            if (user.isNotBlank() && !user.contains(
                    slotKey,
                    ignoreCase = true
                )
            ) "[$slotKey] $user" else user.ifBlank { slotKey }
        } else {
            playerState.username.ifBlank { socketClient.tag.ifBlank { "Account" } }
        }
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            botType = botType,
            username = uname,
            message = clean
        )
        _logs.update { list -> (list + entry).takeLast(250) }
        logCallback?.invoke(clean)
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    private val sessionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var packetListenerJob: Job? = null

    val socketClient = AqwSocketClient()
    val isConnected: StateFlow<Boolean> = socketClient.isConnected

    val playerState = AqwPlayerState()

    private val _isCharLoaded = MutableStateFlow(false)
    val isCharLoaded: StateFlow<Boolean> = _isCharLoaded.asStateFlow()

    private val _events = MutableSharedFlow<AqwEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<AqwEvent> = _events.asSharedFlow()

    val map: AqwMapCommands = AqwMapCommands(
        client = socketClient,
        playerState = playerState,
        ensureAlive = { combat.ensureAlive() },
        rest = { combat.rest() },
        stopAggro = { combat.stopAggro() }
    )

    val combat: AqwCombatCommands = AqwCombatCommands(
        client = socketClient,
        playerState = playerState,
        monstersProvider = { map.allMonsters.value },
        coroutineScope = sessionScope,
        jumpCell = { cell, pad -> map.jumpCell(cell, pad) },
        jumpToMonster = { monName -> map.jumpToMonster(monName, byAliveMonster = true) }
    )

    val item: AqwItemCommands = AqwItemCommands(
        client = socketClient,
        playerState = playerState,
        leaveCombat = { safeLeave -> map.leaveCombat(safeLeave) },
        onScrollEquipped = { scrollId -> combat.scrollId = scrollId },
        onItemUpdated = { quest.triggerAutoQuestCheck() }
    )

    val quest: AqwQuestCommands = AqwQuestCommands(
        client = socketClient,
        playerState = playerState,
        leaveCombat = { safeLeave -> map.leaveCombat(safeLeave) },
        getItemQty = { itemId, isTemp -> item.getItemQty(itemId, isTemp) },
        coroutineScope = sessionScope
    )

    val social: AqwSocialCommands = AqwSocialCommands(client = socketClient)

    var isPaused: () -> Boolean = { false }

    init {
        combat.isPaused = { isPaused() }
        quest.isPaused = { isPaused() }
    }

    suspend fun waitIfPaused(isStopRequested: () -> Boolean = { false }) {
        while (isPaused() && !isStopRequested() && isConnected.value) {
            delay(500.milliseconds)
        }
    }

    var latestPartyId: Int? = null
        private set


    suspend fun start(
        username: String,
        password: String,
        preferredServer: String = "Alteon",
        onLog: ((String) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        this@AqwSession.logCallback = onLog
        socketClient.tag = username
        log("Authenticating account $username...")
        val loginResult = AqwHttpApi.login(username, password)
        if (!loginResult.success) {
            val err = loginResult.errorMessage ?: "Authentication failed"
            log("Error: $err")
            Log.e(TAG, "Login failed: $err")
            return@withContext false
        }

        playerState.username = username
        playerState.authUserId = loginResult.userId
        playerState.token = loginResult.token

        val targetServer = loginResult.servers.firstOrNull {
            it.name.equals(preferredServer, ignoreCase = true) && it.isOnline
        } ?: loginResult.servers.firstOrNull { it.isOnline }

        if (targetServer == null) {
            val err = "No available server found"
            log("Error: $err")
            Log.e(TAG, err)
            return@withContext false
        }

        log("Connecting to ${targetServer.name} (${targetServer.ip}:${targetServer.port})...")
        val connected = socketClient.connect(targetServer.ip, targetServer.port)
        if (!connected) {
            log("Failed to connect to ${targetServer.name}")
            return@withContext false
        }

        register(this@AqwSession)
        startPacketProcessing()

        // Handshake Step 1: Send policy file request
        socketClient.send("<policy-file-request/>")
        true
    }

    private fun startPacketProcessing() {
        packetListenerJob?.cancel()
        packetListenerJob = sessionScope.launch(Dispatchers.IO) {
            socketClient.incomingPackets.collect { rawPacket ->
                if (!isActive) return@collect
                val packetToParse = AqwPacketParser.decodeBase64Packet(rawPacket)
                val subPackets = if (packetToParse.contains('\u0000')) {
                    packetToParse.split('\u0000').filter { it.isNotBlank() }
                } else {
                    listOf(packetToParse)
                }
                for (subPacket in subPackets) {
                    val event = AqwPacketParser.parse(subPacket, playerState.username)
                    handleEvent(event)
                    _events.emit(event)
                }
            }
        }
    }

    private suspend fun handleEvent(event: AqwEvent) {
        val emitLog: (String) -> Unit = { log(it) }
        when (event) {
            is AqwEvent.PolicyReceived -> {
                emitLog("Policy received. Sending login handshake...")
                val loginXml = "<msg t='sys'><body action='login' r='0'>" +
                        "<login z='zone_master'>" +
                        "<nick><![CDATA[SPIDER#0001~${playerState.username}~5.01]]></nick>" +
                        "<pword><![CDATA[${playerState.token}]]></pword>" +
                        "</login></body></msg>"
                socketClient.send(loginXml)
            }

            is AqwEvent.LoginResponseReceived -> {
                emitLog("Login handshake accepted. Joining initial room...")
                socketClient.send("%xt%zm%firstJoin%1%")
                socketClient.send($$"%xt%zm%cmd%1%ignoreList%$clearAll%")
            }

            is AqwEvent.JoinOk -> {
                if (event.myRoomUserId > 0) {
                    playerState.roomUserId = event.myRoomUserId
                }
                playerState.roomUserIds.clear()
                for ((uname, rId) in event.roomUsers) {
                    playerState.roomUserIds.add(rId)
                    if (uname.equals(playerState.username, ignoreCase = true)) {
                        playerState.roomUserId = rId
                    } else {
                        playerState.playersInMap[uname]?.roomUserId = rId
                    }
                }
            }

            is AqwEvent.MoveToArea -> {
                playerState.isJoiningMap = false
                playerState.areaName = event.areaName
                playerState.areaId = event.areaId
                playerState.mapName = event.mapName
                playerState.cell = event.playerCell
                playerState.pad = event.playerPad
                if (event.myRoomUserId > 0) {
                    playerState.roomUserId = event.myRoomUserId
                }
                event.playerHp?.let { playerState.currentHp = it }
                event.playerMaxHp?.let { playerState.maxHp = it }
                event.playerMp?.let { playerState.mp = it }
                map.setMonsters(event.monsters)

                playerState.playersInMap.clear()
                playerState.roomUserIds.clear()
                if (playerState.roomUserId > 0) {
                    playerState.roomUserIds.add(playerState.roomUserId)
                }
                playerState.droppedItems.clear()
                for (op in event.otherPlayers) {
                    playerState.playersInMap[op.username] = op
                    if (op.roomUserId > 0 && op.roomUserId !in playerState.roomUserIds) {
                        playerState.roomUserIds.add(op.roomUserId)
                    }
                    if (op.username.equals(playerState.followedPlayer, ignoreCase = true)) {
                        playerState.followedPlayerCell = op.cell
                    }
                }

                emitLog("Moved to ${event.areaName} [${event.playerCell}, ${event.playerPad}]")
                socketClient.send("%xt%zm%retrieveUserDatas%${event.areaId}%${playerState.roomUserId}%")
            }

            is AqwEvent.YouJoinedMap -> {
                playerState.isJoiningMap = false
                socketClient.send("%xt%zm%retrieveUserDatas%${playerState.areaId}%${playerState.roomUserId}%")
            }

            is AqwEvent.UserDatasLoaded -> {
                if (event.staffList.isNotEmpty()) {
                    emitLog("[SECURITY] Staff / Moderator detected in room: ${event.staffList.joinToString()}")
                }
                event.charId?.let { cid ->
                    if (cid > 0 && playerState.charId == 0) {
                        playerState.charId = cid
                    }
                }
                event.gold?.let { g ->
                    playerState.gold = g
                }
                if (playerState.bank.isEmpty() && playerState.charId > 0) {
                    sessionScope.launch(Dispatchers.IO) {
                        val bankItems = AqwHttpApi.loadBank(playerState.charId, playerState.token)
                        playerState.bank.clear()
                        playerState.bank.addAll(bankItems)
                        emitLog("Bank loaded (${bankItems.size} items)")
                    }
                }
                socketClient.send("%xt%zm%retrieveInventory%${playerState.areaId}%${playerState.roomUserId}%")
            }

            is AqwEvent.SingleUserDataLoaded -> {
                if (event.isStaff) {
                    emitLog("[SECURITY] Staff / Moderator entered room: ${event.username} (Access Level: ${event.accessLevel})")
                }
            }

            is AqwEvent.InventoryLoaded -> {
                playerState.inventory.clear()
                playerState.inventory.addAll(event.items)
                for (f in event.factions) {
                    playerState.addFaction(f)
                }
                _isCharLoaded.value = true
                emitLog("Character inventory loaded (${event.items.size} items, ${event.factions.size} factions)")
            }

            is AqwEvent.SkillsLoaded -> {
                playerState.skills.clear()
                playerState.skills.addAll(event.skills)
                emitLog("Skills loaded: ${event.skills.joinToString { it.name }}")
            }

            is AqwEvent.StatsUpdated -> {
                event.cdReduction?.let { playerState.cdReduction = it }
                event.manaCost?.let { playerState.manaCost = it }
            }

            is AqwEvent.CombatTick -> {
                event.playerHp?.let { playerState.currentHp = it }
                event.playerMp?.let { playerState.mp = it }
                event.playerInCombat?.let { playerState.isInCombat = it }
                if (event.monsterHpMap.isNotEmpty()) {
                    map.updateMonstersHp(event.monsterHpMap)
                }

                // Update skill cooldown upon server-confirmed SARSA execution
                for (sarsa in event.sarsa) {
                    val isMe =
                        (playerState.roomUserId > 0 && sarsa.cInf == "p:${playerState.roomUserId}") ||
                                sarsa.cInf.equals("p:${playerState.username}", ignoreCase = true)
                    if (isMe) {
                        val skillIdx = AqwPacketParser.parseSkillIndex(sarsa.actRef)
                        if (skillIdx != null) {
//                            combat.updateNextUse(skillIdx)
                        }
                    }
                }

                for ((auraObj, tInf) in event.auras) {
                    val auraName = auraObj.name

                    val isPlayerTarget =
                        (playerState.roomUserId > 0 && (tInf == "p:${playerState.roomUserId}" || tInf == playerState.roomUserId.toString())) ||
                                tInf.equals("p:${playerState.username}", ignoreCase = true) ||
                                (tInf.startsWith("p:") && playerState.roomUserId == 0 && !playerState.playersInMap.values.any { tInf == "p:${it.roomUserId}" || tInf == "p:${it.userId}" })

                    if (isPlayerTarget) {
                        playerState.addAura(auraObj)
                    } else if (tInf.startsWith("m")) {
                        val monId =
                            if (tInf.startsWith("m:")) tInf.substringAfter("m:") else tInf.removePrefix(
                                "m"
                            )
                        map.allMonsters.value.firstOrNull { it.monMapId == monId || "m:${it.monMapId}" == tInf }
                            ?.addAura(auraObj)
                    } else if (tInf.startsWith("p:")) {
                        val pTargetId = tInf.substringAfter("p:")
                        playerState.playersInMap.values.firstOrNull {
                            it.roomUserId.toString() == pTargetId || it.userId.toString() == pTargetId || it.username.equals(
                                pTargetId,
                                ignoreCase = true
                            )
                        }?.let { otherP ->
                            if (!otherP.auras.any { it.equals(auraName, ignoreCase = true) }) {
                                otherP.auras.add(auraName)
                            }
                        }
                    }
                }

                for ((auraName, tInf) in event.aurasRemoved) {

                    val isPlayerTarget =
                        (playerState.roomUserId > 0 && (tInf == "p:${playerState.roomUserId}" || tInf == playerState.roomUserId.toString())) ||
                                tInf.equals("p:${playerState.username}", ignoreCase = true) ||
                                (tInf.startsWith("p:") && playerState.roomUserId == 0)

                    if (isPlayerTarget) {
                        playerState.removeAura(auraName)
                    } else if (tInf.startsWith("m")) {
                        val monId =
                            if (tInf.startsWith("m:")) tInf.substringAfter("m:") else tInf.removePrefix(
                                "m"
                            )
                        map.allMonsters.value.firstOrNull { it.monMapId == monId || "m:${it.monMapId}" == tInf }
                            ?.removeAura(auraName)
                    } else if (tInf.startsWith("p:")) {
                        val pTargetId = tInf.substringAfter("p:")
                        playerState.playersInMap.values.firstOrNull {
                            it.roomUserId.toString() == pTargetId || it.userId.toString() == pTargetId || it.username.equals(
                                pTargetId,
                                ignoreCase = true
                            )
                        }?.let { otherP ->
                            otherP.auras.removeAll { it.equals(auraName, ignoreCase = true) }
                        }
                    }
                }
            }

            is AqwEvent.PlayerDied -> {
                if (event.userId == playerState.authUserId) {
                    Log.d(TAG, "playerDeath: ${playerState.username} is DEAD")
                    triggerDeathHandler()
                } else {
                    playerState.playersInMap.values.firstOrNull { it.userId == event.userId }?.let {
                        it.isDead = true
                        it.hp = 0
                    }
                }
            }

            is AqwEvent.PlayerRespawned -> {
                playerState.playersInMap.values.firstOrNull { it.userId == event.userId }?.let {
                    it.isDead = false
                    it.hp = 100
                }

                map.jumpCell(playerState.cell, playerState.pad)
                emitLog("Respawn complete. Current at ${playerState.cell} [${playerState.pad}]")
            }

            is AqwEvent.MonsterStateUpdated -> {
                map.updateMonsterState(event.monMapId, event.hp, event.isAlive)
            }

            is AqwEvent.PlayerStateUpdated -> {
                if (event.username.equals(playerState.username, ignoreCase = true)) {
                    event.hp?.let { playerState.currentHp = it }
                    event.maxHp?.let { playerState.maxHp = it }
                    event.mp?.let { playerState.mp = it }
                    event.inCombat?.let { playerState.isInCombat = it }
                    event.cell?.let { playerState.cell = it }
                    event.pad?.let { playerState.pad = it }
                    event.x?.let { playerState.x = it }
                    event.y?.let { playerState.y = it }
                    event.tx?.let { playerState.tx = it }
                    event.ty?.let { playerState.ty = it }
                } else {
                    val p = playerState.playersInMap[event.username]
                    if (p != null) {
                        event.hp?.let { p.hp = it }
                        event.maxHp?.let { p.maxHp = it }
                        event.mp?.let { p.mp = it }
                        event.cell?.let { p.cell = it }
                        event.pad?.let { p.pad = it }
                        event.x?.let { p.x = it }
                        event.y?.let { p.y = it }
                        event.tx?.let { p.tx = it }
                        event.ty?.let { p.ty = it }
                        event.sp?.let { p.sp = it }
                    } else if (event.username.isNotEmpty()) {
                        playerState.playersInMap[event.username] = AqwOtherPlayer(
                            username = event.username,
                            cell = event.cell ?: "Enter",
                            pad = event.pad ?: "Spawn",
                            x = event.x ?: event.tx ?: 0,
                            y = event.y ?: event.ty ?: 0,
                            tx = event.tx ?: event.x ?: 0,
                            ty = event.ty ?: event.y ?: 0,
                            sp = event.sp ?: 8,
                            hp = event.hp ?: 100,
                            maxHp = event.maxHp ?: 100,
                            mp = event.mp ?: 100
                        )
                    }
                    if (event.username.equals(
                            playerState.followedPlayer,
                            ignoreCase = true
                        )
                    ) {
                        if (event.cell != null) {
                            playerState.followedPlayerCell = event.cell
                        }
                    }
                }
            }

            is AqwEvent.ItemDropped -> {
                val dropList = event.items.ifEmpty {
                    listOf(
                        AqwItem(
                            itemId = event.itemId,
                            name = event.itemName,
                            qty = event.qty
                        )
                    )
                }
                for (drop in dropList) {
                    val existing = playerState.droppedItems.firstOrNull { it.itemId == drop.itemId }
                    if (existing != null) {
                        existing.qty += drop.qty
                    } else {
                        playerState.droppedItems.add(drop)
                    }
                }
                val dropDesc = dropList.joinToString(", ") { "${it.name} x${it.qty}" }
                emitLog("Item drop: $dropDesc")
            }

            is AqwEvent.ShopLoaded -> {
                val existingIdx =
                    playerState.loadedShops.indexOfFirst { it.shopId == event.shop.shopId }
                if (existingIdx != -1) {
                    playerState.loadedShops[existingIdx] = event.shop
                } else {
                    playerState.loadedShops.add(event.shop)
                }
                emitLog("Shop loaded: ${event.shop.shopName} (${event.shop.items.size} items)")
            }

            is AqwEvent.ItemBought -> {
                var shopItemName = "Item ${event.itemId}"
                for (shop in playerState.loadedShops) {
                    val found = shop.items.firstOrNull { it.itemId == event.itemId }
                    if (found != null) {
                        shopItemName = found.name
                        break
                    }
                }
                val invItem = playerState.getItemInventoryById(event.itemId)
                if (invItem != null) {
                    invItem.qty += event.qty
                    invItem.charItemId = event.charItemId
                } else {
                    playerState.inventory.add(
                        AqwItem(
                            itemId = event.itemId,
                            charItemId = event.charItemId,
                            name = shopItemName,
                            qty = event.qty
                        )
                    )
                }
                emitLog("Bought $shopItemName x${event.qty}")
                quest.triggerAutoQuestCheck()
            }

            is AqwEvent.ItemSold -> {
                playerState.gold += event.goldAmount
                playerState.goldFarmed += event.goldAmount
                val invItem =
                    playerState.inventory.firstOrNull { it.charItemId == event.charItemId }
                if (invItem != null) {
                    if (event.qtyNow <= 0) {
                        playerState.inventory.remove(invItem)
                        emitLog("Sold ${invItem.name} x${event.qtySold} (Remaining: 0, Gold: +${event.goldAmount})")
                    } else {
                        invItem.qty = event.qtyNow
                        emitLog("Sold ${invItem.name} x${event.qtySold} (Remaining: ${event.qtyNow}, Gold: +${event.goldAmount})")
                    }
                }
            }

            is AqwEvent.GoldExpAdded -> {
                playerState.gold += event.gold
                playerState.goldFarmed += event.gold
                playerState.expFarmed += event.exp
                if (event.factionId > 0 && event.rep > 0) {
                    playerState.addRepToFaction(event.factionId, event.rep)
                }
//                emitLog("Gained +${event.gold} gold, +${event.exp} exp, +${event.rep} rep")
            }

            is AqwEvent.ItemsAdded -> {
                for (added in event.items) {
                    playerState.droppedItems.removeAll { it.itemId == added.itemId }
                    val wasRecentlyPicked = item.recentlyPickedDropIds.remove(added.itemId)
                    if (added.isTemp || added.charItemId == 0) {
                        val existing = playerState.getItemTempById(added.itemId)
                        if (existing != null) {
                            if (!wasRecentlyPicked) {
                                existing.qty += added.qty
                            }
                        } else {
                            playerState.tempInventory.add(added)
                        }
                        emitLog(
                            "Added temp item: ${added.name} (Qty now: ${
                                playerState.getItemTempById(
                                    added.itemId
                                )?.qty
                            })"
                        )
                    } else {
                        val existing = playerState.getItemInventoryById(added.itemId)
                        if (existing != null) {
                            existing.qty = added.qty
                            existing.charItemId = added.charItemId
                        } else {
                            playerState.inventory.add(added)
                        }
                        val bankExisting = playerState.getItemBankById(added.itemId)
                        if (bankExisting != null) {
                            bankExisting.qty = added.qty
                            bankExisting.charItemId = added.charItemId
                        }
                        emitLog("Added item: ${added.name.ifEmpty { "${added.itemId}" }} (Qty now: ${added.qty})")
                    }
                }
                quest.triggerAutoQuestCheck()
            }

            is AqwEvent.ItemsTurnedIn -> {
                for ((itemId, qty) in event.deductions) {
                    val invItem = playerState.getItemInventoryById(itemId)
                    if (invItem != null) {
                        if (invItem.qty - qty <= 0) {
                            playerState.inventory.removeAll { it.itemId == itemId || (it.charItemId != 0 && it.charItemId == itemId) }
                        } else {
                            invItem.qty -= qty
                        }
                    }
                    val tempItem = playerState.getItemTempById(itemId)
                    if (tempItem != null) {
                        if (tempItem.qty - qty <= 0) {
                            playerState.tempInventory.removeAll { it.itemId == itemId || (it.charItemId != 0 && it.charItemId == itemId) }
                        } else {
                            tempItem.qty -= qty
                        }
                    }
                }
                emitLog("TurnIn deducted: ${event.deductions.joinToString { "${it.first} x${it.second}" }}")
                quest.triggerAutoQuestCheck()
            }

            is AqwEvent.QuestDetailsLoaded -> {
                for (q in event.quests) {
                    val existingIdx =
                        playerState.loadedQuests.indexOfFirst { it.questId == q.questId }
                    if (existingIdx != -1) {
                        playerState.loadedQuests[existingIdx] = q
                    } else {
                        playerState.loadedQuests.add(q)
                    }
                }
                emitLog("Loaded ${event.quests.size} quest details")
                quest.triggerAutoQuestCheck()
            }

            is AqwEvent.QuestAccepted -> {
                if (event.success) {
                    playerState.activeQuestIds.add(event.questId)
                    playerState.failedQuestIds.remove(event.questId)
                    if (playerState.loadedQuests.none { it.questId == event.questId }) {
                        socketClient.send("%xt%zm%getQuests%${playerState.areaId}%${event.questId}%")
                    }
                    emitLog("Quest accepted: ${event.questId}")
                    if (event.questId in playerState.registeredAutoQuestIds) {
                        quest.triggerAutoQuestCheck()
                    }
                } else {
                    playerState.failedQuestIds.add(event.questId)
                    log("Failed to accept quest: ${event.questId}", LogEntryType.ERROR)
                }
            }

            is AqwEvent.QuestCompleted -> {
                if (event.success) {
                    playerState.activeQuestIds.remove(event.questId)

                    // Decrease or remove consumed turn-in items
                    val loadedQuest =
                        playerState.loadedQuests.firstOrNull { it.questId == event.questId }
                    if (loadedQuest != null) {
                        for (req in loadedQuest.turnInItems) {
                            var remainingToDeduct = req.qty
                            // 1. Check temp inventory first
                            val tempItem = playerState.getItemTempById(req.itemId)
                                ?: (if (req.name.isNotBlank()) playerState.getItemTemp(req.name) else null)
                            if (tempItem != null) {
                                val deductTemp = minOf(tempItem.qty, remainingToDeduct)
                                if (tempItem.qty - deductTemp <= 0) {
                                    playerState.tempInventory.removeAll {
                                        it.itemId == req.itemId || (it.charItemId != 0 && it.charItemId == req.itemId) || it.matches(
                                            req.name
                                        )
                                    }
                                } else {
                                    tempItem.qty -= deductTemp
                                }
                                remainingToDeduct -= deductTemp
                            }
                            // 2. Check regular inventory
                            if (remainingToDeduct > 0) {
                                val invItem = playerState.getItemInventoryById(req.itemId)
                                    ?: (if (req.name.isNotBlank()) playerState.getItemInventory(req.name) else null)
                                if (invItem != null) {
                                    val deductInv = minOf(invItem.qty, remainingToDeduct)
                                    if (invItem.qty - deductInv <= 0) {
                                        playerState.inventory.removeAll {
                                            it.itemId == req.itemId || (it.charItemId != 0 && it.charItemId == req.itemId) || it.matches(
                                                req.name
                                            )
                                        }
                                    } else {
                                        invItem.qty -= deductInv
                                    }
                                    remainingToDeduct -= deductInv
                                }
                            }
                        }
                    }

                    emitLog("Quest completed: ${event.questId} - ${event.questName} (+${event.rep} rep)")
                    quest.triggerAutoQuestCheck()

                    if (event.questId in playerState.registeredAutoQuestIds) {
                        sessionScope.launch {
                            delay(500.milliseconds)
                            quest.acceptQuest(event.questId)
                        }
                    }
                } else {
                    if (event.msg.contains("Missing Turn In Item", ignoreCase = true)) {
                        playerState.missingTurnInItemQuestIds.add(event.questId)
                    }
                    if (event.msg.contains("Missing Quest Progress", ignoreCase = true)) {
                        playerState.missingQuestProgressQuestIds.add(event.questId)
                    }
                    if (event.msg.contains("One Time Quest Only", ignoreCase = true)) {
                        playerState.oneTimeQuestIds.add(event.questId)
                    }
                    log(
                        "Quest complete failed: ${event.questId} (${event.msg})",
                        LogEntryType.ERROR
                    )
                }
            }

            is AqwEvent.FactionAdded -> {
                playerState.addFaction(event.faction)
                emitLog("Faction added: ${event.faction.name} (Rep: ${event.faction.rep})")
            }

            is AqwEvent.AurasCleared -> {
                playerState.removeAllAuras()
                map.clearMonsterAuras()
                playerState.playersInMap.values.forEach { it.auras.clear() }
            }

            is AqwEvent.ScrollEquipped -> {
                val slot5 = playerState.skills.firstOrNull { it.index == 5 }
                if (slot5 != null) {
                    slot5.anim = event.anim
                    slot5.strl = event.strl
                    slot5.cdMillis = event.cd
                    slot5.cdSeconds = event.cd / 1000
                    slot5.tgt = event.tgt
                } else {
                    playerState.skills.add(
                        AqwSkill(
                            id = "i1",
                            index = 5,
                            name = "Equipped Scroll/Potion",
                            anim = event.anim,
                            strl = event.strl,
                            cdSeconds = event.cd / 1000,
                            cdMillis = event.cd,
                            tgt = event.tgt
                        )
                    )
                }
                emitLog("Scroll/potion equipped (CD: ${event.cd}s)")
            }

            is AqwEvent.UserEnteredRoom -> {
                if (event.isMe && event.userId > 0) {
                    playerState.roomUserId = event.userId
                }
                if (event.userId > 0) {
                    if (event.userId !in playerState.roomUserIds) {
                        playerState.roomUserIds.add(event.userId)
                    }
                    if (!event.isMe && event.username.isNotEmpty()) {
                        val p = playerState.playersInMap.getOrPut(event.username) {
                            AqwOtherPlayer(username = event.username)
                        }
                        p.roomUserId = event.userId
                    }
                    emitLog("User entered room (ID: ${event.userId}${if (event.username.isNotEmpty()) ", name: ${event.username}" else ""})")
                    socketClient.send("%xt%zm%retrieveUserData%${playerState.areaId}%${event.userId}%")
                }
            }

            is AqwEvent.UserLeftRoom -> {
                if (event.userId > 0) {
                    playerState.roomUserIds.remove(event.userId)
                    val removed =
                        playerState.playersInMap.entries.firstOrNull { it.value.roomUserId == event.userId }
                    if (removed != null) {
                        playerState.playersInMap.remove(removed.key)
                        if (removed.key.equals(playerState.followedPlayer, ignoreCase = true)) {
                            playerState.followedPlayerCell = null
                        }
                        emitLog("User left room: ${removed.key} (ID: ${event.userId})")
                    }
                }
            }

            is AqwEvent.ExitArea -> {
                if (event.username.equals(playerState.followedPlayer, ignoreCase = true)) {
                    playerState.followedPlayerCell = null
                }
                playerState.playersInMap.remove(event.username)
            }

            is AqwEvent.ChatMessage -> {
                log(
                    "[${event.channel.uppercase()}] ${event.sender}: ${event.message}",
                    LogEntryType.SYSTEM
                )
            }

            is AqwEvent.WhisperMessage -> {
                log("[WHISPER] ${event.sender}: ${event.message}", LogEntryType.SYSTEM)
            }

            is AqwEvent.ServerBroadcast -> {
                log("[Server] ${event.message}", LogEntryType.SYSTEM)
            }

            is AqwEvent.Warning -> {
//                if (event.isSpamWarning.not())
                log("[Warning] ${event.message}", LogEntryType.WARNING)
            }

            is AqwEvent.AfkNotice -> {
                log("[AFK] Server marked status as Away From Keyboard", LogEntryType.SYSTEM)
            }

            is AqwEvent.InvalidSessionNotice -> {
                log("[Session] Invalid session reported by server", LogEntryType.WARNING)
            }

            is AqwEvent.LoggedOut -> {
                log("Session logged out by server", LogEntryType.WARNING)
                stop()
            }

            is AqwEvent.PartyInviteReceived -> {
                latestPartyId = event.partyId
                emitLog("Party invite received from ${event.owner} (PID: ${event.partyId})")
            }

            else -> {}
        }
    }

    private var deathHandlerJob: Job? = null

    /**
     * Triggers asynchronous death handler timer (11s) and respawns automatically (matching Python death_handler_task).
     */
    fun triggerDeathHandler() {
        if (playerState.isDead && deathHandlerJob?.isActive == true) return

        val respawnTime = 11
        playerState.isDead = true
        playerState.currentHp = 0
        playerState.isInCombat = false

        log("Player DIED! Respawn countdown started (${respawnTime}s)...", LogEntryType.WARNING)

        deathHandlerJob?.cancel()
        deathHandlerJob = sessionScope.launch(Dispatchers.IO) {
            repeat(respawnTime) {
                if (!socketClient.isConnected.value) return@repeat
                delay(1000.milliseconds)
            }
            if (socketClient.isConnected.value && playerState.isDead) {
                combat.resurrectPlayer()
                log("Player resurrected!")
//                map.jumpCell(playerState.cell, playerState.pad)
//                log("Jump to ${playerState.cell} [${playerState.pad}]")
            }
        }
    }

    fun getCooldowns(): Map<Int, Double> {
        val result = mutableMapOf<Int, Double>()
        for (i in 0..5) {
            val skill = combat.getSkill(i)
            result[i] = skill?.remainingCooldownMs()?.toDouble() ?: 0.0
        }
        return result
    }

    val lastTargetMonster: AqwMonster?
        get() = combat.lastTargetMonster

    fun stop() {
        unregister(this)
        combat.stopAggro()
        playerState.isJoiningMap = false
        deathHandlerJob?.cancel()
        deathHandlerJob = null
        packetListenerJob?.cancel()
        packetListenerJob = null
        _isCharLoaded.value = false
        map.clearMonsters()
        socketClient.disconnect()
    }
}
