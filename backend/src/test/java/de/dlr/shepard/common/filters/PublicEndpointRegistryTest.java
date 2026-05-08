package de.dlr.shepard.common.filters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;

class PublicEndpointRegistryTest {

  @Test
  void exactMatchAccepts() {
    assertTrue(isPublic("/versionz"));
  }

  @Test
  void trailingSlashAccepts() {
    // Canonical form strips trailing slash.
    assertTrue(isPublic("/versionz/"));
  }

  @Test
  void prefixDoesNotMatch() {
    // Pre-fix the bug from `aidocs/07` H5: /versionzXXX must not match.
    assertFalse(isPublic("/versionzanything"));
    assertFalse(isPublic("/versionz/anything"));
    assertFalse(isPublic("/versionz/healthz"));
  }

  @Test
  void traversalAttemptIsNormalised() {
    // /versionz/../containers/1 normalises to /containers/1 → not public.
    assertFalse(isPublic("/versionz/../containers/1"));
  }

  @Test
  void traversalToVersionzStillMatches() {
    // /a/../versionz normalises to /versionz → exact match → public.
    // This is correct behaviour: normalisation is the point of the fix,
    // not a refusal of all `..` segments.
    assertTrue(isPublic("/a/../versionz"));
  }

  @Test
  void unrelatedPathsRejected() {
    assertFalse(isPublic("/collections"));
    assertFalse(isPublic("/"));
    assertFalse(isPublic(""));
  }

  @Test
  void normaliseStripsTrailingSlashOnRoot() {
    // Defensive: root path "/" should stay "/" — never become empty string.
    assertEquals("/", PublicEndpointRegistry.normalise("/"));
  }

  @Test
  void normalisePrefixesLeadingSlash() {
    // JAX-RS UriInfo.getPath() returns paths without leading slash by default.
    assertEquals("/versionz", PublicEndpointRegistry.normalise("versionz"));
  }

  // helper

  private static boolean isPublic(String path) {
    ContainerRequestContext ctx = mock(ContainerRequestContext.class);
    UriInfo uri = mock(UriInfo.class);
    when(ctx.getUriInfo()).thenReturn(uri);
    when(uri.getPath()).thenReturn(path);
    return PublicEndpointRegistry.isRequestPathPublic(ctx);
  }
}
