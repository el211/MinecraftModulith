package dev.oreo.modulith.core;

import java.lang.annotation.*;

/** Maps a record component to a module configuration path. */
@Target(ElementType.RECORD_COMPONENT)
@Retention(RetentionPolicy.RUNTIME)
public @interface ConfigKey {
    String value();
}
