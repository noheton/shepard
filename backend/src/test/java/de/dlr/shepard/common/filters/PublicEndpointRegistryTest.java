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

  // KIP1b — /v2/.well-known/kip/{pid-suffix} is a prefix-matched public
  // endpoint (the suffix is the runtime-minted PID).

  @Test
  void kipResolverBarePrefixIsPublic() {
    // The prefix itself (no PID suffix) is also public — JAX-RS would
    // return 404 anyway, but the auth filter should not reject it.
    assertTrue(isPublic("shepard/api/v2/.well-known/kip"));
    assertTrue(isPublic("shepard/api/v2/.well-known/kip/"));
  }

  @Test
  void kipResolverWithPidSuffixIsPublic() {
    // Mock-shaped PIDs carry colons; Handle/DOI shapes carry slashes —
    // both must pass through the filter without auth.
    assertTrue(isPublic("shepard/api/v2/.well-known/kip/mock:shepard:data-objects:01HF:1747000000000"));
    assertTrue(isPublic("shepard/api/v2/.well-known/kip/21.T11148/abc-def"));
  }

  @Test
  void kipResolverPrefixFootGunGuarded() {
    // /v2/.well-known/kip-foo must NOT match the /v2/.well-known/kip prefix.
    assertFalse(isPublic("shepard/api/v2/.well-known/kip-foo"));
    assertFalse(isPublic("shepard/api/v2/.well-known/kip-evil/abc"));
  }

  @Test
  void matchesPrefixHelperExactAndChildOnly() {
    // Exact equality matches.
    assertTrue(PublicEndpointRegistry.matchesPrefix("/v2/.well-known/kip", "/v2/.well-known/kip"));
    // Child path matches.
    assertTrue(PublicEndpointRegistry.matchesPrefix("/v2/.well-known/kip/abc", "/v2/.well-known/kip"));
    // Non-/ continuation does not match.
    assertFalse(PublicEndpointRegistry.matchesPrefix("/v2/.well-known/kip-foo", "/v2/.well-known/kip"));
    // Nulls return false (defensive).
    assertFalse(PublicEndpointRegistry.matchesPrefix(null, "/v2/.well-known/kip"));
    assertFalse(PublicEndpointRegistry.matchesPrefix("/v2/.well-known/kip", null));
  }

  @Test
  void unhideFeedIsPublic() {
    // UH1a — the Helmholtz Unhide harvest feed is JWT-bypassed; the
    // runtime-mutable access predicate (enabled / feedPublic / X-API-KEY
    // matching :UnhideConfig.harvestApiKeyHash) is enforced inside the
    // resource because a static registry can't express it.
    assertTrue(isPublic("shepard/api/v2/unhide/feed.jsonld"));
    assertTrue(isPublic("shepard/api/v2/unhide/feed.jsonld/"));
  }

  @Test
  void unhideAdminEndpointsAreNotPublic() {
    // The /v2/admin/unhide/... surface stays JWT-protected — it's
    // @RolesAllowed(instance-admin), the feed isn't.
    assertFalse(isPublic("shepard/api/v2/admin/unhide/config"));
    assertFalse(isPublic("shepard/api/v2/admin/unhide/harvest-key/rotate"));
    assertFalse(isPublic("shepard/api/v2/unhide/feed.jsonld/evil"));
    assertFalse(isPublic("shepard/api/v2/unhide/feed.jsonld.evil"));
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
