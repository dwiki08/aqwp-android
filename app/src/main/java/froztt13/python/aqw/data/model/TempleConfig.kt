package froztt13.python.aqw.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class TempleConfig(
    @SerialName("server")
    val server: String = "Alteon",
    @SerialName("room_number")
    val roomNumber: Int = 9099,
    @SerialName("temple_bot_type")
    val templeBotType: String = "MidnightSunBot",
    @SerialName("slots")
    val slots: Map<String, SlotConfig> = defaultSlots()
) {
    fun toJson(): String = appJson.encodeToString(this)

    companion object {
        fun defaultSlots(): Map<String, SlotConfig> = mapOf(
            "slot1" to SlotConfig(
                charClass = "ArchPaladin",
                role = "master",
                isTaunter = true,
                defaultTarget = "Ascended Midnight,Blessless Deer"
            ),
            "slot2" to SlotConfig(
                charClass = "StoneCrusher",
                role = "slave",
                isTaunter = false,
                defaultTarget = "Ascended Midnight,Blessless Deer"
            ),
            "slot3" to SlotConfig(
                charClass = "Legion Revenant",
                role = "slave",
                isTaunter = false,
                defaultTarget = "Ascended Midnight,Blessless Deer"
            ),
            "slot4" to SlotConfig(
                charClass = "Lord of Order",
                role = "slave",
                isTaunter = false,
                defaultTarget = "Ascended Midnight,Blessless Deer"
            )
        )

        fun fromJson(jsonStr: String): TempleConfig =
            appJson.decodeFromString(jsonStr)
    }
}
