package nl.skbotnl.chatog.util

import dev.minn.jda.ktx.coroutines.await
import java.util.*
import kotlin.concurrent.read
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.minimessage.Context
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.trueog.utilitiesog.UtilitiesOG
import nl.skbotnl.chatog.ChatOG.Companion.blocklistManager
import nl.skbotnl.chatog.ChatOG.Companion.config
import nl.skbotnl.chatog.ChatOG.Companion.discordBridge
import nl.skbotnl.chatog.ChatOG.Companion.discordBridgeLock
import nl.skbotnl.chatog.ChatOG.Companion.plugin
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Player

internal object ChatUtil {
    fun getPlayerPartString(player: Player, includeSuffix: Boolean = false): String {
        var playerPart =
            "${PlayerUtils.getPrefix(player.uniqueId)}${player.name}${if (includeSuffix) PlayerUtils.getSuffix(player.uniqueId) else ""}"

        val unionTag =
            MiniMessage.miniMessage().serialize(UtilitiesOG.trueogExpand("<simpleunions_union_color_tag>", player))
        val unionPlainTag = UtilitiesOG.stripFormatting(unionTag)
        if (unionPlainTag.isNotEmpty() && unionPlainTag != "None") {
            playerPart = legacyToMm("&8[$unionTag&8] ") + playerPart
        }

        return playerPart
    }

    val mentionRegex = Regex("@([A-Za-z0-9_]{3,16})")

    fun dingForMentions(dontDingFor: UUID, component: Component) {
        val messageContent = PlainTextComponentSerializer.plainText().serialize(component)

        val namesToMention = mutableListOf<String>()
        mentionRegex.findAll(messageContent).iterator().forEach { namesToMention += it.groups[1]!!.value.lowercase() }

        for (player in Bukkit.getOnlinePlayers()) {
            if (player.uniqueId == dontDingFor) continue
            if (player.name.lowercase() !in namesToMention) continue
            player.playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
        }
    }

    private var translateTimeout: MutableMap<UUID, Long> = HashMap()

    fun getTranslateTimeout(uuid: UUID): Long {
        val cooldown: Long
        if (!translateTimeout.containsKey(uuid)) {
            translateTimeout[uuid] = System.currentTimeMillis()
        }
        if (System.currentTimeMillis() - translateTimeout[uuid]!! > 1000) {
            translateTimeout[uuid] = System.currentTimeMillis()
            cooldown = 0
        } else {
            cooldown = System.currentTimeMillis() - translateTimeout[uuid]!!
        }
        return cooldown
    }

    private val legacyToMmMap =
        mapOf(
            "0" to "<black>",
            "1" to "<dark_blue>",
            "2" to "<dark_green>",
            "3" to "<dark_aqua>",
            "4" to "<dark_red>",
            "5" to "<dark_purple>",
            "6" to "<gold>",
            "7" to "<gray>",
            "8" to "<dark_gray>",
            "9" to "<blue>",
            "a" to "<green>",
            "b" to "<aqua>",
            "c" to "<red>",
            "d" to "<light_purple>",
            "e" to "<yellow>",
            "f" to "<white>",
            "k" to "<obfuscated>",
            "l" to "<bold>",
            "m" to "<strikethrough>",
            "n" to "<underlined>",
            "o" to "<italic>",
            "r" to "<reset>",
            "*" to "<rainbow>",
        )

    val legacyRegex = Regex("[§&]([0-9a-fk-or*])", RegexOption.IGNORE_CASE)

    fun legacyToMm(text: String): String {
        return legacyRegex.replace(text) { legacyToMmMap[it.groupValues[1].lowercase()] ?: it.value }
    }

    fun stripFormatting(text: String): String {
        return MiniMessage.miniMessage().stripTags(legacyRegex.replace(text, ""))
    }

    private val getHandle = Regex("@([a-z0-9_.]{2,32})")

    suspend fun convertMentions(text: String): String {
        val guild = discordBridgeLock.read { discordBridge?.getGuildById(config.discord.guildId!!) }
        if (guild == null) {
            plugin.logger.warning("Guild was null")
            return text
        }

        var tempText = text
        val members = guild.loadMembers().await()

        getHandle.findAll(tempText).iterator().forEach { handle ->
            for (member in members) {
                if (member.user.name.equals(handle.groupValues[1], ignoreCase = true)) {
                    tempText = tempText.replace(handle.value, member.asMention)
                }
            }
        }

        return tempText
    }

