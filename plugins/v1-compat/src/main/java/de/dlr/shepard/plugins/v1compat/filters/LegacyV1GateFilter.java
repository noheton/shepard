package de.dlr.shepard.plugins.v1compat.filters;

import de.dlr.shepard.plugins.v1compat.services.LegacyV1ConfigService;
import io.quarkus.logging.Log;
import io.quarkus.vertx.http.runtime.filters.Filters;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * V1COMPAT.0 — Vert.x route filter that short-circuits every
 * {@code /shepard/api/...} request with HTTP 410 Gone + an RFC 7807
 * problem-detail body when {@code :LegacyV1Config.enabled=false}
 * (per the design's clarification 1 lean A).
 *
 * <p><b>Priority.</b> Runs at Vert.x filter priority 200 (higher = earlier).
 * This places the gate strictly before JWTFilter (priority lower) and
 * before the deprecation filter (priority 190). An anonymous caller
 * hitting a disabled v1 surface sees 410 "administratively removed",
 * NOT 401 "send credentials", consistent with the original design intent.
 *
 * <p><b>Registration.</b> Uses Quarkus's Vert.x {@link Filters} CDI event
 * via {@code @Observes}. This is the ONLY mechanism that reliably registers
 * filters from plugin JARs — both {@code @Provider ContainerRequestFilter}
 * and {@code @ServerRequestFilter} are processed by Quarkus's build-time
 * annotation scanners that only cover the main deployment JAR. CDI event
 * observation fires at runtime and is JAR-origin-agnostic (plugin JARs
 * indexed via {@code quarkus.index-dependency.*} participate in CDI scanning
 * fully). Precedent: {@link de.dlr.shepard.v2.mcp.McpAuthFilter}.
 *
 * <p><b>Performance.</b> The {@link LegacyV1ConfigService#isEnabled()} call
 * goes through the service's 5 s in-process read-through cache; this filter
 * never hits Neo4j on the hot v1-request path.
 *
 * <p><b>Failure mode.</b> If the service's underlying DAO read fails, the
 * service is fail-open (returns the deploy-time default {@code true}). The
 * v1 surface stays available rather than 410-storming legitimate callers
 * during a Neo4j hiccup.
 */
@ApplicationScoped
public class LegacyV1GateFilter {

  /** RFC 7807 problem type URI for the disabled-v1-surface body. */
  public static final String PROBLEM_TYPE_V1_DISABLED = "https://shepard.dlr.de/problems/v1-disabled";

  /** Stable human-readable title for the problem body. */
  static final String PROBLEM_TITLE = "Legacy v1 surface disabled";

  /** Stable detail blurb pointing operators at the migration target. */
  static final String PROBLEM_DETAIL =
    "The legacy /shepard/api/... surface is disabled on this instance. Migrate to /v2/.";

  /**
   * Vert.x filter priority. Higher = runs earlier per the {@link Filters}
   * contract. 200 keeps the gate ahead of the deprecation filter (190) and
   * the default Quarkus auth/route infrastructure.
   */
  static final int GATE_PRIORITY = 200;

  @Inject
  LegacyV1ConfigService configService;

  /** Production no-arg ctor for CDI. */
  public LegacyV1GateFilter() {}

  /** Test-seam ctor — inject the service directly. */
  public LegacyV1GateFilter(LegacyV1ConfigService configService) {
    this.configService = configService;
  }

  void registerFilter(@Observes Filters filters) {
    filters.register(this::handle, GATE_PRIORITY);
    Log.debugf("V1COMPAT.0: LegacyV1GateFilter registered at Vert.x priority %d", GATE_PRIORITY);
  }

  void handle(RoutingContext rc) {
    String path = rc.normalizedPath();
    if (!isV1Path(path)) {
      rc.next();
      return;
    }

    if (configService.isEnabled()) {
      // Default state: v1 surface is on. Let the request continue to the
      // deprecation filter (190) → JWTFilter → the resource.
      rc.next();
      return;
    }

    // v1 surface deliberately off — emit 410 + RFC 7807 body.
    String body = buildGoneBody(path);
    rc.response()
      .setStatusCode(410)
      .putHeader("Content-Type", "application/problem+json")
      // RFC 8594 deprecation hints, also on the 410 so a deprecation-aware
      // client sees the upgrade pointer even when the surface is gated off.
      .putHeader("Deprecation", "true")
      .putHeader("Link", "</v2/>; rel=\"successor-version\"")
      .putHeader("X-Shepard-Legacy", "true")
      .end(body);
    // Do NOT call rc.next() — the chain stops here for disabled v1 paths.
  }

  /**
   * Whether the inbound request targets the upstream-frozen
   * {@code /shepard/api/...} surface.
   *
   * <p>Expects the path from Vert.x {@code RoutingContext.normalizedPath()}
   * which always includes a leading slash. The matching prefix is therefore
   * {@code /shepard/api} (with leading slash).
   */
  static boolean isV1Path(String path) {
    if (path == null) return false;
    return path.startsWith("/shepard/api/") || path.equals("/shepard/api");
  }

  private static String buildGoneBody(String path) {
    return "{" +
      "\"type\":\"" + PROBLEM_TYPE_V1_DISABLED + "\"," +
      "\"title\":\"" + PROBLEM_TITLE + "\"," +
      "\"status\":410," +
      "\"detail\":\"" + PROBLEM_DETAIL + "\"," +
      "\"instance\":\"" + escapeJson(path) + "\"" +
      "}";
  }

  private static String escapeJson(String s) {
    return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
