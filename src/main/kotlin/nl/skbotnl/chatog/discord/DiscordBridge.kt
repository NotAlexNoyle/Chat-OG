package nl.skbotnl.chatog.discord

import club.minnced.discord.webhook.WebhookClient
import club.minnced.discord.webhook.send.WebhookEmbed
import club.minnced.discord.webhook.send.WebhookEmbedBuilder
import club.minnced.discord.webhook.send.WebhookMessageBuilder
import dev.minn.jda.ktx.events.listener
import dev.minn.jda.ktx.generics.getChannel
import dev.minn.jda.ktx.jdabuilder.cache
import dev.minn.jda.ktx.jdabuilder.intents
import dev.minn.jda.ktx.jdabuilder.light
import java.util.*
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.MemberCachePolicy
import net.dv8tion.jda.api.utils.cache.CacheFlag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.trueog.utilitiesog.UtilitiesOG
import nl.skbotnl.chatog.ChatOG.Companion.config
import nl.skbotnl.chatog.ChatOG.Companion.plugin
import nl.skbotnl.chatog.chatsystem.WorldChatSystem
import nl.skbotnl.chatog.translation.command.TranslateMessage
import nl.skbotnl.chatog.util.ChatUtil
import nl.skbotnl.chatog.util.EmojiConverter
import org.bukkit.Bukkit
import org.bukkit.entity.Player

internal class DiscordBridge private constructor() {
    private lateinit var jda: JDA
    private lateinit var webhook: WebhookClient
    private var staffWebhook: WebhookClient? = null
    private var premiumWebhook: WebhookClient? = null
    private var developerWebhook: WebhookClient? = null
    private val gameWebhooks = mutableMapOf<String, WebhookClient>()

