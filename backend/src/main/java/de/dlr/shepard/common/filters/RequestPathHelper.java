package de.dlr.shepard.common.filters;

import de.dlr.shepard.common.util.Constants;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.PathSegment;
import java.util.List;

/**
 * Path-segment helpers shared by filters/services that interpret JAX-RS
 * request paths semantically (e.g. dispatching on the first segment to
 * decide which permission check to apply).
 *
 * <p><b>Why this exists.</b> Pre-P4, {@code quarkus.http.root-path} was set
 * to {@code /shepard/api} and {@link jakarta.ws.rs.core.UriInfo#getPath()}
 * returned the path <em>relative to that root</em> — so the first segment
 * of {@code /shepard/api/collections/42} was {@code "collections"}.
 *
 * <p>Post-P4, root-path is {@code /} and every resource carries an explicit
 * {@code @Path("/shepard/api/...")} prefix; the JAX-RS-reported path now
 * leads with {@code shepard / api / ...}. To keep the existing
 * first-segment dispatch (in {@code PermissionsService}, {@code
 * SubscriptionFilter}, {@code MigrationModeFilter}, {@code
 * PublicEndpointRegistry}) byte-equivalent, those callers route through
 * {@link #applicationSegments} which strips the {@code shepard/api/}
 * prefix back off — the dispatch logic stays identical.
 *
 * <p>Resources under {@code /v2/...} are intentionally <em>not</em>
 * stripped (they don't carry the {@code shepard/api} prefix to begin with);
 * those routes use annotation-based authorisation per L2d / F1.
 */
public final class RequestPathHelper {

  private static final String API = Constants.SHEPARD_API;
  private static final String SHEPARD = "shepard";
  private static final String API_TAIL = "api";

  private RequestPathHelper() {
    // hide constructor
  }

  /**
   * Returns the path segments with the {@code shepard/api/} prefix stripped
   * so that the first segment is the resource family name (e.g.
   * {@code collections}, {@code users}, {@code temp}). For paths that do
   * not start with {@code shepard/api/} (e.g. future {@code /v2/...} routes
   * or {@code /healthz}), the segments are returned unchanged.
   */
  public static List<PathSegment> applicationSegments(ContainerRequestContext ctx) {
    List<PathSegment> segments = ctx.getUriInfo().getPathSegments();
    if (segments.size() < 2) return segments;
    String first = segments.get(0).getPath();
    String second = segments.get(1).getPath();
    if (SHEPARD.equals(first) && API_TAIL.equals(second)) {
      return segments.subList(2, segments.size());
    }
    return segments;
  }

  /**
   * Returns {@link jakarta.ws.rs.core.UriInfo#getPath()} with the
   * {@code shepard/api/} prefix stripped. Mirrors {@link
   * #applicationSegments} for callers that consume {@code String} paths
   * (e.g. {@code .startsWith("/temp")}). Always returns a leading
   * {@code /}.
   */
  public static String applicationPath(ContainerRequestContext ctx) {
    String path = ctx.getUriInfo().getPath();
    if (path == null || path.isEmpty()) return "/";
    String normalised = path.startsWith("/") ? path : "/" + path;
    String prefix = "/" + API + "/";
    if (normalised.startsWith(prefix)) {
      return normalised.substring(prefix.length() - 1); // keep the leading '/'
    }
    if (normalised.equals("/" + API)) return "/";
    return normalised;
  }
}
