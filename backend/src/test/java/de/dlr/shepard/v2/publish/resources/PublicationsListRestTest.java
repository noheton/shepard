package de.dlr.shepard.v2.publish.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.publish.PublishableKindRegistry;
import de.dlr.shepard.publish.daos.PublicationDAO;
import de.dlr.shepard.publish.entities.Publication;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import de.dlr.shepard.v2.publish.io.PublicationIO;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * KIP1k unit tests for {@link PublicationsListRest}.
 *
 * <p>Pattern mirrors {@link PublishRestTest}: real
 * {@code PublishableKindRegistry} (small + safe), all other
 * collaborators mocked.
 */
class PublicationsListRestTest {

  private PublicationsListRest rest;
  private PublishableKindRegistry kindRegistry;
  private PublicationDAO publicationDAO;
  private PermissionsService permissionsService;
  private EntityIdResolver entityIdResolver;
  private SecurityContext securityContext;
  private UriInfo uriInfo;

  @BeforeEach
  void setUp() {
    rest = new PublicationsListRest();
    kindRegistry = new PublishableKindRegistry();
    publicationDAO = mock(PublicationDAO.class);
    permissionsService = mock(PermissionsService.class);
    entityIdResolver = mock(EntityIdResolver.class);
    securityContext = mock(SecurityContext.class);
    uriInfo = mock(UriInfo.class);

    rest.kindRegistry = kindRegistry;
    rest.publicationDAO = publicationDAO;
    rest.permissionsService = permissionsService;
    rest.entityIdResolver = entityIdResolver;

    Principal alice = () -> "alice";
    when(securityContext.getUserPrincipal()).thenReturn(alice);
    when(uriInfo.getBaseUri()).thenReturn(URI.create("https://shepard.example.dlr.de:8443/shepard/api/"));
  }

  private Publication publication(String pid, long mintedAt) {
    Publication p = new Publication();
    p.setAppId("pub-" + pid.hashCode());
    p.setPid(pid);
    p.setMintedAt(mintedAt);
    p.setMinterId("local");
    p.setPublishedBy("alice");
    p.setEntityKind("data-objects");
    p.setEntityAppId("01HF-A");
    p.setVersionNumber(1);
    return p;
  }

  /** Stub count + bounded DAO overload used by PublicationsListRest (default page=0, pageSize=50). */
  private void stubBoundedDao(String appId, List<Publication> pubs) {
    when(publicationDAO.countByEntityAppId(eq(appId))).thenReturn((long) pubs.size());
    when(publicationDAO.findByEntityAppId(eq(appId), eq(0), eq(50))).thenReturn(pubs);
  }

  @Test
  void happyPathReturns200WithListAndResolverUrls() {
    when(entityIdResolver.resolveLong("01HF-A")).thenReturn(42L);
    when(permissionsService.isAccessTypeAllowedForUser(eq(42L), eq(AccessType.Read), eq("alice")))
      .thenReturn(true);
    Publication pub = publication("shepard:dlr.de/shepard-prod:data-objects:01HF-A:v1", 1_747_000_000_000L);
    stubBoundedDao("01HF-A", List.of(pub));

    Response r = rest.list("data-objects", "01HF-A", 0, 50, securityContext, uriInfo);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<PublicationIO> body = (PagedResponseIO<PublicationIO>) r.getEntity();
    assertEquals(1, body.total());
    assertEquals(1, body.items().size());
    assertEquals("shepard:dlr.de/shepard-prod:data-objects:01HF-A:v1", body.items().get(0).pid());
    assertTrue(
      body.items().get(0).resolverUrl().endsWith(
        "/v2/.well-known/kip/shepard:dlr.de/shepard-prod:data-objects:01HF-A:v1"
      ),
      "resolverUrl should point to the KIP resolver path"
    );
  }

  @Test
  void emptyListWhenNeverPublished() {
    when(entityIdResolver.resolveLong("01HF-NEW")).thenReturn(99L);
    when(permissionsService.isAccessTypeAllowedForUser(eq(99L), eq(AccessType.Read), eq("alice")))
      .thenReturn(true);
    stubBoundedDao("01HF-NEW", List.of());

    Response r = rest.list("data-objects", "01HF-NEW", 0, 50, securityContext, uriInfo);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<PublicationIO> body = (PagedResponseIO<PublicationIO>) r.getEntity();
    assertEquals(0, body.total());
    assertTrue(body.items().isEmpty(), "Unpublished entity should return an empty list");
  }

