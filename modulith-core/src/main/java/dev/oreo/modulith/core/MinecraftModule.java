package dev.oreo.modulith.core;

/** Lifecycle contract for an internal module. */
public interface MinecraftModule {
    default void enable(ModuleContext context) throws Exception {
    }

    default void disable() throws Exception {
    }
}
