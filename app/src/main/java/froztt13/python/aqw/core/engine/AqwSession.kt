package froztt13.python.aqw.core.engine

import android.util.Log
import froztt13.python.aqw.core.model.AqwItem
import froztt13.python.aqw.core.model.AqwMonster
import froztt13.python.aqw.core.model.AqwOtherPlayer
import froztt13.python.aqw.core.model.AqwPlayerState
import froztt13.python.aqw.core.model.AqwSkill
import froztt13.python.aqw.core.network.AqwHttpApi
import froztt13.python.aqw.core.network.AqwSocketClient
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

class AqwSession {

    companion object {
        private const val TAG = "AqwSession"
        private val _activeSessions = java.util.concurrent.CopyOnWriteArrayList<AqwSession>()
        val activeSessions: List<AqwSession> get() = _activeSessions

        private var _lastKnownPlayerState: AqwPlayerState? = null
        val lastKnownPlayerState: AqwPlayerState? get() = _lastKnownPlayerState

        fun register(session: AqwSession) {
            if (!_activeSessions.contains(session)) {
                _activeSessions.add(session)
            }
            _lastKnownPlayerState = session.playerState
        }

        fun unregister(session: AqwSession) {
            _activeSessions.remove(session)
        }

        fun getActivePlayerStates(): List<AqwPlayerState> {
            return _activeSessions.map { it.playerState }
        }

        fun getPrimaryPlayerState(): AqwPlayerState? {
            val live = _activeSessions.lastOrNull { it.isConnected.value }?.playerState
                ?: _activeSessions.lastOrNull()?.playerState
            if (live != null) _lastKnownPlayerState = live
            return live ?: _lastKnownPlayerState
        }
    }

    private var logCallback: ((String) -> Unit)? = null

    private val sessionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var packetListenerJob: Job? = null

    val socketClient = AqwSocketClient()
    val isConnected: StateFlow<Boolean> = socketClient.isConnected

    val playerState = AqwPlayerState()

    private val _isCharLoaded = MutableStateFlow(false)
    val isCharLoaded: StateFlow<Boolean> = _isCharLoaded.asStateFlow()

    private val _allMonsters = MutableStateFlow<List<AqwMonster>>(emptyList())
    val allMonsters: StateFlow<List<AqwMonster>> = _allMonsters.asStateFlow()

    private val _events = MutableSharedFlow<AqwEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<AqwEvent> = _events.asSharedFlow()

    val commands: AqwCommandHandler =
        AqwCommandHandler(socketClient, playerState, { _allMonsters.value }, sessionScope)

    var isPaused: () -> Boolean = { false }

    init {
        commands.combat.isPaused = { isPaused() }
        commands.quest.isPaused = { isPaused() }
    }

    suspend fun waitIfPaused(isStopRequested: () -> Boolean = { false }) {
        while (isPaused() && !isStopRequested() && isConnected.value) {
            delay(500.milliseconds)
        }
    }

    var latestPartyId: Int? = null
        private set

    fun getCellMonsters(cell: String = playerState.cell): List<AqwMonster> {
        return _allMonsters.value.filter { it.frame.equals(cell, ignoreCase = true) }
    }

    fun hasAliveMonsters(cell: String = playerState.cell): Boolean {
        return getCellMonsters(cell).any { it.isAlive && it.currentHp > 0 }
    }

