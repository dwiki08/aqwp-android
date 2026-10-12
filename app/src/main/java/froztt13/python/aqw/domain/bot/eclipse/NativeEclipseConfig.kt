package froztt13.python.aqw.domain.bot.eclipse

import froztt13.python.aqw.data.model.EclipseConfig
import froztt13.python.aqw.data.model.PartyMemberType
import froztt13.python.aqw.data.model.PartySlot
import froztt13.python.aqw.data.model.SlotConfig

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
    val DEFAULT_LIGHT_GATHER_SLOTS: List<String> =
        DEFAULT_SLOTS.filter { it.isLightGatherTaunter }.map { it.id }

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
     * Checks if the given slot key belongs to the sun team (slot1, slot2).
     */
    fun isSunSlot(slotKey: String): Boolean =
        slotKey.equals("slot1", ignoreCase = true) || slotKey.equals("slot2", ignoreCase = true)

    /**
     * Checks if the given slot key belongs to the moon team (slot3, slot4).
     */
    fun isMoonSlot(slotKey: String): Boolean =
        slotKey.equals("slot3", ignoreCase = true) || slotKey.equals("slot4", ignoreCase = true)

    /**
     * Resolves the primary fixed boss target for sun/moon slots.
     */
    fun getFixedPrimaryTarget(slotKey: String): String =
        if (isSunSlot(slotKey)) "Ascended Solstice" else "Ascended Midnight"

    /**
     * Enforces fixed roles, taunt flags, and target ordering for an Eclipse slot.
     */
    fun enforceSlotConfig(key: String, config: SlotConfig): SlotConfig {
        val isSun = isSunSlot(key)
        val fixedPrimary = getFixedPrimaryTarget(key)
        val targets =
            config.defaultTarget.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val normalizedTarget = if (targets.isEmpty()) {
            if (key == MASTER_SLOT_KEY) "Ascended Solstice,Blessless Deer" else fixedPrimary
        } else if (!targets.first().equals(fixedPrimary, ignoreCase = true)) {
            val remaining = targets.filterNot { it.equals(fixedPrimary, ignoreCase = true) }
            (listOf(fixedPrimary) + remaining).joinToString(",")
        } else {
            config.defaultTarget
        }
        val isLightGather = key != MASTER_SLOT_KEY && config.lightGatherTaunter

        return config.copy(
            isTaunter = true,
            sunsetKnightTaunter = isSun,
            moonHazeTaunter = !isSun,
            lightGatherTaunter = isLightGather,
            defaultTarget = normalizedTarget
        )
    }

    /**
     * Creates default [SlotConfig] matching the screen's initial presets.
     */
    fun createDefaultSlotConfig(slotKey: String): SlotConfig {
        val slot = getSlot(slotKey) ?: PartySlot(id = slotKey)
        val isMaster = slot.isMaster
        val isSun = isSunSlot(slotKey)
        val isMoon = isMoonSlot(slotKey)
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
        roomNumber: Int = DEFAULT_ROOM_NUMBER
    ): EclipseConfig {
        return EclipseConfig(
            server = server,
            roomNumber = roomNumber,
            slots = ALL_SLOTS.associateWith { createDefaultSlotConfig(it) }
        )
    }
}
