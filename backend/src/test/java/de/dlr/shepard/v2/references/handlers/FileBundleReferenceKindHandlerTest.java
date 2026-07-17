package de.dlr.shepard.v2.references.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.references.file.daos.FileBundleReferenceDAO;
import de.dlr.shepard.context.references.file.daos.FileGroupDAO;
import de.dlr.shepard.context.references.file.entities.FileBundleReference;
import de.dlr.shepard.context.references.file.entities.FileGroup;
import de.dlr.shepard.context.references.file.services.FileBundleReferenceService;
import de.dlr.shepard.context.references.uri.entities.URIReference;
import de.dlr.shepard.data.file.daos.ShepardFileDAO;
import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.file.entities.ShepardFile;
import de.dlr.shepard.data.file.services.FileContainerService;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * APISIMP-KIND-DISCRIMINATOR slice 3 — unit tests for
 * {@link FileBundleReferenceKindHandler}, covering all SPI method paths
 * with mocked dependencies.
 */
class FileBundleReferenceKindHandlerTest {

  private static final String DO_APP_ID = "do-app-42";
  private static final String REF_APP_ID = "fbr-app-7";
  private static final String CONTAINER_APP_ID = "fc-app-99";
  private static final String CONTAINER_MONGO_ID = "mongo-abc123";

  @Mock
  FileBundleReferenceDAO fileBundleReferenceDAO;

  @Mock
  FileBundleReferenceService fileBundleReferenceService;

  @Mock
  FileContainerService fileContainerService;

  @Mock
  DataObjectDAO dataObjectDAO;

  @Mock
  DateHelper dateHelper;

  @Mock
  UserService userService;

  @Mock
  ShepardFileDAO shepardFileDAO;

  @Mock
  FileGroupDAO fileGroupDAO;

  FileBundleReferenceKindHandler handler;

  DataObject parent;
  FileContainer container;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    handler = new FileBundleReferenceKindHandler();
    handler.fileBundleReferenceDAO = fileBundleReferenceDAO;
    handler.fileBundleReferenceService = fileBundleReferenceService;
    handler.fileContainerService = fileContainerService;
    handler.dataObjectDAO = dataObjectDAO;
    handler.dateHelper = dateHelper;
    handler.userService = userService;
    handler.shepardFileDAO = shepardFileDAO;
    handler.fileGroupDAO = fileGroupDAO;

    var coll = new Collection(1L);
    coll.setShepardId(1L);
    parent = new DataObject(42L);
    parent.setAppId(DO_APP_ID);
    parent.setShepardId(101L);
    parent.setCollection(coll);

