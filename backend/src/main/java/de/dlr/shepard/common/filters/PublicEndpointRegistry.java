package de.dlr.shepard.common.filters;

import jakarta.ws.rs.container.ContainerRequestContext;
import java.nio.file.Path;
import java.util.Set;

/**
 * Registry of request paths that bypass {@link JWTFilter}. Path
 * matching uses {@code startsWith} against
 * {@code requestContext.getUriInfo().getPath()} — pre-H5; H5 will
 * harden this to exact-match against a normalised path.
 *
 * <p>A0 adds {@code /v2/admin/bootstrap} — the bootstrap-token
 * endpoint must be reachable before any user is authenticated.
 */
public class PublicEndpointRegistry {

  /**
   * Public-endpoint paths, application-relative (i.e. without the
   * {@code /shepard/api/} prefix that resource classes carry post-P4).
   * Compared against the {@link RequestPathHelper#applicationPath} of
   * the incoming request with **exact equality** — see
   * {@link #isRequestPathPublic} for why.
   *
   * <p>{@code /v2/admin/bootstrap} (A0) is reachable pre-auth so the
   * first instance-admin can be created; the bootstrap-token gate
   * inside the endpoint enforces single-use.
   */
  private static final Set<String> PUBLIC_PATHS = Set.of(
    "/versionz",
    "/v2/admin/bootstrap",
    // AAS1-well-known (aidocs/52 §4a.5): capability self-description,
    // reachable pre-auth so external AAS-aware clients can discover
    // what this shepard speaks. Carries only capability flags / counts
    // — never per-Shell identifiers.
    "/v2/aas/.well-known/aas-server",
    // P4c — per-shelf OpenAPI documents. OpenAPI specs are public,
    // same posture as the combined /shepard/doc/openapi.json that the
    // smallrye-openapi extension already serves pre-auth. The paths
    // here are application-relative (post-RequestPathHelper); the
    // /shepard/api/ prefix never reaches application code because the
    // resource @Path is /shepard/doc/openapi/{v1,v2}.json directly.
    "/shepard/doc/openapi/v1.json",
    "/shepard/doc/openapi/v2.json"
  );

  /**
   * Returns {@code true} when the request path matches a registered public
   * endpoint exactly (modulo {@code /shepard/api/} prefix and trailing
   * slash).
   *
   * <p>Earlier versions used {@code startsWith(path)} which had two
   * problems: (a) {@code /versionz/anything} would match — fine for today's
   * single-entry list but a foot-gun if a future entry like {@code /users}
   * is added (it would match {@code /usersearch}, {@code /users/admin},
   * etc.); (b) {@code .startsWith} did not normalise the URI, so a path
   * like {@code /versionz/../containers/1} would slip through without any
   * actual traversal happening. Both vectors are closed by exact-match
   * against a normalised path. (`aidocs/07` H5.)
   *
   * <p>Post-P4 the request URI carries the {@code /shepard/api/} prefix;
   * {@link RequestPathHelper#applicationPath} strips it so the public-path
   * registry stays in application-relative form.
   */
  public static boolean isRequestPathPublic(ContainerRequestContext requestContext) {
    String applicationPath = RequestPathHelper.applicationPath(requestContext);
    return PUBLIC_PATHS.contains(normalise(applicationPath));
  }

  static String normalise(String requestPath) {
    String withLeadingSlash = requestPath.startsWith("/") ? requestPath : "/" + requestPath;
    // Path.normalize() resolves "." and ".." segments without touching the
    // filesystem.
    String normalised = Path.of(withLeadingSlash).normalize().toString();
    // Canonical form: strip trailing slash so `/versionz/` and `/versionz`
    // both match.
    if (normalised.length() > 1 && normalised.endsWith("/")) {
      normalised = normalised.substring(0, normalised.length() - 1);
    }
    return normalised;
  }
}
