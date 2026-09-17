package froztt13.python.aqw.core.temple

import froztt13.python.aqw.data.SlotConfig
import froztt13.python.aqw.data.TempleConfig
import froztt13.python.aqw.data.model.PartyMemberType
import froztt13.python.aqw.data.model.PartySlot

/**
 * Default configuration presets for Temple bot (Midnight Sun & Solstice Moon).
 * Uses [PartySlot] to encapsulate slot presets (id, memberType, equipClass, priorityTarget).
 */
object NativeTempleConfig {

    const val DEFAULT_BOT_TYPE = "MidnightSunBot"
    const val BOT_TYPE_MIDNIGHT_SUN = "MidnightSunBot"
    const val BOT_TYPE_SOLSTICE_MOON = "SolsticeMoonBot"

    const val MAP_MIDNIGHT_SUN = "midnightsun"
    const val MAP_SOLSTICE_MOON = "solsticemoon"

    const val DEFAULT_TARGET_MIDNIGHT_SUN = "Ascended Midnight,Blessless Deer"
    const val DEFAULT_TARGET_SOLSTICE_MOON = "Lunar Haze"

    // --- Party Slot Presets ---
    val DEFAULT_SLOTS: List<PartySlot> = listOf(
        PartySlot(
            id = "slot1",
            memberType = PartyMemberType.MASTER,
            equipClass = "ArchPaladin",
            priorityTarget = DEFAULT_TARGET_MIDNIGHT_SUN,
            isLightGatherTaunter = false
        ),
        PartySlot(
            id = "slot2",
            memberType = PartyMemberType.SLAVE,
            equipClass = "StoneCrusher",
            priorityTarget = DEFAULT_TARGET_MIDNIGHT_SUN,
            isLightGatherTaunter = false
        ),
        PartySlot(
            id = "slot3",
            memberType = PartyMemberType.SLAVE,
            equipClass = "Legion Revenant",
            priorityTarget = DEFAULT_TARGET_MIDNIGHT_SUN,
            isLightGatherTaunter = false
        ),
        PartySlot(
            id = "slot4",
            memberType = PartyMemberType.SLAVE,
            equipClass = "Lord of Order",
            priorityTarget = DEFAULT_TARGET_MIDNIGHT_SUN,
            isLightGatherTaunter = false
        )
    )

    val ALL_SLOTS: List<String> = DEFAULT_SLOTS.map { it.id }

    val MASTER_SLOT: PartySlot = DEFAULT_SLOTS.first { it.isMaster }
    val MASTER_SLOT_KEY: String = MASTER_SLOT.id

    val SLAVE_SLOTS: List<PartySlot> = DEFAULT_SLOTS.filter { it.isSlave }
    val SLAVE_SLOT_KEYS: List<String> = SLAVE_SLOTS.map { it.id }

    // --- Global Settings Defaults ---
    const val DEFAULT_SERVER = "Alteon"
    const val DEFAULT_ROOM_NUMBER = 9099

    // --- Legacy Constants for Quick Access ---
    const val DEFAULT_CLASS_SLOT1 = "ArchPaladin"
    const val DEFAULT_CLASS_SLOT2 = "StoneCrusher"
    const val DEFAULT_CLASS_SLOT3 = "Legion Revenant"
    const val DEFAULT_CLASS_SLOT4 = "Lord of Order"

    /**
     * Checks if the given slot key belongs to a master role.
     */
    fun isMasterSlot(slotKey: String): Boolean =
        getSlot(slotKey)?.isMaster ?: false

    /**
     * Checks if the given slot key belongs to a slave role.
     */
    fun isSlaveSlot(slotKey: String): Boolean =
        getSlot(slotKey)?.isSlave ?: false

    /**
     * Finds the default [PartySlot] for a given slot key.
     */
    fun getSlot(slotKey: String): PartySlot? =
        DEFAULT_SLOTS.find { it.id.equals(slotKey, ignoreCase = true) }

    /**
     * Resolves the default target for a slot, allowing an optional user override.
     */
    fun getDefaultTarget(
        slotKey: String,
        configuredTarget: String? = null,
        botType: String = DEFAULT_BOT_TYPE
    ): String {
        if (!configuredTarget.isNullOrBlank()) {
            return configuredTarget
        }
        val defaultForMap = if (botType.equals(BOT_TYPE_SOLSTICE_MOON, ignoreCase = true)) {
            DEFAULT_TARGET_SOLSTICE_MOON
        } else {
            DEFAULT_TARGET_MIDNIGHT_SUN
        }
        return getSlot(slotKey)?.priorityTarget?.takeIf { it.isNotBlank() } ?: defaultForMap
    }

    /**
     * Resolves the default class for a slot.
     */
    fun getDefaultClass(slotKey: String): String =
        getSlot(slotKey)?.equipClass ?: ""

    /**
     * Resolves the dungeon map name according to bot type.
     */
    fun getDungeonMap(botType: String): String =
        if (botType.equals(
                BOT_TYPE_SOLSTICE_MOON,
                ignoreCase = true
            )
        ) MAP_SOLSTICE_MOON else MAP_MIDNIGHT_SUN

    /**
     * Resolves the default fallback target monster string according to dungeon map.
     */
    fun getDefaultTargetMonsters(dungeonMap: String): String =
        if (dungeonMap == MAP_SOLSTICE_MOON) DEFAULT_TARGET_SOLSTICE_MOON else "Dying Light,Dawn Knight"

    /**
     * Creates default [SlotConfig] matching the screen's initial presets.
     */
    fun createDefaultSlotConfig(slotKey: String, botType: String = DEFAULT_BOT_TYPE): SlotConfig {
        val slot = getSlot(slotKey) ?: PartySlot(id = slotKey)
        val isMaster = slot.isMaster
        return SlotConfig(
            charClass = slot.equipClass,
            role = if (isMaster) "master" else "slave",
            isTaunter = isMaster,
            defaultTarget = getDefaultTarget(slotKey, botType = botType)
        )
    }

    /**
     * Creates default [TempleConfig] matching the screen's global settings & slot presets.
     */
    fun createDefaultConfig(
        server: String = DEFAULT_SERVER,
        roomNumber: Int = DEFAULT_ROOM_NUMBER,
        templeBotType: String = DEFAULT_BOT_TYPE
    ): TempleConfig {
        return TempleConfig(
            server = server,
            roomNumber = roomNumber,
            templeBotType = templeBotType,
            slots = ALL_SLOTS.associateWith { createDefaultSlotConfig(it, templeBotType) }
        )
    }
}
