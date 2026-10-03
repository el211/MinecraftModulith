package dev.oreo.modulith.core;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Application event bus supporting synchronous/asynchronous listeners, completion policies,
 * persistent publication tracking, and completion metrics.
 */
public final class EventBus {
    private final ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<Handler>> handlers = new ConcurrentHashMap<>();
    private final Executor executor;
    private final EventPublicationRegistry registry;
    private final EventPayloadSerializer serializer;
    private final ModulithMetrics metrics;
    private final AtomicInteger anonymousHandlerIds = new AtomicInteger();

    public EventBus() {
        this(ForkJoinPool.commonPool(), EventPublicationRegistry.noop(),
                EventPayloadSerializer.toStringSerializer(), new ModulithMetrics());
    }

    EventBus(
            Executor executor,
            EventPublicationRegistry registry,
            EventPayloadSerializer serializer,
            ModulithMetrics metrics
    ) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.serializer = Objects.requireNonNull(serializer, "serializer");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    public <E> Subscription subscribe(Class<E> eventType, Consumer<? super E> consumer) {
        return subscribe(eventType, "consumer-" + anonymousHandlerIds.incrementAndGet(), EventDelivery.SYNC, consumer);
    }

    public <E> Subscription subscribe(
            Class<E> eventType,
            String listenerId,
            EventDelivery delivery,
            Consumer<? super E> consumer
    ) {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(listenerId, "listenerId");
        Objects.requireNonNull(delivery, "delivery");
        Objects.requireNonNull(consumer, "consumer");

        Handler handler = new Handler(
                listenerId,
                delivery,
                event -> {
                    consumer.accept(eventType.cast(event));
                    return CompletableFuture.completedFuture(null);
                }
        );
        handlers.computeIfAbsent(eventType, ignored -> new CopyOnWriteArrayList<>()).add(handler);
        return () -> handlers.getOrDefault(eventType, new CopyOnWriteArrayList<>()).remove(handler);
    }

    public List<Subscription> register(Object listener) {
        Objects.requireNonNull(listener, "listener");
        List<Subscription> subscriptions = new ArrayList<>();

        for (Method method : listener.getClass().getDeclaredMethods()) {
            ModuleListener annotation = method.getAnnotation(ModuleListener.class);
            if (annotation == null) {
                continue;
            }
            if (method.getParameterCount() != 1) {
                throw new ModulithException("@ModuleListener method must have exactly one parameter: " + method);
            }

            method.setAccessible(true);
            Class<?> eventType = method.getParameterTypes()[0];
            String listenerId = annotation.id().isBlank()
                    ? listener.getClass().getName() + "#" + method.getName() + "(" + eventType.getName() + ")"
                    : annotation.id();

            Handler handler = new Handler(
                    listenerId,
                    annotation.delivery(),
                    event -> invoke(listener, method, event)
            );
            handlers.computeIfAbsent(eventType, ignored -> new CopyOnWriteArrayList<>()).add(handler);
            subscriptions.add(() -> handlers.getOrDefault(eventType, new CopyOnWriteArrayList<>()).remove(handler));
        }

        return List.copyOf(subscriptions);
    }

    public EventDispatchResult publish(Object event) {
        return publish(event, EventCompletionPolicy.WAIT_FOR_ALL);
    }

