package froztt13.python.aqw.core.general

import froztt13.python.aqw.core.engine.AqwSession
import froztt13.python.aqw.data.GeneralBotConfig
import froztt13.python.aqw.data.GeneralSubModuleInfo
import froztt13.python.aqw.data.GeneralTaskInfo
import froztt13.python.aqw.helper.BotHelper
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Native implementation of Legion Revenant farming bot.
 * Faithful port of bot/LR/core_lr.py and templates/hunt.py.
 * Handles Fealty 1, Fealty 2, Fealty 3, and prerequisite Legion materials.
 */
object NativeLegionRevenantBot {

    val subModuleInfo = GeneralSubModuleInfo(
        id = "lr",
        name = "Legion Revenant Farm",
        category = "Endgame Class",
        description = "Automated Legion Revenant farming: Fealty 1, Fealty 2, Fealty 3, and Legion Tokens.",
        tasks = listOf(
            GeneralTaskInfo(
                "spellscroll",
                "Revenant's Spellscroll (Fealty 1)",
                "Farms Aeacus Empowered (50), Tethered Soul (300), Darkened Essence (500), Dracolich Contract (1000).",
                20,
                "Revenant's Spellscroll",
                6897
            ),
            GeneralTaskInfo(
                "conquest_wreath",
                "Conquest Wreath (Fealty 2)",
                "Farms 400 of each Cohort conquered across 10 maps.",
                6,
                "Conquest Wreath",
                6898
            ),
            GeneralTaskInfo(
                "exalted_crown",
                "Exalted Crown (Fealty 3)",
                "Farms Hooded Legion Cowl, Legion Tokens (4000), Dage's Favor (300), Emblem of Dage (1), Diamond Token (30), Dark Token (100).",
                10,
                "Exalted Crown",
                6899
            ),
            GeneralTaskInfo(
                "legion_token",
                "Legion Token (Shogun Paragon Pet)",
                "Farms Fotia souls for quick Legion Tokens via Quest 5755.",
                4000,
                "Legion Token",
                5755
            ),
            GeneralTaskInfo(
                "dages_favor",
                "Dage's Favor",
                "Hunts in /underworld [r8] for Dage's Favor.",
                300,
                "Dage's Favor",
                0
            ),
            GeneralTaskInfo(
                "emblem_of_dage",
                "Emblem of Dage",
                "Farms Legion Seal (25) and Gem of Mastery (1) in /shadowblast via Quest 4742.",
                20,
                "Emblem of Dage",
                4742
            ),
            GeneralTaskInfo(
                "diamond_token",
                "Diamond Token of Dage",
                "Farms Makai (25), Carnax, Red Dragon, Kathool, Fluffy, Blood Titan via Quest 4743.",
                30,
                "Diamond Token of Dage",
                4743
            ),
            GeneralTaskInfo(
                "dark_token",
                "Dark Token",
                "Farms Seraphic Medals in /seraphicwardage via Quest 6248 & 6249.",
                100,
                "Dark Token",
                6248
            )
        )
    )

    val LR_WHITELIST = setOf(
        "Revenant's Spellscroll",
        "Aeacus Empowered",
        "Tethered Soul",
        "Darkened Essence",
        "Dracolich Contract",
        "Conquest Wreath",
        "Ancient Cohort Conquered",
        "Grim Cohort Conquered",
        "Pirate Cohort Conquered",
        "Battleon Cohort Conquered",
        "Mirror Cohort Conquered",
        "Darkblood Cohort Conquered",
        "Vampire Cohort Conquered",
        "Spirit Cohort Conquered",
        "Dragon Cohort Conquered",
        "Doomwood Cohort Conquered",
        "Exalted Crown",
        "Hooded Legion Cowl",
        "Legion Token",
        "Dage's Favor",
        "Emblem of Dage",
        "Legion Seal",
        "Gem of Mastery",
        "Diamond Token of Dage",
        "Defeated Makai",
        "Carnax Eye",
        "Red Dragon's Fang",
        "Kathool Tentacle",
        "Fluffy's Bones",
        "Blood Titan's Blade",
        "Dark Token",
        "Legion Round 4 Medal",
        "Seraphic Medals",
        "Mega Seraphic Medals"
    )

    fun isDropWhitelisted(itemName: String, trackedItem: String = ""): Boolean {
        if (trackedItem.isNotBlank() && itemName.equals(trackedItem, ignoreCase = true)) return true
        return LR_WHITELIST.any { it.equals(itemName, ignoreCase = true) }
    }

