package de.dlr.shepard.v2.dataobject.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.v2.annotations.daos.SemanticAnnotationV2DAO;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * SHAPES-V-PREFILL-2 — unit tests for {@link DataObjectRdfRest}.
 *
 * <p>All tests use Mockito field injection. No CDI context is started.
 *
 * <p>Covers the five required acceptance criteria:
 * <ol>
 *   <li>200 OK with valid Turtle when annotations exist</li>
 *   <li>200 OK with empty-but-valid Turtle when no annotations</li>
 *   <li>404 when DataObject appId does not exist</li>
 *   <li>401/403 when caller lacks read permission</li>
 *   <li>Response {@code Content-Type} is {@code text/turtle}</li>
 * </ol>
 */
class DataObjectRdfRestTest {

  static final String DO_APP_ID = "018f9c5a-7e26-7000-a000-000000000042";
  static final long DO_OGM_ID = 99L;
  static final String CALLER = "alice";

  @Mock
  SemanticAnnotationV2DAO annotationDAO;

  @Mock
  PermissionsService permissionsService;

  @Mock
  EntityIdResolver entityIdResolver;

  @Mock
  SecurityContext securityContext;

  @Mock
  Principal principal;

  DataObjectRdfRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new DataObjectRdfRest();
    resource.annotationDAO = annotationDAO;
    resource.permissionsService = permissionsService;
    resource.entityIdResolver = entityIdResolver;

    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);

    // Default: DataObject exists
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenReturn(DO_OGM_ID);

    // Default: caller has Read permission
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(DO_APP_ID), eq(AccessType.Read), eq(CALLER)))
        .thenReturn(true);

    // Default: no annotations
    when(annotationDAO.findBySubjectAppId(DO_APP_ID)).thenReturn(List.of());
  }

  // ── test 1: 200 OK with valid Turtle when annotations exist ─────────────

  @Test
  void returns200WithTurtleWhenAnnotationsExist() {
    SemanticAnnotation ann = makeAnnotation(
        "ann-001",
        DO_APP_ID,
        "DataObject",
        "http://purl.org/dc/terms/title",
        "Hot-fire TR-004"
    );
    when(annotationDAO.findBySubjectAppId(DO_APP_ID)).thenReturn(List.of(ann));

    Response r = resource.getDataObjectRdf(DO_APP_ID, securityContext);

    assertEquals(200, r.getStatus());
    String body = (String) r.getEntity();
    assertNotNull(body);
    assertTrue(body.contains("@prefix oa:"), "Expected oa: prefix");
    assertTrue(body.contains("@prefix shepard:"), "Expected shepard: prefix");
    // Flat triple: subject IRI
    assertTrue(body.contains("https://shepard.dlr.de/v2/dataobjects/" + DO_APP_ID),
        "Expected subject IRI in flat triple");
    // Object literal value
    assertTrue(body.contains("Hot-fire TR-004"), "Expected annotation literal in Turtle");
    // OA annotation block
    assertTrue(body.contains("oa:Annotation"), "Expected oa:Annotation type in Turtle");
    assertTrue(body.contains("oa:hasTarget"), "Expected oa:hasTarget in Turtle");
  }

  // ── test 2: 200 OK with empty-but-valid Turtle when no annotations ────

  @Test
  void returns200WithPrefixOnlyTurtleWhenNoAnnotations() {
    // annotationDAO returns empty list (default stub)
    Response r = resource.getDataObjectRdf(DO_APP_ID, securityContext);

    assertEquals(200, r.getStatus());
    String body = (String) r.getEntity();
    assertNotNull(body);
    // Prefixes must be present even when there are no triples
    assertTrue(body.contains("@prefix oa:"), "Expected oa: prefix in empty document");
    assertTrue(body.contains("@prefix prov:"), "Expected prov: prefix in empty document");
    assertTrue(body.contains("@prefix rdf:"), "Expected rdf: prefix in empty document");
    assertTrue(body.contains("@prefix sh:"), "Expected sh: prefix in empty document");
    assertTrue(body.contains("@prefix shepard:"), "Expected shepard: prefix in empty document");
    // No triple content beyond prefixes
    assertTrue(!body.contains("oa:Annotation"),
        "Annotation block should be absent when annotation list is empty");
  }

  // ── test 3: 404 when DataObject appId does not exist ─────────────────

  @Test
  void returns404WhenDataObjectNotFound() {
    String unknownId = "018f-unknown-appid";
    when(entityIdResolver.resolveLong(unknownId)).thenThrow(new NotFoundException());

    Response r = resource.getDataObjectRdf(unknownId, securityContext);

    assertEquals(404, r.getStatus());
    verify(permissionsService, never()).isAccessAllowedForDataObjectAppId(any(), any(), any());
    verify(annotationDAO, never()).findBySubjectAppId(any());
  }

  // ── test 4a: 401 when caller is not authenticated ────────────────────

  @Test
  void returns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);

    Response r = resource.getDataObjectRdf(DO_APP_ID, securityContext);

    assertEquals(401, r.getStatus());
    verify(entityIdResolver, never()).resolveLong(any());
    verify(annotationDAO, never()).findBySubjectAppId(any());
  }

  // ── test 4b: 403 when caller lacks Read permission ────────────────────

  @Test
  void returns403WhenCallerLacksReadPermission() {
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(DO_APP_ID), eq(AccessType.Read), eq(CALLER)))
        .thenReturn(false);

    Response r = resource.getDataObjectRdf(DO_APP_ID, securityContext);

    assertEquals(403, r.getStatus());
    verify(annotationDAO, never()).findBySubjectAppId(any());
  }

  // ── test 5: Content-Type is text/turtle ──────────────────────────────

  @Test
  void responseContentTypeIsTextTurtle() {
    Response r = resource.getDataObjectRdf(DO_APP_ID, securityContext);

    assertEquals(200, r.getStatus());
    // The JAX-RS runtime populates Content-Type from @Produces when
    // Response.ok(entity, mediaType) is used — check the entity type header
    // set in the Response builder.
    String contentType = r.getMediaType() != null ? r.getMediaType().toString() : "";
    // When entity is a String the mediaType comes from the second arg to Response.ok(...)
    assertTrue(contentType.startsWith("text/turtle"),
        "Expected Content-Type text/turtle but got: " + contentType);
  }

  // ── test 6: multiple annotations produce multiple blocks ─────────────

  @Test
  void multipleAnnotationsProduceMultipleBlocks() {
    SemanticAnnotation ann1 = makeAnnotation(
        "ann-001", DO_APP_ID, "DataObject",
        "http://purl.org/dc/terms/title", "Run TR-004"
    );
    SemanticAnnotation ann2 = makeAnnotation(
        "ann-002", DO_APP_ID, "DataObject",
        "http://purl.org/dc/terms/subject", "LOX/LH2"
    );
    when(annotationDAO.findBySubjectAppId(DO_APP_ID)).thenReturn(List.of(ann1, ann2));

    Response r = resource.getDataObjectRdf(DO_APP_ID, securityContext);

    assertEquals(200, r.getStatus());
    String body = (String) r.getEntity();
    // Both literal values must appear
    assertTrue(body.contains("Run TR-004"), "Expected first annotation literal");
    assertTrue(body.contains("LOX/LH2"), "Expected second annotation literal");
    // Two oa:Annotation blocks
    int count = countOccurrences(body, "oa:Annotation");
    assertEquals(2, count, "Expected 2 oa:Annotation blocks");
  }

  // ── test 7: IRI-valued annotation produces IRI object ─────────────────

  @Test
  void iriValuedAnnotationProducesIriObject() {
    SemanticAnnotation ann = new SemanticAnnotation();
    ann.setAppId("ann-iri-01");
    ann.setSubjectAppId(DO_APP_ID);
    ann.setSubjectKind("DataObject");
    ann.setPropertyIRI("http://www.w3.org/1999/02/22-rdf-syntax-ns#type");
    // valueIRI is set — no valueName
    ann.setValueIRI("http://qudt.org/vocab/unit/M-PER-SEC");
    ann.setValueName(null);

    when(annotationDAO.findBySubjectAppId(DO_APP_ID)).thenReturn(List.of(ann));

    Response r = resource.getDataObjectRdf(DO_APP_ID, securityContext);

    assertEquals(200, r.getStatus());
    String body = (String) r.getEntity();
    assertTrue(body.contains("<http://qudt.org/vocab/unit/M-PER-SEC>"),
        "Expected IRI-form object for IRI-valued annotation");
  }

  // ── helper ────────────────────────────────────────────────────────────────

  static SemanticAnnotation makeAnnotation(
      String annAppId,
      String subjectAppId,
      String subjectKind,
      String predicateIri,
      String valueName) {
    SemanticAnnotation a = new SemanticAnnotation();
    a.setAppId(annAppId);
    a.setSubjectAppId(subjectAppId);
    a.setSubjectKind(subjectKind);
    a.setPropertyIRI(predicateIri);
    a.setValueName(valueName);
    return a;
  }

  static int countOccurrences(String haystack, String needle) {
    int count = 0;
    int idx = 0;
    while ((idx = haystack.indexOf(needle, idx)) != -1) {
      count++;
      idx += needle.length();
    }
    return count;
  }
}