    container = new FileContainer(99L);
    container.setAppId(CONTAINER_APP_ID);
    container.setMongoId(CONTAINER_MONGO_ID);
  }

  private FileBundleReference bundle() {
    var ref = new FileBundleReference(7L);
    ref.setAppId(REF_APP_ID);
    ref.setName("my-bundle");
    ref.setShepardId(7L);
    ref.setDataObject(parent);
    ref.setFileContainer(container);
    var group = new FileGroup();
    group.setName("default");
    ref.addGroup(group);
    var file = new ShepardFile();
    file.setOid("oid-1");
    ref.addFile(file);
    return ref;
  }

  private URIReference otherRef() {
    var ref = new URIReference(5L);
    ref.setAppId("uri-5");
    return ref;
  }

  // ─── kind / owns ────────────────────────────────────────────────────────────

  @Test
  void kindAndOwns() {
    assertEquals("bundle", handler.kind());
    assertTrue(handler.owns(bundle()));
    assertFalse(handler.owns(otherRef()));
  }

  // ─── toIO ───────────────────────────────────────────────────────────────────

  @Test
  void toIO_mapsPayloadFields() {
    // APISIMP-BUNDLE-KIND-TOIO-OGM: counts come from DAO Cypher queries, not OGM lazy-loads.
    when(fileGroupDAO.countByBundleAppId(REF_APP_ID)).thenReturn(1L);
    when(shepardFileDAO.countByBundleReferenceAppId(REF_APP_ID)).thenReturn(1L);

    var io = handler.toIO(bundle());
    assertEquals("bundle", io.getKind());
    assertEquals("bundle", io.getReferenceShape());
    assertEquals(CONTAINER_MONGO_ID, io.getPayload().get("containerMongoId"));
    assertEquals(CONTAINER_APP_ID, io.getPayload().get("containerAppId"));
    assertEquals(1, io.getPayload().get("groupCount"));
    assertEquals(1, io.getPayload().get("fileCount"));
  }

  @Test
  void toIO_nullContainer_yieldsNullContainerFields() {
    var ref = bundle();
    ref.setFileContainer(null);
    var io = handler.toIO(ref);
    assertNull(io.getPayload().get("containerMongoId"));
    assertNull(io.getPayload().get("containerAppId"));
  }

  @Test
  void toIO_nullAppId_yieldsZeroCounts() {
    // Null appId guard: DAO must not be called; counts fall back to 0.
    var ref = bundle();
    ref.setAppId(null);
    var io = handler.toIO(ref);
    assertEquals(0, io.getPayload().get("groupCount"));
    assertEquals(0, io.getPayload().get("fileCount"));
  }

  @Test
  void toIO_daosReturnZero_yieldsZeroCounts() {
    // DAO path returns 0 when bundle is empty.
    when(fileGroupDAO.countByBundleAppId(REF_APP_ID)).thenReturn(0L);
    when(shepardFileDAO.countByBundleReferenceAppId(REF_APP_ID)).thenReturn(0L);
    var io = handler.toIO(bundle());
    assertEquals(0, io.getPayload().get("groupCount"));
    assertEquals(0, io.getPayload().get("fileCount"));
  }

  // ─── findByAppId ────────────────────────────────────────────────────────────

  @Test
  void findByAppId_delegates() {
    when(fileBundleReferenceDAO.findByAppId(REF_APP_ID)).thenReturn(bundle());
    BasicReference found = handler.findByAppId(REF_APP_ID);
    assertTrue(found instanceof FileBundleReference);
  }

  // ─── create ─────────────────────────────────────────────────────────────────

  @Test
  void create_success() {
    when(dataObjectDAO.findByAppId(DO_APP_ID)).thenReturn(parent);
    when(fileContainerService.getContainerByAppId(CONTAINER_APP_ID)).thenReturn(container);
    when(fileBundleReferenceService.createReference(eq(1L), eq(101L), any())).thenReturn(bundle());

    var io = handler.create(
      DO_APP_ID,
      Map.of("name", "new-bundle", "fileContainerAppId", CONTAINER_APP_ID)
    );

    assertEquals("bundle", io.getKind());
    verify(fileBundleReferenceService).createReference(eq(1L), eq(101L), any());
  }

  @Test
  void create_withOids_passesThrough() {
    when(dataObjectDAO.findByAppId(DO_APP_ID)).thenReturn(parent);
    when(fileContainerService.getContainerByAppId(CONTAINER_APP_ID)).thenReturn(container);
    when(fileBundleReferenceService.createReference(eq(1L), eq(101L), any())).thenReturn(bundle());

    handler.create(
      DO_APP_ID,
      Map.of(
        "name", "with-oids",
        "fileContainerAppId", CONTAINER_APP_ID,
        "fileOids", List.of("oid-a", "oid-b")
      )
    );

    verify(fileBundleReferenceService).createReference(eq(1L), eq(101L), any());
  }

  @Test
  void create_nullBody_throws400() {
    assertThrows(BadRequestException.class, () -> handler.create(DO_APP_ID, null));
  }

  @Test
  void create_missingName_throws400() {
    when(dataObjectDAO.findByAppId(DO_APP_ID)).thenReturn(parent);
    assertThrows(
      BadRequestException.class,
      () -> handler.create(DO_APP_ID, Map.of("fileContainerAppId", CONTAINER_APP_ID))
    );
  }

  @Test
  void create_missingContainerAppId_throws400() {
    when(dataObjectDAO.findByAppId(DO_APP_ID)).thenReturn(parent);
    assertThrows(
      BadRequestException.class,
      () -> handler.create(DO_APP_ID, Map.of("name", "x"))
    );
  }

  @Test
  void create_unknownDataObject_throws404() {
    when(dataObjectDAO.findByAppId(DO_APP_ID)).thenReturn(null);
    assertThrows(
      NotFoundException.class,
      () -> handler.create(DO_APP_ID, Map.of("name", "x", "fileContainerAppId", CONTAINER_APP_ID))
    );
  }

  // ─── patch ──────────────────────────────────────────────────────────────────

  @Test
  void patch_name_updates() {
    var ref = bundle();
    when(fileBundleReferenceDAO.findByAppId(REF_APP_ID)).thenReturn(ref);
    when(dateHelper.getDate()).thenReturn(new Date());
    when(userService.getCurrentUser()).thenReturn(new User());

    var io = handler.patch(REF_APP_ID, Map.of("name", "renamed"));

    assertEquals("bundle", io.getKind());
    verify(fileBundleReferenceDAO).createOrUpdate(ref);
  }

  @Test
  void patch_missing_throws404() {
    when(fileBundleReferenceDAO.findByAppId(REF_APP_ID)).thenReturn(null);
    assertThrows(NotFoundException.class, () -> handler.patch(REF_APP_ID, Map.of("name", "x")));
  }

  @Test
  void patch_blankName_throws400() {
    when(fileBundleReferenceDAO.findByAppId(REF_APP_ID)).thenReturn(bundle());
    assertThrows(BadRequestException.class, () -> handler.patch(REF_APP_ID, Map.of("name", "  ")));
  }

  // ─── delete ─────────────────────────────────────────────────────────────────

  @Test
  void delete_delegates() {
    when(fileBundleReferenceDAO.findByAppId(REF_APP_ID)).thenReturn(bundle());

    handler.delete(REF_APP_ID);

    verify(fileBundleReferenceService).deleteReference(eq(1L), eq(101L), eq(7L));
  }

  @Test
  void delete_missing_throws404() {
    when(fileBundleReferenceDAO.findByAppId(REF_APP_ID)).thenReturn(null);
    assertThrows(NotFoundException.class, () -> handler.delete(REF_APP_ID));
  }

  // ─── listByDataObject ───────────────────────────────────────────────────────

  @Test
  void list_returnsActiveBundles() {
    when(fileBundleReferenceDAO.findByDataObjectAppId(DO_APP_ID)).thenReturn(List.of(bundle()));

    var out = handler.listByDataObject(DO_APP_ID, null);

    assertEquals(1, out.size());
    assertEquals("bundle", out.get(0).getKind());
  }

  @Test
  void list_filtersDeleted() {
    var deleted = bundle();
    deleted.setDeleted(true);
    when(fileBundleReferenceDAO.findByDataObjectAppId(DO_APP_ID)).thenReturn(List.of(deleted));

    var out = handler.listByDataObject(DO_APP_ID, null);

    assertEquals(0, out.size());
  }
}
