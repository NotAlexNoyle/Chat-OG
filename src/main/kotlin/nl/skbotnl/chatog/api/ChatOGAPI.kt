package nl.skbotnl.chatog.api

import net.kyori.adventure.text.Component
import nl.skbotnl.chatog.ChatOG
import nl.skbotnl.chatog.chatsystem.WorldChatSystem

// Multi world game chat API. Worlds are bound to Discord channels in Chat-OG's config, so a plugin
// only ever supplies formatting. Callers must declare softdepend: [Chat-OG] in their plugin.yml.
object ChatOGAPI {
    // False until Chat-OG has fully enabled, and again once it disables.
    @JvmStatic fun isAvailable(): Boolean = ChatOG.isReady()

    // Sets the chat formatter for a game key, for example "HB". False when Chat-OG is not ready.
    @JvmStatic
    fun setFormatter(key: String, formatter: WorldChatFormatter?): Boolean {
        if (!isAvailable()) return false
        WorldChatSystem.setFormatter(key, formatter)
        return true
    }

    // Lobby id for a world, for example "HB1", or null if it is not part of a multi world game.
    @JvmStatic
    fun getLobbyId(worldName: String): String? {
        if (!isAvailable()) return null
        return WorldChatSystem.forWorld(worldName)?.id
    }

    // Sends a message to everyone in a world and returns how many players received it.
    @JvmStatic
    fun broadcast(worldName: String, message: Component): Int {
        if (!isAvailable()) return 0
        return WorldChatSystem.broadcast(worldName, message)
    }
}
