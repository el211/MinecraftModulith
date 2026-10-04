package dev.oreo.modulith.core;

import java.lang.annotation.*;

/** Exposes public types in a package as a named module API. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PACKAGE)
public @interface NamedInterface {
    String value();
}