    suspend fun start(
        username: String,
        password: String,
        preferredServer: String = "Alteon",
        onLog: ((String) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        this@AqwSession.logCallback = onLog
        socketClient.tag = username
        onLog?.invoke("Authenticating account $username...")
        val loginResult = AqwHttpApi.login(username, password)
        if (!loginResult.success) {
            val err = loginResult.errorMessage ?: "Authentication failed"
            onLog?.invoke("Error: $err")
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
            onLog?.invoke("Error: $err")
            Log.e(TAG, err)
            return@withContext false
        }

        onLog?.invoke("Connecting to ${targetServer.name} (${targetServer.ip}:${targetServer.port})...")
        val connected = socketClient.connect(targetServer.ip, targetServer.port)
        if (!connected) {
            onLog?.invoke("Failed to connect to ${targetServer.name}")
            return@withContext false
        }

        register(this@AqwSession)
        startPacketProcessing(onLog)

        // Handshake Step 1: Send policy file request
        socketClient.send("<policy-file-request/>")
        true
    }

    private fun startPacketProcessing(onLog: ((String) -> Unit)? = null) {
        packetListenerJob?.cancel()
        packetListenerJob = sessionScope.launch(Dispatchers.IO) {
            socketClient.incomingPackets.collect { rawPacket ->
                if (!isActive) return@collect
                val event = AqwPacketParser.parse(rawPacket, playerState.username)
                handleEvent(event, onLog)
                _events.emit(event)
            }
        }
    }

    private suspend fun handleEvent(event: AqwEvent, onLog: ((String) -> Unit)?) {
        when (event) {
            is AqwEvent.PolicyReceived -> {
                onLog?.invoke("Policy received. Sending login handshake...")
                val loginXml = "<msg t='sys'><body action='login' r='0'>" +
                        "<login z='zone_master'>" +
                        "<nick><![CDATA[SPIDER#0001~${playerState.username}~3.0141]]></nick>" +
                        "<pword><![CDATA[${playerState.token}]]></pword>" +
                        "</login></body></msg>"
                socketClient.send(loginXml)
            }

            is AqwEvent.LoginResponseReceived -> {
                onLog?.invoke("Login handshake accepted. Joining initial room...")
                socketClient.send("%xt%zm%firstJoin%1%")
                socketClient.send("%xt%zm%cmd%1%ignoreList%\$clearAll%")
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
                _allMonsters.value = event.monsters

                playerState.playersInMap.clear()
                playerState.roomUserIds.clear()
                if (playerState.roomUserId > 0) {
                    playerState.roomUserIds.add(playerState.roomUserId)
                }
                playerState.droppedItems.clear()
                for (op in event.otherPlayers) {
                    playerState.playersInMap[op.username] = op
                    if (op.roomUserId > 0 && !playerState.roomUserIds.contains(op.roomUserId)) {
                        playerState.roomUserIds.add(op.roomUserId)
                    }
                    if (op.username.equals(playerState.followedPlayer, ignoreCase = true)) {
                        playerState.followedPlayerCell = op.cell
                    }
                }

                onLog?.invoke("Moved to ${event.areaName} [${event.playerCell}, ${event.playerPad}]")
                socketClient.send("%xt%zm%retrieveUserDatas%${event.areaId}%${playerState.roomUserId}%")
            }

            is AqwEvent.YouJoinedMap -> {
                playerState.isJoiningMap = false
                socketClient.send("%xt%zm%retrieveUserDatas%${playerState.areaId}%${playerState.roomUserId}%")
            }

            is AqwEvent.UserDatasLoaded -> {
                if (event.staffList.isNotEmpty()) {
                    onLog?.invoke("[SECURITY] Staff / Moderator detected in room: ${event.staffList.joinToString()}")
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
                        onLog?.invoke("Bank loaded (${bankItems.size} items)")
                    }
                }
                socketClient.send("%xt%zm%retrieveInventory%${playerState.areaId}%${playerState.roomUserId}%")
            }

            is AqwEvent.SingleUserDataLoaded -> {
                if (event.isStaff) {
                    onLog?.invoke("[SECURITY] Staff / Moderator entered room: ${event.username} (Access Level: ${event.accessLevel})")
                }
            }

            is AqwEvent.InventoryLoaded -> {
                playerState.inventory.clear()
                playerState.inventory.addAll(event.items)
                for (f in event.factions) {
                    playerState.addFaction(f)
                }
                _isCharLoaded.value = true
                onLog?.invoke("Character inventory loaded (${event.items.size} items, ${event.factions.size} factions)")
            }

            is AqwEvent.SkillsLoaded -> {
                playerState.skills.clear()
                playerState.skills.addAll(event.skills)
                onLog?.invoke("Skills loaded: ${event.skills.joinToString { it.name }}")
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
                    _allMonsters.value = _allMonsters.value.map { mon ->
                        val updatedHp = event.monsterHpMap[mon.monMapId]
                        if (updatedHp != null) {
                            mon.copy(currentHp = updatedHp, isAlive = updatedHp > 0)
                        } else mon
                    }
                }
                for (aura in event.auras) {
                    val auraObj = aura.first
                    val auraName = auraObj.name
                    val tInf = aura.second

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
                        _allMonsters.value.firstOrNull { it.monMapId == monId || "m:${it.monMapId}" == tInf }
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

                for (rem in event.aurasRemoved) {
                    val auraName = rem.first
                    val tInf = rem.second

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
                        _allMonsters.value.firstOrNull { it.monMapId == monId || "m:${it.monMapId}" == tInf }
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

                commands.jumpCell(playerState.cell, playerState.pad)
                logCallback?.invoke("Respawn complete. Current at ${playerState.cell} [${playerState.pad}]")
            }

            is AqwEvent.MonsterStateUpdated -> {
                _allMonsters.value = _allMonsters.value.map { mon ->
                    if (mon.monMapId == event.monMapId) {
                        mon.copy(
                            currentHp = event.hp ?: mon.currentHp,
                            isAlive = event.isAlive ?: mon.isAlive
                        )
                    } else mon
                }
            }

            is AqwEvent.PlayerStateUpdated -> {
                if (event.username.equals(playerState.username, ignoreCase = true)) {
                    event.hp?.let { playerState.currentHp = it }
                    event.maxHp?.let { playerState.maxHp = it }
                    event.mp?.let { playerState.mp = it }
                    event.inCombat?.let { playerState.isInCombat = it }
                    event.cell?.let { playerState.cell = it }
                    event.pad?.let { playerState.pad = it }
//                    if (event.hp != null && event.hp <= 0 && !playerState.isDead) {
//                        triggerDeathHandler()
//                    }
                } else {
                    val p = playerState.playersInMap[event.username]
                    if (p != null) {
                        event.hp?.let { p.hp = it }
                        event.maxHp?.let { p.maxHp = it }
                        event.mp?.let { p.mp = it }
                        event.cell?.let { p.cell = it }
                        event.pad?.let { p.pad = it }
                    } else if (event.username.isNotEmpty()) {
                        playerState.playersInMap[event.username] = AqwOtherPlayer(
                            username = event.username,
                            cell = event.cell ?: "Enter",
                            pad = event.pad ?: "Spawn",
                            hp = event.hp ?: 100,
                            maxHp = event.maxHp ?: 100,
                            mp = event.mp ?: 100
                        )
                    }
                    if (event.username.equals(
                            playerState.followedPlayer,
                            ignoreCase = true
                        ) && event.cell != null
                    ) {
                        playerState.followedPlayerCell = event.cell
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
                onLog?.invoke("Item drop: ${event.itemName} x${event.qty}")
            }

            is AqwEvent.ShopLoaded -> {
                val existingIdx =
                    playerState.loadedShops.indexOfFirst { it.shopId == event.shop.shopId }
                if (existingIdx != -1) {
                    playerState.loadedShops[existingIdx] = event.shop
                } else {
                    playerState.loadedShops.add(event.shop)
                }
                onLog?.invoke("Shop loaded: ${event.shop.shopName} (${event.shop.items.size} items)")
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
                onLog?.invoke("Bought $shopItemName x${event.qty}")
                commands.quest.triggerAutoQuestCheck()
            }

            is AqwEvent.ItemSold -> {
                playerState.gold += event.goldAmount
                playerState.goldFarmed += event.goldAmount
                val invItem =
                    playerState.inventory.firstOrNull { it.charItemId == event.charItemId }
                if (invItem != null) {
                    if (event.qtyNow <= 0) {
                        playerState.inventory.remove(invItem)
                        onLog?.invoke("Sold ${invItem.name} x${event.qtySold} (Remaining: 0, Gold: +${event.goldAmount})")
                    } else {
                        invItem.qty = event.qtyNow
                        onLog?.invoke("Sold ${invItem.name} x${event.qtySold} (Remaining: ${event.qtyNow}, Gold: +${event.goldAmount})")
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
//                onLog?.invoke("Gained +${event.gold} gold, +${event.exp} exp, +${event.rep} rep")
            }

            is AqwEvent.ItemsAdded -> {
                for (added in event.items) {
                    playerState.droppedItems.removeAll { it.itemId == added.itemId }
                    val wasRecentlyPicked = commands.item.recentlyPickedDropIds.remove(added.itemId)
                    if (added.isTemp || added.charItemId == 0) {
                        val existing = playerState.getItemTempById(added.itemId)
                        if (existing != null) {
                            if (!wasRecentlyPicked) {
                                existing.qty += added.qty
                            }
                        } else {
                            playerState.tempInventory.add(added)
                        }
                        onLog?.invoke(
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
                        onLog?.invoke("Added item: ${added.name.ifEmpty { "${added.itemId}" }} (Qty now: ${added.qty})")
                    }
                }
                commands.quest.triggerAutoQuestCheck()
            }

            is AqwEvent.ItemsTurnedIn -> {
                for (deduction in event.deductions) {
                    val (itemId, qty) = deduction
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
                onLog?.invoke("TurnIn deducted: ${event.deductions.joinToString { "${it.first} x${it.second}" }}")
                commands.quest.triggerAutoQuestCheck()
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
                onLog?.invoke("Loaded ${event.quests.size} quest details")
                commands.quest.triggerAutoQuestCheck()
            }

            is AqwEvent.QuestAccepted -> {
                if (event.success) {
                    playerState.activeQuestIds.add(event.questId)
                    playerState.failedQuestIds.remove(event.questId)
                    if (playerState.loadedQuests.none { it.questId == event.questId }) {
                        socketClient.send("%xt%zm%getQuests%${playerState.areaId}%${event.questId}%")
                    }
                    onLog?.invoke("Quest accepted: ${event.questId}")
                    if (playerState.registeredAutoQuestIds.contains(event.questId)) {
                        commands.quest.triggerAutoQuestCheck()
                    }
                } else {
                    playerState.failedQuestIds.add(event.questId)
                    onLog?.invoke("Failed to accept quest: ${event.questId}")
                }
            }

            is AqwEvent.QuestCompleted -> {
                if (event.success) {
                    playerState.activeQuestIds.remove(event.questId)

                    // Decrease or remove consumed turn-in items
                    val quest = playerState.loadedQuests.firstOrNull { it.questId == event.questId }
                    if (quest != null) {
                        for (req in quest.turnInItems) {
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

                    onLog?.invoke("Quest completed: ${event.questId} - ${event.questName} (+${event.rep} rep)")
                    commands.quest.triggerAutoQuestCheck()

                    if (playerState.registeredAutoQuestIds.contains(event.questId)) {
                        sessionScope.launch {
                            delay(500.milliseconds)
                            commands.quest.acceptQuest(event.questId)
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
                    onLog?.invoke("Quest complete failed: ${event.questId} (${event.msg})")
                }
            }

            is AqwEvent.FactionAdded -> {
                playerState.addFaction(event.faction)
                onLog?.invoke("Faction added: ${event.faction.name} (Rep: ${event.faction.rep})")
            }

            is AqwEvent.AurasCleared -> {
                playerState.removeAllAuras()
                _allMonsters.value.forEach { it.auras.clear() }
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
                onLog?.invoke("Scroll/potion equipped (CD: ${event.cd}s)")
            }

            is AqwEvent.UserEnteredRoom -> {
                if (event.isMe && event.userId > 0) {
                    playerState.roomUserId = event.userId
                }
                if (event.userId > 0) {
                    if (!playerState.roomUserIds.contains(event.userId)) {
                        playerState.roomUserIds.add(event.userId)
                    }
                    if (!event.isMe && event.username.isNotEmpty()) {
                        val p = playerState.playersInMap.getOrPut(event.username) {
                            AqwOtherPlayer(username = event.username)
                        }
                        p.roomUserId = event.userId
                    }
                    onLog?.invoke("User entered room (ID: ${event.userId}${if (event.username.isNotEmpty()) ", name: ${event.username}" else ""})")
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
                        onLog?.invoke("User left room: ${removed.key} (ID: ${event.userId})")
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
                onLog?.invoke("[${event.channel.uppercase()}] ${event.sender}: ${event.message}")
            }

            is AqwEvent.WhisperMessage -> {
                onLog?.invoke("[WHISPER] ${event.sender}: ${event.message}")
            }

            is AqwEvent.ServerBroadcast -> {
                onLog?.invoke("[Server] ${event.message}")
            }

            is AqwEvent.Warning -> {
//                if (event.isSpamWarning.not())
                onLog?.invoke("[Warning] ${event.message}")
            }

            is AqwEvent.AfkNotice -> {
                onLog?.invoke("[AFK] Server marked status as Away From Keyboard")
            }

            is AqwEvent.InvalidSessionNotice -> {
                onLog?.invoke("[Session] Invalid session reported by server")
            }

            is AqwEvent.LoggedOut -> {
                onLog?.invoke("Session logged out by server")
                stop()
            }

            is AqwEvent.PartyInviteReceived -> {
                latestPartyId = event.partyId
                onLog?.invoke("Party invite received from ${event.owner} (PID: ${event.partyId})")
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

        logCallback?.invoke("Player DIED! Respawn countdown started (${respawnTime}s)...")

        deathHandlerJob?.cancel()
        deathHandlerJob = sessionScope.launch(Dispatchers.IO) {
            for (i in respawnTime downTo 1) {
                if (!socketClient.isConnected.value) break
                delay(1000.milliseconds)
            }
            if (socketClient.isConnected.value && playerState.isDead) {
                commands.resurrectPlayer()
                commands.jumpCell(playerState.cell, playerState.pad)
            }
        }
    }

    fun getCooldowns(): Map<Int, Double> {
        val result = mutableMapOf<Int, Double>()
        for (i in 0..5) {
            val skill = commands.getSkill(i)
            result[i] = skill?.remainingCooldownMs()?.toDouble() ?: 0.0
        }
        return result
    }

    val lastTargetMonster: String
        get() = commands.lastTargetMonster

    fun stop() {
        unregister(this)
        commands.stopAggro()
        playerState.isJoiningMap = false
        deathHandlerJob?.cancel()
        deathHandlerJob = null
        packetListenerJob?.cancel()
        packetListenerJob = null
        _isCharLoaded.value = false
        _allMonsters.value = emptyList()
        socketClient.disconnect()
    }
}
