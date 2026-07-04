package de.dlr.shepard.spi.transform;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * V2CONV-B3 — CDI-backed registry that ServiceLoader-loads every
 * {@link TransformExecutor} on the classpath at startup and serves
 * {@code resolve(shapeIri)} lookups for the dispatching
 * {@code POST /v2/mappings/{templateAppId}/materialize} endpoint.
 *
 * <p>Mirrors {@link de.dlr.shepard.spi.view.ViewRecipeRendererRegistry} EXACTLY
 * — same discovery contract, same fail-fast-on-duplicate-IRI posture, same
 * fail-soft skip of malformed entries, same StartupEvent-driven lifecycle and
 * test-seam constructor. See that class for the rationale behind each branch.
 *
 * <h2>Fail-soft resolve</h2>
 *
 * <p>{@link #resolve(String)} returns {@link Optional#empty()} when no executor
 * claims the shape IRI (or the IRI is null/blank) — never throws (CLAUDE.md
 * "registries are fail-soft"). The dispatcher degrades to a 404 problem-JSON so
 * a MAPPING_RECIPE whose plugin isn't installed surfaces a clear, recoverable
 * error rather than a 500.
 *
 * <h2>Fail-fast on duplicate shape IRI</h2>
 *
 * <p>Two executors claiming the same shape IRI is a build-time packaging defect
 * — the registry throws {@link IllegalStateException} at startup naming both
 * registrants (matching the renderer registry; diverges from the fail-soft
 * {@code PluginRegistry} because a shape IRI is vendor-controlled and two
 * plugins claiming it is unresolvable at runtime).
 */
@ApplicationScoped
public class TransformExecutorRegistry {

  /** Immutable shape-IRI → executor map populated at startup. */
  private volatile Map<String, TransformExecutor> byShape = Map.of();

  /** Insertion-ordered set of every registered shape IRI for admin/log output. */
  private volatile Set<String> registeredShapes = Set.of();

  /**
   * Test seam — non-null only on the unit-test constructor path; an
   * {@code Iterable} so a test can stage {@code List.of(stubA, stubB)} without
   * a synthetic Module-Layer or mocked {@link ServiceLoader}.
   */
  private final Iterable<TransformExecutor> testExecutors;

  /** CDI constructor. */
  public TransformExecutorRegistry() {
    this.testExecutors = null;
  }

  /**
   * Test-only constructor — bypasses {@link ServiceLoader#load(Class)} so a
   * unit test can stage synthetic executors. Public to support cross-package
   * tests (the dispatching REST lives in {@code de.dlr.shepard.v2.mappings}).
   *
   * @param executors the executors the discovery pass should index
   */
  public TransformExecutorRegistry(Iterable<TransformExecutor> executors) {
    this.testExecutors = executors;
  }

  @PostConstruct
  void init() {
    // Deliberately empty — discovery runs from StartupEvent so ordering with
    // migrations is well-defined. Mirrors ViewRecipeRendererRegistry.
  }

  /**
   * Quarkus startup hook — populates the registry once all CDI beans are
   * constructed. Throws {@link IllegalStateException} on the
   * duplicate-shape-IRI fail-fast condition; otherwise completes cleanly.
   */
  void onStartup(@Observes StartupEvent ev) {
    discover();
  }

  /**
   * Drive discovery + index-build. Public so cross-package tests can stage a
   * registry without booting Quarkus.
   *
   * @throws IllegalStateException when two executors claim the same shape IRI
   */
  public void discover() {
    Map<String, TransformExecutor> map = new LinkedHashMap<>();
    Set<String> shapes = new LinkedHashSet<>();
    int executorCount = 0;

    Iterable<TransformExecutor> source = testExecutors != null
      ? testExecutors
      : ServiceLoader.load(TransformExecutor.class, Thread.currentThread().getContextClassLoader());

    try {
      for (TransformExecutor executor : source) {
        if (executor == null) continue;
        executorCount++;
        Set<String> claims;
        try {
          claims = executor.supportedShapeIris();
        } catch (RuntimeException ex) {
          Log.warnf(
            ex,
            "V2CONV-B3: TransformExecutor '%s' threw from supportedShapeIris() — skipping",
            executor.getClass().getName()
          );
          continue;
        }
        if (claims == null || claims.isEmpty()) {
          Log.warnf(
            "V2CONV-B3: TransformExecutor '%s' declares no supportedShapeIris() — registered but dormant",
            executor.name()
          );
          continue;
        }
        for (String iri : claims) {
          if (iri == null || iri.isBlank()) {
            Log.warnf(
              "V2CONV-B3: TransformExecutor '%s' returned a null/blank shape IRI — skipping that entry",
              executor.name()
            );
            continue;
          }
          TransformExecutor prior = map.putIfAbsent(iri, executor);
          if (prior != null) {
            String msg = String.format(
              "V2CONV-B3: duplicate shape IRI '%s' claimed by TransformExecutor '%s' (%s) " +
              "and '%s' (%s). A shape IRI must be uniquely owned by one executor; " +
              "two plugins cannot share it. Resolve by removing one plugin or by " +
              "narrowing the IRI namespace of one of them.",
              iri,
              prior.name(),
              prior.getClass().getName(),
              executor.name(),
              executor.getClass().getName()
            );
            Log.error(msg);
            throw new IllegalStateException(msg);
          }
          shapes.add(iri);
          Log.infof(
            "V2CONV-B3: TransformExecutor '%s' (%s) registered for shape <%s>",
            executor.name(),
            executor.getClass().getName(),
            iri
          );
        }
      }
    } catch (ServiceConfigurationError ex) {
      Log.warnf(
        ex,
        "V2CONV-B3: ServiceLoader walk failed for TransformExecutor — check META-INF/services entries"
      );
    }

    this.byShape = Collections.unmodifiableMap(map);
    this.registeredShapes = Collections.unmodifiableSet(shapes);

    Log.infof(
      "V2CONV-B3: TransformExecutorRegistry ready — %d executor(s) discovered, %d shape IRI(s) registered",
      executorCount,
      shapes.size()
    );
  }

  /**
   * Look up the executor that claims the given shape IRI.
   *
   * @param shapeIri the SHACL {@code sh:NodeShape} IRI from the MAPPING_RECIPE
   *                 template body's {@code mappingRecipeShape} field; may be
   *                 null or blank (returns empty)
   * @return the matching executor, or {@link Optional#empty()} when no plugin
   *         claims this shape IRI — the dispatcher's signal to return 404
   */
  public Optional<TransformExecutor> resolve(String shapeIri) {
    if (shapeIri == null || shapeIri.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(byShape.get(shapeIri));
  }

  /** @return the IRIs of every shape this registry is serving, in registration order. */
  public List<String> registeredShapes() {
    return List.copyOf(registeredShapes);
  }

  /** @return the number of distinct executor impls discovered (claiming ≥1 IRI). */
  public int executorCount() {
    return Set.copyOf(byShape.values()).size();
  }
}
