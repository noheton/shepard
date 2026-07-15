package de.dlr.shepard.v2.spatial.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.file.entities.FileReference;
import de.dlr.shepard.context.references.spatialdata.daos.SpatialDataReferenceDAO;
import de.dlr.shepard.context.references.spatialdata.entities.SpatialDataReference;
import de.dlr.shepard.context.references.spatialdata.services.SpatialDataReferenceService;
import de.dlr.shepard.data.spatialdata.daos.SpatialDataContainerDAO;
import de.dlr.shepard.data.spatialdata.model.SpatialDataContainer;
import de.dlr.shepard.v2.references.io.ReferenceV2IO;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * SPATIAL-UNIFY-002 — unit tests for {@link SpatialDataReferenceKindHandler}.
 * Exercises the IO projection, list/find dispatch, create validation, patch,
 * and delete with mocked per-kind services (Video/Git-handler-shaped).
 */
class SpatialDataReferenceKindHandlerTest {

  private static final String DO_APP_ID = "do-app-1";
  private static final String REF_APP_ID = "ref-app-1";
  private static final String CONTAINER_APP_ID = "container-app-1";

  @Mock
  SpatialDataReferenceService spatialDataReferenceService;

  @Mock
  SpatialDataReferenceDAO spatialDataReferenceDAO;

  @Mock
  SpatialDataContainerDAO spatialDataContainerDAO;

  @Mock
  DataObjectDAO dataObjectDAO;

  @Mock
  de.dlr.shepard.auth.users.services.UserService userService;

  @Mock
  DateHelper dateHelper;

