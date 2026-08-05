# Changelog

NotAlexNoyle 08/05/26:

- Add multi world game chat. Worlds named `<prefix><number>-hub` or `<prefix><number>-<map>` get their
  own isolated chat and link to the Discord channel configured under `discord.games` for that prefix.
- Tell a game's worlds apart in Discord with the configurable `gameLobbyLabel` and `gameWorldLabel`.
- Route joins, quits, kicks, deaths and advancements to the world's channel. The main world and its
  nether and end keep using the general channel; any other world is not reported.
- Post a joined and left pair to a game channel when a player is teleported in or out of its worlds,
  so its player count stays balanced.
- Add `ChatOGAPI`, letting a plugin register a `WorldChatFormatter` for the worlds it owns.
- Stop relaying a message to Discord when the blocklist has already suppressed it in game.

NotAlexNoyle 05/06/26:

- Port all uses of PlaceholderAPI to MiniPlaceholders / Utilities-OG APIs.
- Fix Unions-OG Union colors breaking MC -> Discord link.
- Drop the per-Discord-role suffix configuration. Player suffixes shown in messages now come exclusively from each player's individual LuckPerms suffix.
- Fix LuckPerms spacing so exactly one space appears between the player name and any prefix/suffix.
- Configure Color Code Roles.

NotAlexNoyle 05/04/26:

- Migrate vanish detection from the Essentials API to the Vanish-OG API surface.
- Append ` &7> ` separator in chat formatting so the trailing `>` can
  be removed from LuckPerms suffixes.
- Fix Unions-OG prefix support.
