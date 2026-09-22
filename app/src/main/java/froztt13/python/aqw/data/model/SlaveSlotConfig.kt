package froztt13.python.aqw.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class SlaveSlotConfig(
    @SerialName("enabled")
    val enabled: Boolean = true,
    @SerialName("username")
    val username: String = "",
    @SerialName("password")
    val password: String = "",
    @SerialName("char_class")
    val charClass: String = "",
    @SerialName("skills")
    val skills: List<Skill> = defaultSkills(),
    @SerialName("is_taunter")
    val isTaunter: Boolean = false
) {
    fun toJson(): String = appJson.encodeToString(this)

    companion object {
        fun defaultSkills(): List<Skill> = listOf(
            Skill(index = 1),
            Skill(index = 2),
            Skill(index = 3),
            Skill(index = 4)
        )

        fun fromJson(jsonStr: String): SlaveSlotConfig =
            appJson.decodeFromString(jsonStr)
    }
}
