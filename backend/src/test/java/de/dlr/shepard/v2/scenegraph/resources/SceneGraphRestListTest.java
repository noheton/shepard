package de.dlr.shepard.v2.scenegraph.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.v2.scenegraph.io.SceneGraphListIO;
import de.dlr.shepard.v2.scenegraph.services.SceneGraphService;
import de.dlr.shepard.v2.scenegraph.services.SceneGraphService.SceneListPage;
import de.dlr.shepard.v2.scenegraph.services.SceneGraphService.SceneListRow;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * SCENEGRAPH-LIST-1 — unit tests for {@code GET /v2/scene-graphs}.
 *
 * <p>Focused on the new list endpoint added by SCENEGRAPH-LIST-1; the rest
 * of {@link SceneGraphRest} stays covered by the service-layer test suite
 * (everything material happens in {@link SceneGraphService}).
 */
class SceneGraphRestListTest {

  private SceneGraphRest resource;
  private SceneGraphService svc;

  @BeforeEach
  void setUp() {
    resource = new SceneGraphRest();
    svc = mock(SceneGraphService.class);
    resource.sceneGraphService = svc;
  }

  @Test
  void list_returns200_andEnvelopeShape() {
    var rows = List.of(
      new SceneListRow("a", "Scene A", "d", null, "f0", 100L, 200L, 3L, 2L),
      new SceneListRow("b", "Scene B", null, "src", null, null, null, 0L, 0L)
    );
    when(svc.listScenes(anyInt(), anyInt())).thenReturn(new SceneListPage(rows, 7L));

    Response r = resource.list(0, 50);
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

    Response r = resource.list(-3, 9999);
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

    Response r = resource.list(2, 25);
    assertThat(r.getStatus()).isEqualTo(200);
    SceneGraphListIO body = (SceneGraphListIO) r.getEntity();
    assertThat(body.getPage()).isEqualTo(2);
    assertThat(body.getSize()).isEqualTo(25);
  }
}
