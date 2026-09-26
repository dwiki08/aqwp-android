package froztt13.python.aqw.data.engine

import froztt13.python.aqw.domain.model.AqwSkill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AqwPacketParserSarsaTest {

    @Test
    fun testParseSkillIndex() {
        assertEquals(0, AqwPacketParser.parseSkillIndex("aa"))
        assertEquals(0, AqwPacketParser.parseSkillIndex("aa>m:1"))
        assertEquals(0, AqwPacketParser.parseSkillIndex("a0"))
        assertEquals(1, AqwPacketParser.parseSkillIndex("a1"))
        assertEquals(1, AqwPacketParser.parseSkillIndex("a1>m:2"))
        assertEquals(2, AqwPacketParser.parseSkillIndex("a2"))
        assertEquals(2, AqwPacketParser.parseSkillIndex("s2"))
        assertEquals(3, AqwPacketParser.parseSkillIndex("a3"))
        assertEquals(4, AqwPacketParser.parseSkillIndex("a4"))
        assertEquals(5, AqwPacketParser.parseSkillIndex("a5"))
        assertEquals(5, AqwPacketParser.parseSkillIndex("i1>m:1%Scroll of Enrage"))
        assertEquals(5, AqwPacketParser.parseSkillIndex("i1"))
        assertEquals(5, AqwPacketParser.parseSkillIndex("s5"))
    }

    @Test
    fun testCtPacketWithSarsa_parsedCorrectly() {
        val rawJsonPacket = """
            {"t":"xt","b":{"r":-1,"o":{"cmd":"ct","sarsa":[{"cInf":"p:101","a":[{"actRef":"a1","type":"hit","tInf":"m:1","hp":1250},{"actRef":"a2","type":"crit","tInf":"m:2","hp":3400}]},{"cInf":"p:202","a":[{"actRef":"a1","type":"hit","tInf":"m:1","hp":500}]}]}}}
        """.trimIndent()

        val event = AqwPacketParser.parse(rawJsonPacket, "myuser")
        assertTrue(event is AqwEvent.CombatTick)
        val combatTick = event as AqwEvent.CombatTick

        assertEquals(3, combatTick.sarsa.size)

        val first = combatTick.sarsa[0]
        assertEquals("p:101", first.cInf)
        assertEquals("a1", first.actRef)
        assertEquals("hit", first.type)
        assertEquals("m:1", first.tInf)
        assertEquals(1250, first.hp)

        val second = combatTick.sarsa[1]
        assertEquals("p:101", second.cInf)
        assertEquals("a2", second.actRef)
        assertEquals("crit", second.type)
        assertEquals("m:2", second.tInf)
        assertEquals(3400, second.hp)

        val third = combatTick.sarsa[2]
        assertEquals("p:202", third.cInf)
        assertEquals("a1", third.actRef)
    }

    @Test
    fun testSessionUpdatesCooldownOnServerSarsa() {
        val session = AqwSession()
        session.playerState.username = "Hero"
        session.playerState.roomUserId = 101

        val skill1 = AqwSkill(
            index = 1,
            name = "Skill 1",
            cdMillis = 4000.0,
            cdSeconds = 4.0
        )
        session.playerState.skills.add(skill1)

        assertTrue(skill1.isReady())
        assertEquals(0.0, session.getCooldowns()[1] ?: 0.0, 0.01)

        // Simulate server sending CT packet with sarsa confirming cast for roomUserId 101
        val ctPacket = """
            {"t":"xt","b":{"r":-1,"o":{"cmd":"ct","sarsa":[{"cInf":"p:101","a":[{"actRef":"a1","type":"hit","tInf":"m:1","hp":999}]}]}}}
        """.trimIndent()

        val event = AqwPacketParser.parse(ctPacket, "Hero")
        assertTrue(event is AqwEvent.CombatTick)

        // Process event in session (simulate handleEvent)
        for (sarsa in (event as AqwEvent.CombatTick).sarsa) {
            val isMe =
                (session.playerState.roomUserId > 0 && sarsa.cInf == "p:${session.playerState.roomUserId}") ||
                        sarsa.cInf.equals("p:${session.playerState.username}", ignoreCase = true)
            if (isMe) {
                val skillIdx = AqwPacketParser.parseSkillIndex(sarsa.actRef)
                if (skillIdx != null) {
                    session.combat.updateNextUse(skillIdx)
                }
            }
        }

        // Skill 1 should now be on cooldown
        assertFalse(skill1.isReady())
        val cdRemaining = session.getCooldowns()[1] ?: 0.0
        assertTrue("Cooldown should be > 0, was $cdRemaining", cdRemaining > 3000.0)
    }
}
