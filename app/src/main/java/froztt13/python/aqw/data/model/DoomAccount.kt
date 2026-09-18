package froztt13.python.aqw.data.model

import kotlinx.serialization.Serializable

@Serializable
data class DoomAccount(
    val id: String = java.util.UUID.randomUUID().toString(),
    val username: String = "",
    val password: String = "",
    val enabled: Boolean = true
) {
    fun toJson(): String = appJson.encodeToString(serializer(), this)

    companion object {
        fun fromJson(jsonStr: String): DoomAccount = appJson.decodeFromString(serializer(), jsonStr)
    }
}
