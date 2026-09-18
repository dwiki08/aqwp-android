package froztt13.python.aqw.domain.bot.eclipse

import froztt13.python.aqw.data.model.PartyMemberType
import froztt13.python.aqw.data.model.PartySlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeEclipseConfigTest {

    @Test
    fun testDefaultTargets() {
        assertEquals(
            "Ascended Solstice,Blessless Deer",
            NativeEclipseConfig.getDefaultTarget("slot1")
        )
        assertEquals("Ascended Solstice", NativeEclipseConfig.getDefaultTarget("slot2"))
        assertEquals("Ascended Midnight", NativeEclipseConfig.getDefaultTarget("slot3"))
        assertEquals("Ascended Midnight", NativeEclipseConfig.getDefaultTarget("slot4"))
        assertEquals("Ascended Solstice", NativeEclipseConfig.getDefaultTarget("slot5"))

        // Custom override
        assertEquals(
            "Custom Target",
            NativeEclipseConfig.getDefaultTarget("slot1", "Custom Target")
        )
        assertEquals(
            "Ascended Solstice,Blessless Deer",
            NativeEclipseConfig.getDefaultTarget("slot1", "   ")
        )
    }

    @Test
    fun testDefaultClasses() {
        assertEquals("Legion Revenant", NativeEclipseConfig.getDefaultClass("slot1"))
        assertEquals("StoneCrusher", NativeEclipseConfig.getDefaultClass("slot2"))
        assertEquals("ArchPaladin", NativeEclipseConfig.getDefaultClass("slot3"))
        assertEquals("Lord of Order", NativeEclipseConfig.getDefaultClass("slot4"))
        assertEquals("", NativeEclipseConfig.getDefaultClass("slot99"))
    }

    @Test
    fun testCreateDefaultSlotConfig() {
        val slot1 = NativeEclipseConfig.createDefaultSlotConfig("slot1")
        assertEquals("master", slot1.role)
        assertEquals("Legion Revenant", slot1.charClass)
        assertTrue(slot1.sunsetKnightTaunter)
        assertFalse(slot1.moonHazeTaunter)
        assertFalse(slot1.lightGatherTaunter)
        assertEquals("Ascended Solstice,Blessless Deer", slot1.defaultTarget)

        val slot2 = NativeEclipseConfig.createDefaultSlotConfig("slot2")
        assertEquals("slave", slot2.role)
        assertEquals("StoneCrusher", slot2.charClass)
        assertTrue(slot2.sunsetKnightTaunter)
        assertFalse(slot2.moonHazeTaunter)
        assertTrue(slot2.lightGatherTaunter)

        val slot3 = NativeEclipseConfig.createDefaultSlotConfig("slot3")
        assertEquals("slave", slot3.role)
        assertEquals("ArchPaladin", slot3.charClass)
        assertFalse(slot3.sunsetKnightTaunter)
        assertTrue(slot3.moonHazeTaunter)
        assertTrue(slot3.lightGatherTaunter)

        val slot4 = NativeEclipseConfig.createDefaultSlotConfig("slot4")
        assertEquals("slave", slot4.role)
        assertEquals("Lord of Order", slot4.charClass)
        assertFalse(slot4.sunsetKnightTaunter)
        assertTrue(slot4.moonHazeTaunter)
        assertTrue(slot4.lightGatherTaunter)
    }

    @Test
    fun testCreateDefaultConfig() {
        val config = NativeEclipseConfig.createDefaultConfig()
        assertEquals(4, config.slots.size)
        assertEquals(NativeEclipseConfig.DEFAULT_SERVER, config.server)
        assertEquals(NativeEclipseConfig.DEFAULT_ROOM_NUMBER, config.roomNumber)
    }

    @Test
    fun testNativeEclipseBotConfigAlias() {
        assertEquals(NativeEclipseConfig.DEFAULT_SERVER, NativeEclipseBot.Config.DEFAULT_SERVER)
        assertEquals(
            NativeEclipseConfig.DEFAULT_ROOM_NUMBER,
            NativeEclipseBot.Config.DEFAULT_ROOM_NUMBER
        )
    }

    @Test
    fun testPartySlot() {
        val slot = PartySlot(
            id = "slot2",
            memberType = PartyMemberType.SLAVE,
            equipClass = "StoneCrusher",
            priorityTarget = "Ascended Solstice",
            isLightGatherTaunter = true
        )
        assertEquals("slot2", slot.id)
        assertEquals(PartyMemberType.SLAVE, slot.memberType)
        assertFalse(slot.isMaster)
        assertTrue(slot.isSlave)
        assertEquals("StoneCrusher", slot.equipClass)
        assertEquals("Ascended Solstice", slot.priorityTarget)
        assertTrue(slot.isLightGatherTaunter)

        assertEquals(4, NativeEclipseConfig.DEFAULT_SLOTS.size)
        assertEquals("slot1", NativeEclipseConfig.MASTER_SLOT_KEY)
        assertEquals(listOf("slot2", "slot3", "slot4"), NativeEclipseConfig.SLAVE_SLOT_KEYS)
        assertTrue(NativeEclipseConfig.isMasterSlot("slot1"))
        assertFalse(NativeEclipseConfig.isSlaveSlot("slot1"))
        assertFalse(NativeEclipseConfig.isMasterSlot("slot2"))
        assertTrue(NativeEclipseConfig.isSlaveSlot("slot2"))

        val s1 = NativeEclipseConfig.getSlot("slot1")
        assertEquals(PartyMemberType.MASTER, s1!!.memberType)
        assertTrue(s1.isMaster)
        assertFalse(s1.isLightGatherTaunter)
        assertEquals("Legion Revenant", s1.equipClass)

        val s2 = NativeEclipseConfig.getSlot("slot2")
        assertEquals(PartyMemberType.SLAVE, s2!!.memberType)
        assertTrue(s2.isSlave)
        assertTrue(s2.isLightGatherTaunter)
        assertEquals("StoneCrusher", s2.equipClass)

        val s3 = NativeEclipseConfig.getSlot("slot3")
        assertEquals(PartyMemberType.SLAVE, s3!!.memberType)
        assertTrue(s3.isSlave)
        assertTrue(s3.isLightGatherTaunter)
        assertEquals("ArchPaladin", s3.equipClass)

        val s4 = NativeEclipseConfig.getSlot("slot4")
        assertEquals(PartyMemberType.SLAVE, s4!!.memberType)
        assertTrue(s4.isSlave)
        assertTrue(s4.isLightGatherTaunter)
        assertEquals("Lord of Order", s4.equipClass)
    }
}
