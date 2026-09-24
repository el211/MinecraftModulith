package dev.oreo.modulith.paper;

import dev.oreo.modulith.core.ModuleConfiguration;
import dev.oreo.modulith.core.ModuleConfigurationProvider;
import dev.oreo.modulith.core.ModuleDescriptor;
import dev.oreo.modulith.core.ModulithException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;

/** Stores opt-in module configuration under {@code plugins/<plugin>/modules/<module>.yml}. */
public final class YamlModuleConfigurationProvider implements ModuleConfigurationProvider {
    private final JavaPlugin plugin;

    public YamlModuleConfigurationProvider(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public ModuleConfiguration load(ModuleDescriptor descriptor) {
        File directory = new File(plugin.getDataFolder(), "modules");
        File file = new File(directory, descriptor.id() + ".yml");
        ensureFile(descriptor.id(), directory, file);
        return new YamlModuleConfiguration(file);
    }

    private void ensureFile(String moduleId, File directory, File file) {
        try {
            Files.createDirectories(directory.toPath());
            if (file.exists()) {
                return;
            }

            String resourceName = "modules/" + moduleId + ".yml";
            try (InputStream resource = plugin.getResource(resourceName)) {
                if (resource != null) {
                    Files.copy(resource, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.createFile(file.toPath());
                }
            }
        } catch (IOException exception) {
            throw new ModulithException("Could not create configuration for module '" + moduleId + "'", exception);
        }
    }

    private static final class YamlModuleConfiguration implements ModuleConfiguration {
        private final File file;
        private YamlConfiguration yaml;

        private YamlModuleConfiguration(File file) {
            this.file = file;
            reload();
        }

        @Override public Object get(String key) { return yaml.get(key); }
        @Override public String getString(String key, String defaultValue) {
            String value = yaml.getString(key);
            return value == null ? defaultValue : value;
        }
        @Override public int getInt(String key, int defaultValue) { return yaml.getInt(key, defaultValue); }
        @Override public long getLong(String key, long defaultValue) { return yaml.getLong(key, defaultValue); }
        @Override public double getDouble(String key, double defaultValue) { return yaml.getDouble(key, defaultValue); }
        @Override public boolean getBoolean(String key, boolean defaultValue) { return yaml.getBoolean(key, defaultValue); }
        @Override public List<String> getStringList(String key) { return List.copyOf(yaml.getStringList(key)); }
        @Override public void set(String key, Object value) { yaml.set(key, value); }

        @Override
        public void reload() {
            yaml = YamlConfiguration.loadConfiguration(file);
        }

        @Override
        public void save() {
            try {
                yaml.save(file);
            } catch (IOException exception) {
                throw new ModulithException("Could not save module configuration " + file, exception);
            }
        }
    }
}