    private val urlRegex = Regex("(.*?)((?:https?://)?[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(?:/?[a-zA-Z]*)+)(.*)")

    val noColorMm =
        MiniMessage.builder()
            .tags(TagResolver.builder().build())
            .editTags { b -> b.tag("a", ChatUtil::createA) }
            .build()

    val colorMm =
        MiniMessage.builder()
            .tags(
                TagResolver.builder()
                    .resolver(StandardTags.color())
                    .resolver(StandardTags.decorations())
                    .resolver(StandardTags.color())
                    .resolver(StandardTags.reset())
                    .resolver(StandardTags.rainbow())
                    .resolver(StandardTags.gradient())
                    .build()
            )
            .editTags { b -> b.tag("a", ChatUtil::createA) }
            .build()

    fun createA(args: ArgumentQueue, @Suppress("unused") ctx: Context): Tag {
        val link = args.popOr("The <a> tag requires exactly one argument, the link to open").value()

        return Tag.styling(
            TextColor.color(34, 100, 255),
            ClickEvent.openUrl(
                if (!link.startsWith("http")) {
                    "https://"
                } else {
                    ""
                } + link
            ),
            HoverEvent.showText(UtilitiesOG.trueogColorize("<green>Click to open link")),
        )
    }

    fun processText(text: String, player: Player) = processText(text, player, null, false)

    fun processText(text: String, username: String, color: Boolean) = processText(text, null, username, color)

    private fun processText(text: String, player: Player?, username: String?, color: Boolean): Component? {
        val words: MutableList<String> = mutableListOf()
        val canUseFormatting = player?.hasPermission("chat-og.color") == true || color
        val textToProcess = if (canUseFormatting) legacyToMm(text) else stripFormatting(text)

        textToProcess.split(" ").forEach { word ->
            val url = urlRegex.find(word)

            if (url != null) {
                val link = url.groups[2]?.value ?: ""
                if (blocklistManager?.checkUrl(link) == true) {
                    player?.sendMessage(
                        UtilitiesOG.trueogColorize(
                            "${config.prefix}<reset>: <red>WARNING: You are not allowed to post links like that here."
                        )
                    )
                    for (onlinePlayer in Bukkit.getOnlinePlayers()) {
                        if (onlinePlayer.hasPermission("group.moderator")) {
                            onlinePlayer.sendMessage(
                                UtilitiesOG.trueogColorize(
                                    "${config.prefix}<reset>: ${player?.name ?: username} has posted a disallowed link: ${
                                        word.replace(
                                            ".",
                                            "[dot]",
                                        )
                                    }."
                                )
                            )
                        }
                    }
                    return null
                }
                val before = url.groups[1]?.value ?: ""
                val after = url.groups[3]?.value ?: ""
                words += "$before<a:$link>$link</a>$after"
            } else {
                words += word
            }
        }

        return if (canUseFormatting) {
            colorMm.deserialize(words.joinToString(" "))
        } else {
            noColorMm.deserialize(words.joinToString(" "))
        }
    }

    private val emojiRegex = Regex(":([a-zA-Z0-9_]*?):")

    fun convertEmojis(text: String): String {
        var discordMessageString = text

        val guildEmojis = discordBridgeLock.read { discordBridge?.getGuildById(config.discord.guildId!!)?.emojis }
        if (guildEmojis == null) {
            plugin.logger.warning("Guild emojis was null")
        }

        if (guildEmojis != null) {
            emojiRegex
                .findAll(text)
                .iterator()
                .asSequence()
                .distinctBy { it.value }
                .forEach {
                    guildEmojis.forEach { emoji ->
                        if (emoji.name == it.groupValues[1]) {
                            val replaceWith = "<${if (emoji.isAnimated) "a" else ""}:${it.groupValues[1]}:${emoji.id}>"
                            discordMessageString = discordMessageString.replace(it.value, replaceWith)
                        }
                    }
                }
        }

        return discordMessageString
    }

    fun stripGroupMentions(text: String): String {
        var tempText = text.replace("@everyone", "@\u200Eeveryone", false)
        tempText = tempText.replace("@here", "@\u200Ehere", false)

        tempText = tempText.replace("(<@&)(\\d*>)".toRegex(), "$1\u200E$2")

        return tempText
    }

    fun recolorComponent(component: Component, color: TextColor): Component {
        var newComponent = component
        if (component.color() == null) {
            newComponent = component.color(color)
        }

        val children = newComponent.children().map { recolorComponent(it, color) }
        newComponent.children(children)
        return newComponent
    }
}
