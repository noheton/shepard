package de.dlr.shepard.v2.annotations.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.context.semantic.entities.SemanticConfig;
import de.dlr.shepard.context.semantic.services.OntologyConfigService;
import de.dlr.shepard.provenance.entities.Activity;
import de.dlr.shepard.provenance.services.ProvenanceService;
import de.dlr.shepard.v2.annotations.daos.SemanticAnnotationV2DAO;
import de.dlr.shepard.v2.annotations.io.AnnotationIO;
import de.dlr.shepard.v2.annotations.io.CreateAnnotationIO;
import de.dlr.shepard.v2.annotations.io.UpdateAnnotationIO;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import de.dlr.shepard.v2.references.services.ReferencesV2Service;
import de.dlr.shepard.v2.references.spi.ReferenceKindHandler;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * SEMA-V6-004 — unit tests for {@link SemanticAnnotationV2Rest}.
 *
 * <p>All tests use Mockito field injection. No CDI context is started.
 */
class SemanticAnnotationV2RestTest {

  static final String ANN_APP_ID = "ann-001";
  static final String SUBJ_APP_ID = "do-001";
  static final String PREDICATE_IRI = "http://shepard.dlr.de/v/material";
  static final String CALLER = "alice";
  static final long OGM_ID = 42L;

  @Mock
  SemanticAnnotationV2DAO annotationDAO;

  @Mock
  PermissionsService permissionsService;

  @Mock
  EntityIdResolver entityIdResolver;

  @Mock
  OntologyConfigService ontologyConfigService;

  @Mock
  ProvenanceService provenanceService;

  @Mock
  de.dlr.shepard.v2.project.services.ProjectAnnotationConstraints projectAnnotationConstraints;

  @Mock
  ReferencesV2Service referencesService;

  @Mock
  ContainerRequestContext requestContext;

  @Mock
  SecurityContext sc;

  @Mock
  Principal principal;

  SemanticAnnotationV2Rest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new SemanticAnnotationV2Rest();
    resource.annotationDAO = annotationDAO;
    resource.permissionsService = permissionsService;
    resource.entityIdResolver = entityIdResolver;
    resource.ontologyConfigService = ontologyConfigService;
    resource.provenanceService = provenanceService;
    resource.projectAnnotationConstraints = projectAnnotationConstraints;
    resource.referencesService = referencesService;
    resource.requestContext = requestContext;

    when(sc.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);

    // Default: resolveWithLabels returns DataObject labels for the standard subject.
    when(entityIdResolver.resolveWithLabels(SUBJ_APP_ID))
      .thenReturn(new EntityIdResolver.LabeledResolution(OGM_ID, List.of("DataObject")));

