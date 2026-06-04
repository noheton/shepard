package de.dlr.shepard.common.filters;

import de.dlr.shepard.plugin.RestNamespaceRegistry;
import io.quarkus.logging.Log;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.Provider;
import java.util.Optional;

/**
 * V2CONV-A5 — returns {@code 404 Not Found} for any request under a {@code /v2/<prefix>}
 * namespace whose owning plugin / feature is currently <em>disabled</em>.
 *
 * <p>Runs at {@code AUTHENTICATION - 100} so it fires <em>before</em> {@link JWTFilter}:
 * a disabled namespace looks exactly like a route that does not exist, regardless of
 * whether the caller is authenticated. (A 401 here would leak that the endpoint exists
 * but is gated; a 404 is the honest "this surface is not present" answer that matches
 * the OpenAPI strip — the endpoint is absent from the served v2 spec too.)
 *
 * <p>The allowlist is entirely data-driven: this filter never names a prefix. It asks
 * {@link RestNamespaceRegistry#disabledPrefixFor(String)} which iterates the registered
 * {@link de.dlr.shepard.plugin.RestNamespaceContributor}s and checks each owner's
 * runtime enabled-state. Everything not under a disabled owned prefix passes untouched —
 * the frozen {@code /shepard/api/*} surface and all enabled {@code /v2/*} endpoints are
 * never affected.
 *
 * <p><strong>Fail-soft.</strong> If the registry can't resolve enabled-state (an injected
 * supplier throws), the registry itself defaults that namespace to ENABLED — so a registry
 * hiccup never 404s a working endpoint.
 *
 * @see RestNamespaceRegistry
 * @see de.dlr.shepard.common.openapi.DisabledNamespaceOasFilter
 */
@Provider
@Priority(Priorities.AUTHENTICATION - 100)
@ApplicationScoped
public class DisabledNamespaceRequestFilter implements ContainerRequestFilter {

  @Inject
  RestNamespaceRegistry namespaceRegistry;

  @Override
  public void filter(ContainerRequestContext requestContext) {
    final String appPath = RequestPathHelper.applicationPath(requestContext);

    Optional<String> disabledPrefix;
    try {
      disabledPrefix = namespaceRegistry.disabledPrefixFor(appPath);
    } catch (RuntimeException ex) {
      // Defence in depth — the registry is already fail-soft per-entry, but a wholesale
      // failure here must not 500 a request. Allow through.
      Log.warnf(ex, "V2CONV-A5: namespace gate lookup failed for '%s' — allowing request through", appPath);
      return;
    }

    if (disabledPrefix.isEmpty()) {
      return;
    }

    Log.debugf(
      "V2CONV-A5: 404 for '%s' — owning namespace '%s' is disabled",
      appPath,
      disabledPrefix.get()
    );
    requestContext.abortWith(
      Response
        .status(Status.NOT_FOUND)
        .type(MediaType.APPLICATION_JSON)
        .entity("{\"message\":\"Not Found\"}")
        .build()
    );
  }
}
