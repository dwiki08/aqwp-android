package froztt13.python.aqw.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class DoomAccount(
    val id: String = java.util.UUID.randomUUID().toString(),
    val username: String = "",
    val password: String = "",
    val enabled: Boolean = true
) {
    fun toJson(): String = appJson.encodeToString(this)

    companion object {
        fun fromJson(jsonStr: String): DoomAccount = appJson.decodeFromString(jsonStr)
    }
}
