package froztt13.python.aqw.data.engine.commands

import froztt13.python.aqw.data.network.AqwSocketClient
import froztt13.python.aqw.domain.model.AqwItem
import froztt13.python.aqw.domain.model.AqwPlayerState
import froztt13.python.aqw.domain.model.AqwShop
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Handles shops, inventory operations, banking, item equipping, drop collection, and item checks.
 */
class AqwItemCommands(
    private val client: AqwSocketClient,
    private val playerState: AqwPlayerState,
    private val leaveCombat: suspend (safeLeave: Boolean) -> Boolean = { false },
    private val onScrollEquipped: (scrollId: String) -> Unit = {},
    private val onItemUpdated: () -> Unit = {}
) {
    val recentlyPickedDropIds = mutableSetOf<Int>()

    // ==========================================
    // SHOP COMMANDS
    // ==========================================

    suspend fun loadShop(shopId: Int): Boolean {
        val packet = "%xt%zm%loadShop%${playerState.areaId}%${shopId}%"
        return client.send(packet)
    }

    fun getLoadedShop(shopId: Int): AqwShop? {
        return playerState.loadedShops.firstOrNull { it.shopId == shopId }
    }

    suspend fun ensureLoadShop(shopId: Int, timeoutMs: Long = 5000L): AqwShop? {
        if (playerState.isInCombat) {
            leaveCombat(true)
        }
        val cached = getLoadedShop(shopId)
        if (cached != null) return cached

        loadShop(shopId)
        val start = System.currentTimeMillis()
        while (client.isConnected.value && (System.currentTimeMillis() - start) < timeoutMs) {
            val loaded = getLoadedShop(shopId)
            if (loaded != null) return loaded
            delay(200.milliseconds)
        }
        return null
    }

    suspend fun buyItem(shopId: Int, itemName: String, qty: Int = 1): Boolean {
        val shop = ensureLoadShop(shopId) ?: return false
        val item = shop.getItem(itemName) ?: return false
        val packet =
            "%xt%zm%buyItem%${playerState.areaId}%${item.itemId}%${shop.shopId}%${item.charItemId}%${qty}%"
        val sent = client.send(packet)
        delay(1000.milliseconds)
        return sent
    }

    suspend fun buyItem(shopId: Int, itemId: Int, shopItemId: Int, qty: Int = 1): Boolean {
        val packet =
            "%xt%zm%buyItem%${playerState.areaId}%${itemId}%${shopId}%${shopItemId}%${qty}%"
        val sent = client.send(packet)
        delay(1000.milliseconds)
        return sent
    }

    // ==========================================
    // SELL COMMANDS
    // ==========================================

    suspend fun sellItem(itemName: String, qty: Int = 1): Boolean {
        val item = playerState.getItemInventory(itemName) ?: return false
        return sellItem(item.itemId, item.charItemId, qty)
    }

    suspend fun sellItem(itemId: Int, charItemId: Int, qty: Int = 1): Boolean {
        val packet = "%xt%zm%sellItem%${playerState.areaId}%${itemId}%${qty}%${charItemId}%"
        val sent = client.send(packet)
        delay(500.milliseconds)
        return sent
    }

    suspend fun sellItem(item: AqwItem, qty: Int = 1): Boolean {
        return sellItem(item.itemId, item.charItemId, qty)
    }

    // ==========================================
    // BANK OPERATIONS
    // ==========================================

    suspend fun bankToInv(itemName: String): Boolean {
        val bankItem = playerState.getItemBank(itemName)
        if (bankItem != null) {
            val packet =
                "%xt%zm%bankToInv%${playerState.areaId}%${bankItem.itemId}%${bankItem.charItemId}%"
            val sent = client.send(packet)
            if (sent) {
                playerState.bank.remove(bankItem)
                playerState.inventory.add(bankItem)
                delay(500.milliseconds)
            }
            return sent
        }
        return false
    }

    suspend fun bankToInv(itemNames: List<String>): Boolean {
        var allSuccess = true
        for (name in itemNames) {
            if (!client.isConnected.value) break
            if (!bankToInv(name)) {
                allSuccess = false
            }
        }
        return allSuccess
    }

    suspend fun invToBank(itemName: String): Boolean {
        if (playerState.isInCombat) {
            leaveCombat(true)
        }
        val invItem = playerState.getItemInventory(itemName)
        if (invItem != null) {
            val packet =
                "%xt%zm%bankFromInv%${playerState.areaId}%${invItem.itemId}%${invItem.charItemId}%"
            val sent = client.send(packet)
            if (sent) {
                playerState.inventory.remove(invItem)
                playerState.bank.add(invItem)
                delay(500.milliseconds)
            }
            return sent
        }
        return false
    }

    suspend fun invToBank(itemNames: List<String>): Boolean {
        var allSuccess = true
        for (name in itemNames) {
            if (!client.isConnected.value) break
            if (!invToBank(name)) {
                allSuccess = false
            }
        }
        return allSuccess
    }

    // ==========================================
    // EQUIP OPERATIONS
    // ==========================================

    suspend fun equipItem(itemName: String): Boolean {
        val item = playerState.getItemInventory(itemName) ?: return false
        return equipItem(item.itemId)
    }

    suspend fun equipItem(itemId: Int): Boolean {
        val packet = "%xt%zm%equipItem%${playerState.areaId}%${itemId}%"
        return client.send(packet)
    }

    suspend fun equipScroll(itemName: String): Boolean {
        val item = playerState.getItemInventory(itemName) ?: return false
        return equipScroll(item.itemId, item.sMeta.ifBlank { "0" })
    }

    suspend fun equipScroll(itemId: Int, sMeta: String = "0"): Boolean {
        onScrollEquipped(itemId.toString())
        val packet = "%xt%zm%geia%${playerState.areaId}%scroll%${sMeta}%${itemId}%"
        return client.send(packet)
    }

    // ==========================================
    // DROPS & MAP ITEMS
    // ==========================================

    suspend fun getItemDrop(itemId: Int): Boolean {
        val dropItem = playerState.droppedItems.firstOrNull { it.itemId == itemId }
        val packet = "%xt%zm%getDrop%${playerState.areaId}%${itemId}%"
        val sent = client.send(packet)
        if (sent) {
            recentlyPickedDropIds.add(itemId)
            if (dropItem != null) {
                playerState.droppedItems.remove(dropItem)
                if (dropItem.isTemp) {
                    val existing = playerState.getItemTempById(itemId)
                    if (existing != null) {
                        existing.qty += dropItem.qty
                    } else {
                        playerState.tempInventory.add(dropItem.copy())
                    }
                } else {
                    val existing = playerState.getItemInventoryById(itemId)
                    if (existing != null) {
                        existing.qty += dropItem.qty
                    } else {
                        playerState.inventory.add(dropItem.copy())
                    }
                }
            } else {
                val existingTemp = playerState.getItemTempById(itemId)
                val existingInv = playerState.getItemInventoryById(itemId)
                if (existingTemp != null) {
                    existingTemp.qty += 1
                } else if (existingInv != null) {
                    existingInv.qty += 1
                }
            }
            onItemUpdated()
        }
        return sent
    }

    suspend fun getItemDrop(itemName: String): Boolean {
        val drop = playerState.getItemDropped(itemName) ?: return false
        return getItemDrop(drop.itemId)
    }

    suspend fun getMapItem(mapItemId: Int, qty: Int = 1): Boolean {
        var success = true
        for (i in 0 until qty) {
            val packet = "%xt%zm%getMapItem%${playerState.areaId}%${mapItemId}%"
            if (!client.send(packet)) success = false
            delay(1000.milliseconds)
        }
        return success
    }

    // ==========================================
    // QUANTITY & EXISTENCE CHECKS
    // ==========================================

    fun hasItem(itemName: String, qty: Int = 1, isTemp: Boolean = false): Boolean {
        val currentQty = getItemQty(itemName, isTemp)
        return currentQty >= qty
    }

    fun hasItemInBank(itemName: String, qty: Int = 1): Boolean {
        val item = playerState.getItemBank(itemName)
        return (item?.qty ?: 0) >= qty
    }

    fun hasItemInInventoryOrBank(itemName: String, qty: Int = 1, isTemp: Boolean = false): Boolean {
        return hasItem(itemName, qty, isTemp) || hasItemInBank(itemName, qty)
    }

    fun getItemQty(itemName: String, isTemp: Boolean = false): Int {
        return if (isTemp) {
            playerState.getItemTemp(itemName)?.qty ?: 0
        } else {
            playerState.getItemInventory(itemName)?.qty ?: 0
        }
    }

    fun getItemQty(itemId: Int, isTemp: Boolean = false): Int {
        return if (isTemp) {
            playerState.getItemTempById(itemId)?.qty ?: 0
        } else {
            playerState.getItemInventoryById(itemId)?.qty ?: 0
        }
    }
}
