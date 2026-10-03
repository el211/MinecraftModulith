package dev.oreo.modulith.test;

import dev.oreo.modulith.core.MinecraftModule;
import org.junit.jupiter.api.extension.ExtendWith;
import java.lang.annotation.*;

/**
 * Starts one module and its dependency closure once per JUnit class, then stops it automatically.
 * Supports parameter injection for ModuleTestHarness, ModuleRuntime, EventBus and RuntimeDiagnostics.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ExtendWith(MinecraftModuleExtension.class)
public @interface MinecraftModuleTest {
    String value();
    Class<? extends MinecraftModule>[] modules();
    Component[] components() default {};

    @interface Component {
        String module();
        Class<?> type();
    }
}
