/*
 *
 * BuildBattle - Ultimate building competition minigame
 * Copyright (C) 2022 Plugily Projects - maintained by Tigerpanzer_02 and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package plugily.projects.buildbattle;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Filter;
import java.util.logging.LogRecord;
import nl.skbotnl.chatog.api.ChatOGAPI;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.TestOnly;
import plugily.projects.buildbattle.arena.ArenaEvents;
import plugily.projects.buildbattle.arena.ArenaManager;
import plugily.projects.buildbattle.arena.ArenaRegistry;
import plugily.projects.buildbattle.arena.BaseArena;
import plugily.projects.buildbattle.arena.managers.plots.PlotMenuHandler;
import plugily.projects.buildbattle.arena.vote.VoteEvents;
import plugily.projects.buildbattle.arena.vote.VoteItems;
import plugily.projects.buildbattle.boot.AdditionalValueInitializer;
import plugily.projects.buildbattle.boot.MessageInitializer;
import plugily.projects.buildbattle.boot.PlaceholderInitializer;
import plugily.projects.buildbattle.boot.TrueOGPlaceholderInitializer;
import plugily.projects.buildbattle.chat.BuildBattleChatFormatter;
import plugily.projects.buildbattle.commands.ForceStartCommand;
import plugily.projects.buildbattle.commands.HubCommand;
import plugily.projects.buildbattle.commands.HubCommandListener;
import plugily.projects.buildbattle.commands.JoinLobbyCommand;
import plugily.projects.buildbattle.commands.VoteCommandListener;
import plugily.projects.buildbattle.commands.arguments.ArgumentsRegistry;
import plugily.projects.buildbattle.handlers.LanguageMigrator;
import plugily.projects.buildbattle.handlers.language.TrueOGLanguageManager;
import plugily.projects.buildbattle.handlers.menu.OptionsRegistry;
import plugily.projects.buildbattle.handlers.misc.ArenaWorldProvisioner;
import plugily.projects.buildbattle.handlers.misc.BlacklistManager;
import plugily.projects.buildbattle.handlers.misc.BuilderCreativeManager;
import plugily.projects.buildbattle.handlers.misc.GameModeInventoriesGuard;
import plugily.projects.buildbattle.handlers.misc.HeadDatabaseManager;
import plugily.projects.buildbattle.handlers.misc.MyWorldsManager;
import plugily.projects.buildbattle.handlers.misc.PreJoinLocationListener;
import plugily.projects.buildbattle.handlers.misc.PreJoinLocationStore;
import plugily.projects.buildbattle.handlers.misc.ReconnectToMainWorldListener;
import plugily.projects.buildbattle.handlers.misc.ScoreboardOGBridge;
import plugily.projects.buildbattle.handlers.misc.VoidChunkGenerator;
import plugily.projects.buildbattle.handlers.setup.SetupCategoryManager;
import plugily.projects.buildbattle.handlers.stats.BuildBattleScores;
import plugily.projects.buildbattle.handlers.themes.ThemeManager;
import plugily.projects.minigamesbox.api.arena.IPluginArena;
import plugily.projects.minigamesbox.api.handlers.language.ILanguageManager;
import plugily.projects.minigamesbox.classic.PluginMain;
import plugily.projects.minigamesbox.classic.handlers.setup.SetupInventory;
import plugily.projects.minigamesbox.classic.handlers.setup.categories.PluginSetupCategoryManager;

/**
 * Created by Tom on 17/08/2015. Updated by Tigerpanzer_02 on 03.12.2021
 */
public class Main extends PluginMain {

    private VoteItems voteItems;
    private HeadDatabaseManager headDatabaseManager;
    private ThemeManager themeManager;
    private BlacklistManager blacklistManager;
    private OptionsRegistry optionsRegistry;
    private ArenaRegistry arenaRegistry;
    private ArenaManager arenaManager;
    private ArgumentsRegistry argumentsRegistry;
    private PlotMenuHandler plotMenuHandler;
    private MyWorldsManager myWorldsManager;
    private ArenaWorldProvisioner arenaWorldProvisioner;
    private PreJoinLocationStore preJoinLocationStore;
    private BuilderCreativeManager builderCreativeManager;
    private GameModeInventoriesGuard gmiGuard;
    private ILanguageManager languageManager;
    private BuildBattleScores buildBattleScores;

    @TestOnly
    public Main() {

        super();

    }

