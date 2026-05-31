package de.dlr.shepard.v2.admin.publish.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.publish.daos.PublicationDAO;
import de.dlr.shepard.publish.entities.Publication;
import de.dlr.shepard.v2.admin.publish.io.AdminPublicationItemIO;
import de.dlr.shepard.v2.admin.publish.io.AdminPublicationListIO;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * RDM-003 unit tests for {@link AdminPublicationsRest}.
 *
 * <p>Pattern mirrors {@link
 * de.dlr.shepard.v2.publish.resources.PublicationsListRestTest}: real
 * collaborators are replaced with Mockito mocks; the test verifies only the
 * REST-layer behaviour (status codes, pagination clamping, response envelope
 * shape). DAO query correctness is tested at the DAO layer.
 */
class AdminPublicationsRestTest {

  private AdminPublicationsRest rest;
  private PublicationDAO publicationDAO;

  @BeforeEach
  void setUp() {
    rest = new AdminPublicationsRest();
    publicationDAO = mock(PublicationDAO.class);
    rest.publicationDAO = publicationDAO;
  }

  // ── factory helpers ──────────────────────────────────────────────────

  private Publication pub(String pid, String entityKind, String entityAppId, long mintedAt) {
    Publication p = new Publication();
    p.setAppId("pub-" + pid.hashCode());
    p.setPid(pid);
    p.setEntityKind(entityKind);
    p.setEntityAppId(entityAppId);
    p.setMintedAt(mintedAt);
    p.setMinterId("local");
    p.setPublishedBy("alice");
    p.setVersionNumber(1);
    return p;
  }

  // ── happy-path tests ──────────────────────────────────────────────────

  @Test
  void happyPath_returnsTwoRows() {
    Publication p1 = pub("shepard:dlr.de/prod:data-objects:A:v2", "data-objects", "A", 1_748_000_000_000L);
    p1.setVersionNumber(2);
    Publication p2 = pub("shepard:dlr.de/prod:collections:B:v1", "collections", "B", 1_747_000_000_000L);

    when(publicationDAO.findAll(0, 25)).thenReturn(List.of(p1, p2));
    when(publicationDAO.countAll()).thenReturn(2L);

    Response r = rest.list(0, 25);

    assertEquals(200, r.getStatus());
    AdminPublicationListIO body = (AdminPublicationListIO) r.getEntity();
    assertNotNull(body);
    assertEquals(2, body.items().size());
    assertEquals(2L, body.totalCount());
    assertEquals(0, body.page());
    assertEquals(25, body.size());
  }

  @Test
  void happyPath_emptyInstance_returnsEmptyList() {
    when(publicationDAO.findAll(0, 25)).thenReturn(List.of());
    when(publicationDAO.countAll()).thenReturn(0L);

    Response r = rest.list(0, 25);

    assertEquals(200, r.getStatus());
    AdminPublicationListIO body = (AdminPublicationListIO) r.getEntity();
    assertNotNull(body);
    assertTrue(body.items().isEmpty());
    assertEquals(0L, body.totalCount());
  }

  // ── pagination-clamping tests ─────────────────────────────────────────

  @Test
  void negativePage_clampedToZero() {
    when(publicationDAO.findAll(0, 25)).thenReturn(List.of());
    when(publicationDAO.countAll()).thenReturn(0L);

    Response r = rest.list(-5, 25);

    assertEquals(200, r.getStatus());
  }

  @Test
  void oversizedSize_clampedToMaxPageSize() {
    // size > MAX_PAGE_SIZE → should be clamped; we verify the effective size
    // is reflected in the response envelope.
    when(publicationDAO.findAll(0, AdminPublicationsRest.MAX_PAGE_SIZE))
      .thenReturn(List.of());
    when(publicationDAO.countAll()).thenReturn(0L);

    Response r = rest.list(0, 99_999);

    assertEquals(200, r.getStatus());
    AdminPublicationListIO body = (AdminPublicationListIO) r.getEntity();
    assertEquals(AdminPublicationsRest.MAX_PAGE_SIZE, body.size());
  }

  @Test
  void sizeZero_clampedToOne() {
    when(publicationDAO.findAll(0, 1)).thenReturn(List.of());
    when(publicationDAO.countAll()).thenReturn(0L);

    Response r = rest.list(0, 0);

    assertEquals(200, r.getStatus());
    AdminPublicationListIO body = (AdminPublicationListIO) r.getEntity();
    assertEquals(1, body.size());
  }

  // ── retired-rows-included test ────────────────────────────────────────

  @Test
  void retiredRowsIncludedInList() {
    Publication retired = pub("shepard:dlr.de/prod:data-objects:X:v1", "data-objects", "X", 1_747_000_000_000L);
    retired.setDigitalObjectMutability("retired");
    when(publicationDAO.findAll(0, 25)).thenReturn(List.of(retired));
    when(publicationDAO.countAll()).thenReturn(1L);

    Response r = rest.list(0, 25);

    assertEquals(200, r.getStatus());
    AdminPublicationListIO body = (AdminPublicationListIO) r.getEntity();
    assertEquals(1, body.items().size());
    assertEquals("retired", body.items().get(0).digitalObjectMutability());
  }

  // ── item field-projection test ────────────────────────────────────────

  @Test
  void itemFieldsProjectedCorrectly() {
    Publication p = pub("shepard:dlr.de/prod:data-objects:DO1:v3", "data-objects", "DO1", 1_748_100_000_000L);
    p.setVersionNumber(3);
    p.setMinterId("datacite");
    p.setPublishedBy("bob");

    when(publicationDAO.findAll(0, 25)).thenReturn(List.of(p));
    when(publicationDAO.countAll()).thenReturn(1L);

    Response r = rest.list(0, 25);

    assertEquals(200, r.getStatus());
    AdminPublicationListIO body = (AdminPublicationListIO) r.getEntity();
    AdminPublicationItemIO item = body.items().get(0);
    assertEquals("shepard:dlr.de/prod:data-objects:DO1:v3", item.pid());
    assertEquals("data-objects", item.entityKind());
    assertEquals("DO1", item.entityAppId());
    assertEquals("datacite", item.minterId());
    assertEquals("bob", item.publishedBy());
    assertEquals(3, item.versionNumber());
    assertNotNull(item.mintedAt());
  }

  // ── role annotation check ─────────────────────────────────────────────

  @Test
  void classCarriesRolesAllowedInstanceAdmin() throws NoSuchMethodException {
    // Verify the instance-admin gate is declared on the class (not just at
    // the method level) so JAX-RS picks it up for all methods in the resource.
    var annotation = AdminPublicationsRest.class.getAnnotation(
      jakarta.annotation.security.RolesAllowed.class
    );
    assertNotNull(annotation, "@RolesAllowed missing from AdminPublicationsRest");
    assertEquals(1, annotation.value().length);
    assertEquals("instance-admin", annotation.value()[0]);
  }
}
