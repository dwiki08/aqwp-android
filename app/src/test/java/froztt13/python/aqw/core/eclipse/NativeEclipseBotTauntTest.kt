package froztt13.python.aqw.core.eclipse

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class NativeEclipseBotTauntTest {

    @Before
    fun setup() {
        NativeEclipseBot.sunsetKnightCount.set(0)
        NativeEclipseBot.moonHazeCount.set(0)
        NativeEclipseBot.lightGatherCount.set(0)
        NativeEclipseBot.sunConvergeCount.set(0)
        NativeEclipseBot.moonConvergeCount.set(0)
        NativeEclipseBot.lastSunsetKnightTime = 0L
        NativeEclipseBot.lastMoonHazeTime = 0L
        NativeEclipseBot.lastLightGatherTime = 0L
        NativeEclipseBot.lastSunConvergeTime = 0L
        NativeEclipseBot.lastMoonConvergeTime = 0L
        NativeEclipseBot.pendingTauntTargets.clear()
    }

    @Test
    fun testSunWarmthAlternatesBetweenSlot1AndSlot2() {
        // Wave 1: Detected by slot 1
        NativeEclipseBot.onSunWarmthDetected("slot1", delayMs = 0)
        assertEquals(1, NativeEclipseBot.sunsetKnightCount.get())

        // Concurrent/immediate call from slot 2 must be debounced (ignored)
        NativeEclipseBot.onSunWarmthDetected("slot2", delayMs = 0)
        assertEquals(1, NativeEclipseBot.sunsetKnightCount.get())

        // Wave 2: Next wave after debounce period (simulated by resetting timestamp)
        NativeEclipseBot.lastSunsetKnightTime = 0L
        NativeEclipseBot.onSunWarmthDetected("slot2", delayMs = 0)
        assertEquals(2, NativeEclipseBot.sunsetKnightCount.get())

        // Wave 3: Next wave alternates back to slot 1
        NativeEclipseBot.lastSunsetKnightTime = 0L
        NativeEclipseBot.onSunWarmthDetected("slot1", delayMs = 0)
        assertEquals(3, NativeEclipseBot.sunsetKnightCount.get())

        // Wave 4: Next wave alternates to slot 2
        NativeEclipseBot.lastSunsetKnightTime = 0L
        NativeEclipseBot.onSunWarmthDetected("slot4", delayMs = 0)
        assertEquals(4, NativeEclipseBot.sunsetKnightCount.get())
    }

    @Test
    fun testMoonGazeAlternatesBetweenSlot3AndSlot4() {
        // Wave 1: Detected by slot 3
        NativeEclipseBot.onMoonGazeDetected("slot3", delayMs = 0)
        assertEquals(1, NativeEclipseBot.moonHazeCount.get())

        // Immediate duplicate call from another slot must be debounced
        NativeEclipseBot.onMoonGazeDetected("slot4", delayMs = 0)
        assertEquals(1, NativeEclipseBot.moonHazeCount.get())

        // Wave 2: Next wave after debounce period -> slot 4
        NativeEclipseBot.lastMoonHazeTime = 0L
        NativeEclipseBot.onMoonGazeDetected("slot4", delayMs = 0)
        assertEquals(2, NativeEclipseBot.moonHazeCount.get())

        // Wave 3: Next wave alternates back to slot 3
        NativeEclipseBot.lastMoonHazeTime = 0L
        NativeEclipseBot.onMoonGazeDetected("slot1", delayMs = 0)
        assertEquals(3, NativeEclipseBot.moonHazeCount.get())

        // Wave 4: Next wave alternates to slot 4
        NativeEclipseBot.lastMoonHazeTime = 0L
        NativeEclipseBot.onMoonGazeDetected("slot2", delayMs = 0)
        assertEquals(4, NativeEclipseBot.moonHazeCount.get())
    }

    @Test
    fun testLightGatherAlternatesBetweenSlot2Slot3AndSlot4() {
        // Wave 1: Detected by slot 1 -> assigned to slot 2
        NativeEclipseBot.onLightGatherDetected("slot1", delayMs = 0)
        assertEquals(1, NativeEclipseBot.lightGatherCount.get())

        // Immediate duplicate call from another slot must be debounced
        NativeEclipseBot.onLightGatherDetected("slot2", delayMs = 0)
        assertEquals(1, NativeEclipseBot.lightGatherCount.get())

        // Wave 2: Next wave after debounce period -> slot 3
        NativeEclipseBot.lastLightGatherTime = 0L
        NativeEclipseBot.onLightGatherDetected("slot3", delayMs = 0)
        assertEquals(2, NativeEclipseBot.lightGatherCount.get())

        // Wave 3: Alternates to slot 4
        NativeEclipseBot.lastLightGatherTime = 0L
        NativeEclipseBot.onLightGatherDetected("slot4", delayMs = 0)
        assertEquals(3, NativeEclipseBot.lightGatherCount.get())

        // Wave 4: Alternates back to slot 2
        NativeEclipseBot.lastLightGatherTime = 0L
        NativeEclipseBot.onLightGatherDetected("slot1", delayMs = 0)
        assertEquals(4, NativeEclipseBot.lightGatherCount.get())
    }

    @Test
    fun testTauntInfoReflectsCurrentAndNextTaunters() {
        NativeEclipseBot.refreshTauntInfo()
        val initialInfo = NativeEclipseBot.tauntInfo.value
        assertEquals("slot1", initialInfo.sunSide.nextSlot)
        assertEquals("slot3", initialInfo.moonSide.nextSlot)
        assertEquals("slot2", initialInfo.lightGather.nextSlot)
        assertEquals(null, initialInfo.sunSide.pendingSlot)

        // Simulate Sun's warmth wave 1 -> Next Sun taunter becomes slot2
        NativeEclipseBot.onSunWarmthDetected("slot1", delayMs = 0)
        val sunInfo = NativeEclipseBot.tauntInfo.value
        assertEquals("slot2", sunInfo.sunSide.nextSlot)
        assertEquals(1, sunInfo.sunSide.waveCount)

        // Simulate pending taunt
        NativeEclipseBot.pendingTauntTargets["slot1"] = "Sunset Knight"
        NativeEclipseBot.refreshTauntInfo()
        val pendingInfo = NativeEclipseBot.tauntInfo.value
        assertEquals("slot1", pendingInfo.sunSide.pendingSlot)

        // Simulate Moon haze wave 1 -> Next Moon taunter becomes slot4
        NativeEclipseBot.onMoonGazeDetected("slot3", delayMs = 0)
        val moonInfo = NativeEclipseBot.tauntInfo.value
        assertEquals("slot4", moonInfo.moonSide.nextSlot)
        assertEquals(1, moonInfo.moonSide.waveCount)
    }

    @Test
    fun testSunConvergeAlternatesBetweenSlot1AndSlot2() {
        // Wave 1: Detected by slot 1 -> assigned to slot 1
        NativeEclipseBot.onSunConvergeDetected("slot1", delayMs = 0)
        assertEquals(1, NativeEclipseBot.sunConvergeCount.get())
        assertEquals("slot2", NativeEclipseBot.tauntInfo.value.sunConverge.nextSlot)

        // Immediate duplicate call must be debounced
        NativeEclipseBot.onSunConvergeDetected("slot2", delayMs = 0)
        assertEquals(1, NativeEclipseBot.sunConvergeCount.get())

        // Wave 2: Next wave -> slot 2
        NativeEclipseBot.lastSunConvergeTime = 0L
        NativeEclipseBot.onSunConvergeDetected("slot2", delayMs = 0)
        assertEquals(2, NativeEclipseBot.sunConvergeCount.get())
        assertEquals("slot1", NativeEclipseBot.tauntInfo.value.sunConverge.nextSlot)

        // Wave 3: Alternates back to slot 1
        NativeEclipseBot.lastSunConvergeTime = 0L
        NativeEclipseBot.onSunConvergeDetected("slot1", delayMs = 0)
        assertEquals(3, NativeEclipseBot.sunConvergeCount.get())
        assertEquals("slot2", NativeEclipseBot.tauntInfo.value.sunConverge.nextSlot)
    }

    @Test
    fun testMoonConvergeAlternatesBetweenSlot3AndSlot4() {
        // Wave 1: Detected by slot 3 -> assigned to slot 3
        NativeEclipseBot.onMoonConvergeDetected("slot3", delayMs = 0)
        assertEquals(1, NativeEclipseBot.moonConvergeCount.get())
        assertEquals("slot4", NativeEclipseBot.tauntInfo.value.moonConverge.nextSlot)

        // Immediate duplicate call must be debounced
        NativeEclipseBot.onMoonConvergeDetected("slot4", delayMs = 0)
        assertEquals(1, NativeEclipseBot.moonConvergeCount.get())

        // Wave 2: Next wave -> slot 4
        NativeEclipseBot.lastMoonConvergeTime = 0L
        NativeEclipseBot.onMoonConvergeDetected("slot4", delayMs = 0)
        assertEquals(2, NativeEclipseBot.moonConvergeCount.get())
        assertEquals("slot3", NativeEclipseBot.tauntInfo.value.moonConverge.nextSlot)

        // Wave 3: Alternates back to slot 3
        NativeEclipseBot.lastMoonConvergeTime = 0L
        NativeEclipseBot.onMoonConvergeDetected("slot3", delayMs = 0)
        assertEquals(3, NativeEclipseBot.moonConvergeCount.get())
        assertEquals("slot4", NativeEclipseBot.tauntInfo.value.moonConverge.nextSlot)
    }

    @Test
    fun testLightGatherSlot4OnlyMode() {
        val config = froztt13.python.aqw.data.EclipseConfig(
            lightGatherMode = "slot4_only",
            slots = mapOf(
                "slot1" to froztt13.python.aqw.data.SlotConfig(username = "u1", password = "p1"),
                "slot2" to froztt13.python.aqw.data.SlotConfig(username = "u2", password = "p2"),
                "slot3" to froztt13.python.aqw.data.SlotConfig(username = "u3", password = "p3"),
                "slot4" to froztt13.python.aqw.data.SlotConfig(username = "u4", password = "p4")
            )
        ).enforceFixedRoles()

        // enforceFixedRoles should make slot4 the only lightGatherTaunter
        org.junit.Assert.assertFalse(config.slots["slot1"]!!.lightGatherTaunter)
        org.junit.Assert.assertFalse(config.slots["slot2"]!!.lightGatherTaunter)
        org.junit.Assert.assertFalse(config.slots["slot3"]!!.lightGatherTaunter)
        org.junit.Assert.assertTrue(config.slots["slot4"]!!.lightGatherTaunter)

        // Test start() configuration
        val (started, err) = NativeEclipseBot.start(config)
        org.junit.Assert.assertTrue(err ?: "", started)

        try {
            // Next taunter should be slot4 initially
            assertEquals("slot4", NativeEclipseBot.tauntInfo.value.lightGather.nextSlot)

            // Wave 1
            NativeEclipseBot.onLightGatherDetected("slot1", delayMs = 0)
            assertEquals(1, NativeEclipseBot.lightGatherCount.get())
            assertEquals("slot4", NativeEclipseBot.tauntInfo.value.lightGather.nextSlot)

            // Wave 2
            NativeEclipseBot.lastLightGatherTime = 0L
            NativeEclipseBot.onLightGatherDetected("slot2", delayMs = 0)
            assertEquals(2, NativeEclipseBot.lightGatherCount.get())
            assertEquals("slot4", NativeEclipseBot.tauntInfo.value.lightGather.nextSlot)
        } finally {
            NativeEclipseBot.stop()
        }
    }
}
