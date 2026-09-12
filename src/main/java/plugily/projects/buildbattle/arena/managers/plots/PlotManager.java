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

package plugily.projects.buildbattle.arena.managers.plots;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import plugily.projects.buildbattle.api.event.plot.PlotPlayerReceiveEvent;
import plugily.projects.buildbattle.arena.BaseArena;
import plugily.projects.minigamesbox.classic.utils.dimensional.Cuboid;
import plugily.projects.minigamesbox.classic.utils.version.VersionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by Tom on 17/08/2015.
 */
public class PlotManager {

    private final List<Plot> plots = new ArrayList<>();
    private final List<Plot> plotsToClear = new ArrayList<>();
    private final BaseArena arena;

    public PlotManager(BaseArena arena) {

        this.arena = arena;

    }

    public void addBuildPlot(Plot buildPlot) {

        plots.add(buildPlot);

    }

    public void removeBuildPlot(Plot buildPlot) {

        plots.remove(buildPlot);

    }

    public Plot getPlot(Player player) {

        if (player != null) {

            for (Plot buildPlot : plots) {

                if (buildPlot.getMembers().contains(player)) {

                    return buildPlot;

                }

            }

        }

        return null;

    }

    public void resetQueuedPlots() {

        plotsToClear.forEach(Plot::fullyResetPlot);
        plotsToClear.clear();

    }

    public boolean isPlotsCleared() {

        return plotsToClear.isEmpty();

    }

    public void resetPlotsGradually() {

        if (isPlotsCleared()) {

            return;

        }

        plotsToClear.remove(0).fullyResetPlot();

    }

    public void teleportToPlots() {

        for (Plot buildPlot : plots) {

            if (!buildPlot.getMembers().isEmpty()) {

                Cuboid cuboid = buildPlot.getCuboid();
                if (cuboid == null) {

                    continue;

                }

                final Location tploc = cuboid.getCenter();
                if (tploc == null) {

                    continue;

                }

                // Block reads stay on the main thread; the walk is bounded by the plot height.
                Location loc = tploc;
                int maxSteps = Math.max(1, cuboid.getMaxPoint().getBlockY() - cuboid.getMinPoint().getBlockY() + 2);
                for (int step = 0; step < maxSteps && loc.getBlock().getType() != Material.AIR; step++) {

                    loc = loc.add(0, 1, 0);
                    // teleporting 1 x and z block away from center cause Y is above plot limit
                    if (loc.getY() >= cuboid.getMaxPoint().getY()) {

                        loc = cuboid.getCenter().clone().add(1, 0, 1);

                    }

                }

                for (Player player : buildPlot.getMembers()) {

                    VersionUtils.teleport(player, loc);
                    // Fix respawning bug while theme voting
                    Bukkit.getScheduler().runTaskLater(arena.getPlugin(), () -> {

                        player.setAllowFlight(true);
                        player.setFlying(true);

                    }, 2);

                }

            }

            Bukkit.getPluginManager().callEvent(new PlotPlayerReceiveEvent(arena, buildPlot));

        }

    }

    public List<Plot> getTopPlotsOrder() {

        List<Plot> plotRanking = plots.stream().filter(plot -> !plot.getMembers().isEmpty())
                .sorted(Comparator.comparingInt(Plot::getPoints)).collect(Collectors.toList());
        Collections.reverse(plotRanking);
        return plotRanking;

    }

    public List<Plot> getPlots() {

        return plots;

    }

}
