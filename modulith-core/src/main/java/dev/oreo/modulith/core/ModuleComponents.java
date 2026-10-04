package dev.oreo.modulith.core;

import java.lang.reflect.*;
import java.util.*;
import java.util.logging.Logger;

/** Small module-local constructor injector; does not expose internal components across boundaries. */
public final class ModuleComponents {
    private final ModuleContext context;
    private final Map<Class<?>, Object> instances = new LinkedHashMap<>();
    private final Set<Class<?>> candidates;
    private final Set<Class<?>> constructing = new LinkedHashSet<>();

    ModuleComponents(ModuleContext context, Collection<Class<?>> types) {
        this.context = context;
        this.candidates = Set.copyOf(types);
        for (Class<?> type : types) {
            if (!type.isAnnotationPresent(ModuleComponent.class)) {
                throw new ModulithException("Missing @ModuleComponent on " + type.getName());
            }
        }
    }

    void initialize() {
        candidates.stream().sorted(Comparator.comparing(Class::getName)).forEach(this::create);
    }

    public <T> T require(Class<T> type) {
        Objects.requireNonNull(type, "type");
        List<Class<?>> matches = candidates.stream().filter(type::isAssignableFrom).toList();
        if (matches.size() > 1) throw new ModulithException("Ambiguous component " + type.getName() + ": " + matches);
        if (matches.size() == 1) return type.cast(create(matches.getFirst()));
        for (Object instance : instances.values()) if (type.isInstance(instance)) return type.cast(instance);
        return context.services().require(type);
    }

    private Object create(Class<?> type) {
        if (instances.containsKey(type)) return instances.get(type);
        if (!constructing.add(type)) throw new ModulithException("Cyclic constructor injection in module '" +
                context.moduleId() + "': " + constructing + " -> " + type.getName());
        try {
            Constructor<?>[] constructors = type.getDeclaredConstructors();
            List<Constructor<?>> marked = Arrays.stream(constructors)
                    .filter(c -> c.isAnnotationPresent(Inject.class)).toList();
            if (marked.size() > 1) throw new ModulithException("Multiple @Inject constructors: " + type.getName());
            Constructor<?> constructor;
            if (marked.size() == 1) constructor = marked.getFirst();
            else if (constructors.length == 1) constructor = constructors[0];
            else constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object[] args = Arrays.stream(constructor.getParameterTypes()).map(this::resolve).toArray();
            Object instance = constructor.newInstance(args);
            instances.put(type, instance);
            for (Class<?> api : type.getInterfaces()) {
                if (api.isAnnotationPresent(ModuleApi.class) ||
                        api.getPackage().isAnnotationPresent(NamedInterface.class)) {
                    publish(api, instance);
                }
            }
            if (instance instanceof AutoCloseable closeable) {
                context.lifecycle().onClose(() -> {
                    try { closeable.close(); }
                    catch (Exception e) { throw new ModulithException("Component cleanup failed: " + type, e); }
                });
            }
            context.listen(instance);
            return instance;
        } catch (ReflectiveOperationException e) {
            throw new ModulithException("Cannot create component " + type.getName() + " in module " +
                    context.moduleId(), e);
        } finally {
            constructing.remove(type);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void publish(Class<?> api, Object instance) {
        context.services().publish((Class) api, instance);
    }

    private Object resolve(Class<?> type) {
        if (type == ModuleContext.class) return context;
        if (type == ModuleServices.class) return context.services();
        if (type == EventBus.class) return context.events();
        if (type == ModuleConfiguration.class) return context.config();
        if (type == LifecycleScope.class) return context.lifecycle();
        if (type == Logger.class) return context.logger();
        return require(type);
    }
}
