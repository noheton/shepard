package de.dlr.shepard.common.filters;

import jakarta.ws.rs.container.ContainerRequestContext;
import java.nio.file.Path;
import java.util.Set;

public class PublicEndpointRegistry {

  /**
   * Public-endpoint paths. Compared against the **normalised** request path
   * with **exact equality** — see {@link #isRequestPathPublic} for why.
   */
  private static final Set<String> PUBLIC_PATHS = Set.of("/versionz");

  /**
   * Returns {@code true} when the request path matches a registered public
   * endpoint exactly.
   *
   * <p>Earlier versions used {@code startsWith(path)} which had two
   * problems: (a) {@code /versionz/anything} would match — fine for today's
   * single-entry list but a foot-gun if a future entry like {@code /users}
   * is added (it would match {@code /usersearch}, {@code /users/admin},
   * etc.); (b) {@code .startsWith} did not normalise the URI, so a path
   * like {@code /versionz/../containers/1} would slip through without any
   * actual traversal happening. Both vectors are closed by exact-match
   * against a normalised path. (`aidocs/07` H5.)
   */
  public static boolean isRequestPathPublic(ContainerRequestContext requestContext) {
    String requestPath = requestContext.getUriInfo().getPath();
    return PUBLIC_PATHS.contains(normalise(requestPath));
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
