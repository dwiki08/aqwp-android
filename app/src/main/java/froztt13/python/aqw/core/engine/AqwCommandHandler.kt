package froztt13.python.aqw.core.engine

import froztt13.python.aqw.core.engine.commands.AqwCombatCommands
import froztt13.python.aqw.core.engine.commands.AqwItemCommands
import froztt13.python.aqw.core.engine.commands.AqwMovementCommands
import froztt13.python.aqw.core.engine.commands.AqwQuestCommands
import froztt13.python.aqw.core.engine.commands.AqwSocialCommands
import froztt13.python.aqw.core.model.AqwItem
import froztt13.python.aqw.core.model.AqwMonster
import froztt13.python.aqw.core.model.AqwPlayerState
import froztt13.python.aqw.core.model.AqwShop
import froztt13.python.aqw.core.model.AqwSkill
import froztt13.python.aqw.core.network.AqwSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Main command handler coordinating modular sub-commands:
 * - [movement]: Map transfer, cell jumps, pathing, leaving combat.
 * - [combat]: Skill rotation, auto-attack, aggro, cooldown tracking, resurrection.
 * - [quest]: Quest acceptance, turn-in, verification, and registration.
 * - [item]: Inventory management, shops, banking, equipping, and drop collection.
 * - [social]: Party invite/accept, zone/whisper chat, dungeon queues, and raw packets.
 */
