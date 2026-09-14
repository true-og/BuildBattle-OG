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

package plugily.projects.buildbattle.handlers.menu.registry;

import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import plugily.projects.buildbattle.arena.BaseArena;
import plugily.projects.buildbattle.arena.managers.plots.Plot;
import plugily.projects.buildbattle.handlers.menu.MenuOption;
import plugily.projects.buildbattle.handlers.menu.OptionsRegistry;
import plugily.projects.minigamesbox.classic.handlers.language.MessageBuilder;
import plugily.projects.minigamesbox.classic.utils.helper.ItemBuilder;
import plugily.projects.minigamesbox.classic.utils.version.xseries.XMaterial;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * @author Plajer
 *         <p>
 *         Created at 23.12.2018
 */
public class FloorChangeOption implements Listener {

    private final Set<UUID> pendingFloorChange = new HashSet<>();
    private final OptionsRegistry registry;

    public FloorChangeOption(OptionsRegistry registry) {

        this.registry = registry;
        registry.getPlugin().getServer().getPluginManager().registerEvents(this, registry.getPlugin());

        registry.registerOption(new MenuOption(14, "FLOOR",
                new ItemBuilder(XMaterial.OAK_LOG.parseItem())
                        .name(new MessageBuilder("MENU_OPTION_CONTENT_FLOOR_ITEM_NAME").asKey().build())
                        .lore(new MessageBuilder("MENU_OPTION_CONTENT_FLOOR_ITEM_LORE").asKey().build()).build())
        {

            @Override
            public void onClick(InventoryClickEvent event) {

                HumanEntity humanEntity = event.getWhoClicked();

                if (!(humanEntity instanceof Player))
                    return;

                Player player = (Player) humanEntity;
                BaseArena arena = registry.getPlugin().getArenaRegistry().getArena(player);
                if (arena == null) {

                    return;

                }

                pendingFloorChange.add(player.getUniqueId());
                player.closeInventory();
                player.sendMessage("§eRight click a block to make it the floor!");

            }

        });

    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {

        Player player = event.getPlayer();
        if (!pendingFloorChange.contains(player.getUniqueId())) {

            return;

        }

        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
                && event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR)
        {

            return;

        }

        BaseArena arena = registry.getPlugin().getArenaRegistry().getArena(player);
        if (arena == null) {

            pendingFloorChange.remove(player.getUniqueId());
            return;

        }

        Plot plot = arena.getPlotManager().getPlot(player);
        if (plot == null) {

            pendingFloorChange.remove(player.getUniqueId());
            return;

        }

        Material material = null;
        byte data = 0;

        ItemStack itemStack = event.getItem();
        if (itemStack != null && itemStack.getType() != Material.AIR) {

            material = itemStack.getType();
            data = XMaterial.matchXMaterial(itemStack).getData();

        } else if (event.getClickedBlock() != null) {

            material = event.getClickedBlock().getType();
            data = event.getClickedBlock().getData();

        }

        if (material == null) {

            return;

        }

        if (material != XMaterial.WATER_BUCKET.parseMaterial() && material != XMaterial.LAVA_BUCKET.parseMaterial()
                && !(material.isBlock() && material.isSolid() && material.isOccluding()))
        {

            new MessageBuilder("IN_GAME_MESSAGES_PLOT_PERMISSION_FLOOR_ITEM").asKey().player(player).sendPlayer();
            pendingFloorChange.remove(player.getUniqueId());
            return;

        }

        if (registry.getPlugin().getBlacklistManager().getFloorList().contains(material)) {

            new MessageBuilder("IN_GAME_MESSAGES_PLOT_PERMISSION_FLOOR_ITEM").asKey().player(player).sendPlayer();
            pendingFloorChange.remove(player.getUniqueId());
            return;

        }

        plot.changeFloor(material, data);
        new MessageBuilder("MENU_OPTION_CONTENT_FLOOR_CHANGED").asKey().player(player).sendPlayer();
        pendingFloorChange.remove(player.getUniqueId());
        event.setCancelled(true);

    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {

        pendingFloorChange.remove(event.getPlayer().getUniqueId());

    }

}
