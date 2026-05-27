package de.dlr.shepard.v2.timeseriescontainer.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.semantic.daos.AnnotatableTimeseriesDAO;
import de.dlr.shepard.context.semantic.entities.AnnotatableTimeseries;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.data.timeseries.model.TimeseriesEntity;
import de.dlr.shepard.data.timeseries.repositories.TsChannelResolver;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.v2.timeseriescontainer.io.SpatialRolesIO;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for TS-AXIS-AUTO:
 * {@code GET /v2/timeseries-containers/{containerId}/channels/spatial-roles}.
 *
 * <p>Tests the role-map assembly logic: empty container, partial roles,
 * full 6DoF roles, and duplicate role handling (first-wins).
 */
public class SpatialRolesRestTest {

  private TimeseriesContainerChannelsRest resource;
  private TsChannelResolver resolverMock;
  private AnnotatableTimeseriesDAO annotatableTimeseriesDAOMock;
  private TimeseriesContainerService containerServiceMock;

  private static final long CONTAINER_ID = 99L;
  private static final UUID CHAN_X  = UUID.fromString("00000000-0000-4000-8000-000000000001");
  private static final UUID CHAN_Y  = UUID.fromString("00000000-0000-4000-8000-000000000002");
  private static final UUID CHAN_Z  = UUID.fromString("00000000-0000-4000-8000-000000000003");
  private static final UUID CHAN_RA = UUID.fromString("00000000-0000-4000-8000-000000000004");
  private static final UUID CHAN_RB = UUID.fromString("00000000-0000-4000-8000-000000000005");
  private static final UUID CHAN_RC = UUID.fromString("00000000-0000-4000-8000-000000000006");

  @BeforeEach
  void setUp() throws Exception {
    resource = new TimeseriesContainerChannelsRest();
    resolverMock                = mock(TsChannelResolver.class);
    annotatableTimeseriesDAOMock = mock(AnnotatableTimeseriesDAO.class);
    containerServiceMock        = mock(TimeseriesContainerService.class);

    inject(resource, "tsChannelResolver",        resolverMock);
    inject(resource, "annotatableTimeseriesDAO", annotatableTimeseriesDAOMock);
    inject(resource, "timeseriesContainerService", containerServiceMock);
    // timeseriesService not used by spatial-roles — leave null
  }

  private static void inject(Object target, String fieldName, Object value) throws Exception {
    Field f = target.getClass().getDeclaredField(fieldName);
    f.setAccessible(true);
    f.set(target, value);
  }

  // ── helper builders ────────────────────────────────────────────────────────

  private static TimeseriesEntity entityWithShepardId(UUID id) {
    var e = new TimeseriesEntity();
    e.setShepardId(id);
    return e;
  }

  private static AnnotatableTimeseries nodeWithRole(long containerId, UUID shepardId, String role) {
    var ann = new SemanticAnnotation();
    ann.setPropertyIRI(Constants.TS_AXIS_PREDICATE);
    ann.setValueIRI(role);

    var annotations = new ArrayList<SemanticAnnotation>();
    annotations.add(ann);
    var node = new AnnotatableTimeseries(containerId, 1, annotations);
    node.setAppId(shepardId.toString());
    return node;
  }

  // ── tests ──────────────────────────────────────────────────────────────────

  @Test
  void emptyContainer_returnsAllNull() {
    when(resolverMock.listPaged(CONTAINER_ID, 0, 500)).thenReturn(List.of());

    Response resp = resource.getSpatialRoles(CONTAINER_ID);

    assertEquals(200, resp.getStatus());
    SpatialRolesIO body = (SpatialRolesIO) resp.getEntity();
    assertNotNull(body);
    assertNull(body.x());
    assertNull(body.y());
    assertNull(body.z());
    assertNull(body.rot_a());
    assertNull(body.rot_b());
    assertNull(body.rot_c());
  }

  @Test
  void channelWithNoAnnotationNode_contributesNoRole() {
    var entity = entityWithShepardId(CHAN_X);
    when(resolverMock.listPaged(CONTAINER_ID, 0, 500)).thenReturn(List.of(entity));
    when(annotatableTimeseriesDAOMock.findByAppId(CHAN_X.toString())).thenReturn(Optional.empty());

    Response resp = resource.getSpatialRoles(CONTAINER_ID);

    SpatialRolesIO body = (SpatialRolesIO) resp.getEntity();
    assertNull(body.x());
  }

