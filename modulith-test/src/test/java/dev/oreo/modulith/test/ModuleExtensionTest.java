package dev.oreo.modulith.test;

import dev.oreo.modulith.core.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@MinecraftModuleTest(value="homes", modules={ModuleExtensionTest.Economy.class,ModuleExtensionTest.Homes.class})
class ModuleExtensionTest {
    @PluginModule("economy")
    static class Economy implements MinecraftModule {}
    @PluginModule(value="homes", dependencies="economy")
    static class Homes implements MinecraftModule {}

    @Test void injectsHarness(ModuleTestHarness harness, ModuleRuntime runtime) {
        harness.assertRunning("economy").assertRunning("homes");
        assertEquals(2, runtime.modules().size());
    }

    @Test void injectsDiagnostics(RuntimeDiagnostics snapshot) {
        assertEquals(ModuleState.RUNNING, snapshot.states().get("homes"));
    }
}
