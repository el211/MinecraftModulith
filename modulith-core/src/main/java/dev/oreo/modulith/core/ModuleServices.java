package dev.oreo.modulith.core;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Module-scoped facade over the shared service registry. */
public final class ModuleServices {
    private final String moduleId;
    private final List<ModuleDependency> allowedDependencies;
    private final ConcurrentHashMap<Class<?>, ServiceEntry> shared;

    ModuleServices(
            String moduleId,
            List<ModuleDependency> allowedDependencies,
            ConcurrentHashMap<Class<?>, ServiceEntry> shared
    ) {
        this.moduleId = moduleId;
        this.allowedDependencies = List.copyOf(allowedDependencies);
        this.shared = shared;
    }

    public <T> void publish(Class<T> type, T implementation) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(implementation, "implementation");
        if (!type.isInstance(implementation)) {
            throw new IllegalArgumentException(
                    implementation.getClass().getName() + " does not implement " + type.getName()
            );
        }

        ModuleApi api = type.getAnnotation(ModuleApi.class);
        if (api == null) {
            throw new ModulithException(
                    "Published module service " + type.getName() + " must be annotated with @ModuleApi"
            );
        }
        String apiName = api.value().trim();
        if (apiName.isEmpty()) {
            throw new ModulithException("@ModuleApi name cannot be blank on " + type.getName());
        }

        ServiceEntry existing = shared.putIfAbsent(type, new ServiceEntry(moduleId, apiName, implementation));
        if (existing != null) {
            throw new ModulithException(
                    "Service " + type.getName() + " is already published by module '" + existing.owner() + "'"
            );
        }
    }

    public <T> T require(Class<T> type) {
        Objects.requireNonNull(type, "type");
        ServiceEntry entry = shared.get(type);
        if (entry == null) {
            throw new ModulithException("No module service published for " + type.getName());
        }

        if (!entry.owner().equals(moduleId) && allowedDependencies.stream().noneMatch(
                dependency -> dependency.allows(entry.owner(), entry.apiName())
        )) {
            throw new ModuleDependencyException(
                    "Module '" + moduleId + "' tried to access API '" + entry.apiName() + "' from module '"
                            + entry.owner() + "' without declaring dependency '" + entry.owner()
                            + "' or '" + entry.owner() + "::" + entry.apiName() + "'"
            );
        }
        return type.cast(entry.instance());
    }

    Map<Class<?>, ServiceEntry> snapshot() {
        return Map.copyOf(shared);
    }

    record ServiceEntry(String owner, String apiName, Object instance) {
    }
}
