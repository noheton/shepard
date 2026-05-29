package de.dlr.shepard.v2.admin.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.publish.daos.PublicationDAO;
import de.dlr.shepard.publish.entities.Publication;
import de.dlr.shepard.v2.publish.io.PublicationIO;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AdminPublicationsRest} (RDM-003).
 *
 * <p>Mirrors the Mockito-only pattern used by {@link AdminFeaturesRestTest}
 * — no container spin-up, fast feedback, full branch coverage.
 */
class AdminPublicationsRestTest {

  private AdminPublicationsRest rest;
  private PublicationDAO publicationDAO;
  private UriInfo uriInfo;

  @BeforeEach
  void setUp() {
    rest = new AdminPublicationsRest();
    publicationDAO = mock(PublicationDAO.class);
    uriInfo = mock(UriInfo.class);

    rest.publicationDAO = publicationDAO;
    rest.uriInfo = uriInfo;

    when(uriInfo.getBaseUri()).thenReturn(URI.create("https://shepard.example.dlr.de:8443/shepard/api/"));
  }

  // ------------------------------------------------------------------
  // Helper
  // ------------------------------------------------------------------

  private Publication pub(String appId, String pid, long mintedAt, String publishedBy,
                          String entityKind, String entityAppId, String mutability) {
    Publication p = new Publication();
    p.setAppId(appId);
    p.setPid(pid);
    p.setMintedAt(mintedAt);
    p.setMinterId("local");
    p.setPublishedBy(publishedBy);
    p.setEntityKind(entityKind);
    p.setEntityAppId(entityAppId);
    p.setVersionNumber(1);
    p.setDigitalObjectMutability(mutability);
    return p;
  }

  // ------------------------------------------------------------------
  // Security gate
  // ------------------------------------------------------------------

  @Test
  void classCarriesInstanceAdminGate() {
    RolesAllowed gate = AdminPublicationsRest.class.getAnnotation(RolesAllowed.class);
    assertNotNull(gate, "AdminPublicationsRest must be @RolesAllowed-gated at class level");
    assertEquals(1, gate.value().length);
    assertEquals("instance-admin", gate.value()[0]);
  }

  // ------------------------------------------------------------------
  // Happy path: instance-admin sees all publications
  // ------------------------------------------------------------------

  @Test
  void emptyInstanceReturns200WithEmptyList() {
    when(publicationDAO.findAll()).thenReturn(List.of());

    Response r = rest.list();

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<PublicationIO> body = (List<PublicationIO>) r.getEntity();
    assertNotNull(body);
    assertTrue(body.isEmpty());
  }

  @Test
  void singlePublicationReturnedCorrectly() {
    when(publicationDAO.findAll()).thenReturn(List.of(
      pub("pub-01", "shepard:dlr.de/test:data-objects:01HF:v1",
          1_747_000_000_000L, "alice", "data-objects", "01HF", null)
    ));

    Response r = rest.list();
    assertEquals(200, r.getStatus());

    @SuppressWarnings("unchecked")
    List<PublicationIO> body = (List<PublicationIO>) r.getEntity();
    assertEquals(1, body.size());

    PublicationIO io = body.get(0);
    assertEquals("pub-01", io.appId());
    assertEquals("shepard:dlr.de/test:data-objects:01HF:v1", io.pid());
    assertEquals("alice", io.publishedBy());
    assertEquals("data-objects", io.entityKind());
    assertEquals("01HF", io.entityAppId());
    assertEquals("local", io.minterId());
    assertNotNull(io.mintedAt());
    // resolver URL is built from uriInfo base
    assertTrue(io.resolverUrl().contains("well-known/kip"));
    assertTrue(io.resolverUrl().contains(io.pid()));
  }

