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
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.semantic.daos.SemanticAnnotationDAO;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * SHAPES-V-PREFILL-2-RDF-ENDPOINT — unit tests for the flat
 * {@code GET /v2/data-objects/{appId}/rdf} resource. Covers the three
 * gates (200 / 403 / 404) and the Turtle builder on a fully-populated
 * DataObject so the new code clears the {@code min-coverage-changed-files: 70}
 * gate.
 */
class DataObjectRdfRestTest {

  static final String DO_APP_ID = "018f9c5a-7e26-7000-a000-0000000000aa";
  static final String PRED_APP_ID = "018f9c5a-7e26-7000-a000-0000000000bb";
  static final String SUCC_APP_ID = "018f9c5a-7e26-7000-a000-0000000000cc";
  static final String TEMPLATE_APP_ID = "018f9c5a-7e26-7000-a000-0000000000dd";
  static final String CALLER = "alice";

  @Mock
  DataObjectDAO dataObjectDAO;

  @Mock
  SemanticAnnotationDAO semanticAnnotationDAO;

  @Mock
  PermissionsService permissionsService;

  @Mock
  SecurityContext securityContext;

  @Mock
  Principal principal;

  DataObjectRdfRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new DataObjectRdfRest();
    resource.dataObjectDAO = dataObjectDAO;
    resource.semanticAnnotationDAO = semanticAnnotationDAO;
    resource.permissionsService = permissionsService;
    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
    when(semanticAnnotationDAO.findBySubjectAppId(any())).thenReturn(List.of());
  }

  private DataObject makeDataObject(String appId, String name) {
    DataObject d = new DataObject();
    d.setAppId(appId);
    d.setName(name);
    d.setCreatedAt(new Date(1_700_000_000_000L));
    return d;
  }

  // ── gate cases ─────────────────────────────────────────────────────────

  @Test
  void getRdfReturns404WhenUnknown() {
    when(dataObjectDAO.findByAppId(DO_APP_ID)).thenReturn(null);
    Response r = resource.getRdf(DO_APP_ID, securityContext);
    assertEquals(404, r.getStatus());
    verify(permissionsService, never()).isAccessAllowedForDataObjectAppId(any(), any(), any());
  }

  @Test
  void getRdfReturns403WhenNoRead() {
    DataObject d = makeDataObject(DO_APP_ID, "TR-004");
    when(dataObjectDAO.findByAppId(DO_APP_ID)).thenReturn(d);
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(DO_APP_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(false);
    Response r = resource.getRdf(DO_APP_ID, securityContext);
    assertEquals(403, r.getStatus());
    verify(semanticAnnotationDAO, never()).findBySubjectAppId(any());
  }

  @Test
  void getRdfReturns200WithTurtle() {
    DataObject d = makeDataObject(DO_APP_ID, "TR-004");
    d.setDescription("hotfire test run");
    when(dataObjectDAO.findByAppId(DO_APP_ID)).thenReturn(d);
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(DO_APP_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(true);

    Response r = resource.getRdf(DO_APP_ID, securityContext);

    assertEquals(200, r.getStatus());
    Object entity = r.getEntity();
    assertNotNull(entity);
    String body = entity.toString();
    // Mandatory prefix declarations are present.
    assertTrue(body.contains("@prefix dcterms:"), "expected dcterms prefix");
    assertTrue(body.contains("@prefix m4i:"), "expected m4i prefix");
    assertTrue(body.contains("@prefix obo:"), "expected obo prefix");
    // The focus DataObject is the subject.
    assertTrue(body.contains(DO_APP_ID), "expected DataObject appId in body");
    assertTrue(body.contains("m4i:InvestigatedObject"), "expected m4i type");
    assertTrue(body.contains("dcterms:title \"TR-004\""), "expected title");
    assertTrue(body.contains("dcterms:description \"hotfire test run\""), "expected description");
  }

  // ── Turtle builder (pure-function shape) ───────────────────────────────

  @Test
  void buildTurtleEmitsFullSubgraph() {
    DataObject d = makeDataObject(DO_APP_ID, "TR-004");
    d.setDescription("hotfire test\nwith newline");
    d.setAttachedTemplateAppId(TEMPLATE_APP_ID);

    DataObject pred = makeDataObject(PRED_APP_ID, "TR-003");
    DataObject succ = makeDataObject(SUCC_APP_ID, "TR-005");
    d.setPredecessors(new ArrayList<>(List.of(pred)));
    d.setSuccessors(new ArrayList<>(List.of(succ)));

    SemanticAnnotation iriAnn = new SemanticAnnotation();
    iriAnn.setPropertyIRI("http://example.org/hasBench");
    iriAnn.setValueIRI("http://example.org/bench/p8");

    SemanticAnnotation litAnn = new SemanticAnnotation();
    litAnn.setPropertyIRI("http://example.org/note");
    litAnn.setValueName("anomaly observed");

    SemanticAnnotation numAnn = new SemanticAnnotation();
    numAnn.setPropertyIRI("http://example.org/peakVibration");
    numAnn.setNumericValue(12.0);

    SemanticAnnotation skipAnn = new SemanticAnnotation();
    // No predicate → dropped.
    skipAnn.setValueName("orphan");

    String turtle = DataObjectRdfRest.buildTurtle(d, List.of(iriAnn, litAnn, numAnn, skipAnn));

    // Header prefixes.
    assertTrue(turtle.contains("@prefix shepard: <https://shepard.dlr.de/v2/> ."));
    assertTrue(turtle.contains("@prefix xsd: "));

    // Focus subject + identifier.
    assertTrue(turtle.contains("<https://shepard.dlr.de/v2/dataobjects/" + DO_APP_ID + ">"));
    assertTrue(turtle.contains("dcterms:identifier \"" + DO_APP_ID + "\""));

    // Newline-escaped description.
    assertTrue(turtle.contains("dcterms:description \"hotfire test\\nwith newline\""));

    // ISO-8601 dateCreated.
    assertTrue(turtle.contains("^^xsd:dateTime"));

    // Predecessor / successor edges (target IRI form).
    assertTrue(turtle.contains("obo:RO_0002233 <https://shepard.dlr.de/v2/dataobjects/" + PRED_APP_ID + ">"));
    assertTrue(turtle.contains("obo:RO_0002234 <https://shepard.dlr.de/v2/dataobjects/" + SUCC_APP_ID + ">"));

    // Attached template.
    assertTrue(turtle.contains("shepard:hasTemplate <https://shepard.dlr.de/v2/templates/" + TEMPLATE_APP_ID + ">"));

    // Annotations: IRI, literal, numeric — each rendered correctly.
    assertTrue(turtle.contains("<http://example.org/hasBench> <http://example.org/bench/p8>"));
    assertTrue(turtle.contains("<http://example.org/note> \"anomaly observed\""));
    assertTrue(turtle.contains("<http://example.org/peakVibration> \"12.0\"^^xsd:double"));

    // The annotation without a predicate must NOT appear.
    assertTrue(!turtle.contains("orphan"), "annotation without predicateIRI must be skipped");
  }

  @Test
  void buildTurtleHandlesMinimalEntity() {
    DataObject d = new DataObject();
    d.setAppId(DO_APP_ID);
    // No name, no description, no createdAt, no annotations.

    String turtle = DataObjectRdfRest.buildTurtle(d, List.of());

    assertTrue(turtle.contains("a m4i:InvestigatedObject, prov:Entity"));
    assertTrue(turtle.contains("dcterms:identifier \"" + DO_APP_ID + "\""));
    // Trailing period closes the focus block.
    assertTrue(turtle.contains(" .\n"));
  }
}
