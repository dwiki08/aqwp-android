package froztt13.python.aqw.data.engine

import froztt13.python.aqw.domain.model.AqwItem
import froztt13.python.aqw.domain.model.AqwPlayerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnInTest {

    @Test
    fun testTurnInPacketParsing() {
        val packet = """{"t":"xt","b":{"r":-1,"o":{"cmd":"turnIn","sItems":"2578:5, 2579:1"}}}"""
        val event = AqwPacketParser.parse(packet, "hero")

        assertTrue(event is AqwEvent.ItemsTurnedIn)
        val itemsTurnedIn = event as AqwEvent.ItemsTurnedIn
        assertEquals(2, itemsTurnedIn.deductions.size)
        assertEquals(2578, itemsTurnedIn.deductions[0].first)
        assertEquals(5, itemsTurnedIn.deductions[0].second)
        assertEquals(2579, itemsTurnedIn.deductions[1].first)
        assertEquals(1, itemsTurnedIn.deductions[1].second)
    }

    @Test
    fun testTempAndInventoryItemRemoval() {
        val playerState = AqwPlayerState()
        // Add regular inventory item
        val invItem = AqwItem(itemId = 2578, charItemId = 101, name = "Inv Item", qty = 5)
        playerState.inventory.add(invItem)

        // Add temp inventory item
        val tempItem =
            AqwItem(itemId = 2579, charItemId = 0, name = "Temp Item", qty = 3, isTemp = true)
        playerState.tempInventory.add(tempItem)

        // Deduct 5 from invItem (5 - 5 = 0 -> removed)
        val invDeductionQty = 5
        val targetInv = playerState.getItemInventoryById(2578)
        if (targetInv != null) {
            if (targetInv.qty - invDeductionQty <= 0) {
                playerState.inventory.removeAll { it.itemId == 2578 || (it.charItemId != 0 && it.charItemId == 2578) }
            } else {
                targetInv.qty -= invDeductionQty
            }
        }
        assertNull(playerState.getItemInventoryById(2578))

        // Deduct 1 from tempItem (3 - 1 = 2 -> qty becomes 2)
        val tempDeductionQty1 = 1
        val targetTemp1 = playerState.getItemTempById(2579)
        if (targetTemp1 != null) {
            if (targetTemp1.qty - tempDeductionQty1 <= 0) {
                playerState.tempInventory.removeAll { it.itemId == 2579 }
            } else {
                targetTemp1.qty -= tempDeductionQty1
            }
        }
        assertEquals(2, playerState.getItemTempById(2579)?.qty)

        // Deduct remaining 2 from tempItem (2 - 2 = 0 -> removed)
        val tempDeductionQty2 = 2
        val targetTemp2 = playerState.getItemTempById(2579)
        if (targetTemp2 != null) {
            if (targetTemp2.qty - tempDeductionQty2 <= 0) {
                playerState.tempInventory.removeAll { it.itemId == 2579 }
            } else {
                targetTemp2.qty -= tempDeductionQty2
            }
        }
        assertNull(playerState.getItemTempById(2579))
    }

    @Test
    fun testRegisteredQuestPaused() {
        val session = AqwSession()
        session.isPaused = { true }
        assertTrue(session.quest.isPaused())
        session.playerState.registeredAutoQuestIds.add(1234)
        assertTrue(session.playerState.registeredAutoQuestIds.contains(1234))
    }
}