  @Test
  void singleAxisAnnotation_populatesCorrectRole() {
    var entity = entityWithShepardId(CHAN_X);
    when(resolverMock.listPaged(CONTAINER_ID, 0, 500)).thenReturn(List.of(entity));
    when(annotatableTimeseriesDAOMock.findByAppId(CHAN_X.toString()))
        .thenReturn(Optional.of(nodeWithRole(CONTAINER_ID, CHAN_X, "x")));

    Response resp = resource.getSpatialRoles(CONTAINER_ID);

    SpatialRolesIO body = (SpatialRolesIO) resp.getEntity();
    assertEquals(CHAN_X, body.x());
    assertNull(body.y());
    assertNull(body.z());
  }

  @Test
  void full6DoF_allRolesPopulated() {
    var ex = entityWithShepardId(CHAN_X);
    var ey = entityWithShepardId(CHAN_Y);
    var ez = entityWithShepardId(CHAN_Z);
    var era = entityWithShepardId(CHAN_RA);
    var erb = entityWithShepardId(CHAN_RB);
    var erc = entityWithShepardId(CHAN_RC);

    when(resolverMock.listPaged(CONTAINER_ID, 0, 500))
        .thenReturn(List.of(ex, ey, ez, era, erb, erc));
    when(annotatableTimeseriesDAOMock.findByAppId(CHAN_X.toString()))
        .thenReturn(Optional.of(nodeWithRole(CONTAINER_ID, CHAN_X, "x")));
    when(annotatableTimeseriesDAOMock.findByAppId(CHAN_Y.toString()))
        .thenReturn(Optional.of(nodeWithRole(CONTAINER_ID, CHAN_Y, "y")));
    when(annotatableTimeseriesDAOMock.findByAppId(CHAN_Z.toString()))
        .thenReturn(Optional.of(nodeWithRole(CONTAINER_ID, CHAN_Z, "z")));
    when(annotatableTimeseriesDAOMock.findByAppId(CHAN_RA.toString()))
        .thenReturn(Optional.of(nodeWithRole(CONTAINER_ID, CHAN_RA, "rot_a")));
    when(annotatableTimeseriesDAOMock.findByAppId(CHAN_RB.toString()))
        .thenReturn(Optional.of(nodeWithRole(CONTAINER_ID, CHAN_RB, "rot_b")));
    when(annotatableTimeseriesDAOMock.findByAppId(CHAN_RC.toString()))
        .thenReturn(Optional.of(nodeWithRole(CONTAINER_ID, CHAN_RC, "rot_c")));

    Response resp = resource.getSpatialRoles(CONTAINER_ID);

    assertEquals(200, resp.getStatus());
    SpatialRolesIO body = (SpatialRolesIO) resp.getEntity();
    assertEquals(CHAN_X, body.x());
    assertEquals(CHAN_Y, body.y());
    assertEquals(CHAN_Z, body.z());
    assertEquals(CHAN_RA, body.rot_a());
    assertEquals(CHAN_RB, body.rot_b());
    assertEquals(CHAN_RC, body.rot_c());
  }

  @Test
  void duplicateRoleAnnotation_firstWins() {
    // Two channels both annotated as "x" — the first in iteration order wins
    var e1 = entityWithShepardId(CHAN_X);
    var e2 = entityWithShepardId(CHAN_Y);

    when(resolverMock.listPaged(CONTAINER_ID, 0, 500)).thenReturn(List.of(e1, e2));
    when(annotatableTimeseriesDAOMock.findByAppId(CHAN_X.toString()))
        .thenReturn(Optional.of(nodeWithRole(CONTAINER_ID, CHAN_X, "x")));
    when(annotatableTimeseriesDAOMock.findByAppId(CHAN_Y.toString()))
        .thenReturn(Optional.of(nodeWithRole(CONTAINER_ID, CHAN_Y, "x"))); // also claims "x"

    Response resp = resource.getSpatialRoles(CONTAINER_ID);

    SpatialRolesIO body = (SpatialRolesIO) resp.getEntity();
    // First channel in list wins
    assertEquals(CHAN_X, body.x());
  }
}
