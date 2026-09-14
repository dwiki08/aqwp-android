package froztt13.python.aqw.core.engine.commands

import froztt13.python.aqw.core.network.AqwSocketClient

/**
 * Handles party interactions, chat messaging, dungeon queues, and raw packet transmission.
 */
class AqwSocialCommands(
    private val client: AqwSocketClient
) {

    suspend fun partyInvite(targetUsername: String): Boolean {
        val packet = "%xt%zm%gp%1%pi%${targetUsername}%"
        return client.send(packet)
    }

    suspend fun partyAccept(partyId: Int): Boolean {
        val packet = "%xt%zm%gp%1%pa%${partyId}%"
        return client.send(packet)
    }

    suspend fun dungeonQueue(dungeonName: String): Boolean {
        val packet = "%xt%zm%dungeonQueue%25127%${dungeonName}%"
        return client.send(packet)
    }

    suspend fun sendChat(message: String, channel: String = "zone"): Boolean {
        val packet = "%xt%zm%message%1%${message}%${channel}%"
        return client.send(packet)
    }

    suspend fun sendRaw(packet: String): Boolean {
        return client.send(packet)
    }
}
