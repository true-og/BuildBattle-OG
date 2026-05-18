package plugily.projects.buildbattle.handlers.misc;

import com.bergerkiller.bukkit.mw.MyWorlds;
import com.bergerkiller.bukkit.mw.WorldConfig;
import com.bergerkiller.bukkit.mw.WorldConfigStore;
import com.bergerkiller.bukkit.mw.WorldInventory;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import plugily.projects.buildbattle.Main;
import plugily.projects.buildbattle.arena.BaseArena;
import plugily.projects.minigamesbox.classic.handlers.language.MessageBuilder;
import plugily.projects.minigamesbox.classic.utils.configuration.ConfigUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
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

    private void enableRequiredFeatures() {

        File configFile = new File(myWorlds.getDataFolder(), "config.yml");
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(configFile);

        boolean enableWorldInventories = plugin.getConfig().getBoolean("MyWorlds.Enable-World-Inventories", true);
        boolean enableWorldChat = plugin.getConfig().getBoolean("MyWorlds.Enable-World-Chat", true);
        boolean inventoriesChanged = false;
        boolean chatChanged = false;
        boolean changed = false;

        if (configuration.getBoolean("useWorldInventories") != enableWorldInventories) {

            configuration.set("useWorldInventories", enableWorldInventories);
            inventoriesChanged = true;
            changed = true;

        }

        if (configuration.getBoolean("useWorldChatPermissions") != enableWorldChat) {

            configuration.set("useWorldChatPermissions", enableWorldChat);
            chatChanged = true;
            changed = true;

        }

        if (!changed) {

            return;

        }

        try {

            configuration.save(configFile);
            applyRuntimeChanges(enableWorldInventories, enableWorldChat, inventoriesChanged, chatChanged);
            plugin.getDebugger().debug(
                    "[MyWorlds] Updated My_Worlds config: useWorldInventories={0}, useWorldChatPermissions={1}",
                    enableWorldInventories, enableWorldChat);

        } catch (IOException exception) {

            plugin.getLogger().warning("Failed to update My_Worlds/config.yml: " + exception.getMessage());

        }

    }

    private void applyRuntimeChanges(boolean enableWorldInventories, boolean enableWorldChat,
            boolean inventoriesChanged, boolean chatChanged)
    {

        if (inventoriesChanged) {

            myWorlds.setUseWorldInventories(enableWorldInventories);

        }

        if (tryReloadMyWorldsConfig()) {

            return;

        }

        if (chatChanged) {

            MyWorlds.useWorldChatPermissions = enableWorldChat;
            plugin.getLogger().warning(
                    "My_Worlds does not expose loadConfig() on this version. useWorldChatPermissions was updated in config.yml, "
                            + "but a My_Worlds/server restart may be required for the runtime listener state to match.");

        }

    }

    private boolean tryReloadMyWorldsConfig() {

        try {

            Method loadConfigMethod = myWorlds.getClass().getMethod("loadConfig");
            loadConfigMethod.invoke(myWorlds);
            return true;

        } catch (ReflectiveOperationException ignored) {

            return false;

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
