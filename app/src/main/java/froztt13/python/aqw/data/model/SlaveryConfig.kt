package froztt13.python.aqw.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SlaveryConfig(
    @SerialName("server")
    val server: String = "Gravelyn",
    @SerialName("follow_player")
    val followPlayer: String = "",
    @SerialName("default_room_number")
    val defaultRoomNumber: Int = 9099,
    @SerialName("copy_walk")
    val copyWalk: Boolean = true,
    @SerialName("auto_zone")
    val autoZone: String = "none",
    @SerialName("targets_priority")
    val targetsPriority: String = "Defense Drone,Staff of Inversion",
    @SerialName("whitelist")
    val whitelist: String = "Treasure Chest, Void Aura",
    @SerialName("locked_zones")
    val lockedZones: List<String> = defaultLockedZones(),
    @SerialName("slots")
    val slots: Map<String, SlaveSlotConfig> = defaultSlots()
) {
    fun toJson(): String = appJson.encodeToString(serializer(), this)

    companion object {
        fun defaultLockedZones(): List<String> = listOf(
            "ultraezrajal",
            "ultrawarden",
            "ultraengineer",
            "doomvault",
            "doomvaultb",
            "championdrakath",
            "tercessuinotlim",
            "icestormunder"
        )

        fun defaultSlots(): Map<String, SlaveSlotConfig> = mapOf(
            "slot1" to SlaveSlotConfig(enabled = true, charClass = "Lord of Order"),
            "slot2" to SlaveSlotConfig(enabled = true, charClass = "Legion Revenant"),
            "slot3" to SlaveSlotConfig(enabled = false, charClass = "ArchPaladin"),
            "slot4" to SlaveSlotConfig(enabled = false, charClass = "StoneCrusher")
        )

        fun fromJson(jsonStr: String): SlaveryConfig =
            appJson.decodeFromString(serializer(), jsonStr)
    }
}
