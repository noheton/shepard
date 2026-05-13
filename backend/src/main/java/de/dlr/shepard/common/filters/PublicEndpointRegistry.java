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
 *
 * <p><strong>Plugin-contributed public endpoints.</strong> The
 * {@link #PUBLIC_PATHS} + {@link #PUBLIC_PATH_PREFIXES} sets also
 * carry entries owned by plugin modules (today: UH1a's
 * {@code /v2/unhide/feed.jsonld} feed and KIP1g's
 * {@code /v2/.well-known/kip} resolver prefix). The plugin owning
 * the path does not register itself at runtime — the entry is
 * kept here as a static fact for the auth filter's benefit. This
 * keeps the auth surface explicit (one place to audit "what's
 * reachable without a JWT?") at the cost of cross-module
 * coupling: a plugin author adding a new public path must update
 * this registry in the same PR. A follow-up slice may introduce
 * a {@code PluginContext.registerPublicPrefix(String)} API so
 * plugins self-declare their public-prefix contributions; until
 * then, the static list here is the canonical source of truth.
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
    "/shepard/doc/openapi/v2.json",
    // UH1a — the Helmholtz Unhide publish feed. Bypasses JWT auth
    // because its access model is runtime-mutable: feedPublic=true
    // ⇒ truly public; feedPublic=false ⇒ requires X-API-KEY matching
    // :UnhideConfig.harvestApiKeyHash OR an instance-admin caller.
    // The UnhideFeedRest resource performs the per-call auth check
    // (config-load + key compare) because a static registry can't
    // express a runtime-mutable predicate. When enabled=false the
    // feed returns 503 unhide.feed.disabled before any auth path
    // runs.
    "/v2/unhide/feed.jsonld"
  );

  /**
   * Public-endpoint **prefixes** — paths matched by structural
   * prefix rather than exact equality. Used for endpoint families
   * whose URL carries a runtime-supplied suffix (e.g. KIP1b's
   * {@code /v2/.well-known/kip/{pid-suffix}} resolver: the suffix
   * is the freshly-minted PID and varies per row).
   *
   * <p>Entry semantics: {@code "/v2/.well-known/kip"} matches both
   * the bare path and any descendant {@code /v2/.well-known/kip/anything}.
   * The prefix MUST end without a trailing slash; the matcher requires
   * either exact equality or the next character being a slash (so
   * {@code /v2/.well-known/kip-foo} does NOT match the
   * {@code /v2/.well-known/kip} prefix).
   *
   * <p>KIP1a/b — {@code /v2/.well-known/kip/{pid-suffix}} is public
   * by design (resolver returns KIP record metadata, not entity
   * payload — see {@code aidocs/66 §4.2}); the {@code landingPage}
   * URL it points at may still require auth. Post-KIP1g the
   * resolver itself lives in {@code shepard-plugin-kip}, but the
   * prefix stays registered here per the class-level Javadoc:
   * plugin-contributed public paths are tracked centrally so the
   * auth filter has a single source of truth.
   */
  private static final Set<String> PUBLIC_PATH_PREFIXES = Set.of("/v2/.well-known/kip");

  /**
   * Returns {@code true} when the request path matches a registered public
   * endpoint exactly (modulo {@code /shepard/api/} prefix and trailing
   * slash), or when it sits under a registered structural prefix
   * (see {@link #PUBLIC_PATH_PREFIXES}).
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
   * <p>Structural prefix matching (KIP1b) requires the next character
   * after the prefix to be {@code /} — that closes the same foot-gun
   * for prefix entries: {@code /v2/.well-known/kip-foo} does NOT match
   * the {@code /v2/.well-known/kip} prefix; only {@code /v2/.well-known/kip}
   * and {@code /v2/.well-known/kip/...} do.
   *
   * <p>Post-P4 the request URI carries the {@code /shepard/api/} prefix;
   * {@link RequestPathHelper#applicationPath} strips it so the public-path
   * registry stays in application-relative form.
   */
  public static boolean isRequestPathPublic(ContainerRequestContext requestContext) {
    String applicationPath = RequestPathHelper.applicationPath(requestContext);
    String normalised = normalise(applicationPath);
    if (PUBLIC_PATHS.contains(normalised)) return true;
    for (String prefix : PUBLIC_PATH_PREFIXES) {
      if (matchesPrefix(normalised, prefix)) return true;
    }
    return false;
  }

  /**
   * Match {@code path} against a structural prefix: exact equality
   * OR the prefix followed by a path separator. Closes the
   * {@code prefix-foo}-style foot-gun that an unconditional
   * {@code startsWith} would re-open.
   */
  static boolean matchesPrefix(String path, String prefix) {
    if (path == null || prefix == null) return false;
    if (path.equals(prefix)) return true;
    if (!path.startsWith(prefix)) return false;
    return path.charAt(prefix.length()) == '/';
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