    @Override
    public void onEnable() {

        long start = System.currentTimeMillis();
        installDebugNoiseFilter();
        // Write bundled powerups.yml to the data folder so the shaded
        // MiniGamesBox-Classic reader finds it instead of logging
        // "File powerups.yml does not exist!".
        if (!new java.io.File(getDataFolder(), "powerups.yml").exists()) {

            saveResource("powerups.yml", false);

        }

        new LanguageMigrator(this);
        MessageInitializer messageInitializer = new MessageInitializer(this);
        // Must precede super.onEnable(): SignManager caches Signs.Lines and the
        // game-state names into
        // final fields while the base plugin enables, and never re-reads them (not even
        // on /bba reload).
        installTrueOGColorizer();
        super.onEnable();
        getDebugger().debug("[System] [Plugin] Initialization start");
        arenaRegistry = new ArenaRegistry(this);
        myWorldsManager = new MyWorldsManager(this);
        if (!myWorldsManager.initialize()) {

            // super.onEnable() already ran, so a bare return would leave the plugin marked
            // enabled with no arenas, commands or listeners registered. Fail fast instead.
            // Note this runs onDisable() re-entrantly, inside onEnable(). That is safe
            // today only
            // because registerArenas() has not run yet, so onDisable()'s loop over the
            // arena
            // registry is empty and never dereferences the still-null arenaManager. If
            // arenas ever
            // become populated earlier, move this check above super.onEnable() instead.
            getServer().getPluginManager().disablePlugin(this);
            return;

        }

        preJoinLocationStore = new PreJoinLocationStore(this);
        new PlaceholderInitializer(this);
        messageInitializer.registerMessages();
        new AdditionalValueInitializer(this);
        initializePluginClasses();
        // After AdditionalValueInitializer, which registers the POINTS_TOTAL statistic
        // this reads.
        buildBattleScores = new BuildBattleScores(this);
        if (getServer().getPluginManager().getPlugin("Utilities-OG") != null) {

            new TrueOGPlaceholderInitializer(buildBattleScores);

        }

        getDebugger().debug("Full {0} plugin enabled", getName());
        getDebugger().debug("[System] [Plugin] Initialization finished took {0}ms", System.currentTimeMillis() - start);

    }

