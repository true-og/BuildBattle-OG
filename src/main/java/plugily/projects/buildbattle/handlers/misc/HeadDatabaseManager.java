package plugily.projects.buildbattle.handlers.misc;

import plugily.projects.buildbattle.Main;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.logging.Level;

/**
 * @author Tigerpanzer_02
 *         <p>
 *         Created at 09.07.2025
 */
public class HeadDatabaseManager {

    private static final String HEADS_SOURCE_BASE_URL = "https://raw.githubusercontent.com/Plugily-Projects/online-services/refs/heads/master/buildbattle/headdatabase/raw/";

    private final Main plugin;
    private final ArrayList<String> headCatalog = new ArrayList<>(Arrays.asList("alphabet", "animals", "blocks",
            "decoration", "food-drinks", "humanoid", "humans", "miscellaneous", "monsters", "plants"));

    public HeadDatabaseManager(Main plugin) {

        this.plugin = plugin;
        try {

            Files.createDirectories(getDatabaseDirectory().toPath());

        } catch (IOException exception) {

            plugin.getDebugger().debug(Level.WARNING,
                    "Couldn't create heads folder! We must disable heads support. Cause: {0}", exception.getMessage());

        }

    }

    public DownloadStatus getDatabase(String databaseName) {

        if (!headCatalog.contains(databaseName)) {

            return DownloadStatus.FAIL;

        }

        return download(plugin, databaseName);

    }

    public ArrayList<String> getHeadCatalog() {

        return headCatalog;

    }

    private DownloadStatus download(Main plugin, String name) {

        DownloadStatus status = demandHeadDownload(name);
        if (status == DownloadStatus.FAIL) {

            plugin.getDebugger().debug(Level.WARNING,
                    "&cHeads service couldn't download latest heads for plugin! Reduced heads will be used instead!");

        } else if (status == DownloadStatus.SUCCESS) {

            plugin.getDebugger().debug(Level.INFO, "&aDownloaded heads " + name + " properly!");

        } else if (status == DownloadStatus.LATEST) {

            plugin.getDebugger().debug(Level.INFO, "&aHeads " + name + " is latest! Awesome!");

        }

        return status;

    }

    private DownloadStatus demandHeadDownload(String head) {

        File headFile = new File(getDatabaseDirectory(), head + ".yml");
        String remoteData = downloadRemoteCategory(head);
        if (remoteData == null) {

            if (headFile.exists()) {

                plugin.getDebugger().debug(Level.WARNING,
                        "Couldn't refresh heads category {0} from GitHub. Using cached local copy instead.", head);
                return DownloadStatus.LATEST;

            }

            return DownloadStatus.FAIL;

        }

        if (!headFile.exists()) {

            return writeFile(head, remoteData);

        }

        try {

            String localData = Files.readString(headFile.toPath());
            if (remoteData.equals(localData)) {

                return DownloadStatus.LATEST;

            }

            return writeFile(head, remoteData);

        } catch (IOException exception) {

            plugin.getDebugger().debug(Level.WARNING,
                    "Couldn't compare local head database for {0}. Re-downloading latest copy.", head);
            return writeFile(head, remoteData);

        }

    }

    private String downloadRemoteCategory(String head) {

        try (InputStream inputStream = new URL(HEADS_SOURCE_BASE_URL + head + ".yml").openStream()) {

            return new String(inputStream.readAllBytes());

        } catch (IOException exception) {

            plugin.getDebugger().debug(Level.SEVERE,
                    "Could not fetch heads category {0} from GitHub raw! Cause: {1} ({2})", new Object[]
                    { head, exception.getCause(), exception.getMessage() });
            return null;

        }

    }

    private DownloadStatus writeFile(String head, String data) {

        Path targetPath = new File(getDatabaseDirectory(), head + ".yml").toPath();
        Path tempPath = new File(getDatabaseDirectory(), head + ".tmp").toPath();
        try {

            Files.writeString(tempPath, data);
            Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return DownloadStatus.SUCCESS;

        } catch (IOException exception) {

            try {

                Files.deleteIfExists(tempPath);

            } catch (IOException ignored) {

                // ignored on cleanup

            }

            plugin.getDebugger().debug(Level.WARNING,
                    "Demanded head {0} cannot be downloaded or stored! You should notify author!", head);
            return DownloadStatus.FAIL;

        }

    }

    private File getDatabaseDirectory() {

        return new File(plugin.getDataFolder(), "heads/database");

    }

    /*
     * [ARCHIVE] Convert Heads with Json to YML Code private void
     * loadFileFromJSONde(String input) { long start = System.currentTimeMillis();
     * JsonElement element = getJsonElement(
     * "https://raw.githubusercontent.com/Plugily-Projects/online-services/refs/heads/master/buildbattle/headdatabase/raw/"
     * + input + ".yml"); or JsonElement element =
     * getJsonElement("https://minecraft-heads.com/scripts/api.php?cat=decoration");
     * if(element == null) { return; } JsonArray outputJson =
     * element.getAsJsonArray(); AtomicInteger i = new AtomicInteger(); i.set(0);
     * outputJson.forEach(categoryElement -> { JsonObject category =
     * categoryElement.getAsJsonObject(); if(category.get("name") == null) { return;
     * } if(category.get("value") == null) { return; } String name =
     * category.get("name").getAsString(); String value =
     * category.get("value").getAsString(); if(heads.get(name) != null) {
     * heads.put(name + " (dup) " + i.get(), value); i.getAndIncrement(); } else
     * heads.put(name, value); }); System.out.println(heads); FileConfiguration
     * categoryConfig = ConfigUtils.getConfig(plugin, "heads/menus/hd/" + input);
     * for(Map.Entry<String, String> entry : heads.entrySet()) { //
     * System.out.println("Trying to save -> " + entry.getKey() + " "+
     * entry.getValue()); categoryConfig.set(entry.getKey().replace(".", " "),
     * entry.getValue()); } ConfigUtils.saveConfig(plugin, categoryConfig,
     * "heads/menus/hd/" + input);
     * System.out.println("[System] [Plugin] Head finished took ms" +
     * (System.currentTimeMillis() - start)); heads.clear(); }
     * 
     * private final String USER_AGENT = "Plugily Projects Converter v1";
     * 
     * public JsonElement getJsonElement(String fullURL) { try { URL url = new
     * URL(fullURL); HttpURLConnection connection = (HttpURLConnection)
     * url.openConnection(); connection.addRequestProperty("User-Agent",
     * USER_AGENT); InputStream inputStream = connection.getInputStream();
     * InputStreamReader reader = new InputStreamReader(inputStream); return new
     * JsonParser().parse(reader); } catch(IOException e) { e.printStackTrace();
     * return null; } }
     * 
     */

    public enum DownloadStatus {
        SUCCESS, FAIL, LATEST
    }

}