    data class LrMat(
        val itemName: String,
        val qty: Int,
        val mapName: String,
        val cell: String = "Enter",
        val pad: String = "Spawn",
        val isSolo: Boolean = false,
        val monsterName: String = "*"
    )

    // Mats for Revenant's Spellscroll (Fealty 1)
    private val spellscrollMats = listOf(
        LrMat(
            "Aeacus Empowered",
            50,
            "judgement",
            "r10a",
            "Left",
            isSolo = true,
            monsterName = "Ultra Aeacus"
        ),
        LrMat("Tethered Soul", 300, "revenant", "r2", "Left", isSolo = false),
        LrMat("Darkened Essence", 500, "shadowrealmpast", "Enter", "Spawn", isSolo = false),
        LrMat("Dracolich Contract", 1000, "necrodungeon", "r22", "Down", isSolo = false)
    )

    private val spellscrollBankList = listOf(
        "Aeacus Empowered",
        "Tethered Soul",
        "Darkened Essence",
        "Dracolich Contract",
        "Revenant's Spellscroll"
    )

    // Mats for Conquest Wreath (Fealty 2)
    private val conquestWreathMats = listOf(
        LrMat("Ancient Cohort Conquered", 400, "mummies", "Enter", "Spawn", isSolo = false),
        LrMat("Grim Cohort Conquered", 400, "doomvault", "r1", "Right", isSolo = false),
        LrMat("Pirate Cohort Conquered", 400, "wrath", "r5", "Left", isSolo = false),
        LrMat("Battleon Cohort Conquered", 400, "doomwar", "r6", "Left", isSolo = false),
        LrMat("Mirror Cohort Conquered", 400, "overworld", "Enter", "Spawn", isSolo = false),
        LrMat("Darkblood Cohort Conquered", 400, "deathpits", "r1", "Left", isSolo = false),
        LrMat("Vampire Cohort Conquered", 400, "maxius", "r2", "Left", isSolo = false),
        LrMat("Spirit Cohort Conquered", 400, "curseshore", "Enter", "Spawn", isSolo = false),
        LrMat("Dragon Cohort Conquered", 400, "dragonbone", "Enter", "Spawn", isSolo = false),
        LrMat("Doomwood Cohort Conquered", 400, "doomwood", "r6", "Right", isSolo = false)
    )

    private val conquestWreathBankList = listOf(
        "Grim Cohort Conquered",
        "Ancient Cohort Conquered",
        "Pirate Cohort Conquered",
        "Battleon Cohort Conquered",
        "Mirror Cohort Conquered",
        "Darkblood Cohort Conquered",
        "Vampire Cohort Conquered",
        "Spirit Cohort Conquered",
        "Dragon Cohort Conquered",
        "Doomwood Cohort Conquered",
        "Conquest Wreath"
    )

    // Mats for Diamond Token of Dage (Quest 4743)
    private val diamondTokenMats = listOf(
        LrMat("Defeated Makai", 25, "tercessuinotlim", "Enter", "Spawn", isSolo = false),
        LrMat("Carnax Eye", 1, "aqlesson", "Frame9", "Right", isSolo = true),
        LrMat("Red Dragon's Fang", 1, "lair", "End", "Left", isSolo = true),
        LrMat("Kathool Tentacle", 1, "deepchaos", "Frame4", "Left", isSolo = true),
        LrMat("Fluffy's Bones", 1, "dflesson", "r12", "Right", isSolo = true),
        LrMat("Blood Titan's Blade", 1, "bloodtitan", "Enter", "Spawn", isSolo = true)
    )

    private val exaltedCrownBankList = listOf(
        "Dage's Favor",
        "Diamond Token of Dage",
        "Emblem of Dage",
        "Dark Token",
        "Exalted Crown",
        "Legion Seal",
        "Gem of Mastery",
        "Defeated Makai",
        "Legion Token",
        "Legion Round 4 Medal",
        "Hooded Legion Cowl"
    )

