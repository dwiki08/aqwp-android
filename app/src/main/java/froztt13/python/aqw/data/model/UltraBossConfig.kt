package froztt13.python.aqw.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class UltraBossType(
    val displayName: String,
    val mapName: String,
    val defaultTarget: String,
    val insignia: String
) {
    @SerialName("gramiel")
    GRAMIEL("Ultra Gramiel", "ultagramiel", "Ultra Gramiel", "Gramiel Insignia"),

    @SerialName("malgor")
    MALGOR("Ultra Malgor", "ultramalgor", "Ultra Malgor", "Malgor Insignia"),

    @SerialName("drakath")
    DRAKATH("Ultra Drakath", "championdrakath", "Champion Drakath", "Drakath Insignia")
}

data class UltraBossInfo(
    val type: UltraBossType,
    val title: String,
    val mapName: String,
    val recommendedClasses: List<String>,
    val mechanics: List<String>,
    val insigniaName: String,
    val colorHex: Long
)

object UltraBossData {
    val GRAMIEL_INFO = UltraBossInfo(
        type = UltraBossType.GRAMIEL,
        title = "Ultra Gramiel",
        mapName = "ultagramiel",
        recommendedClasses = listOf(
            "DPS (Ravenous)",
            "Lord of Order",
            "StoneCrusher",
            "Legion Revenant"
        ),
        mechanics = listOf(
            "Phase 1: Alternate crystal taunts (LOO/DPS Left, LR/SC Right)",
            "Guard Break: Deal 20 Hits to Gramiel (Mid)",
            "Phase 2: Stop taunt for 4s on HP triggers (7.5M, 5.25M, 3.0M, 750k)"
        ),
        insigniaName = "Gramiel Insignia",
        colorHex = 0xFFF59E0B // Sun Gold
    )

    val MALGOR_INFO = UltraBossInfo(
        type = UltraBossType.MALGOR,
        title = "Ultra Malgor",
        mapName = "ultramalgor",
        recommendedClasses = listOf(
            "Chaos Avenger",
            "Lord of Order",
            "ArchPaladin",
            "Paladin Chronomancer"
        ),
        mechanics = listOf(
            "Dual-Taunter rotation on Elemental Shift callouts",
            "Zone switching & Listen for 'Listen to me!' voice telegraphs",
            "Keep LoO & AP defense buffs maxed"
        ),
        insigniaName = "Malgor Insignia",
        colorHex = 0xFFF43F5E // Doom Crimson
    )

    val DRAKATH_INFO = UltraBossInfo(
        type = UltraBossType.DRAKATH,
        title = "Ultra Drakath",
        mapName = "championdrakath",
        recommendedClasses = listOf(
            "ArchPaladin",
            "Lord of Order",
            "Chaos Avenger",
            "StoneCrusher"
        ),
        mechanics = listOf(
            "Taunt Drakath before reaching 18M, 14M, 10M, and 2M HP thresholds",
            "Alternate AP Skill 4 debuff to neutralize Chaos Nuke",
            "Taunter 1 taunts at start, Taunter 2 taunts at thresholds"
        ),
        insigniaName = "Drakath Insignia",
        colorHex = 0xFF8B5CF6 // Primary Purple
    )

    fun getInfo(type: UltraBossType): UltraBossInfo = when (type) {
        UltraBossType.GRAMIEL -> GRAMIEL_INFO
        UltraBossType.MALGOR -> MALGOR_INFO
        UltraBossType.DRAKATH -> DRAKATH_INFO
    }
}

@Serializable
data class UltraBossConfig(
    @SerialName("server")
    val server: String = "Alteon",
    @SerialName("room_number")
    val roomNumber: Int = 9099,
    @SerialName("selected_boss")
    val selectedBoss: UltraBossType = UltraBossType.GRAMIEL,
    @SerialName("auto_potions")
    val autoPotions: Boolean = true,
    @SerialName("use_scroll_of_enrage")
    val useScrollOfEnrage: Boolean = true,
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
                defaultTarget = "Ultra Gramiel"
            ),
            "slot2" to SlotConfig(
                charClass = "Lord of Order",
                role = "slave",
                isTaunter = true,
                defaultTarget = "Ultra Gramiel"
            ),
            "slot3" to SlotConfig(
                charClass = "Chaos Avenger",
                role = "slave",
                isTaunter = false,
                defaultTarget = "Ultra Gramiel"
            ),
            "slot4" to SlotConfig(
                charClass = "StoneCrusher",
                role = "slave",
                isTaunter = false,
                defaultTarget = "Ultra Gramiel"
            )
        )

        fun fromJson(jsonStr: String): UltraBossConfig =
            appJson.decodeFromString(jsonStr)
    }
}
