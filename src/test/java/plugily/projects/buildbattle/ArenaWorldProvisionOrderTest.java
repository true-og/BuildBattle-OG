package plugily.projects.buildbattle;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// Source canary: provision before registerArenas(), keep the registry guard.
class ArenaWorldProvisionOrderTest {

    private static final Path MAIN_SOURCE = Paths.get("src/main/java/plugily/projects/buildbattle/Main.java");
    private static final Path REGISTRY_SOURCE = Paths
            .get("src/main/java/plugily/projects/buildbattle/arena/ArenaRegistry.java");

    private static String sourceWithoutComments(Path path) throws IOException {

        String source = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "");

    }

    @Test
    @DisplayName("arena worlds are provisioned before the arenas are registered")
    void worldsAreProvisionedBeforeArenasRegister() throws IOException {

        String source = sourceWithoutComments(MAIN_SOURCE);

        int provision = source.indexOf("arenaWorldProvisioner.provisionArenaWorlds();");
        int register = source.indexOf("arenaRegistry.registerArenas();");

        assertTrue(provision >= 0, "provisionArenaWorlds() is no longer called from Main");
        assertTrue(register >= 0, "registerArenas() is no longer called from Main");
        assertTrue(provision < register,
                "provisionArenaWorlds() must run before registerArenas(), otherwise MiniGamesBox generates a "
                        + "vanilla world for every arena world that is not loaded yet");

    }

    @Test
    @DisplayName("the registry refuses arenas whose worlds are not loaded")
    void registryGuardsUnloadedWorlds() throws IOException {

        String source = sourceWithoutComments(REGISTRY_SOURCE);

        int guard = source.indexOf("isWorldAvailable(worldName)");
        int delegate = source.indexOf("super.registerArena(id);");

        assertTrue(guard >= 0, "ArenaRegistry no longer checks isWorldAvailable before registering");
        assertTrue(delegate >= 0, "ArenaRegistry no longer delegates to super.registerArena(id)");
        assertTrue(guard < delegate, "the world check must run before super.registerArena(id)");

    }

}
