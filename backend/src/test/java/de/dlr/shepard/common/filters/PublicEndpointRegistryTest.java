package de.dlr.shepard.common.filters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PublicEndpointRegistryTest {

  @AfterEach
  void resetPluginPaths() {
    // PM1g — clear plugin-contributed registrations so each test runs
    // against a clean slate; the mutable static sets would otherwise
    // persist across test methods within the same JVM.
    PublicEndpointRegistry.resetPluginRegistrations();
  }

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

  // KIP1b / PM1g — /v2/.well-known/kip/{pid-suffix} is a prefix-matched public
  // endpoint declared by the KIP plugin manifest (PM1g).

  @Test
  void kipResolverBarePrefixIsPublic() {
    // Register the KIP prefix via the plugin mechanism (PM1g).
    PublicEndpointRegistry.registerPluginPathPrefix("/v2/.well-known/kip");
    // The prefix itself (no PID suffix) is also public — JAX-RS would
    // return 404 anyway, but the auth filter should not reject it.
    assertTrue(isPublic("shepard/api/v2/.well-known/kip"));
    assertTrue(isPublic("shepard/api/v2/.well-known/kip/"));
  }

  @Test
  void kipResolverWithPidSuffixIsPublic() {
    // Register the KIP prefix via the plugin mechanism (PM1g).
    PublicEndpointRegistry.registerPluginPathPrefix("/v2/.well-known/kip");
    // Mock-shaped PIDs carry colons; Handle/DOI shapes carry slashes —
    // both must pass through the filter without auth.
    assertTrue(isPublic("shepard/api/v2/.well-known/kip/mock:shepard:data-objects:01HF:1747000000000"));
    assertTrue(isPublic("shepard/api/v2/.well-known/kip/21.T11148/abc-def"));
  }

  @Test
  void kipResolverPrefixFootGunGuarded() {
    // Register the KIP prefix via the plugin mechanism (PM1g).
    PublicEndpointRegistry.registerPluginPathPrefix("/v2/.well-known/kip");
    // /v2/.well-known/kip-foo must NOT match the /v2/.well-known/kip prefix.
    assertFalse(isPublic("shepard/api/v2/.well-known/kip-foo"));
    assertFalse(isPublic("shepard/api/v2/.well-known/kip-evil/abc"));
  }

  @Test
  void kipResolverPrefixNotPublicWithoutRegistration() {
    // PM1g: without the plugin registering the prefix, the path is not public.
    assertFalse(isPublic("shepard/api/v2/.well-known/kip"));
    assertFalse(isPublic("shepard/api/v2/.well-known/kip/abc"));
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
    // UH1a / PM1g — the Helmholtz Unhide harvest feed is JWT-bypassed;
    // the path is now declared by the Unhide plugin manifest (PM1g).
    PublicEndpointRegistry.registerPluginPath("/v2/unhide/feed.jsonld");
    assertTrue(isPublic("shepard/api/v2/unhide/feed.jsonld"));
    assertTrue(isPublic("shepard/api/v2/unhide/feed.jsonld/"));
  }

  @Test
  void unhideAdminEndpointsAreNotPublic() {
    // Register the feed path (PM1g) and verify admin endpoints stay protected.
    PublicEndpointRegistry.registerPluginPath("/v2/unhide/feed.jsonld");
    // The /v2/admin/unhide/... surface stays JWT-protected — it's
    // @RolesAllowed(instance-admin), the feed isn't.
    assertFalse(isPublic("shepard/api/v2/admin/unhide/config"));
    assertFalse(isPublic("shepard/api/v2/admin/unhide/harvest-key/rotate"));
    assertFalse(isPublic("shepard/api/v2/unhide/feed.jsonld/evil"));
    assertFalse(isPublic("shepard/api/v2/unhide/feed.jsonld.evil"));
  }

  @Test
  void unhideFeedNotPublicWithoutRegistration() {
    // PM1g: without the plugin registering the path, the feed is not public.
    assertFalse(isPublic("shepard/api/v2/unhide/feed.jsonld"));
  }

  // MCP-1 — /v2/instance/capabilities is public so the sidecar can poll
  // its own enabled state without a JWT credential.

  @Test
  void instanceCapabilitiesIsPublic() {
    assertTrue(isPublic("shepard/api/v2/instance/capabilities"));
    assertTrue(isPublic("shepard/api/v2/instance/capabilities/"));
  }

  @Test
  void instanceCapabilitiesSubpathIsNotPublic() {
    // Exact-match only — subpaths and typo neighbours are not public.
    assertFalse(isPublic("shepard/api/v2/instance/capabilities/evil"));
    assertFalse(isPublic("shepard/api/v2/instance/capabilitiesx"));
  }

  // APISIMP-INSTANCE-REGISTRY-BESPOKE — /v2/instance/registry is the
  // public read surface for the instance registry (moved from the
  // misleading /v2/admin/instances path).

  @Test
  void instanceRegistryIsPublic() {
    assertTrue(isPublic("shepard/api/v2/instance/registry"));
    assertTrue(isPublic("shepard/api/v2/instance/registry/"));
  }

  @Test
  void instanceRegistrySubpathIsNotPublic() {
    assertFalse(isPublic("shepard/api/v2/instance/registry/evil"));
    assertFalse(isPublic("shepard/api/v2/instance/registryx"));
  }

  @Test
  void oldAdminInstancesPathIsNoLongerPublic() {
    // APISIMP-INSTANCE-REGISTRY-BESPOKE: /v2/admin/instances GET moved to
    // /v2/instance/registry. The admin path now carries only PATCH
    // (role-protected) — it must NOT be public any more.
    assertFalse(isPublic("shepard/api/v2/admin/instances"));
    assertFalse(isPublic("shepard/api/v2/admin/instances/"));
  }

  // BACKEND-VERSIONZ-PROBE — /healthz prefix covers the smallrye-health family

  @Test
  void healthzBarePathIsPublic() {
    // quarkus.smallrye-health.root-path=/shepard/api/healthz →
    // application path /healthz. Must bypass JWTFilter without auth.
    assertTrue(isPublic("shepard/api/healthz"));
    assertTrue(isPublic("shepard/api/healthz/"));
  }

  @Test
  void healthzLivenessIsPublic() {
    assertTrue(isPublic("shepard/api/healthz/live"));
  }

  @Test
  void healthzReadinessIsPublic() {
    assertTrue(isPublic("shepard/api/healthz/ready"));
  }

  @Test
  void healthzGroupPathIsPublic() {
    assertTrue(isPublic("shepard/api/healthz/group/neo4j"));
  }

  @Test
  void healthzPrefixFootGunGuarded() {
    // /healthz-evil must NOT match the /healthz prefix.
    assertFalse(isPublic("shepard/api/healthz-evil"));
    assertFalse(isPublic("shepard/api/healthzevil"));
  }

  // PM1g — plugin-path registration API tests

  @Test
  void registerPluginPath_allowsExactMatch() {
    PublicEndpointRegistry.registerPluginPath("/v2/my-plugin/feed");
    assertTrue(isPublic("shepard/api/v2/my-plugin/feed"));
    assertTrue(isPublic("shepard/api/v2/my-plugin/feed/"));
  }

  @Test
  void registerPluginPath_doesNotAllowSubpaths() {
    PublicEndpointRegistry.registerPluginPath("/v2/my-plugin/feed");
    assertFalse(isPublic("shepard/api/v2/my-plugin/feed/secret"));
  }

  @Test
  void registerPluginPathPrefix_allowsExactAndChildren() {
    PublicEndpointRegistry.registerPluginPathPrefix("/v2/my-plugin/resolver");
    assertTrue(isPublic("shepard/api/v2/my-plugin/resolver"));
    assertTrue(isPublic("shepard/api/v2/my-plugin/resolver/"));
    assertTrue(isPublic("shepard/api/v2/my-plugin/resolver/abc"));
  }

  @Test
  void registerPluginPathPrefix_footGunGuarded() {
    PublicEndpointRegistry.registerPluginPathPrefix("/v2/my-plugin/resolver");
    assertFalse(isPublic("shepard/api/v2/my-plugin/resolver-evil"));
    assertFalse(isPublic("shepard/api/v2/my-plugin/resolverfoo"));
  }

  @Test
  void resetPluginRegistrations_clearsRegisteredPaths() {
    PublicEndpointRegistry.registerPluginPath("/v2/my-plugin/feed");
    PublicEndpointRegistry.registerPluginPathPrefix("/v2/my-plugin/resolver");
    assertTrue(isPublic("shepard/api/v2/my-plugin/feed"));
    assertTrue(isPublic("shepard/api/v2/my-plugin/resolver"));
    PublicEndpointRegistry.resetPluginRegistrations();
    assertFalse(isPublic("shepard/api/v2/my-plugin/feed"));
    assertFalse(isPublic("shepard/api/v2/my-plugin/resolver"));
  }

  @Test
  void registerPluginPath_ignoresNullAndBlank() {
    // Defensive: null and blank values must not cause NPE or pollute the set.
    PublicEndpointRegistry.registerPluginPath(null);
    PublicEndpointRegistry.registerPluginPath("");
    PublicEndpointRegistry.registerPluginPath("   ");
    // Core paths still work; no exception thrown.
    assertTrue(isPublic("shepard/api/versionz"));
  }

  @Test
  void registerPluginPathPrefix_ignoresNullAndBlank() {
    PublicEndpointRegistry.registerPluginPathPrefix(null);
    PublicEndpointRegistry.registerPluginPathPrefix("");
    PublicEndpointRegistry.registerPluginPathPrefix("   ");
    assertTrue(isPublic("shepard/api/versionz"));
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
