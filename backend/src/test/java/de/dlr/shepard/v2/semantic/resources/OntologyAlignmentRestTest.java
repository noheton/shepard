package de.dlr.shepard.v2.semantic.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.semantic.daos.OntologyAlignmentDAO;
import de.dlr.shepard.context.semantic.entities.OntologyAlignment;
import de.dlr.shepard.v2.semantic.io.OntologyAlignmentIO;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * TPL3a-lite — unit tests for {@link OntologyAlignmentRest}.
 *
 * <p>The DAO is mocked so no Neo4j instance is required.
 */
class OntologyAlignmentRestTest {

  private OntologyAlignmentDAO dao;
  private OntologyAlignmentRest rest;

  @BeforeEach
  void setUp() throws Exception {
    dao = mock(OntologyAlignmentDAO.class);
    rest = new OntologyAlignmentRest();
    // Inject the mocked DAO via reflection (CDI not available in plain unit tests)
    var field = OntologyAlignmentRest.class.getDeclaredField("ontologyAlignmentDAO");
    field.setAccessible(true);
    field.set(rest, dao);
  }

  // ─── Class-level annotations ──────────────────────────────────────────────

  @Test
  void classCarriesV2PathAnnotation() {
    Path p = OntologyAlignmentRest.class.getAnnotation(Path.class);
    assertNotNull(p, "OntologyAlignmentRest must be @Path-annotated");
    assertTrue(
      p.value().startsWith("/v2/"),
      "@Path must start with /v2/ per CLAUDE.md policy — got: " + p.value()
    );
  }

  @Test
  void classCarriesInstanceAdminRolesAllowed() {
    RolesAllowed ra = OntologyAlignmentRest.class.getAnnotation(RolesAllowed.class);
    assertNotNull(ra, "OntologyAlignmentRest must be @RolesAllowed-annotated");
    boolean hasAdminRole = false;
    for (String role : ra.value()) {
      if (Constants.INSTANCE_ADMIN_ROLE.equals(role)) {
        hasAdminRole = true;
        break;
      }
    }
    assertTrue(hasAdminRole, "OntologyAlignmentRest must require instance-admin role");
  }

  @Test
  void listMethodCarriesGetAnnotation() throws NoSuchMethodException {
    Method m = OntologyAlignmentRest.class.getMethod("list");
    assertNotNull(m.getAnnotation(GET.class), "list() must be annotated with @GET");
    assertNotNull(m.getAnnotation(Produces.class), "list() must be annotated with @Produces");
    assertEquals(
      MediaType.APPLICATION_JSON,
      m.getAnnotation(Produces.class).value()[0],
      "list() must produce application/json"
    );
  }

  // ─── Functional tests ─────────────────────────────────────────────────────

  @Test
  void emptyRegistry_returns200WithEmptyList() {
    when(dao.findAll()).thenReturn(List.of());

    Response r = rest.list();
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<OntologyAlignmentIO> body = (List<OntologyAlignmentIO>) r.getEntity();
    assertNotNull(body);
    assertTrue(body.isEmpty(), "Empty DAO must produce empty list in response");
  }

  @Test
  void populatedRegistry_returns200WithAllRows() {
    OntologyAlignment e1 = makeEntity(
      "app-1", "Collection", "http://purl.obolibrary.org/obo/IAO_0000100",
      "rdfs:subClassOf", "HIGH", "aidocs/semantics/96-upper-ontology-alignment.md", 1000L
    );
    OntologyAlignment e2 = makeEntity(
      "app-2", "DataObject", "http://purl.obolibrary.org/obo/IAO_0000027",
      "rdfs:subClassOf", "HIGH", "aidocs/semantics/96-upper-ontology-alignment.md", 2000L
    );
    when(dao.findAll()).thenReturn(List.of(e1, e2));

    Response r = rest.list();
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<OntologyAlignmentIO> body = (List<OntologyAlignmentIO>) r.getEntity();
    assertNotNull(body);
    assertEquals(2, body.size(), "Two entities must produce two IO objects");

    OntologyAlignmentIO io1 = body.get(0);
    assertEquals("app-1", io1.appId());
    assertEquals("Collection", io1.shepardConcept());
    assertEquals("http://purl.obolibrary.org/obo/IAO_0000100", io1.upperOntologyUri());
    assertEquals("rdfs:subClassOf", io1.relationshipType());
    assertEquals("HIGH", io1.confidence());
    assertEquals("aidocs/semantics/96-upper-ontology-alignment.md", io1.source());
    assertEquals(1000L, io1.createdAt());
  }

  @Test
  void ioFromEntity_mapsAllFields() {
    OntologyAlignment e = makeEntity(
      "my-appid", "Activity", "http://www.w3.org/ns/prov#Activity",
      "rdfs:subClassOf", "HIGH", "aidocs/semantics/96-upper-ontology-alignment.md", 999L
    );
    OntologyAlignmentIO io = OntologyAlignmentIO.from(e);
    assertEquals("my-appid", io.appId());
    assertEquals("Activity", io.shepardConcept());
    assertEquals("http://www.w3.org/ns/prov#Activity", io.upperOntologyUri());
    assertEquals("rdfs:subClassOf", io.relationshipType());
    assertEquals("HIGH", io.confidence());
    assertEquals("aidocs/semantics/96-upper-ontology-alignment.md", io.source());
    assertEquals(999L, io.createdAt());
  }

  @Test
  void ioFromEntity_nullCreatedAt_isAllowed() {
    OntologyAlignment e = makeEntity(
      "a", "Shape", "http://purl.obolibrary.org/obo/IAO_0000104",
      "rdfs:subClassOf", "MEDIUM", "aidocs/semantics/96-upper-ontology-alignment.md", null
    );
    OntologyAlignmentIO io = OntologyAlignmentIO.from(e);
    assertTrue(io.createdAt() == null, "createdAt may be null for rows without timestamp");
  }

  // ─── helpers ──────────────────────────────────────────────────────────────

  private static OntologyAlignment makeEntity(
    String appId,
    String concept,
    String uri,
    String relType,
    String confidence,
    String source,
    Long createdAt
  ) {
    OntologyAlignment e = new OntologyAlignment();
    e.setAppId(appId);
    e.setShepardConcept(concept);
    e.setUpperOntologyUri(uri);
    e.setRelationshipType(relType);
    e.setConfidence(confidence);
    e.setSource(source);
    e.setCreatedAt(createdAt);
    return e;
  }
}
