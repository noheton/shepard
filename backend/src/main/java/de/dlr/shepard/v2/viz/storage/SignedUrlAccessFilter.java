package de.dlr.shepard.v2.viz.storage;

import de.dlr.shepard.common.exceptions.ApiError;
import io.quarkus.logging.Log;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.Provider;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

/**
 * VIS-S1b — JAX-RS {@code ContainerRequestFilter} that validates signed
 * URLs on the {@code /v2/viz/**} path surface.
 *
 * <h2>What it guards</h2>
 *
 * <p>Any request whose URI path starts with {@code /v2/viz/} (after the
 * application root {@code /shepard/api/} is stripped by
 * {@link de.dlr.shepard.common.filters.RequestPathHelper#applicationPath}) is
 * subject to this filter. Requests on other paths pass through immediately
 * with no overhead.
 *
 * <h2>Token format</h2>
 *
 * <p>The caller must supply the token either as:
 * <ul>
 *   <li>query parameters {@code ?exp={epochSeconds}&amp;sig={hexHmac}} on the
 *       request URI (canonical form, set by {@link SignedUrlIssuer#mintSignedUrl}), or</li>
 *   <li>the {@code X-Viz-Token} request header with value
 *       {@code {epochSeconds}:{hexHmac}} (for cases where query parameters
 *       cannot be injected, e.g. {@code <img src>} with header injection).</li>
 * </ul>
 *
 * <h2>Validation steps</h2>
 *
 * <ol>
 *   <li>Parse {@code exp} (Unix epoch seconds) and {@code sig} (hex HMAC).</li>
 *   <li>Check {@code exp &gt; now} — reject with 401 if expired.</li>
 *   <li>Recompute {@code HMAC-SHA256(signing-key, bucket + "|" + objectKey + "|" + exp)}
 *       using {@link SignedUrlIssuer#computeHmacBytes} — same message
 *       that {@link SignedUrlIssuer#mintSignedUrl} signed.</li>
 *   <li>Compare using {@link SignedUrlIssuer#constantTimeEq} — timing-safe,
 *       no early exit on first differing byte.</li>
 *   <li>Abort with 401 on any failure; pass through on success.</li>
 * </ol>
 *
 * <h2>Design constraints</h2>
 *
 * <ul>
 *   <li>Runs at priority {@code AUTHENTICATION + 1} — after
 *       {@link de.dlr.shepard.common.filters.JWTFilter} (which runs at
 *       {@link Priorities#AUTHENTICATION}). On a viz path the JWT may not be
 *       present (browser img/video tags can't inject headers); this filter is
 *       the sole auth mechanism for {@code /v2/viz/**}.</li>
 *   <li>Does NOT modify {@link de.dlr.shepard.common.filters.JWTFilter},
 *       {@link de.dlr.shepard.auth.permission.services.PermissionsService}, or
 *       any other auth component.</li>
 *   <li>Is additive — if a path is not under {@code /v2/viz/} the filter
 *       returns immediately without touching the request context.</li>
 * </ul>
 *
 * <h2>Cross-references</h2>
 *
 * <ul>
 *   <li>{@code aidocs/16} — VIS-S1b row</li>
 *   <li>{@link SignedUrlIssuer} — mints the tokens this filter validates</li>
 * </ul>
 */
@Provider
@Priority(Priorities.AUTHENTICATION + 1)
@ApplicationScoped
public class SignedUrlAccessFilter implements ContainerRequestFilter {

  /** The application-relative path prefix this filter guards (no trailing slash). */
  static final String VIZ_PATH_PREFIX = "/v2/viz";

  /** Header alternative to query parameters — useful for element src attributes with no query support. */
  static final String VIZ_TOKEN_HEADER = "X-Viz-Token";

  @Inject
  SignedUrlIssuer issuer;

  @Override
  public void filter(ContainerRequestContext requestContext) {
    // ─── 1. Scope check — only /v2/viz/** paths are our concern ─────────────
    String path = applicationPath(requestContext);
    if (!isVizPath(path)) {
      return;
    }

    // ─── 2. Extract token parameters (query params or header) ────────────────
    TokenParams params = extractTokenParams(requestContext);
    if (params == null) {
      Log.warnf(
        "VIS-S1b: viz request to %s rejected — no signed-URL token (exp+sig query params or X-Viz-Token header)",
        path
      );
      requestContext.abortWith(unauthorized("Missing signed-URL token (exp and sig query params, or X-Viz-Token header)"));
      return;
    }

    // ─── 3. Expiry check ─────────────────────────────────────────────────────
    long nowEpochSeconds = Instant.now().getEpochSecond();
    if (params.exp() <= nowEpochSeconds) {
      Log.warnf(
        "VIS-S1b: viz request to %s rejected — signed URL expired at epoch %d (now %d)",
        path, params.exp(), nowEpochSeconds
      );
      requestContext.abortWith(unauthorized("Signed URL has expired"));
      return;
    }

    // ─── 4. HMAC verification ─────────────────────────────────────────────────
    // Reconstruct the bucket + objectKey from the path so the HMAC message
    // is determined by the actual request path, not a caller-supplied value.
    // Path shape: /v2/viz/objects/{bucket}/{encodedKey}...
    // We sign the decoded object key to match what mintSignedUrl() signed.
    BucketAndKey bk = parseBucketAndKey(path);
    if (bk == null) {
      // Path under /v2/viz/ but not /v2/viz/objects/ — no HMAC to verify,
      // pass through (this path family is for future admin/meta endpoints).
      return;
    }

    if (!verifyToken(bk.bucket(), bk.objectKey(), params.exp(), params.sig())) {
      Log.warnf(
        "VIS-S1b: viz request to %s rejected — HMAC signature invalid",
        path
      );
      requestContext.abortWith(unauthorized("Signed URL token is invalid or has been tampered with"));
      return;
    }

    // Token is valid — allow the request to proceed to the resource.
    Log.debugf("VIS-S1b: viz request to %s passed signed-URL check (exp=%d)", path, params.exp());
  }

