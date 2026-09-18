package froztt13.python.aqw.data.repository

import froztt13.python.aqw.data.model.EclipseConfig
import froztt13.python.aqw.data.model.GeneralBotConfig
import froztt13.python.aqw.data.model.SlaveryConfig
import froztt13.python.aqw.data.model.SlotConfig
import froztt13.python.aqw.data.model.TempleConfig
import froztt13.python.aqw.data.model.WeeklyDoomConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigRepositoryTest {

    private val repo = ConfigRepositoryImpl()

    @Test
    fun testEclipseSerializationRoundTrip() {
        val original = EclipseConfig().enforceFixedRoles()
        val json = original.toJson()
        println("Eclipse JSON:\n$json")
        assertTrue(json.contains("\"room_number\""))
        assertTrue(json.contains("\"slots\""))

        val deserialized = EclipseConfig.fromJson(json)
        assertEquals(original.server, deserialized.server)
        assertEquals(original.roomNumber, deserialized.roomNumber)
        assertEquals(original.slots.size, deserialized.slots.size)

        val parsedViaRepo = repo.parseEclipseConfig(json)
        assertEquals(original.server, parsedViaRepo.server)
        assertEquals(original.slots.size, parsedViaRepo.slots.size)
    }

    @Test
    fun testSlotConfigSerializationRoundTrip() {
        val original = SlotConfig(
            username = "player1",
            password = "pwd",
            charClass = "ArchPaladin",
            isTaunter = true,
            defaultTarget = "Ascended Midnight"
        )
        val json = original.toJson()
        println("Slot JSON:\n$json")
        assertTrue(json.contains("\"char_class\""))
        assertTrue(json.contains("\"is_taunter\""))

        val deserialized = SlotConfig.fromJson(json)
        assertEquals(original.username, deserialized.username)
        assertEquals(original.charClass, deserialized.charClass)
        assertEquals(original.isTaunter, deserialized.isTaunter)
    }

    @Test
    fun testTempleSerializationRoundTrip() {
        val original = TempleConfig()
        val json = original.toJson()
        println("Temple JSON:\n$json")
        assertTrue(json.contains("\"temple_bot_type\""))

        val deserialized = TempleConfig.fromJson(json)
        assertEquals(original.server, deserialized.server)
        assertEquals(original.roomNumber, deserialized.roomNumber)
        assertEquals(original.slots.size, deserialized.slots.size)

        val parsedViaRepo = repo.parseTempleConfig(json)
        assertEquals(original.server, parsedViaRepo.server)
    }

    @Test
    fun testDoomSerializationRoundTrip() {
        val original = WeeklyDoomConfig()
        val json = original.toJson()
        println("Doom JSON:\n$json")

        val deserialized = WeeklyDoomConfig.fromJson(json)
        assertEquals(original.server, deserialized.server)
        assertEquals(original.accounts.size, deserialized.accounts.size)

        val parsedViaRepo = repo.parseDoomConfig(json)
        assertEquals(original.server, parsedViaRepo.server)
    }

    @Test
    fun testSlaverySerializationRoundTrip() {
        val original = SlaveryConfig()
        val json = original.toJson()
        println("Slavery JSON:\n$json")
        assertTrue(json.contains("\"follow_player\""))
        assertTrue(json.contains("\"locked_zones\""))

        val deserialized = SlaveryConfig.fromJson(json)
        assertEquals(original.server, deserialized.server)
        assertEquals(original.slots.size, deserialized.slots.size)

        val parsedViaRepo = repo.parseSlaveryConfig(json)
        assertEquals(original.server, parsedViaRepo.server)
    }

    @Test
    fun testGeneralSerializationRoundTrip() {
        val original = GeneralBotConfig()
        val json = original.toJson()
        println("General JSON:\n$json")
        assertTrue(json.contains("\"sub_module\""))
        assertTrue(json.contains("\"target_qty\""))

        val deserialized = GeneralBotConfig.fromJson(json)
        assertEquals(original.server, deserialized.server)
        assertEquals(original.targetQty, deserialized.targetQty)

        val parsedViaRepo = repo.parseGeneralConfig(json)
        assertEquals(original.server, parsedViaRepo.server)
    }

    @Test
    fun testEmptyJsonFallback() {
        val parsed = repo.parseEclipseConfig("{}")
        println("Empty Eclipse slots: ${parsed.slots}")
        assertNotNull(parsed)
        assertEquals(4, parsed.slots.size)
    }
}
