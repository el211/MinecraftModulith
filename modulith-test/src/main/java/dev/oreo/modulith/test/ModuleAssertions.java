package dev.oreo.modulith.test;

import dev.oreo.modulith.core.MinecraftModule;
import dev.oreo.modulith.core.ModuleGraphExporter;
import dev.oreo.modulith.core.ModuleRuntime;

import java.util.Collection;

/** Small assertion helpers usable from JUnit, TestNG, or plain Java tests. */
public final class ModuleAssertions {
    private ModuleAssertions() {
    }

    public static void assertValidArchitecture(Collection<Class<? extends MinecraftModule>> modules) {
        ModuleRuntime.builder().modules(modules).build();
    }

    public static void assertMermaidContains(ModuleRuntime runtime, String expected) {
        String graph = ModuleGraphExporter.toMermaid(runtime.modules());
        if (!graph.contains(expected)) {
            throw new AssertionError("Expected Mermaid graph to contain '" + expected + "' but was:\n" + graph);
        }
    }

    public static void assertGraphvizContains(ModuleRuntime runtime, String expected) {
        String graph = ModuleGraphExporter.toGraphviz(runtime.modules());
        if (!graph.contains(expected)) {
            throw new AssertionError("Expected Graphviz graph to contain '" + expected + "' but was:\n" + graph);
        }
    }
}
