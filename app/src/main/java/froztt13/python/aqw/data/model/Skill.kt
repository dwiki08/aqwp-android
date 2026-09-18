package froztt13.python.aqw.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Skill(
    @SerialName("id")
    val id: String = java.util.UUID.randomUUID().toString(),
    @SerialName("index")
    val index: Int = 1,
    @SerialName("threshold_type")
    val thresholdType: ThresholdType = ThresholdType.NONE,
    @SerialName("operator")
    val operator: String = "<",
    @SerialName("threshold_value")
    val thresholdValue: Int = 0
) {
    fun hasThreshold(): Boolean = thresholdType != ThresholdType.NONE && thresholdValue > 0

    fun toJson(): String = appJson.encodeToString(serializer(), this)

    companion object {
        fun fromJson(jsonStr: String): Skill = appJson.decodeFromString(serializer(), jsonStr)
    }
}