    public EventDispatchResult publish(Object event, EventCompletionPolicy policy) {
        try {
            return publishAsync(event, policy).toCompletableFuture().join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
    }

    public CompletionStage<EventDispatchResult> publishAsync(Object event) {
        return publishAsync(event, EventCompletionPolicy.WAIT_FOR_ALL);
    }

    public CompletionStage<EventDispatchResult> publishAsync(Object event, EventCompletionPolicy policy) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(policy, "policy");
        metrics.eventPublished();

        List<Handler> matching = matchingHandlers(event.getClass());
        if (matching.isEmpty()) {
            return CompletableFuture.completedFuture(
                    new EventDispatchResult(event.getClass().getName(), 0, 0, List.of(), false)
            );
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>(matching.size());
        for (Handler handler : matching) {
            futures.add(dispatch(handler, event));
        }

        if (policy == EventCompletionPolicy.FIRE_AND_FORGET) {
            return CompletableFuture.completedFuture(
                    new EventDispatchResult(event.getClass().getName(), matching.size(), 0, List.of(), true)
            );
        }

        if (policy == EventCompletionPolicy.FAIL_FAST) {
            return failFast(event.getClass().getName(), futures);
        }

        CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        return all.handle((ignored, thrown) -> {
            List<Throwable> failures = collectFailures(futures);
            if (!failures.isEmpty()) {
                throw new CompletionException(new EventDispatchException(
                        "Event " + event.getClass().getName() + " failed in " + failures.size() + " listener(s)",
                        failures
                ));
            }
            return new EventDispatchResult(
                    event.getClass().getName(),
                    matching.size(),
                    matching.size(),
                    List.of(),
                    false
            );
        });
    }

    private CompletionStage<EventDispatchResult> failFast(
            String eventType,
            List<CompletableFuture<Void>> futures
    ) {
        CompletableFuture<EventDispatchResult> result = new CompletableFuture<>();
        AtomicInteger completed = new AtomicInteger();

        for (CompletableFuture<Void> future : futures) {
            future.whenComplete((ignored, error) -> {
                if (error != null) {
                    Throwable cause = unwrap(error);
                    result.completeExceptionally(new EventDispatchException(
                            "Event " + eventType + " failed before all listeners completed",
                            List.of(cause)
                    ));
                    return;
                }

                int count = completed.incrementAndGet();
                if (count == futures.size()) {
                    result.complete(new EventDispatchResult(
                            eventType,
                            futures.size(),
                            futures.size(),
                            List.of(),
                            false
                    ));
                }
            });
        }
        return result;
    }

    private CompletableFuture<Void> dispatch(Handler handler, Object event) {
        String payload = serializer.serialize(event);
        EventPublication publication = registry.begin(event.getClass().getName(), handler.id(), payload);
        metrics.listenerInvoked();
        long startedAt = System.nanoTime();

        CompletableFuture<Void> future;
        if (handler.delivery() == EventDelivery.ASYNC) {
            future = CompletableFuture.supplyAsync(() -> handler.invocation().invoke(event), executor)
                    .thenCompose(stage -> stage)
                    .toCompletableFuture();
        } else {
            try {
                future = handler.invocation().invoke(event).toCompletableFuture();
            } catch (RuntimeException exception) {
                future = CompletableFuture.failedFuture(exception);
            }
        }

        UUID publicationId = publication.id();
        return future.whenComplete((ignored, error) -> {
            long elapsed = System.nanoTime() - startedAt;
            if (error == null) {
                registry.complete(publicationId);
                metrics.listenerCompleted(elapsed);
            } else {
                Throwable cause = unwrap(error);
                registry.fail(publicationId, cause.toString());
                metrics.listenerFailed(elapsed);
            }
        });
    }

    /**
     * Replays pending records without inserting duplicate publications. Call only after every
     * module listener has registered. Delivery is at-least-once: listeners must be idempotent.
     * This method must be run by one recovery worker at a time for a given registry.
     */
    public EventRecoveryReport replayIncomplete(int limit) {
        if (limit < 0) throw new IllegalArgumentException("limit must be >= 0");
        if (!(serializer instanceof EventPayloadCodec codec)) {
            throw new ModulithException("Event recovery requires an EventPayloadCodec with deserialization");
        }
        int recovered = 0, failed = 0, unavailable = 0;
        List<EventPublication> publications = registry.incomplete();
        for (int i = 0; i < Math.min(limit, publications.size()); i++) {
            EventPublication publication = publications.get(i);
            Handler handler = handlers.values().stream().flatMap(List::stream)
                    .filter(h -> h.id().equals(publication.listenerId()))
                    .findFirst().orElse(null);
            if (handler == null) { unavailable++; continue; }
            try {
                Object event = codec.deserialize(publication.eventType(), publication.payload());
                // Reuse the publication ID so replay never inserts a second pending record.
                invokeExisting(handler, event, publication.id()).toCompletableFuture().join();
                recovered++;
            } catch (RuntimeException exception) {
                failed++;
            }
        }
        return new EventRecoveryReport(recovered, failed, unavailable);
    }

    private CompletionStage<Void> invokeExisting(Handler handler, Object event, UUID publicationId) {
        CompletableFuture<Void> future;
        if (handler.delivery() == EventDelivery.ASYNC) {
            future = CompletableFuture.supplyAsync(() -> handler.invocation().invoke(event), executor)
                    .thenCompose(stage -> stage).toCompletableFuture();
        } else {
            try { future = handler.invocation().invoke(event).toCompletableFuture(); }
            catch (RuntimeException exception) { future = CompletableFuture.failedFuture(exception); }
        }
        return future.whenComplete((ignored, error) -> {
            if (error == null) registry.complete(publicationId);
            else registry.fail(publicationId, unwrap(error).toString());
        });
    }

    private List<Handler> matchingHandlers(Class<?> actualType) {
        List<Handler> matching = new ArrayList<>();
        handlers.forEach((registeredType, registeredHandlers) -> {
            if (registeredType.isAssignableFrom(actualType)) {
                matching.addAll(registeredHandlers);
            }
        });
        return matching;
    }

    private static List<Throwable> collectFailures(List<CompletableFuture<Void>> futures) {
        List<Throwable> failures = new ArrayList<>();
        for (CompletableFuture<Void> future : futures) {
            try {
                future.join();
            } catch (CompletionException exception) {
                failures.add(unwrap(exception));
            }
        }
        return List.copyOf(failures);
    }

    private static CompletionStage<Void> invoke(Object listener, Method method, Object event) {
        try {
            Object result = method.invoke(listener, event);
            if (result instanceof CompletionStage<?> stage) {
                return stage.thenApply(ignored -> null);
            }
            return CompletableFuture.completedFuture(null);
        } catch (IllegalAccessException exception) {
            throw new ModulithException("Cannot invoke module listener " + method, exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new ModulithException("Module listener failed: " + method, cause);
        }
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    public interface Subscription extends AutoCloseable {
        @Override
        void close();
    }

    @FunctionalInterface
    private interface Invocation {
        CompletionStage<Void> invoke(Object event);
    }

    private record Handler(String id, EventDelivery delivery, Invocation invocation) {
    }
}
