package plugily.projects.buildbattle.handlers.misc;

import com.bergerkiller.bukkit.mw.MyWorlds;
import com.bergerkiller.bukkit.mw.WorldConfig;
import com.bergerkiller.bukkit.mw.WorldConfigStore;
import com.bergerkiller.bukkit.mw.WorldInventory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import plugily.projects.buildbattle.Main;
import plugily.projects.buildbattle.arena.BaseArena;
import plugily.projects.minigamesbox.classic.handlers.language.MessageBuilder;
import plugily.projects.minigamesbox.classic.utils.configuration.ConfigUtils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class MyWorldsManager {

    private static final String[] MY_WORLDS_PLUGIN_NAMES = { "My_Worlds", "MyWorlds" };

    private final Main plugin;
    private MyWorlds myWorlds;
    private Set<String> configuredArenaWorlds;

    public MyWorldsManager(Main plugin) {

        this.plugin = plugin;

    }

    public boolean initialize() {

        Plugin installedPlugin = null;
        for (String name : MY_WORLDS_PLUGIN_NAMES) {

            Plugin candidate = plugin.getServer().getPluginManager().getPlugin(name);
            if (candidate instanceof MyWorlds && candidate.isEnabled()) {

                installedPlugin = candidate;
                break;

            }

        }

        if (installedPlugin == null) {

            plugin.getLogger()
                    .severe("BuildBattle-OG requires MyWorlds (My_Worlds or MyWorlds) to be installed and enabled.");
            plugin.getServer().getPluginManager().disablePlugin(plugin);
            return false;

        }

        myWorlds = (MyWorlds) installedPlugin;
        enableRequiredFeatures();
        return true;

    }

    public void synchronizeArenaWorldInventories() {

        Set<String> arenaWorlds = collectConfiguredArenaWorlds();
        configuredArenaWorlds = arenaWorlds;
        if (arenaWorlds.isEmpty()) {

            return;

        }

        for (String worldName : arenaWorlds) {

            WorldConfig.get(worldName);

        }

        WorldConfigStore.saveAll();
        WorldInventory.detach(arenaWorlds);
        plugin.getDebugger().debug("[MyWorlds] Detached BuildBattle worlds from shared main-world inventories: {0}",
                String.join(", ", arenaWorlds));

    }

    public boolean validateArenaWorld(@Nullable String worldName, String label, @Nullable BaseArena arena) {

        if (worldName == null || worldName.isBlank()) {

            return true;

        }

        if (!isProtectedWorld(worldName)) {

            return true;

        }

        plugin.getDebugger()
                .sendConsoleMsg(new MessageBuilder("VALIDATOR_INVALID_ARENA_CONFIGURATION").asKey()
                        .value(label + " uses protected MyWorlds main world " + worldName
                                + ". Use a dedicated MyWorlds lobby/game world instead.")
                        .arena(arena).build());
        return false;

    }

    /**
     * Stricter variant of {@link #validateArenaWorld} for required arena locations.
     * Blank/missing world names are rejected so players are never stranded inside
     * the arena world after a match ends.
     */
    public boolean requireConfiguredArenaWorld(@Nullable String worldName, String label, @Nullable BaseArena arena) {

        if (worldName == null || worldName.isBlank()) {

            plugin.getDebugger()
                    .sendConsoleMsg(new MessageBuilder("VALIDATOR_INVALID_ARENA_CONFIGURATION").asKey().value(label
                            + " is not configured. Set it to a dedicated MyWorlds lobby/game world via the setup GUI.")
                            .arena(arena).build());
            return false;

        }

        return validateArenaWorld(worldName, label, arena);

    }

    /**
     * Whether the named world appears anywhere in the arenas.yml configuration
     * (lobby, start, end or plot locations). Cached from the last inventory
     * synchronization; computed on demand before that.
     */
    public boolean isConfiguredArenaWorld(@Nullable String worldName) {

        if (worldName == null || worldName.isBlank()) {

            return false;

        }

        Set<String> worlds = configuredArenaWorlds;
        if (worlds == null) {

            worlds = collectConfiguredArenaWorlds();
            configuredArenaWorlds = worlds;

        }

        return worlds.contains(normalizeWorldName(worldName));

    }

    /**
     * The world players are returned to when no pre-join location is known. Prefers
     * MyWorlds' own main world (parity with TheHerobrine-OG), then the configured
     * protected worlds, then the server default.
     */
    public @Nullable World getMainWorld() {

        try {

            World mainWorld = MyWorlds.getMainWorld();
            if (mainWorld != null) {

                return mainWorld;

            }

        } catch (Throwable throwable) {

            plugin.getDebugger().debug("[MyWorlds] MyWorlds.getMainWorld() failed: {0}", throwable.getMessage());

        }

        for (String worldName : plugin.getConfig().getStringList("MyWorlds.Protected-Worlds")) {

            World world = Bukkit.getWorld(worldName);
            if (world != null) {

                return world;

            }

        }

        World world = Bukkit.getWorld("world");
        if (world != null) {

            return world;

        }

        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);

    }

    public boolean isProtectedWorld(@Nullable World world) {

        return world != null && isProtectedWorld(world.getName());

    }

    public boolean isProtectedWorld(@Nullable Location location) {

        return location != null && isProtectedWorld(location.getWorld());

    }

    public boolean isProtectedWorld(@Nullable String worldName) {

        if (worldName == null || worldName.isBlank()) {

            return false;

        }

        return getProtectedWorlds().contains(normalizeWorldName(worldName));

    }

    public String getProtectedWorldsDescription() {

        return getProtectedWorlds().stream().collect(Collectors.joining(", "));

    }

    public @Nullable String getWorldName(@Nullable String serializedLocation) {

        if (serializedLocation == null || serializedLocation.isBlank()) {

            return null;

        }

        int separator = serializedLocation.indexOf(',');
        String worldName = separator == -1 ? serializedLocation : serializedLocation.substring(0, separator);
        return worldName.isBlank() ? null : worldName;

    }

    // Applied at runtime only, the same way TheHerobrine-OG and Splegg-OG do it.
    // MyWorlds' own config.yml stays the admin's file; nothing here rewrites it.
    private void enableRequiredFeatures() {

        boolean enableWorldInventories = plugin.getConfig().getBoolean("MyWorlds.Enable-World-Inventories", true);
        if (enableWorldInventories && !MyWorlds.useWorldInventories) {

            myWorlds.setUseWorldInventories(true);
            plugin.getDebugger().debug("[MyWorlds] Enabled world inventories for this session.");

        }

        if (plugin.getConfig().getBoolean("MyWorlds.Enable-World-Chat", false) && !MyWorlds.useWorldChatPermissions) {

            MyWorlds.useWorldChatPermissions = true;
            plugin.getDebugger().debug("[MyWorlds] Enabled world chat permissions for this session.");

        }

    }

    private Set<String> collectConfiguredArenaWorlds() {

        Set<String> worldNames = new LinkedHashSet<>();
        FileConfiguration arenasConfig = ConfigUtils.getConfig(plugin, "arenas");
        ConfigurationSection instances = arenasConfig.getConfigurationSection("instances");
        if (instances == null) {

            return worldNames;

        }

        for (String arenaId : instances.getKeys(false)) {

            String basePath = "instances." + arenaId + ".";
            addWorld(worldNames, getWorldName(arenasConfig.getString(basePath + "lobbylocation")));
            addWorld(worldNames, getWorldName(arenasConfig.getString(basePath + "startlocation")));
            addWorld(worldNames, getWorldName(arenasConfig.getString(basePath + "endlocation")));
            addWorld(worldNames, arenasConfig.getString(basePath + "world"));

            ConfigurationSection plots = arenasConfig.getConfigurationSection(basePath + "plots");
            if (plots == null) {

                continue;

            }

            for (String plotId : plots.getKeys(false)) {

                addWorld(worldNames, getWorldName(plots.getString(plotId + ".1")));
                addWorld(worldNames, getWorldName(plots.getString(plotId + ".2")));

            }

        }

        return worldNames;

    }

    private void addWorld(Collection<String> worldNames, @Nullable String worldName) {

        if (worldName == null || worldName.isBlank() || isProtectedWorld(worldName)) {

            return;

        }

        worldNames.add(normalizeWorldName(worldName));

    }

    private Set<String> getProtectedWorlds() {

        List<String> configuredWorlds = plugin.getConfig().getStringList("MyWorlds.Protected-Worlds");
        if (configuredWorlds.isEmpty()) {

            return Set.of("world", "world_nether", "world_the_end");

        }

        return configuredWorlds.stream().map(MyWorldsManager::normalizeWorldName)
                .collect(Collectors.toCollection(LinkedHashSet::new));

    }

    private static String normalizeWorldName(String worldName) {

        return worldName.toLowerCase(Locale.ENGLISH);

    }

}
