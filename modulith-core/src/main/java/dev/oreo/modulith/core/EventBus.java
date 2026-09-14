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
