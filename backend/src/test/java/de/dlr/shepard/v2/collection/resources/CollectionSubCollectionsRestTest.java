package de.dlr.shepard.v2.collection.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.v2.collection.daos.SubCollectionsDAO;
import de.dlr.shepard.v2.collection.io.SubCollectionEntryIO;
import de.dlr.shepard.v2.collection.io.SubCollectionsIO;
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
 * Unit tests for {@link CollectionSubCollectionsRest} (PROJ-REST-1).
 *
 * <p>Mock-based — no Quarkus boot. Covers:
 * <ul>
 *   <li>401 when unauthenticated</li>
 *   <li>404 when the parent Collection doesn't exist</li>
 *   <li>403 when the caller lacks Read permission</li>
 *   <li>200 with empty sub-collections (parent exists but no children)</li>
 *   <li>200 with one child, parentIsProject=true</li>
 *   <li>200 with child that belongs to multiple projects (alsoMemberOf
 *       populated)</li>
 *   <li>200 returns programmes list from parent annotations</li>
 * </ul>
 */
class CollectionSubCollectionsRestTest {

  static final String PARENT_APP_ID = "018f9c5a-7e26-7000-a000-000000000042";
  static final long   PARENT_OGM_ID = 42L;
  static final String CALLER        = "alice";
  static final String CHILD_APP_ID  = "018f9c5a-7e26-7000-a000-000000000099";
  static final String OTHER_PROJ_ID = "018f9c5a-7e26-7000-a000-000000000007";

  @Mock SubCollectionsDAO   subCollectionsDAO;
  @Mock PermissionsService  permissionsService;
  @Mock EntityIdResolver    entityIdResolver;
  @Mock CollectionService   collectionService;
  @Mock SecurityContext      sc;
  @Mock Principal            principal;

  CollectionSubCollectionsRest resource;

  @BeforeEach
  void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
    resource = new CollectionSubCollectionsRest();
    resource.subCollectionsDAO  = subCollectionsDAO;
    resource.permissionsService = permissionsService;
    resource.entityIdResolver   = entityIdResolver;
    resource.collectionService  = collectionService;

    when(sc.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
    when(entityIdResolver.resolveLong(PARENT_APP_ID)).thenReturn(PARENT_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(
      eq(PARENT_OGM_ID), eq(AccessType.Read), eq(CALLER), anyLong()))
      .thenReturn(true);
  }

  // ── 401 unauthenticated ────────────────────────────────────────────────────

