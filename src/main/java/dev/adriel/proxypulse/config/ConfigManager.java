package dev.adriel.proxypulse.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

/**
 * Owns the plugin's files on disk: writes the shipped defaults out on first start and reads them
 * back on every reload.
 */
public final class ConfigManager {

  private static final String CONFIG_FILE = "config.yml";
  private static final String ANIMATIONS_FILE = "animations.yml";

  private final Path dataDirectory;

  public ConfigManager(final Path dataDirectory) {
    this.dataDirectory = dataDirectory;
  }

  /** Creates the data directory and copies any file that is not there yet. */
  public void saveDefaults() throws IOException {
    Files.createDirectories(dataDirectory);
    copyIfAbsent(CONFIG_FILE);
    copyIfAbsent(ANIMATIONS_FILE);
  }

  public PulseConfig loadConfig() throws IOException {
    return PulseConfig.load(read(CONFIG_FILE));
  }

  public ConfigurationNode loadAnimations() throws IOException {
    return read(ANIMATIONS_FILE);
  }

  public Path dataDirectory() {
    return dataDirectory;
  }

  private ConfigurationNode read(final String fileName) throws IOException {
    return YamlConfigurationLoader.builder()
        .path(dataDirectory.resolve(fileName))
        .build()
        .load();
  }

  private void copyIfAbsent(final String fileName) throws IOException {
    final Path target = dataDirectory.resolve(fileName);
    if (Files.exists(target)) {
      return;
    }
    try (InputStream source = getClass().getClassLoader().getResourceAsStream(fileName)) {
      if (source == null) {
        throw new IOException("Default " + fileName + " is missing from the plugin jar");
      }
      Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }
}
