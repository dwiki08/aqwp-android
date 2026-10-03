package froztt13.python.aqw.domain.bot.gramiel

import froztt13.python.aqw.data.engine.AqwSession
import froztt13.python.aqw.domain.model.AqwItem
import froztt13.python.aqw.domain.model.AqwMonster
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NativeUltraGramielBotTauntTest {

    @Before
    fun setup() {
        NativeUltraGramielBot.stop()
        NativeUltraGramielBot.shatteringCount.set(0)
        NativeUltraGramielBot.lastShatteringTime = 0L
        NativeUltraGramielBot.pendingTauntTargets.clear()
        NativeUltraGramielBot.activeSessions.clear()
    }

    @Test
    fun testShatteringTauntAlternatesOddEven() {
        // Wave 1 (Odd): slot1 taunts mon 2, slot3 taunts mon 3
        NativeUltraGramielBot.onShatteringDetected("slot1")
        assertEquals(1, NativeUltraGramielBot.shatteringCount.get())
        assertEquals("2", NativeUltraGramielBot.pendingTauntTargets["slot1"])
        assertEquals(null, NativeUltraGramielBot.pendingTauntTargets["slot2"])
        assertEquals("3", NativeUltraGramielBot.pendingTauntTargets["slot3"])
        assertEquals(null, NativeUltraGramielBot.pendingTauntTargets["slot4"])

        // Debounce test: immediate call within debounce window is ignored
        NativeUltraGramielBot.onShatteringDetected("slot1")
        assertEquals(1, NativeUltraGramielBot.shatteringCount.get())

        // Clear pending taunts to simulate taunt execution
        NativeUltraGramielBot.pendingTauntTargets.clear()

        // Wave 2 (Even): slot2 taunts mon 2, slot4 taunts mon 3
        NativeUltraGramielBot.lastShatteringTime = 0L
        NativeUltraGramielBot.onShatteringDetected("slot1")
        assertEquals(2, NativeUltraGramielBot.shatteringCount.get())
        assertEquals(null, NativeUltraGramielBot.pendingTauntTargets["slot1"])
        assertEquals("2", NativeUltraGramielBot.pendingTauntTargets["slot2"])
        assertEquals(null, NativeUltraGramielBot.pendingTauntTargets["slot3"])
        assertEquals("3", NativeUltraGramielBot.pendingTauntTargets["slot4"])

        NativeUltraGramielBot.pendingTauntTargets.clear()

        // Wave 3 (Odd): alternates back to slot1 (mon 2) and slot3 (mon 3)
        NativeUltraGramielBot.lastShatteringTime = 0L
        NativeUltraGramielBot.onShatteringDetected("slot1")
        assertEquals(3, NativeUltraGramielBot.shatteringCount.get())
        assertEquals("2", NativeUltraGramielBot.pendingTauntTargets["slot1"])
        assertEquals(null, NativeUltraGramielBot.pendingTauntTargets["slot2"])
        assertEquals("3", NativeUltraGramielBot.pendingTauntTargets["slot3"])
        assertEquals(null, NativeUltraGramielBot.pendingTauntTargets["slot4"])
    }

    @Test
    fun testDeadMonsterDisablesTauntForAssignedSlots() {
        val session1 = AqwSession()
        session1.slotKey = "slot1"
        NativeUltraGramielBot.activeSessions["slot1"] = session1

        val mon3Only = listOf(
            AqwMonster(
                monMapId = "3",
                name = "Left Boss",
                currentHp = 5000,
                maxHp = 5000,
                isAlive = true,
                frame = "r2"
            )
        )
        session1.map.setMonsters(mon3Only)

        try {
            // Monster ID 2 is dead/missing, Monster ID 3 is alive
            assertFalse(NativeUltraGramielBot.isMonsterAlive("2"))
            assertTrue(NativeUltraGramielBot.isMonsterAlive("3"))

            NativeUltraGramielBot.onShatteringDetected("slot1")

            // Taunt for slots 1 & 2 (Monster ID 2) should NOT be queued
            assertNull(NativeUltraGramielBot.pendingTauntTargets["slot1"])
            assertNull(NativeUltraGramielBot.pendingTauntTargets["slot2"])

            // Taunt for slot 3 (Monster ID 3) SHOULD be queued
            assertEquals("3", NativeUltraGramielBot.pendingTauntTargets["slot3"])
        } finally {
            NativeUltraGramielBot.activeSessions.remove("slot1")
            session1.stop()
        }
    }

    @Test
    fun testOnlyGramielAliveIgnoresShatteringTriggers() {
        val session1 = AqwSession()
        session1.slotKey = "slot1"
        NativeUltraGramielBot.activeSessions["slot1"] = session1

        val gramielOnly = listOf(
            AqwMonster(
                monMapId = "1",
                name = "Ultra Gramiel",
                currentHp = 10000,
                maxHp = 10000,
                isAlive = true,
                frame = "r2"
            )
        )
        session1.map.setMonsters(gramielOnly)

        try {
            assertTrue(NativeUltraGramielBot.isMonsterAlive("1"))
            assertFalse(NativeUltraGramielBot.isMonsterAlive("2"))
            assertFalse(NativeUltraGramielBot.isMonsterAlive("3"))

            // Shattering detection in Phase 2 should ignore queuing taunts
            NativeUltraGramielBot.onShatteringDetected("slot1")
            assertNull(NativeUltraGramielBot.pendingTauntTargets["slot1"])
            assertNull(NativeUltraGramielBot.pendingTauntTargets["slot2"])
            assertNull(NativeUltraGramielBot.pendingTauntTargets["slot3"])
            assertNull(NativeUltraGramielBot.pendingTauntTargets["slot4"])
        } finally {
            NativeUltraGramielBot.activeSessions.remove("slot1")
            session1.stop()
        }
    }

    @Test
    fun testGramielVanquishedTempItemDetection() {
        val session1 = AqwSession()
        session1.slotKey = "slot1"
        NativeUltraGramielBot.activeSessions["slot1"] = session1

        try {
            assertFalse(NativeUltraGramielBot.isGramielVanquished())

            session1.playerState.tempInventory.add(
                AqwItem(
                    itemId = 999,
                    name = "Gramiel the Graceful Vanquished",
                    qty = 1,
                    isTemp = true
                )
            )

            assertTrue(NativeUltraGramielBot.isGramielVanquished())
        } finally {
            NativeUltraGramielBot.activeSessions.remove("slot1")
            session1.stop()
        }
    }
}
