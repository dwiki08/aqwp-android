package froztt13.python.aqw.core.temple

import froztt13.python.aqw.data.model.PartyMemberType
import froztt13.python.aqw.data.model.PartySlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeTempleConfigTest {

    @Test
    fun testPartySlotAndMemberType() {
        val slot = PartySlot(
            id = "slot1",
            memberType = PartyMemberType.MASTER,
            equipClass = "ArchPaladin",
            priorityTarget = "Ascended Midnight,Blessless Deer"
        )
        assertEquals("slot1", slot.id)
        assertEquals(PartyMemberType.MASTER, slot.memberType)
        assertTrue(slot.isMaster)
        assertFalse(slot.isSlave)

        assertEquals(4, NativeTempleConfig.DEFAULT_SLOTS.size)
        assertEquals("slot1", NativeTempleConfig.MASTER_SLOT_KEY)
        assertEquals(listOf("slot2", "slot3", "slot4"), NativeTempleConfig.SLAVE_SLOT_KEYS)
        assertTrue(NativeTempleConfig.isMasterSlot("slot1"))
        assertFalse(NativeTempleConfig.isSlaveSlot("slot1"))
        assertFalse(NativeTempleConfig.isMasterSlot("slot2"))
        assertTrue(NativeTempleConfig.isSlaveSlot("slot2"))
    }

    @Test
    fun testDefaultClasses() {
        assertEquals("ArchPaladin", NativeTempleConfig.getDefaultClass("slot1"))
        assertEquals("StoneCrusher", NativeTempleConfig.getDefaultClass("slot2"))
        assertEquals("Legion Revenant", NativeTempleConfig.getDefaultClass("slot3"))
        assertEquals("Lord of Order", NativeTempleConfig.getDefaultClass("slot4"))
        assertEquals("", NativeTempleConfig.getDefaultClass("unknown"))
    }

    @Test
    fun testDefaultTargets() {
        // MidnightSun default
        assertEquals(
            "Ascended Midnight,Blessless Deer",
            NativeTempleConfig.getDefaultTarget("slot1")
        )
        assertEquals(
            "Ascended Midnight,Blessless Deer",
            NativeTempleConfig.getDefaultTarget("slot2")
        )

        // SolsticeMoon override for unknown slot
        assertEquals(
            "Lunar Haze",
            NativeTempleConfig.getDefaultTarget("unknown", botType = "SolsticeMoonBot")
        )

        // Custom override
        assertEquals("Custom Target", NativeTempleConfig.getDefaultTarget("slot1", "Custom Target"))
    }

    @Test
    fun testMapsAndMonsters() {
        assertEquals("midnightsun", NativeTempleConfig.getDungeonMap("MidnightSunBot"))
        assertEquals("solsticemoon", NativeTempleConfig.getDungeonMap("SolsticeMoonBot"))

        assertEquals("Lunar Haze", NativeTempleConfig.getDefaultTargetMonsters("solsticemoon"))
        assertEquals(
            "Dying Light,Dawn Knight",
            NativeTempleConfig.getDefaultTargetMonsters("midnightsun")
        )
    }

    @Test
    fun testCreateDefaultSlotConfig() {
        val slot1 = NativeTempleConfig.createDefaultSlotConfig("slot1")
        assertEquals("master", slot1.role)
        assertEquals("ArchPaladin", slot1.charClass)
        assertTrue(slot1.isTaunter)

        val slot2 = NativeTempleConfig.createDefaultSlotConfig("slot2")
        assertEquals("slave", slot2.role)
        assertEquals("StoneCrusher", slot2.charClass)
        assertFalse(slot2.isTaunter)
    }

    @Test
    fun testCreateDefaultConfig() {
        val config = NativeTempleConfig.createDefaultConfig()
        assertEquals(4, config.slots.size)
        assertEquals("Alteon", config.server)
        assertEquals(9099, config.roomNumber)
        assertEquals("MidnightSunBot", config.templeBotType)
        assertNotNull(config.slots["slot1"])
    }

    @Test
    fun testNativeTempleBotConfigAlias() {
        assertEquals(NativeTempleConfig.DEFAULT_SERVER, NativeTempleBot.Config.DEFAULT_SERVER)
        assertEquals(
            NativeTempleConfig.DEFAULT_ROOM_NUMBER,
            NativeTempleBot.Config.DEFAULT_ROOM_NUMBER
        )
    }
}
