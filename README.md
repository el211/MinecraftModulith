# MinecraftModulith

MinecraftModulith brings **Spring Modulith-style architecture to Minecraft plugins**: one plugin JAR, explicit internal modules, validated dependency boundaries, named public APIs, module events, deterministic lifecycle ordering, diagnostics, and Paper/Folia lifecycle adapters.

> Current version: **0.2.0-SNAPSHOT**

## What 0.2 adds

The original 0.1 runtime is now backed by the full second-stage architecture:

- compile-time architecture validation through `modulith-processor`
- named public module APIs through `@ModuleApi("name")`
- `internal` package enforcement between modules
- persistent event publication tracking through SQLite
- sync/async event listeners and completion policies
- module-focused test utilities
- Mermaid and Graphviz dependency graph export
- module/event metrics and runtime diagnostics
- Folia-aware scheduler abstraction
- module-owned dynamic command registration and cleanup
- opt-in YAML configuration per module

## Project layout

```text
modulith-core/           Platform-independent module runtime
modulith-processor/      Annotation processor / architecture validator
modulith-events-sqlite/  Persistent event publication registry
modulith-paper/          Paper + Folia adapters, commands and YAML config
modulith-test/           Module-focused test harness/assertions
example-plugin/          Working example exercising the features
```

## Modules and named APIs

```java
@PluginModule("economy")
public final class EconomyModule implements MinecraftModule, EconomyService {
    @Override
    public void enable(ModuleContext context) {
        context.services().publish(EconomyService.class, this);
    }
}

@ModuleApi("payments")
public interface EconomyService {
    long balance(UUID playerId);
}
```

A consumer can depend on the complete module:

```java
@PluginModule(value = "homes", dependencies = "economy")
```

or only on one named API:

```java
@PluginModule(
    value = "homes",
    dependencies = "economy::payments"
)
```