    // Route language values through TrueOG's colorizer when Utilities-OG is
    // installed. It is a
    // compileOnly dependency, so only build the decorator once its classes are
    // known present.
    // Every plugin is loaded before any is enabled, so this check is already valid
    // pre-onEnable.
    private void installTrueOGColorizer() {

        if (getServer().getPluginManager().getPlugin("Utilities-OG") == null) {

            getLogger().info("Utilities-OG is not installed, so messages keep their default formatting.");
            return;

        }

        // Delegate resolved lazily: PluginMain has not built its LanguageManager yet at
        // this point.
        languageManager = new TrueOGLanguageManager(super::getLanguageManager);

    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Returns the colorizing decorator once {@link #installTrueOGColorizer()} has
     * run, which is before {@code super.onEnable()} so that values the base plugin
     * caches at startup (join sign lines, game-state names) are already colorized.
     */
    @Override
    public ILanguageManager getLanguageManager() {

        return languageManager != null ? languageManager : super.getLanguageManager();

    }

    // Suppress known non-actionable startup noise from shaded MiniGamesBox-Classic.
    private void installDebugNoiseFilter() {

        java.util.logging.Logger jul = java.util.logging.Logger.getLogger(getName());
        Filter previous = jul.getFilter();
        jul.setFilter(new Filter() {

            @Override
            public boolean isLoggable(LogRecord record) {

                String msg = record.getMessage();
                // Raw locale records contain color codes and concatenated values,
                // so match them by substring rather than prefix.
                if (msg != null && msg.contains("Loaded locale"))
                    return false;
                return previous == null || previous.isLoggable(record);

            }

        });

    }

    public void initializePluginClasses() {

        addFileName("themes");
        addFileName("vote_items");
        blacklistManager = new BlacklistManager(this);
        headDatabaseManager = new HeadDatabaseManager(this);
        themeManager = new ThemeManager(this);
        BaseArena.init(this);
        new ArenaEvents(this);
        arenaManager = new ArenaManager(this);
        gmiGuard = new GameModeInventoriesGuard(this, player -> isArenaWorld(player.getWorld()));
        builderCreativeManager = new BuilderCreativeManager(this);
        // Load worlds first, or MiniGamesBox generates vanilla ones on register.
        arenaWorldProvisioner = new ArenaWorldProvisioner(this);
        arenaWorldProvisioner.provisionArenaWorlds();
        arenaRegistry.registerArenas();
        // /reload leaves players standing in arena worlds with no suspension.
        gmiGuard.sweepOnlinePlayers();
        new ReconnectToMainWorldListener(this);
        new PreJoinLocationListener(this);
        PluginCommand hubCommand = getCommand("hub");
        HubCommand hub = new HubCommand(this);
        if (hubCommand != null) {

            hubCommand.setExecutor(hub);

        } else {

            getLogger().warning("The 'hub' command is missing from plugin.yml, so /hub will not work.");

        }

        registerCommand("bbjoin", new JoinLobbyCommand(this));
        registerCommand("bbforcestart", new ForceStartCommand(this));
        // Claims /v and /vote inside BuildBattle worlds before VotingPlugin sees them.
        new VoteCommandListener(this);
        // Claims /hub, /lobby and /spawn inside BuildBattle worlds the same way, so
        // another minigame's /hub or Spawn-OG's /spawn never handles an arena player.
        new HubCommandListener(this, hub);
        ensureCommandWhitelist();
        registerChatFormatter();

        myWorldsManager.synchronizeArenaWorldInventories();
        getSignManager().loadSigns();
        getSignManager().updateSigns();
        argumentsRegistry = new ArgumentsRegistry(this);
        voteItems = new VoteItems(this);
        new VoteEvents(this);
        plotMenuHandler = new PlotMenuHandler(this);
        optionsRegistry = new OptionsRegistry(this);
        optionsRegistry.registerOptions();

    }

    public VoteItems getVoteItems() {

        return voteItems;

    }

    public HeadDatabaseManager getHeadDatabaseManager() {

        return headDatabaseManager;

    }

    public ThemeManager getThemeManager() {

        return themeManager;

    }

    public BlacklistManager getBlacklistManager() {

        return blacklistManager;

    }

    public OptionsRegistry getOptionsRegistry() {

        return optionsRegistry;

    }

    public PlotMenuHandler getPlotMenuHandler() {

        return plotMenuHandler;

    }

    public BuildBattleScores getBuildBattleScores() {

        return buildBattleScores;

    }

    public ArenaWorldProvisioner getArenaWorldProvisioner() {

        return arenaWorldProvisioner;

    }

    // Backs the BuildBattle-OG:void id pinned onto arena worlds in MyWorlds.
    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {

        return new VoidChunkGenerator();

    }

    public MyWorldsManager getMyWorldsManager() {

        return myWorldsManager;

    }

    public BuilderCreativeManager getBuilderCreativeManager() {

        return builderCreativeManager;

    }

    public GameModeInventoriesGuard getGmiGuard() {

        return gmiGuard;

    }

    // BuildBattle territory: every registered arena's lobby and game worlds plus
    // the plot worlds arenas.yml names, which MiniGamesBox does not list.
    public boolean isArenaWorld(World world) {

        if (world == null) {

            return false;

        }

        if (arenaRegistry != null) {

            for (World arenaWorld : arenaRegistry.getArenaWorlds()) {

                if (arenaWorld != null && arenaWorld.getName().equals(world.getName())) {

                    return true;

                }

            }

            for (World arenaWorld : arenaRegistry.getArenaIngameWorlds()) {

                if (arenaWorld != null && arenaWorld.getName().equals(world.getName())) {

                    return true;

                }

            }

        }

        return myWorldsManager != null && myWorldsManager.isConfiguredArenaWorld(world.getName());

    }

    // Chat-OG routes a world to a game by the letter prefix of its name (BB1-hub
    // and
    // BB1-map both belong to key "BB"), so the formatter is registered once per
    // distinct prefix across the configured arena worlds.
    private void registerChatFormatter() {

        if (getServer().getPluginManager().getPlugin("Chat-OG") == null) {

            getLogger().info("Chat-OG is not installed, so BuildBattle chat keeps its default formatting.");
            return;

        }

        Set<String> keys = new LinkedHashSet<>();
        for (IPluginArena arena : getArenaRegistry().getArenas()) {

            addGameKey(keys, arena.getLobbyLocation());
            addGameKey(keys, arena.getStartLocation());

        }

        if (keys.isEmpty()) {

            getLogger().warning("No arena world follows the <prefix><number>-<name> convention, so no BuildBattle "
                    + "chat formatter was registered.");
            return;

        }

        BuildBattleChatFormatter formatter = new BuildBattleChatFormatter(this);
        for (String key : keys) {

            if (!ChatOGAPI.setFormatter(key, formatter)) {

                getLogger().warning("Chat-OG was not ready, so no chat formatter was registered for " + key + ".");

            }

        }

    }

    // Mirrors Chat-OG's own parse: letters, then digits, then '-'. Anything else is
    // not part of a multi world game and contributes no key.
    private static void addGameKey(Set<String> keys, Location location) {

        if (location == null || location.getWorld() == null) {

            return;

        }

        String worldName = location.getWorld().getName();
        int index = 0;
        while (index < worldName.length() && Character.isLetter(worldName.charAt(index))) {

            index++;

        }

        if (index == 0) {

            return;

        }

        int digitStart = index;
        while (index < worldName.length() && Character.isDigit(worldName.charAt(index))) {

            index++;

        }

        if (index == digitStart || index >= worldName.length() || worldName.charAt(index) != '-') {

            return;

        }

        keys.add(worldName.substring(0, digitStart));

    }

    // MiniGamesBox blocks every command an arena member runs that is not on
    // Commands.Whitelist, and it does not check whether the event was already
    // cancelled, so the commands HubCommandListener claims have to be listed or
    // the player gets a "blocked" message after being sent home. Older configs
    // predate /lobby and /spawn, so the list is topped up here.
    private void ensureCommandWhitelist() {

        final java.util.List<String> whitelist = new java.util.ArrayList<>(
                getConfig().getStringList("Commands.Whitelist"));
        boolean changed = false;
        for (String required : new String[] { "hub", "lobby", "spawn", "bbjoin", "bbforcestart" }) {

            if (whitelist.stream().noneMatch(required::equalsIgnoreCase)) {

                whitelist.add(required);
                changed = true;

            }

        }

        if (changed) {

            getConfig().set("Commands.Whitelist", whitelist);
            saveConfig();

        }

    }

    private void registerCommand(String name, CommandExecutor executor) {

        PluginCommand command = getCommand(name);
        if (command == null) {

            getLogger()
                    .warning("The '" + name + "' command is missing from plugin.yml, so /" + name + " will not work.");
            return;

        }

        command.setExecutor(executor);
        if (executor instanceof TabCompleter tabCompleter) {

            command.setTabCompleter(tabCompleter);

        }

    }

    public void savePreJoinLocation(Player player) {

        savePreJoinLocation(player.getUniqueId(), player.getLocation());

    }

    public void savePreJoinLocation(UUID playerId, Location location) {

        if (preJoinLocationStore != null && location != null && location.getWorld() != null) {

            preJoinLocationStore.put(playerId, location);

        }

    }

    public boolean hasPreJoinLocation(UUID playerId) {

        return preJoinLocationStore != null && preJoinLocationStore.getWorldName(playerId) != null;

    }

    public void removePreJoinLocation(UUID playerId) {

        if (preJoinLocationStore != null) {

            preJoinLocationStore.remove(playerId);

        }

    }

    public Location getAndRemovePreJoinLocation(UUID playerId) {

        if (preJoinLocationStore == null) {

            return null;

        }

        // Pulls the stored world back in through MyWorlds when it is unloaded.
        Location location = preJoinLocationStore.getOrLoadWorld(playerId);
        preJoinLocationStore.remove(playerId);
        return location;

    }

    @Override
    public void onDisable() {

        // Order matters on /reload, where players stay online. super.onDisable() stops
        // every arena, which restores inventories through MiniGamesBox; lifting the
        // GameModeInventories suspension before that lets GMI swap mid-restore and file
        // arena state as the player's real survival inventory. The location store is
        // flushed last so anything teardown changes still reaches disk.
        super.onDisable();

        // Arena stop already released every claimed sidebar; this catches a stray one.
        ScoreboardOGBridge.releaseAll();

        if (builderCreativeManager != null) {

            builderCreativeManager.revokeAll();

        }

        if (gmiGuard != null) {

            gmiGuard.releaseAll();

        }

        if (preJoinLocationStore != null) {

            preJoinLocationStore.shutdown();

        }

    }

    @Override
    public ArenaRegistry getArenaRegistry() {

        return arenaRegistry;

    }

    @Override
    public ArenaManager getArenaManager() {

        return arenaManager;

    }

    @Override
    public ArgumentsRegistry getArgumentsRegistry() {

        return argumentsRegistry;

    }

    @Override
    public PluginSetupCategoryManager getSetupCategoryManager(SetupInventory setupInventory) {

        return new SetupCategoryManager(setupInventory);

    }

}
