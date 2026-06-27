package de.dlr.shepard.v2.publish.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * APISIMP-PUBLICATIONS-KIND-PATH-SEGMENT unit tests for {@link FlatPublicationsRest}.
 *
 * <p>Pattern mirrors {@link PublicationsListRestTest}: collaborators mocked,
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

  @Test
  void happyPathReturns200WithPublicationList() {
    when(entityIdResolver.resolveLong("01HF-A")).thenReturn(42L);
    when(permissionsService.isAccessTypeAllowedForUser(eq(42L), eq(AccessType.Read), eq("alice")))
      .thenReturn(true);
    Publication pub = publication("shepard:dlr.de:data-objects:01HF-A:v1");
    when(publicationDAO.findByEntityAppId("01HF-A")).thenReturn(List.of(pub));

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
    when(entityIdResolver.resolveLong("01HF-A")).thenReturn(42L);
    when(permissionsService.isAccessTypeAllowedForUser(eq(42L), eq(AccessType.Read), eq("alice")))
      .thenReturn(true);
    Publication pub = publication("shepard:dlr.de:data-objects:01HF-A:v1");
    when(publicationDAO.findByEntityAppId("01HF-A")).thenReturn(List.of(pub));

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
    when(entityIdResolver.resolveLong("01HF-NEW")).thenReturn(99L);
    when(permissionsService.isAccessTypeAllowedForUser(eq(99L), eq(AccessType.Read), eq("alice")))
      .thenReturn(true);
    when(publicationDAO.findByEntityAppId("01HF-NEW")).thenReturn(List.of());

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
    when(publicationDAO.findByEntityAppId("01COLL-A")).thenReturn(List.of(pub));

    Response r = rest.list("01COLL-A", 0, 50, securityContext, uriInfo);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<PublicationIO> body = (PagedResponseIO<PublicationIO>) r.getEntity();
    assertEquals("collections", body.items().get(0).entityKind());
  }

  @Test
  void retiredPublicationIncluded() {
    when(entityIdResolver.resolveLong("01HF-A")).thenReturn(42L);
    when(permissionsService.isAccessTypeAllowedForUser(eq(42L), eq(AccessType.Read), eq("alice")))
      .thenReturn(true);
    Publication retired = publication("shepard:dlr.de:data-objects:01HF-A:v1");
    retired.setDigitalObjectMutability("retired");
    when(publicationDAO.findByEntityAppId("01HF-A")).thenReturn(List.of(retired));

    Response r = rest.list("01HF-A", 0, 50, securityContext, uriInfo);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<PublicationIO> body = (PagedResponseIO<PublicationIO>) r.getEntity();
    assertEquals(1, body.items().size(), "Retired publications should appear in the flat alias too");
    assertEquals("retired", body.items().get(0).digitalObjectMutability());
  }

  @Test
  void paginationFirstPageReturnsSlice() {
    when(entityIdResolver.resolveLong("01HF-A")).thenReturn(42L);
    when(permissionsService.isAccessTypeAllowedForUser(eq(42L), eq(AccessType.Read), eq("alice")))
      .thenReturn(true);
    List<Publication> pubs = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
      pubs.add(publication("pid-" + i));
    }
    when(publicationDAO.findByEntityAppId("01HF-A")).thenReturn(pubs);

    Response r = rest.list("01HF-A", 0, 3, securityContext, uriInfo);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<PublicationIO> body = (PagedResponseIO<PublicationIO>) r.getEntity();
    assertEquals(3, body.items().size(), "First page should contain 3 items");
    assertEquals(7L, body.total(), "Total should reflect full count");
    assertEquals(0, body.page());
    assertEquals(3, body.pageSize());
  }

  @Test
  void paginationSecondPageReturnsNextSlice() {
    when(entityIdResolver.resolveLong("01HF-A")).thenReturn(42L);
    when(permissionsService.isAccessTypeAllowedForUser(eq(42L), eq(AccessType.Read), eq("alice")))
      .thenReturn(true);
    List<Publication> pubs = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
      pubs.add(publication("pid-" + i));
    }
    when(publicationDAO.findByEntityAppId("01HF-A")).thenReturn(pubs);

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
    when(entityIdResolver.resolveLong("01HF-A")).thenReturn(42L);
    when(permissionsService.isAccessTypeAllowedForUser(eq(42L), eq(AccessType.Read), eq("alice")))
      .thenReturn(true);
    List<Publication> pubs = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      pubs.add(publication("pid-" + i));
    }
    when(publicationDAO.findByEntityAppId("01HF-A")).thenReturn(pubs);

    Response r = rest.list("01HF-A", 5, 10, securityContext, uriInfo);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<PublicationIO> body = (PagedResponseIO<PublicationIO>) r.getEntity();
    assertTrue(body.items().isEmpty(), "Page beyond end should return empty items");
    assertEquals(3L, body.total(), "Total should still reflect full count");
  }

  @Test
  void xTotalCountHeaderPresent() {
    when(entityIdResolver.resolveLong("01HF-A")).thenReturn(42L);
    when(permissionsService.isAccessTypeAllowedForUser(eq(42L), eq(AccessType.Read), eq("alice")))
      .thenReturn(true);
    Publication pub = publication("shepard:dlr.de:data-objects:01HF-A:v1");
    when(publicationDAO.findByEntityAppId("01HF-A")).thenReturn(List.of(pub));

    Response r = rest.list("01HF-A", 0, 50, securityContext, uriInfo);

    assertEquals(200, r.getStatus());
    assertEquals("1", r.getHeaderString("X-Total-Count"),
      "X-Total-Count header should carry total count before paging");
  }
}
