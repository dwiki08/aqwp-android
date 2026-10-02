package froztt13.python.aqw.data.engine.commands

import froztt13.python.aqw.data.network.AqwSocketClient
import froztt13.python.aqw.domain.model.AqwMonster
import froztt13.python.aqw.domain.model.AqwPlayerState
import froztt13.python.aqw.domain.model.AqwSkill
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Handles combat skills, auto-attack, aggro loops, cooldown tracking, and survivability.
 */
class AqwCombatCommands(
    private val client: AqwSocketClient,
    private val playerState: AqwPlayerState,
    private val monstersProvider: () -> List<AqwMonster> = { emptyList() },
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val jumpCell: suspend (cell: String, pad: String) -> Boolean = { _, _ -> false },
    private val jumpToMonster: suspend (monsterName: String) -> Boolean = { false }
) {
    companion object {
        private const val TAG = "AqwCombatCommands"
    }

    // ==========================================
    // AGGRO MANAGEMENT
    // ==========================================

    var isPaused: () -> Boolean = { false }

    private var aggroJob: Job? = null
    val aggroMonsId: MutableList<String> = mutableListOf()
    var aggroDelayMs: Long = 1500L
    var isAggroRunning: Boolean = false
        private set

    fun aggro(monsId: List<String>, delayMs: Long = 1500L) {
        isAggroRunning = true
        aggroMonsId.clear()
        aggroMonsId.addAll(monsId)
        aggroDelayMs = delayMs
        aggroJob?.cancel()
        aggroJob = coroutineScope.launch {
            while (isActive && isAggroRunning && client.isConnected.value) {
                if (isPaused()) {
                    delay(500.milliseconds)
                    continue
                }
                if (aggroMonsId.isNotEmpty() && playerState.areaId > 0) {
                    val aggroPacket =
                        "%xt%zm%aggroMon%${playerState.areaId}%${aggroMonsId.joinToString("%")}%"
                    client.send(aggroPacket)
                }
                delay(aggroDelayMs.milliseconds)
            }
        }
    }

    fun stopAggro() {
        isAggroRunning = false
        aggroMonsId.clear()
        aggroJob?.cancel()
        aggroJob = null
    }

    @Suppress("FunctionName")
    fun stop_aggro() = stopAggro()

    // ==========================================
    // SKILLS & COMBAT ACTIONS
    // ==========================================

    var scrollId: String = ""
    var lastTargetMonster: String = ""
    var skillReloadTime: Long = 0L

    fun getSkill(index: Int): AqwSkill? {
        val found = playerState.skills.firstOrNull { it.index == index }
            ?: playerState.skills.getOrNull(index)
        if (found != null) return found
        return null
    }

    fun canUseSkill(index: Int): Boolean {
        val skill = getSkill(index) ?: return false

        // Mana check
        val currentMana = playerState.mp
        val effectiveManaCost = skill.mpCost * playerState.manaCost
        if (currentMana < effectiveManaCost) {
            return false
        }

        // Cooldown check
        return skill.isReady()
    }

    fun checkIsSkillSafe(index: Int): Boolean {
        val equippedClass = playerState.inventory.firstOrNull {
            it.isEquipped && it.sType.equals(
                "Class",
                ignoreCase = true
            )
        }
            ?: playerState.inventory.firstOrNull {
                it.isEquipped && it.sMeta.contains(
                    "Class",
                    ignoreCase = true
                )
            }
        val className = equippedClass?.name?.lowercase() ?: ""

        val hpPct = if (playerState.maxHp > 0) {
            (playerState.currentHp.toDouble() / playerState.maxHp.toDouble()) * 100.0
        } else 100.0

        when {
            className.contains("void highlord") -> {
                if (index == 1 || index == 3) {
                    return hpPct > 50.0
                }
            }

            className.contains("scarlet sorceress") -> {
                if (index == 1 || index == 4) {
                    return hpPct > 50.0
                }
            }

            className.contains("dragon of time") -> {
                if (index == 1 || index == 3) {
                    return hpPct > 40.0
                }
            }

            className.contains("healer") -> {
                if (index == 2) {
                    return hpPct < 70.0
                }
            }
        }
        return true
    }

    fun updateNextUse(index: Int, staticCooldownMs: Long? = null) {
        val skill = getSkill(index) ?: return

        val baseCd = if (skill.cdMillis > 0.0) skill.cdMillis else (skill.cdSeconds * 1000.0)
        val effectiveCd = when {
            staticCooldownMs != null -> staticCooldownMs.toDouble()
//            index == 0 || skill.index == 0 -> baseCd
            else -> {
                val cdr = minOf(maxOf(playerState.cdReduction, 0.0), 0.5)
                baseCd * (1.0 - cdr)
            }
        }

        val nextUseTime = System.currentTimeMillis() + effectiveCd.toLong()
        skill.nextUseTimestamp = nextUseTime

        if (playerState.skills.none { it.index == skill.index }) {
            playerState.skills.add(skill)
        }

        val effectiveCost = (skill.mpCost * playerState.manaCost).toInt()
        if (effectiveCost > 0) {
            playerState.mp = maxOf(0, playerState.mp - effectiveCost)
        }
    }

    suspend fun resurrectPlayer(): Boolean {
        val sent =
            client.send("%xt%zm%resPlayerTimed%${playerState.areaId}%${playerState.roomUserId}%")
        playerState.apply {
            isDead = false
            currentHp = maxHp
            mp = 100
            isInCombat = false
//            cdReduction = 0.0
            manaCost = 1.0
            removeAllAuras()
            resetAllSkills()
        }
        skillReloadTime = 0L
        return sent
    }

    suspend fun ensureAlive(timeoutSeconds: Int = 11): Boolean {
        if (!playerState.isDead && playerState.currentHp > 0) {
            return true
        }
        val startTime = System.currentTimeMillis()
        val timeoutMs = timeoutSeconds * 1000L
        while (client.isConnected.value && (playerState.isDead || playerState.currentHp <= 0)) {
            if (System.currentTimeMillis() - startTime >= timeoutMs) {
                resurrectPlayer()
                return true
            }
            delay(500.milliseconds)
        }
        return !playerState.isDead
    }

    suspend fun useBuff(
        index: Int,
        reloadDelayMs: Long = 500L
    ): Boolean {
        if (!ensureAlive()) {
            return false
        }
        if (!canUseSkill(index) || !checkIsSkillSafe(index)) {
            return false
        }

        val skill = getSkill(index)
        val tgtType = skill?.tgt ?: "h"
        if (tgtType.equals("h", ignoreCase = true)) {
            return false
        }

        val waitReloadMs = skillReloadTime - System.currentTimeMillis()
        if (waitReloadMs > 0 && index != 0) {
            delay(waitReloadMs.milliseconds)
        }

        val usernameId = playerState.roomUserId
        val targetParam = when (tgtType) {
            "s" -> "a$index>p:$usernameId" // self
            "f" -> {
                val maxTarget = skill?.tgtMax ?: 1
                val userIds = playerState.roomUserIds.ifEmpty {
                    playerState.playersInMap.values.mapNotNull {
                        if (it.roomUserId > 0) it.roomUserId else if (it.userId > 0) it.userId else null
                    }
                }
                val targets = mutableListOf<String>()
                for (i in userIds) {
                    if (targets.size >= maxTarget - 1) break
                    targets.add("a$index>p:$i")
                }
                targets.add(0, "a$index>p:$usernameId")
                targets.distinct().joinToString(",")
            }

            else -> "a$index>p:$usernameId"
        }

        val sent = client.send("%xt%zm%gar%1%1%${targetParam}%wvz%")
        if (sent) {
            delay(200.milliseconds)
            updateNextUse(index)
            skillReloadTime = System.currentTimeMillis() + reloadDelayMs
            return true
        }
        return false
    }

    suspend fun useSkill(
        index: Int,
        targetMonMapId: String? = null,
        reloadDelayMs: Long = 500L
    ): Boolean {
        if (!ensureAlive()) {
            return false
        }
        if (!canUseSkill(index) || !checkIsSkillSafe(index)) {
            return false
        }

        val skill = getSkill(index)
        val tgtType = skill?.tgt ?: "h"
        if (!tgtType.equals("h", ignoreCase = true)) {
            return useBuff(index, reloadDelayMs)
        }

        val waitReloadMs = skillReloadTime - System.currentTimeMillis()
        if (waitReloadMs > 0 && index != 0) {
            delay(waitReloadMs.milliseconds)
        }

        val maxTarget = (skill?.tgtMax ?: 1).coerceAtLeast(1)
        val currentCellMons = monstersProvider().filter {
            it.frame.equals(
                playerState.cell,
                ignoreCase = true
            ) && it.isAlive && it.currentHp > 0
        }
        val sortedCellMons = currentCellMons
            .filter { it.monMapId.isNotBlank() }
            .sortedWith(
                compareBy(
                    { it.monMapId.toIntOrNull() ?: Int.MAX_VALUE },
                    { it.monMapId })
            )

        val primaryId = targetMonMapId?.takeIf { it.isNotBlank() }
            ?: sortedCellMons.firstOrNull()?.monMapId
            ?: "1"

        val otherMonIds = sortedCellMons
            .map { it.monMapId }
            .filter { it != primaryId }
            .distinct()

        val selectedMonIds = mutableListOf<String>()
        selectedMonIds.add(primaryId)
        for (id in otherMonIds) {
            if (selectedMonIds.size >= maxTarget) break
            selectedMonIds.add(id)
        }

        val packet = if (index == 5 && scrollId.isNotBlank()) {
            val targets = selectedMonIds.joinToString(",") { monId -> "i1>m:$monId" }
            "%xt%zm%gar%1%0%${targets}%${scrollId}%wvz%"
        } else {
            val prefix = if (index == 0) "aa" else "a$index"
            val targets = selectedMonIds.joinToString(",") { monId -> "$prefix>m:$monId" }
            "%xt%zm%gar%1%0%${targets}%wvz%"
        }

        val sent = client.send(packet)
        if (sent) {
            delay(200.milliseconds)
            updateNextUse(index)
            val newReloadTime = System.currentTimeMillis() + reloadDelayMs
            skillReloadTime =
                if (index != 0) newReloadTime else maxOf(skillReloadTime, newReloadTime)
            val mon = monstersProvider().firstOrNull { it.monMapId == primaryId }
            val resolvedName = mon?.name?.trim()?.ifEmpty { null }
            lastTargetMonster = resolvedName ?: targetMonMapId ?: "Monster #$primaryId"
            return true
        }
        return false
    }

    suspend fun useSkillToPlayer(skillIndex: Int, maxTarget: Int = 1): Boolean {
        val waitReloadMs = skillReloadTime - System.currentTimeMillis()
        if (waitReloadMs > 0 && skillIndex != 0) {
            delay(waitReloadMs.milliseconds)
        }

        val usernameId = playerState.roomUserId
        val userIds = playerState.roomUserIds.ifEmpty {
            playerState.playersInMap.values.mapNotNull {
                if (it.roomUserId > 0) it.roomUserId else if (it.userId > 0) it.userId else null
            }
        }

        val targets = mutableListOf<String>()
        for (i in userIds) {
            if (targets.size >= maxTarget - 1) break
            targets.add("a$skillIndex>p:$i")
        }
        targets.add(0, "a$skillIndex>p:$usernameId")

        val finalTarget = targets.distinct().joinToString(",")

        val packet = "%xt%zm%gar%1%0%${finalTarget}%wvz%"
        val sent = client.send(packet)
        if (sent) {
            delay(200.milliseconds)
            updateNextUse(skillIndex)
            skillReloadTime = System.currentTimeMillis() + 500L
        }
        return sent
    }

    suspend fun waitUseSkill(
        index: Int,
        targetMonMapId: String? = null,
        timeoutMs: Long = 5000L
    ): Boolean {
        val start = System.currentTimeMillis()
        while (client.isConnected.value && (System.currentTimeMillis() - start) < timeoutMs) {
            if (canUseSkill(index) && checkIsSkillSafe(index)) {
                return useSkill(index, targetMonMapId)
            }
            delay(100.milliseconds)
        }
        return false
    }

    suspend fun attack(targetMonMapId: String): Boolean {
        return useSkill(0, targetMonMapId)
    }

    suspend fun taunt(monMapId: String): Boolean {
        val waitReloadMs = skillReloadTime - System.currentTimeMillis()
        if (waitReloadMs > 0) {
            delay(waitReloadMs.milliseconds)
        }
        val packet = if (scrollId.isNotBlank()) {
            "%xt%zm%gar%1%0%i1>m:${monMapId}%${scrollId}%wvz%"
        } else {
            "%xt%zm%gar%1%0%a5>m:${monMapId}%wvz%"
        }
        val sent = client.send(packet)
        if (sent) {
            delay(200.milliseconds)
            updateNextUse(5)
            skillReloadTime = System.currentTimeMillis() + 500L
            val mon = monstersProvider().firstOrNull { it.monMapId == monMapId }
            val resolvedName = mon?.name?.trim()?.ifEmpty { null }
            lastTargetMonster = resolvedName ?: monMapId
        }
        return sent
    }

    suspend fun doPwd(monMapId: String): Boolean {
        val packet = "%xt%zm%gar%1%3%p6>m:${monMapId}%wvz%"
        return client.send(packet)
    }

    fun isMonsterAlive(monsterNameOrId: String = "*"): Boolean {
        val allMons = monstersProvider()
        val currentCellMons =
            allMons.filter { it.frame.equals(playerState.cell, ignoreCase = true) }
        return if (monsterNameOrId == "*") {
            currentCellMons.any { it.isAlive && it.currentHp > 0 }
        } else {
            val clean = if (monsterNameOrId.startsWith(
                    "id.",
                    ignoreCase = true
                )
            ) monsterNameOrId.substringAfter("id.") else monsterNameOrId
            currentCellMons.any {
                (it.name.equals(
                    clean,
                    ignoreCase = true
                ) || it.monMapId == clean) && it.isAlive && it.currentHp > 0
            }
        }
    }

    fun getMonster(monsterNameOrId: String): AqwMonster? {
        val allMons = monstersProvider()
        val clean = if (monsterNameOrId.startsWith(
                "id.",
                ignoreCase = true
            )
        ) monsterNameOrId.substringAfter("id.") else monsterNameOrId
        return allMons.firstOrNull {
            it.frame.equals(playerState.cell, ignoreCase = true) &&
                    (it.name.equals(clean, ignoreCase = true) || it.monMapId == clean)
        }
    }

    fun getMonsterHp(monsterNameOrId: String): Int {
        return getMonster(monsterNameOrId)?.currentHp ?: 0
    }

    fun getMonsterHpPercentage(monsterNameOrId: String): Int {
        return getMonster(monsterNameOrId)?.hpPercent ?: 0
    }

    suspend fun rest(): Boolean {
        val packet = "%xt%zm%rest%${playerState.areaId}%"
        return client.send(packet)
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

    fun isMonsterAlive(monsterNameOrId: String = "*", onlyCurrentCell: Boolean = true): Boolean {
        val clean = if (monsterNameOrId.startsWith("id.", ignoreCase = true)) {
            monsterNameOrId.substringAfter("id.")
        } else monsterNameOrId

        val allMons = monstersProvider()
        val mons = if (onlyCurrentCell) {
            allMons.filter { it.frame.equals(playerState.cell, ignoreCase = true) }
        } else allMons

        return mons.any { matchesMonster(it, clean) && it.isAlive && it.currentHp > 0 }
    }

    /**
     * Attacks the specified target monster using a skill rotation loop until the monster is killed
     * or no longer present in the cell.
     *
     * @param monsterNameOrId Name of the monster (or "id.<monMapId>" or "*" for any alive monster in cell).
     * @param skills List of skill indexes to execute in sequence (default: 0, 1, 2, 0, 3, 4).
     * @param delayMs Delay between rotation attempts in milliseconds (default: 250ms).
     * @param timeoutMs Maximum duration to attempt killing the monster in milliseconds (default: 60000ms / 1 min). Pass 0 for no timeout.
     * @param hunt If true, automatically jumps to the cell where the target monster is located.
     * @param isStopRequested Optional predicate to abort the kill loop early.
     * @return True if the monster was killed or dead, false if timed out or interrupted.
     */
    suspend fun killMonster(
        monsterNameOrId: String = "*",
        skills: List<Int> = listOf(0, 1, 2, 0, 3, 4),
        delayMs: Long = 500L,
        timeoutMs: Long = 60000L,
        hunt: Boolean = false,
        isStopRequested: () -> Boolean = { false }
    ): Boolean {
        val startTime = System.currentTimeMillis()
        var skillIdx = 0

        val cleanName = if (monsterNameOrId.startsWith("id.", ignoreCase = true)) {
            monsterNameOrId.substringAfter("id.")
        } else monsterNameOrId

        while (client.isConnected.value && !isStopRequested()) {
            while (isPaused() && !isStopRequested() && client.isConnected.value) {
                delay(500.milliseconds)
            }
            if (isStopRequested() || !client.isConnected.value) break

            if (!ensureAlive()) {
                return false
            }

            if (timeoutMs > 0 && (System.currentTimeMillis() - startTime) >= timeoutMs) {
                return false
            }

            // Auto-jump to the cell where the target monster is located if hunt is enabled
            if (hunt && cleanName != "*") {
                val hasAliveTargetInCell = monstersProvider().any {
                    it.frame.equals(playerState.cell, ignoreCase = true) &&
                            matchesMonster(it, cleanName) &&
                            it.isAlive && it.currentHp > 0
                }
                if (!hasAliveTargetInCell) {
                    val jumped = jumpToMonster(cleanName) || jumpToMonsterCell(cleanName)
                    if (jumped) {
                        delay(600.milliseconds)
                    }
                }
            }

            val currentCellMons = monstersProvider().filter {
                it.frame.equals(
                    playerState.cell,
                    ignoreCase = true
                ) && it.isAlive && it.currentHp > 0
            }

            val target = currentCellMons.firstOrNull { matchesMonster(it, cleanName) }

            // Target killed or disappeared from current cell
            if (target == null) {
                if (!hunt) {
                    return true
                }
                // When hunting, check if the monster is still alive anywhere in the map
                val aliveAnywhere = monstersProvider().any {
                    matchesMonster(it, cleanName) && it.isAlive && it.currentHp > 0
                }
                if (!aliveAnywhere) {
                    return true
                }
            } else {
                if (skills.isNotEmpty()) {
                    val skill = skills[skillIdx]
                    skillIdx = (skillIdx + 1) % skills.size

                    if (skill == 0) {
                        attack(target.monMapId)
                    } else {
                        useSkill(skill, target.monMapId)
                    }
                }
            }

            delay(delayMs.milliseconds)
        }

        return !isMonsterAlive(monsterNameOrId, onlyCurrentCell = !hunt)
    }

    suspend fun killMonster(
        target: AqwMonster,
        skills: List<Int> = listOf(0, 1, 2, 0, 3, 4),
        delayMs: Long = 500L,
        timeoutMs: Long = 60000L,
        hunt: Boolean = false,
        isStopRequested: () -> Boolean = { false }
    ): Boolean = killMonster(
        monsterNameOrId = target.monMapId,
        skills = skills,
        delayMs = delayMs,
        timeoutMs = timeoutMs,
        hunt = hunt,
        isStopRequested = isStopRequested
    )

    suspend fun jumpToMonsterCell(cleanName: String): Boolean {
        if (cleanName == "*") return false
        val allMons = monstersProvider()
        val target = allMons.firstOrNull {
            matchesMonster(it, cleanName) && it.isAlive && it.currentHp > 0
        } ?: allMons.firstOrNull {
            matchesMonster(it, cleanName)
        }

        if (target != null && !target.frame.equals(playerState.cell, ignoreCase = true)) {
            jumpCell(target.frame, "Left")
            delay(500.milliseconds)
            return true
        }
        return false
    }
}
