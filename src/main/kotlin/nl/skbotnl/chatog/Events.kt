package nl.skbotnl.chatog

import de.myzelyam.api.vanish.PlayerVanishStateChangeEvent
import de.myzelyam.api.vanish.VanishAPI
import io.papermc.paper.advancement.AdvancementDisplay.Frame.*
import io.papermc.paper.event.player.AsyncChatEvent
import kotlin.concurrent.read
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.TranslatableComponent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.trueog.utilitiesog.UtilitiesOG
import nl.skbotnl.chatog.ChatOG.Companion.config
import nl.skbotnl.chatog.ChatOG.Companion.discordBridge
import nl.skbotnl.chatog.ChatOG.Companion.discordBridgeLock
import nl.skbotnl.chatog.ChatOG.Companion.scope
import nl.skbotnl.chatog.chatsystem.GeneralChatSystem
import nl.skbotnl.chatog.chatsystem.WorldChatSystem
import nl.skbotnl.chatog.util.ChatUtil
import nl.skbotnl.chatog.util.ChatUtil.legacyToMm
import nl.skbotnl.chatog.util.PlayerExtensions.chatSystem
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerAdvancementDoneEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.server.BroadcastMessageEvent
import xyz.jpenilla.announcerplus.listener.JoinQuitListener

internal class Events : Listener {
    // The main world and its nether and end, taken from level-name rather than assumed to be "world".
    private fun isMainWorld(worldName: String): Boolean {
        val main = Bukkit.getWorlds().firstOrNull()?.name ?: return false
        return worldName.equals(main, true) ||
            worldName.equals("${main}_nether", true) ||
            worldName.equals("${main}_the_end", true)
    }

    // A multi world game world reports to its own channel, the main world to general, anything else nowhere.
    private fun reports(worldName: String, key: String?) = key != null || isMainWorld(worldName)

    // A game channel counts only its own players, since the server total would mislead there.
    private fun onlineCount(key: String?) =
        if (key == null) Bukkit.getOnlinePlayers().count() else WorldChatSystem.playersForKey(key).size

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        if (!config.discord.enabled) {
            return
        }
        if (VanishAPI.isVanished(event.player) && event.joinMessage() !is TextComponent) {
            return
        }

        val worldName = event.player.world.name
        val key = WorldChatSystem.keyForWorld(worldName)
        if (!reports(worldName, key)) {
            return
        }

        val playerPartString = ChatUtil.getPlayerPartString(event.player)
        val message = "$playerPartString has joined the game. ${onlineCount(key)} player(s) online."

        scope.launch {
            discordBridgeLock.read { discordBridge?.sendEmbed(message, event.player.uniqueId, 0x00FF00, key) }
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        if (!config.discord.enabled) {
            return
        }
        if (VanishAPI.isVanished(event.player)) {
            return
        }

        val worldName = event.player.world.name
        val key = WorldChatSystem.keyForWorld(worldName)
        if (!reports(worldName, key)) {
            return
        }

        val playerPartString = ChatUtil.getPlayerPartString(event.player)
        val message = "$playerPartString has left the game. ${onlineCount(key) - 1} player(s) online."

        scope.launch {
            discordBridgeLock.read { discordBridge?.sendEmbed(message, event.player.uniqueId, 0xFF0000, key) }
        }
    }

    // Game players are teleported in rather than connecting, so without this a game channel only sees leaves.
    @EventHandler
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        if (!config.discord.enabled) {
            return
        }
        if (VanishAPI.isVanished(event.player)) {
            return
        }

        // Only the game channels track a per world population; general counts the whole server, which
        // a world change does not alter.
        val fromKey = WorldChatSystem.keyForWorld(event.from.name)
        val toKey = WorldChatSystem.keyForWorld(event.player.world.name)
        if (fromKey == toKey) {
            return
        }

        val playerPartString = ChatUtil.getPlayerPartString(event.player)
        val leftMessage = fromKey?.let { "$playerPartString has left the game. ${onlineCount(it)} player(s) online." }
        val joinedMessage = toKey?.let { "$playerPartString has joined the game. ${onlineCount(it)} player(s) online." }

