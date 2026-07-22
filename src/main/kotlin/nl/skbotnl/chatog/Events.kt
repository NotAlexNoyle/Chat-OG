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
import nl.skbotnl.chatog.util.ChatUtil
import nl.skbotnl.chatog.util.ChatUtil.legacyToMm
import nl.skbotnl.chatog.util.PlayerExtensions.chatSystem
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerAdvancementDoneEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.server.BroadcastMessageEvent
import xyz.jpenilla.announcerplus.listener.JoinQuitListener

internal class Events : Listener {
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        if (!config.discord.enabled) {
            return
        }
        if (VanishAPI.isVanished(event.player) && event.joinMessage() !is TextComponent) {
            return
        }

        val playerPartString = ChatUtil.getPlayerPartString(event.player)

        scope.launch {
            discordBridgeLock.read {
                discordBridge?.sendEmbed(
                    "$playerPartString has joined the game. ${
                        Bukkit.getOnlinePlayers().count()
                    } player(s) online.",
                    event.player.uniqueId,
                    0x00FF00,
                )
            }
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

        val playerPartString = ChatUtil.getPlayerPartString(event.player)

        scope.launch {
            discordBridgeLock.read {
                discordBridge?.sendEmbed(
                    "$playerPartString has left the game. ${
                        Bukkit.getOnlinePlayers().count() - 1
                    } player(s) online.",
                    event.player.uniqueId,
                    0xFF0000,
                )
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

        val playerPartString = ChatUtil.getPlayerPartString(event.player)

        val reason = PlainTextComponentSerializer.plainText().serialize(event.reason())

        scope.launch {
            discordBridgeLock.read {
                discordBridge?.sendEmbed(
                    "$playerPartString was kicked with reason: \"${reason}\". ${
                        Bukkit.getOnlinePlayers().count() - 1
                    } player(s) online.",
                    event.player.uniqueId,
                    0xFF0000,
                )
            }
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
                discordBridge?.sendEmbed("$playerPartString $advancementMessage.", event.player.uniqueId, 0xFFFF00)
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

        scope.launch {
            val eventMessage = event.message() as TextComponent
            event.player.chatSystem.sendMessage(eventMessage.content(), event.player)
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

        val deathMessage = event.deathMessage()
        if (deathMessage is TextComponent) {
            scope.launch {
                discordBridgeLock.read {
                    discordBridge?.sendEmbed(deathMessage.content(), event.player.uniqueId, 0xFF0000)
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
        scope.launch {
            discordBridgeLock.read {
                discordBridge?.sendEmbed(
                    PlainTextComponentSerializer.plainText().serialize(newDeathMessage),
                    event.player.uniqueId,
                    0xFF0000,
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
