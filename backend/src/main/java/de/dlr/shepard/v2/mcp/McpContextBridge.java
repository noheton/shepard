package de.dlr.shepard.v2.mcp;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.security.JWTPrincipal;
import io.quarkus.logging.Log;
import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

/**
 * Copies the {@link JWTPrincipal} validated by {@link McpAuthFilter}
 * into the per-tool-invocation {@link AuthenticationContext} so the
 * service layer's {@code authenticationContext.getCurrentUserName()}
 * resolves to the caller.
 *
 * <p>Why this is necessary: the Quarkus MCP server activates a fresh
 * CDI request scope for every MCP tool invocation (it installs its own
 * internal {@code ContextSupport$2} that calls
 * {@code CurrentVertxRequest.setCurrent(ctx)} but does NOT populate
 * any custom request-scoped beans). That fresh
 * {@link AuthenticationContext} starts with {@code principal == null}.
 * If the service layer calls {@code getCurrentUserName()} it would
 * crash with NPE on the first {@code username.equals(...)} on the
 * permission path.
 *
 * <p>By calling {@link #bind()} at the top of every MCP tool method,
 * we copy the principal from the routing-context stash (set by
 * {@link McpAuthFilter} on the HTTP request) into the per-invocation
 * {@link AuthenticationContext}. The Vert.x routing context is
 * accessible via the thread-local {@link CurrentVertxRequest} because
 * the MCP framework already wires that for us.
 *
 * <p>If the routing context has no principal (e.g. someone reused
 * this bean outside the MCP route), the binder logs at DEBUG and
 * leaves the request scope alone — the call would then 401 in the
 * normal service-layer path.
 */
@ApplicationScoped
public class McpContextBridge {

  @Inject
  Instance<CurrentVertxRequest> currentVertxRequest;

  @Inject
  AuthenticationContext authenticationContext;

  /**
   * Copy the routing-context principal onto the request-scoped
   * {@link AuthenticationContext}. Idempotent — safe to call multiple
   * times within one tool invocation.
   */
  public void bind() {
    JWTPrincipal principal = currentPrincipal();
    if (principal == null) {
      Log.debug("MCP tool: no principal in routing context — service layer will see null username");
      return;
    }
    try {
      authenticationContext.setPrincipal(principal);
    } catch (RuntimeException ex) {
      Log.warnf("Could not propagate MCP principal to AuthenticationContext: %s", ex.getMessage());
    }
  }

  private JWTPrincipal currentPrincipal() {
    try {
      CurrentVertxRequest cvr = currentVertxRequest.get();
      RoutingContext rc = cvr == null ? null : cvr.getCurrent();
      if (rc == null) return null;
      Object stash = rc.get(McpAuthFilter.PRINCIPAL_CONTEXT_KEY);
      return stash instanceof JWTPrincipal p ? p : null;
    } catch (RuntimeException ex) {
      Log.debugf("Could not read MCP principal from routing context: %s", ex.getMessage());
      return null;
    }
  }
}