        scope.launch {
            discordBridgeLock.read {
                if (leftMessage != null) discordBridge?.sendEmbed(leftMessage, event.player.uniqueId, 0xFF0000, fromKey)
                if (joinedMessage != null)
                    discordBridge?.sendEmbed(joinedMessage, event.player.uniqueId, 0x00FF00, toKey)
            }
        }
    }

    @EventHandler
    fun onKick(event: PlayerKickEvent) {
        if (!config.discord.enabled) {
            return
        }
        if (VanishAPI.isVanished(event.player)) {
            return
        }

        val worldName = event.player.world.name
        val key = WorldChatSystem.keyForWorld(worldName)
        if (!reports(worldName, key)) {
            return
        }

        val playerPartString = ChatUtil.getPlayerPartString(event.player)

        val reason = PlainTextComponentSerializer.plainText().serialize(event.reason())
        val message =
            "$playerPartString was kicked with reason: \"${reason}\". ${onlineCount(key) - 1} player(s) online."

        scope.launch {
            discordBridgeLock.read { discordBridge?.sendEmbed(message, event.player.uniqueId, 0xFF0000, key) }
        }
    }

    @EventHandler
    fun onAdvancement(event: PlayerAdvancementDoneEvent) {
        if (!config.discord.enabled) {
            return
        }
        if (VanishAPI.isVanished(event.player)) {
            return
        }

        val worldName = event.player.world.name
        val key = WorldChatSystem.keyForWorld(worldName)
        if (!reports(worldName, key)) {
            return
        }

        val playerPartString = ChatUtil.getPlayerPartString(event.player)

        val advancementTitleKey = event.advancement.display?.title() ?: return
        val advancementTitle = PlainTextComponentSerializer.plainText().serialize(advancementTitleKey)

        val advancementMessage =
            when (event.advancement.display?.frame()) {
                GOAL -> "has reached the goal [$advancementTitle]"
                TASK -> "has made the advancement [$advancementTitle]"
                CHALLENGE -> "has completed the challenge [$advancementTitle]"
                else -> {
                    return
                }
            }

        scope.launch {
            discordBridgeLock.read {
                discordBridge?.sendEmbed("$playerPartString $advancementMessage.", event.player.uniqueId, 0xFFFF00, key)
            }
        }
    }

    @EventHandler
    fun onBroadcast(event: BroadcastMessageEvent) {
        if (!config.discord.enabled) {
            return
        }

        val message = event.message()
        if (message !is TextComponent) {
            return
        }

        val content = message.content()
        if (content == "") {
            return
        }

        scope.launch { discordBridgeLock.read { discordBridge?.sendMessage(content, "[Server] Broadcast", null) } }
    }

    @EventHandler
    fun onChat(event: AsyncChatEvent) {
        if (event.isCancelled) return
        event.isCancelled = true

        val worldName = event.player.world.name

        scope.launch {
            val eventMessage = event.message() as TextComponent
            val chatSystem = event.player.chatSystem
            // A multi world game world replaces general chat only, so staff, premium and developer stay global.
            val effective =
                if (chatSystem === GeneralChatSystem) WorldChatSystem.forWorld(worldName) ?: chatSystem else chatSystem
            effective.sendMessage(eventMessage.content(), event.player)
        }
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        if (!config.discord.enabled) {
            return
        }
        if (VanishAPI.isVanished(event.player)) {
            return
        }

        // The in game death message is still rewritten below even when the world reports nowhere.
        val worldName = event.player.world.name
        val key = WorldChatSystem.keyForWorld(worldName)
        val report = reports(worldName, key)

        val deathMessage = event.deathMessage()
        if (deathMessage is TextComponent) {
            if (report) {
                scope.launch {
                    discordBridgeLock.read {
                        discordBridge?.sendEmbed(deathMessage.content(), event.player.uniqueId, 0xFF0000, key)
                    }
                }
            }
            return
        }

        val translatableDeathMessage = deathMessage as TranslatableComponent
        val builder = translatableDeathMessage.toBuilder()
        builder.color(TextColor.color(16755200))
        builder.append(Component.text("."))
        val nameComponent = UtilitiesOG.trueogColorize(legacyToMm(ChatUtil.getPlayerPartString(event.player)))
        builder.args(translatableDeathMessage.args().toMutableList().apply { this[0] = nameComponent })
        val newDeathMessage = builder.build()

        event.deathMessage(newDeathMessage)
        if (!report) {
            return
        }
        scope.launch {
            discordBridgeLock.read {
                discordBridge?.sendEmbed(
                    PlainTextComponentSerializer.plainText().serialize(newDeathMessage),
                    event.player.uniqueId,
                    0xFF0000,
                    key,
                )
            }
        }
    }

    @EventHandler
    fun onVanish(event: PlayerVanishStateChangeEvent) {
        val player = event.player ?: return
        if (event.isVanishing) {
            val playerQuitEvent =
                PlayerQuitEvent(
                    player,
                    Component.translatable("multiplayer.player.left", NamedTextColor.YELLOW, player.displayName()),
                    PlayerQuitEvent.QuitReason.DISCONNECTED,
                )
            JoinQuitListener().onQuit(playerQuitEvent)
            onQuit(playerQuitEvent)
        } else {
            val playerJoinEvent = PlayerJoinEvent(player, Component.text(""))
            JoinQuitListener().onJoin(playerJoinEvent)
            onJoin(playerJoinEvent)
        }
    }
}
