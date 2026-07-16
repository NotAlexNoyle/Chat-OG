package nl.skbotnl.chatog.chatsystem

import java.util.*
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.trueog.utilitiesog.UtilitiesOG
import nl.skbotnl.chatog.translation.command.TranslateMessage
import nl.skbotnl.chatog.util.ChatUtil
import nl.skbotnl.chatog.util.PlayerUtils
import org.bukkit.entity.Player

internal abstract class ChatSystem {
    abstract val prefix: String?
    abstract val audience: Audience
    abstract val name: String

    companion object {
        private val chatItemResolver: java.lang.reflect.Method? by lazy {
            try {
                Class.forName("me.dadus33.chatitem.chatmanager.ChatManager")
                    .getMethod("replaceItemsWithNames", String::class.java)
            } catch (_: Throwable) {
                null
            }
        }

        fun resolveChatItems(text: String): String {
            val method = chatItemResolver ?: return text
            return try {
                method.invoke(null, text) as? String ?: text
            } catch (_: Throwable) {
                text
            }
        }
    }

    abstract fun sendDiscordMessage(text: String, playerPartString: String, uuid: UUID)

    fun sendMessage(text: String, player: Player) {
        var playerPartString = ChatUtil.getPlayerPartString(player)
        playerPartString = listOfNotNull(prefix, playerPartString).joinToString(" | ")

        val discordPlayerPartString =
            listOfNotNull(prefix, ChatUtil.getPlayerPartString(player, includeSuffix = true)).joinToString(" | ")

        sendDiscordMessage(resolveChatItems(text), discordPlayerPartString, player.uniqueId)

        val messageComponent =
            ChatUtil.processText(text, player)?.color(PlayerUtils.getMessageColor(player.uniqueId)) ?: return

        val chatComponent =
            UtilitiesOG.trueogColorize(
                ChatUtil.legacyToMm("$playerPartString<reset>${PlayerUtils.getSuffix(player.uniqueId)} &7> ")
            )

        var textComponent = Component.join(JoinConfiguration.noSeparators(), chatComponent, messageComponent)
        textComponent =
            textComponent.hoverEvent(
                HoverEvent.hoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    UtilitiesOG.trueogColorize("<green>Click to translate this message"),
                )
            )

        val randomUUID = UUID.randomUUID()
        textComponent =
            textComponent.clickEvent(
                ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, "/translatemessage $randomUUID 1")
            )

        TranslateMessage.chatMessages[randomUUID] = TranslateMessage.SentChatMessage(text, player)

        audience.sendMessage(textComponent)

        ChatUtil.dingForMentions(player.uniqueId, messageComponent)
    }
}