    fun resolveRoute(taskId: String): Triple<String, String, String> {
        return when (taskId) {
            "spellscroll" -> Triple("judgement", "r10a", "Ultra Aeacus")
            "conquest_wreath" -> Triple("mummies", "Enter", "Mummy")
            "exalted_crown" -> Triple("underworld", "r8", "Legion")
            "legion_token" -> Triple("fotia", "Enter", "Femme Fataler")
            "dages_favor" -> Triple("underworld", "r8", "Legion")
            "emblem_of_dage" -> Triple("shadowblast", "r10", "Legion Fenrir")
            "diamond_token" -> Triple("tercessuinotlim", "Enter", "Dark Makai")
            "dark_token" -> Triple("seraphicwardage", "Enter", "Seraphic")
            else -> Triple("judgement", "r10a", "Ultra Aeacus")
        }
    }

    suspend fun execute(
        session: AqwSession,
        config: GeneralBotConfig,
        task: GeneralTaskInfo,
        isStopRequested: () -> Boolean,
        onProgressUpdate: (currentQty: Int) -> Unit
    ) {
        val targetQty = if (config.targetQty > 0) config.targetQty else task.defaultQty

        when (task.id) {
            "spellscroll" -> revenantSpellscroll(
                session,
                config,
                targetQty,
                isStopRequested,
                onProgressUpdate
            )

            "conquest_wreath" -> conquestWreath(
                session,
                config,
                targetQty,
                isStopRequested,
                onProgressUpdate
            )

            "exalted_crown" -> exaltedCrown(
                session,
                config,
                targetQty,
                isStopRequested,
                onProgressUpdate
            )

            "legion_token" -> getLetoSsp(
                session,
                config,
                targetQty,
                isStopRequested,
                onProgressUpdate
            )

            "dages_favor" -> getDagesFavor(
                session,
                config,
                targetQty,
                isStopRequested,
                onProgressUpdate
            )

            "emblem_of_dage" -> getEmblemOfDage(
                session,
                config,
                targetQty,
                isStopRequested,
                onProgressUpdate
            )

            "diamond_token" -> getDiamondTokenOfDage(
                session,
                config,
                targetQty,
                isStopRequested,
                onProgressUpdate
            )

            "dark_token" -> getDarkToken(
                session,
                config,
                targetQty,
                isStopRequested,
                onProgressUpdate
            )

            else -> revenantSpellscroll(
                session,
                config,
                targetQty,
                isStopRequested,
                onProgressUpdate
            )
        }
    }

    /**
     * Farms Fealty 1: Revenant's Spellscroll (Quest 6897).
     */
    suspend fun revenantSpellscroll(
        session: AqwSession,
        config: GeneralBotConfig,
        qty: Int = 20,
        isStopRequested: () -> Boolean,
        onProgressUpdate: (currentQty: Int) -> Unit
    ) {
        val itemName = "Revenant's Spellscroll"
        session.commands.bankToInv(spellscrollBankList)

        while (!isStopRequested() && session.isConnected.value) {
            val currentQty = session.commands.getItemQty(itemName)
            onProgressUpdate(currentQty)
            if (currentQty >= qty) {
                BotHelper.dispatchLog(
                    "general",
                    config.username,
                    "[LR] Target reached: $currentQty / $qty $itemName"
                )
                break
            }

            session.commands.ensureAcceptQuest(6897)
            farmMats(session, config, spellscrollMats, isStopRequested, onProgressUpdate)
            if (isStopRequested() || !session.isConnected.value) break

            session.commands.ensureTurnInQuest(6897)
            delay(1000.milliseconds)
        }

        session.commands.invToBank(spellscrollBankList)
    }

    /**
     * Farms Fealty 2: Conquest Wreath (Quest 6898).
     */
    suspend fun conquestWreath(
        session: AqwSession,
        config: GeneralBotConfig,
        qty: Int = 6,
        isStopRequested: () -> Boolean,
        onProgressUpdate: (currentQty: Int) -> Unit
    ) {
        val itemName = "Conquest Wreath"
        session.commands.bankToInv(conquestWreathBankList)

        while (!isStopRequested() && session.isConnected.value) {
            val currentQty = session.commands.getItemQty(itemName)
            onProgressUpdate(currentQty)
            if (currentQty >= qty) {
                BotHelper.dispatchLog(
                    "general",
                    config.username,
                    "[LR] Target reached: $currentQty / $qty $itemName"
                )
                break
            }

            session.commands.ensureAcceptQuest(6898)
            farmMats(session, config, conquestWreathMats, isStopRequested, onProgressUpdate)
            if (isStopRequested() || !session.isConnected.value) break

            session.commands.ensureTurnInQuest(6898)
            delay(1000.milliseconds)
        }

        session.commands.invToBank(conquestWreathBankList)
    }

