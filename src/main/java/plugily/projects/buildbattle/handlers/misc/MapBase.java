package plugily.projects.buildbattle.handlers.misc;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;

// File helpers for <MapBase>/<Name>/ maps; no Bukkit dependency.
public final class MapBase {

    private MapBase() {

    }

    // Rejects names that could escape the map base or world container.
    public static boolean isSafeWorldName(@Nullable String name) {

        if (name == null || name.isBlank()) {

            return false;

        }

        if (name.equals(".") || name.equals("..")) {

            return false;

        }

        return name.indexOf('/') < 0 && name.indexOf('\\') < 0 && name.indexOf('\0') < 0;

    }

    // Exact, then case-insensitive, then separator-insensitive lookup.
    public static @Nullable File resolveWorldDir(@Nullable File baseDir, @Nullable String name) {

        if (baseDir == null || !isSafeWorldName(name)) {

            return null;

        }

        File exact = new File(baseDir, name);
        if (exact.isDirectory()) {

            return exact;

        }

        File[] candidates = baseDir.listFiles(File::isDirectory);
        if (candidates == null) {

            return null;

        }

        for (File candidate : candidates) {

            if (candidate.getName().equalsIgnoreCase(name)) {

                return candidate;

            }

        }

        String flattened = flattenName(name);
        for (File candidate : candidates) {

            if (flattenName(candidate.getName()).equals(flattened)) {

                return candidate;

            }

        }

        return null;

    }

    // Lower-cases and strips everything but letters and digits.
    public static String flattenName(String name) {

        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");

    }

    // Drops uid.dat and session.lock, which a copied world must not carry.
    public static boolean isWorldDir(@Nullable File dir) {

        return dir != null && dir.isDirectory() && new File(dir, "level.dat").isFile();

    }

    // Lists the world folders under a directory, for error messages.
    public static String describeWorldDirs(@Nullable File baseDir) {

        File[] candidates = baseDir == null ? null : baseDir.listFiles(File::isDirectory);
        if (candidates == null || candidates.length == 0) {

            return "No world folders found in " + (baseDir == null ? "(unset)" : baseDir.getPath()) + ".";

        }

        StringBuilder sb = new StringBuilder("Available world folders in " + baseDir.getPath() + ": ");
        for (int i = 0; i < candidates.length; i++) {

            if (i > 0) {

                sb.append(", ");

            }

            sb.append(candidates[i].getName());

        }

        return sb.append(".").toString();

    }

    // Drops uid.dat (duplicate world UID) and session.lock (stale lock).
    public static void sanitizeWorldFolder(@Nullable File worldDir) throws IOException {

        if (worldDir == null) {

            return;

        }

        Files.deleteIfExists(new File(worldDir, "uid.dat").toPath());
        Files.deleteIfExists(new File(worldDir, "session.lock").toPath());

    }

    public static void deleteRecursive(Path root) throws IOException {

        if (!Files.exists(root)) {

            return;

        }

        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {

                Files.delete(file);
                return FileVisitResult.CONTINUE;

            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {

                if (exc != null) {

                    throw exc;

                }

                Files.delete(dir);
                return FileVisitResult.CONTINUE;

            }

        });

    }

    public static void copyRecursive(Path source, Path target) throws IOException {

        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {

                Path dest = target.resolve(source.relativize(dir).toString());
                if (!Files.exists(dest)) {

                    Files.createDirectories(dest);

                }

                return FileVisitResult.CONTINUE;

            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {

                Path dest = target.resolve(source.relativize(file).toString());
                Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;

            }

        });

    }

}
