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
                return true;

            }

            plugin.getBuilderCreativeManager().revoke(player);
            send(player, "&aReturned to your previous location.");
            return true;

        }

        World mainWorld = findMainWorld();
        if (mainWorld == null) {

            send(player, "&cNo main world is available.");
            return true;

        }

        Location destination = mainWorld.getSpawnLocation();
        if (!player.teleport(destination)) {

            send(player, "&cUnable to return you to the hub.");
            return true;

        }

        plugin.getBuilderCreativeManager().revoke(player);
        send(player, "&aReturned to the hub.");
        return true;

    }

    private World findMainWorld() {

        return plugin.getMyWorldsManager().getMainWorld();

    }

    private void send(Player player, String message) {

        player.sendMessage(LEGACY_SERIALIZER.deserialize(plugin.getPluginMessagePrefix() + message));

    }

}