    /**
     * Farms Fealty 3: Exalted Crown (Quest 6899).
     */
    suspend fun exaltedCrown(
        session: AqwSession,
        config: GeneralBotConfig,
        qty: Int = 10,
        isStopRequested: () -> Boolean,
        onProgressUpdate: (currentQty: Int) -> Unit
    ) {
        val itemName = "Exalted Crown"
        session.commands.bankToInv(exaltedCrownBankList)

        while (!isStopRequested() && session.isConnected.value) {
            val currentQty = session.commands.getItemQty(itemName)
            onProgressUpdate(currentQty)
            if (currentQty >= qty) {
                BotHelper.dispatchLog(
                    "general",
                    config.username,
                    "[LR] Target reached: $currentQty / $qty $itemName"
                )
                break
            }

            session.commands.ensureAcceptQuest(6899)

            getHoodedLegionCowl(session, config, isStopRequested)
            if (isStopRequested() || !session.isConnected.value) break

            getLetoSsp(session, config, 4000, isStopRequested, onProgressUpdate)
            if (isStopRequested() || !session.isConnected.value) break

            getDagesFavor(session, config, 300, isStopRequested, onProgressUpdate)
            if (isStopRequested() || !session.isConnected.value) break

            getEmblemOfDage(session, config, 1, isStopRequested, onProgressUpdate)
            if (isStopRequested() || !session.isConnected.value) break

            getDiamondTokenOfDage(session, config, 30, isStopRequested, onProgressUpdate)
            if (isStopRequested() || !session.isConnected.value) break

            getDarkToken(session, config, 100, isStopRequested, onProgressUpdate)
            if (isStopRequested() || !session.isConnected.value) break

            session.commands.ensureTurnInQuest(6899)
            delay(1000.milliseconds)
        }

        session.commands.invToBank(exaltedCrownBankList)
    }

    /**
     * Buys Hooded Legion Cowl from shop 216 in /underworld if not present in inventory.
     */
    suspend fun getHoodedLegionCowl(
        session: AqwSession,
        config: GeneralBotConfig,
        isStopRequested: () -> Boolean
    ) {
        val item = "Hooded Legion Cowl"
        session.commands.bankToInv(item)
        if (session.commands.hasItem(item)) return

        BotHelper.dispatchLog("general", config.username, "[LR] Buying $item from shop 216...")
        session.commands.joinMap("underworld", config.roomNumber, "Enter", "Spawn")
        delay(1500.milliseconds)
        session.commands.ensureLoadShop(216)
        session.commands.buyItem(216, item, 1)
        delay(1000.milliseconds)
    }

    /**
     * Hunts Dage's Favor in /underworld [r8].
     */
    suspend fun getDagesFavor(
        session: AqwSession,
        config: GeneralBotConfig,
        qty: Int = 300,
        isStopRequested: () -> Boolean,
        onProgressUpdate: ((currentQty: Int) -> Unit)? = null
    ) {
        huntItem(
            session = session,
            itemName = "Dage's Favor",
            targetQty = qty,
            mapName = "underworld",
            cell = "r8",
            pad = "Left",
            roomNumber = config.roomNumber,
            isStopRequested = isStopRequested,
            onProgressUpdate = onProgressUpdate
        )
    }

    /**
     * Farms Emblem of Dage via Quest 4742 in /shadowblast.
     */
    suspend fun getEmblemOfDage(
        session: AqwSession,
        config: GeneralBotConfig,
        qty: Int = 1,
        isStopRequested: () -> Boolean,
        onProgressUpdate: ((currentQty: Int) -> Unit)? = null
    ) {
        val item = "Emblem of Dage"
        session.commands.bankToInv(item)
        if (session.commands.hasItem(item, qty)) return

        session.commands.bankToInv("Legion Round 4 Medal")
        if (!session.commands.hasItem("Legion Round 4 Medal")) {
            BotHelper.dispatchLog(
                "general",
                config.username,
                "[LR] Required: 'Legion Round 4 Medal' in inventory"
            )
            return
        }

        while (!isStopRequested() && session.isConnected.value) {
            val current = session.commands.getItemQty(item)
            onProgressUpdate?.invoke(current)
            if (current >= qty) break

            session.commands.ensureAcceptQuest(4742)
            huntItem(
                session = session,
                itemName = "Legion Seal",
                targetQty = 25,
                mapName = "shadowblast",
                cell = "r10",
                pad = "Left",
                roomNumber = config.roomNumber,
                isStopRequested = isStopRequested
            )
            if (isStopRequested() || !session.isConnected.value) break

            huntItem(
                session = session,
                itemName = "Gem of Mastery",
                targetQty = 1,
                mapName = "shadowblast",
                cell = "r10",
                pad = "Left",
                roomNumber = config.roomNumber,
                isStopRequested = isStopRequested
            )
            if (isStopRequested() || !session.isConnected.value) break

            session.commands.ensureTurnInQuest(4742)
            session.commands.invToBank(listOf("Legion Seal", "Gem of Mastery"))
            delay(1000.milliseconds)
        }
    }

