package froztt13.python.aqw.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class EclipseConfig(
    @SerialName("server")
    val server: String = "Alteon",
    @SerialName("room_number")
    val roomNumber: Int = 9099,
    @SerialName("slots")
    val slots: Map<String, SlotConfig> = defaultSlots()
) {
    fun enforceFixedRoles(): EclipseConfig {
        val updatedSlots = slots.mapValues { (key, config) ->
            enforceSlotConfig(key, config)
        }
        return copy(slots = updatedSlots)
    }

    fun withToggledLightGather(slotKey: String): EclipseConfig {
        if (slotKey == "slot1") return this
        val slot = slots[slotKey] ?: return this
        val newSlots = slots.toMutableMap()
        newSlots[slotKey] = slot.copy(lightGatherTaunter = !slot.lightGatherTaunter)
        return copy(slots = newSlots)
    }

    fun withUpdatedSlot(slotKey: String, slotConfig: SlotConfig): EclipseConfig {
        val newSlots = slots.toMutableMap()
        newSlots[slotKey] = enforceSlotConfig(slotKey, slotConfig)
        return copy(slots = newSlots)
    }

    private fun enforceSlotConfig(key: String, config: SlotConfig): SlotConfig {
        val isSun = key == "slot1" || key == "slot2"
        val fixedPrimary = if (isSun) "Ascended Solstice" else "Ascended Midnight"
        val targets =
            config.defaultTarget.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val normalizedTarget = if (targets.isEmpty()) {
            if (key == "slot1") "Ascended Solstice,Blessless Deer" else fixedPrimary
        } else if (!targets.first().equals(fixedPrimary, ignoreCase = true)) {
            val remaining = targets.filterNot { it.equals(fixedPrimary, ignoreCase = true) }
            (listOf(fixedPrimary) + remaining).joinToString(",")
        } else {
            config.defaultTarget
        }
        val isLightGather = key != "slot1" && config.lightGatherTaunter

        return config.copy(
            isTaunter = true,
            sunsetKnightTaunter = isSun,
            moonHazeTaunter = !isSun,
            lightGatherTaunter = isLightGather,
            defaultTarget = normalizedTarget
        )
    }

    fun toJson(): String = appJson.encodeToString(this)

    companion object {
        fun defaultSlots(): Map<String, SlotConfig> = mapOf(
            "slot1" to SlotConfig(
                charClass = "Legion Revenant",
                role = "master",
                isTaunter = true,
                moonHazeTaunter = false,
                sunsetKnightTaunter = true,
                lightGatherTaunter = false,
                defaultTarget = "Ascended Solstice,Blessless Deer"
            ),
            "slot2" to SlotConfig(
                charClass = "StoneCrusher",
                role = "slave",
                isTaunter = true,
                moonHazeTaunter = false,
                sunsetKnightTaunter = true,
                lightGatherTaunter = true,
                defaultTarget = "Ascended Solstice"
            ),
            "slot3" to SlotConfig(
                charClass = "ArchPaladin",
                role = "slave",
                isTaunter = true,
                moonHazeTaunter = true,
                sunsetKnightTaunter = false,
                lightGatherTaunter = true,
                defaultTarget = "Ascended Midnight"
            ),
            "slot4" to SlotConfig(
                charClass = "Lord of Order",
                role = "slave",
                isTaunter = true,
                moonHazeTaunter = true,
                sunsetKnightTaunter = false,
                lightGatherTaunter = true,
                defaultTarget = "Ascended Midnight"
            )
        )

        fun fromJson(jsonStr: String): EclipseConfig =
            appJson.decodeFromString(jsonStr)
    }
}
