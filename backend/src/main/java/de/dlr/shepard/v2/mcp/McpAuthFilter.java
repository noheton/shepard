package de.dlr.shepard.v2.mcp;

import de.dlr.shepard.auth.security.ApiKeyAuthService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.security.JWTPrincipal;
import de.dlr.shepard.auth.security.JwtTokenAuthService;
import de.dlr.shepard.common.util.Constants;
import io.jsonwebtoken.ExpiredJwtException;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.logging.Log;
import io.quarkus.vertx.http.runtime.filters.Filters;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

/**
 * Bridge that authenticates Vert.x routes under {@code /v2/mcp/*}.
 *
 * <p>Accepts <strong>two</strong> credential shapes — picking whichever
 * matches the MCP client's UX:
 *
 * <ul>
 *   <li>{@code Authorization: Bearer <oidc-jwt>} — interactive flows
 *       (web sign-in, IDE assistants that handle OAuth).</li>
 *   <li>{@code Authorization: Bearer <shepard-api-key-jws>}
 *       (or {@code X-API-KEY: <jws>}) — static-credential flows
 *       (Claude Desktop, claude.ai connectors, scripts). This is the
 *       expected pattern for most MCP clients; they expose a single
 *       "API key" field that they put after {@code Bearer }.</li>
 * </ul>
 *
 * <p>For a bare {@code Bearer} value we try OIDC validation first and
 * fall through to API-key validation on failure. The OIDC and API-key
 * paths use disjoint signing keys (Keycloak's RSA pubkey vs shepard's
 * own RSA keypair from {@link de.dlr.shepard.common.util.PKIHelper}),
 * so the order doesn't matter for correctness — only for log noise on
 * misconfigured clients.
 *
 * <p>On success the resolved {@link JWTPrincipal} is stashed on the
 * routing context under {@link #PRINCIPAL_CONTEXT_KEY} (always works,
 * regardless of CDI scope state) and also set on
 * {@link AuthenticationContext} for the JAX-RS-shaped CDI scope of the
 * incoming HTTP request. {@link McpContextBridge} re-copies the stash
 * onto the fresh per-tool-invocation request scope.
 *
 * <p>On failure: 401 with a JSON {@code AuthenticationException} body.
 * Expired API keys get a distinct {@code WWW-Authenticate: ApiKey
 * error="expired"} header so a smart client can prompt for re-issue
 * rather than retry-with-the-same-token.
 */
@ApplicationScoped
public class McpAuthFilter {

  /** Vert.x route key under which the validated principal is stashed. */
  public static final String PRINCIPAL_CONTEXT_KEY = "shepard.principal";

  /** Path prefix that triggers this filter. */
  private static final String MCP_PREFIX = "/v2/mcp";

  /**
   * Priority for the Vert.x filter. Higher numbers run earlier per the
   * {@code Filters} contract; {@code 100} keeps us well ahead of the
   * MCP route handlers.
   */
  private static final int FILTER_PRIORITY = 100;

  @Inject
  JwtTokenAuthService jwtTokenAuthService;

  @Inject
  ApiKeyAuthService apiKeyAuthService;

  /**
   * {@code Instance<>} indirection — {@link AuthenticationContext} is
   * {@code @RequestScoped}; resolving it eagerly from this
   * {@code @ApplicationScoped} bean at startup would fail. The
   * {@code Instance<>} lets us defer the lookup to per-request time
   * where the request scope is (expected to be) active.
   */
  @Inject
  Instance<AuthenticationContext> authenticationContext;

  void registerFilter(@Observes Filters filters) {
    filters.register(this::handle, FILTER_PRIORITY);
    Log.infof("MCP auth filter registered for prefix %s (Bearer-OIDC | Bearer-ApiKey | X-API-KEY)", MCP_PREFIX);
  }

