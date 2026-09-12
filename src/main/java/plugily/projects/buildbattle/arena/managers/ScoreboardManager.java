/*
 *
 * BuildBattle - Ultimate building competition minigame
 * Copyright (C) 2022 Plugily Projects - maintained by Tigerpanzer_02 and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package plugily.projects.buildbattle.arena.managers;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import plugily.projects.buildbattle.arena.BaseArena;
import plugily.projects.buildbattle.arena.GuessArena;
import plugily.projects.buildbattle.handlers.misc.ScoreboardOGBridge;
import plugily.projects.minigamesbox.api.user.IUser;
import plugily.projects.minigamesbox.classic.arena.PluginArena;
import plugily.projects.minigamesbox.classic.arena.managers.PluginScoreboardManager;
import plugily.projects.minigamesbox.classic.handlers.language.MessageBuilder;

/**
 * @author Tigerpanzer_02
 *         <p>
 *         Created at 19.12.2021
 */
public class ScoreboardManager extends PluginScoreboardManager {

    // Renders the §x hex form MiniGamesBox's colorizer emits.
    private static final LegacyComponentSerializer SECTION_SERIALIZER = LegacyComponentSerializer.builder()
            .character('§').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    private final PluginArena arena;
    // Players whose sidebar Scoreboard-OG renders for this arena.
    private final Set<UUID> claimed = ConcurrentHashMap.newKeySet();

    public ScoreboardManager(PluginArena arena) {

        super(arena);
        this.arena = arena;

    }

    // With Scoreboard-OG present the arena board rides its sidebar and the network
    // board returns by itself on leave. Without it the MiniGamesBox board is used.
    @Override
    public void createScoreboard(IUser user) {

        Player player = user.getPlayer();
        if (player == null || !ScoreboardOGBridge.isAvailable()) {

            super.createScoreboard(user);
            return;

        }

        claimed.add(player.getUniqueId());
        ScoreboardOGBridge.claim(player, this::title, this::lines);

    }

    // Scoreboard-OG polls the provider itself, so only the FastBoards need pushing.
    @Override
    public void updateScoreboards() {

        if (!ScoreboardOGBridge.isAvailable()) {

            super.updateScoreboards();

        }

    }

    @Override
    public void removeScoreboard(IUser user) {

        Player player = user.getPlayer();
        if (player != null && claimed.remove(player.getUniqueId())) {

            ScoreboardOGBridge.release(player);

        }

        super.removeScoreboard(user);

    }

    @Override
    public void stopAllScoreboards() {

        for (UUID playerId : new ArrayList<>(claimed)) {

            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {

                ScoreboardOGBridge.release(player);

            }

        }

        claimed.clear();
        super.stopAllScoreboards();

    }

    private Component title(Player player) {

        return legacy(new MessageBuilder("SCOREBOARD_TITLE").asKey().arena(arena).player(player).build());

    }

    private List<Component> lines(Player player) {

        List<Component> out = new ArrayList<>();
        for (String line : formatScoreboardLines(getScoreboardLines(player), player)) {

            out.add(legacy(line));

        }

        return out;

    }

    private static Component legacy(String text) {

        if (text == null || text.isEmpty()) {

            return Component.empty();

        }

        return SECTION_SERIALIZER.deserialize(ChatColor.translateAlternateColorCodes('&', text));

    }

    @Override
    public List<String> getScoreboardLines(Player player) {

        List<String> lines;
        switch (arena.getArenaState()) {

            case IN_GAME: {

                if (arena instanceof GuessArena) {

                    lines = arena.getPlugin().getLanguageManager().getLanguageList("Scoreboard.Content."
                            + arena.getArenaState().getFormattedName() + ".Guess-The-Build"
                            + (((GuessArena) arena).getArenaInGameState() == BaseArena.ArenaInGameState.PLOT_VOTING
                                    ? "-Waiting"
                                    : ""));

                } else {

                    if (arena.getArenaOption("PLOT_MEMBER_SIZE") <= 1) {

                        lines = arena.getPlugin().getLanguageManager().getLanguageList(
                                "Scoreboard.Content." + arena.getArenaState().getFormattedName() + ".Classic");

                    } else {

                        lines = arena.getPlugin().getLanguageManager().getLanguageList(
                                "Scoreboard.Content." + arena.getArenaState().getFormattedName() + ".Teams");

                    }

                }

                break;

            }
            case ENDING: {

                if (arena instanceof GuessArena) {

                    lines = arena.getPlugin().getLanguageManager().getLanguageList(
                            "Scoreboard.Content." + arena.getArenaState().getFormattedName() + ".Guess-The-Build");

                } else {

                    lines = arena.getPlugin().getLanguageManager().getLanguageList(
                            "Scoreboard.Content." + arena.getArenaState().getFormattedName() + ".Classic");

                }

                break;

            }
            default: {

                lines = super.getScoreboardLines(player);

            }

        }

        return lines;

    }

}
