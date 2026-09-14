package plugily.projects.buildbattle.handlers.misc;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import com.bergerkiller.bukkit.mw.WorldConfig;
import com.bergerkiller.bukkit.mw.WorldConfigStore;

import plugily.projects.buildbattle.Main;
import plugily.projects.minigamesbox.classic.utils.configuration.ConfigUtils;

// Copies arena worlds in from the map dir and loads them via MyWorlds.
public class ArenaWorldProvisioner {

    private final Main plugin;

    public ArenaWorldProvisioner(Main plugin) {

        this.plugin = plugin;

    }

    // Map dir from MyWorlds.Map-Base; null when empty. Relative to world dir.
    public @Nullable File getMapBaseDir() {

        String raw = plugin.getConfig().getString("MyWorlds.Map-Base", "maps");
        if (raw == null || raw.isBlank()) {

            return null;

        }

        File asPath = new File(raw);
        if (asPath.isAbsolute()) {

            return asPath;

        }

        return new File(plugin.getServer().getWorldContainer(), raw);

    }

    public boolean isVoidGeneratorEnabled() {

        return plugin.getConfig().getBoolean("MyWorlds.Void-Generator", true);

    }

    // Refreshes and loads every arena world; call before registerArenas().
    public void provisionArenaWorlds() {

        Map<String, List<String>> worlds = collectArenaWorlds(ConfigUtils.getConfig(plugin, "arenas"), null);
        if (worlds.isEmpty()) {

            return;

        }

        File mapBase = getMapBaseDir();
        if (mapBase != null && !mapBase.isDirectory()) {

            plugin.getLogger()
                    .info("Map directory " + mapBase.getPath()
                            + " does not exist; arena worlds are loaded from the server root instead. Create it and"
                            + " put the maps there (see MyWorlds.Map-Base in config.yml).");
            mapBase = null;

        }

        for (Map.Entry<String, List<String>> entry : worlds.entrySet()) {

            String worldName = entry.getKey();
            if (plugin.getMyWorldsManager().isProtectedWorld(worldName)) {

                // The arena validator rejects it with its own message.
                continue;

            }

            if (!MapBase.isSafeWorldName(worldName)) {

                plugin.getLogger().severe(
                        "Refusing to provision arena world '" + worldName + "': the name is not a plain folder name.");
                continue;

            }

            File template = resolveTemplate(mapBase, entry.getValue());
            if (template != null) {

                refreshFromTemplate(worldName, template);

            } else {

                if (mapBase != null) {

                    plugin.getLogger()
                            .info("No map for arena world '" + worldName + "' under " + mapBase.getPath()
                                    + " (looked for " + String.join(", ", entry.getValue()) + "); loading it from the"
                                    + " server root instead. " + MapBase.describeWorldDirs(mapBase));

                }

                ensureWorldLoaded(worldName);

            }

        }

    }

    // Loaded, or loadable from an existing folder. Never generates.
    public boolean isWorldAvailable(@Nullable String worldName) {

        if (worldName == null || worldName.isBlank()) {

            return false;

        }

        return Bukkit.getWorld(worldName) != null || ensureWorldLoaded(worldName) != null;

    }

    // Loads an existing world folder via MyWorlds; null (logged) if none.
    public @Nullable World ensureWorldLoaded(String worldName) {

        World existing = Bukkit.getWorld(worldName);
        if (existing != null) {

            return existing;

        }

        if (plugin.getMyWorldsManager().isProtectedWorld(worldName)) {

            return null;

        }

        File container = plugin.getServer().getWorldContainer();
        File worldDir = MapBase.resolveWorldDir(container, worldName);
        if (!MapBase.isWorldDir(worldDir)) {

            File mapBase = getMapBaseDir();
            plugin.getLogger()
                    .severe("Arena world '" + worldName + "' has no world folder in " + container.getPath()
                            + (mapBase == null ? "" : " and no map under " + mapBase.getPath()) + ". Put the map at "
                            + (mapBase == null ? new File(container, worldName) : new File(mapBase, worldName))
                                    .getPath()
                            + " (a folder with level.dat inside) and restart. "
                            + MapBase.describeWorldDirs(mapBase == null ? container : mapBase));
            return null;

        }

        if (!worldDir.getName().equals(worldName)) {

            plugin.getLogger().info("Arena world '" + worldName + "' resolved to folder '" + worldDir.getName()
                    + "'; rename the folder or fix arenas.yml so they match exactly.");

        }

        return loadThroughMyWorlds(worldDir.getName());

    }

    // Arena worlds -> template names to try (game world also tries mapname).
    public static Map<String, List<String>> collectArenaWorlds(FileConfiguration arenasConfig,
            @Nullable String onlyArenaId)
    {

        Map<String, List<String>> result = new LinkedHashMap<>();
        Map<String, String> spellingByKey = new LinkedHashMap<>();
        ConfigurationSection instances = arenasConfig.getConfigurationSection("instances");
        if (instances == null) {

            return result;

        }

        for (String arenaId : instances.getKeys(false)) {

            if (onlyArenaId != null && !onlyArenaId.equalsIgnoreCase(arenaId)) {

                continue;

            }

            String basePath = "instances." + arenaId + ".";
            String mapName = arenasConfig.getString(basePath + "mapname");
            addWorld(result, spellingByKey, arenasConfig.getString(basePath + "world"), mapName);
            addWorld(result, spellingByKey, worldOf(arenasConfig.getString(basePath + "startlocation")), mapName);
            addWorld(result, spellingByKey, worldOf(arenasConfig.getString(basePath + "lobbylocation")), null);
            addWorld(result, spellingByKey, worldOf(arenasConfig.getString(basePath + "endlocation")), null);
            addWorld(result, spellingByKey, worldOf(arenasConfig.getString(basePath + "spectatorlocation")), null);

            ConfigurationSection plots = arenasConfig.getConfigurationSection(basePath + "plots");
            if (plots == null) {

                continue;

            }

            for (String plotId : plots.getKeys(false)) {

                addWorld(result, spellingByKey, worldOf(plots.getString(plotId + ".1")), null);
                addWorld(result, spellingByKey, worldOf(plots.getString(plotId + ".2")), null);

            }

        }

        return result;

    }