    /**
     * Farms Diamond Token of Dage via Quest 4743 across 6 maps.
     */
    suspend fun getDiamondTokenOfDage(
        session: AqwSession,
        config: GeneralBotConfig,
        qty: Int = 30,
        isStopRequested: () -> Boolean,
        onProgressUpdate: ((currentQty: Int) -> Unit)? = null
    ) {
        val item = "Diamond Token of Dage"
        session.commands.bankToInv(item)

        while (!isStopRequested() && session.isConnected.value) {
            val current = session.commands.getItemQty(item)
            onProgressUpdate?.invoke(current)
            if (current >= qty) break

            session.commands.ensureAcceptQuest(4743)
            farmMats(session, config, diamondTokenMats, isStopRequested, onProgressUpdate)
            if (isStopRequested() || !session.isConnected.value) break

            session.commands.ensureTurnInQuest(4743)
            delay(1000.milliseconds)
        }
    }

    /**
     * Farms Dark Token in /seraphicwardage via Quest 6248 & 6249.
     */
    suspend fun getDarkToken(
        session: AqwSession,
        config: GeneralBotConfig,
        qty: Int = 100,
        isStopRequested: () -> Boolean,
        onProgressUpdate: ((currentQty: Int) -> Unit)? = null
    ) {
        val item = "Dark Token"
        session.commands.bankToInv(item)
        if (session.commands.getItemQty(item) >= qty) return

        BotHelper.dispatchLog(
            "general",
            config.username,
            "[LR] Farming Dark Token ($qty) in seraphicwardage..."
        )
        if (!session.commands.isInMap("seraphicwardage")) {
            session.commands.joinMap("seraphicwardage", config.roomNumber, "Enter", "Spawn")
            delay(1500.milliseconds)
        }

        val skillRotation = listOf(0, 1, 2, 0, 3, 4)

        while (!isStopRequested() && session.isConnected.value) {
            val current = session.commands.getItemQty(item)
            onProgressUpdate?.invoke(current)
            if (current >= qty) break

            for (q in listOf(6248, 6249)) {
                if (session.commands.questNotInProgress(q)) {
                    session.commands.acceptQuest(q)
                }
                if (session.commands.canTurnInQuest(q)) {
                    session.commands.turnInQuest(q)
                }
            }

            if (session.playerState.isDead) {
                delay(500.milliseconds)
                continue
            }

            session.commands.killMonster(
                monsterNameOrId = "*",
                skills = skillRotation,
                delayMs = 220L,
                timeoutMs = 15000L,
                hunt = false,
                isStopRequested = isStopRequested
            )
            delay(100.milliseconds)
        }
        session.commands.leaveCombat(true)
    }

    /**
     * Farms Legion Token in /fotia via Quest 5755 (Shogun Paragon Pet).
     */
    suspend fun getLetoSsp(
        session: AqwSession,
        config: GeneralBotConfig,
        qty: Int = 4000,
        isStopRequested: () -> Boolean,
        onProgressUpdate: ((currentQty: Int) -> Unit)? = null
    ) {
        val item = "Legion Token"
        session.commands.bankToInv("Shogun Paragon Pet")
        session.commands.bankToInv(item)
        if (session.commands.getItemQty(item) >= qty) return

        BotHelper.dispatchLog(
            "general",
            config.username,
            "[LR] Farming Legion Token ($qty) in fotia..."
        )
        if (!session.commands.isInMap("fotia")) {
            session.commands.joinMap("fotia", config.roomNumber, "Enter", "Spawn")
            delay(1500.milliseconds)
        }

        val skillRotation = listOf(0, 1, 2, 0, 3, 4)

        while (!isStopRequested() && session.isConnected.value) {
            val current = session.commands.getItemQty(item)
            onProgressUpdate?.invoke(current)
            if (current >= qty) break

            if (session.commands.questNotInProgress(5755)) {
                session.commands.acceptQuest(5755)
            }
            if (session.commands.canTurnInQuest(5755)) {
                session.commands.turnInQuest(5755)
            }

            if (session.playerState.isDead) {
                delay(500.milliseconds)
                continue
            }

            session.commands.killMonster(
                monsterNameOrId = "*",
                skills = skillRotation,
                delayMs = 220L,
                timeoutMs = 15000L,
                hunt = false,
                isStopRequested = isStopRequested
            )
            delay(100.milliseconds)
        }
        session.commands.leaveCombat(true)
    }

