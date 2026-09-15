package froztt13.python.aqw.core.engine

import android.util.Log
import froztt13.python.aqw.core.model.AqwFaction
import froztt13.python.aqw.core.model.AqwItem
import froztt13.python.aqw.core.model.AqwMonster
import froztt13.python.aqw.core.model.AqwOtherPlayer
import froztt13.python.aqw.core.model.AqwQuest
import froztt13.python.aqw.core.model.AqwQuestItem
import froztt13.python.aqw.core.model.AqwShop
import froztt13.python.aqw.core.model.AqwSkill
import org.json.JSONObject

sealed interface AqwEvent {
    object PolicyReceived : AqwEvent
    object LoginResponseReceived : AqwEvent
    data class JoinOk(
        val rawXml: String,
        val myRoomUserId: Int = 0,
        val roomUsers: Map<String, Int> = emptyMap()
    ) : AqwEvent

    data class LoggedOut(val rawXml: String) : AqwEvent
    data class UserEnteredRoom(
        val userId: Int,
        val username: String = "",
        val isMe: Boolean = false,
        val rawXml: String = ""
    ) : AqwEvent

    data class UserLeftRoom(val rawXml: String, val userId: Int = 0) : AqwEvent

    data class MoveToArea(
        val areaName: String,
        val areaId: Int,
        val mapName: String,
        val playerCell: String,
        val playerPad: String,
        val monsters: List<AqwMonster>,
        val otherPlayers: List<AqwOtherPlayer> = emptyList(),
        val myRoomUserId: Int = 0,
        val playerHp: Int? = null,
        val playerMaxHp: Int? = null,
        val playerMp: Int? = null
    ) : AqwEvent

    object YouJoinedMap : AqwEvent
    data class InventoryLoaded(
        val items: List<AqwItem>,
        val factions: List<AqwFaction> = emptyList()
    ) : AqwEvent

    data class SkillsLoaded(val skills: List<AqwSkill>) : AqwEvent
    data class StatsUpdated(val cdReduction: Double? = null, val manaCost: Double? = null) :
        AqwEvent

    data class CombatTick(
        val playerHp: Int?,
        val playerMp: Int?,
        val playerInCombat: Boolean? = null,
        val monsterHpMap: Map<String, Int>,
        val animMsgs: List<String> = emptyList(),
        val auras: List<Pair<String, String>> = emptyList(),
        val aurasRemoved: List<Pair<String, String>> = emptyList()
    ) : AqwEvent

    data class PartyInviteReceived(val partyId: Int, val owner: String) : AqwEvent

    data class MonsterStateUpdated(
        val monMapId: String,
        val hp: Int? = null,
        val isAlive: Boolean? = null
    ) : AqwEvent

    data class PlayerStateUpdated(
        val username: String,
        val hp: Int? = null,
        val maxHp: Int? = null,
        val mp: Int? = null,
        val inCombat: Boolean? = null,
        val cell: String? = null,
        val pad: String? = null
    ) : AqwEvent

    data class ItemDropped(
        val itemId: Int,
        val itemName: String,
        val qty: Int,
        val items: List<AqwItem> = emptyList()
    ) : AqwEvent

    data class ItemsAdded(val items: List<AqwItem>) : AqwEvent
    data class ItemsTurnedIn(val deductions: List<Pair<Int, Int>>) : AqwEvent

    data class ShopLoaded(val shop: AqwShop) : AqwEvent
    data class ItemBought(val itemId: Int, val charItemId: Int, val qty: Int) : AqwEvent
    data class ItemSold(
        val charItemId: Int,
        val qtySold: Int,
        val qtyNow: Int,
        val goldAmount: Long
    ) : AqwEvent

    data class GoldExpAdded(val gold: Long, val exp: Long, val factionId: Int, val rep: Int) :
        AqwEvent

    data class QuestDetailsLoaded(val quests: List<AqwQuest>) : AqwEvent
    data class QuestAccepted(val questId: Int, val success: Boolean) : AqwEvent
    data class QuestCompleted(
        val questId: Int,
        val questName: String,
        val success: Boolean,
        val msg: String,
        val factionId: Int,
        val rep: Int
    ) : AqwEvent

    data class FactionAdded(val faction: AqwFaction) : AqwEvent
    object AurasCleared : AqwEvent
    data class ScrollEquipped(val anim: String, val strl: String, val cd: Double, val tgt: String) :
        AqwEvent

    data class UserDatasLoaded(
        val charId: Int? = null,
        val gold: Long? = null,
        val staffList: List<String> = emptyList()
    ) : AqwEvent

