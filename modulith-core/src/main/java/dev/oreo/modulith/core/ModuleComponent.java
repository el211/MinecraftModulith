package dev.oreo.modulith.core;

import java.lang.annotation.*;

/** Module-scoped object constructed by MinecraftModulith. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ModuleComponent {}