    /**
     * Loops through a list of materials, switching between solo and farm class as needed,
     * and hunting each material to its required quantity.
     */
    suspend fun farmMats(
        session: AqwSession,
        config: GeneralBotConfig,
        itemsToFarm: List<LrMat>,
        isStopRequested: () -> Boolean,
        onProgressUpdate: ((currentQty: Int) -> Unit)? = null
    ) {
        for (mat in itemsToFarm) {
            if (isStopRequested() || !session.isConnected.value) return

            if (mat.isSolo && config.soloClass.isNotBlank()) {
                session.commands.equipItem(config.soloClass)
                delay(1000.milliseconds)
            } else if (!mat.isSolo && config.farmClass.isNotBlank()) {
                session.commands.equipItem(config.farmClass)
                delay(1000.milliseconds)
            }

            val room =
                if (mat.mapName.equals("revenant", ignoreCase = true)) 999999 else config.roomNumber

            huntItem(
                session = session,
                itemName = mat.itemName,
                targetQty = mat.qty,
                mapName = mat.mapName,
                cell = mat.cell,
                pad = mat.pad,
                roomNumber = room,
                monsterName = mat.monsterName,
                isStopRequested = isStopRequested,
                onProgressUpdate = onProgressUpdate
            )
        }
    }

    /**
     * Port of hunt_item from templates/hunt.py.
     * Moves to the specified map and cell, ensuring bank-to-inv and killing monsters
     * until the required item quantity is obtained.
     */
    suspend fun huntItem(
        session: AqwSession,
        itemName: String,
        targetQty: Int,
        mapName: String,
        cell: String? = null,
        pad: String = "Left",
        roomNumber: Int? = null,
        monsterName: String = "*",
        isTemp: Boolean = false,
        isStopRequested: () -> Boolean,
        onProgressUpdate: ((currentQty: Int) -> Unit)? = null
    ) {
        if (isStopRequested() || !session.isConnected.value) return
        session.waitIfPaused(isStopRequested)
        if (isStopRequested() || !session.isConnected.value) return

        if (session.commands.hasItemInBank(itemName)) {
            session.commands.bankToInv(itemName)
        }

        if (session.commands.hasItem(itemName, targetQty, isTemp)) {
            return
        }

        BotHelper.dispatchLog(
            "general",
            session.playerState.username,
            "[LR] Hunting $itemName ($targetQty) in $mapName..."
        )

        if (!session.commands.isInMap(mapName)) {
            session.commands.joinMap(mapName, roomNumber, cell ?: "Enter", pad)
            delay(1500.milliseconds)
        }

        if (cell != null && !session.playerState.cell.equals(cell, ignoreCase = true)) {
            session.commands.jumpCell(cell, pad)
            delay(1000.milliseconds)
        }

        val skillRotation = listOf(0, 1, 2, 0, 3, 4)

        while (!isStopRequested() && session.isConnected.value) {
            session.waitIfPaused(isStopRequested)
            if (isStopRequested() || !session.isConnected.value) break

            val currentQty = session.commands.getItemQty(itemName, isTemp)
            onProgressUpdate?.invoke(currentQty)

            if (currentQty >= targetQty) {
                session.commands.leaveCombat(true)
                break
            }

            if (session.playerState.isDead) {
                delay(500.milliseconds)
                continue
            }

            if (cell != null && !session.playerState.cell.equals(cell, ignoreCase = true)) {
                session.commands.jumpCell(cell, pad)
                delay(600.milliseconds)
            }

            session.commands.killMonster(
                monsterNameOrId = monsterName,
                skills = skillRotation,
                delayMs = 700,
                timeoutMs = 15000L,
                hunt = cell == null,
                isStopRequested = isStopRequested
            )
            delay(100.milliseconds)
        }
    }
}
