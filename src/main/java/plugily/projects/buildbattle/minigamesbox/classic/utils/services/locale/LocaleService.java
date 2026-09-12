package plugily.projects.buildbattle.minigamesbox.classic.utils.services.locale;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Scanner;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Pattern;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import plugily.projects.minigamesbox.api.utils.misc.IDebugger;
import plugily.projects.minigamesbox.api.utils.services.locale.ILocale;
import plugily.projects.minigamesbox.classic.PluginMain;
import plugily.projects.minigamesbox.classic.utils.configuration.ConfigUtils;
import plugily.projects.minigamesbox.classic.utils.services.ServiceRegistry;
import plugily.projects.minigamesbox.classic.utils.services.locale.Locale;
import plugily.projects.minigamesbox.classic.utils.services.locale.LocaleRegistry;

public class LocaleService {

    private static final String METADATA_PATH = "/locales/locale_data";
    private static final String DEFAULT_LOCALE_PATH = "locales/language_default.yml";

    private PluginMain plugin;
    private FileConfiguration localeData;

    public LocaleService(PluginMain plugin) {

        if (ServiceRegistry.getRegisteredService() == null || !ServiceRegistry.getRegisteredService().equals(plugin)) {

            throw new IllegalArgumentException(
                    "LocaleService cannot be used without registering service via ServiceRegistry first!");

        }

        if (!ServiceRegistry.isServiceEnabled()) {

            return;

        }

        this.plugin = plugin;

        try {

            String fetchedMetadata = readAll(requestLocaleFetch(null));
            File localeDataFile = new File(plugin.getDataFolder().getPath() + "/locales/locale_data.yml");

            if (!localeDataFile.exists()) {

                new File(plugin.getDataFolder().getPath() + "/locales").mkdir();
                if (!localeDataFile.createNewFile()) {

                    debug(Level.WARNING, "Couldn't create locales folder! We must disable locales support.");
                    return;

                }

            }

            String metadataToWrite = resolveMetadataContent(fetchedMetadata);
            Files.write(localeDataFile.toPath(), metadataToWrite.getBytes(StandardCharsets.UTF_8));
            this.localeData = ConfigUtils.getConfig(plugin, METADATA_PATH);
            if (this.localeData == null) {

                debug(Level.WARNING, "Locale metadata could not be loaded, locales will be disabled.");
                return;

            }

            if (isValidMetadata(this.localeData)) {

                debug(Level.WARNING, "Fetched latest localization file from repository.");

            } else {

                this.localeData = loadFallbackMetadata();
                if (this.localeData == null || !isValidMetadata(this.localeData)) {

                    debug(Level.WARNING,
                            "Fallback localization metadata is missing required locales.register section.");
                    return;

                }

                ConfigUtils.saveConfig(plugin, this.localeData, "/locales/locale_data");
                debug(Level.WARNING, "Remote localization metadata was malformed, loaded bundled fallback instead.");

            }

            loadPluginLocales();

        } catch (IOException exception) {

            debug(Level.WARNING,
                    "Couldn't access locale fetcher service or there is other problem! You should notify author!");

        }

    }

    private void loadPluginLocales() {

        if (localeData == null) {

            return;

        }

        ConfigurationSection registerSection = localeData.getConfigurationSection("locales.register");
        if (registerSection == null) {

            debug(Level.WARNING, "Localization metadata is missing locales.register section.");
            return;

        }

        Set<String> localeKeys = registerSection.getKeys(false);
        for (String key : localeKeys) {

            String name = localeData.getString("locales.register." + key + ".name", key);
            String originalName = localeData.getString("locales.register." + key + ".original_name", key);
            String region = key.contains("_") ? key.split("_", 2)[1] : key;
            LocaleRegistry.registerLocale(
                    new Locale(name, originalName, key, "PoEditor Contributors https://translate.plugily.xyz",
                            Arrays.asList(key.toLowerCase(), name, originalName, region)));

        }

    }

    private static String toReadable(String version) {

        String[] data = Pattern.compile(".", Pattern.CASE_INSENSITIVE).split(version.replace("v", ""));
        StringBuilder builder = new StringBuilder();
        for (String element : data) {

            builder.append(String.format("%4s", element));

        }

        return builder.toString();

    }

    // This fork never contacts plugily.xyz. Every fetch answers with an empty
    // stream, so the bundled locale is the only one that ever loads.
    private InputStream requestLocaleFetch(ILocale locale) {

        debug(Level.FINE, "Remote locale fetch is disabled in this fork; using the bundled locale.");
        return new ByteArrayInputStream(new byte[0]);

    }

    public DownloadStatus demandLocaleDownload(ILocale locale) {

        if (localeData == null) {

            return DownloadStatus.FAIL;

        }

        File localeFile = new File(plugin.getDataFolder() + "/locales/" + locale.getPrefix() + ".yml");
        if (!localeFile.exists() || !isExact(locale, localeFile)) {

            return writeFile(locale);

        }

        return DownloadStatus.LATEST;

    }

