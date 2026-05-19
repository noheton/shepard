package de.dlr.shepard.v2.publish.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.publish.PublishableKind;
import de.dlr.shepard.publish.PublishableKindRegistry;
import de.dlr.shepard.publish.entities.Publication;
import de.dlr.shepard.publish.minter.MinterException;
import de.dlr.shepard.publish.minter.MinterNotInstalledException;
import de.dlr.shepard.publish.services.PublishService;
import de.dlr.shepard.v2.publish.io.PublicationIO;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PublishRestTest {

  private PublishRest rest;
  private PublishableKindRegistry kindRegistry;
  private PublishService publishService;
  private PermissionsService permissionsService;
  private EntityIdResolver entityIdResolver;
  private SecurityContext securityContext;
  private UriInfo uriInfo;

  @BeforeEach
  void setUp() {
    rest = new PublishRest();
    kindRegistry = new PublishableKindRegistry(); // real — small + safe
    publishService = mock(PublishService.class);
    permissionsService = mock(PermissionsService.class);
    entityIdResolver = mock(EntityIdResolver.class);
    securityContext = mock(SecurityContext.class);
    uriInfo = mock(UriInfo.class);

    rest.kindRegistry = kindRegistry;
    rest.publishService = publishService;
    rest.permissionsService = permissionsService;
    rest.entityIdResolver = entityIdResolver;

    Principal alice = () -> "alice";
    when(securityContext.getUserPrincipal()).thenReturn(alice);
    when(uriInfo.getBaseUri()).thenReturn(URI.create("https://shepard.example.dlr.de:8443/shepard/api/"));
  }

  private Publication publication(String pid) {
    Publication p = new Publication();
    p.setAppId("pub-app-id");
    p.setPid(pid);
    p.setMintedAt(1_747_000_000_000L);
    p.setMinterId("local");
    p.setPublishedBy("alice");
    p.setEntityKind("data-objects");
    p.setEntityAppId("01HF-A");
    p.setVersionNumber(1);
    return p;
  }

  @Test
  void happyPathReturns200WithPublicationAndResolverUrl() {
    when(entityIdResolver.resolveLong("01HF-A")).thenReturn(42L);
    when(permissionsService.isAccessTypeAllowedForUser(eq(42L), eq(AccessType.Write), eq("alice"), anyLong())).thenReturn(true);
    Publication pub = publication("shepard:dlr.de/shepard-prod:data-objects:01HF-A:v1");
    when(publishService.publish(eq(PublishableKind.DATA_OBJECTS), eq("01HF-A"), anyString(), eq("alice"), eq(false)))
      .thenReturn(new PublishService.PublishOutcome(pub, true));

    Response r = rest.publish("data-objects", "01HF-A", false, securityContext, uriInfo);

    assertEquals(200, r.getStatus());
    PublicationIO io = (PublicationIO) r.getEntity();
    assertEquals("shepard:dlr.de/shepard-prod:data-objects:01HF-A:v1", io.pid());
    assertEquals("local", io.minterId());
    assertEquals("alice", io.publishedBy());
    assertEquals(
      "https://shepard.example.dlr.de:8443/v2/.well-known/kip/shepard:dlr.de/shepard-prod:data-objects:01HF-A:v1",
      io.resolverUrl()
    );
    assertNotNull(io.mintedAt());
    assertEquals(Integer.valueOf(1), io.versionNumber());
  }

  @Test
  void permissionDeniedReturns403() {
    when(entityIdResolver.resolveLong("01HF-A")).thenReturn(42L);
    when(permissionsService.isAccessTypeAllowedForUser(eq(anyLong()), eq(any()), eq(anyString()), anyLong())).thenReturn(false);
    Response r = rest.publish("data-objects", "01HF-A", false, securityContext, uriInfo);
    assertEquals(403, r.getStatus());
  }

  @Test
  void missingAuthReturns401() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = rest.publish("data-objects", "01HF-A", false, securityContext, uriInfo);
    assertEquals(401, r.getStatus());
  }

  @Test
  void unsupportedKindReturns404WithProblemJson() {
    Response r = rest.publish("file-references", "01HF-A", false, securityContext, uriInfo);
    assertEquals(404, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
    assertTrue(r.getEntity().toString().contains("publish.kind.unsupported"));
  }

  @Test
  void missingEntityReturns404() {
    when(entityIdResolver.resolveLong("01HF-MISSING")).thenThrow(new NotFoundException("nope"));
    Response r = rest.publish("data-objects", "01HF-MISSING", false, securityContext, uriInfo);
    assertEquals(404, r.getStatus());
  }

  @Test
  void wrongKindReturns404WithProblemJson() {
    when(entityIdResolver.resolveLong("01HF-A")).thenReturn(42L);
    when(permissionsService.isAccessTypeAllowedForUser(eq(42L), eq(AccessType.Write), eq("alice"), anyLong())).thenReturn(true);
    when(publishService.publish(any(), anyString(), anyString(), anyString(), anyBoolean()))
      .thenThrow(new NotFoundException("No DataObject entity with appId 01HF-A exists"));
    Response r = rest.publish("data-objects", "01HF-A", false, securityContext, uriInfo);
    assertEquals(404, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
    assertTrue(r.getEntity().toString().contains("publish.entity.wrong-kind"));
  }

  @Test
  void minterFailureReturns500WithProblemJson() {
    when(entityIdResolver.resolveLong("01HF-A")).thenReturn(42L);
    when(permissionsService.isAccessTypeAllowedForUser(eq(42L), eq(AccessType.Write), eq("alice"), anyLong())).thenReturn(true);
    when(publishService.publish(any(), anyString(), anyString(), anyString(), anyBoolean()))
      .thenThrow(new MinterException("ePIC returned 503"));
    Response r = rest.publish("data-objects", "01HF-A", false, securityContext, uriInfo);
    assertEquals(500, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
    assertTrue(r.getEntity().toString().contains("publish.minter.failed"));
  }

  @Test
  void minterNotInstalledReturns503WithProblemJson() {
    // KIP1h: when no minter is wired the service throws
    // MinterNotInstalledException; REST maps it to 503 RFC 7807
    // `publish.minter.not-installed` with an actionable hint.
    when(entityIdResolver.resolveLong("01HF-A")).thenReturn(42L);
    when(permissionsService.isAccessTypeAllowedForUser(eq(42L), eq(AccessType.Write), eq("alice"), anyLong())).thenReturn(true);
    when(publishService.publish(any(), anyString(), anyString(), anyString(), anyBoolean()))
      .thenThrow(new MinterNotInstalledException("shepard.publish.minter is unset or no matching plugin is installed. Install plugins/minter-local/ ..."));

    Response r = rest.publish("data-objects", "01HF-A", false, securityContext, uriInfo);

    assertEquals(503, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
    String body = r.getEntity().toString();
    assertTrue(body.contains("publish.minter.not-installed"));
    assertTrue(body.contains("plugins/minter-local/"));
  }

  @Test
  void idempotentSecondCallReturnsExistingPublication() {
    when(entityIdResolver.resolveLong("01HF-A")).thenReturn(42L);
    when(permissionsService.isAccessTypeAllowedForUser(eq(42L), eq(AccessType.Write), eq("alice"), anyLong())).thenReturn(true);
    Publication existing = publication("shepard:dlr.de/shepard-prod:data-objects:01HF-A:v1");
    when(publishService.publish(any(), anyString(), anyString(), anyString(), eq(false)))
      .thenReturn(new PublishService.PublishOutcome(existing, false));

    Response r = rest.publish("data-objects", "01HF-A", false, securityContext, uriInfo);

    assertEquals(200, r.getStatus());
    PublicationIO io = (PublicationIO) r.getEntity();
    assertEquals("shepard:dlr.de/shepard-prod:data-objects:01HF-A:v1", io.pid());
  }

  @Test
  void forceTrueIsPropagatedToService() {
    when(entityIdResolver.resolveLong("01HF-A")).thenReturn(42L);
    when(permissionsService.isAccessTypeAllowedForUser(eq(42L), eq(AccessType.Write), eq("alice"), anyLong())).thenReturn(true);
    Publication fresh = publication("shepard:dlr.de/shepard-prod:data-objects:01HF-A:v2");
    when(publishService.publish(any(), anyString(), anyString(), anyString(), eq(true)))
      .thenReturn(new PublishService.PublishOutcome(fresh, true));

    Response r = rest.publish("data-objects", "01HF-A", true, securityContext, uriInfo);
    assertEquals(200, r.getStatus());
    PublicationIO io = (PublicationIO) r.getEntity();
    assertEquals("shepard:dlr.de/shepard-prod:data-objects:01HF-A:v2", io.pid());
  }

  @Test
  void absoluteUrlSkipsDefaultPortsCanonically() {
    UriInfo http80 = mock(UriInfo.class);
    when(http80.getBaseUri()).thenReturn(URI.create("http://localhost:80/"));
    assertEquals("http://localhost/v2/x", PublishRest.absoluteUrl(http80, "/v2/x"));

    UriInfo https443 = mock(UriInfo.class);
    when(https443.getBaseUri()).thenReturn(URI.create("https://shepard.example/"));
    assertEquals("https://shepard.example/v2/x", PublishRest.absoluteUrl(https443, "/v2/x"));

    UriInfo nonStandard = mock(UriInfo.class);
    when(nonStandard.getBaseUri()).thenReturn(URI.create("https://shepard.example:8443/"));
    assertEquals("https://shepard.example:8443/v2/x", PublishRest.absoluteUrl(nonStandard, "/v2/x"));
  }

  @Test
  void absoluteUrlPrependsSlashWhenMissing() {
    UriInfo u = mock(UriInfo.class);
    when(u.getBaseUri()).thenReturn(URI.create("https://shepard.example/"));
    assertEquals("https://shepard.example/v2/x", PublishRest.absoluteUrl(u, "v2/x"));
  }

  @Test
  void collectionsKindResolvesAndPublishes() {
    // Smoke test that 'collections' kind also routes correctly through
    // the same code path as 'data-objects'.
    when(entityIdResolver.resolveLong("01HF-C")).thenReturn(99L);
    when(permissionsService.isAccessTypeAllowedForUser(eq(99L), eq(AccessType.Write), eq("alice"), anyLong())).thenReturn(true);
    Publication pub = publication("shepard:dlr.de/shepard-prod:collections:01HF-C:v1");
    pub.setEntityKind("collections");
    pub.setEntityAppId("01HF-C");
    when(publishService.publish(eq(PublishableKind.COLLECTIONS), eq("01HF-C"), anyString(), eq("alice"), eq(false)))
      .thenReturn(new PublishService.PublishOutcome(pub, true));

    Response r = rest.publish("collections", "01HF-C", false, securityContext, uriInfo);
    assertEquals(200, r.getStatus());
    PublicationIO io = (PublicationIO) r.getEntity();
    assertEquals("collections", io.entityKind());
  }

  // ---------- KIP1f: DELETE /v2/{kind}/{appId}/publish ----------

  @Test
  void retireHappyPathReturns204() {
    when(entityIdResolver.resolveLong("01HF-A")).thenReturn(42L);
    when(permissionsService.isAccessTypeAllowedForUser(eq(42L), eq(AccessType.Write), eq("alice"), anyLong())).thenReturn(true);
    when(publishService.retire(PublishableKind.DATA_OBJECTS, "01HF-A")).thenReturn(true);

    Response r = rest.retire("data-objects", "01HF-A", securityContext);
    assertEquals(204, r.getStatus());
  }

  @Test
  void retireMissingAuthReturns401() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = rest.retire("data-objects", "01HF-A", securityContext);
    assertEquals(401, r.getStatus());
    verify(publishService, never()).retire(any(), anyString());
  }

  @Test
  void retireUnsupportedKindReturns404WithProblemJson() {
    Response r = rest.retire("file-references", "01HF-A", securityContext);
    assertEquals(404, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
    assertTrue(r.getEntity().toString().contains("publish.kind.unsupported"));
  }

  @Test
  void retireMissingEntityReturns404() {
    when(entityIdResolver.resolveLong("01HF-MISSING")).thenThrow(new NotFoundException("nope"));
    Response r = rest.retire("data-objects", "01HF-MISSING", securityContext);
    assertEquals(404, r.getStatus());
  }

  @Test
  void retirePermissionDeniedReturns403() {
    when(entityIdResolver.resolveLong("01HF-A")).thenReturn(42L);
    when(permissionsService.isAccessTypeAllowedForUser(eq(anyLong()), eq(any()), eq(anyString()), anyLong())).thenReturn(false);
    Response r = rest.retire("data-objects", "01HF-A", securityContext);
    assertEquals(403, r.getStatus());
  }

  @Test
  void retireWhenNoPublicationReturns404WithProblemJson() {
    // Entity exists + caller has Write, but entity has no :Publication.
    when(entityIdResolver.resolveLong("01HF-A")).thenReturn(42L);
    when(permissionsService.isAccessTypeAllowedForUser(eq(42L), eq(AccessType.Write), eq("alice"), anyLong())).thenReturn(true);
    when(publishService.retire(PublishableKind.DATA_OBJECTS, "01HF-A")).thenReturn(false);

    Response r = rest.retire("data-objects", "01HF-A", securityContext);
    assertEquals(404, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
    assertTrue(r.getEntity().toString().contains("publish.publication.not-found"));
  }

  @Test
  void retireWrongKindReturns404WithProblemJson() {
    // Service throws NotFoundException (entity exists but not under this kind).
    when(entityIdResolver.resolveLong("01HF-A")).thenReturn(42L);
    when(permissionsService.isAccessTypeAllowedForUser(eq(42L), eq(AccessType.Write), eq("alice"), anyLong())).thenReturn(true);
    when(publishService.retire(any(), anyString()))
      .thenThrow(new NotFoundException("No DataObject entity with appId 01HF-A"));

    Response r = rest.retire("data-objects", "01HF-A", securityContext);
    assertEquals(404, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
    assertTrue(r.getEntity().toString().contains("publish.entity.wrong-kind"));
  }

  @Test
  void retireDoubleRetireIsIdempotent() {
    // Second retire call on an already-retired row still returns 204
    // (the service / DAO are idempotent).
    when(entityIdResolver.resolveLong("01HF-A")).thenReturn(42L);
    when(permissionsService.isAccessTypeAllowedForUser(eq(42L), eq(AccessType.Write), eq("alice"), anyLong())).thenReturn(true);
    when(publishService.retire(PublishableKind.DATA_OBJECTS, "01HF-A")).thenReturn(true);

    assertEquals(204, rest.retire("data-objects", "01HF-A", securityContext).getStatus());
    assertEquals(204, rest.retire("data-objects", "01HF-A", securityContext).getStatus());
  }
}