    private static void addWorld(Map<String, List<String>> result, Map<String, String> spellingByKey,
            @Nullable String worldName, @Nullable String mapName)
    {

        if (worldName == null || worldName.isBlank()) {

            return;

        }

        String key = worldName.toLowerCase(Locale.ROOT);
        String spelling = spellingByKey.computeIfAbsent(key, ignored -> worldName);
        List<String> candidates = result.computeIfAbsent(spelling, ignored -> new ArrayList<>());
        addCandidate(candidates, worldName);
        addCandidate(candidates, mapName);

    }

    private static void addCandidate(List<String> candidates, @Nullable String candidate) {

        if (candidate == null || candidate.isBlank()) {

            return;

        }

        for (String existing : candidates) {

            if (existing.equalsIgnoreCase(candidate)) {

                return;

            }

        }

        candidates.add(candidate);

    }

    private static @Nullable String worldOf(@Nullable String serializedLocation) {

        if (serializedLocation == null || serializedLocation.isBlank()) {

            return null;

        }

        int separator = serializedLocation.indexOf(',');
        String worldName = separator == -1 ? serializedLocation : serializedLocation.substring(0, separator);
        return worldName.isBlank() ? null : worldName;

    }

    private static @Nullable File resolveTemplate(@Nullable File mapBase, List<String> candidates) {

        if (mapBase == null) {

            return null;

        }

        for (String candidate : candidates) {

            File dir = MapBase.resolveWorldDir(mapBase, candidate);
            if (MapBase.isWorldDir(dir)) {

                return dir;

            }

        }

        return null;

    }

    // Wipes the server-root copy, recopies the template and loads it.
    private void refreshFromTemplate(String worldName, File source) {

        File container = plugin.getServer().getWorldContainer();
        File dest = new File(container, worldName);
        try {

            if (dest.exists() && source.getCanonicalFile().equals(dest.getCanonicalFile())) {

                plugin.getLogger()
                        .warning("Map for '" + worldName + "' is the server-root world itself (" + source.getPath()
                                + "); refusing to wipe and re-copy it onto itself. Move the map under"
                                + " the map directory.");
                ensureWorldLoaded(worldName);
                return;

            }

        } catch (IOException ex) {

            plugin.getLogger().warning("Could not compare " + source.getPath() + " with " + dest.getPath() + ": "
                    + ex.getMessage() + ". Leaving the world alone.");
            ensureWorldLoaded(worldName);
            return;

        }

        if (!unloadIfPresent(worldName)) {

            plugin.getLogger().warning("Could not unload '" + worldName + "' before refreshing it from "
                    + source.getPath() + "; keeping the current copy.");
            return;

        }

        try {

            if (dest.exists()) {

                MapBase.deleteRecursive(dest.toPath());

            }

            MapBase.copyRecursive(source.toPath(), dest.toPath());
            MapBase.sanitizeWorldFolder(dest);

        } catch (IOException ex) {

            plugin.getLogger().severe(
                    "Failed to copy map " + source.getPath() + " to " + dest.getPath() + ": " + ex.getMessage());
            return;

        }

        if (loadThroughMyWorlds(worldName) != null) {

            plugin.getLogger().info("Loaded arena world '" + worldName + "' from " + source.getPath() + ".");

        }

    }

    private @Nullable World loadThroughMyWorlds(String worldName) {

        WorldConfig worldConfig = WorldConfig.get(worldName);
        applyVoidGenerator(worldConfig);
        World loaded;
        try {

            loaded = worldConfig.loadWorld();

        } catch (Exception ex) {

            plugin.getLogger().severe("MyWorlds failed to load arena world '" + worldName + "': " + ex.getMessage());
            return null;

        }

        if (loaded == null) {

            plugin.getLogger().severe("MyWorlds could not load arena world '" + worldName + "'.");
            return null;

        }

        WorldConfigStore.saveAll();
        return loaded;

    }

    // Copied folders carry no generator; pin void unless one is set.
    private void applyVoidGenerator(WorldConfig worldConfig) {

        if (!isVoidGeneratorEnabled()) {

            return;

        }

        String existing = worldConfig.getChunkGeneratorName();
        if (existing != null && !existing.isBlank()) {

            return;

        }

        worldConfig.setChunkGeneratorName(plugin.getName() + ":void");

    }

    private boolean unloadIfPresent(String worldName) {

        World live = Bukkit.getWorld(worldName);
        WorldConfig worldConfig = WorldConfig.getIfExists(worldName);
        if (live == null && (worldConfig == null || !worldConfig.isLoaded())) {

            return true;

        }

        if (live != null) {

            evictPlayers(live);

        }

        if (worldConfig != null) {

            return worldConfig.unloadWorld();

        }

        return Bukkit.unloadWorld(live, false);

    }

    private void evictPlayers(World world) {

        if (world.getPlayers().isEmpty()) {

            return;

        }

        World fallback = plugin.getMyWorldsManager().getMainWorld();
        if (fallback == null || fallback.equals(world)) {

            return;

        }

        Location destination = fallback.getSpawnLocation();
        for (Player player : new ArrayList<>(world.getPlayers())) {

            player.teleport(destination);

        }

    }

}
