package froztt13.python.aqw.core.model

import froztt13.python.aqw.helper.Utils

data class AqwServer(
    val name: String = "",
    val ip: String = "",
    val port: Int = 5588,
    val maxPlayers: Int = 1000,
    val currentCount: Int = 0,
    val isMemberOnly: Boolean = false,
    val isOnline: Boolean = true
)

data class AqwLoginResult(
    val success: Boolean = false,
    val userId: Int = 0,
    val token: String = "",
    val isMember: Boolean = false,
    val servers: List<AqwServer> = emptyList(),
    val errorMessage: String? = null
)

data class AqwMonster(
    val monMapId: String = "",
    val monId: String = "",
    var name: String = "",
    var currentHp: Int = 0,
    var maxHp: Int = 0,
    var isAlive: Boolean = true,
    var frame: String = "",
    val auras: MutableList<AqwAura> = mutableListOf()
) {
    val hpFraction: Float
        get() = if (maxHp > 0) (currentHp.toFloat() / maxHp.toFloat()).coerceIn(0f, 1f) else 0f

    val hpPercent: Int
        get() = if (maxHp > 0) ((currentHp.toDouble() / maxHp.toDouble()) * 100).toInt() else 0

    fun addAura(auras: List<AqwAura>) {
        for (aura in auras) {
            val isNew = aura.isNew
            val name = Utils.normalize(aura.name)
            if (isNew) {
                this.auras.add(aura.copy(name = name))
            } else {
                for (existingAura in this.auras) {
                    if (existingAura.name == name || Utils.normalize(existingAura.name) == name) {
                        existingAura.refresh(aura.duration)
                    }
                }
            }
        }
    }

    fun addAura(aura: AqwAura) {
        addAura(listOf(aura))
    }

    fun addAura(auraName: String, duration: Int = 0, isNew: Boolean = true) {
        addAura(listOf(AqwAura(name = auraName, duration = duration, isNew = isNew)))
    }

    fun removeAura(auraName: String?) {
        if (auraName.isNullOrEmpty()) return
        val normalizedName = Utils.normalize(auraName)
        val iterator = this.auras.iterator()
        while (iterator.hasNext()) {
            val aura = iterator.next()
            if (Utils.normalize(aura.name) == normalizedName) {
                iterator.remove()
                break
            }
        }
    }

    fun removeAllAuras() {
        this.auras.clear()
    }

    fun getAura(auraName: String): AqwAura? {
        val normalizedName = Utils.normalize(auraName)
        for (aura in this.auras) {
            if (Utils.normalize(aura.name) == normalizedName && !aura.isExpired()) {
                return aura
            }
        }
        return null
    }

    fun hasAura(auraName: String): Boolean {
        return getAura(auraName) != null
    }
}

data class AqwItem(
    val itemId: Int = 0,
    var charItemId: Int = 0,
    val name: String = "",
    var qty: Int = 1,
    val maxQty: Int = 1,
    val isCoins: Boolean = false,
    val isTemp: Boolean = false,
    val sMeta: String = "",
    val sType: String = "",
    var isEquipped: Boolean = false
) {
    val normalizedName: String
        get() = Utils.normalize(name)

    fun matches(otherName: String?): Boolean {
        return Utils.normalize(name) == Utils.normalize(otherName)
    }
}

data class AqwOtherPlayer(
    val username: String = "",
    val userId: Int = 0,
    var roomUserId: Int = 0,
    var cell: String = "Enter",
    var pad: String = "Spawn",
    var hp: Int = 0,
    var maxHp: Int = 0,
    var mp: Int = 0,
    var isDead: Boolean = false,
    val auras: MutableList<String> = mutableListOf()
)

data class AqwSkill(
    val id: String = "",
    val index: Int = 0,
    var name: String = "",
    var anim: String = "",
    var strl: String = "",
    var cdSeconds: Double = 2.0,
    var cdMillis: Double = 2000.0,
    var mpCost: Double = 0.0,
    var tgt: String = "h",
    var tgtMax: Int = 1,
    var nextUseTimestamp: Long = 0L
) {
    fun isReady(): Boolean = System.currentTimeMillis() >= nextUseTimestamp
    fun remainingCooldownMs(): Long = maxOf(0L, nextUseTimestamp - System.currentTimeMillis())
    fun resetCooldown() {
        nextUseTimestamp = 0L
    }
}

