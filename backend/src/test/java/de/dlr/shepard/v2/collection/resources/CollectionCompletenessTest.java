package de.dlr.shepard.v2.collection.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.v2.annotations.daos.SemanticAnnotationV2DAO;
import de.dlr.shepard.v2.collection.io.CollectionCompletenessIO;
import de.dlr.shepard.v2.labjournal.daos.CollectionLabJournalEntriesDAO;
import jakarta.validation.Validator;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * RDM-005a(d) — unit tests for
 * {@code GET /v2/collections/{collectionAppId}/completeness}.
 *
 * <p>Mock-based, no Quarkus boot. Exercises the full 9-check scoring logic,
 * the three score bands, the access-gate (401/403/404), and the invariant
 * that the check list always has exactly 9 entries summing to 100 points.
 */
class CollectionCompletenessTest {

  static final String COLL_APP_ID = "018f9c5a-7e26-7000-a000-000000000010";
  static final long   COLL_OGM_ID = 42L;
  static final String CALLER      = "alice";

  @Mock CollectionService              collectionService;
  @Mock PermissionsService             permissionsService;
  @Mock EntityIdResolver               entityIdResolver;
  @Mock Validator                      validator;
  @Mock SemanticAnnotationV2DAO        semanticAnnotationV2DAO;
  @Mock CollectionLabJournalEntriesDAO collectionLabJournalEntriesDAO;
  @Mock SecurityContext                securityContext;
  @Mock Principal                      principal;

  CollectionV2Rest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new CollectionV2Rest();
    resource.collectionService              = collectionService;
    resource.permissionsService             = permissionsService;
    resource.entityIdResolver               = entityIdResolver;
    resource.validator                      = validator;
    resource.objectMapper                   = new ObjectMapper();
    resource.semanticAnnotationV2DAO        = semanticAnnotationV2DAO;
    resource.collectionLabJournalEntriesDAO = collectionLabJournalEntriesDAO;

    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
    when(validator.validate(any())).thenReturn(Collections.emptySet());

