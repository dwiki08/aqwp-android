package froztt13.python.aqw.domain.bot.general

import froztt13.python.aqw.data.engine.AqwSession
import froztt13.python.aqw.data.model.GeneralBotConfig
import froztt13.python.aqw.data.model.GeneralSubModuleInfo
import froztt13.python.aqw.data.model.GeneralTaskInfo

import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Native implementation of Nulgath Materials farming bot based on bot/nulgath/larvae.py.
 * Handles Nulgath Larva quest (2566), farming:
 * - Mana Energy for Nulgath (qty: 1, map: elemental, cell: r5, pad: Left, monster: Mana Golem)
 * - Charged Mana Energy for Nulgath (qty: 5, map: elemental, cell: r3, pad: Left, monster: Mana Falcon)
 *
 * Drops from Nulgath Larva:
 * Uni 13, Diamonds, Dark Crystal Shards, Tainted Gems, Totems, Gems, Blood Gems,
 * and Voucher of Nulgath (non-mem).
 * Automatically sells member "Voucher of Nulgath" for 250,000 gold after each complete.
 */
object NativeNulgathBot {

    data class FarmTask(
        val itemName: String,
        val qty: Int,
        val mapName: String = "elemental",
        val cell: String,
        val pad: String = "Left",
        val targetMonster: String = ""
    )

    val larvaeFarmTasks = listOf(
        FarmTask(
            itemName = "Mana Energy for Nulgath",
            qty = 1,
            mapName = "elemental",
            cell = "r5",
            pad = "Left",
            targetMonster = "Mana Golem"
        ),
        FarmTask(
            itemName = "Charged Mana Energy for Nulgath",
            qty = 5,
            mapName = "elemental",
            cell = "r3",
            pad = "Left",
            targetMonster = "Mana Falcon"
        )
    )

    val subModuleInfo = GeneralSubModuleInfo(
        id = "nulgath",
        name = "Nulgath Materials Farm",
        category = "Nation",
        description = "Nulgath Larva farming for Uni 13, Diamonds, Dark Crystal Shards, Tainted Gems, and Vouchers.",
        tasks = listOf(
            GeneralTaskInfo(
                "larvae",
                "Nulgath Larva (Continuous)",
                "Farms Mana Energy in /elemental in an infinite loop.",
                0,
                "",
                2566
            ),
            GeneralTaskInfo(
                "larvae_uni13",
                "Unidentified 13 (Uni 13)",
                "Farms Nulgath Larva quest 2566 until target Unidentified 13 is reached.",
                3,
                "Unidentified 13",
                2566
            ),
            GeneralTaskInfo(
                "larvae_diamond",
                "Diamond of Nulgath",
                "Farms Nulgath Larva quest 2566 until target Diamond of Nulgath is reached.",
                100,
                "Diamond of Nulgath",
                2566
            ),
            GeneralTaskInfo(
                "larvae_dcs",
                "Dark Crystal Shard",
                "Farms Nulgath Larva quest 2566 until target Dark Crystal Shard is reached.",
                50,
                "Dark Crystal Shard",
                2566
            ),
            GeneralTaskInfo(
                "larvae_tainted",
                "Tainted Gem",
                "Farms Nulgath Larva quest 2566 until target Tainted Gem is reached.",
                100,
                "Tainted Gem",
                2566
            ),
            GeneralTaskInfo(
                "larvae_voucher_nonmem",
                "Voucher of Nulgath (non-mem)",
                "Farms Nulgath Larva quest 2566 until non-member Voucher drops.",
                1,
                "Voucher of Nulgath (non-mem)",
                2566
            ),
            GeneralTaskInfo(
                "larvae_totem",
                "Totem of Nulgath",
                "Farms Nulgath Larva quest 2566 until target Totem is reached.",
                10,
                "Totem of Nulgath",
                2566
            ),
            GeneralTaskInfo(
                "larvae_gem",
                "Gem of Nulgath",
                "Farms Nulgath Larva quest 2566 until target Gem is reached.",
                50,
                "Gem of Nulgath",
                2566
            ),
            GeneralTaskInfo(
                "larvae_blood_gem",
                "Blood Gem of the Archfiend",
                "Farms Nulgath Larva quest 2566 until target Blood Gem is reached.",
                10,
                "Blood Gem of the Archfiend",
                2566
            )
        )
    )

    val dropWhitelist = setOf(
        "Mana Energy for Nulgath",
        "Gem of Nulgath",
        "Diamond of Nulgath",
        "Voucher of Nulgath",
        "Voucher of Nulgath (non-mem)",
        "Dark Crystal Shard",
        "Totem of Nulgath",
        "Blood Gem of the Archfiend",
        "Unidentified 13",
        "Tainted Gem",
        "Unidentified 10"
    )

