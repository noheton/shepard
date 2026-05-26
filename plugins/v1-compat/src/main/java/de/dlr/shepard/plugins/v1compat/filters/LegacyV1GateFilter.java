package de.dlr.shepard.plugins.v1compat.filters;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.plugins.v1compat.services.LegacyV1ConfigService;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * V1COMPAT.0 — request filter that short-circuits every
 * {@code /shepard/api/...} request with HTTP 410 Gone + an RFC 7807
 * problem-detail body when {@code :LegacyV1Config.enabled=false}
 * (per the design's clarification 1 lean A).
 *
 * <p><b>Priority.</b> Runs at {@code Priorities.AUTHENTICATION - 100}
 * — strictly before {@code JWTFilter} (which runs at
 * {@code AUTHENTICATION = 1000}). This is the load-bearing decision
 * per the advisor's review: an anonymous caller hitting a disabled
 * v1 surface must see 410 (the right status — "administratively
 * removed"), NOT 401 (which would imply "send credentials and try
 * again"). The 410-before-auth ordering also keeps the deprecated
 * surface cheap on the auth path — no JWT parsing for traffic the
 * operator has already declared off.
 *
 * <p><b>Performance.</b> The {@link LegacyV1ConfigService#isEnabled()}
 * call goes through the service's 5 s in-process read-through cache;
 * the filter does not hit Neo4j on the request path.
 *
 * <p><b>Failure mode.</b> If the service's underlying DAO read fails,
 * the service is fail-open (returns the deploy-time default
 * {@code true}). The v1 surface stays available rather than 410-storming
 * legitimate callers during a Neo4j hiccup.
 */
@Provider
@ApplicationScoped
@Priority(Priorities.AUTHENTICATION - 100)
public class LegacyV1GateFilter implements ContainerRequestFilter {

  /** RFC 7807 problem type URI for the disabled-v1-surface body. */
  public static final String PROBLEM_TYPE_V1_DISABLED = "https://shepard.dlr.de/problems/v1-disabled";

  /** Stable human-readable title for the problem body. */
  static final String PROBLEM_TITLE = "Legacy v1 surface disabled";

  /** Stable detail blurb pointing operators at the migration target. */
  static final String PROBLEM_DETAIL =
    "The legacy /shepard/api/... surface is disabled on this instance. Migrate to /v2/.";

  @Inject
  LegacyV1ConfigService configService;

  /** Production no-arg ctor for CDI. */
  public LegacyV1GateFilter() {}

  /** Test-seam ctor — inject the service directly. */
  public LegacyV1GateFilter(LegacyV1ConfigService configService) {
    this.configService = configService;
  }

  @Override
  public void filter(ContainerRequestContext request) {
    // Only the upstream-frozen surface — every other path is a no-op.
    if (!isV1Path(request)) return;

    if (configService.isEnabled()) {
      // Default state: v1 surface is on. Let the request continue to
      // JWTFilter → the resource. The deprecation filter (priority
      // higher than AUTHENTICATION) instruments the response.
      return;
    }

    // v1 surface deliberately off — emit 410 + RFC 7807 body. The
    // {@code instance} field carries the original path so a caller's
    // logs can correlate to the exact request that was rejected.
    String originalPath = request.getUriInfo() == null
      ? null
      : "/" + (request.getUriInfo().getPath() == null ? "" : request.getUriInfo().getPath());

    ProblemJson body = new ProblemJson(
      PROBLEM_TYPE_V1_DISABLED,
      PROBLEM_TITLE,
      Response.Status.GONE.getStatusCode(),
      PROBLEM_DETAIL,
      originalPath
    );

    request.abortWith(
      Response
        .status(Response.Status.GONE)
        .type(Constants.APPLICATION_PROBLEM_JSON)
        // RFC 8594 deprecation hints, also emitted on the disabled
        // response so a deprecation-aware client sees the upgrade
        // pointer even when the surface is fully gated off.
        .header("Deprecation", "true")
        .header("Link", "</v2/>; rel=\"successor-version\"")
        .header("X-Shepard-Legacy", "true")
        .entity(body)
        .build()
    );
  }

  /**
   * Whether the inbound request targets the upstream-frozen
   * {@code /shepard/api/...} surface. Mirrors the
   * {@link de.dlr.shepard.common.filters.RequestPathHelper#applicationPath}
   * detection without depending on the helper at this priority
   * (which is intentionally before the auth+resource scope).
   */
  static boolean isV1Path(ContainerRequestContext request) {
    if (request == null || request.getUriInfo() == null) return false;
    String path = request.getUriInfo().getPath();
    if (path == null) return false;
    // UriInfo.getPath() returns the path WITHOUT the leading slash
    // because quarkus.http.root-path is "/". So we match the bare
    // prefix "shepard/api" (no leading "/").
    return path.startsWith(Constants.SHEPARD_API + "/") || path.equals(Constants.SHEPARD_API);
  }
}