  @Test
  void multiplePublicationsReturnedSortedNewestFirst() {
    Publication older = pub("pub-old", "pid-old", 1_000_000_000L, "alice",
        "data-objects", "01HF-A", null);
    Publication newer = pub("pub-new", "pid-new", 2_000_000_000L, "bob",
        "data-objects", "01HF-B", null);
    Publication middle = pub("pub-mid", "pid-mid", 1_500_000_000L, "carol",
        "collections", "01HF-C", null);

    // DAO returns in arbitrary order
    when(publicationDAO.findAll()).thenReturn(new ArrayList<>(List.of(older, newer, middle)));

    Response r = rest.list();
    assertEquals(200, r.getStatus());

    @SuppressWarnings("unchecked")
    List<PublicationIO> body = (List<PublicationIO>) r.getEntity();
    assertEquals(3, body.size());
    // newest first
    assertEquals("pub-new", body.get(0).appId());
    assertEquals("pub-mid", body.get(1).appId());
    assertEquals("pub-old", body.get(2).appId());
  }

  @Test
  void retiredPublicationHasMutabilityField() {
    when(publicationDAO.findAll()).thenReturn(List.of(
      pub("pub-ret", "pid-retired", 1_000_000_000L, "alice",
          "data-objects", "01HF-X", "retired")
    ));

    Response r = rest.list();
    assertEquals(200, r.getStatus());

    @SuppressWarnings("unchecked")
    List<PublicationIO> body = (List<PublicationIO>) r.getEntity();
    assertEquals("retired", body.get(0).digitalObjectMutability());
  }

  @Test
  void activePublicationHasNullMutabilityField() {
    when(publicationDAO.findAll()).thenReturn(List.of(
      pub("pub-act", "pid-active", 1_000_000_000L, "alice",
          "data-objects", "01HF-Y", null)
    ));

    Response r = rest.list();
    assertEquals(200, r.getStatus());

    @SuppressWarnings("unchecked")
    List<PublicationIO> body = (List<PublicationIO>) r.getEntity();
    // digitalObjectMutability should be null for active publications
    assertNotNull(body.get(0));
    assertEquals(null, body.get(0).digitalObjectMutability());
  }

  // ------------------------------------------------------------------
  // resolverBase helper
  // ------------------------------------------------------------------

  @Test
  void resolverBaseStripsDefaultHttpsPort() {
    UriInfo u = mock(UriInfo.class);
    when(u.getBaseUri()).thenReturn(URI.create("https://shepard.example/shepard/api/"));
    String base = AdminPublicationsRest.resolverBase(u);
    assertEquals("https://shepard.example/v2/.well-known/kip/", base);
  }

  @Test
  void resolverBaseStripsDefaultHttpPort() {
    UriInfo u = mock(UriInfo.class);
    when(u.getBaseUri()).thenReturn(URI.create("http://localhost:80/shepard/api/"));
    String base = AdminPublicationsRest.resolverBase(u);
    assertEquals("http://localhost/v2/.well-known/kip/", base);
  }

  @Test
  void resolverBaseKeepsNonStandardPort() {
    UriInfo u = mock(UriInfo.class);
    when(u.getBaseUri()).thenReturn(URI.create("https://shepard.example:8443/shepard/api/"));
    String base = AdminPublicationsRest.resolverBase(u);
    assertEquals("https://shepard.example:8443/v2/.well-known/kip/", base);
  }

  @Test
  void resolverUrlContainedInPublicationIO() {
    when(publicationDAO.findAll()).thenReturn(List.of(
      pub("pub-02", "shepard:dlr.de/test:data-objects:01HF-Z:v1",
          1_000_000_000L, "bob", "data-objects", "01HF-Z", null)
    ));

    Response r = rest.list();
    @SuppressWarnings("unchecked")
    List<PublicationIO> body = (List<PublicationIO>) r.getEntity();

    // resolver URL must be fully qualified and include the PID
    String url = body.get(0).resolverUrl();
    assertTrue(url.startsWith("https://shepard.example.dlr.de:8443/v2/.well-known/kip/"),
      "resolverUrl should start with the kip resolver prefix, got: " + url);
    assertTrue(url.endsWith("shepard:dlr.de/test:data-objects:01HF-Z:v1"));
  }
}
