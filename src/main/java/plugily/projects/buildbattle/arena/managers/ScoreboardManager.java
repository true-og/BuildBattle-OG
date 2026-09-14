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
import plugily.projects.buildbattle.arena.BuildArena;
import plugily.projects.buildbattle.arena.GuessArena;
import plugily.projects.buildbattle.arena.managers.plots.Plot;
import plugily.projects.buildbattle.handlers.misc.ScoreboardOGBridge;
import plugily.projects.buildbattle.handlers.misc.TrueOGBoard;
import plugily.projects.minigamesbox.api.arena.IArenaState;
import plugily.projects.minigamesbox.api.user.IUser;
import plugily.projects.minigamesbox.classic.arena.PluginArena;
import plugily.projects.minigamesbox.classic.arena.managers.PluginScoreboardManager;
import plugily.projects.minigamesbox.classic.handlers.language.MessageBuilder;

// The arena sidebar in the Scoreboard-OG house style. Lines are built here rather
// than read from language.yml so every card fits 1.8 clients and stays branded.
public class ScoreboardManager extends PluginScoreboardManager {

    // Renders the §x hex form MiniGamesBox's colorizer emits.
    private static final LegacyComponentSerializer SECTION_SERIALIZER = LegacyComponentSerializer.builder()
            .character('§').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    private final BaseArena arena;
    // Players whose sidebar Scoreboard-OG renders for this arena.
    private final Set<UUID> claimed = ConcurrentHashMap.newKeySet();

    public ScoreboardManager(PluginArena arena) {

        super(arena);
        this.arena = (BaseArena) arena;

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

        return legacy(TrueOGBoard.TITLE);

    }