    data class SingleUserDataLoaded(
        val username: String,
        val accessLevel: Int,
        val isStaff: Boolean
    ) : AqwEvent

    data class WheelSpun(val dropNames: List<String>) : AqwEvent

    data class PlayerDied(val userId: Int) : AqwEvent
    data class PlayerRespawned(val userId: Int) : AqwEvent
    data class ServerBroadcast(val message: String) : AqwEvent
    data class Warning(val message: String, val isSpamWarning: Boolean = false) : AqwEvent
    data class ExitArea(val username: String) : AqwEvent
    data class ChatMessage(val sender: String, val message: String, val channel: String) : AqwEvent
    data class WhisperMessage(val sender: String, val message: String) : AqwEvent
    object AfkNotice : AqwEvent
    object InvalidSessionNotice : AqwEvent
    data class Unknown(val raw: String) : AqwEvent
}

object AqwPacketParser {

    private const val TAG = "AqwPacketParser"

    fun parse(raw: String, currentUsername: String): AqwEvent {
        val trimmed = raw.trim()

        // 1. XML Messages
        if (trimmed.startsWith("<")) {
            if (trimmed.contains("<cross-domain-policy>")) {
                return AqwEvent.PolicyReceived
            }
            if (trimmed.contains("joinOK")) {
                val userMap = mutableMapOf<String, Int>()
                var myRoomId = 0
                val uMatches = Regex(
                    """<u\s+[^>]*?i=['"](\d+)['"][^>]*>[\s\S]*?<n>(?:<!\[CDATA\[)?(.*?)(?:\]\]>)?<\/n>""",
                    RegexOption.IGNORE_CASE
                ).findAll(trimmed)
                for (match in uMatches) {
                    val id = match.groupValues[1].toIntOrNull() ?: continue
                    val name = match.groupValues[2].trim()
                    userMap[name] = id
                    if (name.equals(currentUsername, ignoreCase = true)) {
                        myRoomId = id
                    }
                }
                return AqwEvent.JoinOk(trimmed, myRoomId, userMap)
            }
            if (trimmed.contains("userGone", ignoreCase = true)) {
                val match =
                    Regex("""<user\s+[^>]*?id=['"](\d+)['"]""", RegexOption.IGNORE_CASE).find(
                        trimmed
                    )
                val userId = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                return AqwEvent.UserLeftRoom(trimmed, userId)
            }
            if (trimmed.contains("uER", ignoreCase = true)) {
                val idMatch =
                    Regex("""<u\s+[^>]*?i=['"](\d+)['"]""", RegexOption.IGNORE_CASE).find(trimmed)
                val userId = idMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                val nMatch = Regex(
                    """<n>(?:<!\[CDATA\[)?(.*?)(?:\]\]>)?<\/n>""",
                    RegexOption.IGNORE_CASE
                ).find(trimmed)
                val userName = nMatch?.groupValues?.getOrNull(1)?.trim() ?: ""
                val isMe =
                    userName.isNotEmpty() && userName.equals(currentUsername, ignoreCase = true)
                return AqwEvent.UserEnteredRoom(userId, userName, isMe, trimmed)
            }
            if (trimmed.contains("logout")) {
                return AqwEvent.LoggedOut(trimmed)
            }
            return AqwEvent.Unknown(trimmed)
        }

        // 2. Delimited XT Messages
        if (trimmed.startsWith("%") && trimmed.endsWith("%")) {
            val parts = trimmed.split("%")
            if (parts.size > 2) {
                val cmdType = parts[2]
                when {
                    cmdType.equals(
                        "loginResponse",
                        ignoreCase = true
                    ) -> return AqwEvent.LoginResponseReceived

                    cmdType.equals("server", ignoreCase = true) -> {
                        val msg = if (parts.size > 4) parts[4] else ""
                        return AqwEvent.ServerBroadcast(msg)
                    }

                    cmdType.equals("warning", ignoreCase = true) -> {
                        val msg = if (parts.size > 4) parts[4] else ""
                        val isSpam = msg.contains("spamming the server", ignoreCase = true) ||
                                msg.contains("Please slow down", ignoreCase = true)
                        return AqwEvent.Warning(msg, isSpam)
                    }

                    cmdType.equals("exitArea", ignoreCase = true) -> {
                        val username = if (parts.size > 5) parts[5] else ""
                        return AqwEvent.ExitArea(username)
                    }

                    cmdType.equals("uotls", ignoreCase = true) -> {
                        val username = if (parts.size > 4) parts[4] else ""
                        val movement = if (parts.size > 5) parts[5] else ""
                        var cell: String? = null
                        var pad: String? = null
                        movement.split(",").forEach { m ->
                            val kv = m.split(":")
                            if (kv.size == 2) {
                                if (kv[0] == "strFrame") cell = kv[1]
                                else if (kv[0] == "strPad") pad = kv[1]
                            }
                        }
                        return AqwEvent.PlayerStateUpdated(
                            username = username,
                            cell = cell,
                            pad = pad
                        )
                    }

                    cmdType.equals("chatm", ignoreCase = true) -> {
                        val rawText = if (parts.size > 4) parts[4] else ""
                        val sender = if (parts.size > 5) parts[5] else ""
                        val channel = when {
                            rawText.startsWith("guild~") -> "guild"
                            rawText.startsWith("party~") -> "party"
                            else -> "zone"
                        }
                        val cleanedText = rawText.removePrefix("zone~").removePrefix("guild~")
                            .removePrefix("party~")
                        return AqwEvent.ChatMessage(sender, cleanedText, channel)
                    }

                    cmdType.equals("whisper", ignoreCase = true) -> {
                        val text = if (parts.size > 4) parts[4] else ""
                        val sender = if (parts.size > 5) parts[5] else ""
                        return AqwEvent.WhisperMessage(sender, text)
                    }

                    cmdType.equals("resTimed", ignoreCase = true) -> {

                    }
                }
            }
            if (trimmed.contains("You joined", ignoreCase = true)) {
                return AqwEvent.YouJoinedMap
            }
            if (trimmed.contains("Your status is now Away From Keyboard", ignoreCase = true)) {
                return AqwEvent.AfkNotice
            }
            if (trimmed.contains("invalid session", ignoreCase = true)) {
                return AqwEvent.InvalidSessionNotice
            }
            return AqwEvent.Unknown(trimmed)
        }

        // 3. JSON XT Messages
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return try {
                parseJsonMessage(trimmed, currentUsername)
            } catch (_: Exception) {
                AqwEvent.Unknown(trimmed)
            }
        }