  SpatialDataReferenceKindHandler handler;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    handler = new SpatialDataReferenceKindHandler();
    handler.spatialDataReferenceService = spatialDataReferenceService;
    handler.spatialDataReferenceDAO = spatialDataReferenceDAO;
    handler.spatialDataContainerDAO = spatialDataContainerDAO;
    handler.dataObjectDAO = dataObjectDAO;
    handler.userService = userService;
    handler.dateHelper = dateHelper;
  }

  @Test
  void kindIsSpatial() {
    assertEquals("spatial", handler.kind());
  }

  @Test
  void ownsSpatialDataReference() {
    assertTrue(handler.owns(new SpatialDataReference()));
  }

  @Test
  void doesNotOwnOtherReference() {
    assertFalse(handler.owns(new FileReference()));
  }

  /** Attach a DataObject so the BasicReferenceIO constructor's shepardId read succeeds. */
  private static SpatialDataReference withParent(SpatialDataReference ref) {
    DataObject parent = new DataObject();
    parent.setShepardId(42L);
    ref.setDataObject(parent);
    return ref;
  }

  @Test
  void toIOProjectsPayloadIncludingContainerAppId() {
    SpatialDataContainer container = new SpatialDataContainer();
    container.setAppId(CONTAINER_APP_ID);
    SpatialDataReference ref = withParent(new SpatialDataReference());
    ref.setName("TPS pointcloud");
    ref.setStartTime(10L);
    ref.setEndTime(20L);
    ref.setLimit(100);
    ref.setSpatialDataContainer(container);

    ReferenceV2IO io = handler.toIO(ref);

    assertEquals("spatial", io.getKind());
    assertEquals(CONTAINER_APP_ID, io.getPayload().get("spatialDataContainerAppId"));
    // APISIMP-SPATIAL-TIMEWINDOW-NANOS: toIO() now emits ISO 8601 strings.
    assertEquals("1970-01-01T00:00:00.000000010Z", io.getPayload().get("startTime"));
    assertEquals("1970-01-01T00:00:00.000000020Z", io.getPayload().get("endTime"));
    assertEquals(100, io.getPayload().get("limit"));
    assertNull(io.getReferenceShape());
  }

  @Test
  void toIOHandlesNullContainer() {
    SpatialDataReference ref = withParent(new SpatialDataReference());
    ref.setName("orphan");
    ReferenceV2IO io = handler.toIO(ref);
    assertNull(io.getPayload().get("spatialDataContainerAppId"));
  }

  @Test
  void findByAppIdDelegatesToService() {
    SpatialDataReference ref = new SpatialDataReference();
    when(spatialDataReferenceService.findByAppId(REF_APP_ID)).thenReturn(ref);
    assertEquals(ref, handler.findByAppId(REF_APP_ID));
  }

  @Test
  void listByDataObjectProjectsNonDeleted() {
    SpatialDataContainer container = new SpatialDataContainer();
    container.setAppId(CONTAINER_APP_ID);
    SpatialDataReference live = withParent(new SpatialDataReference());
    live.setName("live");
    live.setSpatialDataContainer(container);
    SpatialDataReference deleted = new SpatialDataReference();
    deleted.setDeleted(true);
    when(spatialDataReferenceService.listByDataObjectAppId(DO_APP_ID)).thenReturn(List.of(live, deleted));

    List<ReferenceV2IO> out = handler.listByDataObject(DO_APP_ID, null);

    assertEquals(1, out.size());
    assertEquals("live", out.get(0).getName());
  }

  @Test
  void listByDataObjectRejectsBlankAppId() {
    assertThrows(BadRequestException.class, () -> handler.listByDataObject("  ", null));
  }

  @Test
  void createRequiresContainerAppId() {
    assertThrows(BadRequestException.class, () -> handler.create(DO_APP_ID, Map.of("name", "x")));
  }

  @Test
  void createRejectsUnknownDataObject() {
    when(dataObjectDAO.findByAppId(DO_APP_ID)).thenReturn(null);
    assertThrows(
      NotFoundException.class,
      () -> handler.create(DO_APP_ID, Map.of("spatialDataContainerAppId", CONTAINER_APP_ID))
    );
  }

  @Test
  void createRejectsUnknownContainer() {
    when(dataObjectDAO.findByAppId(DO_APP_ID)).thenReturn(new DataObject());
    when(spatialDataContainerDAO.findByAppId(CONTAINER_APP_ID)).thenReturn(null);
    assertThrows(
      BadRequestException.class,
      () -> handler.create(DO_APP_ID, Map.of("spatialDataContainerAppId", CONTAINER_APP_ID))
    );
  }

  @Test
  void createBindsToExistingContainer() {
    DataObject parent = new DataObject();
    SpatialDataContainer container = new SpatialDataContainer();
    container.setName("cont");
    container.setAppId(CONTAINER_APP_ID);
    when(dataObjectDAO.findByAppId(DO_APP_ID)).thenReturn(parent);
    when(spatialDataContainerDAO.findByAppId(CONTAINER_APP_ID)).thenReturn(container);
    when(spatialDataReferenceDAO.createOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));
    when(userService.getCurrentUser()).thenReturn(new de.dlr.shepard.auth.users.entities.User());

    ReferenceV2IO io = handler.create(DO_APP_ID, Map.of("spatialDataContainerAppId", CONTAINER_APP_ID));

    assertEquals("spatial", io.getKind());
    assertEquals(CONTAINER_APP_ID, io.getPayload().get("spatialDataContainerAppId"));
  }

  @Test
  void create_acceptsIso8601StartAndEndTime() {
    DataObject parent = new DataObject();
    SpatialDataContainer container = new SpatialDataContainer();
    container.setName("cont");
    container.setAppId(CONTAINER_APP_ID);
    when(dataObjectDAO.findByAppId(DO_APP_ID)).thenReturn(parent);
    when(spatialDataContainerDAO.findByAppId(CONTAINER_APP_ID)).thenReturn(container);
    when(spatialDataReferenceDAO.createOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));
    when(userService.getCurrentUser()).thenReturn(new de.dlr.shepard.auth.users.entities.User());

    ReferenceV2IO io = handler.create(DO_APP_ID, Map.of(
        "spatialDataContainerAppId", CONTAINER_APP_ID,
        "startTime", "2024-06-02T10:00:00Z",
        "endTime",   "2024-06-02T11:00:00Z"
    ));

    // APISIMP-SPATIAL-TIMEWINDOW-NANOS: ISO strings round-trip through nanoseconds → ISO.
    assertEquals("2024-06-02T10:00:00Z", io.getPayload().get("startTime"));
    assertEquals("2024-06-02T11:00:00Z", io.getPayload().get("endTime"));
  }

  @Test
  void deleteRejectsUnknownAppId() {
    when(spatialDataReferenceService.findByAppId(REF_APP_ID)).thenReturn(null);
    assertThrows(NotFoundException.class, () -> handler.delete(REF_APP_ID));
  }

  @Test
  void deleteDelegatesToService() {
    SpatialDataReference ref = new SpatialDataReference();
    when(spatialDataReferenceService.findByAppId(REF_APP_ID)).thenReturn(ref);
    handler.delete(REF_APP_ID);
    verify(spatialDataReferenceService).delete(ref);
  }

  @Test
  void patchRenamesReference() {
    SpatialDataReference ref = withParent(new SpatialDataReference());
    ref.setName("old");
    when(spatialDataReferenceService.findByAppId(REF_APP_ID)).thenReturn(ref);
    when(spatialDataReferenceDAO.createOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));
    when(userService.getCurrentUser()).thenReturn(new de.dlr.shepard.auth.users.entities.User());

    ReferenceV2IO io = handler.patch(REF_APP_ID, Map.of("name", "new"));

    assertEquals("new", io.getName());
  }
}
