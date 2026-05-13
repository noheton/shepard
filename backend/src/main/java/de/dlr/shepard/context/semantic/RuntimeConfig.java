package de.dlr.shepard.context.semantic;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * N1c2 — value object carrying the runtime-mutable subset of the
 * ontology-preseed knobs.
 *
 * <p>{@link OntologySeedService} pulls this on every
 * {@code seedIfNeeded()} pass via a {@code Supplier<RuntimeConfig>}
 * injected at construction. Production wiring threads
 * {@code OntologyConfigService.loadRuntimeConfig()} through; tests pass
 * closures.
 *
 * <p>The fields mirror the {@code :SemanticConfig} entity (one row, one
 * record). The factory {@link #deployTimeOnly()} returns the
 * "no-runtime-overrides" shape — equivalent to "no
 * {@code :SemanticConfig} on disk yet" or "the runtime config layer is
 * unavailable" (e.g. pre-N1c2 callers). When the runtime layer is
 * absent the precedence cascade falls through to the deploy-time
 * {@code shepard.semantic.internal.preseed-ontologies.*} keys, which is
 * the exact pre-N1c2 contract.
 *
 * @see SemanticConfig#preseedEnabled
 * @see SemanticConfig#disabledBundles
 */
public record RuntimeConfig(boolean preseedEnabled, Set<String> disabledBundles) {
  /**
   * Defensive-copy + immutability on construction. Null
   * {@code disabledBundles} is normalised to the empty set.
   */
  public RuntimeConfig {
    if (disabledBundles == null) {
      disabledBundles = Collections.emptySet();
    } else {
      disabledBundles = Collections.unmodifiableSet(new LinkedHashSet<>(disabledBundles));
    }
  }

  /**
   * The "no runtime overrides" shape — preseed enabled, no per-bundle
   * disables. This is what the seed service sees when no
   * {@code :SemanticConfig} row exists yet (first start, before the
   * service has seeded one from deploy-time defaults), or when the
   * legacy ctor injects a no-op supplier.
   */
  public static RuntimeConfig deployTimeOnly() {
    return new RuntimeConfig(true, Collections.emptySet());
  }

  /** Convenience factory from a {@link List} (the OGM's storage shape). */
  public static RuntimeConfig of(boolean preseedEnabled, List<String> disabledBundles) {
    if (disabledBundles == null || disabledBundles.isEmpty()) {
      return new RuntimeConfig(preseedEnabled, Collections.emptySet());
    }
    return new RuntimeConfig(preseedEnabled, new LinkedHashSet<>(disabledBundles));
  }
}
