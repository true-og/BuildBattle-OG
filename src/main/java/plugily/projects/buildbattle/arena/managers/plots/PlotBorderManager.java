/*
 * Derived from the EternalCombat (https://github.com/EternalCodeTeam/EternalCombat) border module
 * by the EternalCode Team, licensed under the Apache License, Version 2.0, by way of the
 * Duels-OG adaptation by the TrueOG Network.
 *
 * Modifications by the TrueOG Network for BuildBattle-OG:
 *   - The legal area is a plot cuboid grown by a grace margin and the wall is the one-block
 *     shell just outside it on all six faces, instead of the inner face of a PvP region.
 *   - Blocks are sent with Paper's Player#sendMultiBlockChange instead of PacketEvents and
 *     restored from the live world instead of a chunk-snapshot cache.
 *   - EternalCombat's rainbow hue function is kept and mapped onto Bukkit materials.
 *   - A builder found past the wall is pulled to the nearest legal spot at their own height,
 *     or to the plot spawn when that is blocked, in place of the region eject.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package plugily.projects.buildbattle.arena.managers.plots;

import io.papermc.paper.math.BlockPosition;
import io.papermc.paper.math.Position;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import plugily.projects.buildbattle.Main;
import plugily.projects.buildbattle.arena.BaseArena;
import plugily.projects.buildbattle.arena.GuessArena;
import plugily.projects.minigamesbox.api.arena.IArenaState;
import plugily.projects.minigamesbox.classic.handlers.language.MessageBuilder;
import plugily.projects.minigamesbox.classic.utils.dimensional.Cuboid;
import plugily.projects.minigamesbox.classic.utils.version.VersionUtils;

import java.awt.Color;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Per-viewer wall around the plot being built in, and the pull-back past it.
public class PlotBorderManager implements Listener {

    private static final long UPDATE_TICKS = 10L;
    private static final String RAINBOW_PREFIX = "RAINBOW_";

    private final Main plugin;
    private final int grace;
    private final int distance;
    private final boolean wallEnabled;
    // Null for a rainbow wall; otherwise the block every wall point shows.
    private BlockData fixedBlock;
    // Rainbow palette: one stained block per dye colour.
    private final Map<DyeColor, BlockData> palette = new EnumMap<>(DyeColor.class);
    private final Map<UUID, Shown> shown = new HashMap<>();

    public PlotBorderManager(Main plugin) {

        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();
        grace = Math.max(0, config.getInt("Plot.Border.Grace", 3));
        distance = Math.max(1, config.getInt("Plot.Border.Distance", 8));
        wallEnabled = config.getBoolean("Plot.Border.Enabled", true)
                && resolveBlock(config.getString("Plot.Border.Block", "RAINBOW_GLASS"));
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshAll, UPDATE_TICKS, UPDATE_TICKS);

    }

    // RAINBOW_<FAMILY> builds a dye palette; anything else is a block name.
    private boolean resolveBlock(String name) {

        String upper = name.toUpperCase(Locale.ROOT);
        if (upper.startsWith(RAINBOW_PREFIX)) {

            String family = upper.substring(RAINBOW_PREFIX.length());
            String suffix = family.equals("GLASS") ? "_STAINED_GLASS" : "_" + family;
            for (DyeColor dye : DyeColor.values()) {

                Material material = Material.matchMaterial(dye.name() + suffix);
                if (material == null || !material.isBlock()) {

                    plugin.getLogger().warning(
                            "Plot.Border.Block " + name + " has no " + dye.name() + suffix + "; wall disabled.");
                    return false;

                }

                palette.put(dye, material.createBlockData());

            }

            return true;

        }

        Material material = Material.matchMaterial(upper);
        if (material == null || !material.isBlock()) {

            plugin.getLogger().warning("Plot.Border.Block " + name + " is not a block; wall disabled.");
            return false;

        }

        fixedBlock = material.createBlockData();
        return true;

    }

    // A move ending outside the legal box is snapped back; otherwise redraw.
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()))
        {

            return;

        }

        Player player = event.getPlayer();
        Confinement confinement = confinementOf(player);
        if (confinement == null) {

            clear(player);
            return;

        }

        if (!confinement.box.contains(to)) {

            if (confinement.box.contains(from)) {

                // The wall failed; cancelling puts them back where they were.
                event.setCancelled(true);
                return;

            }

            pullBack(player, confinement);
            return;

        }

        update(player, confinement, to);

    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {

        Location to = event.getTo();
        if (to == null) {

            return;

        }

        Player player = event.getPlayer();
        Confinement confinement = confinementOf(player);
        if (confinement == null || !to.getWorld().equals(confinement.world)) {

            clear(player);
            return;

        }

        update(player, confinement, to);

    }

    // A world change resends chunks, so the client already lost the wall.
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {

        shown.remove(event.getPlayer().getUniqueId());

    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {

        shown.remove(event.getPlayer().getUniqueId());

    }

    // The three-second arena check: anyone beyond the wall is brought back.
    public void enforce(Player player) {

        Confinement confinement = confinementOf(player);
        if (confinement == null) {

            return;

        }

        Location at = player.getLocation();
        if (!at.getWorld().equals(confinement.world) || !confinement.box.contains(at)) {

            pullBack(player, confinement);

        }

    }

    // Restores every wall still on a client; used on disable.
    public void clearAll() {

        for (UUID uuid : new HashSet<>(shown.keySet())) {

            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {

                clear(player);

            } else {

                shown.remove(uuid);

            }

        }

    }

    private void refreshAll() {

        for (Player player : plugin.getServer().getOnlinePlayers()) {

            Confinement confinement = confinementOf(player);
            if (confinement == null) {

                clear(player);
                continue;

            }

            Location at = player.getLocation();
            if (!at.getWorld().equals(confinement.world)) {

                clear(player);
                continue;

            }

            update(player, confinement, at);

        }

    }

    // The plot a player is held to right now, or null while they may roam.
    private Confinement confinementOf(Player player) {

        if (plugin.getConfigPreferences().getOption("PLOT_MOVE_OUTSIDE")) {

            return null;

        }

        BaseArena arena = plugin.getArenaRegistry().getArena(player);
        if (arena == null || arena.getArenaState() != IArenaState.IN_GAME
                || arena.getArenaInGameState() != BaseArena.ArenaInGameState.BUILD_TIME
                || !arena.getPlayersLeft().contains(player))
        {

            return null;

        }

        Plot plot = arena instanceof GuessArena ? ((GuessArena) arena).getBuildPlot() : arena.getPlotFromPlayer(player);
        if (plot == null || plot.getCuboid() == null) {

            return null;

        }

        Cuboid cuboid = plot.getCuboid();
        Location min = cuboid.getMinPoint();
        Location max = cuboid.getMaxPoint();
        if (min.getWorld() == null) {

            return null;

        }

        Box box = new Box(min.getBlockX() - grace, min.getBlockY() - grace, min.getBlockZ() - grace,
                max.getBlockX() + grace, max.getBlockY() + grace, max.getBlockZ() + grace);
        return new Confinement(arena, plot, min.getWorld(), box);

    }

    // Sends nearby wall points and restores the ones left behind.
    private void update(Player player, Confinement confinement, Location at) {

        UUID uuid = player.getUniqueId();
        Set<BlockPosition> next = wallEnabled ? wallPoints(confinement, at) : Collections.emptySet();
        Shown current = shown.get(uuid);
        Map<Position, BlockData> changes = new HashMap<>();
        if (current != null) {

            for (BlockPosition point : current.points) {

                if (!next.contains(point)) {

                    changes.put(point, restoreData(current.world, point));

                }

            }

        }

        for (BlockPosition point : next) {

            changes.put(point, wallData(point));

        }

        if (!changes.isEmpty()) {

            player.sendMultiBlockChange(changes);

        }

        if (next.isEmpty()) {

            shown.remove(uuid);

        } else {

            shown.put(uuid, new Shown(confinement.world, next));

        }

    }

    private void clear(Player player) {

        Shown current = shown.remove(player.getUniqueId());
        if (current == null || !player.getWorld().equals(current.world)) {

            return;

        }

        Map<Position, BlockData> changes = new HashMap<>();
        for (BlockPosition point : current.points) {

            changes.put(point, restoreData(current.world, point));

        }

        player.sendMultiBlockChange(changes);

    }

    // The shell just outside the legal box, clipped to the view range.
    private Set<BlockPosition> wallPoints(Confinement confinement, Location at) {

        Box box = confinement.box;
        int px = at.getBlockX();
        int py = at.getBlockY();
        int pz = at.getBlockZ();
        int x0 = Math.max(box.minX - 1, px - distance);
        int x1 = Math.min(box.maxX + 1, px + distance);
        int y0 = Math.max(box.minY - 1, py - distance);
        int y1 = Math.min(box.maxY + 1, py + distance);
        int z0 = Math.max(box.minZ - 1, pz - distance);
        int z1 = Math.min(box.maxZ + 1, pz + distance);
        Set<BlockPosition> points = new HashSet<>();
        if (x0 > x1 || y0 > y1 || z0 > z1) {

            return points;

        }

        for (int x : new int[] { box.minX - 1, box.maxX + 1 }) {

            if (x < x0 || x > x1) {

                continue;

            }

            for (int y = y0; y <= y1; y++) {

                for (int z = z0; z <= z1; z++) {

                    addPoint(points, confinement.world, at, x, y, z);

                }

            }

        }

        for (int y : new int[] { box.minY - 1, box.maxY + 1 }) {

            if (y < y0 || y > y1) {

                continue;

            }

            for (int x = x0; x <= x1; x++) {

                for (int z = z0; z <= z1; z++) {

                    addPoint(points, confinement.world, at, x, y, z);

                }

            }

        }

        for (int z : new int[] { box.minZ - 1, box.maxZ + 1 }) {

            if (z < z0 || z > z1) {

                continue;

            }

            for (int x = x0; x <= x1; x++) {

                for (int y = y0; y <= y1; y++) {

                    addPoint(points, confinement.world, at, x, y, z);

                }

            }

        }

        return points;

    }

    // Only non-solid blocks in range become wall; real blocks stay.
    private void addPoint(Set<BlockPosition> points, World world, Location at, int x, int y, int z) {

        double dx = x + 0.5 - at.getX();
        double dy = y + 0.5 - at.getY();
        double dz = z + 0.5 - at.getZ();
        if (dx * dx + dy * dy + dz * dz > (double) distance * distance) {

            return;

        }

        if (world.getBlockAt(x, y, z).getType().isSolid()) {

            return;

        }

        points.add(Position.block(x, y, z));

    }

    private BlockData wallData(BlockPosition point) {

        if (fixedBlock != null) {

            return fixedBlock;

        }

        return palette.get(nearestDye(rainbow(point.blockX(), point.blockY(), point.blockZ())));

    }

    private static BlockData restoreData(World world, BlockPosition point) {

        return world.getBlockAt(point.blockX(), point.blockY(), point.blockZ()).getBlockData();

    }

    // EternalCombat's hue: a slow sine over x and z, brighter with height.
    private static Color rainbow(int x, int y, int z) {

        float hue = (float) ((Math.sin(x * 0.05) + Math.cos(z * 0.05)) * 0.5 + 0.5);
        float brightness = 0.8f + 0.2f * Math.max(0.0f, Math.min(1.0f, (float) y / 255));
        return Color.getHSBColor(hue, 1.0f, brightness);

    }

    private static DyeColor nearestDye(Color rgb) {

        int best = Integer.MAX_VALUE;
        DyeColor dye = DyeColor.WHITE;
        for (DyeColor candidate : DyeColor.values()) {

            org.bukkit.Color color = candidate.getColor();
            int delta = Math.abs(color.getRed() - rgb.getRed()) + Math.abs(color.getGreen() - rgb.getGreen())
                    + Math.abs(color.getBlue() - rgb.getBlue());
            if (delta < best) {

                best = delta;
                dye = candidate;

            }

        }

        return dye;

    }

    // Nearest legal spot at the player's height, or the plot spawn if blocked.
    private void pullBack(Player player, Confinement confinement) {

        Box box = confinement.box;
        Location at = player.getLocation();
        double x = Math.min(Math.max(at.getX(), box.minX + 0.5), box.maxX + 0.5);
        double y = Math.min(Math.max(at.getY(), box.minY), box.maxY - 1.0);
        double z = Math.min(Math.max(at.getZ(), box.minZ + 0.5), box.maxZ + 0.5);
        Location target = new Location(confinement.world, x, y, z, at.getYaw(), at.getPitch());
        if (target.getBlock().getType().isSolid() || target.clone().add(0, 1, 0).getBlock().getType().isSolid()) {

            target = confinement.plot.getTeleportLocation();

        }

        VersionUtils.teleport(player, target);
        new MessageBuilder("IN_GAME_MESSAGES_PLOT_PERMISSION_OUTSIDE").asKey().arena(confinement.arena).player(player)
                .sendPlayer();

    }

    // Inclusive block bounds a player may occupy.
    private static final class Box {

        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;

        private Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;

        }

        private boolean contains(Location location) {

            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;

        }

    }

    private static final class Confinement {

        private final BaseArena arena;
        private final Plot plot;
        private final World world;
        private final Box box;

        private Confinement(BaseArena arena, Plot plot, World world, Box box) {

            this.arena = arena;
            this.plot = plot;
            this.world = world;
            this.box = box;

        }

    }

    private static final class Shown {

        private final World world;
        private final Set<BlockPosition> points;

        private Shown(World world, Set<BlockPosition> points) {

            this.world = world;
            this.points = points;

        }

    }

}
