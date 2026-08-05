package nl.skbotnl.chatog.chatsystem

import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.read
import kotlinx.coroutines.launch
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import nl.skbotnl.chatog.ChatOG.Companion.config
import nl.skbotnl.chatog.ChatOG.Companion.discordBridge
import nl.skbotnl.chatog.ChatOG.Companion.discordBridgeLock
import nl.skbotnl.chatog.ChatOG.Companion.plugin
import nl.skbotnl.chatog.ChatOG.Companion.scope
import nl.skbotnl.chatog.api.WorldChatFormatter
import nl.skbotnl.chatog.util.ChatUtil
import org.bukkit.Bukkit
import org.bukkit.entity.Player

// Chat for one world of a multi world game, isolated in game but sharing the game's Discord channel.
internal class WorldChatSystem(private val worldName: String, val key: String, val id: String, lobby: Boolean) :
    ChatSystem() {
    // No label in game, since everyone who can read this chat is already in the same world.
    override val prefix = null

    // The label only tags the webhook username, which is what tells a game's worlds apart in Discord.
    private val label =
        (if (lobby) config.discord.gameLobbyLabel ?: DEFAULT_LOBBY_LABEL
            else config.discord.gameWorldLabel ?: DEFAULT_WORLD_LABEL)
            .replace("%id%", id)

    // Filtering the online players avoids touching World.getPlayers() from the async chat coroutine.
    override val audience: Audience
        get() = Audience.audience(Bukkit.getOnlinePlayers().filter { it.world.name == worldName })

    override val name = "world:$worldName"

    override fun sendDiscordMessage(text: String, playerPartString: String, uuid: UUID) {
        if (!config.discord.enabled || config.discord.games[key]?.enabled != true) return
        val discordMessageString = ChatUtil.convertEmojis(text)

        scope.launch {
            discordBridgeLock.read {
                discordBridge?.sendGameMessage(key, discordMessageString, "$label $playerPartString", uuid)
            }
        }
    }

    // A formatter belongs to another plugin, so a failure there must not take the chat line down with it.
    override fun formatLine(player: Player, message: Component): Component? {
        val formatter = formatters[key.uppercase()] ?: return null
        return try {
            formatter.format(player, message, worldName, id)
        } catch (throwable: Throwable) {
            plugin.logger.warning("The chat formatter for game \"$key\" failed: $throwable")
            null
        }
    }

    companion object {
        private const val DEFAULT_LOBBY_LABEL = "[%id% - Lobby]"
        private const val DEFAULT_WORLD_LABEL = "[%id% - In Game]"

        // Keyed by uppercased game key so registration order and config casing cannot disagree.
        private val formatters = ConcurrentHashMap<String, WorldChatFormatter>()

        // Worlds are named <prefix><number>-hub for a lobby and <prefix><number>-<map> for a game, so HB1-hub and
        // HB1-Ancient_Plateau both belong to the configured discord.games key "HB".
        fun forWorld(worldName: String): WorldChatSystem? {
            var index = 0
            while (index < worldName.length && worldName[index].isLetter()) index++
            if (index == 0) return null

            val digitStart = index
            while (index < worldName.length && worldName[index].isDigit()) index++
            if (index == digitStart) return null

            if (index >= worldName.length || worldName[index] != '-') return null
            val rest = worldName.substring(index + 1)
            if (rest.isEmpty()) return null

            val prefix = worldName.substring(0, digitStart)
            // Resolved from the configured key alone, so turning a game's Discord channel off does not
            // also drop its worlds back into global chat.
            val key = config.discord.games.keys.firstOrNull { it.equals(prefix, ignoreCase = true) } ?: return null

            return WorldChatSystem(worldName, key, worldName.substring(0, index), rest.equals("hub", true))
        }

        fun keyForWorld(worldName: String): String? = forWorld(worldName)?.key

        fun playersForKey(key: String): List<Player> =
            Bukkit.getOnlinePlayers().filter { keyForWorld(it.world.name) == key }

        // A null formatter clears the one registered for that game.
        fun setFormatter(key: String, formatter: WorldChatFormatter?) {
            if (formatter == null) formatters.remove(key.uppercase()) else formatters[key.uppercase()] = formatter
        }

        // In game only. Chat is what belongs in Discord, not every game announcement.
        fun broadcast(worldName: String, message: Component): Int {
            val recipients = Bukkit.getOnlinePlayers().filter { it.world.name == worldName }
            Audience.audience(recipients).sendMessage(message)
            return recipients.size
        }
    }
}
