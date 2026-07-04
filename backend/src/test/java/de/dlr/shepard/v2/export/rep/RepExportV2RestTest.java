package de.dlr.shepard.v2.export.rep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.daos.CollectionPropertiesDAO;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link RepExportV2Rest} covering the
 * <b>MCP-PERMS-AUDIT-2 REST flip</b> (Read → Write on the POST build
 * endpoint, 2026-05-31).
 *
 * <p>The MCP {@code rep_export} tool already gates Write because REP build
 * records a Collection-scoped PROV-O Activity. This test pins the REST
 * gate so the two surfaces stay aligned.
 *
 * <p>Pure Mockito; no Quarkus container.
 */
class RepExportV2RestTest {

  static final String COLL_APP_ID = "018f9c5a-7e26-7000-a000-000000000042";
  static final long   COLL_OGM_ID = 42L;
  static final String CALLER      = "alice";

  @Mock CollectionPropertiesDAO collectionPropertiesDAO;
  @Mock PermissionsService permissionsService;
  @Mock RepExportService repExportService;
  @Mock SecurityContext sc;
  @Mock Principal principal;

  RepExportV2Rest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new RepExportV2Rest();
    resource.collectionPropertiesDAO = collectionPropertiesDAO;
    resource.permissionsService = permissionsService;
    resource.repExportService = repExportService;

    when(sc.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
    when(collectionPropertiesDAO.findCollectionIdByAppId(COLL_APP_ID))
      .thenReturn(Optional.of(COLL_OGM_ID));
    // Default: caller has Write (and therefore Read).
    when(permissionsService.isAccessTypeAllowedForUser(
        eq(COLL_OGM_ID), eq(AccessType.Write), eq(CALLER), anyLong()))
      .thenReturn(true);
    when(permissionsService.isAccessTypeAllowedForUser(
        eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER), anyLong()))
      .thenReturn(true);
  }

  // ── POST build — gate flipped to Write ─────────────────────────────────

  @Test
  void buildRepExport_returns401WhenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);
    assertThat(resource.buildRepExport(COLL_APP_ID, sc).getStatus()).isEqualTo(401);
  }

  @Test
  void buildRepExport_returns404WhenCollectionMissing() {
    when(collectionPropertiesDAO.findCollectionIdByAppId(COLL_APP_ID))
      .thenReturn(Optional.empty());
    assertThat(resource.buildRepExport(COLL_APP_ID, sc).getStatus()).isEqualTo(404);
  }

  /**
   * MCP-PERMS-AUDIT-2 regression-guard: a caller with Read-only permission
   * must NOT be able to trigger a REP build. Pre-flip this returned 200; the
   * MCP rep_export tool already enforced Write, leaving the two surfaces
   * divergent. The fix is to flip REST to Write so they match.
   */
  @Test
  void buildRepExport_returns403WhenCallerOnlyHasRead() {
    when(permissionsService.isAccessTypeAllowedForUser(
        eq(COLL_OGM_ID), eq(AccessType.Write), eq(CALLER), anyLong()))
      .thenReturn(false);
    // Read access alone must be insufficient for a build that records an Activity.
    assertThat(resource.buildRepExport(COLL_APP_ID, sc).getStatus()).isEqualTo(403);
  }

  @Test
  void buildRepExport_returns200WhenCallerHasWrite() {
    when(repExportService.buildExport(eq(COLL_APP_ID), eq(CALLER)))
      .thenReturn(new RepExportIO());
    var r = resource.buildRepExport(COLL_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    verify(repExportService).buildExport(COLL_APP_ID, CALLER);
  }

  @Test
  void buildRepExport_gateUsesWriteAccessType() {
    when(repExportService.buildExport(eq(COLL_APP_ID), eq(CALLER)))
      .thenReturn(new RepExportIO());
    resource.buildRepExport(COLL_APP_ID, sc);
    // Pin the specific AccessType.Write — a future ad-hoc revert to Read
    // would break this assertion and the MCP-PERMS-AUDIT-2 alignment.
    verify(permissionsService).isAccessTypeAllowedForUser(
      eq(COLL_OGM_ID), eq(AccessType.Write), eq(CALLER), anyLong());
  }

  // ── GET latest — stays on Read ─────────────────────────────────────────

  @Test
  void getLatestRepExport_returns401WhenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);
    assertThat(resource.getLatestRepExport(COLL_APP_ID, sc).getStatus()).isEqualTo(401);
  }

  @Test
  void getLatestRepExport_gateUsesReadAccessType() {
    // The GET endpoint throws NotFoundException for TPL14b — we only care
    // about the gate. Suppress the throw by catching it.
    try {
      resource.getLatestRepExport(COLL_APP_ID, sc);
    } catch (jakarta.ws.rs.NotFoundException expected) {
      // expected — TPL14b persistence not yet implemented
    }
    verify(permissionsService).isAccessTypeAllowedForUser(
      eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER), anyLong());
  }
}
