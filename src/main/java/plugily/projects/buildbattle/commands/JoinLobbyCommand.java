package plugily.projects.buildbattle.commands;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import plugily.projects.buildbattle.Main;
import plugily.projects.buildbattle.arena.BaseArena;

// Joins a BuildBattle lobby by its id (BB1 or 1); with no id the lobby list is
// shown. Maps and themes are not accepted.
public class JoinLobbyCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    public JoinLobbyCommand(Main plugin) {

        this.plugin = plugin;

    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {

            sender.sendMessage("Only players can use this command.");
            return true;

        }

        if (args.length == 0) {

            LobbyList.send(plugin, player);
            return true;

        }

        final BaseArena arena = LobbyResolver.resolve(plugin, args[0]);
        if (arena == null) {

            send(player, "&cLobby &e" + args[0] + " &cdoes not exist.");
            LobbyList.send(plugin, player);
            return true;

        }

        plugin.getArenaManager().joinAttempt(player, arena);
        return true;

    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        if (args.length != 1) {

            return null;

        }

        final String prefix = LobbyResolver.normalize(args[0]);
        final List<String> completions = new ArrayList<>();
        for (String id : LobbyResolver.lobbyIds(plugin)) {

            if (LobbyResolver.normalize(id).startsWith(prefix)) {

                completions.add(id);

            }

        }

        return completions;

    }

    private void send(Player player, String message) {

        player.sendMessage(LEGACY_SERIALIZER.deserialize(plugin.getPluginMessagePrefix() + message));

    }

}