    // Default: caller has Read+Write+Manage on the DataObject subject
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(SUBJ_APP_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(true);
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(SUBJ_APP_ID), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(true);
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(SUBJ_APP_ID), eq(AccessType.Manage), eq(CALLER)))
      .thenReturn(true);

    // Default: policy singleton returns null (= 'author-or-manager' default)
    SemanticConfig defaultConfig = new SemanticConfig();
    when(ontologyConfigService.loadSingleton()).thenReturn(defaultConfig);

    // Default: provenance capture is a no-op unless overridden per test
    when(provenanceService.record(anyString(), anyString(), anyString(), anyString(),
        anyString(), anyString(), anyString(), anyInt(), anyLong(), anyLong()))
      .thenReturn(null);
  }

  // ─── list ────────────────────────────────────────────────────────────────

  @Test
  void list_bySubjectAppId_returns200WithAnnotations() {
    var ann = annotation(ANN_APP_ID, SUBJ_APP_ID, "DataObject", PREDICATE_IRI, "CF/LMPAEK");
    when(annotationDAO.findFiltered(eq(SUBJ_APP_ID), any(), any(), any(), eq(0), eq(50)))
      .thenReturn(List.of(ann));
    when(annotationDAO.countFiltered(eq(SUBJ_APP_ID), any(), any(), any())).thenReturn(1L);

    Response r = resource.list(SUBJ_APP_ID, null, null, null, 0, 50, sc);

    assertThat(r.getStatus()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    PagedResponseIO<AnnotationIO> page = (PagedResponseIO<AnnotationIO>) r.getEntity();
    assertThat(page.items()).hasSize(1);
    assertThat(page.total()).isEqualTo(1L);
    assertThat(page.items().get(0).getSubjectAppId()).isEqualTo(SUBJ_APP_ID);
    assertThat(page.items().get(0).getObjectLiteral()).isEqualTo("CF/LMPAEK");
  }

  @Test
  void list_returns401WhenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);
    assertThat(resource.list(SUBJ_APP_ID, null, null, null, 0, 50, sc).getStatus()).isEqualTo(401);
  }

  @Test
  void list_returns403WhenNoReadPermission() {
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(SUBJ_APP_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(false);
    assertThat(resource.list(SUBJ_APP_ID, null, null, null, 0, 50, sc).getStatus()).isEqualTo(403);
  }

  @Test
  void list_returns400WhenPageSizeExceedsMax() {
    assertThat(resource.list(null, null, null, null, 0, 201, sc).getStatus()).isEqualTo(400);
  }

  @Test
  void list_returns400WhenPageNegative() {
    assertThat(resource.list(null, null, null, null, -1, 10, sc).getStatus()).isEqualTo(400);
  }

  // ─── pagination ──────────────────────────────────────────────────────────

  @Test
  void list_pagination_passesPageAndPageSizeToDAO() {
    when(annotationDAO.findFiltered(eq(SUBJ_APP_ID), any(), any(), any(), eq(2), eq(10)))
      .thenReturn(List.of());

    Response r = resource.list(SUBJ_APP_ID, null, null, null, 2, 10, sc);

    assertThat(r.getStatus()).isEqualTo(200);
    verify(annotationDAO).findFiltered(eq(SUBJ_APP_ID), any(), any(), any(), eq(2), eq(10));
  }

  // ─── get by appId ─────────────────────────────────────────────────────────

  @Test
  void get_returns200() {
    var ann = annotation(ANN_APP_ID, SUBJ_APP_ID, "DataObject", PREDICATE_IRI, "CF/LMPAEK");
    when(annotationDAO.findByAnnotationAppId(ANN_APP_ID)).thenReturn(ann);

    Response r = resource.get(ANN_APP_ID, sc);

    assertThat(r.getStatus()).isEqualTo(200);
    AnnotationIO io = (AnnotationIO) r.getEntity();
    assertThat(io.getAppId()).isEqualTo(ANN_APP_ID);
    assertThat(io.getPredicateIri()).isEqualTo(PREDICATE_IRI);
  }

  @Test
  void get_returns404WhenMissing() {
    when(annotationDAO.findByAnnotationAppId(ANN_APP_ID)).thenReturn(null);
    assertThat(resource.get(ANN_APP_ID, sc).getStatus()).isEqualTo(404);
  }

  @Test
  void get_returns403WhenNoReadPermission() {
    var ann = annotation(ANN_APP_ID, SUBJ_APP_ID, "DataObject", PREDICATE_IRI, "v");
    when(annotationDAO.findByAnnotationAppId(ANN_APP_ID)).thenReturn(ann);
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(SUBJ_APP_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(false);

    assertThat(resource.get(ANN_APP_ID, sc).getStatus()).isEqualTo(403);
  }

  // ─── create ──────────────────────────────────────────────────────────────

  @Test
  void create_returns201AndPersists() {
    CreateAnnotationIO body = createBody("DataObject", SUBJ_APP_ID, PREDICATE_IRI, "CF/LMPAEK", null);

    Response r = resource.create(body, sc, null);

    assertThat(r.getStatus()).isEqualTo(201);
    verify(annotationDAO).createOrUpdate(any(SemanticAnnotation.class));
    AnnotationIO io = (AnnotationIO) r.getEntity();
    assertThat(io.getObjectLiteral()).isEqualTo("CF/LMPAEK");
    assertThat(io.getSubjectKind()).isEqualTo("DataObject");
    assertThat(io.getSourceMode()).isEqualTo("human");
    assertThat(io.getConfidence()).isEqualTo(1.0);
  }

  @Test
  void create_returns400WhenBodyNull() {
    assertThat(resource.create(null, sc, null).getStatus()).isEqualTo(400);
    verify(annotationDAO, never()).createOrUpdate(any());
  }

  @Test
  void create_returns400WhenSubjectAppIdBlank() {
    CreateAnnotationIO body = createBody("DataObject", "", PREDICATE_IRI, "v", null);
    assertThat(resource.create(body, sc, null).getStatus()).isEqualTo(400);
  }

  @Test
  void create_returns400WhenSubjectKindBlank() {
    CreateAnnotationIO body = createBody("", SUBJ_APP_ID, PREDICATE_IRI, "v", null);
    assertThat(resource.create(body, sc, null).getStatus()).isEqualTo(400);
  }

  @Test
  void create_returns400WhenPredicateIriBlank() {
    CreateAnnotationIO body = createBody("DataObject", SUBJ_APP_ID, "", "v", null);
    assertThat(resource.create(body, sc, null).getStatus()).isEqualTo(400);
  }

  @Test
  void create_returns400WhenBothLiteralAndIriProvided() {
    CreateAnnotationIO body = createBody("DataObject", SUBJ_APP_ID, PREDICATE_IRI, "v", "http://example.org/v");
    assertThat(resource.create(body, sc, null).getStatus()).isEqualTo(400);
  }

  @Test
  void create_returns400WhenNeitherLiteralNorIriProvided() {
    CreateAnnotationIO body = new CreateAnnotationIO();
    body.setSubjectAppId(SUBJ_APP_ID);
    body.setSubjectKind("DataObject");
    body.setPredicateIri(PREDICATE_IRI);
    assertThat(resource.create(body, sc, null).getStatus()).isEqualTo(400);
  }

  @Test
  void create_returns403WhenNoWritePermission() {
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(SUBJ_APP_ID), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(false);
    CreateAnnotationIO body = createBody("DataObject", SUBJ_APP_ID, PREDICATE_IRI, "v", null);
    assertThat(resource.create(body, sc, null).getStatus()).isEqualTo(403);
    verify(annotationDAO, never()).createOrUpdate(any());
  }

  @Test
  void create_withIriObject_setsObjectIriNotLiteral() {
    CreateAnnotationIO body = new CreateAnnotationIO();
    body.setSubjectAppId(SUBJ_APP_ID);
    body.setSubjectKind("DataObject");
    body.setPredicateIri(PREDICATE_IRI);
    body.setObjectIri("http://example.org/material/CF_LMPAEK");

    Response r = resource.create(body, sc, null);
    assertThat(r.getStatus()).isEqualTo(201);
    AnnotationIO io = (AnnotationIO) r.getEntity();
    assertThat(io.getObjectIri()).isEqualTo("http://example.org/material/CF_LMPAEK");
    assertThat(io.getObjectLiteral()).isNull();
  }

  // ─── update ──────────────────────────────────────────────────────────────

  @Test
  void update_patchesObjectLiteral() {
    var ann = annotation(ANN_APP_ID, SUBJ_APP_ID, "DataObject", PREDICATE_IRI, "old-value");
    when(annotationDAO.findByAnnotationAppId(ANN_APP_ID)).thenReturn(ann);

    UpdateAnnotationIO body = new UpdateAnnotationIO();
    body.setObjectLiteral("new-value");

    Response r = resource.update(ANN_APP_ID, body, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    AnnotationIO io = (AnnotationIO) r.getEntity();
    assertThat(io.getObjectLiteral()).isEqualTo("new-value");
    verify(annotationDAO).createOrUpdate(ann);
  }

  @Test
  void update_patchesConfidence() {
    var ann = annotation(ANN_APP_ID, SUBJ_APP_ID, "DataObject", PREDICATE_IRI, "v");
    ann.setConfidence(1.0);
    when(annotationDAO.findByAnnotationAppId(ANN_APP_ID)).thenReturn(ann);

    UpdateAnnotationIO body = new UpdateAnnotationIO();
    body.setConfidence(0.85);

    Response r = resource.update(ANN_APP_ID, body, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    assertThat(((AnnotationIO) r.getEntity()).getConfidence()).isEqualTo(0.85);
  }

  @Test
  void update_returns404WhenMissing() {
    when(annotationDAO.findByAnnotationAppId(ANN_APP_ID)).thenReturn(null);
    assertThat(resource.update(ANN_APP_ID, new UpdateAnnotationIO(), sc).getStatus()).isEqualTo(404);
  }

  @Test
  void update_returns403WhenNoWritePermission() {
    var ann = annotation(ANN_APP_ID, SUBJ_APP_ID, "DataObject", PREDICATE_IRI, "v");
    when(annotationDAO.findByAnnotationAppId(ANN_APP_ID)).thenReturn(ann);
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(SUBJ_APP_ID), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(false);

    assertThat(resource.update(ANN_APP_ID, new UpdateAnnotationIO(), sc).getStatus()).isEqualTo(403);
    verify(annotationDAO, never()).createOrUpdate(any());
  }

  @Test
  void update_returns400WhenBothLiteralAndIriProvided() {
    var ann = annotation(ANN_APP_ID, SUBJ_APP_ID, "DataObject", PREDICATE_IRI, "v");
    when(annotationDAO.findByAnnotationAppId(ANN_APP_ID)).thenReturn(ann);

    UpdateAnnotationIO body = new UpdateAnnotationIO();
    body.setObjectLiteral("literal");
    body.setObjectIri("http://example.org/iri");

    assertThat(resource.update(ANN_APP_ID, body, sc).getStatus()).isEqualTo(400);
  }

  // ─── delete ──────────────────────────────────────────────────────────────

  @Test
  void delete_returns204AndDeletes() {
    // Caller is the author → permitted under default 'author-or-manager' policy.
    var ann = annotation(ANN_APP_ID, SUBJ_APP_ID, "DataObject", PREDICATE_IRI, "v");
    ann.setAgentUsername(CALLER);
    ann.setId(99L);
    when(annotationDAO.findByAnnotationAppId(ANN_APP_ID)).thenReturn(ann);

    Response r = resource.delete(ANN_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(204);
    verify(annotationDAO).deleteByNeo4jId(99L);
  }

  @Test
  void delete_returns404WhenMissing() {
    when(annotationDAO.findByAnnotationAppId(ANN_APP_ID)).thenReturn(null);
    assertThat(resource.delete(ANN_APP_ID, sc).getStatus()).isEqualTo(404);
    verify(annotationDAO, never()).deleteByNeo4jId(anyLong());
  }

  @Test
  void delete_returns403WhenNoWritePermission_nonAuthor() {
    // Caller is neither the author nor a manager → 403 under default policy.
    var ann = annotation(ANN_APP_ID, SUBJ_APP_ID, "DataObject", PREDICATE_IRI, "v");
    ann.setAgentUsername("other-user");  // not CALLER
    ann.setId(99L);
    when(annotationDAO.findByAnnotationAppId(ANN_APP_ID)).thenReturn(ann);
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(SUBJ_APP_ID), eq(AccessType.Manage), eq(CALLER)))
      .thenReturn(false);

    assertThat(resource.delete(ANN_APP_ID, sc).getStatus()).isEqualTo(403);
    verify(annotationDAO, never()).deleteByNeo4jId(anyLong());
  }

  @Test
  void delete_returns401WhenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);
    assertThat(resource.delete(ANN_APP_ID, sc).getStatus()).isEqualTo(401);
  }

  // ─── SEMA-V6-013: annotationDeletePolicy ─────────────────────────────────

  @Test
  void deleteAnnotation_byAuthor_allowedUnderDefaultPolicy() {
    // Default policy (null = 'author-or-manager'): author can delete.
    var ann = annotation(ANN_APP_ID, SUBJ_APP_ID, "DataObject", PREDICATE_IRI, "v");
    ann.setAgentUsername(CALLER);
    ann.setId(55L);
    when(annotationDAO.findByAnnotationAppId(ANN_APP_ID)).thenReturn(ann);
    // configService already returns default (null policy) from setUp

    Response r = resource.delete(ANN_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(204);
    verify(annotationDAO).deleteByNeo4jId(55L);
  }

  @Test
  void deleteAnnotation_byNonAuthorNonManager_blockedUnderDefaultPolicy() {
    // Default policy: non-author + non-manager → 403.
    var ann = annotation(ANN_APP_ID, SUBJ_APP_ID, "DataObject", PREDICATE_IRI, "v");
    ann.setAgentUsername("other-user");
    ann.setId(56L);
    when(annotationDAO.findByAnnotationAppId(ANN_APP_ID)).thenReturn(ann);
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(SUBJ_APP_ID), eq(AccessType.Manage), eq(CALLER)))
      .thenReturn(false);

    Response r = resource.delete(ANN_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(403);
    verify(annotationDAO, never()).deleteByNeo4jId(anyLong());
  }

  @Test
  void deleteAnnotation_withAuthorOnlyPolicy_blocksManager() {
    // 'author-only' policy: manager who is not the author is blocked.
    SemanticConfig cfg = new SemanticConfig();
    cfg.setAnnotationDeletePolicy("author-only");
    when(ontologyConfigService.loadSingleton()).thenReturn(cfg);

    var ann = annotation(ANN_APP_ID, SUBJ_APP_ID, "DataObject", PREDICATE_IRI, "v");
    ann.setAgentUsername("original-author");  // CALLER is not the author
    ann.setId(57L);
    when(annotationDAO.findByAnnotationAppId(ANN_APP_ID)).thenReturn(ann);
    // Manage=true is already mocked in setUp (CALLER is a manager)

    Response r = resource.delete(ANN_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(403);
    verify(annotationDAO, never()).deleteByNeo4jId(anyLong());
  }

  // ─── N1h: numeric value + unit IRI ───────────────────────────────────────

  /**
   * N1h — POST with numericValue + unitIri: fields are persisted on the entity
   * and reflected in the 201 response body.
   */
  @Test
  void create_withNumericValueAndUnitIri_roundTrips() {
    CreateAnnotationIO body = createBody("DataObject", SUBJ_APP_ID, PREDICATE_IRI, "25.0 kN", null);
    body.setNumericValue(25.0);
    body.setUnitIri("http://qudt.org/vocab/unit/KiloN");

    Response r = resource.create(body, sc, null);

    assertThat(r.getStatus()).isEqualTo(201);
    AnnotationIO io = (AnnotationIO) r.getEntity();
    assertThat(io.getNumericValue()).isEqualTo(25.0);
    assertThat(io.getUnitIri()).isEqualTo("http://qudt.org/vocab/unit/KiloN");
  }

  /**
   * N1h — POST without numericValue/unitIri: existing IRI-pair annotations
   * are unaffected (both fields null in the response).
   */
  @Test
  void create_withoutNumericValue_legacyAnnotationUnaffected() {
    CreateAnnotationIO body = createBody("DataObject", SUBJ_APP_ID, PREDICATE_IRI, "CF/LMPAEK", null);
    // numericValue and unitIri intentionally not set

    Response r = resource.create(body, sc, null);

    assertThat(r.getStatus()).isEqualTo(201);
    AnnotationIO io = (AnnotationIO) r.getEntity();
    assertThat(io.getNumericValue()).isNull();
    assertThat(io.getUnitIri()).isNull();
  }

  /**
   * N1h — GET after setting numericValue: fields are exposed on the AnnotationIO
   * returned by the GET endpoint.
   */
  @Test
  void get_withNumericValueAndUnitIri_returnsFields() {
    var ann = annotation(ANN_APP_ID, SUBJ_APP_ID, "DataObject", PREDICATE_IRI, "25.0 kN");
    ann.setNumericValue(25.0);
    ann.setUnitIRI("http://qudt.org/vocab/unit/KiloN");
    when(annotationDAO.findByAnnotationAppId(ANN_APP_ID)).thenReturn(ann);

    Response r = resource.get(ANN_APP_ID, sc);

    assertThat(r.getStatus()).isEqualTo(200);
    AnnotationIO io = (AnnotationIO) r.getEntity();
    assertThat(io.getNumericValue()).isEqualTo(25.0);
    assertThat(io.getUnitIri()).isEqualTo("http://qudt.org/vocab/unit/KiloN");
  }

  /**
   * N1h — PUT with numericValue: the existing annotation is patched and the
   * updated value is returned.
   */
  @Test
  void update_patchesNumericValueAndUnitIri() {
    var ann = annotation(ANN_APP_ID, SUBJ_APP_ID, "DataObject", PREDICATE_IRI, "old-value");
    ann.setNumericValue(10.0);
    ann.setUnitIRI("http://qudt.org/vocab/unit/N");
    when(annotationDAO.findByAnnotationAppId(ANN_APP_ID)).thenReturn(ann);

    UpdateAnnotationIO body = new UpdateAnnotationIO();
    body.setNumericValue(25.0);
    body.setUnitIri("http://qudt.org/vocab/unit/KiloN");

    Response r = resource.update(ANN_APP_ID, body, sc);

    assertThat(r.getStatus()).isEqualTo(200);
    AnnotationIO io = (AnnotationIO) r.getEntity();
    assertThat(io.getNumericValue()).isEqualTo(25.0);
    assertThat(io.getUnitIri()).isEqualTo("http://qudt.org/vocab/unit/KiloN");
  }

  // ─── turtle export ────────────────────────────────────────────────────────

  @Test
  void exportTurtle_returns200WithTurtleContent() {
    var ann = annotation(ANN_APP_ID, SUBJ_APP_ID, "DataObject", PREDICATE_IRI, "CF/LMPAEK");
    when(annotationDAO.findByAnnotationAppId(ANN_APP_ID)).thenReturn(ann);

    Response r = resource.exportTurtle(ANN_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    String turtle = (String) r.getEntity();
    assertThat(turtle).contains("oa:Annotation");
    assertThat(turtle).contains(PREDICATE_IRI);
    assertThat(turtle).contains("CF/LMPAEK");
    assertThat(turtle).contains("oa:hasTarget");
    assertThat(turtle).contains("oa:hasBody");
  }

  @Test
  void exportTurtle_returns404WhenMissing() {
    when(annotationDAO.findByAnnotationAppId(ANN_APP_ID)).thenReturn(null);
    assertThat(resource.exportTurtle(ANN_APP_ID, sc).getStatus()).isEqualTo(404);
  }

  // ─── collection subject kind (RESEED-FIND-MISC item a) ──────────────────

  static final String COLL_APP_ID = "coll-001";

  /** GET annotation on a Collection subject — owner must receive 200, not 403. */
  @Test
  void get_collectionSubject_returns200_whenOwnerHasReadPermission() {
    var ann = annotation(ANN_APP_ID, COLL_APP_ID, "Collection", PREDICATE_IRI, "v");
    when(annotationDAO.findByAnnotationAppId(ANN_APP_ID)).thenReturn(ann);
    when(entityIdResolver.resolveWithLabels(COLL_APP_ID))
      .thenReturn(new EntityIdResolver.LabeledResolution(OGM_ID, List.of("Collection")));
    // 3-arg — the fix: same overload used by every other Collection permission check in v2
    when(permissionsService.isAccessTypeAllowedForUser(eq(OGM_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(true);

    Response r = resource.get(ANN_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    verify(permissionsService).isAccessTypeAllowedForUser(eq(OGM_ID), eq(AccessType.Read), eq(CALLER));
  }

  @Test
  void get_collectionSubject_returns403_whenReadDenied() {
    var ann = annotation(ANN_APP_ID, COLL_APP_ID, "Collection", PREDICATE_IRI, "v");
    when(annotationDAO.findByAnnotationAppId(ANN_APP_ID)).thenReturn(ann);
    when(entityIdResolver.resolveWithLabels(COLL_APP_ID))
      .thenReturn(new EntityIdResolver.LabeledResolution(OGM_ID, List.of("Collection")));
    when(permissionsService.isAccessTypeAllowedForUser(eq(OGM_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(false);

    assertThat(resource.get(ANN_APP_ID, sc).getStatus()).isEqualTo(403);
  }

  /** LIST annotations on a Collection subject — owner must receive 200. */
  @Test
  void list_collectionSubject_returns200_whenOwnerHasReadPermission() {
    when(entityIdResolver.resolveWithLabels(COLL_APP_ID))
      .thenReturn(new EntityIdResolver.LabeledResolution(OGM_ID, List.of("Collection")));
    when(permissionsService.isAccessTypeAllowedForUser(eq(OGM_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(true);
    when(annotationDAO.findFiltered(eq(COLL_APP_ID), any(), any(), any(), eq(0), eq(50)))
      .thenReturn(List.of());
    when(annotationDAO.countFiltered(eq(COLL_APP_ID), any(), any(), any())).thenReturn(0L);

    Response r = resource.list(COLL_APP_ID, "Collection", null, null, 0, 50, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    verify(permissionsService).isAccessTypeAllowedForUser(eq(OGM_ID), eq(AccessType.Read), eq(CALLER));
  }

  @Test
  void list_collectionSubject_returns403_whenReadDenied() {
    when(entityIdResolver.resolveWithLabels(COLL_APP_ID))
      .thenReturn(new EntityIdResolver.LabeledResolution(OGM_ID, List.of("Collection")));
    when(permissionsService.isAccessTypeAllowedForUser(eq(OGM_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(false);

    assertThat(resource.list(COLL_APP_ID, "Collection", null, null, 0, 50, sc).getStatus()).isEqualTo(403);
  }

  // ─── legacy annotation (null subjectAppId) ────────────────────────────────

  @Test
  void get_legacyAnnotation_withNullSubject_grants_read() {
    var ann = annotation(ANN_APP_ID, null, null, PREDICATE_IRI, "legacy-value");
    when(annotationDAO.findByAnnotationAppId(ANN_APP_ID)).thenReturn(ann);

    Response r = resource.get(ANN_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(200);
  }

  @Test
  void delete_legacyAnnotation_withNullSubject_denies_write() {
    var ann = annotation(ANN_APP_ID, null, null, PREDICATE_IRI, "legacy-value");
    ann.setId(77L);
    when(annotationDAO.findByAnnotationAppId(ANN_APP_ID)).thenReturn(ann);

    Response r = resource.delete(ANN_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(403);
  }

  // ─── SEMA-V6-007: provenance back-pointer and AI-mode detection ──────────

  /**
   * SEMA-V6-007 — POST annotation: verify that the 201 response body carries
   * a non-null {@code sourceActivityAppId} equal to the Activity minted by
   * {@link ProvenanceService#record}.
   */
  @Test
  void createAnnotation_writesSourceActivityAppId() {
    Activity stubActivity = new Activity();
    stubActivity.setAppId("act-test-uuid");
    when(provenanceService.record(anyString(), anyString(), anyString(), anyString(),
        anyString(), anyString(), anyString(), anyInt(), anyLong(), anyLong()))
      .thenReturn(stubActivity);

    CreateAnnotationIO body = createBody("DataObject", SUBJ_APP_ID, PREDICATE_IRI, "CF/LMPAEK", null);
    Response r = resource.create(body, sc, null);

    assertThat(r.getStatus()).isEqualTo(201);
    AnnotationIO io = (AnnotationIO) r.getEntity();
    assertThat(io.getSourceActivityAppId())
      .as("sourceActivityAppId must be the appId minted by ProvenanceService")
      .isEqualTo("act-test-uuid");
    // DAO back-stamp must also have been called
    verify(annotationDAO).setSourceActivityAppId(anyString(), eq("act-test-uuid"));
  }

  /**
   * SEMA-V6-007 — PUT annotation: verify that a new {@code sourceActivityAppId}
   * is written for every update (CLAUDE.md: "Every annotation write records a
   * typed {@code :Activity}").
   */
  @Test
  void updateAnnotation_writesNewSourceActivityAppId() {
    Activity stubActivity = new Activity();
    stubActivity.setAppId("act-update-uuid");
    when(provenanceService.record(anyString(), anyString(), anyString(), anyString(),
        anyString(), anyString(), anyString(), anyInt(), anyLong(), anyLong()))
      .thenReturn(stubActivity);

    var ann = annotation(ANN_APP_ID, SUBJ_APP_ID, "DataObject", PREDICATE_IRI, "old-value");
    ann.setSourceActivityAppId("act-old-uuid");
    when(annotationDAO.findByAnnotationAppId(ANN_APP_ID)).thenReturn(ann);

    UpdateAnnotationIO body = new UpdateAnnotationIO();
    body.setObjectLiteral("new-value");
    Response r = resource.update(ANN_APP_ID, body, sc);

    assertThat(r.getStatus()).isEqualTo(200);
    AnnotationIO io = (AnnotationIO) r.getEntity();
    assertThat(io.getSourceActivityAppId())
      .as("sourceActivityAppId must reflect the Activity minted for this update, not the old value")
      .isEqualTo("act-update-uuid");
    verify(annotationDAO).setSourceActivityAppId(eq(ANN_APP_ID), eq("act-update-uuid"));
  }

  /**
   * SEMA-V6-007 — POST annotation with {@code X-AI-Agent} header: verify that
   * {@code sourceMode} defaults to {@code "ai"} when the header is present and
   * the caller does not supply an explicit {@code sourceMode}.
   */
  @Test
  void createAnnotation_withAiAgentHeader_setsSourceModeAi() {
    CreateAnnotationIO body = createBody("DataObject", SUBJ_APP_ID, PREDICATE_IRI, "CF/LMPAEK", null);
    // sourceMode not set → resource must infer "ai" from the header

    Response r = resource.create(body, sc, "MyTestAgent/1.0");

    assertThat(r.getStatus()).isEqualTo(201);
    AnnotationIO io = (AnnotationIO) r.getEntity();
    assertThat(io.getSourceMode())
      .as("X-AI-Agent header present without explicit sourceMode → must default to 'ai'")
      .isEqualTo("ai");
  }

  // ─── F9: Reference subject — walks to parent DataObject ──────────────────

  static final String REF_APP_ID = "ref-001";
  static final String PARENT_DO_APP_ID = "do-parent-001";

  /**
   * F9 — POST annotation on a FileReference subject: the gate resolves the
   * parent DataObject appId and calls isAccessAllowedForDataObjectAppId with it
   * (not with the Reference appId), then returns 201.
   */
  @Test
  void create_referenceSubject_walksToParentDataObject_returns201() {
    // Set up resolver: REF_APP_ID resolves to a FileReference node
    when(entityIdResolver.resolveWithLabels(REF_APP_ID))
      .thenReturn(new EntityIdResolver.LabeledResolution(99L, List.of("FileReference")));

    // Set up reference service: resolves to a reference whose parent DO has PARENT_DO_APP_ID
    BasicReference stubRef = new BasicReference();
    DataObject parentDo = new DataObject();
    parentDo.setAppId(PARENT_DO_APP_ID);
    stubRef.setDataObject(parentDo);
    ReferenceKindHandler stubHandler = org.mockito.Mockito.mock(ReferenceKindHandler.class);
    when(referencesService.resolveByAppId(REF_APP_ID))
      .thenReturn(Optional.of(new ReferencesV2Service.ResolvedReference(stubHandler, stubRef)));

    // Grant write on the parent DO
    when(permissionsService.isAccessAllowedForDataObjectAppId(
        eq(PARENT_DO_APP_ID), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(true);

    CreateAnnotationIO body = createBody("FileReference", REF_APP_ID, PREDICATE_IRI, "annotated-ref", null);
    Response r = resource.create(body, sc, null);

    assertThat(r.getStatus()).isEqualTo(201);
    // Verify gating used the PARENT DO appId, not the Reference appId
    verify(permissionsService)
      .isAccessAllowedForDataObjectAppId(eq(PARENT_DO_APP_ID), eq(AccessType.Write), eq(CALLER));
  }

  /**
   * F9 — POST annotation on a FileReference subject where the caller lacks Write
   * on the parent DataObject: must return 403, not 201.
   */
  @Test
  void create_referenceSubject_denied_when_parentDoForbidden() {
    when(entityIdResolver.resolveWithLabels(REF_APP_ID))
      .thenReturn(new EntityIdResolver.LabeledResolution(99L, List.of("FileReference")));

    BasicReference stubRef = new BasicReference();
    DataObject parentDo = new DataObject();
    parentDo.setAppId(PARENT_DO_APP_ID);
    stubRef.setDataObject(parentDo);
    ReferenceKindHandler stubHandler = org.mockito.Mockito.mock(ReferenceKindHandler.class);
    when(referencesService.resolveByAppId(REF_APP_ID))
      .thenReturn(Optional.of(new ReferencesV2Service.ResolvedReference(stubHandler, stubRef)));

    when(permissionsService.isAccessAllowedForDataObjectAppId(
        eq(PARENT_DO_APP_ID), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(false);

    CreateAnnotationIO body = createBody("FileReference", REF_APP_ID, PREDICATE_IRI, "v", null);
    assertThat(resource.create(body, sc, null).getStatus()).isEqualTo(403);
  }

  // ─── APISIMP-ANNOT-LIST-PARAMS-UNDOCUMENTED — @Parameter reflection gates ─

  /**
   * APISIMP-ANNOT-LIST-PARAMS-UNDOCUMENTED — verifies that every {@code @QueryParam}
   * on {@link SemanticAnnotationV2Rest#list} carries a non-blank {@code @Parameter}
   * description so the generated OpenAPI schema documents all six filters.
   */
  @Test
  void listAnnotations_subjectAppId_paramCarriesParameterAnnotation() throws NoSuchMethodException {
    var param = findListParam("subjectAppId");
    assertThat(param).as("subjectAppId must carry @QueryParam").isNotNull();
    var ann = param.getAnnotation(org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
    assertThat(ann).as("subjectAppId must carry @Parameter").isNotNull();
    assertThat(ann.description()).as("@Parameter.description for subjectAppId must be non-blank").isNotBlank();
  }

  @Test
  void listAnnotations_subjectKind_paramCarriesParameterAnnotation() throws NoSuchMethodException {
    var param = findListParam("subjectKind");
    assertThat(param).as("subjectKind must carry @QueryParam").isNotNull();
    var ann = param.getAnnotation(org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
    assertThat(ann).as("subjectKind must carry @Parameter").isNotNull();
    assertThat(ann.description()).as("@Parameter.description for subjectKind must be non-blank").isNotBlank();
  }

  @Test
  void listAnnotations_predicateIri_paramCarriesParameterAnnotation() throws NoSuchMethodException {
    var param = findListParam("predicateIri");
    assertThat(param).as("predicateIri must carry @QueryParam").isNotNull();
    var ann = param.getAnnotation(org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
    assertThat(ann).as("predicateIri must carry @Parameter").isNotNull();
    assertThat(ann.description()).as("@Parameter.description for predicateIri must be non-blank").isNotBlank();
  }

  @Test
  void listAnnotations_vocabId_paramCarriesParameterAnnotation() throws NoSuchMethodException {
    var param = findListParam("vocabId");
    assertThat(param).as("vocabId must carry @QueryParam").isNotNull();
  }

  // ─── APISIMP-ANNOT-FIND-PARAMS-UNDOCUMENTED — @Parameter reflection gates ─

  /**
   * APISIMP-ANNOT-FIND-PARAMS-UNDOCUMENTED — verifies that every {@code @QueryParam}
   * on {@link SemanticAnnotationV2Rest#find} carries a non-blank {@code @Parameter}
   * annotation so the generated OpenAPI schema documents all four params.
   */
  @Test
  void findAnnotations_q_paramIsRequired() throws NoSuchMethodException {
    var param = findFindParam("q");
    assertThat(param).as("q must carry @QueryParam on find()").isNotNull();
    var ann = param.getAnnotation(org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
    assertThat(ann).as("q must carry @Parameter").isNotNull();
    assertThat(ann.required()).as("q must be marked required=true").isTrue();
    assertThat(ann.description()).as("@Parameter.description for q must be non-blank").isNotBlank();
  }

  @Test
  void findAnnotations_vocabId_paramCarriesParameterAnnotation() throws NoSuchMethodException {
    var param = findFindParam("vocabId");
    assertThat(param).as("vocabId must carry @QueryParam on find()").isNotNull();
    var ann = param.getAnnotation(org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
    assertThat(ann).as("vocabId must carry @Parameter").isNotNull();
    assertThat(ann.description()).as("@Parameter.description for vocabId must be non-blank").isNotBlank();
  }

  @Test
  void listAnnotations_page_paramCarriesParameterAnnotation() throws NoSuchMethodException {
    var param = findListParam("page");
    assertThat(param).as("page must carry @QueryParam").isNotNull();
  }

  void findAnnotations_page_paramCarriesParameterAnnotation() throws NoSuchMethodException {
    var param = findFindParam("page");
    assertThat(param).as("page must carry @QueryParam on find()").isNotNull();
    var ann = param.getAnnotation(org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
    assertThat(ann).as("page must carry @Parameter").isNotNull();
    assertThat(ann.description()).as("@Parameter.description for page must be non-blank").isNotBlank();
  }

  @Test
  void listAnnotations_pageSize_paramCarriesParameterAnnotation() throws NoSuchMethodException {
    var param = findListParam("pageSize");
    assertThat(param).as("pageSize must carry @QueryParam").isNotNull();
  }

  void findAnnotations_pageSize_paramCarriesParameterAnnotation() throws NoSuchMethodException {
    var param = findFindParam("pageSize");
    assertThat(param).as("pageSize must carry @QueryParam on find()").isNotNull();
    var ann = param.getAnnotation(org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
    assertThat(ann).as("pageSize must carry @Parameter").isNotNull();
    assertThat(ann.description()).as("@Parameter.description for pageSize must be non-blank").isNotBlank();
  }

  private static java.lang.reflect.Parameter findListParam(String queryParamName)
      throws NoSuchMethodException {
    java.lang.reflect.Method method = SemanticAnnotationV2Rest.class.getMethod(
        "list", String.class, String.class, String.class, String.class,
        int.class, int.class, SecurityContext.class);
    return java.util.Arrays.stream(method.getParameters())
        .filter(p -> {
          var qp = p.getAnnotation(jakarta.ws.rs.QueryParam.class);
          return qp != null && queryParamName.equals(qp.value());
        })
        .findFirst()
        .orElse(null);
  }

  private static java.lang.reflect.Parameter findFindParam(String queryParamName)
      throws NoSuchMethodException {
    java.lang.reflect.Method method = SemanticAnnotationV2Rest.class.getMethod(
        "find", String.class, String.class, int.class, int.class,
        jakarta.ws.rs.core.SecurityContext.class);
    return java.util.Arrays.stream(method.getParameters())
        .filter(p -> {
          var qp = p.getAnnotation(jakarta.ws.rs.QueryParam.class);
          return qp != null && queryParamName.equals(qp.value());
        })
        .findFirst()
        .orElse(null);
  }

  // ─── helpers ─────────────────────────────────────────────────────────────

  private static SemanticAnnotation annotation(
    String appId, String subjectAppId, String subjectKind, String predicateIri, String value
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

  private static CreateAnnotationIO createBody(
    String subjectKind, String subjectAppId, String predicateIri,
    String objectLiteral, String objectIri
  ) {
    CreateAnnotationIO body = new CreateAnnotationIO();
    body.setSubjectKind(subjectKind);
    body.setSubjectAppId(subjectAppId);
    body.setPredicateIri(predicateIri);
    body.setObjectLiteral(objectLiteral);
    body.setObjectIri(objectIri);
    return body;
  }
}