    private List<Component> lines(Player player) {

        List<Component> out = new ArrayList<>();
        for (String line : getScoreboardLines(player)) {

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

    // Top to bottom in the network board's layout: a blank, labelled blocks split
    // by blanks, then the site footer. Also feeds the FastBoard fallback.
    @Override
    public List<String> getScoreboardLines(Player player) {

        List<String> lines = new ArrayList<>();
        lines.add("");
        IArenaState state = arena.getArenaState();
        if (state == IArenaState.IN_GAME) {

            if (arena instanceof GuessArena) {

                addGuessLines(lines, player, (GuessArena) arena);

            } else {

                addBuildLines(lines, player);

            }

        } else if (state == IArenaState.ENDING) {

            addEndingLines(lines);

        } else if (state == IArenaState.RESTARTING) {

            lines.add("&cRestarting...");

        } else {

            addLobbyLines(lines);

        }

        lines.add("");
        lines.add(TrueOGBoard.FOOTER);
        return lines;

    }

    private void addLobbyLines(List<String> lines) {

        lines.add("&6Mode:");
        lines.add("&f" + modeName());
        lines.add("");
        lines.add("&eMap:");
        lines.add("&f" + TrueOGBoard.fit(arena.getMapName(), TrueOGBoard.VALUE_WIDTH));
        lines.add("");
        lines.add("&6Players:");
        lines.add("&f" + arena.getPlayers().size() + "&7/&f" + arena.getMaximumPlayers());
        lines.add("");
        lines.add("&6Starting in:");
        IArenaState state = arena.getArenaState();
        if (state == IArenaState.STARTING || state == IArenaState.FULL_GAME) {

            lines.add("&f" + TrueOGBoard.clock(arena.getTimer()));

        } else {

            int needed = Math.max(0, arena.getMinimumPlayers() - arena.getPlayers().size());
            lines.add(needed == 0 ? "&aReady" : "&7Need " + needed + " more");

        }

    }

    // Classic and Teams: theme, phase and clock, then the player's company.
    private void addBuildLines(List<String> lines, Player player) {

        lines.add("&eTheme:");
        lines.add("&f" + TrueOGBoard.fit(theme(player), TrueOGBoard.VALUE_WIDTH));
        lines.add("");
        lines.add("&6Phase:");
        lines.add("&f" + phaseName());
        lines.add("&6Time: &f" + TrueOGBoard.clock(arena.getTimer()));
        lines.add("");

        if (arena.getArenaType() == BaseArena.ArenaType.TEAM) {

            lines.add("&2Teammate:");
            lines.add("&f" + TrueOGBoard.fit(teammate(player), TrueOGBoard.VALUE_WIDTH));

        } else {

            lines.add("&2Players: &f" + arena.getPlayers().size());

        }

        if (arena.getArenaInGameState() == BaseArena.ArenaInGameState.PLOT_VOTING && arena instanceof BuildArena) {

            Plot voting = ((BuildArena) arena).getVotingPlot();
            lines.add("");
            lines.add("&eViewing:");
            lines.add("&f"
                    + TrueOGBoard.fit(voting == null ? "???" : voting.getFormattedMembers(), TrueOGBoard.VALUE_WIDTH));

        }

    }

    // Guess The Build: who builds, what the viewer may know, the round clock and
    // the viewer's points against the leader.
    private void addGuessLines(List<String> lines, Player player, GuessArena guess) {

        Plot buildPlot = guess.getBuildPlot();
        lines.add("&6Builder:");
        lines.add("&f" + TrueOGBoard.fit(
                buildPlot == null || buildPlot.getMembers().isEmpty() ? "???" : buildPlot.getFormattedMembers(),
                TrueOGBoard.VALUE_WIDTH));
        lines.add("");
        lines.add("&eTheme:");
        lines.add("&f" + TrueOGBoard.fit(theme(player), TrueOGBoard.VALUE_WIDTH));
        lines.add("");
        lines.add("&6Round: &f" + guess.getRound());
        lines.add("&6Time: &f" + TrueOGBoard.clock(arena.getTimer()));
        lines.add("");
        Plot own = arena.getPlotManager().getPlot(player);
        lines.add("&bPoints: &f" + TrueOGBoard.compact(own == null ? 0 : own.getPoints()));
        List<Plot> ranking = arena.getPlotManager().getTopPlotsOrder();
        if (!ranking.isEmpty() && ranking.get(0).getPoints() > 0) {

            lines.add("&6Leader:");
            lines.add("&f" + TrueOGBoard.fit(ranking.get(0).getFormattedMembers(), TrueOGBoard.VALUE_WIDTH));

        }

    }

    private void addEndingLines(List<String> lines) {

        lines.add("&cGame over!");
        lines.add("");
        Plot winner = arena.getWinnerPlot();
        if (winner == null) {

            List<Plot> ranking = arena.getPlotManager().getTopPlotsOrder();
            winner = ranking.isEmpty() ? null : ranking.get(0);

        }

        lines.add("&eWinner:");
        lines.add(
                "&f" + TrueOGBoard.fit(winner == null ? "???" : winner.getFormattedMembers(), TrueOGBoard.VALUE_WIDTH));
        lines.add("&bPoints: &f" + TrueOGBoard.compact(winner == null ? 0 : winner.getPoints()));

    }

    private String modeName() {

        switch (arena.getArenaType()) {

            case TEAM:
                return "Teams";
            case GUESS_THE_BUILD:
                return "GuessTheBuild";
            default:
                return "Solo";

        }

    }

    private String phaseName() {

        switch (arena.getArenaInGameState()) {

            case THEME_VOTING:
                return "Theme vote";
            case PLOT_VOTING:
                return "Plot vote";
            case BUILD_TIME:
                return "Building";
            default:
                return "Starting";

        }

    }

    // The arena placeholder hides the theme from Guess The Build guessers.
    private String theme(Player player) {

        String theme = new MessageBuilder("%arena_theme%").arena(arena).player(player).build();
        return theme == null || theme.isEmpty() ? "???" : ChatColor.stripColor(theme);

    }

    private String teammate(Player player) {

        Plot plot = arena.getPlotFromPlayer(player);
        if (plot == null) {

            return "Nobody";

        }

        for (Player member : plot.getMembers()) {

            if (!member.equals(player)) {

                return member.getName();

            }

        }

        return "Nobody";

    }

}
