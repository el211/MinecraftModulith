package dev.oreo.modulith.test;

import dev.oreo.modulith.core.EventBus;
import dev.oreo.modulith.core.EventPayloadSerializer;
import dev.oreo.modulith.core.EventPublicationRegistry;
import dev.oreo.modulith.core.MinecraftModule;
import dev.oreo.modulith.core.ModuleDependency;
import dev.oreo.modulith.core.ModuleRuntime;
import dev.oreo.modulith.core.ModuleState;
import dev.oreo.modulith.core.ModulithException;
import dev.oreo.modulith.core.PluginModule;
import dev.oreo.modulith.core.RuntimeDiagnostics;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.logging.Logger;

/**
 * Lightweight module-focused test harness. It starts a target module and its transitive module
 * dependencies without requiring a Minecraft server.
 */
public final class ModuleTestHarness implements AutoCloseable {
    private final ModuleRuntime runtime;

    private ModuleTestHarness(ModuleRuntime runtime) {
        this.runtime = runtime;
    }

    public static Builder builder() {
        return new Builder();
    }

    public ModuleRuntime runtime() {
        return runtime;
    }

    public EventBus events() {
        return runtime.events();
    }

    public RuntimeDiagnostics diagnostics() {
        return runtime.diagnostics();
    }

    public ModuleTestHarness assertRunning(String moduleId) {
        ModuleState state = runtime.states().get(moduleId);
        if (state != ModuleState.RUNNING) {
            throw new AssertionError("Expected module '" + moduleId + "' to be RUNNING but was " + state);
        }
        return this;
    }

    public ModuleTestHarness assertStartupOrder(String... expected) {
        List<String> actual = runtime.modules().stream().map(descriptor -> descriptor.id()).toList();
        if (!actual.equals(List.of(expected))) {
            throw new AssertionError("Expected startup order " + List.of(expected) + " but was " + actual);
        }
        return this;
    }

    @Override
    public void close() {
        runtime.close();
    }

    public static final class Builder {
        private final Set<Class<? extends MinecraftModule>> moduleTypes = new LinkedHashSet<>();
        private final Map<Class<?>, Object> platformServices = new LinkedHashMap<>();
        private String targetModule;
        private final Map<String, List<Class<?>>> components = new LinkedHashMap<>();
        private Executor eventExecutor = ForkJoinPool.commonPool();
        private EventPublicationRegistry registry = EventPublicationRegistry.noop();
        private EventPayloadSerializer serializer = EventPayloadSerializer.toStringSerializer();

        public Builder module(Class<? extends MinecraftModule> moduleType) {
            moduleTypes.add(Objects.requireNonNull(moduleType, "moduleType"));
            return this;
        }

        public Builder modules(Collection<Class<? extends MinecraftModule>> moduleTypes) {
            moduleTypes.forEach(this::module);
            return this;
        }

        /** Register injectable components in the module under test. */
        public Builder component(String moduleId, Class<?> componentType) {
            components.computeIfAbsent(moduleId, ignored -> new java.util.ArrayList<>()).add(componentType);
            return this;
        }

        public Builder target(String moduleId) {
            this.targetModule = Objects.requireNonNull(moduleId, "moduleId");
            return this;
        }

        public <T> Builder platformService(Class<T> type, T service) {
            platformServices.put(type, service);
            return this;
        }

        public Builder eventExecutor(Executor executor) {
            this.eventExecutor = Objects.requireNonNull(executor, "executor");
            return this;
        }

        public Builder publicationRegistry(EventPublicationRegistry registry) {
            this.registry = Objects.requireNonNull(registry, "registry");
            return this;
        }

        public Builder eventSerializer(EventPayloadSerializer serializer) {
            this.serializer = Objects.requireNonNull(serializer, "serializer");
            return this;
        }

        public ModuleTestHarness start() {
            Collection<Class<? extends MinecraftModule>> selected = targetModule == null
                    ? moduleTypes
                    : selectTargetAndDependencies(targetModule, moduleTypes);

            ModuleRuntime.Builder builder = ModuleRuntime.builder()
                    .modules(selected)
                    .eventExecutor(eventExecutor)
                    .publicationRegistry(registry)
                    .eventSerializer(serializer)
                    .logger(Logger.getLogger("MinecraftModulithTest"));

            platformServices.forEach((type, service) -> addPlatformService(builder, type, service));
            components.forEach((id, types) -> types.forEach(type -> builder.component(id, type)));
            return new ModuleTestHarness(builder.start());
        }

        private static Collection<Class<? extends MinecraftModule>> selectTargetAndDependencies(
                String target,
                Collection<Class<? extends MinecraftModule>> moduleTypes
        ) {
            Map<String, Class<? extends MinecraftModule>> byId = new LinkedHashMap<>();
            for (Class<? extends MinecraftModule> type : moduleTypes) {
                PluginModule annotation = type.getAnnotation(PluginModule.class);
                if (annotation == null) {
                    throw new ModulithException("Module class is missing @PluginModule: " + type.getName());
                }
                byId.put(annotation.value(), type);
            }
            if (!byId.containsKey(target)) {
                throw new ModulithException("Unknown target module '" + target + "'");
            }

            LinkedHashSet<Class<? extends MinecraftModule>> selected = new LinkedHashSet<>();
            collect(target, byId, selected, new LinkedHashSet<>());
            return selected;
        }

        private static void collect(
                String moduleId,
                Map<String, Class<? extends MinecraftModule>> byId,
                LinkedHashSet<Class<? extends MinecraftModule>> selected,
                Set<String> visiting
        ) {
            if (selected.stream().anyMatch(type -> type.getAnnotation(PluginModule.class).value().equals(moduleId))) {
                return;
            }
            if (!visiting.add(moduleId)) {
                throw new ModulithException("Circular dependency while selecting test module '" + moduleId + "'");
            }

            Class<? extends MinecraftModule> type = byId.get(moduleId);
            if (type == null) {
                throw new ModulithException("Missing dependency module '" + moduleId + "'");
            }

            PluginModule annotation = type.getAnnotation(PluginModule.class);
            for (String rawDependency : annotation.dependencies()) {
                collect(ModuleDependency.parse(rawDependency).moduleId(), byId, selected, visiting);
            }
            selected.add(type);
            visiting.remove(moduleId);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static void addPlatformService(ModuleRuntime.Builder builder, Class type, Object value) {
            builder.platformService(type, value);
        }
    }
}
