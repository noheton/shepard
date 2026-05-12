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

  // Post-P4 the JAX-RS-reported path leads with `shepard/api/`. The test
  // exercises full request paths and relies on RequestPathHelper to strip
  // the prefix before the public-path registry compares.

  @Test
  void exactMatchAccepts() {
    assertTrue(isPublic("shepard/api/versionz"));
  }

  @Test
  void trailingSlashAccepts() {
    // Canonical form strips trailing slash.
    assertTrue(isPublic("shepard/api/versionz/"));
  }

  @Test
  void prefixDoesNotMatch() {
    // Pre-fix the bug from `aidocs/07` H5: /versionzXXX must not match.
    assertFalse(isPublic("shepard/api/versionzanything"));
    assertFalse(isPublic("shepard/api/versionz/anything"));
    assertFalse(isPublic("shepard/api/versionz/healthz"));
  }

  @Test
  void traversalAttemptIsNormalised() {
    // /versionz/../containers/1 normalises to /containers/1 → not public.
    assertFalse(isPublic("shepard/api/versionz/../containers/1"));
  }

  @Test
  void traversalToVersionzStillMatches() {
    // /a/../versionz normalises to /versionz → exact match → public.
    // This is correct behaviour: normalisation is the point of the fix,
    // not a refusal of all `..` segments.
    assertTrue(isPublic("shepard/api/a/../versionz"));
  }

  @Test
  void unrelatedPathsRejected() {
    assertFalse(isPublic("shepard/api/collections"));
    assertFalse(isPublic("shepard/api/"));
    assertFalse(isPublic(""));
  }

  @Test
  void nonShepardPathsRejected() {
    // Future /v2/... routes don't carry the shepard/api prefix.
    // They are not public unless explicitly registered.
    assertFalse(isPublic("v2/versionz"));
    assertFalse(isPublic("v2/anything"));
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

  @Test
  void aasWellKnownIsPublic() {
    // AAS1-well-known (aidocs/52 §4a.5): reachable pre-auth so external
    // AAS-aware clients can discover capabilities.
    assertTrue(isPublic("shepard/api/v2/aas/.well-known/aas-server"));
    assertTrue(isPublic("shepard/api/v2/aas/.well-known/aas-server/"));
  }

  @Test
  void dotWellKnownSegmentIsPreservedByNormalisation() {
    // Defensive: Path.normalize() must not strip `.well-known` (which
    // shares its leading dot with the special `.` segment but is not it).
    assertEquals("/v2/aas/.well-known/aas-server", PublicEndpointRegistry.normalise("/v2/aas/.well-known/aas-server"));
  }

  @Test
  void aasWellKnownPrefixDoesNotMatch() {
    // /v2/aas/.well-known/aas-server-evil and /aas-server/subpath must not match.
    assertFalse(isPublic("shepard/api/v2/aas/.well-known/aas-server-evil"));
    assertFalse(isPublic("shepard/api/v2/aas/.well-known/aas-server/subpath"));
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
