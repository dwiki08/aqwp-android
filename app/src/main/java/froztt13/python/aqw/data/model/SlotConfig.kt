package froztt13.python.aqw.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class SlotConfig(
    @SerialName("username")
    val username: String = "",
    @SerialName("password")
    val password: String = "",
    @SerialName("char_class")
    val charClass: String = "ArchPaladin",
    @SerialName("role")
    val role: String = "slave",
    @SerialName("is_taunter")
    val isTaunter: Boolean = false,
    @SerialName("moon_haze_taunter")
    val moonHazeTaunter: Boolean = false,
    @SerialName("sunset_knight_taunter")
    val sunsetKnightTaunter: Boolean = false,
    @SerialName("light_gather_taunter")
    val lightGatherTaunter: Boolean = false,
    @SerialName("default_target")
    val defaultTarget: String = ""
) {
    fun toJson(): String = appJson.encodeToString(this)

    companion object {
        fun fromJson(jsonStr: String): SlotConfig = appJson.decodeFromString(jsonStr)
    }
}
