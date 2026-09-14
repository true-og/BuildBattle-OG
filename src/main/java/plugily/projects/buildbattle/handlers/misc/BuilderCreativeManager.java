package plugily.projects.buildbattle.handlers.misc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;

import plugily.projects.buildbattle.Main;

// Makes the arena worlds the only place on the server where a regular player
// may hold creative mode, and only as the builder.
//
// The GameModeInventories-OG inventory swap itself is suspended by
// GameModeInventoriesGuard for the whole stay in arena territory, whichever
// route the player took in. This class only layers the builder grant on top:
// gamemodeinventories.anywhere sanctions creative and stops the forced-survival
// watchdogs, gamemodeinventories.bypass lifts creative item restrictions inside
// the plots with GMI's default bypass flags. Both are stripped the moment the
// builder leaves creative by any route: round rotation, arena leave, game end,
// world change, quit.
public class BuilderCreativeManager implements Listener {

    private static final String GMI_ANYWHERE_PERMISSION = "gamemodeinventories.anywhere";
    private static final String GMI_BYPASS_PERMISSION = "gamemodeinventories.bypass";

    private final Main plugin;
    private final Map<UUID, PermissionAttachment> builders = new ConcurrentHashMap<>();

    public BuilderCreativeManager(Main plugin) {

        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

    }

    // Suspends GameModeInventories swapping for an arena participant. Idempotent,
    // and a no-op for anyone the world-entry listener already covered.
    public void enterArena(Player player) {

        plugin.getGmiGuard().suspend(player);

    }

    // Puts an arena builder into creative mode. Refused when the player is not
    // inside an arena world, so BuildBattle can never become a creative exemption
    // anywhere else on the server.
    public void grantBuilderCreative(Player player) {

        if (!plugin.isArenaWorld(player.getWorld())) {

            plugin.getDebugger().debug("[BuilderCreative] Refusing creative for {0}: world {1} is not an arena world.",
                    player.getName(), player.getWorld().getName());
            return;

        }

        // Whatever route put them here, the swap must be off before the flip.
        plugin.getGmiGuard().suspend(player);
        PermissionAttachment attachment = builders.computeIfAbsent(player.getUniqueId(),
                id -> player.addAttachment(plugin));
        attachment.setPermission(GMI_ANYWHERE_PERMISSION, true);
        attachment.setPermission(GMI_BYPASS_PERMISSION, true);

        player.setGameMode(GameMode.CREATIVE);
        if (player.getGameMode() != GameMode.CREATIVE) {

            plugin.getDebugger().debug(
                    "[BuilderCreative] Another plugin cancelled the creative switch for {0}; dropping builder perms.",
                    player.getName());
            endBuilderCreative(player);

        }

    }

    // The player is done with the arena. The builder grant goes at once; the
    // swap suspension is only lifted once they are actually outside arena
    // territory, a tick after the teleport out, so the MyWorlds gamemode restore
    // on the way home runs under it. Safe to call for players that never had
    // either.
    public void revoke(Player player) {

        endBuilderCreative(player);
        plugin.getGmiGuard().releaseAfterLeaving(player);

    }

    public void revokeAll() {

        for (UUID playerId : builders.keySet()) {

            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {

                endBuilderCreative(player);

            } else {

                builders.remove(playerId);

            }

        }

        plugin.getGmiGuard().releaseAll();

    }

    // The builder left creative by any route (round rotation to adventure, the
    // MiniGamesBox end-of-game restore to survival, an admin /gamemode). The
    // demotion is deferred a tick so the inventory restore that triggered the
    // change finishes first.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {

        Player player = event.getPlayer();
        if (event.getNewGameMode() == GameMode.CREATIVE || !builders.containsKey(player.getUniqueId())) {

            return;

        }

        Bukkit.getScheduler().runTask(plugin, () -> {

            if (player.getGameMode() != GameMode.CREATIVE) {

                endBuilderCreative(player);

            }

        });

    }

    // A builder left the arena worlds still in creative (a respawn or another
    // plugin's teleport, since the arena's own exits demote first). Flipped to
    // survival while the swap suspension still holds, then the grant goes.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {

        Player player = event.getPlayer();
        if (!builders.containsKey(player.getUniqueId()) || plugin.isArenaWorld(player.getWorld())) {

            return;

        }

        if (player.getGameMode() == GameMode.CREATIVE) {

            player.setGameMode(GameMode.SURVIVAL);

        }

        endBuilderCreative(player);

    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {

        endBuilderCreative(event.getPlayer());

    }

    private void endBuilderCreative(Player player) {

        if (player == null) {

            return;

        }

        PermissionAttachment attachment = builders.remove(player.getUniqueId());
        if (attachment == null) {

            return;

        }

        try {

            attachment.remove();

        } catch (IllegalArgumentException ignored) {

            // The player instance is already gone; the attachment died with it.

        }

    }

}
