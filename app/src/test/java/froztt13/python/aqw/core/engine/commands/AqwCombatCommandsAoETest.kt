package froztt13.python.aqw.core.engine.commands

import froztt13.python.aqw.core.model.AqwMonster
import froztt13.python.aqw.core.model.AqwPlayerState
import froztt13.python.aqw.core.model.AqwSkill
import froztt13.python.aqw.core.network.AqwSocketClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AqwCombatCommandsAoETest {

    private class FakeSocketClient : AqwSocketClient() {
        val sentPackets = mutableListOf<String>()

        override suspend fun send(packet: String): Boolean {
            sentPackets.add(packet)
            return true
        }
    }

    @Test
    fun testUseSkill_hostileAoE_prioritizesTargetMonMapIdAndSortsOthers() = runBlocking {
        val client = FakeSocketClient()
        val playerState = AqwPlayerState(
            cell = "r2",
            currentHp = 1000,
            maxHp = 1000,
            mp = 100,
            maxMp = 100
        )
        // Set up skills: skill 1 is AoE with tgtMax = 3
        playerState.skills.add(
            AqwSkill(
                index = 1,
                name = "Fireball",
                tgt = "h",
                tgtMax = 3,
                mpCost = 10.0,
                cdMillis = 1000.0
            )
        )

        // Monsters:
        // Mon 1: in r2, alive
        // Mon 2: in r2, alive (target)
        // Mon 3: in r2, dead
        // Mon 4: in r3 (different cell), alive
        // Mon 10: in r2, alive
        val monsters = listOf(
            AqwMonster(
                monMapId = "1",
                name = "Goblin A",
                currentHp = 100,
                maxHp = 100,
                isAlive = true,
                frame = "r2"
            ),
            AqwMonster(
                monMapId = "2",
                name = "Goblin B",
                currentHp = 100,
                maxHp = 100,
                isAlive = true,
                frame = "r2"
            ),
            AqwMonster(
                monMapId = "3",
                name = "Goblin C",
                currentHp = 0,
                maxHp = 100,
                isAlive = false,
                frame = "r2"
            ),
            AqwMonster(
                monMapId = "4",
                name = "Goblin D",
                currentHp = 100,
                maxHp = 100,
                isAlive = true,
                frame = "r3"
            ),
            AqwMonster(
                monMapId = "10",
                name = "Goblin Boss",
                currentHp = 500,
                maxHp = 500,
                isAlive = true,
                frame = "r2"
            )
        )

        val combat = AqwCombatCommands(
            client = client,
            playerState = playerState,
            monstersProvider = { monsters }
        )

        val success = combat.useSkill(index = 1, targetMonMapId = "2", reloadDelayMs = 0)
        assertTrue(success)
        assertEquals(1, client.sentPackets.size)
        // Primary is 2, followed by remaining alive in r2 sorted: 1, 10
        assertEquals("%xt%zm%gar%1%0%a1>m:2,a1>m:1,a1>m:10%wvz%", client.sentPackets.first())
        assertEquals("Goblin B", combat.lastTargetMonster)
    }

    @Test
    fun testUseSkill_autoAttackAoE() = runBlocking {
        val client = FakeSocketClient()
        val playerState = AqwPlayerState(
            cell = "Enter",
            currentHp = 1000,
            maxHp = 1000,
            mp = 100,
            maxMp = 100
        )
        // Skill 0: auto attack with tgtMax = 2
        playerState.skills.add(
            AqwSkill(
                index = 0,
                name = "Auto Attack",
                tgt = "h",
                tgtMax = 2,
                mpCost = 0.0,
                cdMillis = 1500.0
            )
        )

        val monsters = listOf(
            AqwMonster(
                monMapId = "5",
                name = "Wolf A",
                currentHp = 50,
                maxHp = 50,
                isAlive = true,
                frame = "Enter"
            ),
            AqwMonster(
                monMapId = "2",
                name = "Wolf B",
                currentHp = 50,
                maxHp = 50,
                isAlive = true,
                frame = "Enter"
            )
        )

        val combat = AqwCombatCommands(
            client = client,
            playerState = playerState,
            monstersProvider = { monsters }
        )

        val success = combat.useSkill(index = 0, targetMonMapId = "5", reloadDelayMs = 0)
        assertTrue(success)
        assertEquals("%xt%zm%gar%1%0%aa>m:5,aa>m:2%wvz%", client.sentPackets.first())
        assertEquals("Wolf A", combat.lastTargetMonster)
    }

    @Test
    fun testUseSkill_withoutTargetMonMapId_picksFirstAliveInCell() = runBlocking {
        val client = FakeSocketClient()
        val playerState = AqwPlayerState(
            cell = "Boss",
            currentHp = 1000,
            maxHp = 1000,
            mp = 100,
            maxMp = 100
        )
        playerState.skills.add(
            AqwSkill(
                index = 2,
                name = "Cleave",
                tgt = "h",
                tgtMax = 2,
                mpCost = 15.0,
                cdMillis = 2000.0
            )
        )

        val monsters = listOf(
            AqwMonster(
                monMapId = "7",
                name = "Minion B",
                currentHp = 200,
                maxHp = 200,
                isAlive = true,
                frame = "Boss"
            ),
            AqwMonster(
                monMapId = "3",
                name = "Minion A",
                currentHp = 200,
                maxHp = 200,
                isAlive = true,
                frame = "Boss"
            )
        )

        val combat = AqwCombatCommands(
            client = client,
            playerState = playerState,
            monstersProvider = { monsters }
        )

        val success = combat.useSkill(index = 2, targetMonMapId = null, reloadDelayMs = 0)
        assertTrue(success)
        // Sorted: 3 is first, followed by 7
        assertEquals("%xt%zm%gar%1%0%a2>m:3,a2>m:7%wvz%", client.sentPackets.first())
        assertEquals("Minion A", combat.lastTargetMonster)
    }

    @Test
    fun testTaunt_updatesNextUseStatic10Seconds_withoutCdr() = runBlocking {
        val client = FakeSocketClient()
        val playerState = AqwPlayerState(
            cell = "Boss",
            currentHp = 1000,
            maxHp = 1000,
            cdReduction = 0.5 // 50% CDR should NOT affect taunt (must remain static 10s)
        )
        val monsters = listOf(
            AqwMonster(
                monMapId = "99",
                name = "Boss Monster",
                currentHp = 10000,
                maxHp = 10000,
                isAlive = true,
                frame = "Boss"
            )
        )
        val combat = AqwCombatCommands(
            client = client,
            playerState = playerState,
            monstersProvider = { monsters }
        )

        // Initially skill 5 can be used
        assertTrue(combat.canUseSkill(5))

        val start = System.currentTimeMillis()
        val sent = combat.taunt("99")
        assertTrue(sent)

        val skill5 = combat.getSkill(5)
        org.junit.Assert.assertNotNull(skill5)
        // Verify cooldown is ~10000 ms from start
        val remaining = skill5!!.nextUseTimestamp - start
        assertTrue(
            "Remaining cooldown should be around 10000 ms, was $remaining",
            remaining in 9800L..10200L
        )
        // canUseSkill(5) must now be false
        org.junit.Assert.assertFalse(combat.canUseSkill(5))
        assertEquals("Boss Monster", combat.lastTargetMonster)
    }

    @Test
    fun testUseBuff_withSelfTgtType_sendsPacketToSelfAndReturnsTrue() = runBlocking {
        val client = FakeSocketClient()
        val playerState = AqwPlayerState(
            cell = "Boss",
            currentHp = 1000,
            maxHp = 1000,
            roomUserId = 42
        )
        playerState.skills.add(
            AqwSkill(
                index = 2,
                name = "Self Shield",
                tgt = "s",
                mpCost = 10.0,
                cdMillis = 1000.0
            )
        )
        val combat = AqwCombatCommands(
            client = client,
            playerState = playerState,
            monstersProvider = { emptyList() }
        )

        val success = combat.useBuff(index = 2, reloadDelayMs = 0)
        assertTrue(success)
        assertEquals(1, client.sentPackets.size)
        assertEquals("%xt%zm%gar%1%0%a2>p:42%wvz%", client.sentPackets.first())
    }

    @Test
    fun testUseBuff_withFriendlyTgtType_sendsPacketToPartyAndReturnsTrue() = runBlocking {
        val client = FakeSocketClient()
        val playerState = AqwPlayerState(
            cell = "Boss",
            currentHp = 1000,
            maxHp = 1000,
            roomUserId = 10,
            roomUserIds = mutableListOf(20, 30)
        )
        playerState.skills.add(
            AqwSkill(
                index = 3,
                name = "Party Heal",
                tgt = "f",
                tgtMax = 3,
                mpCost = 15.0,
                cdMillis = 1000.0
            )
        )
        val combat = AqwCombatCommands(
            client = client,
            playerState = playerState,
            monstersProvider = { emptyList() }
        )

        val success = combat.useBuff(index = 3, reloadDelayMs = 0)
        assertTrue(success)
        assertEquals(1, client.sentPackets.size)
        assertEquals("%xt%zm%gar%1%0%a3>p:10,a3>p:20,a3>p:30%wvz%", client.sentPackets.first())
    }

    @Test
    fun testUseBuff_withHostileTgtType_returnsFalseAndDoesNotSendPacket() = runBlocking {
        val client = FakeSocketClient()
        val playerState = AqwPlayerState(
            cell = "Boss",
            currentHp = 1000,
            maxHp = 1000,
            roomUserId = 10
        )
        playerState.skills.add(
            AqwSkill(
                index = 1,
                name = "Fireball",
                tgt = "h",
                mpCost = 10.0,
                cdMillis = 1000.0
            )
        )
        val combat = AqwCombatCommands(
            client = client,
            playerState = playerState,
            monstersProvider = { emptyList() }
        )

        val success = combat.useBuff(index = 1, reloadDelayMs = 0)
        org.junit.Assert.assertFalse(success)
        assertTrue(client.sentPackets.isEmpty())
    }
}
