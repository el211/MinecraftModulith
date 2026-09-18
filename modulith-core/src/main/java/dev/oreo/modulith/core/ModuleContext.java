package dev.oreo.modulith.core;

import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/** Runtime facilities available to a module. */
public final class ModuleContext {
    private final String moduleId;
    private final ModuleServices services;
    private final EventBus events;
    private final LifecycleScope lifecycle;
    private final ModuleConfiguration configuration;
    private final Map<Class<?>, Object> platformServices;
    private final Logger logger;

    ModuleContext(
            String moduleId,
            ModuleServices services,
            EventBus events,
            LifecycleScope lifecycle,
            ModuleConfiguration configuration,
            Map<Class<?>, Object> platformServices,
            Logger logger
    ) {
        this.moduleId = moduleId;
        this.services = services;
        this.events = events;
        this.lifecycle = lifecycle;
        this.configuration = configuration;
        this.platformServices = platformServices;
        this.logger = logger;
    }

    public String moduleId() {
        return moduleId;
    }

    public ModuleServices services() {
        return services;
    }

    public EventBus events() {
        return events;
    }

    public LifecycleScope lifecycle() {
        return lifecycle;
    }

    public ModuleConfiguration config() {
        return configuration;
    }

    public Logger logger() {
        return logger;
    }

    public <T> T platform(Class<T> type) {
        Objects.requireNonNull(type, "type");
        Object value = platformServices.get(type);
        if (value == null) {
            throw new ModulithException("Platform service not available: " + type.getName());
        }
        return type.cast(value);
    }

    /** Registers all @ModuleListener methods and automatically unsubscribes them on module stop. */
    public void listen(Object listener) {
        for (EventBus.Subscription subscription : events.register(listener)) {
            lifecycle.onClose(subscription::close);
        }
    }
}