  @Test
  void get_returns401WhenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);

    Response r = resource.get(PARENT_APP_ID, "trim", sc);

    assertThat(r.getStatus()).isEqualTo(401);
  }

  // ── 404 not found ──────────────────────────────────────────────────────────

  @Test
  void get_returns404WhenCollectionNotFound() {
    when(entityIdResolver.resolveLong(PARENT_APP_ID)).thenThrow(new NotFoundException());

    Response r = resource.get(PARENT_APP_ID, "trim", sc);

    assertThat(r.getStatus()).isEqualTo(404);
  }

  // ── 403 forbidden ─────────────────────────────────────────────────────────

  @Test
  void get_returns403WhenNoReadPermission() {
    when(permissionsService.isAccessTypeAllowedForUser(
      eq(PARENT_OGM_ID), eq(AccessType.Read), eq(CALLER), anyLong()))
      .thenReturn(false);

    Response r = resource.get(PARENT_APP_ID, "trim", sc);

    assertThat(r.getStatus()).isEqualTo(403);
  }

  // ── 200 empty ─────────────────────────────────────────────────────────────

  @Test
  void get_returns200WithEmptySubCollections() {
    when(subCollectionsDAO.findSubCollections(PARENT_APP_ID))
      .thenReturn(new SubCollectionsIO(PARENT_APP_ID, false, List.of(), List.of()));

    Response r = resource.get(PARENT_APP_ID, "trim", sc);

    assertThat(r.getStatus()).isEqualTo(200);
    SubCollectionsIO body = (SubCollectionsIO) r.getEntity();
    assertThat(body.getParentAppId()).isEqualTo(PARENT_APP_ID);
    assertThat(body.isParentIsProject()).isFalse();
    assertThat(body.getProgrammes()).isEmpty();
    assertThat(body.getSubCollections()).isEmpty();
  }

  // ── 200 with one child, parentIsProject=true ──────────────────────────────

  @Test
  void get_returns200WithOneChildAndIsProjectTrue() {
    SubCollectionEntryIO child = new SubCollectionEntryIO(
      CHILD_APP_ID, 99L, "LUMEN Campaign Q3", null, 15L, null, null, List.of()
    );
    when(subCollectionsDAO.findSubCollections(PARENT_APP_ID))
      .thenReturn(new SubCollectionsIO(
        PARENT_APP_ID, true, List.of("Clean Aviation JU"), List.of(child)
      ));

    Response r = resource.get(PARENT_APP_ID, "trim", sc);

    assertThat(r.getStatus()).isEqualTo(200);
    SubCollectionsIO body = (SubCollectionsIO) r.getEntity();
    assertThat(body.isParentIsProject()).isTrue();
    assertThat(body.getProgrammes()).containsExactly("Clean Aviation JU");
    assertThat(body.getSubCollections()).hasSize(1);

    SubCollectionEntryIO entry = body.getSubCollections().get(0);
    assertThat(entry.getAppId()).isEqualTo(CHILD_APP_ID);
    assertThat(entry.getId()).isEqualTo(99L);
    assertThat(entry.getName()).isEqualTo("LUMEN Campaign Q3");
    assertThat(entry.getDoCount()).isEqualTo(15L);
    assertThat(entry.getAlsoMemberOf()).isEmpty();
  }

  // ── 200 with child that belongs to multiple projects ──────────────────────

  @Test
  void get_returns200WithAlsoMemberOfPopulated() {
    SubCollectionEntryIO child = new SubCollectionEntryIO(
      CHILD_APP_ID, 99L, "Shared Campaign", null, 7L, null, null,
      List.of(OTHER_PROJ_ID)
    );
    when(subCollectionsDAO.findSubCollections(PARENT_APP_ID))
      .thenReturn(new SubCollectionsIO(
        PARENT_APP_ID, false, List.of(), List.of(child)
      ));

    Response r = resource.get(PARENT_APP_ID, "trim", sc);

    assertThat(r.getStatus()).isEqualTo(200);
    SubCollectionEntryIO entry = ((SubCollectionsIO) r.getEntity())
      .getSubCollections().get(0);
    assertThat(entry.getAlsoMemberOf()).containsExactly(OTHER_PROJ_ID);
  }

  // ── permission check uses the correct ogmId ───────────────────────────────

  @Test
  void get_usesParentOgmIdForPermissionCheck() {
    when(subCollectionsDAO.findSubCollections(PARENT_APP_ID))
      .thenReturn(new SubCollectionsIO(PARENT_APP_ID, false, List.of(), List.of()));

    resource.get(PARENT_APP_ID, "trim", sc);

    verify(permissionsService)
      .isAccessTypeAllowedForUser(eq(PARENT_OGM_ID), eq(AccessType.Read), eq(CALLER), anyLong());
  }

  // ── Cache-Control header ───────────────────────────────────────────────────

  @Test
  void get_setsShortCacheControlHeader() {
    when(subCollectionsDAO.findSubCollections(PARENT_APP_ID))
      .thenReturn(new SubCollectionsIO(PARENT_APP_ID, false, List.of(), List.of()));

    Response r = resource.get(PARENT_APP_ID, "trim", sc);

    assertThat(r.getHeaderString("Cache-Control")).isEqualTo("max-age=60, must-revalidate");
  }
}
