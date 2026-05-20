# Changelog

All notable OG-fork changes. Upstream history at https://github.com/Plugily-Projects/BuildBattle.

## [5.1.4] - OG fork

### Added

- `/hub` command for returning players from BuildBattle lobbies or active games to the main world spawn.
- Reconnect handling that returns players to the main world when they rejoin from an arena world or after quitting during a game.
- Hub leave cleanup for arena membership, plot membership, scoreboards, bossbars, action bars, player visibility, and empty-game shutdown.

### Removed

- External telemetry, custom chart registration, bundled metrics implementation, and the metrics plugin id.
- `hub` is whitelisted by default for in-arena command blocking.
- Default sign lines now show `&4BuildBattle-OG`, arena name, arena state, and player count.

## [5.1.3] — OG fork

### Removed

- Plugily Projects service hooks. Upstream `ServiceRegistry.registerService()` pinged `https://api.plugily.xyz/ping.php` on enable and constructed `LocaleService` + `MetricsService`. Override skips the ping and leaves `serviceEnabled=false`, which transitively disables:
  - Remote translation fetching from `api.plugily.xyz/locale/v3/fetch.php` (bundled `Default` locale is used instead).
  - Automatic error reporting to `api.plugily.xyz/error/report.php` (`ReportedException` gates on `isServiceEnabled()` and short-circuits).
  - Plugily's internal `MetricsService` timer.
- Upstream `UpdateChecker`. Override returns a completed `UP_TO_DATE` result instead of hitting `api.spiget.org/v2/resources/{id}/versions`. Disables the on-enable update check and the OP-join notifier.

### Changed

- `build.gradle.kts` shadowJar excludes upstream `ServiceRegistry.class` and `UpdateChecker*.class` so the local overrides win after relocation.
- Head cache fix.
- MyWorlds fork compatibility and 1.19.4 API backport.
- Gradle migration from Maven.
- Project renamed to BuildBattle-OG.
