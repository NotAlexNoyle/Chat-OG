package nl.skbotnl.chatog.config.discord

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls

data class DiscordConfig
@JsonCreator
constructor(
    @param:JsonProperty("enabled") val enabled: Boolean,
    @param:JsonProperty("botToken") val botToken: String?,
    @param:JsonProperty("guildId") val guildId: String?,
    @param:JsonProperty("status") val status: String,
    @param:JsonProperty("serverHasStartedMessage") val serverHasStartedMessage: String,
    @param:JsonProperty("serverHasStoppedMessage") val serverHasStoppedMessage: String,
    @param:JsonProperty("general") val general: DiscordGeneralChatConfig,
    @param:JsonProperty("staff") val staff: DiscordRoleChatConfig,
    @param:JsonProperty("premium") val premium: DiscordRoleChatConfig,
    @param:JsonProperty("developer") val developer: DiscordRoleChatConfig,
    // Multi world game channels keyed by lobby prefix, e.g. "HB" for the worlds HB1-hub and HB1-<map>.
    @param:JsonProperty("games")
    @field:JsonSetter(nulls = Nulls.AS_EMPTY)
    val games: Map<String, DiscordRoleChatConfig>,
    // Null falls back to the defaults in WorldChatSystem so older configs keep loading.
    @param:JsonProperty("gameLobbyLabel") val gameLobbyLabel: String?,
    @param:JsonProperty("gameWorldLabel") val gameWorldLabel: String?,
    @param:JsonProperty("listCommandName") val listCommandName: String,
    @param:JsonProperty("listCommandText") val listCommandText: String,
    @param:JsonProperty("useColorCodeRoles") val useColorCodeRoles: Boolean,
    @param:JsonProperty("colorCodeRoles") @field:JsonSetter(nulls = Nulls.AS_EMPTY) val colorCodeRoles: List<String>,
    @param:JsonProperty("roles") @field:JsonSetter(nulls = Nulls.AS_EMPTY) val roles: Map<String, DiscordRoleConfig>,
)
