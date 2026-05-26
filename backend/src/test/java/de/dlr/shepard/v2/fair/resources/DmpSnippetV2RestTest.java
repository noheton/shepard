package de.dlr.shepard.v2.fair.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import de.dlr.shepard.v2.fair.io.DmpSnippetIO;
import de.dlr.shepard.v2.fair.services.DmpSnippetService;
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
 * FAIR7 unit tests for {@link DmpSnippetV2Rest}.
 *
 * <p>Mock-based, no Quarkus boot. Covers the 8-test grid required by the
 * FAIR7 spec: 401/404/403 auth guards, happy-path markdown + JSON variants,
 * missing-field detection, and the {@code missingFields} array on the JSON
 * envelope.
 */
class DmpSnippetV2RestTest {

  static final String COLL_APP_ID = "018f9c5a-7e26-7000-a000-000000000099";
  static final long COLL_OGM_ID = 77L;
  static final String CALLER = "bob";

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

  // DmpSnippetService is a pure stateless bean — use the real impl, no mock
  DmpSnippetService dmpSnippetService = new DmpSnippetService();

  DmpSnippetV2Rest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new DmpSnippetV2Rest();
    resource.collectionPropertiesDAO = collectionPropertiesDAO;
    resource.permissionsService = permissionsService;
    resource.collectionService = collectionService;
    resource.dmpSnippetService = dmpSnippetService;

    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
  }

  // ── Test 1: 401 unauthenticated ──────────────────────────────────────────

  @Test
  void returnsUnauthorizedWhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);

    Response r = resource.getDmpSnippet(COLL_APP_ID, "text/markdown", securityContext);

    assertEquals(401, r.getStatus());
    verify(collectionPropertiesDAO, never()).findCollectionIdByAppId(COLL_APP_ID);
  }

  // ── Test 2: 404 unknown collection ──────────────────────────────────────

  @Test
  void returnsNotFoundWhenCollectionMissing() {
    when(collectionPropertiesDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.empty());

    Response r = resource.getDmpSnippet(COLL_APP_ID, "text/markdown", securityContext);

    assertEquals(404, r.getStatus());
    verify(permissionsService, never()).isAccessTypeAllowedForUser(anyLong(), eq(AccessType.Read), eq(CALLER), anyLong());
  }

  // ── Test 3: 403 no Read permission ──────────────────────────────────────

  @Test
  void returnsForbiddenWhenCallerLacksReadPermission() {
    when(collectionPropertiesDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER), anyLong()))
      .thenReturn(false);

    Response r = resource.getDmpSnippet(COLL_APP_ID, "text/markdown", securityContext);

    assertEquals(403, r.getStatus());
    verify(collectionService, never()).getCollectionWithDataObjectsAndIncomingReferences(anyLong());
  }

  // ── Test 4: 200 full snippet — all fields present ───────────────────────

  @Test
  void returnsMarkdownSnippetWithAllFieldsPresent() {
    Collection coll = buildFullCollection();
    stubHappyPath(coll);

    Response r = resource.getDmpSnippet(COLL_APP_ID, null, securityContext);

    assertEquals(200, r.getStatus());
    String body = (String) r.getEntity();
    assertNotNull(body);
    assertTrue(body.contains("## Data Management Plan"), "snippet must start with DMP heading");
    assertTrue(body.contains("CC-BY-4.0"), "snippet must include license");
    assertTrue(body.contains("OPEN"), "snippet must include access rights");
    assertTrue(body.contains("0000-0001-2345-6789"), "snippet must include ORCID");
  }

  // ── Test 5: 200 — snippet notes missing license ─────────────────────────

  @Test
  void markdownSnippetNotesMissingLicense() {
    Collection coll = buildFullCollection();
    coll.setLicense(null);
    stubHappyPath(coll);

    Response r = resource.getDmpSnippet(COLL_APP_ID, null, securityContext);

    assertEquals(200, r.getStatus());
    String body = (String) r.getEntity();
    assertTrue(body.contains("No license has been assigned"), "snippet must note missing license");
  }

  // ── Test 6: 200 — snippet notes missing ORCID ───────────────────────────

  @Test
  void markdownSnippetNotesMissingOrcid() {
    Collection coll = buildFullCollection();
    // Clear ORCIDs on all DataObjects
    for (DataObject dobj : coll.getDataObjects()) {
      dobj.setCreatedByOrcid(null);
    }
    stubHappyPath(coll);

    Response r = resource.getDmpSnippet(COLL_APP_ID, null, securityContext);

    assertEquals(200, r.getStatus());
    String body = (String) r.getEntity();
    assertTrue(body.contains("not specified"), "snippet must note missing ORCIDs");
  }

  // ── Test 7: 200 — JSON variant via Accept: application/json ─────────────

  @Test
  void returnsJsonVariantWhenAcceptIsJson() {
    Collection coll = buildFullCollection();
    stubHappyPath(coll);

    Response r = resource.getDmpSnippet(COLL_APP_ID, "application/json", securityContext);

    assertEquals(200, r.getStatus());
    assertNotNull(r.getEntity());
    DmpSnippetIO io = (DmpSnippetIO) r.getEntity();
    assertEquals(COLL_APP_ID, io.getCollectionAppId());
    assertEquals("LUMEN Hotfire Campaign", io.getCollectionName());
    assertNotNull(io.getSnippet());
    assertNotNull(io.getMissingFields());
    assertTrue(io.getSnippet().contains("## Data Management Plan"), "JSON snippet must contain Markdown heading");
  }

  // ── Test 8: missingFields contains "license" when unset ─────────────────

  @Test
  void missingFieldsContainsLicenseWhenUnset() {
    Collection coll = buildFullCollection();
    coll.setLicense(null);
    stubHappyPath(coll);

    Response r = resource.getDmpSnippet(COLL_APP_ID, "application/json", securityContext);

    assertEquals(200, r.getStatus());
    DmpSnippetIO io = (DmpSnippetIO) r.getEntity();
    assertTrue(io.getMissingFields().contains("license"), "missingFields must contain 'license' when unset");
    assertFalse(io.getMissingFields().contains("description"), "missingFields must not contain 'description' when set");
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  private void stubHappyPath(Collection coll) {
    when(collectionPropertiesDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER), anyLong()))
      .thenReturn(true);
    when(collectionService.getCollectionWithDataObjectsAndIncomingReferences(COLL_OGM_ID))
      .thenReturn(coll);
  }

  /**
   * Build a Collection with all FAIR fields populated and one DataObject
   * carrying an ORCID and an embargo end-date.
   */
  private Collection buildFullCollection() {
    Collection coll = new Collection(COLL_OGM_ID);
    coll.setAppId(COLL_APP_ID);
    coll.setName("LUMEN Hotfire Campaign");
    coll.setDescription("Synthetic hotfire test campaign data for DLR LUMEN engine tests.");
    coll.setLicense("CC-BY-4.0");
    coll.setAccessRights("OPEN");

    DataObject dobj = new DataObject(101L);
    dobj.setAppId("018f9c5a-7e26-7000-a000-000000001001");
    dobj.setName("TR-001");
    dobj.setCreatedByOrcid("0000-0001-2345-6789");
    dobj.setEmbargoEndDate("2027-12-31");
    coll.setDataObjects(List.of(dobj));

    return coll;
  }
}