class AqwCommandHandler(
    private val client: AqwSocketClient,
    private val playerState: AqwPlayerState,
    private val monstersProvider: () -> List<AqwMonster> = { emptyList() },
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {

    // ==========================================
    // SUB-COMMAND INSTANCES
    // ==========================================

    val combat: AqwCombatCommands = AqwCombatCommands(
        client = client,
        playerState = playerState,
        monstersProvider = monstersProvider,
        coroutineScope = coroutineScope,
        jumpCell = { cell, pad -> movement.jumpCell(cell, pad) },
        jumpToMonster = { monName -> movement.jumpToMonster(monName, byAliveMonster = true) }
    )

    val movement: AqwMovementCommands = AqwMovementCommands(
        client = client,
        playerState = playerState,
        monstersProvider = monstersProvider,
        ensureAlive = { combat.ensureAlive() },
        rest = { combat.rest() },
        stopAggro = { combat.stopAggro() }
    )

    val item: AqwItemCommands = AqwItemCommands(
        client = client,
        playerState = playerState,
        leaveCombat = { safeLeave -> movement.leaveCombat(safeLeave) },
        onScrollEquipped = { scrollId -> combat.scrollId = scrollId },
        onItemUpdated = { quest.triggerAutoQuestCheck() }
    )

    val quest: AqwQuestCommands = AqwQuestCommands(
        client = client,
        playerState = playerState,
        leaveCombat = { safeLeave -> movement.leaveCombat(safeLeave) },
        getItemQty = { itemId, isTemp -> item.getItemQty(itemId, isTemp) },
        coroutineScope = coroutineScope
    )

    val social: AqwSocialCommands = AqwSocialCommands(client = client)

    // ==========================================
    // AGGRO DELEGATIONS
    // ==========================================

    val aggroMonsId: MutableList<String> get() = combat.aggroMonsId
    var aggroDelayMs: Long
        get() = combat.aggroDelayMs
        set(value) {
            combat.aggroDelayMs = value
        }
    val isAggroRunning: Boolean get() = combat.isAggroRunning

    fun aggro(monsId: List<String>, delayMs: Long = 1500L) = combat.aggro(monsId, delayMs)
    fun stopAggro() = combat.stopAggro()
    fun stop_aggro() = combat.stop_aggro()

    // ==========================================
    // MOVEMENT DELEGATIONS
    // ==========================================

    fun isInMap(mapName: String): Boolean = movement.isInMap(mapName)
    fun isNotInMap(mapName: String): Boolean = movement.isNotInMap(mapName)
    fun is_in_map(mapName: String): Boolean = movement.is_in_map(mapName)
    fun is_not_in_map(mapName: String): Boolean = movement.is_not_in_map(mapName)

    suspend fun joinMap(
        mapName: String,
        roomNumber: Int? = null,
        cell: String = "Enter",
        pad: String = "Spawn",
        safeLeave: Boolean = true
    ): Boolean = movement.joinMap(mapName, roomNumber, cell, pad, safeLeave)

    suspend fun join_map(
        mapName: String,
        roomNumber: Int? = null,
        safeLeave: Boolean = true
    ): Boolean = movement.join_map(mapName, roomNumber, safeLeave)

    suspend fun joinHouse(houseName: String, safeLeave: Boolean = true): Boolean =
        movement.joinHouse(houseName, safeLeave)

    suspend fun jumpCell(cell: String, pad: String = "Spawn"): Boolean =
        movement.jumpCell(cell, pad)

    suspend fun walkTo(x: Int, y: Int, speed: Int = 8): Boolean =
        movement.walkTo(x, y, speed)

    suspend fun gotoPlayer(targetUsername: String): Boolean {
        leaveCombat()
        return movement.gotoPlayer(targetUsername)
    }

    suspend fun leaveCombat(safeLeave: Boolean = true): Boolean =
        movement.leaveCombat(safeLeave)

    fun findBestCell(
        monsterName: String,
        byMostMonster: Boolean = true,
        byAliveMonster: Boolean = false
    ): String? = movement.findBestCell(monsterName, byMostMonster, byAliveMonster)

    suspend fun jumpToMonster(
        monsterName: String,
        byMostMonster: Boolean = true,
        byAliveMonster: Boolean = false
    ): Boolean = movement.jumpToMonster(monsterName, byMostMonster, byAliveMonster)

    // ==========================================
    // COMBAT & SKILL DELEGATIONS
    // ==========================================

    var scrollId: String
        get() = combat.scrollId
        set(value) {
            combat.scrollId = value
        }

    val lastTargetMonster: String
        get() = combat.lastTargetMonster

    fun getSkill(index: Int): AqwSkill? = combat.getSkill(index)
    fun canUseSkill(index: Int): Boolean = combat.canUseSkill(index)
    fun checkIsSkillSafe(index: Int): Boolean = combat.checkIsSkillSafe(index)
    fun updateNextUse(index: Int, staticCooldownMs: Long? = null) =
        combat.updateNextUse(index, staticCooldownMs)

    suspend fun resurrectPlayer(): Boolean = combat.resurrectPlayer()
    suspend fun ensureAlive(timeoutSeconds: Int = 11): Boolean =
        combat.ensureAlive(timeoutSeconds)

    suspend fun useSkill(
        index: Int,
        targetMonMapId: String? = null,
        reloadDelayMs: Long = 200L
    ): Boolean = combat.useSkill(index, targetMonMapId, reloadDelayMs)

    suspend fun useSkillToPlayer(skillIndex: Int, maxTarget: Int = 1): Boolean =
        combat.useSkillToPlayer(skillIndex, maxTarget)

    suspend fun waitUseSkill(
        index: Int,
        targetMonMapId: String? = null,
        timeoutMs: Long = 5000L
    ): Boolean = combat.waitUseSkill(index, targetMonMapId, timeoutMs)

    suspend fun attack(targetMonMapId: String): Boolean = combat.attack(targetMonMapId)
    suspend fun taunt(monMapId: String): Boolean = combat.taunt(monMapId)
    suspend fun doPwd(monMapId: String): Boolean = combat.doPwd(monMapId)

    fun isMonsterAlive(monsterNameOrId: String = "*", onlyCurrentCell: Boolean = true): Boolean =
        combat.isMonsterAlive(monsterNameOrId, onlyCurrentCell)

    fun getMonster(monsterNameOrId: String): AqwMonster? = combat.getMonster(monsterNameOrId)
    fun getMonsterHp(monsterNameOrId: String): Int = combat.getMonsterHp(monsterNameOrId)
    fun getMonsterHpPercentage(monsterNameOrId: String): Int =
        combat.getMonsterHpPercentage(monsterNameOrId)

    suspend fun rest(): Boolean = combat.rest()

    suspend fun killMonster(
        monsterNameOrId: String = "*",
        skills: List<Int> = listOf(0, 1, 2, 0, 3, 4),
        delayMs: Long = 500L,
        timeoutMs: Long = 120_000L,
        hunt: Boolean = false,
        isStopRequested: () -> Boolean = { false }
    ): Boolean =
        combat.killMonster(monsterNameOrId, skills, delayMs, timeoutMs, hunt, isStopRequested)

    suspend fun killMonster(
        target: AqwMonster,
        skills: List<Int> = listOf(0, 1, 2, 0, 3, 4),
        delayMs: Long = 250L,
        timeoutMs: Long = 60000L,
        hunt: Boolean = false,
        isStopRequested: () -> Boolean = { false }
    ): Boolean = combat.killMonster(target, skills, delayMs, timeoutMs, hunt, isStopRequested)

    // ==========================================
    // QUEST DELEGATIONS
    // ==========================================

    suspend fun acceptQuest(questId: Int): Boolean = quest.acceptQuest(questId)

    suspend fun ensureAcceptQuest(
        questId: Int,
        maxRetries: Int = 5,
        retryDelayMs: Long = 1000L
    ): Boolean = quest.ensureAcceptQuest(questId, maxRetries, retryDelayMs)

    suspend fun turnInQuest(questId: Int, itemId: Int = -1, qty: Int = 1): Boolean =
        quest.turnInQuest(questId, itemId, qty)

    suspend fun ensureTurnInQuest(
        questId: Int,
        itemId: Int = 0,
        qty: Int = 1,
        maxRetries: Int = 5,
        retryDelayMs: Long = 1000L
    ): Boolean = quest.ensureTurnInQuest(questId, itemId, qty, maxRetries, retryDelayMs)

    fun registerQuest(questId: Int) = quest.registerQuest(questId)
    fun unregisterQuest(questId: Int) = quest.unregisterQuest(questId)
    fun triggerAutoQuestCheck() = quest.triggerAutoQuestCheck()
    suspend fun checkAndProcessRegisteredQuests() = quest.checkAndProcessRegisteredQuests()
    fun canTurnInQuest(questId: Int): Boolean = quest.canTurnInQuest(questId)
    fun isGreenQuest(questId: Int): Boolean = quest.isGreenQuest(questId)
    fun isCompletedBefore(questId: Int): Boolean = quest.isCompletedBefore(questId)
    fun questInProgress(questId: Int): Boolean = quest.questInProgress(questId)
    fun questNotInProgress(questId: Int): Boolean = quest.questNotInProgress(questId)
    suspend fun getQuests(questIds: List<Int>): Boolean = quest.getQuests(questIds)

    // ==========================================
    // ITEM & SHOP DELEGATIONS
    // ==========================================

    suspend fun loadShop(shopId: Int): Boolean = item.loadShop(shopId)
    fun getLoadedShop(shopId: Int): AqwShop? = item.getLoadedShop(shopId)

    suspend fun ensureLoadShop(shopId: Int, timeoutMs: Long = 5000L): AqwShop? =
        item.ensureLoadShop(shopId, timeoutMs)

    suspend fun buyItem(shopId: Int, itemName: String, qty: Int = 1): Boolean =
        item.buyItem(shopId, itemName, qty)

    suspend fun buyItem(shopId: Int, itemId: Int, shopItemId: Int, qty: Int = 1): Boolean =
        item.buyItem(shopId, itemId, shopItemId, qty)

    suspend fun sellItem(itemName: String, qty: Int = 1): Boolean =
        item.sellItem(itemName, qty)

    suspend fun sellItem(itemId: Int, charItemId: Int, qty: Int = 1): Boolean =
        item.sellItem(itemId, charItemId, qty)

    suspend fun sellItem(itemObj: AqwItem, qty: Int = 1): Boolean =
        item.sellItem(itemObj, qty)

    suspend fun bankToInv(itemName: String): Boolean = item.bankToInv(itemName)
    suspend fun bankToInv(itemNames: List<String>): Boolean = item.bankToInv(itemNames)
    suspend fun invToBank(itemName: String): Boolean = item.invToBank(itemName)
    suspend fun invToBank(itemNames: List<String>): Boolean = item.invToBank(itemNames)

    suspend fun equipItem(itemName: String): Boolean = item.equipItem(itemName)
    suspend fun equipItem(itemId: Int): Boolean = item.equipItem(itemId)
    suspend fun equipScroll(itemName: String): Boolean = item.equipScroll(itemName)
    suspend fun equipScroll(itemId: Int, sMeta: String = "0"): Boolean =
        item.equipScroll(itemId, sMeta)

    suspend fun getItemDrop(itemId: Int): Boolean = item.getItemDrop(itemId)
    suspend fun getItemDrop(itemName: String): Boolean = item.getItemDrop(itemName)
    suspend fun getMapItem(mapItemId: Int, qty: Int = 1): Boolean =
        item.getMapItem(mapItemId, qty)

    fun hasItem(itemName: String, qty: Int = 1, isTemp: Boolean = false): Boolean =
        item.hasItem(itemName, qty, isTemp)

    fun hasItemInBank(itemName: String, qty: Int = 1): Boolean =
        item.hasItemInBank(itemName, qty)

    fun hasItemInInventoryOrBank(
        itemName: String,
        qty: Int = 1,
        isTemp: Boolean = false
    ): Boolean = item.hasItemInInventoryOrBank(itemName, qty, isTemp)

    fun getItemQty(itemName: String, isTemp: Boolean = false): Int =
        item.getItemQty(itemName, isTemp)

    fun getItemQty(itemId: Int, isTemp: Boolean = false): Int =
        item.getItemQty(itemId, isTemp)

    // ==========================================
    // SOCIAL & UTILITY DELEGATIONS
    // ==========================================

    suspend fun partyInvite(targetUsername: String): Boolean = social.partyInvite(targetUsername)
    suspend fun partyAccept(partyId: Int): Boolean = social.partyAccept(partyId)
    suspend fun dungeonQueue(dungeonName: String): Boolean = social.dungeonQueue(dungeonName)
    suspend fun sendChat(message: String, channel: String = "zone"): Boolean =
        social.sendChat(message, channel)

    suspend fun sendRaw(packet: String): Boolean = social.sendRaw(packet)
}
