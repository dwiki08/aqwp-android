package froztt13.python.aqw.data.model

import froztt13.python.aqw.domain.bot.eclipse.NativeEclipseConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EclipseConfig(
    @SerialName("server")
    val server: String = NativeEclipseConfig.DEFAULT_SERVER,
    @SerialName("room_number")
    val roomNumber: Int = NativeEclipseConfig.DEFAULT_ROOM_NUMBER,
    @SerialName("slots")
    val slots: Map<String, SlotConfig> = defaultSlots()
) {
    fun enforceFixedRoles(): EclipseConfig {
        val updatedSlots = slots.mapValues { (key, config) ->
            NativeEclipseConfig.enforceSlotConfig(key, config)
        }
        return copy(slots = updatedSlots)
    }

    fun withToggledLightGather(slotKey: String): EclipseConfig {
        val slot = slots[slotKey] ?: return this
        val newSlots = slots.toMutableMap()
        newSlots[slotKey] = slot.copy(lightGatherTaunter = !slot.lightGatherTaunter)
        return copy(slots = newSlots)
    }

    fun withUpdatedSlot(slotKey: String, slotConfig: SlotConfig): EclipseConfig {
        val newSlots = slots.toMutableMap()
        newSlots[slotKey] = NativeEclipseConfig.enforceSlotConfig(slotKey, slotConfig)
        return copy(slots = newSlots)
    }

    fun toJson(): String = appJson.encodeToString(this)

    companion object {
        fun defaultSlots(): Map<String, SlotConfig> =
            NativeEclipseConfig.ALL_SLOTS.associateWith {
                NativeEclipseConfig.createDefaultSlotConfig(it)
            }

        fun fromJson(jsonStr: String): EclipseConfig =
            appJson.decodeFromString(jsonStr)
    }
}