data class AqwAura(
    var name: String = "",
    var count: Int = 1,
    var duration: Int = 0,
    var appliedAt: Long = System.currentTimeMillis(),
    var expiredAt: Long = if (duration > 0) System.currentTimeMillis() + (duration * 1000L) else 0L,
    var isNew: Boolean = false
) {
    fun isExpired(): Boolean = expiredAt > 0L && System.currentTimeMillis() >= expiredAt

    fun refresh(dur: Int) {
        duration = dur
        appliedAt = System.currentTimeMillis()
        expiredAt = if (dur > 0) appliedAt + (dur * 1000L) else 0L
    }

    val remainingDurationMs: Long
        get() = if (expiredAt <= 0L) Long.MAX_VALUE else maxOf(
            0L,
            expiredAt - System.currentTimeMillis()
        )

    companion object {
        fun normalize(name: String?): String = Utils.normalize(name)
    }
}

data class AqwShop(
    val shopId: Int = 0,
    val shopName: String = "",
    val isMember: Boolean = false,
    val items: List<AqwItem> = emptyList()
) {
    fun getItem(itemName: String): AqwItem? {
        val norm = Utils.normalize(itemName)
        return items.firstOrNull { Utils.normalize(it.name) == norm }
    }
}

data class AqwQuestItem(
    val itemId: Int = 0,
    val name: String = "",
    val qty: Int = 1
)

data class AqwQuest(
    val questId: Int = 0,
    val name: String = "",
    val turnInItems: List<AqwQuestItem> = emptyList()
)

data class AqwFaction(
    val factionId: Int = 0,
    val charFactionId: String = "",
    val name: String = "",
    var rep: Int = 0
) {
    fun getRank(): Int {
        return when {
            rep >= 302500 -> 10
            rep >= 202500 -> 9
            rep >= 129600 -> 8
            rep >= 78400 -> 7
            rep >= 44100 -> 6
            rep >= 22500 -> 5
            rep >= 10000 -> 4
            rep >= 3600 -> 3
            rep >= 900 -> 2
            else -> 1
        }
    }
}

