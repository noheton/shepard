package de.dlr.shepard.v2.fair.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.daos.CollectionPropertiesDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.v2.fair.io.MetadataCompletenessScoreIO;
import de.dlr.shepard.v2.fair.services.MetadataCompletenessService;
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
 * FAIR4 unit tests for {@link MetadataCompletenessRest}.
 *
 * <p>Mock-based, no Quarkus boot. Covers the standard 4-gate grid:
 * 401/403/404 auth guards + happy-path 200 with score body.
 *
 * <p>The {@link MetadataCompletenessService} is instantiated directly
 * (no mocking) so that we exercise the real scoring logic up to the
 * Neo4j boundary — count-based checks (semanticAnnotation, labJournal,
 * keywords) return 0 in the absence of a live session; that is the
 * expected fail-soft behaviour.
 */
class MetadataCompletenessRestTest {

  static final String COLL_APP_ID = "018f9c5a-7e26-7000-a000-000000000099";
  static final long COLL_OGM_ID = 77L;
  static final String CALLER = "alice";

  @Mock
  CollectionPropertiesDAO collectionPropertiesDAO;

  @Mock
  PermissionsService permissionsService;

  @Mock
  CollectionService collectionService;

  @Mock
  SecurityContext securityContext;

  @Mock
  Principal principal;

  // Use the real service — count queries fail-soft to 0 in test (no Neo4j)
  MetadataCompletenessService metadataCompletenessService = new MetadataCompletenessService();

  MetadataCompletenessRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new MetadataCompletenessRest();
    resource.collectionPropertiesDAO = collectionPropertiesDAO;
    resource.permissionsService = permissionsService;
    resource.collectionService = collectionService;
    resource.metadataCompletenessService = metadataCompletenessService;

    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
  }

  // ── Test 1: 401 unauthenticated ──────────────────────────────────────────────

  @Test
  void returnsUnauthorizedWhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);

    Response r = resource.getMetadataCompleteness(COLL_APP_ID, securityContext);

    assertEquals(401, r.getStatus());
    verify(collectionPropertiesDAO, never()).findCollectionIdByAppId(COLL_APP_ID);
  }

  // ── Test 2: 404 unknown collection ──────────────────────────────────────────

  @Test
  void returnsNotFoundWhenCollectionMissing() {
    when(collectionPropertiesDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.empty());

    Response r = resource.getMetadataCompleteness(COLL_APP_ID, securityContext);

    assertEquals(404, r.getStatus());
    verify(permissionsService, never()).isAccessTypeAllowedForUser(
      anyLong(), eq(AccessType.Read), eq(CALLER), anyLong()
    );
  }

  // ── Test 3: 403 no Read permission ──────────────────────────────────────────

  @Test
  void returnsForbiddenWhenCallerLacksReadPermission() {
    when(collectionPropertiesDAO.findCollectionIdByAppId(COLL_APP_ID))
      .thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(
      eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER), anyLong()
    )).thenReturn(false);

    Response r = resource.getMetadataCompleteness(COLL_APP_ID, securityContext);

    assertEquals(403, r.getStatus());
    verify(collectionService, never()).getCollectionWithDataObjectsAndIncomingReferences(anyLong());
  }

  // ── Test 4: 200 — full collection with static checks ────────────────────────

  @Test
  void returnsOkWithScoreBodyForFullCollection() {
    Collection coll = buildFullCollection();
    stubHappyPath(coll);

    Response r = resource.getMetadataCompleteness(COLL_APP_ID, securityContext);

    assertEquals(200, r.getStatus());
    assertNotNull(r.getEntity());
    MetadataCompletenessScoreIO io = (MetadataCompletenessScoreIO) r.getEntity();
    assertEquals(COLL_APP_ID, io.getCollectionAppId());
    assertEquals(100, io.getMaxScore());
    assertTrue(io.getScore() >= 0 && io.getScore() <= 100,
      "Score should be in range [0, 100]");
    assertEquals(9, io.getChecks().size(), "Should have exactly 9 checks");
  }

  // ── Test 5: 200 — static-field checks individually inspectable ──────────────

  @Test
  void staticFieldChecks_licenseAndNamePass() {
    Collection coll = buildFullCollection();
    stubHappyPath(coll);

    Response r = resource.getMetadataCompleteness(COLL_APP_ID, securityContext);
    MetadataCompletenessScoreIO io = (MetadataCompletenessScoreIO) r.getEntity();

    var licenseCheck = io.getChecks().stream()
      .filter(c -> "license".equals(c.getCheckId()))
      .findFirst().orElseThrow();
    var nameCheck = io.getChecks().stream()
      .filter(c -> "name".equals(c.getCheckId()))
      .findFirst().orElseThrow();

    assertTrue(licenseCheck.isPassed(), "license check should pass for CC-BY-4.0");
    assertTrue(nameCheck.isPassed(), "name check should pass for non-empty name");
  }

  // ── Test 6: 200 — missing license lowers score ──────────────────────────────

  @Test
  void missingLicense_checksShowLicenseAsFailed() {
    Collection coll = buildFullCollection();
    coll.setLicense(null);
    stubHappyPath(coll);

    Response r = resource.getMetadataCompleteness(COLL_APP_ID, securityContext);
    MetadataCompletenessScoreIO io = (MetadataCompletenessScoreIO) r.getEntity();

    var licenseCheck = io.getChecks().stream()
      .filter(c -> "license".equals(c.getCheckId()))
      .findFirst().orElseThrow();
    assertNotNull(licenseCheck.getHint(), "hint should not be null");
    assertTrue(!licenseCheck.isPassed(), "license check should fail when license is null");
  }

  // ── helpers ──────────────────────────────────────────────────────────────────

  private void stubHappyPath(Collection coll) {
    when(collectionPropertiesDAO.findCollectionIdByAppId(COLL_APP_ID))
      .thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(
      eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER), anyLong()
    )).thenReturn(true);
    when(collectionService.getCollectionWithDataObjectsAndIncomingReferences(COLL_OGM_ID))
      .thenReturn(coll);
  }

  private Collection buildFullCollection() {
    Collection coll = new Collection(COLL_OGM_ID);
    coll.setAppId(COLL_APP_ID);
    coll.setName("LUMEN Hotfire Campaign");
    coll.setDescription(
      "Synthetic showcase dataset for shepard. Hotfire test campaign of " +
      "the LUMEN demonstrator engine. NOT REAL DLR/LUMEN data. Full description here."
    );
    coll.setLicense("CC-BY-4.0");
    coll.setAccessRights("OPEN");
    coll.setCreatedByOrcid("0000-0001-2345-6789");

    DataObject dobj = new DataObject(101L);
    dobj.setAppId("018f9c5a-7e26-7000-a000-000000001001");
    dobj.setName("TR-001");
    coll.setDataObjects(List.of(dobj));

    return coll;
  }
}
