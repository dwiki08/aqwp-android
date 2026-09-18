package froztt13.python.aqw.domain.bot.general

import froztt13.python.aqw.data.engine.AqwSession
import froztt13.python.aqw.data.model.GeneralBotConfig
import froztt13.python.aqw.data.model.GeneralSubModuleInfo
import froztt13.python.aqw.data.model.GeneralTaskInfo
import froztt13.python.aqw.helper.BotHelper
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Native implementation of Void Aura farming bot.
 * Handles Quest 4432 (Retrieve Void Auras) and individual boss essence hunting.
 */
object NativeVoidAuraBot {

    val subModuleInfo = GeneralSubModuleInfo(
        id = "va",
        name = "Void Aura Farm",
        category = "Endgame Weapon",
        description = "Farms Void Auras and individual boss essences across Lore.",
        tasks = listOf(
            GeneralTaskInfo(
                "retrieve_va",
                "Retrieve Void Auras (Quest 4432)",
                "Farms 100 of all 10 essences and turns in Quest 4432.",
                100,
                "Void Aura",
                4432
            ),
            GeneralTaskInfo(
                "essence_astral",
                "Astral Ephemerite Essence",
                "Hunts Astral Ephemerite in /timespace.",
                100,
                "Astral Ephemerite Essence",
                0
            ),
            GeneralTaskInfo(
                "essence_belrot",
                "Belrot the Fiend Essence",
                "Hunts Belrot the Fiend in /citadel.",
                100,
                "Belrot the Fiend Essence",
                0
            ),
            GeneralTaskInfo(
                "essence_blackknight",
                "Black Knight Essence",
                "Hunts Black Knight in /greenguardwest.",
                100,
                "Black Knight Essence",
                0
            ),
            GeneralTaskInfo(
                "essence_tigerleech",
                "Tiger Leech Essence",
                "Hunts Tiger Leech in /mudluk.",
                100,
                "Tiger Leech Essence",
                0
            ),
            GeneralTaskInfo(
                "essence_carnax",
                "Carnax Essence",
                "Hunts Carnax in /aqlesson.",
                100,
                "Carnax Essence",
                0
            ),
            GeneralTaskInfo(
                "essence_chaosvordred",
                "Chaos Vordred Essence",
                "Hunts Chaos Vordred in /necrocavern.",
                100,
                "Chaos Vordred Essence",
                0
            ),
            GeneralTaskInfo(
                "essence_daitengu",
                "Dai Tengu Essence",
                "Hunts Dai Tengu in /hachiko.",
                100,
                "Dai Tengu Essence",
                0
            ),
            GeneralTaskInfo(
                "essence_unending",
                "Unending Avatar Essence",
                "Hunts Unending Avatar in /timevoid.",
                100,
                "Unending Avatar Essence",
                0
            ),
            GeneralTaskInfo(
                "essence_voiddragon",
                "Void Dragon Essence",
                "Hunts Void Dragon in /dragonchallenge.",
                100,
                "Void Dragon Essence",
                0
            ),
            GeneralTaskInfo(
                "essence_creature",
                "Creature Creation Essence",
                "Hunts Creature Creation in /maul.",
                100,
                "Creature Creation Essence",
                0
            )
        )
    )

    fun resolveRoute(taskId: String): Triple<String, String, String> {
        return when (taskId) {
            "retrieve_va", "essence_astral" -> Triple("timespace", "Enter", "Astral Ephemerite")
            "essence_belrot" -> Triple("citadel", "m13", "Belrot the Fiend")
            "essence_blackknight" -> Triple("greenguardwest", "r1", "Black Knight")
            "essence_tigerleech" -> Triple("mudluk", "Boss", "Tiger Leech")
            "essence_carnax" -> Triple("aqlesson", "r6", "Carnax")
            "essence_chaosvordred" -> Triple("necrocavern", "r13", "Chaos Vordred")
            "essence_daitengu" -> Triple("hachiko", "Boss", "Dai Tengu")
            "essence_unending" -> Triple("timevoid", "r6", "Unending Avatar")
            "essence_voiddragon" -> Triple("dragonchallenge", "r2", "Void Dragon")
            "essence_creature" -> Triple("maul", "r3", "Creature Creation")
            else -> Triple("timespace", "Enter", "Astral Ephemerite")
        }
    }

    suspend fun execute(
        session: AqwSession,
        config: GeneralBotConfig,
        task: GeneralTaskInfo,
        isStopRequested: () -> Boolean,
        onProgressUpdate: (currentQty: Int) -> Unit
    ) {
        val username = config.username.trim()
        val roomNumber = config.roomNumber
        val questId = task.questId
        val trackedItem = task.trackedItem
        val targetQty = config.targetQty

        val (targetMap, targetCell, targetMonsters) = resolveRoute(task.id)

        BotHelper.dispatchLog(
            "general",
            username,
            "[Void Aura] Moving to $targetMap-$roomNumber [$targetCell]..."
        )
        session.map.joinMap(targetMap, roomNumber, targetCell, "Spawn")
        delay(2000.milliseconds)

        if (questId > 0) {
            session.quest.acceptQuest(questId)
            delay(1000.milliseconds)
        }

        val skillRotation = listOf(0, 1, 2, 0, 3, 4)
        var killCount = 0

        while (!isStopRequested() && session.isConnected.value) {
            session.waitIfPaused(isStopRequested)
            if (isStopRequested() || !session.isConnected.value) break

            if (session.playerState.isDead) {
                delay(500.milliseconds)
                continue
            }

            val currentQty = if (trackedItem.isNotBlank()) {
                session.playerState.inventory.firstOrNull {
                    it.name.equals(
                        trackedItem,
                        ignoreCase = true
                    )
                }?.qty ?: 0
            } else 0

            onProgressUpdate(currentQty)

            if (targetQty > 0 && trackedItem.isNotBlank() && currentQty >= targetQty) {
                BotHelper.dispatchLog(
                    "general",
                    username,
                    "[Void Aura] Target reached: $currentQty / $targetQty $trackedItem!"
                )
                session.social.sendChat("[Void Aura] Target reached: $currentQty $trackedItem")
                break
            }

            if (questId > 0 && (session.quest.canTurnInQuest(questId) || killCount % 5 == 0)) {
                session.quest.turnInQuest(questId)
                delay(1200.milliseconds)
                session.quest.acceptQuest(questId)
            }

            session.combat.killMonster(
                monsterNameOrId = targetMonsters.ifBlank { "*" },
                skills = skillRotation,
//                hunt = true,
                isStopRequested = isStopRequested
            )
            killCount++

            delay(200.milliseconds)
        }
    }
}
