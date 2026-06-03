package de.dlr.shepard.v2.annotations.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.semantic.entities.SemanticConfig;
import de.dlr.shepard.context.semantic.services.OntologyConfigService;
import de.dlr.shepard.provenance.services.ProvenanceService;
import de.dlr.shepard.v2.annotations.daos.SemanticAnnotationV2DAO;
import de.dlr.shepard.v2.annotations.io.BulkAnnotationEntryIO;
import de.dlr.shepard.v2.annotations.io.BulkCreateAnnotationsIO;
import de.dlr.shepard.v2.annotations.io.BulkCreateAnnotationsResultIO;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * SEMANTIC-ANNOTATE-BULK-REST-1 — unit tests for the
 * {@code POST /v2/annotations/bulk} endpoint added to
 * {@link SemanticAnnotationV2Rest}.
 *
 * <p>Uses the same Mockito field-injection base pattern as
 * {@link SemanticAnnotationV2RestTest}. No CDI context is started.
 */
class SemanticAnnotationBulkRestTest {

  static final String SUBJ_APP_ID_1 = "do-bulk-001";
  static final String SUBJ_APP_ID_2 = "do-bulk-002";
  static final String PREDICATE_IRI = "http://shepard.dlr.de/v/material";
  static final String CALLER = "alice";

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
    resource.requestContext = requestContext;

    when(sc.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);

    // Default: caller has Read+Write on both subjects
    when(permissionsService.isAccessAllowedForDataObjectAppId(anyString(), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(true);
    when(permissionsService.isAccessAllowedForDataObjectAppId(anyString(), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(true);

    // Default policy singleton (null = 'author-or-manager' default)
    when(ontologyConfigService.loadSingleton()).thenReturn(new SemanticConfig());

    // Provenance is a no-op by default
    when(provenanceService.record(anyString(), anyString(), any(), anyString(),
        anyString(), anyString(), anyString(), anyInt(), anyLong(), anyLong()))
      .thenReturn(null);
  }

  // ─── success path ─────────────────────────────────────────────────────────

  /**
   * POST with 2 valid entries → 200, created=2, failed=0, errors empty.
   */
  @Test
  void bulkCreate_twoValidEntries_returns200WithCreated2() {
    List<BulkAnnotationEntryIO> entries = List.of(
      new BulkAnnotationEntryIO(SUBJ_APP_ID_1, null, PREDICATE_IRI, "CF/LMPAEK"),
      new BulkAnnotationEntryIO(SUBJ_APP_ID_2, null, PREDICATE_IRI, "Ti-6Al-4V")
    );
    BulkCreateAnnotationsIO body = new BulkCreateAnnotationsIO(entries);

    Response r = resource.bulkCreate(body, sc);

    assertThat(r.getStatus()).isEqualTo(200);
    BulkCreateAnnotationsResultIO result = (BulkCreateAnnotationsResultIO) r.getEntity();
    assertThat(result.created()).isEqualTo(2);
    assertThat(result.failed()).isEqualTo(0);
    assertThat(result.errors()).isEmpty();
  }

  // ─── structural validation ─────────────────────────────────────────────────

  /**
   * POST with empty entries list → 400.
   */
  @Test
  void bulkCreate_emptyEntries_returns400() {
    BulkCreateAnnotationsIO body = new BulkCreateAnnotationsIO(List.of());

    Response r = resource.bulkCreate(body, sc);

    assertThat(r.getStatus()).isEqualTo(400);
  }

  /**
   * POST with 101 entries (> MAX_BULK_ENTRIES = 100) → 400.
   */
  @Test
  void bulkCreate_tooManyEntries_returns400() {
    List<BulkAnnotationEntryIO> entries = new ArrayList<>();
    for (int i = 0; i < 101; i++) {
      entries.add(new BulkAnnotationEntryIO("do-" + i, null, PREDICATE_IRI, "value-" + i));
    }
    BulkCreateAnnotationsIO body = new BulkCreateAnnotationsIO(entries);

    Response r = resource.bulkCreate(body, sc);

    assertThat(r.getStatus()).isEqualTo(400);
  }

  /**
   * POST with null body → 400.
   */
  @Test
  void bulkCreate_nullBody_returns400() {
    Response r = resource.bulkCreate(null, sc);

    assertThat(r.getStatus()).isEqualTo(400);
  }

  /**
   * Unauthenticated caller → 401.
   */
  @Test
  void bulkCreate_unauthenticated_returns401() {
    when(sc.getUserPrincipal()).thenReturn(null);
    BulkCreateAnnotationsIO body = new BulkCreateAnnotationsIO(
      List.of(new BulkAnnotationEntryIO(SUBJ_APP_ID_1, null, PREDICATE_IRI, "v"))
    );

    Response r = resource.bulkCreate(body, sc);

    assertThat(r.getStatus()).isEqualTo(401);
  }

  // ─── per-row error isolation ──────────────────────────────────────────────

  /**
   * When one entry lacks permission the other entries still succeed,
   * and the failed entry is reported in errors[].
   */
  @Test
  void bulkCreate_oneEntryForbidden_otherEntrySucceeds() {
    // SUBJ_APP_ID_1 — no Write permission → will fail
    when(permissionsService.isAccessAllowedForDataObjectAppId(
        eq(SUBJ_APP_ID_1), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(false);
    // SUBJ_APP_ID_2 — Write permission granted → will succeed

    List<BulkAnnotationEntryIO> entries = List.of(
      new BulkAnnotationEntryIO(SUBJ_APP_ID_1, null, PREDICATE_IRI, "forbidden-value"),
      new BulkAnnotationEntryIO(SUBJ_APP_ID_2, null, PREDICATE_IRI, "allowed-value")
    );
    BulkCreateAnnotationsIO body = new BulkCreateAnnotationsIO(entries);

    Response r = resource.bulkCreate(body, sc);

    assertThat(r.getStatus()).isEqualTo(200);
    BulkCreateAnnotationsResultIO result = (BulkCreateAnnotationsResultIO) r.getEntity();
    assertThat(result.created()).isEqualTo(1);
    assertThat(result.failed()).isEqualTo(1);
    assertThat(result.errors()).hasSize(1);
    assertThat(result.errors().get(0).index()).isEqualTo(0);
    assertThat(result.errors().get(0).dataObjectAppId()).isEqualTo(SUBJ_APP_ID_1);
  }

  /**
   * POST with exactly MAX_BULK_ENTRIES (100) valid entries → 200.
   */
  @Test
  void bulkCreate_exactlyMaxEntries_returns200() {
    List<BulkAnnotationEntryIO> entries = new ArrayList<>();
    for (int i = 0; i < SemanticAnnotationV2Rest.MAX_BULK_ENTRIES; i++) {
      entries.add(new BulkAnnotationEntryIO("do-" + i, null, PREDICATE_IRI, "v-" + i));
    }
    BulkCreateAnnotationsIO body = new BulkCreateAnnotationsIO(entries);

    Response r = resource.bulkCreate(body, sc);

    assertThat(r.getStatus()).isEqualTo(200);
    BulkCreateAnnotationsResultIO result = (BulkCreateAnnotationsResultIO) r.getEntity();
    assertThat(result.created()).isEqualTo(100);
    assertThat(result.failed()).isEqualTo(0);
  }
}