data class AqwPlayerState(
    var username: String = "",
    var charId: Int = 0,
    var authUserId: Int = 0,
    var roomUserId: Int = 0,
    var token: String = "",
    var cell: String = "Enter",
    var pad: String = "Spawn",
    var currentHp: Int = 9999,
    var maxHp: Int = 9999,
    var mp: Int = 100,
    var maxMp: Int = 100,
    var isInCombat: Boolean = false,
    var isDead: Boolean = false,
    var gold: Long = 0L,
    var goldFarmed: Long = 0L,
    var expFarmed: Long = 0L,
    var cdReduction: Double = 0.0,
    var manaCost: Double = 1.0,
    var areaName: String = "",
    var areaId: Int = 0,
    var mapName: String = "",
    var roomNumber: Int? = null,
    var isJoiningMap: Boolean = false,
    var followedPlayer: String = "",
    var followedPlayerCell: String? = null,
    val inventory: MutableList<AqwItem> = mutableListOf(),
    val tempInventory: MutableList<AqwItem> = mutableListOf(),
    val droppedItems: MutableList<AqwItem> = mutableListOf(),
    val bank: MutableList<AqwItem> = mutableListOf(),
    val skills: MutableList<AqwSkill> = mutableListOf(),
    val auras: MutableList<AqwAura> = mutableListOf(),
    val factions: MutableList<AqwFaction> = mutableListOf(),
    val loadedShops: MutableList<AqwShop> = mutableListOf(),
    val loadedQuests: MutableList<AqwQuest> = mutableListOf(),
    val activeQuestIds: MutableSet<Int> = mutableSetOf(),
    val registeredAutoQuestIds: MutableSet<Int> = mutableSetOf(),
    val failedQuestIds: MutableSet<Int> = mutableSetOf(),
    val missingTurnInItemQuestIds: MutableSet<Int> = mutableSetOf(),
    val missingQuestProgressQuestIds: MutableSet<Int> = mutableSetOf(),
    val oneTimeQuestIds: MutableSet<Int> = mutableSetOf(),
    val roomUserIds: MutableList<Int> = mutableListOf(),
    val playersInMap: MutableMap<String, AqwOtherPlayer> = mutableMapOf()
) {
    fun addAura(auras: List<AqwAura>) {
        for (aura in auras) {
            val isNew = aura.isNew
            val name = Utils.normalize(aura.name)
            if (isNew) {
                this.auras.add(aura.copy(name = name))
            } else {
                for (existingAura in this.auras) {
                    if (existingAura.name == name || Utils.normalize(existingAura.name) == name) {
                        existingAura.refresh(aura.duration)
                    }
                }
            }
        }
    }

    fun addAura(aura: AqwAura) {
        addAura(listOf(aura))
    }

    fun addAura(auraName: String, duration: Int = 0, isNew: Boolean = true) {
        addAura(listOf(AqwAura(name = auraName, duration = duration, isNew = isNew)))
    }

    fun removeAura(auraName: String?) {
        if (auraName.isNullOrEmpty()) return
        val normalizedName = Utils.normalize(auraName)
        val iterator = this.auras.iterator()
        while (iterator.hasNext()) {
            val aura = iterator.next()
            if (Utils.normalize(aura.name) == normalizedName) {
                iterator.remove()
                break
            }
        }
    }

    fun removeAllAuras() {
        this.auras.clear()
    }

    fun resetAllSkills() {
        for (skill in skills) {
            skill.resetCooldown()
        }
    }

    fun getAura(auraName: String): AqwAura? {
        val normalizedName = Utils.normalize(auraName)
        for (aura in this.auras) {
            if (Utils.normalize(aura.name) == normalizedName && !aura.isExpired()) {
                return aura
            }
        }
        return null
    }

    fun hasAura(auraName: String): Boolean {
        return getAura(auraName) != null
    }

    companion object {
        fun normalize(name: String?): String = Utils.normalize(name)
    }

    fun getItemInventory(name: String): AqwItem? {
        val norm = Utils.normalize(name)
        return inventory.firstOrNull { Utils.normalize(it.name) == norm }
    }

    fun getItemBank(name: String): AqwItem? {
        val norm = Utils.normalize(name)
        return bank.firstOrNull { Utils.normalize(it.name) == norm }
    }

    fun getItemTemp(name: String): AqwItem? {
        val norm = Utils.normalize(name)
        return tempInventory.firstOrNull { Utils.normalize(it.name) == norm }
    }

    fun getItemDropped(name: String): AqwItem? {
        val norm = Utils.normalize(name)
        return droppedItems.firstOrNull { Utils.normalize(it.name) == norm }
    }

    fun getItemInventoryById(id: Int): AqwItem? {
        return inventory.firstOrNull { it.itemId == id || (it.charItemId != 0 && it.charItemId == id) }
    }

    fun getItemBankById(id: Int): AqwItem? {
        return bank.firstOrNull { it.itemId == id || (it.charItemId != 0 && it.charItemId == id) }
    }

    fun getItemTempById(id: Int): AqwItem? {
        return tempInventory.firstOrNull { it.itemId == id || (it.charItemId != 0 && it.charItemId == id) }
    }

    fun getItemDroppedById(id: Int): AqwItem? {
        return droppedItems.firstOrNull { it.itemId == id }
    }

    fun addFaction(faction: AqwFaction) {
        val existing = factions.firstOrNull { it.factionId == faction.factionId }
        if (existing != null) {
            existing.rep = faction.rep
        } else {
            factions.add(faction)
        }
    }

    fun addRepToFaction(factionId: Int, repGained: Int) {
        val existing = factions.firstOrNull { it.factionId == factionId }
        if (existing != null) {
            existing.rep += repGained
        } else {
            factions.add(AqwFaction(factionId = factionId, rep = repGained))
        }
    }

    fun snapshot(): AqwPlayerState {
        return this.copy(
            inventory = inventory.map { it.copy() }.toMutableList(),
            tempInventory = tempInventory.map { it.copy() }.toMutableList(),
            droppedItems = droppedItems.map { it.copy() }.toMutableList(),
            bank = bank.map { it.copy() }.toMutableList(),
            skills = skills.map { it.copy() }.toMutableList(),
            auras = auras.map { it.copy() }.toMutableList(),
            factions = factions.map { it.copy() }.toMutableList(),
            loadedShops = loadedShops.map { it.copy() }.toMutableList(),
            loadedQuests = loadedQuests.map { it.copy() }.toMutableList(),
            activeQuestIds = activeQuestIds.toMutableSet(),
            registeredAutoQuestIds = registeredAutoQuestIds.toMutableSet(),
            failedQuestIds = failedQuestIds.toMutableSet(),
            missingTurnInItemQuestIds = missingTurnInItemQuestIds.toMutableSet(),
            missingQuestProgressQuestIds = missingQuestProgressQuestIds.toMutableSet(),
            oneTimeQuestIds = oneTimeQuestIds.toMutableSet(),
            roomUserIds = roomUserIds.toMutableList(),
            playersInMap = playersInMap.mapValues { it.value.copy(auras = it.value.auras.toMutableList()) }
                .toMutableMap()
        )
    }
}
