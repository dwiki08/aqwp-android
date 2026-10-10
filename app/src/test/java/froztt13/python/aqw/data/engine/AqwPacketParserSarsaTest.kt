package froztt13.python.aqw.data.engine

import froztt13.python.aqw.domain.model.AqwSkill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater

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

    @Test
    fun testDecodeBase64Packet_withoutZPrefix_returnsOriginal() {
        val plainText = "%xt%zm%cmd%1%test%"
        val result = AqwPacketParser.decodeBase64Packet(plainText)
        assertEquals(plainText, result)
    }

    @Test
    fun testDecodeBase64Packet_withZPrefix_decodesSuccessfully() {
        // Base64 encoding of "%xt%zm%cmd%1%test%" is "JXh0JXptJWNtZCUxJXRlc3Ql"
        val rawZPacket = "ZJXh0JXptJWNtZCUxJXRlc3Ql"
        val decoded = AqwPacketParser.decodeBase64Packet(rawZPacket)
        assertEquals("%xt%zm%cmd%1%test%", decoded)
    }

    @Test
    fun testParse_withZPrefixedBase64JsonPacket_parsesEventCorrectly() {
        val rawJson = """{"t":"xt","b":{"r":-1,"o":{"cmd":"ct","sarsa":[]}}}"""
        val base64Payload = Base64.getEncoder().encodeToString(rawJson.toByteArray(Charsets.UTF_8))
        val zPacket = "Z$base64Payload"

        val event = AqwPacketParser.parse(zPacket, "Hero")
        assertTrue(event is AqwEvent.CombatTick)
    }

    @Test
    fun testDecodeBase64Packet_withZlibCompression_decompressesCorrectly() {
        val jsonPayload =
            """{"t":"xt","b":{"r":-1,"o":{"cmd":"moveToArea","areaId":42,"areaName":"battleon","mapName":"battleon-1","sType":"normal","monsters":[]}}}"""
        val rawBytes = jsonPayload.toByteArray(Charsets.UTF_8)

        // Compress bytes using Deflater (Zlib)
        val deflater = Deflater()
        deflater.setInput(rawBytes)
        deflater.finish()
        val baos = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!deflater.finished()) {
            val count = deflater.deflate(buf)
            baos.write(buf, 0, count)
        }
        deflater.end()

        val compressedBase64 = Base64.getEncoder().encodeToString(baos.toByteArray())
        val zPacket = "Z$compressedBase64"

        // Ensure base64 payload starts with expected zlib header signature 'eJ'
        assertTrue(
            "Compressed Base64 should start with 'eJ', got: $compressedBase64",
            compressedBase64.startsWith("eJ")
        )

        val decompressed = AqwPacketParser.decodeBase64Packet(zPacket)
        assertEquals(jsonPayload, decompressed)

        val event = AqwPacketParser.parse(zPacket, "Hero")
        assertTrue(event is AqwEvent.MoveToArea)
        val moveEvent = event as AqwEvent.MoveToArea
        assertEquals(42, moveEvent.areaId)
        assertEquals("battleon", moveEvent.areaName)
    }

    @Test
    fun testDecodeBase64Packet_withRealGzipUserPacket_decompressesSuccessfully() {
        val samplePacket =
            "ZSInFlU1v4jAQhv8KmnPYTQKBxbe2wJYVqRCw2sOKwywMEJHYkWO+VPHfKydpCBAiukLtKR6P5tXj8evJKyhgsFNgwD9gryCBVS0DhF5PgxkwCMSGxuJBEoIBKAl7M2CNhmMl0QsGBAzIp4C4Qr/aMlstMMDjarwPdcoCA6LOTkkEBnqtpIvheV2W6Ho+pckHOUW+kGJD35XY8ipGSqJfbfxCbpvfou0cDAgEDzAE9vcVXMFdDHttYJahA72qt8xYuCsTSWnrkw5pBmyOfkQx6HA00sc+GHkN+w4atZyGdapRu1WjfgcN5w4ajev9qN+q0cxp2Kcazq0aPzIN2/oIyCT2yozmsVeiIU51SSfnv/zxPK76tCEfWC1pWN/jK1zokl4QusgxNazgqVn1XqUXhNl+zshpSWrZSMlHWuJGZ7bor0AfsBTIKgXS0l30p4IXMp2lcljHwlvInpYoIji9wRyVXUCVvW67kOyn8Cm4BpbV1qR1C96L4ARn3ihp2h/0Vx5fdJGkR2NJdE6o9ypiXmlTpDy+L8IMBI8UyeqF1lXeiQFr8SiRT5exDdcilZqTlKSUAAMom5xhEsUTzXSazVjxd0SSXxTlXkGHK5lL1gD1BB+FuOXJSB4pVBTPx2NrLDPp1PMAWM1sJoE7yCdc3J3k4jhOqx0w/dnHH5yvsqcXLcX2yRe4AqbkOt14Jj9I4vQ95npxdXyfc8eg73BFoMfcEfR0LPQ3qSe27cFIH6109n8+QO3i8V8CmCUA5ocB7NK/zucDOF8N0PhqDzQvRm1hB0pbcBuC/Y7gNMr/tUUM9RKE+v/cwuRwOLwByI8xAA=="

        val decompressed = AqwPacketParser.decodeBase64Packet(samplePacket)
        assertTrue(
            "Decompressed packet should not equal original encoded packet",
            decompressed != samplePacket
        )
        assertTrue(
            "Decompressed packet should contain JSON structure or xt command",
            decompressed.contains("{") || decompressed.contains("%xt%")
        )
    }
}