    private DownloadStatus writeFile(ILocale locale) {

        try {

            String content = readAll(requestLocaleFetch(locale));
            if (!isValidYamlMap(content)) {

                if ("default".equalsIgnoreCase(locale.getPrefix())) {

                    String fallback = readBundledResource(DEFAULT_LOCALE_PATH);
                    if (fallback != null && isValidYamlMap(fallback)) {

                        content = fallback;

                    } else {

                        debug(Level.WARNING, "Demanded locale {0} cannot be downloaded and bundled default is invalid.",
                                locale.getPrefix());
                        return DownloadStatus.FAIL;

                    }

                } else {

                    debug(Level.WARNING, "Demanded locale {0} returned malformed YAML and cannot be loaded.",
                            locale.getPrefix());
                    return DownloadStatus.FAIL;

                }

            }

            try (OutputStreamWriter writer = new OutputStreamWriter(Files.newOutputStream(
                    new File(plugin.getDataFolder().getPath() + "/locales/" + locale.getPrefix() + ".yml").toPath()),
                    StandardCharsets.UTF_8))
            {

                writer.write(content);

            }

            return DownloadStatus.SUCCESS;

        } catch (IOException exception) {

            debug(Level.WARNING, "Demanded locale {0} cannot be downloaded! You should notify author!",
                    locale.getPrefix());
            return DownloadStatus.FAIL;

        }

    }

    public boolean isValidVersion() {

        if (localeData == null) {

            return false;

        }

        String pluginVersion = plugin.getDescription().getVersion();
        String localeVersion = localeData.getString("locales.valid-version", pluginVersion);
        plugin.getDebugger().debug("Version check on language api: Plugin: {0} Locale: {1}", pluginVersion,
                localeVersion);
        return !checkHigher(pluginVersion, localeVersion);

    }

    private boolean isExact(ILocale locale, File file) {

        try {

            String remoteContent = readAll(requestLocaleFetch(locale));
            if (!isValidYamlMap(remoteContent)) {

                return false;

            }

            String localContent = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            return remoteContent.equals(localContent);

        } catch (IOException exception) {

            return false;

        }

    }

    private boolean checkHigher(String pluginVersion, String localeVersion) {

        String readablePluginVersion = toReadable(pluginVersion);
        String readableLocaleVersion = toReadable(localeVersion);
        return readablePluginVersion.compareTo(readableLocaleVersion) < 0;

    }

    private String resolveMetadataContent(String fetchedMetadata) throws IOException {

        if (isValidMetadata(fetchedMetadata)) {

            return fetchedMetadata;

        }

        String fallback = readBundledResource("locales/locale_data.yml");
        return fallback == null ? fetchedMetadata : fallback;

    }

    private FileConfiguration loadFallbackMetadata() throws IOException {

        String fallback = readBundledResource("locales/locale_data.yml");
        if (fallback == null || !isValidMetadata(fallback)) {

            return null;

        }

        YamlConfiguration configuration = new YamlConfiguration();
        try {

            configuration.loadFromString(fallback);

        } catch (InvalidConfigurationException exception) {

            return null;

        }

        return configuration;

    }

    private boolean isValidMetadata(String content) {

        if (!isValidYamlMap(content)) {

            return false;

        }

        YamlConfiguration configuration = new YamlConfiguration();
        try {

            configuration.loadFromString(content);

        } catch (InvalidConfigurationException exception) {

            return false;

        }

        return isValidMetadata(configuration);

    }

    private boolean isValidMetadata(FileConfiguration configuration) {

        return configuration.getConfigurationSection("locales.register") != null;

    }

    private boolean isValidYamlMap(String content) {

        if (content == null || content.isBlank()) {

            return false;

        }

        try {

            YamlConfiguration configuration = new YamlConfiguration();
            configuration.loadFromString(content);
            return true;

        } catch (InvalidConfigurationException exception) {

            return false;

        }

    }

    private String readBundledResource(String resourcePath) throws IOException {

        try (InputStream inputStream = plugin.getResource(resourcePath)) {

            if (inputStream == null) {

                return null;

            }

            return readAll(inputStream);

        }

    }

    private String readAll(InputStream inputStream) throws IOException {

        try (InputStream stream = inputStream; Scanner scanner = new Scanner(stream, StandardCharsets.UTF_8)) {

            scanner.useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";

        }

    }

    private void debug(Level level, String message, Object... values) {

        IDebugger debugger = plugin.getDebugger();
        if (values.length == 0) {

            debugger.debug(level, message);
            return;

        }

        debugger.debug(level, message, values);

    }

    public enum DownloadStatus {
        SUCCESS, FAIL, LATEST
    }

}
