package froztt13.python.aqw.core.engine.commands

import froztt13.python.aqw.core.model.AqwPlayerState
import froztt13.python.aqw.core.network.AqwSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlin.time.Duration.Companion.milliseconds

/**
 * Handles quest acceptance, progress tracking, turn-ins, auto-quest registration, and queries.
 */
class AqwQuestCommands(
    private val client: AqwSocketClient,
    private val playerState: AqwPlayerState,
    private val leaveCombat: suspend (safeLeave: Boolean) -> Boolean = { false },
    private val getItemQty: (itemId: Int, isTemp: Boolean) -> Int = { _, _ -> 0 },
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val autoQuestMutex = Mutex()

    suspend fun acceptQuest(questId: Int): Boolean {
        val packet = "%xt%zm%acceptQuest%${playerState.areaId}%${questId}%"
        return client.send(packet)
    }

    suspend fun ensureAcceptQuest(
        questId: Int,
        maxRetries: Int = 5,
        retryDelayMs: Long = 1000L
    ): Boolean {
        var retries = 0
        while (questNotInProgress(questId) && client.isConnected.value && retries < maxRetries) {
            if (playerState.failedQuestIds.contains(questId)) return false
            acceptQuest(questId)
            delay(retryDelayMs.milliseconds)
            retries++
        }
        return questInProgress(questId)
    }

    suspend fun turnInQuest(questId: Int, itemId: Int = -1, qty: Int = 1): Boolean {
        if (playerState.isInCombat) {
            leaveCombat(true)
        }
        val packet =
            "%xt%zm%tryQuestComplete%${playerState.areaId}%${questId}%${itemId}%false%${qty}%wvz%"
        return client.send(packet)
    }

    suspend fun ensureTurnInQuest(
        questId: Int,
        itemId: Int = -1,
        qty: Int = 1,
        maxRetries: Int = 5,
        retryDelayMs: Long = 1000L
    ): Boolean {
        var retries = 0
        while (questInProgress(questId) && client.isConnected.value && retries < maxRetries) {
            if (playerState.failedQuestIds.contains(questId)) return false
            turnInQuest(questId, itemId, qty)
            delay(retryDelayMs.milliseconds)
            retries++
        }
        return questNotInProgress(questId)
    }

    fun registerQuest(questId: Int) {
        playerState.registeredAutoQuestIds.add(questId)
        triggerAutoQuestCheck()
    }

    fun unregisterQuest(questId: Int) {
        playerState.registeredAutoQuestIds.remove(questId)
    }

    fun triggerAutoQuestCheck() {
        if (playerState.registeredAutoQuestIds.isEmpty() || !client.isConnected.value) return
        coroutineScope.launch {
            checkAndProcessRegisteredQuests()
        }
    }

    suspend fun checkAndProcessRegisteredQuests() {
        if (playerState.registeredAutoQuestIds.isEmpty() || !client.isConnected.value) return
        if (!autoQuestMutex.tryLock()) return
        try {
            val registeredList = playerState.registeredAutoQuestIds.toList()
            for (questId in registeredList) {
                if (!client.isConnected.value) break

                // 1. Ensure quest definition is loaded
                if (playerState.loadedQuests.none { it.questId == questId }) {
                    getQuests(listOf(questId))
                    delay(400.milliseconds)
                }

                // 2. Ensure quest is accepted / in progress
                if (questNotInProgress(questId)) {
                    acceptQuest(questId)
                    delay(400.milliseconds)
                }

                // 3. Check if ready to turn in
                if (canTurnInQuest(questId)) {
                    turnInQuest(questId)
                    delay(800.milliseconds)
                }
            }
        } finally {
            autoQuestMutex.unlock()
        }
    }

    fun canTurnInQuest(questId: Int): Boolean {
        if (playerState.failedQuestIds.contains(questId)) return false
        if (playerState.oneTimeQuestIds.contains(questId)) return false
        val quest = playerState.loadedQuests.firstOrNull { it.questId == questId } ?: return false
        if (quest.turnInItems.isEmpty()) return false
        for (req in quest.turnInItems) {
            val hasEnough =
                (getItemQty(req.itemId, false) + getItemQty(req.itemId, true)) >= req.qty
            if (!hasEnough) return false
        }
        return true
    }

    fun isGreenQuest(questId: Int): Boolean {
        return playerState.missingTurnInItemQuestIds.contains(questId)
    }

    fun isCompletedBefore(questId: Int): Boolean {
        return playerState.oneTimeQuestIds.contains(questId)
    }

    fun questInProgress(questId: Int): Boolean {
        return playerState.activeQuestIds.contains(questId)
    }

    fun questNotInProgress(questId: Int): Boolean {
        return !questInProgress(questId)
    }

    suspend fun getQuests(questIds: List<Int>): Boolean {
        val packet = "%xt%zm%getQuests%${playerState.areaId}%${questIds.joinToString(",")}%"
        return client.send(packet)
    }
}
