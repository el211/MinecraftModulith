package dev.oreo.modulith.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a type as part of a module's public API.
 *
 * <p>The optional value names the API surface so consumers may depend on
 * {@code module::apiName} instead of the entire module.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ModuleApi {
    String value() default "default";
}
