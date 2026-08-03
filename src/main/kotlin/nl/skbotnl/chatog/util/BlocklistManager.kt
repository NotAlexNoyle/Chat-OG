package nl.skbotnl.chatog.util

import java.io.IOException
import java.net.URI
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.skbotnl.chatog.ChatOG.Companion.config
import nl.skbotnl.chatog.ChatOG.Companion.plugin
import nl.skbotnl.chatog.ChatOG.Companion.scope

internal class BlocklistManager {
    private val lock = Any()
    private val blockedDomainTrie = DomainTrie()

    init {
        plugin.logger.info("Loading the blocklists...")
        refresh()

        scope.launch {
            while (true) {
                delay(1.days)
                plugin.logger.info("Refreshing the blocklists...")
                refresh()
            }
        }
    }

    private fun refresh() {
        synchronized(lock) {
            blockedDomainTrie.clear()
            config.blocklist.blocklists.forEach { blocklist ->
                try {
                    URI(blocklist).toURL().openStream().use { input ->
                        input.bufferedReader().use { bufferedReader ->
                            bufferedReader.lines().forEach { if (!it.startsWith("#")) blockedDomainTrie.insert(it) }
                        }
                    }
                } catch (_: IOException) {
                    plugin.logger.severe("Failed to load the blocklists")
                    return
                }
            }
        }
        plugin.logger.info("Loaded the blocklists!")
    }

    private val urlRegex = Regex("(?:https?://)?([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})")

    fun checkUrl(url: String): Boolean {
        val match = urlRegex.find(url) ?: return false
        val baseUrl = match.groups[1]?.value ?: return false
        return synchronized(lock) { blockedDomainTrie.contains(baseUrl) }
    }
}
