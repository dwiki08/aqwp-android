package froztt13.python.aqw.core.eclipse

/**
 * Role type for a party member in coordinated party bot.
 */
enum class PartyMemberType {
    MASTER,
    SLAVE
}

/**
 * Data class representing a party slot's configuration presets and roles.
 *
 * @property id The slot key identifier (e.g. "slot1", "slot2").
 * @property memberType The role type ([PartyMemberType.MASTER] or [PartyMemberType.SLAVE]) for this slot.
 * @property equipClass The recommended class to equip for this slot.
 * @property priorityTarget The primary target monster(s) for this slot.
 * @property isLightGatherTaunter Whether this slot is assigned to taunt Suffocated Light.
 */
data class PartySlot(
    val id: String,
    val memberType: PartyMemberType = PartyMemberType.SLAVE,
    val equipClass: String = "",
    val priorityTarget: String = "",
    val isLightGatherTaunter: Boolean = false
) {
    val isMaster: Boolean get() = memberType == PartyMemberType.MASTER
    val isSlave: Boolean get() = memberType == PartyMemberType.SLAVE
}
