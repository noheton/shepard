package de.dlr.shepard.v2.scenegraph.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.v2.scenegraph.io.SceneGraphListIO;
import de.dlr.shepard.v2.scenegraph.services.SceneGraphPermissionService;
import de.dlr.shepard.v2.scenegraph.services.SceneGraphService;
import de.dlr.shepard.v2.scenegraph.services.SceneGraphService.SceneListPage;
import de.dlr.shepard.v2.scenegraph.services.SceneGraphService.SceneListRow;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * SCENEGRAPH-LIST-1 + SCENEGRAPH-PERMS-1 — unit tests for {@code GET /v2/scene-graphs}.
 *
 * <p>Covers the list shape from SCENEGRAPH-LIST-1 plus the per-scene
 * permission filtering added by SCENEGRAPH-PERMS-1 (2026-05-31).
 */
class SceneGraphRestListTest {

  private SceneGraphRest resource;
  private SceneGraphService svc;
  private SceneGraphPermissionService perms;
  private SecurityContext sc;
  private Principal principal;

  @BeforeEach
  void setUp() {
    resource = new SceneGraphRest();
    svc = mock(SceneGraphService.class);
    perms = mock(SceneGraphPermissionService.class);
    sc = mock(SecurityContext.class);
    principal = mock(Principal.class);

    resource.sceneGraphService = svc;
    resource.scenePermissions = perms;

    when(sc.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn("alice");
    // Default: every row passes the permission filter unless a test overrides.
    when(perms.isAllowed(anyString(), any(AccessType.class), anyString(), anyBoolean()))
      .thenReturn(true);
  }

  @Test
  void list_returns200_andEnvelopeShape() {
    var rows = List.of(
      new SceneListRow("a", "Scene A", "d", null, "f0", 100L, 200L, 3L, 2L),
      new SceneListRow("b", "Scene B", null, "src", null, null, null, 0L, 0L)
    );
    when(svc.listScenes(anyInt(), anyInt())).thenReturn(new SceneListPage(rows, 7L));

    Response r = resource.list(0, 50, sc);
    assertThat(r.getStatus()).isEqualTo(200);

    SceneGraphListIO body = (SceneGraphListIO) r.getEntity();
    assertThat(body.getItems()).hasSize(2);
    assertThat(body.getTotal()).isEqualTo(7L);
    assertThat(body.getPage()).isEqualTo(0);
    assertThat(body.getSize()).isEqualTo(50);
    assertThat(body.getItems().get(0).getAppId()).isEqualTo("a");
    assertThat(body.getItems().get(0).getFrameCount()).isEqualTo(3L);
    assertThat(body.getItems().get(0).getJointCount()).isEqualTo(2L);
    assertThat(body.getItems().get(0).getCreatedAt()).isEqualTo(100L);
    assertThat(body.getItems().get(0).getUpdatedAt()).isEqualTo(200L);
    assertThat(body.getItems().get(1).getCreatedAt()).isNull();
    assertThat(body.getItems().get(1).getUpdatedAt()).isNull();
  }

  @Test
  void list_clampsPageToZero_andSizeIntoSafeRange() {
    when(svc.listScenes(anyInt(), anyInt())).thenReturn(new SceneListPage(List.of(), 0L));

    Response r = resource.list(-3, 9999, sc);
    assertThat(r.getStatus()).isEqualTo(200);

    ArgumentCaptor<Integer> pageCap = ArgumentCaptor.forClass(Integer.class);
    ArgumentCaptor<Integer> sizeCap = ArgumentCaptor.forClass(Integer.class);
    verify(svc).listScenes(pageCap.capture(), sizeCap.capture());
    assertThat(pageCap.getValue()).isEqualTo(0);
    assertThat(sizeCap.getValue()).isEqualTo(200);

    SceneGraphListIO body = (SceneGraphListIO) r.getEntity();
    assertThat(body.getPage()).isEqualTo(0);
    assertThat(body.getSize()).isEqualTo(200);
  }

  @Test
  void list_acceptsValidPagination_andEchoesPageSize() {
    when(svc.listScenes(anyInt(), anyInt())).thenReturn(new SceneListPage(List.of(), 0L));

    Response r = resource.list(2, 25, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    SceneGraphListIO body = (SceneGraphListIO) r.getEntity();
    assertThat(body.getPage()).isEqualTo(2);
    assertThat(body.getSize()).isEqualTo(25);
  }

  @Test
  void list_returns401_whenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);
    Response r = resource.list(0, 50, sc);
    assertThat(r.getStatus()).isEqualTo(401);
  }

  @Test
  void list_filtersRows_byPermission() {
    // SCENEGRAPH-PERMS-1: scene "a" readable, scene "b" not readable.
    var rows = List.of(
      new SceneListRow("a", "Scene A", null, null, null, 100L, 200L, 0L, 0L),
      new SceneListRow("b", "Scene B", null, null, null, 100L, 200L, 0L, 0L)
    );
    when(svc.listScenes(anyInt(), anyInt())).thenReturn(new SceneListPage(rows, 2L));
    when(perms.isAllowed(eq("a"), any(AccessType.class), anyString(), anyBoolean())).thenReturn(true);
    when(perms.isAllowed(eq("b"), any(AccessType.class), anyString(), anyBoolean())).thenReturn(false);

    Response r = resource.list(0, 50, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    SceneGraphListIO body = (SceneGraphListIO) r.getEntity();
    assertThat(body.getItems()).hasSize(1);
    assertThat(body.getItems().get(0).getAppId()).isEqualTo("a");
    // Total still reflects the unfiltered universe per the class Javadoc.
    assertThat(body.getTotal()).isEqualTo(2L);
  }
}