  void handle(RoutingContext rc) {
    String path = rc.normalizedPath();
    if (!matchesPrefix(path, MCP_PREFIX)) {
      rc.next();
      return;
    }

    if (HttpMethod.OPTIONS.equals(rc.request().method())) {
      rc.next();
      return;
    }

    String authHeader = rc.request().getHeader("Authorization");
    String apiKeyHeader = rc.request().getHeader(Constants.API_KEY_HEADER);

    // Vert.x route filters run BEFORE Quarkus activates the CDI request
    // scope for the request, so any @RequestScoped dependency we transit
    // (PKIHelper, ApiKeyService, RoleDAO, AuthenticationContext) would
    // throw ContextNotActiveException. Activate the scope ourselves for
    // the duration of the auth validation; rc.next() will hand the same
    // scope to the downstream MCP route handler.
    ManagedContext requestCtx = managedRequestContext();
    boolean activatedHere = false;
    if (requestCtx != null && !requestCtx.isActive()) {
      requestCtx.activate();
      activatedHere = true;
    }

    JWTPrincipal principal = null;
    try {
      if (authHeader != null && authHeader.startsWith("Bearer ")) {
        principal = resolveBearer(authHeader);
      } else if (apiKeyHeader != null && !apiKeyHeader.isBlank()) {
        principal = apiKeyAuthService.parseApiKey(apiKeyHeader);
      } else {
        Log.warnf("MCP request without credential on %s", path);
        abortUnauthorized(rc, "Missing Authorization or X-API-KEY header", null);
        if (activatedHere && requestCtx != null) requestCtx.terminate();
        return;
      }
    } catch (ExpiredJwtException ex) {
      Log.warnf("MCP API-key expired: %s", ex.getMessage());
      abortUnauthorized(rc, "API key expired", "ApiKey error=\"expired\", error_description=\"API key expired\"");
      if (activatedHere && requestCtx != null) requestCtx.terminate();
      return;
    } catch (RuntimeException ex) {
      Log.warnf("MCP credential validation threw: %s", ex.getMessage());
      abortUnauthorized(rc, "Invalid token", null);
      if (activatedHere && requestCtx != null) requestCtx.terminate();
      return;
    }

    if (principal == null) {
      abortUnauthorized(rc, "Invalid Authentication", null);
      if (activatedHere && requestCtx != null) requestCtx.terminate();
      return;
    }

    rc.put(PRINCIPAL_CONTEXT_KEY, principal);
    try {
      authenticationContext.get().setPrincipal(principal);
    } catch (RuntimeException ex) {
      Log.debugf(
        "Could not set AuthenticationContext for MCP request: %s",
        ex.getMessage()
      );
    }

    // Do NOT terminate the request context here even if we activated it —
    // the downstream MCP route handler reads from AuthenticationContext
    // and needs the same scope still active. Quarkus's own request-end
    // lifecycle (the Vert.x close hook on the response) will terminate it.
    rc.next();
  }

  /**
   * Try OIDC first, fall through to API-key. The two validators use
   * disjoint signing keys so each only accepts its own tokens — there's
   * no risk of cross-binding.
   */
  private JWTPrincipal resolveBearer(String authHeader) {
    JWTPrincipal p = jwtTokenAuthService.parseBearerToken(authHeader);
    if (p != null) return p;

    String token = authHeader.substring("Bearer ".length()).trim();
    if (ApiKeyAuthService.looksLikeJws(token)) {
      return apiKeyAuthService.parseApiKey(token);
    }
    return null;
  }

  /**
   * Seam for unit tests: produced from {@link Arc#container()} in
   * production, mocked away in tests where the Arc runtime isn't up.
   * Returns {@code null} if no container is available — the filter
   * then runs without scope activation, which is the right behaviour
   * for the test environment (mocks don't use CDI).
   */
  ManagedContext managedRequestContext() {
    var container = Arc.container();
    return container == null ? null : container.requestContext();
  }

  private static boolean matchesPrefix(String path, String prefix) {
    if (path == null || prefix == null) return false;
    if (path.equals(prefix)) return true;
    if (!path.startsWith(prefix)) return false;
    return path.charAt(prefix.length()) == '/';
  }

  private static void abortUnauthorized(RoutingContext rc, String message, String wwwAuthenticate) {
    String body = "{\"status\":401,\"type\":\"AuthenticationException\",\"message\":\"" +
      escapeJson(message) +
      "\"}";
    var resp = rc.response()
      .setStatusCode(401)
      .putHeader("Content-Type", "application/json");
    if (wwwAuthenticate != null) {
      resp.putHeader("WWW-Authenticate", wwwAuthenticate);
    }
    resp.end(body);
  }

  private static String escapeJson(String s) {
    return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
