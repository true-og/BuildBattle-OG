# Changelog

All notable OG-fork changes are documented here. Upstream history is at
https://github.com/Plugily-Projects/BuildBattle.

## 5.1.11 - 2026-09-14

### Changes

- The arena sidebar now follows the Scoreboard-OG network board's look: a
  `♥ BuildBattle-OG ♥` title, one blank line between labelled blocks and a
  `true-og.net` footer. The lobby card shows the mode, map, player count and
  countdown; Classic and Teams show the theme, phase, clock, player count or
  teammate and the plot being judged; Guess The Build shows the builder, the
  theme the viewer may know, round, clock, own points and the leader; the end
  card names the winner. Lines are built in code, so the `Scoreboard.Content`
  lists in `language.yml` are no longer read, and every line stays within 16
  legacy characters so 1.8 clients on ViaBackwards see it uncut.
- Arena worlds no longer come up as vanilla terrain. The shaded MiniGamesBox
  deserializes arena locations with `Bukkit.createWorld(new WorldCreator(name))`
  for any world that is not loaded, so the first boot after 5.1.10 shipped the
  ready-made `BB1` arena generated plain overworlds named `BB1-map` and
  `BB1-hub` when the map folders were not in place yet, and MyWorlds recorded
  them with an empty chunk generator. `ArenaRegistry` now refuses to register
  an arena whose worlds cannot be loaded from an existing folder, so that path
  is never reached.
- Maps are stored under a cold-storage directory instead of the server root,
  the way Splegg-OG and TheHerobrine-OG do it: `MyWorlds.Map-Base` (default
  `maps`, relative to the server root) holds one folder per map. On every
  enable each world named in `arenas.yml` is resolved to `<Map-Base>/<Name>/`
  (case-insensitively, ignoring `-` and `_`, and for an arena's game world also
  by its `mapname`, so `BB1-map` finds `maps/Plaza` and `BB1-hub` finds
  `maps/BB1_Hub`), any loaded copy is unloaded, the server-root copy is deleted
  and replaced with a fresh copy, and MyWorlds loads it. A world without a map
  there is loaded from the server root as before. Set `Map-Base` empty to turn
  the copy off.
- Bundled `BuildBattle-OG:void` chunk generator, pinned onto every arena world
  the plugin loads unless MyWorlds already has one for it
  (`MyWorlds.Void-Generator`), so chunks outside the saved map stay void.
- README: Quick Setup no longer asks for a manual copy to the server root or
  `/mw load`; the Bundled maps and Configuration sections describe the map
  directory and the two new keys.

## 5.1.10 - 2026-09-14

### Changes

- Ship `arenas.yml` configured for the open source TrueOG maps. Arena `BB1`
  now points at `Plaza` (public domain, twelve 31x31 plots around a central
  plaza) as `BB1-map` and `BB1_Hub` (Green Leave Lobby, CC BY-SA 3.0) as
  `BB1-hub`, with all twelve plot cuboids, the plaza centre as start and
  spectator location, the hub spawn as lobby and end location, and
  `maximumplayers: 12`. A fresh install needs the two worlds copied in and
  registered with MyWorlds, nothing else. Both maps live under `maps/` in the
  true-og repository.
- README: Quick Setup follows the bundled-map path first; the manual setup GUI
  flow is kept for custom arenas and additional lobbies. New "Bundled maps"
  section documents the worlds, licenses and layout.

## 5.1.9 - 2026-09-12

### Changes

- Claim `/hub`, `/lobby` and `/spawn` inside BuildBattle worlds before any other
  plugin sees them, the way `/vote` already is. Splegg-OG and TheHerobrine-OG
  register `/hub` as well, and Bukkit hands the bare label to whichever plugin
  loads first, so an arena player's `/hub` could run another minigame's command
  and teleport them out while the arena still counted them; Spawn-OG's `/spawn`
  did the same. `/lobby` is now an alias of `/hub` everywhere, and the three
  labels are topped up into `Commands.Whitelist` on startup so MiniGamesBox's
  in-game command block does not report them.
- `/bb join` and `/bbjoin` with no lobby list every arena with its state, fill
  and a clickable join line, like `/hbjoin`. `/bb join` now also accepts a bare
  number (`/bb join 1`) and a case-insensitive id, and replaces the bundled
  MiniGamesBox join argument.

