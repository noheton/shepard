package de.dlr.shepard.v2.publish.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
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
 * APISIMP-PUBLICATIONS-KIND-PATH-SEGMENT unit tests for {@link FlatPublicationsRest}.
 *
 * <p>Collaborators mocked,
 * UriInfo seeded with a realistic base URI.
 */
class FlatPublicationsRestTest {

  private FlatPublicationsRest rest;
  private PublicationDAO publicationDAO;
  private PermissionsService permissionsService;
  private EntityIdResolver entityIdResolver;
  private SecurityContext securityContext;
  private UriInfo uriInfo;

  @BeforeEach
  void setUp() {
    rest = new FlatPublicationsRest();
    publicationDAO = mock(PublicationDAO.class);
    permissionsService = mock(PermissionsService.class);
    entityIdResolver = mock(EntityIdResolver.class);
    securityContext = mock(SecurityContext.class);
    uriInfo = mock(UriInfo.class);

    rest.publicationDAO = publicationDAO;
    rest.permissionsService = permissionsService;
    rest.entityIdResolver = entityIdResolver;

    Principal alice = () -> "alice";
    when(securityContext.getUserPrincipal()).thenReturn(alice);
    when(uriInfo.getBaseUri()).thenReturn(URI.create("https://shepard.example.dlr.de:8443/shepard/api/"));
  }

  private Publication publication(String pid) {
    Publication p = new Publication();
    p.setAppId("pub-" + Math.abs(pid.hashCode()));
    p.setPid(pid);
    p.setMintedAt(1_747_000_000_000L);
    p.setMinterId("local");
    p.setPublishedBy("alice");
    p.setEntityKind("data-objects");
    p.setEntityAppId("01HF-A");
    p.setVersionNumber(1);
    return p;
  }

  private void stubPagedDao(String appId, long ogmId, List<Publication> page, long total,
    int skip, int limit) {
    when(entityIdResolver.resolveLong(appId)).thenReturn(ogmId);
    when(permissionsService.isAccessTypeAllowedForUser(eq(ogmId), eq(AccessType.Read), eq("alice")))
      .thenReturn(true);
    when(publicationDAO.countByEntityAppId(appId)).thenReturn(total);
    when(publicationDAO.findByEntityAppId(eq(appId), eq(skip), eq(limit))).thenReturn(page);
  }

  @Test
  void happyPathReturns200WithPublicationList() {
    Publication pub = publication("shepard:dlr.de:data-objects:01HF-A:v1");
    stubPagedDao("01HF-A", 42L, List.of(pub), 1L, 0, 50);

    Response r = rest.list("01HF-A", 0, 50, securityContext, uriInfo);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<PublicationIO> body = (PagedResponseIO<PublicationIO>) r.getEntity();
    assertEquals(1, body.items().size());
    assertEquals("shepard:dlr.de:data-objects:01HF-A:v1", body.items().get(0).pid());
    assertEquals(1L, body.total());
    assertEquals(0, body.page());
    assertEquals(50, body.pageSize());
  }

  @Test
  void resolverUrlIsBuiltCorrectly() {
    Publication pub = publication("shepard:dlr.de:data-objects:01HF-A:v1");
    stubPagedDao("01HF-A", 42L, List.of(pub), 1L, 0, 50);

    Response r = rest.list("01HF-A", 0, 50, securityContext, uriInfo);
    @SuppressWarnings("unchecked")
    PagedResponseIO<PublicationIO> body = (PagedResponseIO<PublicationIO>) r.getEntity();
    assertTrue(
      body.items().get(0).resolverUrl().endsWith("/v2/.well-known/kip/shepard:dlr.de:data-objects:01HF-A:v1"),
      "resolverUrl should point to the KIP resolver path"
    );
  }

  @Test
  void emptyListWhenNeverPublished() {
    stubPagedDao("01HF-NEW", 99L, List.of(), 0L, 0, 50);

    Response r = rest.list("01HF-NEW", 0, 50, securityContext, uriInfo);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<PublicationIO> body = (PagedResponseIO<PublicationIO>) r.getEntity();
    assertTrue(body.items().isEmpty(), "Unpublished entity should return empty items");
    assertEquals(0L, body.total());
  }

  @Test
  void missingEntityAppIdReturns400() {
    Response r = rest.list(null, 0, 50, securityContext, uriInfo);
    assertEquals(400, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
  }

  @Test
  void blankEntityAppIdReturns400() {
    Response r = rest.list("   ", 0, 50, securityContext, uriInfo);
    assertEquals(400, r.getStatus());
  }

  @Test
  void missingAuthReturns401() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = rest.list("01HF-A", 0, 50, securityContext, uriInfo);
    assertEquals(401, r.getStatus());
  }

