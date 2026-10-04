package dev.oreo.modulith.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TypedModuleConfigurationTest {
    record Settings(@ConfigKey("home-limit") @ConfigRange(min=1,max=10) int limit,
                    String greeting) {}

    @Test void mapsAndValidatesRecord() {
        ModuleConfiguration config = new MapConfig(Map.of("home-limit", 3, "greeting", "hello"));
        Settings settings = TypedModuleConfiguration.read(config, Settings.class);
        assertEquals(3, settings.limit());
        assertEquals("hello", settings.greeting());
    }
    @Test void rejectsOutOfRangeValues() {
        assertThrows(ModulithException.class, () ->
            TypedModuleConfiguration.read(new MapConfig(Map.of("home-limit", 11, "greeting", "hi")),
                    Settings.class));
    }

    record MapConfig(Map<String, Object> values) implements ModuleConfiguration {
        public Object get(String key) { return values.get(key); }
        public String getString(String key, String fallback) { return (String) values.getOrDefault(key, fallback); }
        public int getInt(String key, int fallback) { return ((Number) values.getOrDefault(key, fallback)).intValue(); }
        public long getLong(String key, long fallback) { return ((Number) values.getOrDefault(key, fallback)).longValue(); }
        public double getDouble(String key, double fallback) { return ((Number) values.getOrDefault(key, fallback)).doubleValue(); }
        public boolean getBoolean(String key, boolean fallback) { return (Boolean) values.getOrDefault(key, fallback); }
        public List<String> getStringList(String key) { return List.of(); }
        public void set(String key, Object value) { throw new UnsupportedOperationException(); }
    }
}
