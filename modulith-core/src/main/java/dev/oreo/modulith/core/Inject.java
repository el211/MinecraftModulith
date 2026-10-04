package dev.oreo.modulith.core;

import java.lang.annotation.*;

/** Selects the injection constructor when more than one is present. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.CONSTRUCTOR)
public @interface Inject {}
