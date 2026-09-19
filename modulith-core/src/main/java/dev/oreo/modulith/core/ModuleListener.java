package dev.oreo.modulith.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a single-argument method as a MinecraftModulith event listener. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ModuleListener {
    /** Stable identifier used by the persistent publication registry. */
    String id() default "";

    EventDelivery delivery() default EventDelivery.SYNC;
}
