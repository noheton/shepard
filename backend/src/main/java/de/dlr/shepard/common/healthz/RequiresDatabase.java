package de.dlr.shepard.common.healthz;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Declarative metadata: marks a JAX-RS resource (class or method) as requiring
 * one or more databases to be reachable. Enforced by {@link RequiresDatabaseFilter}.
 */
@Target({ TYPE, METHOD })
@Retention(RUNTIME)
public @interface RequiresDatabase {
  DatabaseKind[] value();
}