  // ─── Verification helper (package-private for tests) ──────────────────────

  /**
   * Recompute the expected HMAC for the given parameters and compare
   * against the supplied {@code sig} in constant time.
   *
   * @param bucket    the S3 bucket name (from the request path)
   * @param objectKey the S3 object key (decoded, from the request path)
   * @param exp       expiry epoch seconds
   * @param sig       the caller-supplied hex HMAC to verify
   * @return {@code true} when the HMAC is correct
   */
  boolean verifyToken(String bucket, String objectKey, long exp, String sig) {
    if (sig == null || sig.isBlank()) return false;
    try {
      String message = SignedUrlIssuer.buildMessage(bucket, objectKey, exp);
      byte[] expected = issuer.computeHmacBytes(message);
      byte[] actual = HexFormat.of().parseHex(sig);
      return SignedUrlIssuer.constantTimeEq(expected, actual);
    } catch (IllegalArgumentException ex) {
      // sig is not valid hex
      Log.debugf("VIS-S1b: failed to parse sig as hex: %s", ex.getMessage());
      return false;
    } catch (IllegalStateException ex) {
      Log.warnf(ex, "VIS-S1b: HMAC computation failed during token verification");
      return false;
    }
  }

  // ─── Path helpers ──────────────────────────────────────────────────────────

  /**
   * Return the application-relative path, stripping the {@code /shepard/api/}
   * prefix the same way
   * {@link de.dlr.shepard.common.filters.RequestPathHelper#applicationPath} does.
   */
  static String applicationPath(ContainerRequestContext ctx) {
    String path = ctx.getUriInfo().getPath();
    if (path == null || path.isEmpty()) return "/";
    String normalised = path.startsWith("/") ? path : "/" + path;
    String prefix = "/shepard/api/";
    if (normalised.startsWith(prefix)) {
      return normalised.substring(prefix.length() - 1);
    }
    if (normalised.equals("/shepard/api")) return "/";
    return normalised;
  }

  /** Return true when the (application-relative) path starts with {@code /v2/viz}. */
  static boolean isVizPath(String path) {
    if (path == null) return false;
    return path.equals(VIZ_PATH_PREFIX) || path.startsWith(VIZ_PATH_PREFIX + "/");
  }

  /**
   * Parse {@code /v2/viz/objects/{bucket}/{encodedKey}} into its components.
   * Returns {@code null} for paths that don't match the objects sub-path
   * (e.g. future admin endpoints under {@code /v2/viz/}).
   */
  static BucketAndKey parseBucketAndKey(String path) {
    String objectsPrefix = "/v2/viz/objects/";
    if (!path.startsWith(objectsPrefix)) return null;
    String remainder = path.substring(objectsPrefix.length());
    int slash = remainder.indexOf('/');
    if (slash < 0) {
      // /v2/viz/objects/{bucket} with no object key — malformed, reject
      return null;
    }
    String bucket = remainder.substring(0, slash);
    String encodedKey = remainder.substring(slash + 1);
    // Strip any ?query suffix that may appear (UriInfo.getPath() should not
    // include query, but be defensive).
    int qmark = encodedKey.indexOf('?');
    if (qmark >= 0) encodedKey = encodedKey.substring(0, qmark);
    if (bucket.isBlank() || encodedKey.isBlank()) return null;
    // Decode the key — mintSignedUrl signs the decoded key.
    String decodedKey = java.net.URLDecoder.decode(encodedKey, StandardCharsets.UTF_8);
    return new BucketAndKey(bucket, decodedKey);
  }

  // ─── Token extraction ──────────────────────────────────────────────────────

  private TokenParams extractTokenParams(ContainerRequestContext ctx) {
    // Prefer query params (canonical form from mintSignedUrl).
    String expStr = ctx.getUriInfo().getQueryParameters().getFirst("exp");
    String sig    = ctx.getUriInfo().getQueryParameters().getFirst("sig");

    if (expStr != null && sig != null) {
      return parseExpAndSig(expStr, sig);
    }

    // Fallback: X-Viz-Token header with value "{exp}:{sig}".
    String header = ctx.getHeaderString(VIZ_TOKEN_HEADER);
    if (header != null && !header.isBlank()) {
      int colon = header.indexOf(':');
      if (colon > 0) {
        return parseExpAndSig(header.substring(0, colon), header.substring(colon + 1));
      }
    }

    return null;
  }

  private TokenParams parseExpAndSig(String expStr, String sig) {
    try {
      long exp = Long.parseLong(expStr.trim());
      return new TokenParams(exp, sig.trim());
    } catch (NumberFormatException ex) {
      Log.debugf("VIS-S1b: could not parse exp value '%s' as long", expStr);
      return null;
    }
  }

  // ─── Response helpers ─────────────────────────────────────────────────────

  private Response unauthorized(String message) {
    return Response.status(Status.UNAUTHORIZED)
      .entity(new ApiError(Status.UNAUTHORIZED.getStatusCode(), "SignedUrlAccessFilter", message))
      .build();
  }

  // ─── Value types ──────────────────────────────────────────────────────────

  record TokenParams(long exp, String sig) {}
  record BucketAndKey(String bucket, String objectKey) {}
}