  @Test
  void unknownEntityReturns404() {
    when(entityIdResolver.resolveLong("01HF-X")).thenThrow(new NotFoundException("nope"));
    Response r = rest.list("01HF-X", 0, 50, securityContext, uriInfo);
    assertEquals(404, r.getStatus());
  }

  @Test
  void permissionDeniedReturns403() {
    when(entityIdResolver.resolveLong("01HF-A")).thenReturn(42L);
    when(permissionsService.isAccessTypeAllowedForUser(eq(42L), eq(AccessType.Read), eq("alice")))
      .thenReturn(false);
    Response r = rest.list("01HF-A", 0, 50, securityContext, uriInfo);
    assertEquals(403, r.getStatus());
  }

  @Test
  void collectionEntityWorks() {
    when(entityIdResolver.resolveLong("01COLL-A")).thenReturn(55L);
    when(permissionsService.isAccessTypeAllowedForUser(eq(55L), eq(AccessType.Read), eq("alice")))
      .thenReturn(true);
    Publication pub = publication("shepard:dlr.de:collections:01COLL-A:v1");
    pub.setEntityKind("collections");
    pub.setEntityAppId("01COLL-A");
    when(publicationDAO.countByEntityAppId("01COLL-A")).thenReturn(1L);
    when(publicationDAO.findByEntityAppId(eq("01COLL-A"), anyInt(), anyInt())).thenReturn(List.of(pub));

    Response r = rest.list("01COLL-A", 0, 50, securityContext, uriInfo);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<PublicationIO> body = (PagedResponseIO<PublicationIO>) r.getEntity();
    assertEquals("collections", body.items().get(0).entityKind());
  }

  @Test
  void retiredPublicationIncluded() {
    Publication retired = publication("shepard:dlr.de:data-objects:01HF-A:v1");
    retired.setDigitalObjectMutability("retired");
    stubPagedDao("01HF-A", 42L, List.of(retired), 1L, 0, 50);

    Response r = rest.list("01HF-A", 0, 50, securityContext, uriInfo);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<PublicationIO> body = (PagedResponseIO<PublicationIO>) r.getEntity();
    assertEquals(1, body.items().size(), "Retired publications should appear in the flat alias too");
    assertEquals("retired", body.items().get(0).digitalObjectMutability());
  }

  @Test
  void paginationFirstPageReturnsSlice() {
    List<Publication> firstPage = List.of(
      publication("pid-0"), publication("pid-1"), publication("pid-2"));
    stubPagedDao("01HF-A", 42L, firstPage, 7L, 0, 3);

    Response r = rest.list("01HF-A", 0, 3, securityContext, uriInfo);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<PublicationIO> body = (PagedResponseIO<PublicationIO>) r.getEntity();
    assertEquals(3, body.items().size(), "First page should contain 3 items");
    assertEquals(7L, body.total(), "Total should reflect full count from countByEntityAppId");
    assertEquals(0, body.page());
    assertEquals(3, body.pageSize());
  }

  @Test
  void paginationSecondPageReturnsNextSlice() {
    List<Publication> secondPage = List.of(
      publication("pid-3"), publication("pid-4"), publication("pid-5"));
    stubPagedDao("01HF-A", 42L, secondPage, 7L, 3, 3);

    Response r = rest.list("01HF-A", 1, 3, securityContext, uriInfo);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<PublicationIO> body = (PagedResponseIO<PublicationIO>) r.getEntity();
    assertEquals(3, body.items().size(), "Second page should contain 3 items");
    assertEquals(7L, body.total());
    assertEquals(1, body.page());
  }

  @Test
  void paginationBeyondEndReturnsEmptyItems() {
    // page=5 * pageSize=10 = 50 > total=3; clamped to total (3) to prevent CWE-190 overflow.
    // Neo4j SKIP 3 LIMIT 10 on a 3-record set returns empty — same observable result.
    stubPagedDao("01HF-A", 42L, List.of(), 3L, 3, 10);

    Response r = rest.list("01HF-A", 5, 10, securityContext, uriInfo);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<PublicationIO> body = (PagedResponseIO<PublicationIO>) r.getEntity();
    assertTrue(body.items().isEmpty(), "Page beyond end should return empty items");
    assertEquals(3L, body.total(), "Total should still reflect full count");
  }

  @Test
  void totalCountPresentInBody() {
    Publication pub = publication("shepard:dlr.de:data-objects:01HF-A:v1");
    stubPagedDao("01HF-A", 42L, List.of(pub), 1L, 0, 50);

    Response r = rest.list("01HF-A", 0, 50, securityContext, uriInfo);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<PublicationIO> body = (PagedResponseIO<PublicationIO>) r.getEntity();
    assertEquals(1L, body.total(), "total count should be present in paged response body");
  }
}
