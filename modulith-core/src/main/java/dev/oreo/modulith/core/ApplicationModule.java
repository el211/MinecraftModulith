package dev.oreo.modulith.core;

import java.lang.annotation.*;

/** Declares a package as a module. The annotation processor generates its lifecycle anchor. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PACKAGE)
public @interface ApplicationModule {
    String id();
    String[] allowedDependencies() default {};
    boolean configuration() default false;
}
