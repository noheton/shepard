package de.dlr.shepard.plugins.v1compat.filters;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.plugins.v1compat.services.LegacyV1StatsService;
import io.quarkus.logging.Log;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;

/**
 * V1COMPAT.0 — request + response filter that instruments every
 * {@code /shepard/api/...} request (when the v1 surface is on) per
 * the design's clarification 3 lean D (hybrid).
 *
 * <p>On every request:
 * <ol>
 *   <li>increment the in-memory counters
 *       ({@link LegacyV1StatsService#recordHit});</li>
 *   <li>WARN once per {@code (path, principal)} pair per process
 *       lifetime (deduplicated via
 *       {@link LegacyV1StatsService#checkAndMarkFirstHit}) so the
 *       high-rate MQTT collectors mentioned in
 *       {@code aidocs/103 R4} don't flood the log;</li>
 *   <li>for write methods (POST / PUT / PATCH / DELETE), emit one
 *       INFO-level audit line documenting the mutation — the
 *       durable {@code :Activity} audit trail PROV1a already
 *       captures the request via the existing pipeline, so this
 *       filter only adds a structured marker for grep-from-WARN
 *       audits.</li>
 * </ol>
 *
 * <p>On every response:
 * <ol>
 *   <li>add the three additive deprecation headers per CL1 (the
 *       header-strategy clarification): {@code Deprecation: true} +
 *       {@code Link: </v2/>; rel="successor-version"} (RFC 8594) +
 *       {@code X-Shepard-Legacy: true} (fork-specific marker the
 *       frontend banner watches).</li>
 * </ol>
 *
 * <p><b>Priority.</b> {@code Priorities.AUTHENTICATION + 1} —
 * strictly AFTER {@code JWTFilter} (1000), so the {@link
 * SecurityContext} has been populated and the principal sub is
 * available for the per-{@code (path, sub)} dedup. Mirrors
 * {@code UserFilter}'s exact priority placement.
 *
 * <p>The 410-gate filter ({@link LegacyV1GateFilter}) runs even
 * earlier — at {@code AUTHENTICATION - 100}. When the surface is
 * gated off, the gate's {@code abortWith} short-circuits the
 * pipeline before this filter runs; the headers + counters apply
 * only to requests that get to the resource.
 */
@Provider
@ApplicationScoped
@Priority(Priorities.AUTHENTICATION + 1)
public class LegacyV1DeprecationFilter implements ContainerRequestFilter, ContainerResponseFilter {

  @Inject
  LegacyV1StatsService stats;

  /** Production no-arg ctor for CDI. */
  public LegacyV1DeprecationFilter() {}

  /** Test-seam ctor — inject the service directly. */
  public LegacyV1DeprecationFilter(LegacyV1StatsService stats) {
    this.stats = stats;
  }

  @Override
  public void filter(ContainerRequestContext request) {
    if (!LegacyV1GateFilter.isV1Path(request)) return;

    String pattern = pathPattern(request);
    String principal = principalSub(request);
    stats.recordHit(pattern, principal);

    if (stats.checkAndMarkFirstHit(pattern, principal)) {
      Log.warnf(
        "V1COMPAT.0: first v1 hit this process by principal '%s' on path-pattern '%s'. " +
        "The /shepard/api/... surface is deprecated; migrate to /v2/. " +
        "See /v2/admin/legacy/v1/stats for the full breakdown.",
        principal,
        pattern
      );
    }

    if (isWriteMethod(request.getMethod())) {
      // PROV1a's ProvenanceCaptureFilter persists the durable :Activity
      // row; this INFO line is the human-readable audit marker that
      // operators grep when investigating "who wrote what via v1
      // after we declared v1 deprecated". Reads (GET) are silent
      // here per the design's lean D (counters only, no audit).
      Log.infof(
        "V1COMPAT.0 WRITE: principal='%s' method=%s path='%s' pattern='%s'",
        principal,
        request.getMethod(),
        request.getUriInfo() == null ? "" : request.getUriInfo().getPath(),
        pattern
      );
    }
  }

  @Override
  public void filter(ContainerRequestContext request, ContainerResponseContext response) {
    if (!LegacyV1GateFilter.isV1Path(request)) return;
    if (response == null || response.getHeaders() == null) return;
    // putSingle (not add) so a double-fire of the filter — e.g.
    // through Quarkus's internal forwarding — doesn't produce
    // duplicate headers. Per RFC 8594 §3 / §4 a single value per
    // header is the correct shape.
    response.getHeaders().putSingle("Deprecation", "true");
    response.getHeaders().putSingle("Link", "</v2/>; rel=\"successor-version\"");
    response.getHeaders().putSingle("X-Shepard-Legacy", "true");
  }

  // ──────────────────────────────────────────────────────────────────────
  //  Helpers
  // ──────────────────────────────────────────────────────────────────────

  /**
   * Reduce a full v1 path to its endpoint family — the second path
   * segment after {@code shepard/api/}. Used as the counter key so a
   * thousand {@code /shepard/api/collections/42/dataObjects/7} hits
   * roll up to one {@code /shepard/api/collections} row rather than
   * a thousand distinct counters.
   *
   * <p>Examples:
   * <ul>
   *   <li>{@code shepard/api/collections} → {@code /shepard/api/collections}</li>
   *   <li>{@code shepard/api/collections/42/dataObjects/7} → {@code /shepard/api/collections}</li>
   *   <li>{@code shepard/api/users/me} → {@code /shepard/api/users}</li>
   *   <li>{@code shepard/api} (bare) → {@code /shepard/api}</li>
   * </ul>
   */
  static String pathPattern(ContainerRequestContext request) {
    String prefix = Constants.SHEPARD_API; // "shepard/api"
    String path = request.getUriInfo() == null ? "" : request.getUriInfo().getPath();
    if (path == null || path.isBlank()) return "/" + prefix;
    String trimmed = path.startsWith("/") ? path.substring(1) : path;
    if (!trimmed.startsWith(prefix)) return "/" + trimmed; // shouldn't happen post-isV1Path
    String tail = trimmed.length() > prefix.length() + 1 ? trimmed.substring(prefix.length() + 1) : "";
    if (tail.isEmpty()) return "/" + prefix;
    int slash = tail.indexOf('/');
    String family = slash < 0 ? tail : tail.substring(0, slash);
    return "/" + prefix + "/" + family;
  }

  /**
   * Resolve the principal sub for the per-{@code (path, sub)} dedup.
   * Prefers the JWT/API-key sub claim; falls back to the JAX-RS
   * {@link SecurityContext#getUserPrincipal()}; finally falls back
   * to {@code "anonymous"}. We deliberately don't include the
   * remote IP here — Phase 1 stats are per-principal, not per-IP.
   */
  static String principalSub(ContainerRequestContext request) {
    SecurityContext sc = request.getSecurityContext();
    if (sc != null && sc.getUserPrincipal() != null && sc.getUserPrincipal().getName() != null) {
      return sc.getUserPrincipal().getName();
    }
    return "anonymous";
  }

  private static boolean isWriteMethod(String method) {
    if (method == null) return false;
    return HttpMethod.POST.equalsIgnoreCase(method)
      || HttpMethod.PUT.equalsIgnoreCase(method)
      || "PATCH".equalsIgnoreCase(method)
      || HttpMethod.DELETE.equalsIgnoreCase(method);
  }
}
