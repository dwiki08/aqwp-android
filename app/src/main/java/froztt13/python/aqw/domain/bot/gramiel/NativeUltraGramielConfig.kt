package froztt13.python.aqw.domain.bot.gramiel

import froztt13.python.aqw.data.model.PartyMemberType
import froztt13.python.aqw.data.model.PartySlot
import froztt13.python.aqw.data.model.SlotConfig
import froztt13.python.aqw.data.model.UltraBossConfig

/**
 * Default configuration presets for Native Ultra Gramiel bot.
 */
object NativeUltraGramielConfig {

    const val DEFAULT_TARGET_SLOT_1_2 = "2"
    const val DEFAULT_TARGET_SLOT_3_4 = "3"

    val DEFAULT_SLOTS: List<PartySlot> = listOf(
        PartySlot(
            id = "slot1",
            memberType = PartyMemberType.MASTER,
            equipClass = "LightCaster",
            priorityTarget = DEFAULT_TARGET_SLOT_1_2
        ),
        PartySlot(
            id = "slot2",
            memberType = PartyMemberType.SLAVE,
            equipClass = "ArchPaladin",
            priorityTarget = DEFAULT_TARGET_SLOT_1_2
        ),
        PartySlot(
            id = "slot3",
            memberType = PartyMemberType.SLAVE,
            equipClass = "StoneCrusher",
            priorityTarget = DEFAULT_TARGET_SLOT_3_4
        ),
        PartySlot(
            id = "slot4",
            memberType = PartyMemberType.SLAVE,
            equipClass = "Lord of Order",
            priorityTarget = DEFAULT_TARGET_SLOT_3_4
        )
    )

    val ALL_SLOTS: List<String> = DEFAULT_SLOTS.map { it.id }

    val MASTER_SLOT: PartySlot = DEFAULT_SLOTS.first { it.isMaster }
    val MASTER_SLOT_KEY: String = MASTER_SLOT.id

    val SLAVE_SLOTS: List<PartySlot> = DEFAULT_SLOTS.filter { it.isSlave }
    val SLAVE_SLOT_KEYS: List<String> = SLAVE_SLOTS.map { it.id }

    const val DEFAULT_SERVER = "Alteon"
    const val DEFAULT_ROOM_NUMBER = 9099

    fun getSlot(slotKey: String): PartySlot? =
        DEFAULT_SLOTS.find { it.id.equals(slotKey, ignoreCase = true) }

    fun getDefaultTarget(slotKey: String, configuredTarget: String? = null): String {
        if (!configuredTarget.isNullOrBlank()) {
            return configuredTarget
        }
        return if (slotKey == "slot1" || slotKey == "slot2") DEFAULT_TARGET_SLOT_1_2 else DEFAULT_TARGET_SLOT_3_4
    }

    fun getDefaultClass(slotKey: String): String {
        return getSlot(slotKey)?.equipClass ?: ""
    }

    fun isMasterSlot(slotKey: String): Boolean =
        getSlot(slotKey)?.isMaster ?: false

    fun isSlaveSlot(slotKey: String): Boolean =
        getSlot(slotKey)?.isSlave ?: false

    fun createDefaultSlotConfig(slotKey: String): SlotConfig {
        val slot = getSlot(slotKey) ?: PartySlot(id = slotKey)
        return SlotConfig(
            charClass = slot.equipClass,
            role = if (slot.isMaster) "master" else "slave",
            isTaunter = true,
            defaultTarget = getDefaultTarget(slotKey)
        )
    }

    fun createDefaultConfig(
        server: String = DEFAULT_SERVER,
        roomNumber: Int = DEFAULT_ROOM_NUMBER
    ): UltraBossConfig {
        return UltraBossConfig(
            server = server,
            roomNumber = roomNumber,
            slots = ALL_SLOTS.associateWith { createDefaultSlotConfig(it) }
        )
    }
}
