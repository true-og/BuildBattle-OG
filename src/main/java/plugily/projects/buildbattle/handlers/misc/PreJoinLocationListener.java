package plugily.projects.buildbattle.handlers.misc;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import plugily.projects.buildbattle.Main;

// Players reach a BuildBattle world by more than /bbjoin -- /mw tp, portals and
// other plugins all land here. Without a recorded spot those players get dumped
// at main world spawn instead of where they came from, and without the
// GameModeInventories suspension their survival inventory is at risk on the
// way out.
public class PreJoinLocationListener implements Listener {

    private final Main plugin;

    public PreJoinLocationListener(Main plugin) {

        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {

        Location from = event.getFrom();
        Location to = event.getTo();
        if (from == null || to == null || from.getWorld() == null || to.getWorld() == null) {

            return;

        }

        if (from.getWorld().equals(to.getWorld())) {

            return;

        }

        boolean fromArena = plugin.isArenaWorld(from.getWorld());
        boolean toArena = plugin.isArenaWorld(to.getWorld());

        if (toArena && !fromArena) {

            // ArenaManager.joinAttempt records its own spot before teleporting, so
            // only fill the gap when nothing is stored yet.
            if (!plugin.hasPreJoinLocation(event.getPlayer().getUniqueId())) {

                plugin.savePreJoinLocation(event.getPlayer().getUniqueId(), from);

            }

            // Before the world change: MyWorlds' gamemode restore on entry and every
            // flip MiniGamesBox makes must run without GameModeInventories.
            plugin.getGmiGuard().suspend(event.getPlayer());
            return;

        }

        // Left an arena world for a real one by any route, so the recorded spot has
        // served its purpose. Dropping it stops a stale spot yanking them later.
        if (fromArena && !toArena) {

            plugin.removePreJoinLocation(event.getPlayer().getUniqueId());
            // Stays attached through this teleport's world change (MyWorlds restores
            // the real inventory and then the saved survival gamemode there).
            plugin.getGmiGuard().suspend(event.getPlayer());
            plugin.getGmiGuard().releaseAfterLeaving(event.getPlayer());

        }

    }

    // A respawn is a world change without a teleport event, so the suspension
    // is handled here for both directions.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawnMonitor(PlayerRespawnEvent event) {

        Player player = event.getPlayer();
        Location respawn = event.getRespawnLocation();
        if (respawn == null || respawn.getWorld() == null) {

            return;

        }

        boolean fromArena = plugin.isArenaWorld(player.getWorld());
        boolean toArena = plugin.isArenaWorld(respawn.getWorld());

        if (toArena) {

            plugin.getGmiGuard().suspend(player);
            return;

        }

        if (fromArena) {

            plugin.getGmiGuard().suspend(player);
            plugin.getGmiGuard().releaseAfterLeaving(player);

        }

    }

    // A login inside an arena world: MyWorlds forces the world's gamemode at
    // join, MiniGamesBox may restore a mid-game snapshot, and the return
    // teleport follows a tick later.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onLogin(PlayerLoginEvent event) {

        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {

            return;

        }

        Player player = event.getPlayer();
        if (plugin.isArenaWorld(player.getWorld())) {

            plugin.getGmiGuard().suspend(player);

        }

    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {

        plugin.getGmiGuard().release(event.getPlayer());

    }

    // A player who dies in an arena and respawns after the arena released them
    // would otherwise respawn at main world spawn.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {

        Player player = event.getPlayer();
        if (plugin.getArenaRegistry().getArena(player) != null) {

            return;

        }

        if (!plugin.hasPreJoinLocation(player.getUniqueId())) {

            return;

        }

        Location destination = plugin.getAndRemovePreJoinLocation(player.getUniqueId());
        if (destination != null && destination.getWorld() != null) {

            event.setRespawnLocation(destination);

        }

    }

}
