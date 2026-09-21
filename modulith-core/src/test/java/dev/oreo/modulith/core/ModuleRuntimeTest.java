package dev.oreo.modulith.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleRuntimeTest {
    private static final List<String> ORDER = new ArrayList<>();

    @AfterEach
    void reset() {
        ORDER.clear();
    }

    @Test
    void startsDependenciesFirstAndStopsInReverseOrder() {
        ModuleRuntime runtime = ModuleRuntime.builder()
                .module(Homes.class)
                .module(Economy.class)
                .start();

        assertEquals(List.of("economy:on", "homes:on"), ORDER);
        runtime.stop();
        assertEquals(List.of("economy:on", "homes:on", "homes:off", "economy:off"), ORDER);
    }

    @Test
    void rejectsCycles() {
        assertThrows(ModuleDependencyException.class, () -> ModuleRuntime.builder()
                .module(CycleA.class)
                .module(CycleB.class)
                .build());
    }

    @Test
    void enforcesNamedApiDependenciesAtRuntime() {
        ModuleRuntime runtime = ModuleRuntime.builder()
                .module(Economy.class)
                .module(PaymentsConsumer.class)
                .start();

        assertEquals(ModuleState.RUNNING, runtime.states().get("payments-consumer"));
        assertTrue(runtime.graphMermaid().contains("|payments|"));
        runtime.close();
    }
