package dev.oreo.modulith.core;

import java.lang.annotation.*;

/** Inclusive range applied when reading typed module configuration. */
@Target(ElementType.RECORD_COMPONENT)
@Retention(RetentionPolicy.RUNTIME)
public @interface ConfigRange {
    double min() default -Double.MAX_VALUE;
    double max() default Double.MAX_VALUE;
}
