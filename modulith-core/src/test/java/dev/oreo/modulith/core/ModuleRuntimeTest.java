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

    @PluginModule("economy")
    static final class Economy implements MinecraftModule, PaymentsApi {
        @Override
        public void enable(ModuleContext context) {
            ORDER.add("economy:on");
            context.services().publish(PaymentsApi.class, this);
        }

        @Override
        public void disable() {
            ORDER.add("economy:off");
        }
    }

    @ModuleApi("payments")
    interface PaymentsApi {
        default int balance() {
            return 42;
        }
    }

    @PluginModule(value = "homes", dependencies = "economy")
    static final class Homes implements MinecraftModule {
        @Override
        public void enable(ModuleContext context) {
            ORDER.add("homes:on");
        }

        @Override
        public void disable() {
            ORDER.add("homes:off");
        }
    }

    @PluginModule(value = "payments-consumer", dependencies = "economy::payments")
    static final class PaymentsConsumer implements MinecraftModule {
        @Override
        public void enable(ModuleContext context) {
            assertEquals(42, context.services().require(PaymentsApi.class).balance());
        }
    }

    @PluginModule(value = "a", dependencies = "b")
    static final class CycleA implements MinecraftModule {
    }

    @PluginModule(value = "b", dependencies = "a")
    static final class CycleB implements MinecraftModule {
    }
}