  @Test
  void multiplePublicationsReturnedMostRecentFirst() {
    when(entityIdResolver.resolveLong("01HF-A")).thenReturn(42L);
    when(permissionsService.isAccessTypeAllowedForUser(eq(42L), eq(AccessType.Read), eq("alice")))
      .thenReturn(true);
    Publication v2 = publication("shepard:dlr.de/shepard-prod:data-objects:01HF-A:v2", 1_747_100_000_000L);
    v2.setVersionNumber(2);
    Publication v1 = publication("shepard:dlr.de/shepard-prod:data-objects:01HF-A:v1", 1_747_000_000_000L);
    // DAO returns most-recent first (matches PublicationDAO.findByEntityAppId ordering)
    stubBoundedDao("01HF-A", List.of(v2, v1));

    Response r = rest.list("data-objects", "01HF-A", 0, 50, securityContext, uriInfo);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<PublicationIO> body = (PagedResponseIO<PublicationIO>) r.getEntity();
    assertEquals(2, body.total());
    assertEquals(2, body.items().size());
    assertTrue(body.items().get(0).pid().endsWith(":v2"), "First item should be most-recent (v2)");
    assertTrue(body.items().get(1).pid().endsWith(":v1"), "Second item should be v1");
  }

  @Test
  void retiredPublicationIncludedInList() {
    when(entityIdResolver.resolveLong("01HF-A")).thenReturn(42L);
    when(permissionsService.isAccessTypeAllowedForUser(eq(42L), eq(AccessType.Read), eq("alice")))
      .thenReturn(true);
    Publication retired = publication("shepard:dlr.de/shepard-prod:data-objects:01HF-A:v1", 1_747_000_000_000L);
    retired.setDigitalObjectMutability("retired");
    stubBoundedDao("01HF-A", List.of(retired));

    Response r = rest.list("data-objects", "01HF-A", 0, 50, securityContext, uriInfo);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<PublicationIO> body = (PagedResponseIO<PublicationIO>) r.getEntity();
    assertEquals(1, body.total());
    assertEquals(1, body.items().size(), "Retired publications should still appear in the list");
    assertEquals("retired", body.items().get(0).digitalObjectMutability());
  }

  @Test
  void missingAuthReturns401() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = rest.list("data-objects", "01HF-A", 0, 50, securityContext, uriInfo);
    assertEquals(401, r.getStatus());
  }

  @Test
  void unsupportedKindReturns404WithProblemJson() {
    Response r = rest.list("file-references", "01HF-A", 0, 50, securityContext, uriInfo);
    assertEquals(404, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
    assertTrue(r.getEntity().toString().contains("publish.kind.unsupported"));
  }

  @Test
  void missingEntityReturns404() {
    when(entityIdResolver.resolveLong("01HF-MISSING")).thenThrow(new NotFoundException("nope"));
    Response r = rest.list("data-objects", "01HF-MISSING", 0, 50, securityContext, uriInfo);
    assertEquals(404, r.getStatus());
  }

  @Test
  void permissionDeniedReturns403() {
    when(entityIdResolver.resolveLong("01HF-A")).thenReturn(42L);
    when(permissionsService.isAccessTypeAllowedForUser(eq(42L), eq(AccessType.Read), eq("alice")))
      .thenReturn(false);
    Response r = rest.list("data-objects", "01HF-A", 0, 50, securityContext, uriInfo);
    assertEquals(403, r.getStatus());
  }

  @Test
  void collectionsKindIsAccepted() {
    when(entityIdResolver.resolveLong("01COLL-A")).thenReturn(55L);
    when(permissionsService.isAccessTypeAllowedForUser(eq(55L), eq(AccessType.Read), eq("alice")))
      .thenReturn(true);
    Publication pub = publication("shepard:dlr.de/shepard-prod:collections:01COLL-A:v1", 1_747_000_000_000L);
    pub.setEntityKind("collections");
    pub.setEntityAppId("01COLL-A");
    stubBoundedDao("01COLL-A", List.of(pub));

    Response r = rest.list("collections", "01COLL-A", 0, 50, securityContext, uriInfo);

    assertEquals(200, r.getStatus());
  }

  @Test
  void paginationParamsForwardedToDao() {
    when(entityIdResolver.resolveLong("01HF-A")).thenReturn(42L);
    when(permissionsService.isAccessTypeAllowedForUser(eq(42L), eq(AccessType.Read), eq("alice")))
      .thenReturn(true);
    // Simulate 75 publications total; request page 1, pageSize 25 → skip=25.
    when(publicationDAO.countByEntityAppId(eq("01HF-A"))).thenReturn(75L);
    Publication pub = publication("shepard:dlr.de/shepard-prod:data-objects:01HF-A:v26", 1_747_000_000_000L);
    when(publicationDAO.findByEntityAppId(eq("01HF-A"), eq(25), eq(25))).thenReturn(List.of(pub));

    Response r = rest.list("data-objects", "01HF-A", 1, 25, securityContext, uriInfo);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<PublicationIO> body = (PagedResponseIO<PublicationIO>) r.getEntity();
    assertEquals(75L, body.total());
    assertEquals(1, body.page());
    assertEquals(25, body.pageSize());
    assertEquals(1, body.items().size());
    assertEquals("75", r.getHeaderString("X-Total-Count"));
  }
}
