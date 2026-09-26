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

        public Builder target(String moduleId) {
            this.targetModule = Objects.requireNonNull(moduleId, "moduleId");