    companion object {
        // The discord.games key whose channel this is, or null when it is not a game channel.
        private fun gameKeyOf(channelId: String): String? =
            config.discord.games.entries.firstOrNull { it.value.enabled && it.value.channelId == channelId }?.key

        fun create(): DiscordBridge? {
            val discordBridge = DiscordBridge()
            if (config.discord.general.webhook != null) {
                try {
                    discordBridge.webhook = WebhookClient.withUrl(config.discord.general.webhook!!.toString())
                } catch (_: IllegalArgumentException) {
                    plugin.logger.warning("Config option \"webhook\" is invalid")
                }
            } else {
                plugin.logger.severe("You have enabled Discord but have not set up the webhook")
                return null
            }
            if (config.discord.staff.webhook != null) {
                try {
                    discordBridge.staffWebhook = WebhookClient.withUrl(config.discord.staff.webhook!!.toString())
                } catch (_: IllegalArgumentException) {
                    plugin.logger.warning("Config option \"staffWebhook\" is invalid")
                }
            } else if (config.discord.staff.enabled) {
                plugin.logger.warning("You have enabled staff Discord but have not set up the staff webhook")
            }
            if (config.discord.premium.webhook != null) {
                try {
                    discordBridge.premiumWebhook = WebhookClient.withUrl(config.discord.premium.webhook!!.toString())
                } catch (_: IllegalArgumentException) {
                    plugin.logger.warning("Config option \"premiumWebhook\" is invalid")
                }
            } else if (config.discord.premium.enabled) {
                plugin.logger.warning("You have enabled premium Discord but have not set up the premium webhook")
            }
            if (config.discord.developer.webhook != null) {
                try {
                    discordBridge.developerWebhook =
                        WebhookClient.withUrl(config.discord.developer.webhook!!.toString())
                } catch (_: IllegalArgumentException) {
                    plugin.logger.warning("Config option \"developerWebhook\" is invalid")
                }
            } else if (config.discord.developer.enabled) {
                plugin.logger.warning("You have enabled developer Discord but have not set up the developer webhook")
            }
            for ((key, game) in config.discord.games) {
                if (!game.enabled) continue
                if (game.channelId == null) {
                    plugin.logger.warning(
                        "You have enabled game Discord \"$key\" but have not set up its channelId, so Discord to Minecraft will not work for it"
                    )
                }
                if (game.webhook == null) {
                    plugin.logger.warning("You have enabled game Discord \"$key\" but have not set up its webhook")
                    continue
                }
                try {
                    discordBridge.gameWebhooks[key] = WebhookClient.withUrl(game.webhook.toString())
                } catch (_: IllegalArgumentException) {
                    plugin.logger.warning("Config option \"games.$key.webhook\" is invalid")
                }
            }
            // Two channels sharing an id would silently steal each other's messages, so say so loudly.
            val channelIds =
                listOfNotNull(
                    config.discord.general.channelId,
                    config.discord.staff.channelId.takeIf { config.discord.staff.enabled },
                    config.discord.premium.channelId.takeIf { config.discord.premium.enabled },
                    config.discord.developer.channelId.takeIf { config.discord.developer.enabled },
                ) + config.discord.games.values.filter { it.enabled }.mapNotNull { it.channelId }
            channelIds
                .groupingBy { it }
                .eachCount()
                .filterValues { it > 1 }
                .keys
                .forEach { plugin.logger.severe("Discord channel id \"$it\" is configured for more than one channel") }

            if (config.discord.botToken == null) {
                plugin.logger.severe("You have enabled Discord but have not set up the bot token")
                return null
            }
            discordBridge.jda =
                light(config.discord.botToken!!, enableCoroutines = true) {
                    intents += listOf(GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT)
                    cache += listOf(CacheFlag.EMOJI)
                    setMemberCachePolicy(MemberCachePolicy.ALL)
                }

            discordBridge.jda.presence.setPresence(Activity.playing(config.discord.status), false)

            if (config.discord.guildId == null) {
                plugin.logger.severe("You have enabled Discord but have not set up the guild ID")
                return null
            }

            discordBridge.jda.listener<ReadyEvent> {
                discordBridge.sendMessageWithBot(config.discord.serverHasStartedMessage)
                val guild = discordBridge.jda.getGuildById(config.discord.guildId!!)
                if (guild == null) {
                    plugin.logger.severe("Guild was null")
                    return@listener
                }

                guild.upsertCommand(config.discord.listCommandName, "List all online players.").queue()
            }

            discordBridge.jda.listener<SlashCommandInteractionEvent> {
                if (it.name == config.discord.listCommandName) {
                    it.deferReply().queue()
                    try {
                        if (Bukkit.getOnlinePlayers().isEmpty()) {
                            it.hook.sendMessage("There are no players online.").queue()
                            return@listener
                        }
                        it.hook
                            .sendMessage(
                                "${
                                    config.discord.listCommandText
                                        .replace("%onlineplayers%", Bukkit.getOnlinePlayers().count().toString())
                                        .replace("%maxplayers%", Bukkit.getMaxPlayers().toString())
                                }\n${
                                    Bukkit.getOnlinePlayers().joinToString(separator = ", ") { player -> player.name }
                                }"
                            )
                            .queue()
                    } catch (_: Exception) {
                        plugin.logger.warning("Could not respond to /list interaction")
                    }
                }
            }

            discordBridge.jda.listener<MessageReceivedEvent> {
                if (
                    it.channel.id != config.discord.general.channelId &&
                        (if (config.discord.staff.enabled) it.channel.id != config.discord.staff.channelId else true) &&
                        (if (config.discord.premium.enabled) it.channel.id != config.discord.premium.channelId
                        else true) &&
                        (if (config.discord.developer.enabled) it.channel.id != config.discord.developer.channelId
                        else true) &&
                        gameKeyOf(it.channel.id) == null
                ) {
                    return@listener
                }

                if (it.author.isBot) {
                    return@listener
                }

                val message = it.message

                val user = it.author
                val member = it.member ?: return@listener
                val color = member.color
                val userColor: TextColor =
                    if (color == null) {
                        TextColor.color(153, 170, 181)
                    } else {
                        TextColor.color(color.rgb)
                    }

                val roles = member.roles
                val topRole = roles.maxByOrNull { role -> role.positionRaw }
                val sortedRoles = member.roles.sortedByDescending { role -> role.positionRaw }

                val discordComponent = Component.text("Discord: ").color(TextColor.color(88, 101, 242))
                val userComponent: TextComponent =
                    if (color == null) {
                        Component.text("@${user.name}").color(NamedTextColor.GRAY)
                    } else {
                        Component.text("[#${topRole!!.name}] @${user.name}").color(userColor)
                    }

                val messageColor =
                    if (config.discord.roles.isEmpty()) {
                        NamedTextColor.GRAY
                    } else {
                        sortedRoles.firstNotNullOfOrNull { r -> config.discord.roles[r.id] }?.messageColor
                            ?: NamedTextColor.GRAY
                    }

                val suffix = " > "

                val messageComponents = mutableListOf<Component>(UtilitiesOG.trueogColorize(suffix))

                val attachmentComponents = mutableListOf<Component>()

                if (message.stickers.isNotEmpty()) {
                    attachmentComponents += Component.text("[Sticker: ${message.stickers[0].name}]").color(messageColor)
                }

                if (message.attachments.isNotEmpty()) {
                    message.attachments.forEach { attachment ->
                        var linkComponent = Component.text(attachment.url).color(TextColor.color(34, 100, 255))

                        linkComponent =
                            linkComponent.hoverEvent(
                                HoverEvent.hoverEvent(
                                    HoverEvent.Action.SHOW_TEXT,
                                    UtilitiesOG.trueogColorize("<green>Click to open link"),
                                )
                            )

                        linkComponent =
                            linkComponent.clickEvent(ClickEvent.clickEvent(ClickEvent.Action.OPEN_URL, attachment.url))

                        attachmentComponents += linkComponent
                    }
                }

                val useColor =
                    if (config.discord.useColorCodeRoles) {
                        roles.any { role -> role.id in config.discord.colorCodeRoles }
                    } else {
                        true
                    }

                if (message.contentDisplay != "") {
                    messageComponents +=
                        ChatUtil.recolorComponent(
                            ChatUtil.processText(
                                EmojiConverter.replaceEmojisWithNames(message.contentDisplay),
                                "@${user.name}",
                                useColor,
                            ) ?: return@listener,
                            messageColor,
                        )
                }

                val contentComponent =
                    Component.join(
                        JoinConfiguration.separator(Component.text(" ")),
                        Component.join(JoinConfiguration.noSeparators(), messageComponents),
                        Component.join(JoinConfiguration.noSeparators(), attachmentComponents),
                    )

                var replyComponent = Component.text("")

                if (message.messageReference != null) {
                    val replyMessage = message.messageReference!!.message
                    if (replyMessage != null) {
                        replyComponent =
                            Component.text(
                                    "[Reply to: ${if (!replyMessage.isWebhookMessage) "@" else ""}${replyMessage.author.name}] "
                                )
                                .color(NamedTextColor.GREEN)
                    }
                }

                var messageComponent =
                    when (it.channel.id) {
                        config.discord.staff.channelId -> {
                            val staffComponent = Component.text("STAFF | ").color(NamedTextColor.RED)
                            Component.join(
                                JoinConfiguration.noSeparators(),
                                discordComponent,
                                staffComponent,
                                replyComponent,
                                userComponent,
                                contentComponent,
                            )
                        }

                        config.discord.premium.channelId -> {
                            val premiumComponent = Component.text("PREMIUM | ").color(NamedTextColor.GREEN)
                            Component.join(
                                JoinConfiguration.noSeparators(),
                                discordComponent,
                                premiumComponent,
                                replyComponent,
                                userComponent,
                                contentComponent,
                            )
                        }

                        config.discord.developer.channelId -> {
                            val developerComponent = Component.text("DEVELOPER | ").color(NamedTextColor.AQUA)
                            Component.join(
                                JoinConfiguration.noSeparators(),
                                discordComponent,
                                developerComponent,
                                replyComponent,
                                userComponent,
                                contentComponent,
                            )
                        }

                        // General and the game channels are both unlabelled.
                        else -> {
                            Component.join(
                                JoinConfiguration.noSeparators(),
                                discordComponent,
                                replyComponent,
                                userComponent,
                                contentComponent,
                            )
                        }
                    }

                messageComponent =
                    messageComponent.hoverEvent(
                        HoverEvent.hoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            UtilitiesOG.trueogColorize("<green>Click to translate this message"),
                        )
                    )

                val randomUUID = UUID.randomUUID()
                messageComponent =
                    messageComponent.clickEvent(
                        ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, "/translatemessage $randomUUID 2")
                    )

