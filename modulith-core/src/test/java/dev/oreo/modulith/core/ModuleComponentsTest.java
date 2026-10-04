package dev.oreo.modulith.core;

import org.junit.jupiter.api.Test;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ModuleComponentsTest {
    @ModuleApi("payments")
    interface Payments { long balance(); }

    @PluginModule("economy")
    public static class Economy implements MinecraftModule {}

    @PluginModule(value = "homes", dependencies = {"economy::payments"})
    public static class Homes implements MinecraftModule {
        @Override public void enable(ModuleContext context) {
            assertEquals(42L, context.components().require(HomesComponent.class).balance());
        }
    }

    @ModuleComponent
    public static class EconomyComponent implements Payments {
        public long balance() { return 42L; }
    }

    @ModuleComponent
    public static class HomesComponent {
        private final Payments payments;
        @Inject public HomesComponent(Payments payments) { this.payments = payments; }
        public long balance() { return payments.balance(); }
    }

    @Test
    void injectsDeclaredServiceAcrossModuleBoundary() {
        try (ModuleRuntime runtime = ModuleRuntime.builder()
                .module(Economy.class).module(Homes.class)
                .component("economy", EconomyComponent.class)
                .component("homes", HomesComponent.class)
                .start()) {
            assertEquals(ModuleState.RUNNING, runtime.states().get("homes"));
        }
    }

    @Test
    void rejectsUndeclaredInjection() {
        ModulithException failure = assertThrows(ModulithException.class, () -> {
            try (ModuleRuntime ignored = ModuleRuntime.builder()
                    .module(Economy.class).module(Undeclared.class)
                    .component("economy", EconomyComponent.class)
                    .component("undeclared", HomesComponent.class)
                    .start()) {}
        });
        assertInstanceOf(ModuleDependencyException.class, failure.getCause());
    }

    @PluginModule("undeclared")
    public static class Undeclared implements MinecraftModule {}

    @Test
    void detectsConstructorCycle() {
        assertThrows(ModulithException.class, () -> {
            try (ModuleRuntime ignored = ModuleRuntime.builder()
                    .module(Cyclic.class)
                    .component("cyclic", A.class).component("cyclic", B.class)
                    .start()) {}
        });
    }

    @PluginModule("cyclic")
    public static class Cyclic implements MinecraftModule {}
    @ModuleComponent public static class A { A(B value) {} }
    @ModuleComponent public static class B { B(A value) {} }
}