## 5.1.8 - 2026-09-12

### Changes

- Render the arena sidebar through Scoreboard-OG's sidebar API when Scoreboard-OG
  `1.2.0` or newer is installed, so the network board comes back by itself after a
  match and `/togglescoreboard` is honoured. Without it the MiniGamesBox board is
  used as before.
- `/hub` now puts MiniGamesBox's inventory snapshot back before the teleport home.
  The snapshot is taken after the teleport into the arena, so it belongs to the
  arena world's MyWorlds inventory; restoring it after the teleport overwrote the
  survival inventory MyWorlds had just swapped back in.
- MyWorlds settings are applied at runtime only, the way TheHerobrine-OG and
  Splegg-OG do it. `MyWorlds/config.yml` is no longer rewritten on startup.
- The locale service stub no longer carries the remote fetch code; every fetch
  answers with an empty stream, so the bundled locale is the only one that loads.
- Plot teleport targets are resolved on the main thread instead of a ForkJoin
  worker reading block state.

## 5.1.7 - 2026-08-24

### Changes

- Register `<bb_score>` and `<bb_rank>` MiniPlaceholders through Utilities-OG.
  `<bb_score>` is the player's `POINTS_TOTAL`; `<bb_rank>` maps it onto a
  builder-themed ladder (Apprentice through Master Builder, then Inception's
  Forger, Dreamer, Extractor and Dom), with Dreamweaver reserved for
  the top scorer once they also hold Dom.

- Back both with a plugin-wide score cache seeded from the stats backend on
  enable and kept live by MiniGamesBox's statistic change event, so values are
  current the moment a game's points are awarded.

## 5.1.6 - 2026-08-17

### Changes

- Join signs now match TheHerobrine-OG's style: gold map name, black-on-sign
  player counts, and bold `JOIN` / `STARTING` / `LIVE` / `FULL` / `ENDING` /
  `RESTARTING` state labels. A language-file migration (version 3) rewrites the
  sign lines and `Placeholders.Game-States` on existing installs while leaving
  the MOTD state labels unchanged.

- Arena chat now renders the standard TrueOG name segment (union bracket tag,
  display name, LuckPerms suffix) like TheHerobrine-OG, with a caret colored by
  the sender's Chat-OG message color, while keeping the queue count, `SPEC`,
  `VOTE` / `JUDGING`, and theme prefixes.

## 5.1.5 - 2026-08-05

### Changes

- Scope creative mode to arena builders. `BuilderCreativeManager` grants a runtime
  creative exemption only while a player is an active builder inside an arena world,
  and suppresses GameModeInventories-OG's inventory swap for arena participants so
  the swap cannot clobber armour and offhand contents.
- Remember where a player was before they joined an arena. `PreJoinLocationStore`
  keeps that location on disk, reloading an unloaded world through MyWorlds when it
  is needed, and expires entries after `MyWorlds.PreJoin-Location-Expiry-Days`.
- Add the `MyWorlds.Protected-Worlds` list so arenas, plots and lobby locations can
  never be placed in the server's main overworld, nether or end.
- Bundle `powerups.yml` and write it out on first start, so the shaded
  MiniGamesBox-Classic reader stops logging "File powerups.yml does not exist!".
- Suppress the shaded library's "Loaded locale" console spam.
- Drop a dead clause from the console-noise filter. It tried to hide the
  "you are using some fork that was not tested by us" warning, but that message is
  written straight to the console sender rather than through the plugin logger the
  filter is attached to, so the condition could never fire. That warning is
  therefore still printed on startup; suppressing it needs a different mechanism
  and is left for a later release. The "Loaded locale" suppression is unaffected
  and still works.
- **Breaking:** the bundled `arenas.yml` template now names its worlds `BB1-hub` and
  `BB1-map` instead of `bb_lobby` and `bb_game_1`. Chat-OG resolves a world to a
  multi-world game by a `<letters><digits>-` prefix and keys the per-game Discord
  channel off it, so arena worlds have to follow that shape to get scoped chat and
  the Discord relay. Existing arenas keep the names they already have — changing
  the template does not migrate them. To adopt the convention, rename the world
  folders and update both `arenas.yml` and your MyWorlds configuration, then add a
  matching `BB` entry under `discord.games` in Chat-OG's config.
