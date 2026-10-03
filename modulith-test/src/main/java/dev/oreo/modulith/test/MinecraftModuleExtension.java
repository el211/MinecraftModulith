package dev.oreo.modulith.test;

import dev.oreo.modulith.core.EventBus;
import dev.oreo.modulith.core.ModuleRuntime;
import dev.oreo.modulith.core.RuntimeDiagnostics;
import org.junit.jupiter.api.extension.*;
import java.util.Arrays;

/** JUnit 5 extension; each test class owns and closes its own module harness. */
public final class MinecraftModuleExtension
        implements BeforeAllCallback, AfterAllCallback, ParameterResolver {
    private static final ExtensionContext.Namespace NS =
            ExtensionContext.Namespace.create(MinecraftModuleExtension.class);

    @Override
    public void beforeAll(ExtensionContext context) {
        MinecraftModuleTest annotation = context.getRequiredTestClass()
                .getAnnotation(MinecraftModuleTest.class);
        if (annotation == null) throw new ExtensionConfigurationException("Missing @MinecraftModuleTest");
        ModuleTestHarness.Builder builder = ModuleTestHarness.builder()
                .modules(Arrays.asList(annotation.modules()))
                .target(annotation.value());
        for (MinecraftModuleTest.Component component : annotation.components()) {
            builder.component(component.module(), component.type());
        }
        context.getStore(NS).put("harness", builder.start());
    }

    @Override
    public void afterAll(ExtensionContext context) {
        ModuleTestHarness harness = context.getStore(NS).remove("harness", ModuleTestHarness.class);
        if (harness != null) harness.close();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
        Class<?> type = parameter.getParameter().getType();
        return type == ModuleTestHarness.class || type == ModuleRuntime.class ||
                type == EventBus.class || type == RuntimeDiagnostics.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameter, ExtensionContext context) {
        ModuleTestHarness harness = context.getStore(NS).get("harness", ModuleTestHarness.class);
        if (harness == null) throw new ParameterResolutionException("Module harness has not started");
        Class<?> type = parameter.getParameter().getType();
        if (type == ModuleTestHarness.class) return harness;
        if (type == ModuleRuntime.class) return harness.runtime();
        if (type == EventBus.class) return harness.events();
        if (type == RuntimeDiagnostics.class) return harness.diagnostics();
        throw new ParameterResolutionException("Unsupported parameter " + type.getName());
    }
}
