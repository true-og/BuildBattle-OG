package plugily.projects.buildbattle.commands;

import java.util.List;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import plugily.projects.buildbattle.Main;
import plugily.projects.minigamesbox.api.arena.IArenaState;
import plugily.projects.minigamesbox.api.arena.IPluginArena;

// The lobby list players see from /bb join and /bbjoin with no lobby named,
// TheHerobrine-OG style: one clickable line per arena with its state and fill.
public final class LobbyList {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    private LobbyList() {

    }

    public static void send(Main plugin, Player player) {

        final List<IPluginArena> arenas = plugin.getArenaRegistry().getArenas();
        if (arenas.isEmpty()) {

            send(player, plugin, "&cThere are no BuildBattle lobbies. Create one with /bba setup create <BB1>.");
            return;

        }

        send(player, plugin, "&6Join a lobby with /bb join <id>.");
        send(player, plugin, "&6Lobbies available to join:");

        final boolean overfill = player.hasPermission("buildbattle.fullgames");
        for (IPluginArena arena : arenas) {

            final String id = arena.getId();
            final int fill = arena.getPlayers().size();
            final int max = arena.getMaximumPlayers();
            final String line;
            final String hover;
            switch (arena.getArenaState()) {

                case WAITING_FOR_PLAYERS, STARTING, FULL_GAME -> {

                    final boolean room = fill < max;
                    final String state = arena.getArenaState() == IArenaState.WAITING_FOR_PLAYERS ? "&e&lWAITING "
                            : "&d&lSTARTING ";
                    line = "&b" + id + ": &e" + fill + "/" + max + "&7 - &r"
                            + (room ? state + "&r&a(JOIN)" : "&c&lFULL &r" + (overfill ? "&a(JOIN)" : ""));
                    hover = room || overfill ? "&6Click here to join &b" + id : null;

                }
                case IN_GAME -> {

                    line = "&b" + id + ": &e" + fill + " playing&8 - &b&lLIVE &2(SPECTATE)";
                    hover = "&6Click here to spectate &b" + id;

                }
                case ENDING -> {

                    line = "&b" + id + ": &8&lENDING";
                    hover = null;

                }
                default -> {

                    line = "&b" + id + ": &8&lRESTARTING";
                    hover = null;

                }

            }

            TextComponent component = LEGACY_SERIALIZER.deserialize(plugin.getPluginMessagePrefix() + line);
            if (hover != null) {

                component = component
                        .hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT,
                                LEGACY_SERIALIZER.deserialize(hover)))
                        .clickEvent(ClickEvent.runCommand("/bb join " + id));

            } else {

                component = component.hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT,
                        LEGACY_SERIALIZER.deserialize("&cThis lobby cannot be joined right now.")));

            }

            player.sendMessage(component);

        }

    }

    private static void send(Player player, Main plugin, String message) {

        player.sendMessage(LEGACY_SERIALIZER.deserialize(plugin.getPluginMessagePrefix() + message));

    }

}
