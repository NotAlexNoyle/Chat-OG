package nl.skbotnl.chatog

import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlinx.coroutines.*
import net.luckperms.api.LuckPerms
import nl.skbotnl.chatog.chatsystem.command.DeveloperChat
import nl.skbotnl.chatog.chatsystem.command.GeneralChat
import nl.skbotnl.chatog.chatsystem.command.PremiumChat
import nl.skbotnl.chatog.chatsystem.command.StaffChat
import nl.skbotnl.chatog.config.Config
import nl.skbotnl.chatog.config.ConfigModel
import nl.skbotnl.chatog.config.command.ChatConfigReload
import nl.skbotnl.chatog.discord.DiscordBridge
import nl.skbotnl.chatog.messaging.command.PrivateMessage
import nl.skbotnl.chatog.messaging.command.Reply
import nl.skbotnl.chatog.translation.LanguageDatabase
import nl.skbotnl.chatog.translation.OpenAITranslator
import nl.skbotnl.chatog.translation.command.TranslateMessage
import nl.skbotnl.chatog.translation.command.TranslateSettings
import nl.skbotnl.chatog.translation.command.TranslateSettingsTabCompleter
import nl.skbotnl.chatog.util.BlocklistManager
import nl.skbotnl.chatog.util.EmojiConverter
import org.bukkit.Bukkit
import org.bukkit.plugin.ServicePriority
import org.bukkit.plugin.java.JavaPlugin

internal class ChatOG : JavaPlugin() {
    companion object {
        val scope =
            CoroutineScope(
                Dispatchers.Default +
                    SupervisorJob() +
                    CoroutineExceptionHandler { _, throwable ->
                        val logger = if (::plugin.isInitialized) plugin.logger else Bukkit.getLogger()
                        logger.severe(
                            "Uncaught exception in Chat-OG coroutine: ${throwable.javaClass.simpleName}: ${throwable.message}"
                        )
                        throwable.printStackTrace()
                    }
            )

        lateinit var plugin: JavaPlugin
        lateinit var config: ConfigModel
        var blocklistManager: BlocklistManager? = null
        lateinit var luckPerms: LuckPerms
        lateinit var languageDatabase: LanguageDatabase
        var translator: OpenAITranslator? = null
        var discordBridge: DiscordBridge? = null
        val discordBridgeLock = ReentrantReadWriteLock()
        var lastMessagedMap: MutableMap<UUID, UUID> = HashMap()

        // Only true once onEnable has fully succeeded, so the API never runs against a half started plugin.
        @Volatile private var ready = false

        fun isReady() = ready

        fun isConfigInitialized() = ::config.isInitialized

        fun isLanguageDatabaseInitialized() = ::languageDatabase.isInitialized
    }

    override fun onEnable() {
        plugin = this

        Companion.config =
            Config.loadConfig()
                ?: run {
                    Bukkit.getPluginManager().disablePlugin(this)
                    return
                }

        if (Companion.config.blocklist.enabled) {
            blocklistManager = BlocklistManager()
        }

        translator =
            if (Companion.config.openai.enabled) {
                if (Companion.config.openai.baseUrl == null) {
                    this.logger.warning(
                        "You have enabled OpenAI translation but have not set up the base url, not enabling the translator"
                    )
                    null
                } else if (Companion.config.openai.apiKey == null) {
                    this.logger.warning(
                        "You have enabled OpenAI translation but have not set up the api key, not enabling the translator"
                    )
                    null
                } else {
                    OpenAITranslator()
                }
            } else null

        languageDatabase = LanguageDatabase()
        if (languageDatabase.testConnection()) {
            logger.severe("Could not connect to Redis")
            Bukkit.getPluginManager().disablePlugin(this)
            return
        }

        scope.launch { EmojiConverter.load() }

        val luckPermsProvider = Bukkit.getServicesManager().getRegistration(LuckPerms::class.java)
        if (luckPermsProvider == null) {
            this.logger.severe("LuckPerms API is null, quitting...")
            Bukkit.getPluginManager().disablePlugin(this)
            return
        }
        luckPerms = luckPermsProvider.provider

        this.server.pluginManager.registerEvents(Events(), this)
        this.getCommand("translatemessage")?.setExecutor(TranslateMessage())
        this.getCommand("translatesettings")?.setExecutor(TranslateSettings())
        this.getCommand("translatesettings")?.tabCompleter = TranslateSettingsTabCompleter()
        this.getCommand("chatconfigreload")?.setExecutor(ChatConfigReload())
        this.getCommand("gc")?.setExecutor(GeneralChat())
        this.getCommand("g")?.setExecutor(GeneralChat())
        this.getCommand("sc")?.setExecutor(StaffChat())
        this.getCommand("s")?.setExecutor(StaffChat())
        this.getCommand("pc")?.setExecutor(PremiumChat())
        this.getCommand("p")?.setExecutor(PremiumChat())
        this.getCommand("dc")?.setExecutor(DeveloperChat())
        this.getCommand("d")?.setExecutor(DeveloperChat())
        this.getCommand("msg")?.setExecutor(PrivateMessage())
        this.getCommand("whisper")?.setExecutor(PrivateMessage())
        this.getCommand("pm")?.setExecutor(PrivateMessage())
        this.getCommand("reply")?.setExecutor(Reply())
        this.getCommand("r")?.setExecutor(Reply())

        if (Companion.config.discord.enabled) {
            scope.launch { discordBridgeLock.write { discordBridge = DiscordBridge.create() } }
        }

        val chatAPI = ChatAPI()
        this.server.servicesManager.register(ChatAPI::class.java, chatAPI, this, ServicePriority.Normal)

        ready = true
    }

    override fun onDisable() {
        ready = false

        if (isConfigInitialized()) {
            if (discordBridge != null) {
                discordBridgeLock.read {
                    val discordBridge = discordBridge
                    discordBridge!!.sendMessageWithBot(Companion.config.discord.serverHasStoppedMessage)
                    discordBridge.shutdownNow()
                }
            }
        }

        if (isLanguageDatabaseInitialized()) {
            languageDatabase.shutdown()
        }

        scope.cancel()

        runBlocking { scope.coroutineContext[Job]?.join() }
    }
}
