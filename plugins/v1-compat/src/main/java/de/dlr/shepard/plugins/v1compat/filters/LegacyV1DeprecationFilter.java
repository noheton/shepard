package de.dlr.shepard.plugins.v1compat.filters;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.plugins.v1compat.services.LegacyV1StatsService;
import io.quarkus.logging.Log;
import io.quarkus.vertx.http.runtime.filters.Filters;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.HttpMethod;

/**
 * V1COMPAT.0 — Vert.x route filter that instruments every
 * {@code /shepard/api/...} request (when the v1 surface is on) per
 * the design's clarification 3 lean D (hybrid).
 *
 * <p>On every v1 request (pre-JAX-RS processing):
 * <ol>
 *   <li>Set three additive deprecation headers per CL1:
 *       {@code Deprecation: true} + {@code Link: </v2/>; rel="successor-version"}
 *       (RFC 8594) + {@code X-Shepard-Legacy: true}. These are set before
 *       {@link RoutingContext#next()} so they survive into the final HTTP
 *       response that RESTEasy Reactive writes.</li>
 *   <li>Increment the in-memory counters
 *       ({@link LegacyV1StatsService#recordHit});</li>
 *   <li>WARN once per {@code path-pattern} per process lifetime (deduplicated
 *       via {@link LegacyV1StatsService#checkAndMarkFirstHit}) so high-rate
 *       collectors don't flood the log;</li>
 *   <li>For write methods (POST / PUT / PATCH / DELETE), emit one INFO-level
 *       audit line documenting the mutation.</li>
 * </ol>
 *
 * <p><b>Principal.</b> This filter runs at Vert.x level, strictly before
 * the JAX-RS JWTFilter (which runs later in the request processing chain).
 * No {@code SecurityContext} is available here. All v1 hits are recorded
 * under the principal {@code "anonymous"} in Phase 1. Phase 2 enhancement:
 * parse the {@code Authorization} header to extract the sub claim directly.
 *
 * <p><b>Priority.</b> Vert.x priority 190 — runs after the gate filter
 * (200) and therefore only processes requests that the gate filter passed
 * through (i.e., v1 is enabled). If the gate filter emitted 410 and did
 * not call {@code rc.next()}, this filter is never invoked.
 *
 * <p><b>Registration.</b> Uses Quarkus's Vert.x {@link Filters} CDI event
 * via {@code @Observes}. See {@link LegacyV1GateFilter} class javadoc for
 * why {@code @Provider} and {@code @ServerRequestFilter} do not work from
 * plugin JARs.
 */
@ApplicationScoped
public class LegacyV1DeprecationFilter {

  /**
   * Vert.x filter priority. Lower than the gate filter (200) so this only
   * runs when the gate passed the request through.
   */
  static final int DEPRECATION_PRIORITY = 190;

  @Inject
  LegacyV1StatsService stats;

  /** Production no-arg ctor for CDI. */
  public LegacyV1DeprecationFilter() {}

  /** Test-seam ctor — inject the service directly. */
  public LegacyV1DeprecationFilter(LegacyV1StatsService stats) {
    this.stats = stats;
  }

  void registerFilter(@Observes Filters filters) {
    filters.register(this::handle, DEPRECATION_PRIORITY);
    Log.debugf("V1COMPAT.0: LegacyV1DeprecationFilter registered at Vert.x priority %d", DEPRECATION_PRIORITY);
  }

  void handle(RoutingContext rc) {
    String path = rc.normalizedPath();
    if (!LegacyV1GateFilter.isV1Path(path)) {
      rc.next();
      return;
    }

    // Set deprecation headers before passing to the next handler.
    // Vert.x putHeader semantics replace any existing value, so a double-fire
    // of this filter (e.g. internal forwarding) never produces duplicate headers.
    // Per RFC 8594 §3/§4 a single value per header is the correct shape.
    rc.response()
      .putHeader("Deprecation", "true")
      .putHeader("Link", "</v2/>; rel=\"successor-version\"")
      .putHeader("X-Shepard-Legacy", "true");

    // Stats and audit logging. This filter runs before JWTFilter (JAX-RS layer),
    // so SecurityContext is not available and the principal is always "anonymous".
    String pattern = pathPattern(path);
    stats.recordHit(pattern, "anonymous");

    if (stats.checkAndMarkFirstHit(pattern, "anonymous")) {
      Log.warnf(
        "V1COMPAT.0: first v1 hit this process on path-pattern '%s'. " +
        "The /shepard/api/... surface is deprecated; migrate to /v2/. " +
        "See /v2/admin/legacy/v1/stats for the full breakdown.",
        pattern
      );
    }

    String method = rc.request().method().name();
    if (isWriteMethod(method)) {
      // PROV1a's ProvenanceCaptureFilter persists the durable :Activity row;
      // this INFO line is the human-readable audit marker for grep audits.
      Log.infof(
        "V1COMPAT.0 WRITE: method=%s path='%s' pattern='%s'",
        method,
        path,
        pattern
      );
    }

    rc.next();
  }

  // ──────────────────────────────────────────────────────────────────────
  //  Helpers
  // ──────────────────────────────────────────────────────────────────────

  /**
   * Reduce a full v1 path to its endpoint family — the first path segment
   * after {@code /shepard/api/}. Used as the counter key so a thousand
   * {@code /shepard/api/collections/42/dataObjects/7} hits roll up to one
   * {@code /shepard/api/collections} row.
   *
   * <p>Expects a Vert.x normalized path (WITH leading slash).
   *
   * <p>Examples:
   * <ul>
   *   <li>{@code /shepard/api/collections} → {@code /shepard/api/collections}</li>
   *   <li>{@code /shepard/api/collections/42/dataObjects/7} → {@code /shepard/api/collections}</li>
   *   <li>{@code /shepard/api/users/me} → {@code /shepard/api/users}</li>
   *   <li>{@code /shepard/api} (bare) → {@code /shepard/api}</li>
   * </ul>
   */
  static String pathPattern(String path) {
    String prefix = "/" + Constants.SHEPARD_API; // "/shepard/api"
    if (path == null || path.isBlank()) return prefix;
    if (!path.startsWith(prefix)) return path; // shouldn't happen post-isV1Path check
    // path.length() <= prefix.length() + 1 covers both "/shepard/api" and "/shepard/api/"
    if (path.length() <= prefix.length() + 1) return prefix;
    String tail = path.substring(prefix.length() + 1); // skip "/shepard/api/"
    if (tail.isEmpty()) return prefix;
    int slash = tail.indexOf('/');
    String family = slash < 0 ? tail : tail.substring(0, slash);
    return prefix + "/" + family;
  }

  private static boolean isWriteMethod(String method) {
    if (method == null) return false;
    return HttpMethod.POST.equalsIgnoreCase(method)
      || HttpMethod.PUT.equalsIgnoreCase(method)
      || "PATCH".equalsIgnoreCase(method)
      || HttpMethod.DELETE.equalsIgnoreCase(method);
  }
}
