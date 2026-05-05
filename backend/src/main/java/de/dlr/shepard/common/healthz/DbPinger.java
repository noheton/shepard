package de.dlr.shepard.common.healthz;

/**
 * Strategy used by readiness and startup checks to probe a single database
 * and update the shared {@link DbHealthState}.
 */
interface DbPinger {
  String name();

  DbHealthState state();

  /**
   * Returns true if a fresh probe just succeeded.
   */
  boolean ping();

  /**
   * Returns true if the underlying DB is required in the current configuration.
   * If false, startup and readiness checks short-circuit to UP.
   */
  default boolean isRequired() {
    return true;
  }
}