        return AqwEvent.Unknown(trimmed)
    }

    private fun parseJsonMessage(jsonStr: String, currentUsername: String): AqwEvent {
        val root = JSONObject(jsonStr)
        val data = root.optJSONObject("b")?.optJSONObject("o") ?: return AqwEvent.Unknown(jsonStr)
        val cmd = data.optString("cmd", "")

//        Log.i(TAG, "$cmd: $data")

        return when (cmd) {
            "moveToArea" -> {
                val areaName = data.optString("areaName", "")
                val areaId = data.optInt("areaId", 0)
                val mapName = data.optString("strMapName", "")

                var pCell = "Enter"
                var pPad = "Spawn"
                var myRoomId = 0
                var pHp: Int? = null
                var pMaxHp: Int? = null
                var pMp: Int? = null
                val otherPlayersList = mutableListOf<AqwOtherPlayer>()
                val uoBranch = data.optJSONArray("uoBranch")
                if (uoBranch != null) {
                    for (i in 0 until uoBranch.length()) {
                        val uObj = uoBranch.optJSONObject(i) ?: continue
                        val uName = uObj.optString("uoName", "")
                        val frame = uObj.optString("strFrame", "Enter")
                        val pad = uObj.optString("strPad", "Spawn")
                        val hp = if (uObj.has("intHP")) uObj.optInt("intHP") else null
                        val maxHp = if (uObj.has("intHPMax")) uObj.optInt("intHPMax") else hp
                        val mp = if (uObj.has("intMP")) uObj.optInt("intMP") else null
                        val state = if (uObj.has("intState")) uObj.optInt("intState") else 1
                        val entId = uObj.optInt("entID", 0)

                        if (uName.equals(currentUsername, ignoreCase = true)) {
                            pCell = frame
                            pPad = pad
                            if (entId > 0) myRoomId = entId
                            pHp = hp
                            pMaxHp = maxHp
                            pMp = mp
                        } else {
                            otherPlayersList.add(
                                AqwOtherPlayer(
                                    username = uName,
                                    userId = uObj.optInt("ID", 0),
                                    roomUserId = entId,
                                    cell = frame,
                                    pad = pad,
                                    hp = hp ?: 100,
                                    maxHp = maxHp ?: 100,
                                    mp = mp ?: 100,
                                    isDead = state <= 0
                                )
                            )
                        }
                    }
                }

                val monstersList = mutableListOf<AqwMonster>()
                val monBranch = data.optJSONArray("monBranch")
                val monDef = data.optJSONArray("mondef")
                val monMap = data.optJSONArray("monmap")

                val namesMap = mutableMapOf<String, String>()
                if (monDef != null) {
                    for (i in 0 until monDef.length()) {
                        val dObj = monDef.optJSONObject(i) ?: continue
                        val mId = dObj.optString("MonID", "")
                        val mName = dObj.optString("strMonName", "")
                        if (mId.isNotEmpty()) namesMap[mId] = mName
                    }
                }

                val framesMap = mutableMapOf<String, String>()
                if (monMap != null) {
                    for (i in 0 until monMap.length()) {
                        val mObj = monMap.optJSONObject(i) ?: continue
                        val mMapId = mObj.optString("MonMapID", "")
                        val mFrame = mObj.optString("strFrame", "")
                        if (mMapId.isNotEmpty()) framesMap[mMapId] = mFrame
                    }
                }

                if (monBranch != null) {
                    for (i in 0 until monBranch.length()) {
                        val bObj = monBranch.optJSONObject(i) ?: continue
                        val monMapId = bObj.optString("MonMapID", "")
                        val monId = bObj.optString("MonID", "")
                        val hp = bObj.optInt("intHP", 0)
                        val maxHp = bObj.optInt("intHPMax", hp)
                        val isAlive = bObj.optInt("intState", 1) > 0

                        monstersList.add(
                            AqwMonster(
                                monMapId = monMapId,
                                monId = monId,
                                name = namesMap[monId] ?: "Monster $monMapId",
                                currentHp = hp,
                                maxHp = maxHp,
                                isAlive = isAlive,
                                frame = framesMap[monMapId] ?: ""
                            )
                        )
                    }
                }

                AqwEvent.MoveToArea(
                    areaName = areaName,
                    areaId = areaId,
                    mapName = mapName,
                    playerCell = pCell,
                    playerPad = pPad,
                    monsters = monstersList,
                    otherPlayers = otherPlayersList,
                    myRoomUserId = myRoomId,
                    playerHp = pHp,
                    playerMaxHp = pMaxHp,
                    playerMp = pMp
                )
            }

            "loadInventoryBig" -> {
                val itemsList = mutableListOf<AqwItem>()
                val itemsArr = data.optJSONArray("items")
                if (itemsArr != null) {
                    for (i in 0 until itemsArr.length()) {
                        val itObj = itemsArr.optJSONObject(i) ?: continue
                        itemsList.add(
                            AqwItem(
                                itemId = itObj.optInt("ItemID", 0),
                                charItemId = itObj.optInt("CharItemID", 0),
                                name = itObj.optString("sName", ""),
                                qty = itObj.optInt("iQty", 1),
                                maxQty = itObj.optInt("iStk", 1),
                                isCoins = itObj.optInt("bCoins", 0) == 1,
                                isTemp = itObj.optInt("bTemp", 0) == 1,
                                sMeta = itObj.optString("sMeta", "0"),
                                sType = itObj.optString("sType", ""),
                                isEquipped = itObj.optInt("bEquip", 0) == 1
                            )
                        )
                    }
                }
                val factionsList = mutableListOf<AqwFaction>()
                val factionsArr = data.optJSONArray("factions")
                if (factionsArr != null) {
                    for (i in 0 until factionsArr.length()) {
                        val fObj = factionsArr.optJSONObject(i) ?: continue
                        factionsList.add(
                            AqwFaction(
                                factionId = fObj.optInt("FactionID", 0),
                                charFactionId = fObj.optString("CharFactionID", ""),
                                name = fObj.optString("sName", ""),
                                rep = fObj.optInt("iRep", 0)
                            )
                        )
                    }
                }
                AqwEvent.InventoryLoaded(itemsList, factionsList)
            }

            "sAct" -> {
                val skillsList = mutableListOf<AqwSkill>()
                val activeArr = data.optJSONObject("actions")?.optJSONArray("active")
                if (activeArr != null) {
                    for (i in 0 until activeArr.length()) {
                        val sObj = activeArr.optJSONObject(i) ?: continue
                        val rawCd = sObj.optDouble("cd", 2000.0)
                        val cdMillis = if (rawCd in 0.1..50.0) rawCd * 1000.0 else rawCd
                        val mp = sObj.optDouble("mp", 0.0)
                        val tgt = sObj.optString("tgt", "h")
                        val tgtMax = sObj.optInt("tgtMax", 1)
                        skillsList.add(
                            AqwSkill(
                                id = sObj.optString("id", sObj.optString("ref", "")),
                                index = i,
                                name = sObj.optString(
                                    "nam",
                                    if (i == 0) "Auto Attack" else "Skill $i"
                                ),
                                anim = sObj.optString("anim", ""),
                                strl = sObj.optString("strl", ""),
                                cdSeconds = cdMillis / 1000.0,
                                cdMillis = cdMillis,
                                mpCost = mp,
                                tgt = tgt,
                                tgtMax = tgtMax
                            )
                        )
                    }
                }
                AqwEvent.SkillsLoaded(skillsList)
            }

            "stu" -> {
                val sta = data.optJSONObject("sta")
                val cdRed = if (sta != null && sta.has("\$tha")) sta.optDouble("\$tha") else null
                val manaCost = if (sta != null && sta.has("\$cmc")) sta.optDouble("\$cmc") else null
                AqwEvent.StatsUpdated(cdRed, manaCost)
            }

            "ct" -> {
                var pCurHp: Int? = null
                var pCurMp: Int? = null
                var pInCombat: Boolean? = null

                // Player stats
                val pObj = data.optJSONObject("p")
                if (pObj != null) {
                    val pMe = pObj.optJSONObject(currentUsername)
                    if (pMe != null) {
                        if (pMe.has("intHP")) pCurHp = pMe.optInt("intHP")
                        if (pMe.has("intMP")) pCurMp = pMe.optInt("intMP")
                        if (pMe.has("intState")) pInCombat = pMe.optInt("intState") == 2
                    }
                }

                // Monster stats
                val monsterHpMap = mutableMapOf<String, Int>()
                val mObj = data.optJSONObject("m")
                if (mObj != null) {
                    val keys = mObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val mEntry = mObj.optJSONObject(k)
                        if (mEntry != null && mEntry.has("intHP")) {
                            monsterHpMap[k] = mEntry.optInt("intHP")
                        }
                    }
                }

                // Animation messages
                val animMsgs = mutableListOf<String>()
                val animsArr = data.optJSONArray("anims") ?: data.optJSONArray("anim")
                if (animsArr != null) {
                    for (i in 0 until animsArr.length()) {
                        val aObj = animsArr.optJSONObject(i) ?: continue
                        val msg = aObj.optString("msg", "").ifEmpty { aObj.optString("str", "") }
                        if (msg.isNotEmpty() && !animMsgs.contains(msg)) animMsgs.add(msg)
                    }
                } else {
                    val aObj = data.optJSONObject("anims") ?: data.optJSONObject("anim")
                    if (aObj != null) {
                        val msg = aObj.optString("msg", "").ifEmpty { aObj.optString("str", "") }
                        if (msg.isNotEmpty() && !animMsgs.contains(msg)) animMsgs.add(msg)
                    }
                }
                val directMsg = data.optString("msg", "")
                if (directMsg.isNotEmpty() && !animMsgs.contains(directMsg)) {
                    animMsgs.add(directMsg)
                }

                // Auras
                val aurasList = mutableListOf<Pair<String, String>>()
                val aurasRemovedList = mutableListOf<Pair<String, String>>()
                val actionsArr = data.optJSONArray("a")
                if (actionsArr != null) {
                    for (i in 0 until actionsArr.length()) {
                        val actObj = actionsArr.optJSONObject(i) ?: continue
                        val tInf = actObj.optString("tInf", "")
                        val actCmd = actObj.optString("cmd", "")
                        if (actCmd.contains("aura+")) {
                            val aurasArr = actObj.optJSONArray("auras")
                            if (aurasArr != null) {
                                for (j in 0 until aurasArr.length()) {
                                    val auObj = aurasArr.optJSONObject(j) ?: continue
                                    val nam = cleanAuraName(auObj.optString("nam", ""))
                                    if (nam.isNotEmpty()) aurasList.add(Pair(nam, tInf))
                                }
                            }
                            val singleAura = actObj.optJSONObject("aura")
                            if (singleAura != null) {
                                val nam = cleanAuraName(singleAura.optString("nam", ""))
                                if (nam.isNotEmpty()) aurasList.add(Pair(nam, tInf))
                            }
                        } else if (actCmd.contains("aura-") || actCmd.contains("aura")) {
                            val remAura = cleanAuraName(
                                actObj.optJSONObject("aura")?.optString("nam", "") ?: ""
                            )
                            if (remAura.isNotEmpty()) {
                                aurasRemovedList.add(Pair(remAura, tInf))
                            } else {
                                val aurasArr = actObj.optJSONArray("auras")
                                if (aurasArr != null) {
                                    for (j in 0 until aurasArr.length()) {
                                        val auObj = aurasArr.optJSONObject(j) ?: continue
                                        val nam = cleanAuraName(auObj.optString("nam", ""))
                                        if (nam.isNotEmpty()) aurasRemovedList.add(Pair(nam, tInf))
                                    }
                                }
                            }
                        }
                    }
                }

                AqwEvent.CombatTick(
                    pCurHp,
                    pCurMp,
                    pInCombat,
                    monsterHpMap,
                    animMsgs,
                    aurasList,
                    aurasRemovedList
                )
            }

            "mtls" -> {
                val monMapId = data.optString("id", "")
                val oObj = data.optJSONObject("o")
                val hp = if (oObj != null && oObj.has("intHP")) oObj.optInt("intHP") else null
                val state =
                    if (oObj != null && oObj.has("intState")) (oObj.optInt("intState") > 0) else null
                AqwEvent.MonsterStateUpdated(monMapId, hp, state)
            }

            "uotls" -> {
                val unm = data.optString("unm", "")
                val oObj = data.optJSONObject("o")
                val hp = if (oObj != null && oObj.has("intHP")) oObj.optInt("intHP") else null
                val maxHp =
                    if (oObj != null && oObj.has("intHPMax")) oObj.optInt("intHPMax") else null
                val mp = if (oObj != null && oObj.has("intMP")) oObj.optInt("intMP") else null
                val state =
                    if (oObj != null && oObj.has("intState")) (oObj.optInt("intState") == 2) else null
                val cell = oObj?.optString("strFrame")?.takeIf { it.isNotEmpty() }
                val pad = oObj?.optString("strPad")?.takeIf { it.isNotEmpty() }
                AqwEvent.PlayerStateUpdated(
                    username = unm,
                    hp = hp,
                    maxHp = maxHp,
                    mp = mp,
                    inCombat = state,
                    cell = cell,
                    pad = pad
                )
            }

            "initUserDatas" -> {
                Log.i(TAG, "initUserDatas: $data")
                var cId: Int? = null
                var g: Long? = null
                val staffList = mutableListOf<String>()
                val aArr = data.optJSONArray("a")
                if (aArr != null) {
                    for (i in 0 until aArr.length()) {
                        val dObj = aArr.optJSONObject(i)?.optJSONObject("data") ?: continue
                        val uName = dObj.optString("strUsername", "")
                        val accessLevel =
                            if (dObj.has("intAccessLevel")) dObj.optInt("intAccessLevel") else 0
                        if (accessLevel >= 30 && uName.isNotEmpty()) {
                            staffList.add(uName)
                        }
                        if (uName.equals(currentUsername, ignoreCase = true)) {
                            if (dObj.has("CharID")) {
                                cId = dObj.optInt("CharID")
                            }
                            if (dObj.has("intGold")) {
                                g = dObj.optLong("intGold")
                            }
                        }
                    }
                }
                AqwEvent.UserDatasLoaded(cId, g, staffList)
            }

            "initUserData" -> {
                val dObj = data.optJSONObject("data")
                val uName = dObj?.optString("strUsername", "") ?: ""
                val accessLevel =
                    if (dObj != null && dObj.has("intAccessLevel")) dObj.optInt("intAccessLevel") else 0
                val isStaff = accessLevel >= 30 && uName.isNotEmpty()
                AqwEvent.SingleUserDataLoaded(uName, accessLevel, isStaff)
            }

            "loadShop" -> {
                val shopInfo = data.optJSONObject("shopinfo")
                if (shopInfo != null) {
                    val shopId = shopInfo.optInt("ShopID", 0)
                    val sName = shopInfo.optString("sName", "")
                    val bUpgrd = shopInfo.optString("bUpgrd", "0") == "1"
                    val itemsArr = shopInfo.optJSONArray("items")
                    val shopItems = mutableListOf<AqwItem>()
                    if (itemsArr != null) {
                        for (i in 0 until itemsArr.length()) {
                            val itObj = itemsArr.optJSONObject(i) ?: continue
                            shopItems.add(
                                AqwItem(
                                    itemId = itObj.optInt("ItemID", 0),
                                    charItemId = itObj.optInt("ShopItemID", 0),
                                    name = itObj.optString("sName", ""),
                                    qty = itObj.optInt("iQty", 1),
                                    maxQty = itObj.optInt("iStk", 1),
                                    isCoins = itObj.optInt("bCoins", 0) == 1,
                                    isTemp = false,
                                    sMeta = itObj.optString("sMeta", "0"),
                                    sType = itObj.optString("sType", "")
                                )
                            )
                        }
                    }
                    AqwEvent.ShopLoaded(AqwShop(shopId, sName, bUpgrd, shopItems))
                } else {
                    AqwEvent.Unknown(jsonStr)
                }
            }

            "buyItem" -> {
                val bitSuccess = data.optInt("bitSuccess", 0)
                if (bitSuccess == 1) {
                    val itemId = data.optInt("ItemID", 0)
                    val charItemId = data.optInt("CharItemID", 0)
                    val qty = data.optInt("iQty", 1)
                    AqwEvent.ItemBought(itemId, charItemId, qty)
                } else {
                    AqwEvent.Unknown(jsonStr)
                }
            }

            "sellItem" -> {
                val charItemId = data.optInt("CharItemID", 0)
                val qtyNow = data.optInt("iQtyNow", 0)
                val qty = data.optInt("iQty", 1)
                val amount = data.optLong("intAmount", 0L)
                AqwEvent.ItemSold(charItemId, qty, qtyNow, amount)
            }

            "addGoldExp" -> {
                val intGold = data.optLong("intGold", 0L)
                val intExp = data.optLong("intExp", 0L)
                val factionId = data.optInt("FactionID", 0)
                val rep = data.optInt("iRep", 0)
                AqwEvent.GoldExpAdded(intGold, intExp, factionId, rep)
            }

            "Wheel", "wheel" -> {
                val dropItems = data.optJSONObject("dropItems")
                val dropsList = mutableListOf<String>()
                if (dropItems != null) {
                    val keys = dropItems.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val itemObj = dropItems.optJSONObject(k)
                        val sName = itemObj?.optString("sName", "")
                        if (!sName.isNullOrEmpty()) dropsList.add(sName)
                    }
                }
                AqwEvent.WheelSpun(dropsList)
            }

            "dropItem" -> {
                val itemsObj = data.optJSONObject("items")
                if (itemsObj != null) {
                    val droppedList = mutableListOf<AqwItem>()
                    val keys = itemsObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val itemEntry = itemsObj.optJSONObject(k) ?: continue
                        val id = itemEntry.optInt("ItemID", k.toIntOrNull() ?: 0)
                        val name = itemEntry.optString("sName", "Item $id")
                        val qty = itemEntry.optInt("iQty", 1)
                        val maxQty = itemEntry.optInt("iStk", 1)
                        val isCoins = itemEntry.optInt("bCoins", 0) == 1 || itemEntry.optString(
                            "bCoins",
                            "0"
                        ) == "1"
                        val isTemp = itemEntry.optInt("bTemp", 0) == 1 || itemEntry.optString(
                            "bTemp",
                            "0"
                        ) == "1"
                        val sMeta = itemEntry.optString("sMeta", "")
                        val sType = itemEntry.optString("sType", "")
                        droppedList.add(
                            AqwItem(
                                itemId = id,
                                name = name,
                                qty = qty,
                                maxQty = maxQty,
                                isCoins = isCoins,
                                isTemp = isTemp,
                                sMeta = sMeta,
                                sType = sType
                            )
                        )
                    }
                    if (droppedList.isNotEmpty()) {
                        val first = droppedList.first()
                        return AqwEvent.ItemDropped(
                            itemId = first.itemId,
                            itemName = first.name,
                            qty = first.qty,
                            items = droppedList
                        )
                    }
                }
                AqwEvent.Unknown(jsonStr)
            }

            "addItems" -> {
                val itemsObj = data.optJSONObject("items")
                val addedItems = mutableListOf<AqwItem>()
                if (itemsObj != null) {
                    val keys = itemsObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val itObj = itemsObj.optJSONObject(k) ?: continue
                        val itemId = itObj.optInt("ItemID", k.toIntOrNull() ?: 0)
                        val charItemId = itObj.optInt("CharItemID", 0)
                        val bTemp =
                            itObj.optInt("bTemp", 0) == 1 || itObj.optString("bTemp", "0") == "1"
                        val isTemp = charItemId == 0 || bTemp
                        val deltaQty = itObj.optInt("iQty", 1)
                        val qtyNow = itObj.optInt("iQtyNow", deltaQty)
                        val qty = if (isTemp) deltaQty else qtyNow

                        addedItems.add(
                            AqwItem(
                                itemId = itemId,
                                charItemId = charItemId,
                                name = itObj.optString("sName", ""),
                                qty = qty,
                                maxQty = itObj.optInt("iStk", 1),
                                isCoins = itObj.optInt(
                                    "bCoins",
                                    0
                                ) == 1 || itObj.optString("bCoins", "0") == "1",
                                isTemp = isTemp,
                                sMeta = itObj.optString("sMeta", ""),
                                sType = itObj.optString("sType", "")
                            )
                        )
                    }
                }
                AqwEvent.ItemsAdded(addedItems)
            }

            "turnIn" -> {
                val sItems = data.optString("sItems", "")
                val deductions = mutableListOf<Pair<Int, Int>>()
                if (sItems.isNotEmpty()) {
                    sItems.split(",").forEach { pair ->
                        val parts = pair.split(":")
                        if (parts.size >= 2) {
                            val itemId = parts[0].toIntOrNull() ?: 0
                            val qty = parts[1].toIntOrNull() ?: 0
                            if (itemId > 0) deductions.add(Pair(itemId, qty))
                        }
                    }
                }
                AqwEvent.ItemsTurnedIn(deductions)
            }

            "ccqr" -> {
                val questId = data.optInt("QuestID", 0)
                val sName = data.optString("sName", "")
                val bSuccess = data.optInt("bSuccess", 0) == 1
                val msg = data.optString("msg", "")
                val rewardObj = data.optJSONObject("rewardObj")
                val factionId = rewardObj?.optInt("FactionID", 0) ?: 0
                val rep = rewardObj?.optInt("iRep", 0) ?: 0
                AqwEvent.QuestCompleted(questId, sName, bSuccess, msg, factionId, rep)
            }

            "acceptQuest" -> {
                val questId = data.optInt("QuestID", 0)
                val bSuccess = data.optInt("bSuccess", 0) == 1
                AqwEvent.QuestAccepted(questId, bSuccess)
            }

            "getQuests" -> {
                val questsObj = data.optJSONObject("quests")
                val questsList = mutableListOf<AqwQuest>()
                if (questsObj != null) {
                    val keys = questsObj.keys()
                    while (keys.hasNext()) {
                        val qId = keys.next()
                        val qData = questsObj.optJSONObject(qId) ?: continue
                        val questId = qData.optInt("QuestID", qId.toIntOrNull() ?: 0)
                        val qName = qData.optString("sName", "")
                        val turnInArr = qData.optJSONArray("turnin")
                        val reqList = mutableListOf<AqwQuestItem>()
                        if (turnInArr != null) {
                            for (i in 0 until turnInArr.length()) {
                                val rObj = turnInArr.optJSONObject(i) ?: continue
                                reqList.add(
                                    AqwQuestItem(
                                        itemId = rObj.optInt("ItemID", 0),
                                        name = rObj.optString("sName", ""),
                                        qty = rObj.optInt("iQty", 1)
                                    )
                                )
                            }
                        }
                        questsList.add(AqwQuest(questId, qName, reqList))
                    }
                }
                AqwEvent.QuestDetailsLoaded(questsList)
            }

            "addFaction" -> {
                val facObj = data.optJSONObject("faction")
                if (facObj != null) {
                    val fId = facObj.optInt("FactionID", 0)
                    val charFactionId = facObj.optString("CharFactionID", "")
                    val sName = facObj.optString("sName", "")
                    val iRep = facObj.optInt("iRep", 0)
                    AqwEvent.FactionAdded(AqwFaction(fId, charFactionId, sName, iRep))
                } else {
                    AqwEvent.Unknown(jsonStr)
                }
            }

            "clearAuras" -> {
                AqwEvent.AurasCleared
            }

            "seia" -> {
                val oObj = data.optJSONObject("o")
                val anim = oObj?.optString("anim", "") ?: ""
                val strl = oObj?.optString("strl", "") ?: ""
                val cd = oObj?.optDouble("cd", 10.0) ?: 10.0
                val tgt = oObj?.optString("tgt", "h") ?: "h"
                AqwEvent.ScrollEquipped(anim, strl, cd, tgt)
            }

            "pi" -> {
                val pid = data.optInt("pid", 0)
                val owner = data.optString("owner", "")
                AqwEvent.PartyInviteReceived(pid, owner)
            }

            "playerDeath" -> {
                val userId = data.optInt("userID", 0)
                AqwEvent.PlayerDied(userId)
            }

            else -> AqwEvent.Unknown(jsonStr)
        }
    }

    private fun cleanAuraName(raw: String): String {
        return raw.trim().replace("`", "'").replace("’", "'").replace("❜", "'")
    }
}

