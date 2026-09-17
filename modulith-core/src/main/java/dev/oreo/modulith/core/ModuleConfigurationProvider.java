package dev.oreo.modulith.core;

/** Loads configuration for modules that opt in through {@link PluginModule#configuration()}. */
@FunctionalInterface
public interface ModuleConfigurationProvider {
    ModuleConfiguration load(ModuleDescriptor descriptor);

    static ModuleConfigurationProvider none() {
        return descriptor -> ModuleConfiguration.empty();
    }
}
