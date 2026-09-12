package plugily.projects.buildbattle.commands;

import java.util.Locale;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import plugily.projects.buildbattle.Main;

// Claims /hub, /lobby and /spawn for arena members and anyone standing in a
// configured BuildBattle world. BuildBattle-OG, Splegg-OG and TheHerobrine-OG
// all declare /hub, and Bukkit hands the bare label to whichever registered
// first, so without this a BuildBattle player's /hub could run another
// minigame's command and teleport them out while the arena still counted them;
// /spawn from Spawn-OG did the same. Everywhere else the event is left alone.
public class HubCommandListener implements Listener {

    private final Main plugin;
    private final HubCommand hubCommand;

    public HubCommandListener(Main plugin, HubCommand hubCommand) {

        this.plugin = plugin;
        this.hubCommand = hubCommand;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {

        final String label = commandLabel(event.getMessage());
        if (!label.equals("hub") && !label.equals("lobby") && !label.equals("spawn")) {

            return;

        }

        final Player player = event.getPlayer();
        if (plugin.getArenaRegistry().getArena(player) == null
                && !plugin.getMyWorldsManager().isConfiguredArenaWorld(player.getWorld().getName()))
        {

            return;

        }

        event.setCancelled(true);
        hubCommand.handle(player);

    }

    // The bare command name in lower case, without the leading slash or a
    // plugin namespace such as buildbattle-og:hub.
    static String commandLabel(String message) {

        if (message == null || message.length() < 2 || message.charAt(0) != '/') {

            return "";

        }

        final String[] parts = message.substring(1).trim().split("\\s+");
        if (parts.length == 0) {

            return "";

        }

        String label = parts[0].toLowerCase(Locale.ROOT);
        final int colon = label.indexOf(':');
        if (colon >= 0) {

            label = label.substring(colon + 1);

        }

        return label;

    }

}
