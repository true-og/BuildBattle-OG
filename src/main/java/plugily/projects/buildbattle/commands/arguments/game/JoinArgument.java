package plugily.projects.buildbattle.commands.arguments.game;

import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import plugily.projects.buildbattle.Main;
import plugily.projects.buildbattle.arena.BaseArena;
import plugily.projects.buildbattle.commands.LobbyList;
import plugily.projects.buildbattle.commands.LobbyResolver;
import plugily.projects.buildbattle.commands.arguments.ArgumentsRegistry;
import plugily.projects.minigamesbox.api.arena.IPluginArena;
import plugily.projects.minigamesbox.classic.commands.arguments.data.CommandArgument;

// Replaces MiniGamesBox's /bb join. With no lobby it lists the lobbies with
// clickable join lines like /hbjoin; with one it accepts BB1, bb1 or a bare 1.
// "maxplayers" still joins the fullest open arena.
public class JoinArgument {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    public JoinArgument(ArgumentsRegistry registry) {

        // MiniGamesBox runs the first mapped argument whose name matches, so the
        // bundled join has to go before ours is mapped.
        final List<CommandArgument> mapped = registry.getMappedArguments().get("buildbattle");
        if (mapped != null) {

            mapped.removeIf(argument -> "join".equalsIgnoreCase(argument.getArgumentName()));

        }

        registry.mapArgument("buildbattle", new CommandArgument("join", "", CommandArgument.ExecutorType.PLAYER) {

            @Override
            public void execute(CommandSender sender, String[] args) {

                final Player player = (Player) sender;
                final Main plugin = (Main) registry.getPlugin();

                if (args.length < 2 || args[1].isBlank()) {

                    LobbyList.send(plugin, player);
                    return;

                }

                if (args[1].equalsIgnoreCase("maxplayers")) {

                    joinFullest(plugin, player);
                    return;

                }

                final BaseArena arena = LobbyResolver.resolve(plugin, args[1]);
                if (arena == null) {

                    player.sendMessage(LEGACY_SERIALIZER.deserialize(
                            plugin.getPluginMessagePrefix() + "&cLobby &e" + args[1] + " &cdoes not exist."));
                    LobbyList.send(plugin, player);
                    return;

                }

                plugin.getArenaManager().joinAttempt(player, arena);

            }

        });

    }

    private static void joinFullest(Main plugin, Player player) {

        IPluginArena best = null;
        for (IPluginArena arena : plugin.getArenaRegistry().getArenas()) {

            if (arena.getPlayers().size() >= arena.getMaximumPlayers()) {

                continue;

            }

            if (best == null || arena.getPlayers().size() > best.getPlayers().size()) {

                best = arena;

            }

        }

        if (best == null) {

            LobbyList.send(plugin, player);
            return;

        }

        plugin.getArenaManager().joinAttempt(player, plugin.getArenaRegistry().getArena(best.getId()));

    }

}
