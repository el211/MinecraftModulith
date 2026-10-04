package dev.oreo.modulith.core;

import java.util.*;

/**
 * ServiceLoader extension SPI for separately packaged, classpath-visible module sets.
 * Each contributing JAR must publish META-INF/services/dev.oreo.modulith.core.ModuleContributor.
 * A dedicated cross-plugin classloader bridge is NOT implied by this interface.
 */
public interface ModuleContributor {
    Collection<Class<? extends MinecraftModule>> modules();

    default Map<String, ? extends Collection<Class<?>>> components() {
        return Map.of();
    }
}
