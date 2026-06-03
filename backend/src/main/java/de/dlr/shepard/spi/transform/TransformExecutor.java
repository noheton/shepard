package de.dlr.shepard.spi.transform;

import java.util.Set;

/**
 * V2CONV-B3 — SPI contract for a backend executor that materializes a
 * {@code MAPPING_RECIPE}
 * {@link de.dlr.shepard.template.entities.ShepardTemplate} (binding existing
 * input reference {@code appId}s) into a <em>derived</em> output (a new
 * reference appId, or a played/rendered view-model).
 *
 * <p>This is the transform-direction sibling of
 * {@link de.dlr.shepard.spi.view.ViewRecipeRenderer} (the view direction). It
 * is the enabler that later dissolves the bespoke scene-graph and KRL
 * namespaces (V2CONV-B4/B5) into {@code MAPPING_RECIPE} shapes — the
 * scene-graph play envelope becomes a VIEW result, the KRL joint trajectory
 * becomes a REFERENCE result — but those consumers are NOT implemented here;
 * this task ships only the generic machinery + a built-in no-op default.
 *
 * <p>Implementations are plain POJOs registered through
 * {@code META-INF/services/de.dlr.shepard.spi.transform.TransformExecutor}
 * — NOT CDI beans. Discovery happens via
 * {@link java.util.ServiceLoader#load(Class)} in
 * {@link TransformExecutorRegistry} at startup. This matches the
 * {@link de.dlr.shepard.spi.view.ViewRecipeRenderer} SPI shape EXACTLY and
 * sidesteps the Quarkus {@code @Inject ServiceLoader} dance.
 *
 * <h2>Dispatch key — the shape IRI</h2>
 *
 * <p>Each {@code MAPPING_RECIPE} template body carries a
 * {@code mappingRecipeShape} field naming the SHACL {@code sh:NodeShape} it
 * targets, e.g.
 * {@code "http://semantics.dlr.de/shepard/transform#IdentityTransformShape"}.
 * The {@link TransformExecutorRegistry#resolve(String)} lookup is keyed by that
 * IRI. {@link #supportedShapeIris()} returns the set this executor claims; the
 * registry refuses to start when two executors claim the same IRI (fail-fast on
 * build-time packaging conflict — mirrors the renderer registry).
 *
 * <h2>Failure posture</h2>
 *
 * <p>Throw {@link TransformException} (with a typed {@link
 * TransformException#code()}) for recoverable contract failures the dispatcher
 * maps to 4xx; throw any other {@link RuntimeException} only for unrecoverable
 * conditions (the dispatcher logs at WARN and surfaces HTTP 500).
 *
 * @see TransformExecutorRegistry
 * @see TransformRequest
 * @see TransformResult
 * @see de.dlr.shepard.spi.view.ViewRecipeRenderer
 */
public interface TransformExecutor {
  /**
   * SHACL {@code sh:NodeShape} IRIs this executor claims. The registry indexes
   * implementations by the union of these returns. Each IRI must be globally
   * unique across discovered executors — duplicate registration is a fail-fast
   * condition at registry startup, naming both registrants. An empty return is
   * legal but disables registration (the executor is dormant).
   *
   * @return the set of shape IRIs this executor handles; never null
   */
  Set<String> supportedShapeIris();

  /**
   * Materialize the recipe: derive an output from the bound input references.
   *
   * @param req the dispatch request — never null
   * @return the derived output envelope — never null
   * @throws TransformException for typed executor failures (invalid body,
   *         missing/unresolvable input, …) the dispatcher maps to 4xx
   * @throws RuntimeException for unexpected failures; the dispatcher logs at
   *         WARN and surfaces HTTP 500
   */
  TransformResult materialize(TransformRequest req);

  /**
   * Human-readable name surfaced in startup logs and the materialize
   * dispatcher. Default: simple class name.
   *
   * @return a non-empty short name
   */
  default String name() {
    return getClass().getSimpleName();
  }
}
