package nl.skbotnl.chatog.api

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

// Lets a plugin render its own chat line for the multi world game it owns.
fun interface WorldChatFormatter {
    // The message is already blocklist checked, colorized and permission gated by Chat-OG.
    // Never serialize it back to a string and re-parse it, or players without chat-og.color regain formatting.
    // Compose instead, for example Component.join(noSeparators(), UtilitiesOG.trueogColorize(prefix), message).
    // Return the finished line, null to use Chat-OG's default format, or Component.empty() to drop the message.
    fun format(sender: Player, message: Component, worldName: String, lobbyId: String): Component?
}