    fun isDropWhitelisted(itemName: String, extraItem: String = ""): Boolean {
        if (extraItem.isNotBlank() && itemName.equals(extraItem.trim(), ignoreCase = true)) {
            return true
        }
        return dropWhitelist.any { it.equals(itemName.trim(), ignoreCase = true) }
    }

    fun resolveRoute(taskId: String): Triple<String, String, String> {
        return Triple("elemental", "r5", "Mana Golem")
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
        val questId = if (task.questId > 0) task.questId else 2566
        val trackedItem = task.trackedItem
        val targetQty = config.targetQty

        // If tracked item is stored in bank, transfer to inventory first (matching python hunt_item)
        if (trackedItem.isNotBlank() && session.item.hasItemInBank(trackedItem)) {
            session.log("[Nulgath] Moving $trackedItem from bank to inventory...")
            session.item.bankToInv(trackedItem)
            delay(1000.milliseconds)
        }

        // Bank-to-inv non-voucher rewards so they stack in inventory
        val bankableItems =
            dropWhitelist.filter { !it.equals("Voucher of Nulgath", ignoreCase = true) }
        session.item.bankToInv(bankableItems)
        delay(1000.milliseconds)

        session.log("[Nulgath] Registering quest $questId for auto accept & turn-in...")
        session.quest.registerQuest(questId)
        delay(1000.milliseconds)

        val skillRotation = listOf(0, 1, 2, 0, 3, 4)
        var completeCount = 0

        try {
            while (!isStopRequested() && session.isConnected.value) {
                session.waitIfPaused(isStopRequested)
                if (isStopRequested() || !session.isConnected.value) break

                // Check tracking / target condition
                val currentQty = if (trackedItem.isNotBlank()) {
                    session.item.getItemQty(trackedItem)
                } else {
                    completeCount
                }
                onProgressUpdate(currentQty)

                if (targetQty > 0 && trackedItem.isNotBlank() && currentQty >= targetQty) {
                    session.log("[Nulgath] Target reached: $currentQty / $targetQty $trackedItem!")
                    session.social.sendChat("[Nulgath] Target reached: $currentQty $trackedItem")
                    break
                } else if (task.id == "larvae" && targetQty > 0 && completeCount >= targetQty) {
                    session.log("[Nulgath] Target completes reached: $completeCount / $targetQty!")
                    break
                }

                // Execute do_farm_tasks (item_to_farm)
                for (farmTask in larvaeFarmTasks) {
                    if (isStopRequested() || !session.isConnected.value) break

                    fun getTaskItemQty(): Int = maxOf(
                        session.item.getItemQty(farmTask.itemName, isTemp = true),
                        session.item.getItemQty(farmTask.itemName, isTemp = false)
                    )

                    if (getTaskItemQty() >= farmTask.qty) {
                        continue
                    }

                    // Join map if not in map
                    if (session.map.isNotInMap(farmTask.mapName)) {
                        session.log("[Nulgath] Joining /${farmTask.mapName}-$roomNumber [${farmTask.cell}]...")
                        session.map.joinMap(
                            farmTask.mapName,
                            roomNumber,
                            farmTask.cell,
                            farmTask.pad
                        )
                        delay(2000.milliseconds)
                    }

                    // Jump to cell if not in cell
                    if (!session.playerState.cell.equals(farmTask.cell, ignoreCase = true)) {
                        session.map.jumpCell(farmTask.cell, farmTask.pad)
                        delay(1000.milliseconds)
                    }

                    session.log("[Nulgath] Farming ${farmTask.itemName} [${getTaskItemQty()}/${farmTask.qty}] in cell ${farmTask.cell}...")

                    // Attack loop for this task item using killMonster with hunt
                    while (!isStopRequested() && session.isConnected.value && getTaskItemQty() < farmTask.qty) {
                        session.waitIfPaused(isStopRequested)
                        if (isStopRequested() || !session.isConnected.value) break

                        val targetName = farmTask.targetMonster.ifBlank { "*" }
                        session.combat.killMonster(
                            monsterNameOrId = targetName,
                            skills = skillRotation,
                            isStopRequested = isStopRequested
                        )
                        delay(250.milliseconds)
                    }

                    // Leave combat after task item fulfilled
                    session.map.leaveCombat(safeLeave = false)
                }

                if (isStopRequested() || !session.isConnected.value) break

                // Auto-sell member Voucher of Nulgath (matching larvae.py)
                val memberVoucher = session.playerState.inventory.firstOrNull {
                    it.name.equals("Voucher of Nulgath", ignoreCase = true) &&
                            !it.name.contains("non-mem", ignoreCase = true)
                }
                if (memberVoucher != null) {
                    session.log("[Nulgath] Selling member Voucher of Nulgath...")
                    session.item.sellItem(memberVoucher.name)
                    delay(1000.milliseconds)
                }

                completeCount++
                session.log("[Nulgath] Cycle #$completeCount complete")
                delay(2000.milliseconds)
            }
        } finally {
            session.quest.unregisterQuest(questId)
        }
    }
}
