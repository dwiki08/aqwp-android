package froztt13.python.aqw.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WeeklyDoomConfig(
    @SerialName("server")
    val server: String = "Alteon",
    @SerialName("accounts")
    val accounts: List<DoomAccount> = listOf(DoomAccount())
) {
    fun toJson(): String = appJson.encodeToString(serializer(), this)

    companion object {
        fun fromJson(jsonStr: String): WeeklyDoomConfig =
            appJson.decodeFromString(serializer(), jsonStr)
    }
}
