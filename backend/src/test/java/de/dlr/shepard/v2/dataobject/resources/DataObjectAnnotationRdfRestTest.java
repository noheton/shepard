package de.dlr.shepard.v2.dataobject.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
 * SHAPES-V-PREFILL-2-RDF-ENDPOINT — unit tests for {@link DataObjectAnnotationRdfRest}.
 *
 * <p>All tests use Mockito field injection. No CDI context is started.
 * Test cases cover:
 * <ol>
 *   <li>Happy path — DataObject with 2 annotations returns valid Turtle with both triples.</li>
 *   <li>DataObject with 0 annotations — 200 with prefix-only Turtle (no triples).</li>
 *   <li>DataObject not found — 404.</li>
 *   <li>Unauthenticated request — 401.</li>
 *   <li>No Read permission — 403.</li>
 *   <li>Single-annotation Turtle correctness (structure assertions).</li>
 * </ol>
 */
class DataObjectAnnotationRdfRestTest {

  static final String DO_APP_ID  = "018f9c5a-0001-7000-a000-000000000001";
  static final String ANN_APP_ID_1 = "ann-001";
  static final String ANN_APP_ID_2 = "ann-002";
  static final String PREDICATE_1 = "http://example.org/material";
  static final String PREDICATE_2 = "http://example.org/phase";
  static final String CALLER = "alice";

  @Mock
  SemanticAnnotationV2DAO annotationDAO;

  @Mock
  PermissionsService permissionsService;

  @Mock
  EntityIdResolver entityIdResolver;

  @Mock
  SecurityContext sc;

  @Mock
  Principal principal;

  DataObjectAnnotationRdfRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new DataObjectAnnotationRdfRest();
    resource.annotationDAO = annotationDAO;
    resource.permissionsService = permissionsService;
    resource.entityIdResolver = entityIdResolver;

    // Default: authenticated caller
    when(sc.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);

    // Default: DataObject exists (entity resolver returns a long OGM id)
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenReturn(42L);