                when (it.channel.id) {
                    config.discord.staff.channelId -> {
                        for (p in Bukkit.getOnlinePlayers()) {
                            if (p.hasPermission("chat-og.staff")) {
                                p.sendMessage(messageComponent)
                            }
                        }
                    }

                    config.discord.premium.channelId -> {
                        for (p in Bukkit.getOnlinePlayers()) {
                            if (p.hasPermission("chat-og.premium")) {
                                p.sendMessage(messageComponent)
                            }
                        }
                    }

                    config.discord.developer.channelId -> {
                        for (p in Bukkit.getOnlinePlayers()) {
                            if (p.hasPermission("chat-og.developer")) {
                                p.sendMessage(messageComponent)
                            }
                        }
                    }

                    // Skips the game worlds, which have their own channel and their own chat.
                    config.discord.general.channelId -> {
                        for (p in Bukkit.getOnlinePlayers()) {
                            if (WorldChatSystem.keyForWorld(p.world.name) != null) continue
                            p.sendMessage(messageComponent)
                        }
                    }

                    // A game channel reaches only the players inside that game's worlds.
                    else -> {
                        val gameKey = gameKeyOf(it.channel.id) ?: return@listener
                        for (p in WorldChatSystem.playersForKey(gameKey)) {
                            p.sendMessage(messageComponent)
                        }
                    }
                }

