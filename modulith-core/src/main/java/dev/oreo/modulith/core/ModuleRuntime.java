package dev.oreo.modulith.core;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Validates, starts, observes and stops a set of internal plugin modules. */
public final class ModuleRuntime implements AutoCloseable {
    private final List<ModuleDescriptor> startupOrder;
    private final Map<Class<?>, Object> platformServices;
    private final Logger logger;
    private final EventPublicationRegistry publicationRegistry;
    private final ModuleConfigurationProvider configurationProvider;
    private final ModulithMetrics metrics;
    private final EventBus eventBus;
    private final ConcurrentHashMap<Class<?>, ModuleServices.ServiceEntry> services = new ConcurrentHashMap<>();
    private final Map<String, RunningModule> running = new LinkedHashMap<>();
    private final Map<String, ModuleState> states = new LinkedHashMap<>();
    private boolean started;

    private ModuleRuntime(
            List<ModuleDescriptor> startupOrder,
            Map<Class<?>, Object> platformServices,
            Logger logger,
            Executor eventExecutor,
            EventPublicationRegistry publicationRegistry,
            EventPayloadSerializer eventSerializer,
            ModuleConfigurationProvider configurationProvider,
            ModulithMetrics metrics
    ) {
        this.startupOrder = startupOrder;
        this.platformServices = Map.copyOf(platformServices);
        this.logger = logger;
        this.publicationRegistry = publicationRegistry;
        this.configurationProvider = configurationProvider;
        this.metrics = metrics;
        this.eventBus = new EventBus(eventExecutor, publicationRegistry, eventSerializer, metrics);
        startupOrder.forEach(descriptor -> states.put(descriptor.id(), ModuleState.DISCOVERED));
    }

    public static Builder builder() {
        return new Builder();
    }

    public synchronized ModuleRuntime start() {
        if (started) {
            return this;
        }

        logger.info(() -> "[MinecraftModulith] Starting " + startupOrder.size() + " modules: "
                + startupOrder.stream().map(ModuleDescriptor::id).toList());

        try {
            for (ModuleDescriptor descriptor : startupOrder) {
                startOne(descriptor);
            }
