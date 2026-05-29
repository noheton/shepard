package de.dlr.shepard.spi.view;

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
 * VIS-S1 — CDI-backed registry that ServiceLoader-loads every
 * {@link ViewRecipeRenderer} on the classpath at startup and serves
 * {@code resolve(shapeIri)} lookups for the dispatching
 * {@code POST /v2/shapes/render} endpoint.
 *
 * <h2>Discovery contract</h2>
 *
 * <p>Renderers register through
 * {@code META-INF/services/de.dlr.shepard.spi.view.ViewRecipeRenderer}
 * — the same shape the
 * {@link de.dlr.shepard.spi.payload.PayloadKind PayloadKind} P-series
 * SPI uses. The registry caches the discovered list as an immutable
 * {@code Map<String, ViewRecipeRenderer>} keyed by every shape IRI
 * the renderers claim ({@link ViewRecipeRenderer#supportedShapeIris()}).
 *
 * <h2>Fail-fast on duplicate shape IRI</h2>
 *
 * <p>Two renderers claiming the same shape IRI is a build-time error
 * — refusing to start is the actionable signal. The registry throws
 * {@link IllegalStateException} at startup, naming both registrants
 * and the conflicting IRI. This diverges from the prevailing
 * {@code PluginRegistry} fail-soft posture (PM1a) because the shape
 * IRI is a vendor-controlled namespace; two plugins claiming the
 * same IRI is a packaging defect, not a runtime drift the operator
 * can resolve.
 *
 * <p>Other failure modes are fail-soft:
 *
 * <ul>
 *   <li>An impl whose {@code supportedShapeIris()} returns null or
 *       empty is logged at WARN and skipped (won't appear in any
 *       {@code resolve()} result).</li>
 *   <li>A {@link ServiceConfigurationError} during the ServiceLoader
 *       walk (malformed service file) is logged at WARN and the
 *       walk continues with the next entry.</li>
 *   <li>An impl whose constructor throws is reported at WARN by
 *       {@link ServiceLoader} itself; the registry skips it.</li>
 * </ul>
 *
 * <h2>Thread-safety</h2>
 *
 * <p>{@link ServiceLoader} caches iterated providers lazily — first
 * iteration on a shared loader is NOT thread-safe. We therefore
 * drain the iterator into an unmodifiable map inside
 * {@link #discover()} (invoked on the startup thread, single-shot)
 * and serve all subsequent {@code resolve()} calls from the frozen
 * map without re-touching {@link ServiceLoader}.
 *
 * <h2>Lifecycle</h2>
 *
 * <p>{@link #onStartup(StartupEvent)} drives discovery — strictly
 * after migrations and after the CDI container is up. The
 * {@link PostConstruct} hook is a no-op on the production path; it
 * exists so unit tests can construct the registry via
 * {@link #ViewRecipeRendererRegistry(ServiceLoader) the package-private
 * constructor} without booting Quarkus.
 *
 * <h2>Cross-references</h2>
 *
 * <ul>
 *   <li>{@code aidocs/16} — VIS-S1 row</li>
 *   <li>{@code aidocs/semantics/98 §1.2} — render endpoint contract</li>
 *   <li>{@link de.dlr.shepard.spi.payload.PayloadKind} — P-series SPI
 *       prior art</li>
 * </ul>
 */
@ApplicationScoped
public class ViewRecipeRendererRegistry {

  /** Immutable shape-IRI → renderer map populated at startup. */
  private volatile Map<String, ViewRecipeRenderer> byShape = Map.of();

  /** Insertion-ordered set of every registered shape IRI for admin/log output. */
  private volatile Set<String> registeredShapes = Set.of();

  /**
   * Test seam — non-null only on the unit-test constructor path.
   * Iterable rather than {@link ServiceLoader} so a test can stage a
   * plain {@code List.of(stubA, stubB)} without standing up a
   * synthetic Module-Layer or a mocked {@link ServiceLoader}.
   */
  private final Iterable<ViewRecipeRenderer> testRenderers;

  /** CDI constructor. */
  public ViewRecipeRendererRegistry() {
    this.testRenderers = null;
  }

  /**
   * Test-only constructor — bypasses {@link ServiceLoader#load(Class)}
   * so a unit test can stage synthetic renderers via
   * {@code List.of(stubA, stubB)}.
   *
   * <p>Public to support cross-package tests (the dispatching
   * {@code ShapesRenderRest} lives in {@code de.dlr.shepard.v2.shapes.resources}
   * and needs to wire a synthetic registry in {@code @BeforeEach}).
   * Production wiring still goes through CDI's no-arg constructor +
   * {@link #onStartup(StartupEvent)}; this overload is purely a
   * test seam.
   *
   * @param renderers the renderers the discovery pass should index
   */
  public ViewRecipeRendererRegistry(Iterable<ViewRecipeRenderer> renderers) {
    this.testRenderers = renderers;
  }

  @PostConstruct
  void init() {
    // Deliberately empty — discovery runs from StartupEvent so
    // ordering with ShepardMain.init()'s migrations is well-defined.
    // The hook exists so @ApplicationScoped lazy-init still wires
    // the bean early enough for tests that bypass startup.
  }

  /**
   * Quarkus startup hook — populates the registry once all CDI beans
   * are constructed. Throws {@link IllegalStateException} on the
   * duplicate-shape-IRI fail-fast condition; otherwise completes
   * cleanly and emits one INFO line per registered shape.
   */
  void onStartup(@Observes StartupEvent ev) {
    discover();
  }

  /**
   * Drive discovery + index-build. Public so cross-package tests can
   * stage a registry + drive it without booting Quarkus; the
   * production path runs this from {@link #onStartup(StartupEvent)}.
   *
   * @throws IllegalStateException when two renderers claim the same
   *         shape IRI — fail-fast on build-time packaging conflict
   */
  public void discover() {
    Map<String, ViewRecipeRenderer> map = new LinkedHashMap<>();
    Set<String> shapes = new LinkedHashSet<>();
    int rendererCount = 0;

    Iterable<ViewRecipeRenderer> source = testRenderers != null
      ? testRenderers
      : ServiceLoader.load(
        ViewRecipeRenderer.class,
        Thread.currentThread().getContextClassLoader()
      );

    try {
      for (ViewRecipeRenderer renderer : source) {
        if (renderer == null) continue;
        rendererCount++;
        Set<String> claims;
        try {
          claims = renderer.supportedShapeIris();
        } catch (RuntimeException ex) {
          Log.warnf(
            ex,
            "VIS-S1: ViewRecipeRenderer '%s' threw from supportedShapeIris() — skipping",
            renderer.getClass().getName()
          );
          continue;
        }
        if (claims == null || claims.isEmpty()) {
          Log.warnf(
            "VIS-S1: ViewRecipeRenderer '%s' declares no supportedShapeIris() — registered but dormant",
            renderer.name()
          );
          continue;
        }
        for (String iri : claims) {
          if (iri == null || iri.isBlank()) {
            Log.warnf(
              "VIS-S1: ViewRecipeRenderer '%s' returned a null/blank shape IRI — skipping that entry",
              renderer.name()
            );
            continue;
          }
          ViewRecipeRenderer prior = map.putIfAbsent(iri, renderer);
          if (prior != null) {
            // FAIL-FAST — duplicate shape IRI across renderers is a
            // build-time packaging defect. Two plugins claiming the
            // same shape is unresolvable at runtime; refusing to
            // start is the actionable signal.
            String msg = String.format(
              "VIS-S1: duplicate shape IRI '%s' claimed by ViewRecipeRenderer '%s' (%s) " +
              "and '%s' (%s). A shape IRI must be uniquely owned by one renderer; " +
              "two plugins cannot share it. Resolve by removing one plugin or by " +
              "narrowing the IRI namespace of one of them.",
              iri,
              prior.name(),
              prior.getClass().getName(),
              renderer.name(),
              renderer.getClass().getName()
            );
            Log.error(msg);
            throw new IllegalStateException(msg);
          }
          shapes.add(iri);
          Log.infof(
            "VIS-S1: ViewRecipeRenderer '%s' (%s) registered for shape <%s>",
            renderer.name(),
            renderer.getClass().getName(),
            iri
          );
        }
      }
    } catch (ServiceConfigurationError ex) {
      // A malformed META-INF/services/... file is a build-time defect
      // on a plugin we don't otherwise own. Log loudly and continue
      // with whatever we'd discovered before the bad entry. This
      // matches PluginRegistry's classpath-walk posture.
      Log.warnf(
        ex,
        "VIS-S1: ServiceLoader walk failed for ViewRecipeRenderer — check META-INF/services entries"
      );
    }

    this.byShape = Collections.unmodifiableMap(map);
    this.registeredShapes = Collections.unmodifiableSet(shapes);

    Log.infof(
      "VIS-S1: ViewRecipeRendererRegistry ready — %d renderer(s) discovered, %d shape IRI(s) registered",
      rendererCount,
      shapes.size()
    );
  }

  /**
   * Look up the renderer that claims the given shape IRI.
   *
   * @param shapeIri the SHACL {@code sh:NodeShape} IRI from the
   *                 VIEW_RECIPE template body's {@code viewRecipeShape}
   *                 field; may be null or blank (returns empty)
   * @return the matching renderer, or {@link Optional#empty()} when no
   *         plugin claims this shape IRI — the dispatcher's signal to
   *         fall back to the in-tree DECLARED-status projection
   */
  public Optional<ViewRecipeRenderer> resolve(String shapeIri) {
    if (shapeIri == null || shapeIri.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(byShape.get(shapeIri));
  }

  /**
   * @return the IRIs of every shape this registry is serving, in
   *         registration order. Surfaced by a future
   *         {@code GET /v2/admin/view-recipe-renderers} admin
   *         endpoint and by the startup INFO line.
   */
  public List<String> registeredShapes() {
    return List.copyOf(registeredShapes);
  }

  /**
   * @return the number of renderer impls discovered (regardless of
   *         whether they claimed any shape IRI). Distinct from
   *         {@link #registeredShapes()} when some impls returned
   *         empty supportedShapeIris() sets.
   */
  public int rendererCount() {
    // The set of *renderer instances* — there can be N IRIs per
    // instance, so count distinct values, not keys.
    return Set.copyOf(byShape.values()).size();
  }
}