                TranslateMessage.customMessages[randomUUID] =
                    TranslateMessage.SentCustomMessage(
                        message.contentDisplay,
                        member.effectiveName,
                        Component.join(JoinConfiguration.noSeparators(), discordComponent, userComponent),
                        Component.text(suffix),
                    )
            }
            return discordBridge
        }
    }

    fun shutdownNow() {
        jda.shutdownNow()
    }

    fun getGuildById(id: String): Guild? {
        return jda.getGuildById(id)
    }

    fun sendMessageWithBot(message: String) {
        if (config.discord.general.channelId == null) return

        val channel = jda.getChannel<MessageChannel>(config.discord.general.channelId!!)
        if (channel == null) {
            plugin.logger.warning("channelId has not been set or is invalid")
            return
        }

        channel.sendMessage(UtilitiesOG.stripFormatting(message)).complete()
    }

    suspend fun sendMessage(message: String, player: Player) {
        val webhookMessage =
            WebhookMessageBuilder()
                .setUsername(UtilitiesOG.stripFormatting(ChatUtil.getPlayerPartString(player, includeSuffix = true)))
                .setContent(UtilitiesOG.stripFormatting(ChatUtil.stripGroupMentions(ChatUtil.convertMentions(message))))
                .setAvatarUrl("https://minotar.net/helm/${player.uniqueId}.png")

        webhook.send(webhookMessage.build())
    }

    suspend fun sendMessage(message: String, name: String, uuid: UUID?) {
        val webhookMessage =
            WebhookMessageBuilder().apply {
                setUsername(UtilitiesOG.stripFormatting(name))
                setContent(
                    UtilitiesOG.stripFormatting(ChatUtil.stripGroupMentions((ChatUtil.convertMentions(message))))
                )
                if (uuid != null) setAvatarUrl("https://minotar.net/helm/$uuid.png")
            }

        webhook.send(webhookMessage.build())
    }

    suspend fun sendStaffMessage(message: String, name: String, uuid: UUID?) {
        if (!config.discord.staff.enabled) return

        if (staffWebhook == null) {
            plugin.logger.warning("staffWebhook has not been set or is invalid")
            return
        }

        val webhookMessage =
            WebhookMessageBuilder().apply {
                setUsername(UtilitiesOG.stripFormatting(name))
                setContent(
                    UtilitiesOG.stripFormatting(ChatUtil.stripGroupMentions((ChatUtil.convertMentions(message))))
                )
                if (uuid != null) setAvatarUrl("https://minotar.net/helm/$uuid.png")
            }

        staffWebhook!!.send(webhookMessage.build())
    }

    suspend fun sendPremiumMessage(message: String, name: String, uuid: UUID?) {
        if (!config.discord.premium.enabled) return

        if (premiumWebhook == null) {
            plugin.logger.warning("premiumWebhook has not been set or is invalid")
            return
        }

        val webhookMessage =
            WebhookMessageBuilder().apply {
                setUsername(UtilitiesOG.stripFormatting(name))
                setContent(
                    UtilitiesOG.stripFormatting(ChatUtil.stripGroupMentions((ChatUtil.convertMentions(message))))
                )
                if (uuid != null) setAvatarUrl("https://minotar.net/helm/$uuid.png")
            }

        premiumWebhook!!.send(webhookMessage.build())
    }

    suspend fun sendDeveloperMessage(message: String, name: String, uuid: UUID?) {
        if (!config.discord.developer.enabled) return

        if (developerWebhook == null) {
            plugin.logger.warning("developerWebhook has not been set or is invalid")
            return
        }

        val webhookMessage =
            WebhookMessageBuilder().apply {
                setUsername(UtilitiesOG.stripFormatting(name))
                setContent(
                    UtilitiesOG.stripFormatting(ChatUtil.stripGroupMentions((ChatUtil.convertMentions(message))))
                )
                if (uuid != null) setAvatarUrl("https://minotar.net/helm/$uuid.png")
            }

        developerWebhook!!.send(webhookMessage.build())
    }

    suspend fun sendGameMessage(key: String, message: String, name: String, uuid: UUID?) {
        // create() already warned about this once at startup, so stay quiet rather than log per message.
        val gameWebhook = gameWebhooks[key] ?: return

        val webhookMessage =
            WebhookMessageBuilder().apply {
                setUsername(UtilitiesOG.stripFormatting(name))
                setContent(
                    UtilitiesOG.stripFormatting(ChatUtil.stripGroupMentions((ChatUtil.convertMentions(message))))
                )
                if (uuid != null) setAvatarUrl("https://minotar.net/helm/$uuid.png")
            }

        gameWebhook.send(webhookMessage.build())
    }

    // A null key sends to the general channel.
    fun sendEmbed(message: String, uuid: UUID?, color: Int?, key: String? = null) {
        val target = if (key == null) webhook else gameWebhooks[key] ?: return

        var iconUrl: String? = null
        if (uuid != null) {
            iconUrl = "https://minotar.net/helm/$uuid.png"
        }

        val webhookMessage =
            WebhookEmbedBuilder().apply {
                if (color != null)
                    setColor(color)
                        .setAuthor(WebhookEmbed.EmbedAuthor(UtilitiesOG.stripFormatting(message), iconUrl, null))
            }

        target.send(webhookMessage.build())
    }
}
