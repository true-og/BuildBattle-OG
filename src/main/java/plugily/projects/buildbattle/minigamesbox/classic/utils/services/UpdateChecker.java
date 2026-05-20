package plugily.projects.buildbattle.minigamesbox.classic.utils.services;

import java.util.concurrent.CompletableFuture;

import org.bukkit.plugin.java.JavaPlugin;

public final class UpdateChecker {

    public static final VersionScheme VERSION_SCHEME_DECIMAL = (first, second) -> null;

    private static UpdateChecker instance;
    private UpdateResult lastResult;

    private UpdateChecker() {

    }

    public static UpdateChecker init(JavaPlugin plugin, int pluginID, VersionScheme scheme) {

        if (instance == null) {

            instance = new UpdateChecker();

        }

        return instance;

    }

    public static UpdateChecker init(JavaPlugin plugin, int pluginID) {

        return init(plugin, pluginID, VERSION_SCHEME_DECIMAL);

    }

    public static UpdateChecker get() {

        return instance;

    }

    public static boolean isInitialized() {

        return instance != null;

    }

    public CompletableFuture<UpdateResult> requestUpdateCheck() {

        lastResult = new UpdateResult(UpdateReason.UP_TO_DATE, "");
        return CompletableFuture.completedFuture(lastResult);

    }

    public UpdateResult getLastResult() {

        return lastResult;

    }

    public interface VersionScheme {

        String compareVersions(String first, String second);

    }

    public enum UpdateReason {
        NEW_UPDATE, COULD_NOT_CONNECT, INVALID_JSON, UNAUTHORIZED_QUERY, UNRELEASED_VERSION, UNKNOWN_ERROR,
        UNSUPPORTED_VERSION_SCHEME, UP_TO_DATE
    }

    public static final class UpdateResult {

        private final UpdateReason reason;
        private final String newestVersion;

        private UpdateResult(UpdateReason reason, String newestVersion) {

            this.reason = reason;
            this.newestVersion = newestVersion;

        }

        public UpdateReason getReason() {

            return reason;

        }

        public boolean requiresUpdate() {

            return false;

        }

        public String getNewestVersion() {

            return newestVersion;

        }

    }

}