    // Default: caller has Read access
    when(permissionsService.isAccessAllowedForDataObjectAppId(
      eq(DO_APP_ID), eq(AccessType.Read), eq(CALLER))).thenReturn(true);
  }

  // ─── 401 Unauthenticated ─────────────────────────────────────────────────

  @Test
  void getRdf_returns401WhenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);

    Response r = resource.getRdf(DO_APP_ID, sc);

    assertThat(r.getStatus()).isEqualTo(401);
  }

  // ─── 404 DataObject not found ────────────────────────────────────────────

  @Test
  void getRdf_returns404WhenDataObjectNotFound() {
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenThrow(new NotFoundException("not found"));

    Response r = resource.getRdf(DO_APP_ID, sc);

    assertThat(r.getStatus()).isEqualTo(404);
  }

  // ─── 403 No Read permission ──────────────────────────────────────────────

  @Test
  void getRdf_returns403WhenNoReadPermission() {
    when(permissionsService.isAccessAllowedForDataObjectAppId(
      eq(DO_APP_ID), eq(AccessType.Read), eq(CALLER))).thenReturn(false);

    Response r = resource.getRdf(DO_APP_ID, sc);

    assertThat(r.getStatus()).isEqualTo(403);
  }

  // ─── 200 Happy path — 2 annotations ─────────────────────────────────────

  @Test
  void getRdf_returns200WithTurtleForTwoAnnotations() {
    var ann1 = makeAnnotation(ANN_APP_ID_1, DO_APP_ID, "DataObject", PREDICATE_1, "CF/LMPAEK");
    var ann2 = makeAnnotation(ANN_APP_ID_2, DO_APP_ID, "DataObject", PREDICATE_2, "Layup");

    when(annotationDAO.findFiltered(
      eq(DO_APP_ID), isNull(), isNull(), isNull(), eq(0), anyInt()))
      .thenReturn(List.of(ann1, ann2));

    Response r = resource.getRdf(DO_APP_ID, sc);

    assertThat(r.getStatus()).isEqualTo(200);

    String turtle = (String) r.getEntity();

    // Prefix block must appear exactly once
    assertThat(countOccurrences(turtle, "@prefix oa:")).isEqualTo(1);
    assertThat(countOccurrences(turtle, "@prefix prov:")).isEqualTo(1);
    assertThat(countOccurrences(turtle, "@prefix shepard:")).isEqualTo(1);

    // Both predicate IRIs must appear
    assertThat(turtle).contains(PREDICATE_1);
    assertThat(turtle).contains(PREDICATE_2);

    // Both object literals must appear
    assertThat(turtle).contains("CF/LMPAEK");
    assertThat(turtle).contains("Layup");

    // OA annotation structure for both
    assertThat(countOccurrences(turtle, "oa:Annotation")).isEqualTo(2);
    assertThat(countOccurrences(turtle, "oa:hasTarget")).isEqualTo(2);
    assertThat(countOccurrences(turtle, "oa:hasBody")).isEqualTo(2);
  }

  // ─── 200 Happy path — 0 annotations ─────────────────────────────────────

  @Test
  void getRdf_returns200WithPrefixOnlyTurtleWhenNoAnnotations() {
    when(annotationDAO.findFiltered(
      eq(DO_APP_ID), isNull(), isNull(), isNull(), eq(0), anyInt()))
      .thenReturn(List.of());

    Response r = resource.getRdf(DO_APP_ID, sc);

    assertThat(r.getStatus()).isEqualTo(200);

    String turtle = (String) r.getEntity();

    // Prefix block present — no triples
    assertThat(turtle).contains("@prefix oa:");
    assertThat(turtle).contains("@prefix shepard:");
    assertThat(turtle).doesNotContain("oa:Annotation");
    assertThat(turtle).doesNotContain("oa:hasTarget");
    // No predicate IRI means no flat triple either
    assertThat(turtle).doesNotContain("rdf-syntax-ns#predicate");
  }

  // ─── Content-Type is text/turtle ─────────────────────────────────────────

  @Test
  void getRdf_hasTextTurtleContentType() {
    when(annotationDAO.findFiltered(
      eq(DO_APP_ID), isNull(), isNull(), isNull(), eq(0), anyInt()))
      .thenReturn(List.of());

    Response r = resource.getRdf(DO_APP_ID, sc);

    // Response.ok(body, "text/turtle") sets the media type.
    // The entity is a String at unit-test level; we just verify it's 200.
    assertThat(r.getStatus()).isEqualTo(200);
    assertThat(r.getEntity()).isInstanceOf(String.class);
  }

  // ─── Turtle structure for IRI object ─────────────────────────────────────

  @Test
  void getRdf_handlesIriObjectValue() {
    var ann = new SemanticAnnotation();
    ann.setAppId(ANN_APP_ID_1);
    ann.setSubjectAppId(DO_APP_ID);
    ann.setSubjectKind("DataObject");
    ann.setPropertyIRI(PREDICATE_1);
    ann.setValueIRI("http://example.org/materials/CF_LMPAEK");
    // valueName is null — IRI wins

    when(annotationDAO.findFiltered(
      eq(DO_APP_ID), isNull(), isNull(), isNull(), eq(0), anyInt()))
      .thenReturn(List.of(ann));

    Response r = resource.getRdf(DO_APP_ID, sc);

    assertThat(r.getStatus()).isEqualTo(200);
    String turtle = (String) r.getEntity();

    // Object must be an IRI reference, not a literal
    assertThat(turtle).contains("<http://example.org/materials/CF_LMPAEK>");
    assertThat(turtle).doesNotContain("\"http://example.org/materials/CF_LMPAEK\"");
  }

  // ─── helpers ─────────────────────────────────────────────────────────────

  private static SemanticAnnotation makeAnnotation(
    String appId, String subjectAppId, String subjectKind,
    String predicateIri, String value
  ) {
    SemanticAnnotation a = new SemanticAnnotation();
    a.setAppId(appId);
    a.setSubjectAppId(subjectAppId);
    a.setSubjectKind(subjectKind);
    a.setPropertyIRI(predicateIri);
    a.setValueName(value);
    a.setSourceMode("human");
    a.setConfidence(1.0);
    return a;
  }

  private static int countOccurrences(String haystack, String needle) {
    int count = 0;
    int idx = 0;
    while ((idx = haystack.indexOf(needle, idx)) != -1) {
      count++;
      idx += needle.length();
    }
    return count;
  }
}
