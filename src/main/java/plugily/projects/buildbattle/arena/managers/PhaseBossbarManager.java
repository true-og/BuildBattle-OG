package plugily.projects.buildbattle.arena.managers;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import plugily.projects.buildbattle.arena.BaseArena;
import plugily.projects.buildbattle.handlers.misc.TrueOGBoard;
import plugily.projects.minigamesbox.api.arena.IArenaState;
import plugily.projects.minigamesbox.api.arena.IPluginArena;
import plugily.projects.minigamesbox.classic.arena.managers.BossbarManager;

// The arena boss bar as a phase timer with true-og.net in its title.
public class PhaseBossbarManager extends BossbarManager {

    private static final String SITE = " &8| &e"
            + ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', TrueOGBoard.FOOTER));

    private final BaseArena arena;
    // One bar per arena; every player and spectator watches the same phase.
    private final BossBar bar;

    public PhaseBossbarManager(BaseArena arena) {

        super(arena);
        this.arena = arena;
        this.bar = Bukkit.createBossBar("", BarColor.WHITE, BarStyle.SOLID);

    }

    @Override
    public void doBarAction(IPluginArena.IBarAction action, Player player) {

        if (!arena.getPlugin().getConfigPreferences().getOption("BOSSBAR")) {

            return;

        }

        if (action == IPluginArena.IBarAction.ADD) {

            bar.addPlayer(player);
            bossBarUpdate();

        } else {

            bar.removePlayer(player);

        }

    }

    // Called once per arena second, after the state handlers have run.
    @Override
    public void bossBarUpdate() {

        if (bar.getPlayers().isEmpty()) {

            return;

        }

        bar.setColor(color());
        bar.setTitle(ChatColor.translateAlternateColorCodes('&', title() + SITE));
        setProgress(progress());

    }

    @Override
    public void setProgress(double progress) {

        bar.setProgress(Math.max(0.0, Math.min(1.0, progress)));

    }

    public void removeAll() {

        bar.removeAll();

    }

    private String title() {

        final String clock = " &f" + TrueOGBoard.clock(arena.getTimer());
        switch (arena.getArenaState()) {

            case WAITING_FOR_PLAYERS:
                return "&eWaiting for players &7(" + arena.getPlayers().size() + "/" + arena.getMinimumPlayers() + ")";
            case STARTING:
            case FULL_GAME:
                return "&aStarting in" + clock;
            case IN_GAME:
                return "&6" + phaseName() + clock;
            case ENDING:
                return "&cGame over" + clock;
            case RESTARTING:
                return "&cRestarting" + clock;
            default:
                return "&e" + TrueOGBoard.fit(arena.getMapName(), 32);

        }

    }

    // Guess The Build reuses the build states for pick, guess and round break.
    private String phaseName() {

        boolean guess = arena.getArenaType() == BaseArena.ArenaType.GUESS_THE_BUILD;
        switch (arena.getArenaInGameState()) {

            case THEME_VOTING:
                return guess ? "Builder picks a theme" : "Theme vote";
            case BUILD_TIME:
                return guess ? "Guess the build" : "Building";
            case PLOT_VOTING:
                return guess ? "Next round in" : "Plot vote";
            default:
                return "Starting";

        }

    }

    private BarColor color() {

        IArenaState state = arena.getArenaState();
        if (state == IArenaState.IN_GAME) {

            switch (arena.getArenaInGameState()) {

                case BUILD_TIME:
                    return BarColor.GREEN;
                case PLOT_VOTING:
                    return BarColor.BLUE;
                default:
                    return BarColor.YELLOW;

            }

        }

        if (state == IArenaState.ENDING || state == IArenaState.RESTARTING) {

            return BarColor.RED;

        }

        return state == IArenaState.WAITING_FOR_PLAYERS ? BarColor.WHITE : BarColor.GREEN;

    }

    // Full while waiting, otherwise the share of the phase still to run.
    private double progress() {

        if (arena.getArenaState() == IArenaState.WAITING_FOR_PLAYERS) {

            return 1.0;

        }

        int length = arena.getPhaseLength();
        if (length <= 0) {

            return 1.0;

        }

        return (double) arena.getTimer() / length;

    }

}
