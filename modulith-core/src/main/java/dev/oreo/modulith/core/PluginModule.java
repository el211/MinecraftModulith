package dev.oreo.modulith.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a class as a module managed by MinecraftModulith.
 *
 * <p>Dependencies may target a whole module ({@code economy}) or a named public API
 * ({@code economy::payments}).</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PluginModule {
    String value();

    String[] dependencies() default {};

    /** Enables module-specific configuration when a provider is installed. */
    boolean configuration() default false;
}
