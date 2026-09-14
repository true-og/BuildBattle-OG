package plugily.projects.buildbattle.handlers.misc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// Pins folder matching, copy sanitizing and the path-traversal guard.
class MapBaseTest {

    @TempDir
    Path temp;

    private File world(String name) throws IOException {

        Path dir = temp.resolve(name);
        Files.createDirectories(dir);
        Files.write(dir.resolve("level.dat"), new byte[] { 1 });
        return dir.toFile();

    }

    @Test
    @DisplayName("exact, case-insensitive and separator-insensitive folder names all resolve")
    void resolvesTolerantSpellings() throws IOException {

        File hub = world("BB1_Hub");
        world("Plaza");

        assertEquals(hub, MapBase.resolveWorldDir(temp.toFile(), "BB1_Hub"));
        assertEquals(hub, MapBase.resolveWorldDir(temp.toFile(), "bb1_hub"));
        assertEquals(hub, MapBase.resolveWorldDir(temp.toFile(), "BB1-hub"));
        assertNull(MapBase.resolveWorldDir(temp.toFile(), "BB1-map"));

    }

    @Test
    @DisplayName("names that could escape the directory are rejected")
    void rejectsPathTraversal() throws IOException {

        world("Plaza");

        assertFalse(MapBase.isSafeWorldName("../Plaza"));
        assertFalse(MapBase.isSafeWorldName(".."));
        assertFalse(MapBase.isSafeWorldName("a/b"));
        assertFalse(MapBase.isSafeWorldName(""));
        assertTrue(MapBase.isSafeWorldName("BB1-map"));
        assertNull(MapBase.resolveWorldDir(temp.toFile(), "../" + temp.getFileName() + "/Plaza"));

    }

    @Test
    @DisplayName("a folder without level.dat is not a world")
    void requiresLevelDat() throws IOException {

        Files.createDirectories(temp.resolve("Empty"));

        assertFalse(MapBase.isWorldDir(temp.resolve("Empty").toFile()));
        assertTrue(MapBase.isWorldDir(world("Plaza")));

    }

    @Test
    @DisplayName("copying a template replaces the destination and drops uid.dat and session.lock")
    void copiesAndSanitizes() throws IOException {

        File source = world("Plaza");
        Files.createDirectories(source.toPath().resolve("region"));
        Files.write(source.toPath().resolve("region/r.0.0.mca"), new byte[] { 7 });
        Files.write(source.toPath().resolve("uid.dat"), new byte[] { 9 });
        Files.write(source.toPath().resolve("session.lock"), new byte[] { 9 });

        Path dest = temp.resolve("BB1-map");
        Files.createDirectories(dest.resolve("region"));
        Files.write(dest.resolve("region/r.5.5.mca"), new byte[] { 3 });

        MapBase.deleteRecursive(dest);
        MapBase.copyRecursive(source.toPath(), dest);
        MapBase.sanitizeWorldFolder(dest.toFile());

        assertTrue(Files.isRegularFile(dest.resolve("level.dat")));
        assertTrue(Files.isRegularFile(dest.resolve("region/r.0.0.mca")));
        assertFalse(Files.exists(dest.resolve("region/r.5.5.mca")), "stale vanilla chunks must not survive");
        assertFalse(Files.exists(dest.resolve("uid.dat")));
        assertFalse(Files.exists(dest.resolve("session.lock")));
        assertTrue(Files.isRegularFile(source.toPath().resolve("uid.dat")), "the template is never modified");

    }

}
