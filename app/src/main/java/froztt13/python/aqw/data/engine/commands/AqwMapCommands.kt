package froztt13.python.aqw.data.engine.commands

import froztt13.python.aqw.data.network.AqwSocketClient
import froztt13.python.aqw.domain.model.AqwMonster
import froztt13.python.aqw.domain.model.AqwPlayerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration.Companion.milliseconds

/**
 * Handles map state, monster tracking, cell jumps, map transitions, and pathing navigation.
 */
class AqwMapCommands(
    private val client: AqwSocketClient,
    private val playerState: AqwPlayerState,
    private val ensureAlive: suspend () -> Boolean = { true },
    private val rest: suspend () -> Boolean = { false },
    private val stopAggro: () -> Unit = {}
) {

    companion object {
        private const val TAG = "AqwMapCommands"
    }

    // ==========================================
    // MONSTER STATE MANAGEMENT
    // ==========================================

    private val _allMonsters = MutableStateFlow<List<AqwMonster>>(emptyList())
    val allMonsters: StateFlow<List<AqwMonster>> = _allMonsters.asStateFlow()

    /**
     * Returns monsters located in [cell].
     *
     * @param cell Target cell name (defaults to current player cell).
     * @param alive If true, filters only monsters that are alive and have HP > 0.
     */
    fun getMonsters(cell: String = playerState.cell, alive: Boolean = true): List<AqwMonster> {
        return allMonsters.value.filter {
            it.frame.equals(cell, ignoreCase = true) && (!alive || (it.isAlive && it.currentHp > 0))
        }
    }

    /**
     * Returns all monsters (both alive and dead) located in [cell].
     */
    fun getCellMonsters(cell: String = playerState.cell): List<AqwMonster> {
        return getMonsters(cell, alive = false)
    }

    /**
     * Checks if there is at least one alive monster with HP > 0 in [cell].
     */
    fun hasAliveMonsters(cell: String = playerState.cell): Boolean {
        return getMonsters(cell, alive = true).isNotEmpty()
    }

    fun setMonsters(monsters: List<AqwMonster>) {
        _allMonsters.value = monsters
    }

    fun clearMonsters() {
        _allMonsters.value = emptyList()
    }

    fun updateMonstersHp(hpMap: Map<String, Int>) {
        if (hpMap.isEmpty()) return
        _allMonsters.value = _allMonsters.value.map { mon ->
            val updatedHp = hpMap[mon.monMapId]
            if (updatedHp != null) {
                mon.copy(currentHp = updatedHp, isAlive = updatedHp > 0)
            } else mon
        }
    }

    fun updateMonsterState(monMapId: String, hp: Int?, isAlive: Boolean?) {
        _allMonsters.value = _allMonsters.value.map { mon ->
            if (mon.monMapId == monMapId) {
                mon.copy(
                    currentHp = hp ?: mon.currentHp,
                    isAlive = isAlive ?: mon.isAlive
                )
            } else mon
        }
    }

    fun clearMonsterAuras() {
        _allMonsters.value.forEach { it.auras.clear() }
    }

    // ==========================================
    // MAP & MOVEMENT OPERATIONS
    // ==========================================

    fun isInMap(mapName: String): Boolean {
        val current = playerState.mapName.ifBlank { playerState.areaName }
        if (current.equals(mapName, ignoreCase = true)) return true
        val curClean = current.substringBefore("-").trim()
        val targetClean = mapName.substringBefore("-").trim()
        return curClean.equals(targetClean, ignoreCase = true)
    }

    fun isNotInMap(mapName: String): Boolean = !isInMap(mapName)

    suspend fun joinMap(
        mapName: String,
        roomNumber: Int? = null,
        cell: String = "Enter",
        pad: String = "Spawn",
        safeLeave: Boolean = true
    ): Boolean {
        if (!ensureAlive()) {
            return false
        }
        stopAggro()
        if (isInMap(mapName)) {
            if ((cell != "Enter" || pad != "Spawn") && !playerState.cell.equals(
                    cell,
                    ignoreCase = true
                )
            ) {
                jumpCell(cell, pad)
            }
            return true
        }

        playerState.isJoiningMap = true
        if (safeLeave) {
            leaveCombat(safeLeave = true)
        }

        val targetRoom = roomNumber ?: playerState.roomNumber
        val targetMap = if (targetRoom != null) "$mapName-$targetRoom" else mapName

        val packet = if (cell != "Enter" || pad != "Spawn") {
            "%xt%zm%cmd%1%tfer%${playerState.username}%${targetMap}%${cell}%${pad}%"
        } else {
            "%xt%zm%cmd%1%tfer%${playerState.username}%${targetMap}%"
        }

        val sent = client.send(packet)
        delay(3000.milliseconds)

        if (cell != "Enter" || pad != "Spawn") {
            if (!playerState.cell.equals(cell, ignoreCase = true)) {
                jumpCell(cell, pad)
            }
        }
        return sent
    }

    suspend fun joinHouse(houseName: String, safeLeave: Boolean = true): Boolean {
        if (!ensureAlive()) return false
        if (isInMap(houseName)) return true
        playerState.isJoiningMap = true
        if (safeLeave) leaveCombat(safeLeave = true)
        val packet = "%xt%zm%cmd%1%house%${houseName}%"
        return client.send(packet)
    }

    suspend fun jumpCell(cell: String, pad: String = "Spawn"): Boolean {
        val packet = "%xt%zm%moveToCell%${playerState.areaId}%${cell}%${pad}%"
        playerState.cell = cell
        playerState.pad = pad
        return client.send(packet)
    }

    suspend fun walkTo(x: Int, y: Int, speed: Int = 8): Boolean {
        val packet = "%xt%zm%mv%${playerState.areaId}%${x}%${y}%${speed}%"
        return client.send(packet)
    }

    suspend fun gotoPlayer(targetUsername: String): Boolean {
        val packet = "%xt%zm%cmd%1%goto%${targetUsername}%"
        return client.send(packet)
    }

    suspend fun leaveCombat(safeLeave: Boolean = true): Boolean {
        if (playerState.isInCombat) {
            jumpCell(playerState.cell, playerState.pad)
            delay(1000.milliseconds)
            if (playerState.isInCombat) {
                rest()
                delay(1000.milliseconds)
            }
        } else if (safeLeave) {
            jumpCell(playerState.cell, playerState.pad)
            delay(500.milliseconds)
        }
        return !playerState.isInCombat
    }

    private fun matchesMonster(mon: AqwMonster, query: String): Boolean {
        if (query == "*" || query.isBlank()) return true
        val targets = query.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (targets.isEmpty()) return true
        return targets.any { target ->
            mon.monMapId.equals(target, ignoreCase = true) ||
                    mon.name.contains(target, ignoreCase = true)
        }
    }

    fun findBestCell(
        monsterName: String,
        byMostMonster: Boolean = true,
        byAliveMonster: Boolean = false
    ): String? {
        val cleanName = if (monsterName.startsWith("id.", ignoreCase = true)) {
            monsterName.substringAfter("id.")
        } else monsterName

        val allMons = allMonsters.value
        val filtered = allMons.filter { mon ->
            val matchName = matchesMonster(mon, cleanName)
            if (byAliveMonster) matchName && mon.isAlive else matchName
        }

        if (filtered.isEmpty()) return null

        val counts = mutableMapOf<String, Int>()
        for (m in filtered) {
            counts[m.frame] = (counts[m.frame] ?: 0) + 1
        }
        return counts.maxByOrNull { it.value }?.key
    }

    suspend fun jumpToMonster(
        monsterName: String,
        byMostMonster: Boolean = true,
        byAliveMonster: Boolean = false
    ): Boolean {
        val cleanName = if (monsterName.startsWith("id.", ignoreCase = true)) {
            monsterName.substringAfter("id.")
        } else monsterName

        val allMons = allMonsters.value
        // If monster is already in the current cell and alive, do nothing
        val currentCellMonster = allMons.firstOrNull {
            matchesMonster(it, cleanName) &&
                    it.isAlive && it.frame.equals(playerState.cell, ignoreCase = true)
        }
        if (currentCellMonster != null) {
            return true
        }

        // Search for best cell
        val bestCell = findBestCell(cleanName, byMostMonster, byAliveMonster)
        if (bestCell != null) {
            if (bestCell.equals(playerState.cell, ignoreCase = true)) {
                return true
            }
            jumpCell(bestCell, "Left")
            delay(1000.milliseconds)
            return true
        }

        // Fallback: search any cell hosting this monster
        val anyMonster = allMons.firstOrNull {
            matchesMonster(it, cleanName) && it.isAlive
        }
        if (anyMonster != null && !anyMonster.frame.equals(playerState.cell, ignoreCase = true)) {
            jumpCell(anyMonster.frame, "Left")
            delay(1000.milliseconds)
            return true
        }

        return false
    }
}