    // Default: appId resolves, caller has Read permission.
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER), anyLong()))
      .thenReturn(true);

    // Default: no annotations, no lab journal entries.
    when(semanticAnnotationV2DAO.countBySubjectAppId(any())).thenReturn(0L);
    when(collectionLabJournalEntriesDAO.findByCollectionAppId(any())).thenReturn(List.of());
  }

  // ── access gates ─────────────────────────────────────────────────────────

  @Test
  void returns404WhenAppIdUnknown() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenThrow(new NotFoundException());
    Response r = resource.getCompleteness(COLL_APP_ID, securityContext);
    assertEquals(404, r.getStatus());
  }

  @Test
  void returns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = resource.getCompleteness(COLL_APP_ID, securityContext);
    assertEquals(401, r.getStatus());
  }

  @Test
  void returns403WhenNoReadPermission() {
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER), anyLong()))
      .thenReturn(false);
    Response r = resource.getCompleteness(COLL_APP_ID, securityContext);
    assertEquals(403, r.getStatus());
  }

  // ── check structure invariant ─────────────────────────────────────────────

  @Test
  void checksListHasExactlyNineEntries() {
    when(collectionService.getCollectionWithDataObjectsAndIncomingReferences(COLL_OGM_ID))
      .thenReturn(emptyCollection());

    Response r = resource.getCompleteness(COLL_APP_ID, securityContext);
    assertEquals(200, r.getStatus());

    CollectionCompletenessIO body = (CollectionCompletenessIO) r.getEntity();
    assertNotNull(body);
    assertNotNull(body.getChecks());
    assertEquals(9, body.getChecks().size(), "check list must have exactly 9 entries");
  }

  @Test
  void maxScoreIsAlways100() {
    when(collectionService.getCollectionWithDataObjectsAndIncomingReferences(COLL_OGM_ID))
      .thenReturn(emptyCollection());

    Response r = resource.getCompleteness(COLL_APP_ID, securityContext);
    CollectionCompletenessIO body = (CollectionCompletenessIO) r.getEntity();
    assertEquals(100, body.getMaxScore(), "sum of all check points must equal 100");
  }

  @Test
  void checkPointsSumToMaxScore() {
    when(collectionService.getCollectionWithDataObjectsAndIncomingReferences(COLL_OGM_ID))
      .thenReturn(emptyCollection());

    Response r = resource.getCompleteness(COLL_APP_ID, securityContext);
    CollectionCompletenessIO body = (CollectionCompletenessIO) r.getEntity();
    int sumOfPoints = body.getChecks().stream().mapToInt(c -> c.getPoints()).sum();
    assertEquals(body.getMaxScore(), sumOfPoints, "sum of check.points must equal maxScore");
  }

  // ── score bands ───────────────────────────────────────────────────────────

  @Test
  void collectionWithOnlyNameScoresErrorBand() {
    // name (10) only → score = 10 → band = "error" (< 50)
    Collection c = emptyCollection();
    c.setName("TR-004");
    when(collectionService.getCollectionWithDataObjectsAndIncomingReferences(COLL_OGM_ID)).thenReturn(c);

    Response r = resource.getCompleteness(COLL_APP_ID, securityContext);
    CollectionCompletenessIO body = (CollectionCompletenessIO) r.getEntity();
    assertEquals(10, body.getScore());
    assertEquals("error", body.getBand());
  }

  @Test
  void collectionWithAllFieldsScoresSuccessBand() {
    // name(10) + description(15) + license(20) + accessRights(10) + creatorOrcid(10)
    // + semanticAnnotation(10) + labJournal(5) + dataObjects(15) = 95
    // keywords always 0 (conservative); total = 95 → "success" (≥ 80)
    Collection c = fullyPopulatedCollection();
    when(collectionService.getCollectionWithDataObjectsAndIncomingReferences(COLL_OGM_ID)).thenReturn(c);
    when(semanticAnnotationV2DAO.countBySubjectAppId(COLL_APP_ID)).thenReturn(2L);
    when(collectionLabJournalEntriesDAO.findByCollectionAppId(COLL_APP_ID))
      .thenReturn(List.of(new de.dlr.shepard.context.labJournal.entities.LabJournalEntry()));

    Response r = resource.getCompleteness(COLL_APP_ID, securityContext);
    CollectionCompletenessIO body = (CollectionCompletenessIO) r.getEntity();
    // keywords always false → 95 (not 100)
    assertEquals(95, body.getScore());
    assertEquals("success", body.getBand());
  }

  @Test
  void collectionWithMidRangeScoreScoresWarningBand() {
    // name(10) + description(15) + accessRights(10) + dataObjects(15) = 50 → "warning"
    Collection c = emptyCollection();
    c.setName("TR-001");
    c.setDescription("A".repeat(50));
    c.setAccessRights("OPEN");
    c.getDataObjects().add(new de.dlr.shepard.context.collection.entities.DataObject());
    when(collectionService.getCollectionWithDataObjectsAndIncomingReferences(COLL_OGM_ID)).thenReturn(c);

    Response r = resource.getCompleteness(COLL_APP_ID, securityContext);
    CollectionCompletenessIO body = (CollectionCompletenessIO) r.getEntity();
    assertEquals(50, body.getScore());
    assertEquals("warning", body.getBand());
  }

  // ── per-check pass/fail ───────────────────────────────────────────────────

  @Test
  void descriptionMustBeAtLeast50CharsToPpass() {
    Collection shortDesc = emptyCollection();
    shortDesc.setName("x");
    shortDesc.setDescription("A".repeat(49)); // 49 < 50

    Collection longDesc = emptyCollection();
    longDesc.setName("x");
    longDesc.setDescription("A".repeat(50)); // exactly 50

    when(collectionService.getCollectionWithDataObjectsAndIncomingReferences(COLL_OGM_ID)).thenReturn(shortDesc);
    CollectionCompletenessIO shortResult = (CollectionCompletenessIO)
      resource.getCompleteness(COLL_APP_ID, securityContext).getEntity();
    boolean shortPassed = shortResult.getChecks().stream()
      .filter(c -> "description".equals(c.getId())).findFirst().get().isPassed();
    assertEquals(false, shortPassed, "49-char description must fail");

    when(collectionService.getCollectionWithDataObjectsAndIncomingReferences(COLL_OGM_ID)).thenReturn(longDesc);
    CollectionCompletenessIO longResult = (CollectionCompletenessIO)
      resource.getCompleteness(COLL_APP_ID, securityContext).getEntity();
    boolean longPassed = longResult.getChecks().stream()
      .filter(c -> "description".equals(c.getId())).findFirst().get().isPassed();
    assertEquals(true, longPassed, "50-char description must pass");
  }

  @Test
  void keywordsCheckAlwaysFails() {
    // keywords is conservatively 0 server-side (matches the client widget behaviour)
    when(collectionService.getCollectionWithDataObjectsAndIncomingReferences(COLL_OGM_ID))
      .thenReturn(fullyPopulatedCollection());
    when(semanticAnnotationV2DAO.countBySubjectAppId(any())).thenReturn(99L);

    CollectionCompletenessIO body = (CollectionCompletenessIO)
      resource.getCompleteness(COLL_APP_ID, securityContext).getEntity();
    boolean keywordsPassed = body.getChecks().stream()
      .filter(c -> "keywords".equals(c.getId())).findFirst().get().isPassed();
    assertEquals(false, keywordsPassed, "keywords check must always fail server-side (conservative)");
  }

  @Test
  void semanticAnnotationPassesWhenCountPositive() {
    Collection c = emptyCollection();
    c.setName("x");
    when(collectionService.getCollectionWithDataObjectsAndIncomingReferences(COLL_OGM_ID)).thenReturn(c);
    when(semanticAnnotationV2DAO.countBySubjectAppId(any())).thenReturn(1L);

    CollectionCompletenessIO body = (CollectionCompletenessIO)
      resource.getCompleteness(COLL_APP_ID, securityContext).getEntity();
    boolean annPassed = body.getChecks().stream()
      .filter(ch -> "semanticAnnotation".equals(ch.getId())).findFirst().get().isPassed();
    assertEquals(true, annPassed);
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  private Collection emptyCollection() {
    Collection c = new Collection();
    c.setShepardId(COLL_OGM_ID);
    c.setAppId(COLL_APP_ID);
    return c;
  }

  private Collection fullyPopulatedCollection() {
    Collection c = emptyCollection();
    c.setName("LUMEN Campaign Q3");
    c.setDescription("A".repeat(50));
    c.setLicense("CC-BY-4.0");
    c.setAccessRights("OPEN");
    c.setCreatedByOrcid("0000-0001-2345-6789");
    c.getDataObjects().add(new de.dlr.shepard.context.collection.entities.DataObject());
    return c;
  }
}
