package froztt13.python.aqw.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class GeneralBotConfig(
    @SerialName("server")
    val server: String = "Alteon",
    @SerialName("room_number")
    val roomNumber: Int = 9099,
    @SerialName("username")
    val username: String = "",
    @SerialName("password")
    val password: String = "",
    @SerialName("sub_module")
    val subModule: String = "lr",
    @SerialName("task")
    val task: String = "spellscroll",
    @SerialName("target_qty")
    val targetQty: Int = 20,
    @SerialName("solo_class")
    val soloClass: String = "Void Highlord",
    @SerialName("farm_class")
    val farmClass: String = "Legion Revenant"
) {
    fun toJson(): String = appJson.encodeToString(this)

    companion object {
        fun fromJson(jsonStr: String): GeneralBotConfig =
            appJson.decodeFromString(jsonStr)
    }
}
