package plugily.projects.buildbattle.commands;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import plugily.projects.buildbattle.Main;
import plugily.projects.buildbattle.arena.BaseArena;
import plugily.projects.minigamesbox.classic.utils.serialization.InventorySerializer;

// /hub and /lobby. Leaves the arena the player is in and sends them back where
// they came from, or to main spawn. HubCommandListener routes /hub, /lobby and
// /spawn here for anyone inside BuildBattle territory, whichever plugin owns
// the bare label.
public class HubCommand implements CommandExecutor {

    private final Main plugin;
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    public HubCommand(Main plugin) {

        this.plugin = plugin;

    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {

            sender.sendMessage("Only players can use this command.");
            return true;

        }

        handle(player);
        return true;

    }

    public void handle(Player player) {

        BaseArena arena = plugin.getArenaRegistry().getArena(player);
        if (arena != null) {

            plugin.getArenaManager().hubLeaveAttempt(player, arena);
            // The MiniGamesBox snapshot belongs to the arena world's MyWorlds inventory,
            // so it goes back before the teleport home, not over the survival one.
            InventorySerializer.loadInventory(plugin, player);

        }

        Location savedLoc = plugin.getAndRemovePreJoinLocation(player.getUniqueId());
        if (savedLoc != null && savedLoc.getWorld() != null) {

            if (!player.teleport(savedLoc)) {

                send(player, "&cUnable to return you to your previous location.");
                return;

            }

            plugin.getBuilderCreativeManager().revoke(player);
            send(player, "&aReturned to your previous location.");
            return;

        }

        // MyWorlds' main world spawn is what Spawn-OG's /setspawn writes, so this
        // lands on the server spawn.
        World mainWorld = findMainWorld();
        if (mainWorld == null) {

            send(player, "&cNo main world is available.");
            return;

        }

        Location destination = mainWorld.getSpawnLocation();
        if (!player.teleport(destination)) {

            send(player, "&cUnable to return you to the hub.");
            return;

        }

        plugin.getBuilderCreativeManager().revoke(player);
        send(player, "&aReturned to the hub.");

    }

    private World findMainWorld() {

        return plugin.getMyWorldsManager().getMainWorld();

    }

    private void send(Player player, String message) {

        player.sendMessage(LEGACY_SERIALIZER.deserialize(plugin.getPluginMessagePrefix() + message));

    }

}
