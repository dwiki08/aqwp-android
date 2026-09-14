package froztt13.python.aqw.core.engine.commands

import froztt13.python.aqw.core.model.AqwMonster
import froztt13.python.aqw.core.model.AqwPlayerState
import froztt13.python.aqw.core.network.AqwSocketClient
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Handles player movement, map transitions, cell jumps, and pathing navigation.
 */
class AqwMovementCommands(
    private val client: AqwSocketClient,
    private val playerState: AqwPlayerState,
    private val monstersProvider: () -> List<AqwMonster> = { emptyList() },
    private val ensureAlive: suspend () -> Boolean = { true },
    private val rest: suspend () -> Boolean = { false },
    private val stopAggro: () -> Unit = {}
) {

    fun isInMap(mapName: String): Boolean {
        val current = playerState.mapName.ifBlank { playerState.areaName }
        if (current.equals(mapName, ignoreCase = true)) return true
        val curClean = current.substringBefore("-").trim()
        val targetClean = mapName.substringBefore("-").trim()
        return curClean.equals(targetClean, ignoreCase = true)
    }

    fun isNotInMap(mapName: String): Boolean = !isInMap(mapName)

    fun is_in_map(mapName: String): Boolean = isInMap(mapName)
    fun is_not_in_map(mapName: String): Boolean = isNotInMap(mapName)

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

    suspend fun join_map(
        mapName: String,
        roomNumber: Int? = null,
        safeLeave: Boolean = true
    ): Boolean = joinMap(mapName = mapName, roomNumber = roomNumber, safeLeave = safeLeave)

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
            jumpCell("Enter", "Spawn")
            delay(1000.milliseconds)
            if (playerState.isInCombat) {
                rest()
                delay(1000.milliseconds)
            }
        } else if (safeLeave) {
            jumpCell("Enter", "Spawn")
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

        val allMons = monstersProvider()
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

        val allMons = monstersProvider()
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
