package dev.oreo.modulith.core;

import java.util.List;

/** Minimal platform-neutral configuration facade for one module. */
public interface ModuleConfiguration {
    Object get(String key);

    String getString(String key, String defaultValue);

    int getInt(String key, int defaultValue);

    long getLong(String key, long defaultValue);

    double getDouble(String key, double defaultValue);

    boolean getBoolean(String key, boolean defaultValue);

    List<String> getStringList(String key);

    void set(String key, Object value);

    default void reload() {
    }

    default void save() {
    }

    static ModuleConfiguration empty() {
        return EmptyModuleConfiguration.INSTANCE;
    }

    final class EmptyModuleConfiguration implements ModuleConfiguration {
        private static final EmptyModuleConfiguration INSTANCE = new EmptyModuleConfiguration();

        private EmptyModuleConfiguration() {
        }

        @Override public Object get(String key) { return null; }
        @Override public String getString(String key, String defaultValue) { return defaultValue; }
        @Override public int getInt(String key, int defaultValue) { return defaultValue; }
        @Override public long getLong(String key, long defaultValue) { return defaultValue; }
        @Override public double getDouble(String key, double defaultValue) { return defaultValue; }
        @Override public boolean getBoolean(String key, boolean defaultValue) { return defaultValue; }
        @Override public List<String> getStringList(String key) { return List.of(); }

        @Override
        public void set(String key, Object value) {
            throw new UnsupportedOperationException("Module configuration is not enabled");
        }
    }
}
