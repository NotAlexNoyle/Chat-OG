# Chat-OG
Chat plugin for [TrueOG](https://github.com/true-og/true-og)

## Features
- Adds LuckPerms prefix, Unions-OG tag, and LuckPerms suffix to chat messages.
- Formats chat messages with MiniMessage if the player sending them has the `chat-og.color` permission, with legacy `&` and `§` color code support.
- Translates chat messages using any OpenAI(-compatible) API. Click any chat or Discord message to translate it on-demand, and run command `/translatesettings <language>` to pick your preferred language (preferences persisted in KeyDB).
- Discord bridge with custom emoji (Minecraft → Discord), animated NQN emoji support, clickable attachment links, and Unicode emoji-to-shortcode conversion (Emoji 17.0).
- Multi-channel chat: general (`/g` / `/gc`), staff (`/s` / `/sc`), premium (`/p` / `/pc`) and developer (`/d` / `/dc`), each with its own Discord webhook.
- Multi world game chat: every world of a game gets its own chat, and all of a game's worlds share one Discord channel.
- Private messaging (`/msg`, `/whisper`, `/pm`) with `/r` and `/reply` shortcuts, working across every world.
- Forwards joins, quits, kicks, advancements, deaths and broadcasts to Discord, with full Vanish-OG awareness so vanished players never leak into Discord.
- LuckPerms-driven Discord role color mapping so that player rank based formatting permissions carry over to Discord.
- Pings in chat alert the mentioned player. Works both ways from Minecraft <-> Discord.
- Censor `@everyone`, `@here` and role mentions on the Discord side.
- API for other plugins to send messages through the Discord bridge and to style multi world game chat.
- Run command `/chatconfigreload` to fully reload the config and the Discord bridge at runtime.

## Multi world games

A game is a set of worlds sharing one Discord channel. Worlds are matched by name: `HB1-hub` is a
lobby, `HB1-Ancient_Plateau` is a live game world, and both belong to the `HB` channel.

```yaml
discord:
  games:
    HB:
      enabled: true
      channelId: "..."
      webhook: "https://discord.com/api/webhooks/..."

  # %id% is the lobby id, for example HB1.
  gameLobbyLabel: "[%id% - Lobby]"
  gameWorldLabel: "[%id% - In Game]"
```

- Chat in a game world reaches only that world, plus the game's Discord channel.
- Joins, quits, kicks, deaths and advancements follow the same routing. The main world and its nether
  and end keep using the general channel; any other world is not reported.
- Staff, premium and developer chat, private messages and `/broadcast` stay global in every world.
- `games` ships empty, so nothing changes until it is filled in.

## API

Add Chat-OG as a submodule:

```bash
git submodule add https://github.com/true-og/Chat-OG libs/Chat-OG
```

`build.gradle.kts`:

```kotlin
extra["kotlinAttribute"] = Attribute.of("kotlin-tag", Boolean::class.javaObjectType)
val kotlinAttribute: Attribute<Boolean> by rootProject.extra

dependencies {
    compileOnlyApi(project(":libs:Chat-OG")) { attributes { attribute(kotlinAttribute, true) } }
}
```

`plugin.yml`:

```yaml
softdepend:
  - Chat-OG
```

### Styling a game's chat

Chat-OG decides who receives a message; your plugin decides how it looks. Register one formatter per
game key, the same key used under `discord.games`.

Kotlin:

```kotlin
ChatOGAPI.setFormatter("HB") { sender, message, worldName, lobbyId ->
    Component.join(
        JoinConfiguration.noSeparators(),
        UtilitiesOG.trueogColorize("&9${sender.name}&8 » &r"),
        message,
    )
}
```

Java:

```java
ChatOGAPI.setFormatter("HB", (sender, message, worldName, lobbyId) ->
        Component.join(JoinConfiguration.noSeparators(),
                UtilitiesOG.trueogColorize("&9" + sender.getName() + "&8 » &r"),
                message));
```

`message` is already blocklist checked, colorized and permission gated, so build around it instead of
turning it back into a string. `trueogColorize` accepts both modern and legacy color codes.

Return `null` to use Chat-OG's default format, or `Component.empty()` to drop the message. A
formatter that throws is logged and falls back to the default, so it cannot break chat.

### `ChatOGAPI`

| Method | Purpose |
| --- | --- |
| `isAvailable()` | Whether Chat-OG is enabled and ready. |
| `setFormatter(key, formatter)` | Sets a game's chat formatter. Pass `null` to clear it. |
| `getLobbyId(worldName)` | The lobby id for a world, e.g. `HB1`, or `null` if it is not a game world. |
| `broadcast(worldName, message)` | Sends a message to everyone in a world, in game only. |

All of them are safe to call before Chat-OG loads.

### Sending to Discord

`ChatAPI` posts to the built-in channels and is registered with Bukkit's `ServicesManager`:

```java
ChatAPI api = Bukkit.getServicesManager().getRegistration(ChatAPI.class).getProvider();
api.sendMessage("Hello", "SomeName", uuid);
```

It also offers `sendStaffMessage`, `sendPremiumMessage`, `sendMessageWithBot` and `sendEmbed`.

## Building
```./gradlew clean build eclipse --warning-mode all```

## Emoji Converter Credits
- https://github.com/mathiasbynens/emoji-test-regex-pattern
- https://github.com/amio/emoji.json/blob/HEAD/scripts/gen.js (this project is using a modified version, it's in the repository's root folder also called `gen.js`)
