package plugily.projects.buildbattle.handlers.misc;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import plugily.projects.buildbattle.Main;
import plugily.projects.buildbattle.arena.BaseArena;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ReconnectToMainWorldListener implements Listener {

    private final Main plugin;
    private final Set<UUID> returnOnJoin = ConcurrentHashMap.newKeySet();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    public ReconnectToMainWorldListener(Main plugin) {

        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void rememberReconnect(PlayerJoinEvent event) {

        Player player = event.getPlayer();
        if (plugin.getUserManager().getUsersQuitDuringGame().containsKey(player.getUniqueId())
                || isArenaWorld(player.getWorld()))
        {

            returnOnJoin.add(player.getUniqueId());

        }

    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void returnReconnectToMainWorld(PlayerJoinEvent event) {

        Player player = event.getPlayer();
        if (!returnOnJoin.remove(player.getUniqueId()) && !isArenaWorld(player.getWorld())) {

            return;

        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {

            if (!player.isOnline()) {

                return;

            }

            BaseArena arena = plugin.getArenaRegistry().getArena(player);
            if (arena != null) {

                plugin.getArenaManager().hubLeaveAttempt(player, arena);

            }

            Location savedLoc = plugin.getAndRemovePreJoinLocation(player.getUniqueId());
            if (savedLoc != null && savedLoc.getWorld() != null) {

                player.teleport(savedLoc);
                return;

            }

            World mainWorld = findMainWorld();
            if (mainWorld == null) {

                send(player, "&cNo main world is available.");
                return;

            }

            if (!player.teleport(mainWorld.getSpawnLocation())) {

                send(player, "&cUnable to return you to the hub.");

            }

        }, 1L);

    }

    private boolean isArenaWorld(World world) {

        if (world == null) {

            return false;

        }

        for (World arenaWorld : plugin.getArenaRegistry().getArenaWorlds()) {

            if (arenaWorld != null && arenaWorld.getName().equals(world.getName())) {

                return true;

            }

        }

        return false;

    }

    private World findMainWorld() {

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

    private void send(Player player, String message) {

        player.sendMessage(LEGACY_SERIALIZER.deserialize(plugin.getPluginMessagePrefix() + message));

    }

}
