package dev.oreo.modulith.paper;

import dev.oreo.modulith.core.EventPayloadSerializer;
import dev.oreo.modulith.core.EventPublicationRegistry;
import dev.oreo.modulith.core.MinecraftModule;
import dev.oreo.modulith.core.ModuleConfigurationProvider;
import dev.oreo.modulith.core.ModuleRuntime;
import dev.oreo.modulith.core.ModulithException;
import dev.oreo.modulith.core.PluginModule;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/** Entry point for bootstrapping MinecraftModulith inside a Paper/Folia plugin. */
public final class PaperModulith implements AutoCloseable {
    private final ModuleRuntime runtime;

    private PaperModulith(ModuleRuntime runtime) {
        this.runtime = runtime;
    }

    public static Builder builder(JavaPlugin plugin) {
        return new Builder(plugin);
    }

    public ModuleRuntime runtime() {
        return runtime;
    }

    @Override
    public void close() {
        runtime.stop();
    }

    public static final class Builder {
        private final JavaPlugin plugin;
        private final Set<Class<? extends MinecraftModule>> explicitModules = new LinkedHashSet<>();
        private String basePackage;
        private EventPublicationRegistry publicationRegistry = EventPublicationRegistry.noop();
        private EventPayloadSerializer eventSerializer = EventPayloadSerializer.toStringSerializer();
        private Executor eventExecutor = ForkJoinPool.commonPool();
        private ModuleConfigurationProvider configurationProvider;

        private Builder(JavaPlugin plugin) {
            this.plugin = Objects.requireNonNull(plugin, "plugin");
        }

        public Builder basePackage(String basePackage) {
            this.basePackage = Objects.requireNonNull(basePackage, "basePackage");
            return this;
        }

        public Builder module(Class<? extends MinecraftModule> moduleType) {
            explicitModules.add(Objects.requireNonNull(moduleType, "moduleType"));
            return this;
        }

        public Builder publicationRegistry(EventPublicationRegistry publicationRegistry) {
            this.publicationRegistry = Objects.requireNonNull(publicationRegistry, "publicationRegistry");
            return this;
        }
