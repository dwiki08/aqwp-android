package froztt13.python.aqw.core.eclipse

import froztt13.python.aqw.data.EclipseConfig
import froztt13.python.aqw.data.SlotConfig

/**
 * Default configuration presets for Eclipse bot.
 * Uses [PartySlot] to encapsulate slot presets (id, equipClass, priorityTarget, isLightGatherTaunter).
 */
object NativeEclipseConfig {

    const val DEFAULT_TARGET_FALLBACK = "Ascended Solstice"

    // --- Party Slot Presets ---
    val DEFAULT_SLOTS: List<PartySlot> = listOf(
        PartySlot(
            id = "slot1",
            memberType = PartyMemberType.MASTER,
            equipClass = "Legion Revenant",
            priorityTarget = "Ascended Solstice,Blessless Deer",
            isLightGatherTaunter = false
        ),
        PartySlot(
            id = "slot2",
            memberType = PartyMemberType.SLAVE,
            equipClass = "StoneCrusher",
            priorityTarget = "Ascended Solstice",
            isLightGatherTaunter = true
        ),
        PartySlot(
            id = "slot3",
            memberType = PartyMemberType.SLAVE,
            equipClass = "ArchPaladin",
            priorityTarget = "Ascended Midnight",
            isLightGatherTaunter = true
        ),
        PartySlot(
            id = "slot4",
            memberType = PartyMemberType.SLAVE,
            equipClass = "Lord of Order",
            priorityTarget = "Ascended Midnight",
            isLightGatherTaunter = true
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
    const val DEFAULT_LIGHT_GATHER_MODE = "rotation"
    val DEFAULT_LIGHT_GATHER_SLOTS: List<String> =
        DEFAULT_SLOTS.filter { it.isLightGatherTaunter }.map { it.id }

    // --- Legacy Constants for Quick Access ---
    const val DEFAULT_CLASS_SLOT1 = "Legion Revenant"
    const val DEFAULT_CLASS_SLOT2 = "StoneCrusher"
    const val DEFAULT_CLASS_SLOT3 = "ArchPaladin"
    const val DEFAULT_CLASS_SLOT4 = "Lord of Order"

    const val DEFAULT_TARGET_SLOT1 = "Ascended Solstice,Blessless Deer"
    const val DEFAULT_TARGET_SLOT2 = "Ascended Solstice"
    const val DEFAULT_TARGET_SLOT3 = "Ascended Midnight"
    const val DEFAULT_TARGET_SLOT4 = "Ascended Midnight"

    /**
     * Finds the default [PartySlot] for a given slot key.
     */
    fun getSlot(slotKey: String): PartySlot? =
        DEFAULT_SLOTS.find { it.id.equals(slotKey, ignoreCase = true) }

    /**
     * Resolves the default target for a slot, allowing an optional user override.
     */
    fun getDefaultTarget(slotKey: String, configuredTarget: String? = null): String {
        if (!configuredTarget.isNullOrBlank()) {
            return configuredTarget
        }
        return getSlot(slotKey)?.priorityTarget ?: DEFAULT_TARGET_FALLBACK
    }

    /**
     * Resolves the default class for a slot.
     */
    fun getDefaultClass(slotKey: String): String {
        return getSlot(slotKey)?.equipClass ?: ""
    }

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
     * Creates default [SlotConfig] matching the screen's initial presets.
     */
    fun createDefaultSlotConfig(slotKey: String): SlotConfig {
        val slot = getSlot(slotKey) ?: PartySlot(id = slotKey)
        val isMaster = slot.isMaster
        val isSun = slotKey in listOf("slot1", "slot2")
        val isMoon = slotKey in listOf("slot3", "slot4")
        return SlotConfig(
            charClass = slot.equipClass,
            role = if (isMaster) "master" else "slave",
            isTaunter = true,
            sunsetKnightTaunter = isSun,
            moonHazeTaunter = isMoon,
            lightGatherTaunter = slot.isLightGatherTaunter,
            defaultTarget = getDefaultTarget(slotKey)
        )
    }

    /**
     * Creates default [EclipseConfig] matching the screen's global settings & slot presets.
     */
    fun createDefaultConfig(
        server: String = DEFAULT_SERVER,
        roomNumber: Int = DEFAULT_ROOM_NUMBER,
        lightGatherMode: String = DEFAULT_LIGHT_GATHER_MODE
    ): EclipseConfig {
        return EclipseConfig(
            server = server,
            roomNumber = roomNumber,
            lightGatherMode = lightGatherMode,
            slots = ALL_SLOTS.associateWith { createDefaultSlotConfig(it) }
        )
    }
}
