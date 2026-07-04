package de.dlr.shepard.common.filters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.plugin.RestNamespaceRegistry;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * V2CONV-A5 — unit tests for {@link DisabledNamespaceRequestFilter}.
 *
 * <p>Mocks the {@link RestNamespaceRegistry} and the JAX-RS request context so the
 * filter's gate decision (404 vs. pass-through) is asserted in isolation.
 */
class DisabledNamespaceRequestFilterTest {

  private RestNamespaceRegistry registry;
  private DisabledNamespaceRequestFilter filter;
  private ContainerRequestContext context;
  private UriInfo uriInfo;

  @BeforeEach
  void setUp() {
    registry = Mockito.mock(RestNamespaceRegistry.class);
    filter = new DisabledNamespaceRequestFilter();
    filter.namespaceRegistry = registry;
    context = Mockito.mock(ContainerRequestContext.class);
    uriInfo = Mockito.mock(UriInfo.class);
    when(context.getUriInfo()).thenReturn(uriInfo);
  }

  private void requestPath(String appRelative) {
    // RequestPathHelper.applicationPath strips the /shepard/api prefix; feed the
    // already-application-relative form (no prefix) so getPath() returns it verbatim.
    when(uriInfo.getPath()).thenReturn(appRelative);
  }

  @Test
  void disabledNamespace_returns404() {
    requestPath("/v2/aas/shells");
    when(registry.disabledPrefixFor("/v2/aas/shells")).thenReturn(Optional.of("/v2/aas"));

    filter.filter(context);

    ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
    verify(context).abortWith(captor.capture());
    assertEquals(404, captor.getValue().getStatus());
  }

  @Test
  void enabledNamespace_passesThrough() {
    requestPath("/v2/aas/shells");
    when(registry.disabledPrefixFor("/v2/aas/shells")).thenReturn(Optional.empty());

    filter.filter(context);

    verify(context, never()).abortWith(any());
  }

  @Test
  void nonNamespacedPath_passesThrough() {
    requestPath("/v2/collections");
    when(registry.disabledPrefixFor(anyString())).thenReturn(Optional.empty());

    filter.filter(context);

    verify(context, never()).abortWith(any());
  }

  @Test
  void frozenV1Path_passesThrough() {
    requestPath("/collections");
    when(registry.disabledPrefixFor(anyString())).thenReturn(Optional.empty());

    filter.filter(context);

    verify(context, never()).abortWith(any());
  }

  @Test
  void registryThrows_failsSoftAndAllows() {
    requestPath("/v2/aas/shells");
    when(registry.disabledPrefixFor(anyString())).thenThrow(new RuntimeException("registry down"));

    filter.filter(context);

    // Fail-soft: a wholesale lookup failure must not 500 or 404 — the request passes.
    verify(context, never()).abortWith(any());
  }
}