- Render `language.yml` values through Utilities-OG's colorizer when that plugin is
  installed, so MiniMessage tags, `<#rrggbb>` hex, `<gradient:...>`, named colours
  and the `&*` rainbow code all work in messages, titles, action bars, scoreboards
  and signs. Only values that actually contain such a construct are converted;
  values using plain `&` codes are passed through byte-for-byte, so nothing that
  ships with the plugin changes appearance. Without Utilities-OG, every value keeps
  its previous formatting.
- Remove seven permission nodes from `plugin.yml` that nothing ever checked:
  `buildbattle.admin.stopgame`, `.addsign`, `.plotwand`, `.create`,
  `.forcestart.theme`, `.supervotes.manage` and `buildbattle.command.bypass`. The
  nodes that actually gate those behaviours are `buildbattle.admin.stop`,
  `.admin.locwand`, `.admin.setup` and `.command.override`; grant those instead.
  In particular, bypassing in-game command blocking has always required
  `buildbattle.command.override`, which was never declared, while the declared
  `buildbattle.command.bypass` did nothing.
- Declare the permission nodes that were checked but never listed, including
  `buildbattle.admin.setup`, `.stop`, `.locwand`, `.teleport`, `.spychat`,
  `.statistic`, `.forceplay`, `.theme` and `buildbattle.command.selectplot`, so
  `buildbattle.admin.*` grants the whole admin surface.
- Disable the plugin instead of half-starting it when MyWorlds is missing. The
  startup check returned early after the base plugin had already enabled, leaving
  the server with a plugin marked enabled but no arenas, commands or listeners.
- Suppress Guess The Build answers through Paper's `AsyncChatEvent` rather than the
  legacy `AsyncPlayerChatEvent`. Registering a legacy chat listener forced the whole
  server onto Paper's legacy chat path, and the handler raced Chat-OG for ownership
  of the event, so a correct guess could reach the world and Discord before it was
  suppressed.
- Stop `/hub` registration from throwing when the command is absent from
  `plugin.yml`.
- Fix a malformed `Core-Version: 1148-` marker in `powerups.yml` that parsed as `0`
  and would have left a duplicate `Do-Not-Edit` block behind on migration.
- Report `1.19` as a supported version in `internal/data.yml`, and bring
  `locales/locale_data.yml` up to the current release.
- Build Utilities-OG from a git submodule, add `bootstrap.sh`, and harvest vendored
  library licenses into `META-INF/licenses` automatically instead of listing them
  one by one.

## 5.1.4 - 2026-05-20

### Changes

- Add a `/hub` command that returns players from BuildBattle lobbies or active games
  to the main world spawn.
- Return players to the main world when they reconnect from an arena world, or when
  they quit mid-game.
- Clean up arena membership, plot membership, scoreboards, bossbars, action bars,
  player visibility and empty-game shutdown when a player leaves via `/hub`.
- Whitelist `hub` by default for in-arena command blocking.
- Show `&4BuildBattle-OG`, the arena name, its state and the player count on default
  sign lines.
- Remove external telemetry, custom chart registration, the bundled metrics
  implementation and the metrics plugin id.

## 5.1.3 - 2026-04-23

### Changes

- Remove the Plugily Projects service hooks. Upstream's `ServiceRegistry.registerService()`
  pinged `https://api.plugily.xyz/ping.php` on enable and constructed `LocaleService`
  and `MetricsService`. The override skips the ping and leaves `serviceEnabled=false`,
  which also disables:
  - remote translation fetching from `api.plugily.xyz/locale/v3/fetch.php`, so the
    bundled `Default` locale is used instead;
  - automatic error reporting to `api.plugily.xyz/error/report.php`, since
    `ReportedException` gates on `isServiceEnabled()`;
  - Plugily's internal `MetricsService` timer.
- Replace upstream's `UpdateChecker` with one that returns `UP_TO_DATE` instead of
  calling `api.spiget.org`, disabling both the on-enable check and the OP-join
  notifier.
- Exclude the upstream `ServiceRegistry` and `UpdateChecker` classes from the shaded
  jar so the local overrides win after relocation.
- Fix the head cache.
- Add MyWorlds fork compatibility and backport to the 1.19.4 API.
- Migrate the build from Maven to Gradle and rename the project to BuildBattle-OG.
