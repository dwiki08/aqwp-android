package froztt13.python.aqw.domain.bot.malgor

import froztt13.python.aqw.data.engine.AqwSession
import froztt13.python.aqw.domain.model.AqwItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NativeUltraMalgorBotTauntTest {

    @Before
    fun setup() {
        NativeUltraMalgorBot.stop()
        NativeUltraMalgorBot.calloutCount.set(0)
        NativeUltraMalgorBot.lastCalloutTime = 0L
        NativeUltraMalgorBot.pendingTauntTargets.clear()
        NativeUltraMalgorBot.activeSessions.clear()
    }

    @Test
    fun testCalloutTauntRotatesAcross4SlotsInRoundRobin() {
        NativeUltraMalgorBot.onCalloutDetected("slot1", "Listen to me!")
        assertEquals(1, NativeUltraMalgorBot.calloutCount.get())
        assertEquals("1", NativeUltraMalgorBot.pendingTauntTargets["slot1"])
        assertNull(NativeUltraMalgorBot.pendingTauntTargets["slot2"])
    }

    @Test
    fun testMalgorVanquishedTempItemCompletion() {
        val session1 = AqwSession()
        session1.slotKey = "slot1"
        NativeUltraMalgorBot.activeSessions["slot1"] = session1

        try {
            session1.playerState.tempInventory.add(
                AqwItem(
                    itemId = 888,
                    name = "Ultra Speaker Vanquished",
                    qty = 1,
                    isTemp = true
                )
            )

            assertTrue(
                session1.playerState.tempInventory.any {
                    it.name.equals("Ultra Speaker Vanquished", ignoreCase = true)
                }
            )
        } finally {
            NativeUltraMalgorBot.activeSessions.remove("slot1")
            session1.stop()
        }
    }

    @Test
    fun testMagiaBurnAuraAndDeadPlayerSkipsSlot() {
        val session1 = AqwSession()
        session1.slotKey = "slot1"
        NativeUltraMalgorBot.activeSessions["slot1"] = session1

        val session2 = AqwSession()
        session2.slotKey = "slot2"
        NativeUltraMalgorBot.activeSessions["slot2"] = session2

        try {
            // Slot 1 has "Magia Burn" aura -> ineligible
            session1.playerState.addAura("Magia Burn", 10)
            assertFalse(NativeUltraMalgorBot.isSlotEligibleForTaunt("slot1"))

            // Slot 2 is alive with no Magia Burn -> eligible
            assertTrue(NativeUltraMalgorBot.isSlotEligibleForTaunt("slot2"))

            // Wave 1 would pick slot1, but slot1 has Magia Burn -> advances to slot2
            NativeUltraMalgorBot.onCalloutDetected("slot1", "Listen!")
            assertNull(NativeUltraMalgorBot.pendingTauntTargets["slot1"])
            assertEquals("1", NativeUltraMalgorBot.pendingTauntTargets["slot2"])
        } finally {
            NativeUltraMalgorBot.activeSessions.clear()
            session1.stop()
            session2.stop()
        }
    }
}
