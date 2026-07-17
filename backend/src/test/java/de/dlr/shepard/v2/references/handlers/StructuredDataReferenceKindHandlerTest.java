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
import de.dlr.shepard.context.references.structureddata.daos.StructuredDataReferenceDAO;
import de.dlr.shepard.context.references.structureddata.entities.StructuredDataReference;
import de.dlr.shepard.context.references.structureddata.services.StructuredDataReferenceService;
import de.dlr.shepard.context.references.uri.entities.URIReference;
import de.dlr.shepard.data.structureddata.entities.StructuredData;
import de.dlr.shepard.data.structureddata.entities.StructuredDataContainer;
import de.dlr.shepard.data.structureddata.services.StructuredDataContainerService;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * APISIMP-STRUCTURED-DATA-KIND — unit tests for {@link StructuredDataReferenceKindHandler},
 * covering all SPI method paths with mocked dependencies.
 */
class StructuredDataReferenceKindHandlerTest {

  private static final String DO_APP_ID = "do-app-42";
  private static final String REF_APP_ID = "sdr-app-7";
  private static final String CONTAINER_APP_ID = "sdc-app-99";

  @Mock
  StructuredDataReferenceDAO structuredDataReferenceDAO;

  @Mock
  StructuredDataReferenceService structuredDataReferenceService;

  @Mock
  StructuredDataContainerService structuredDataContainerService;

  @Mock
  DataObjectDAO dataObjectDAO;

  @Mock
  DateHelper dateHelper;

  @Mock
  UserService userService;

  StructuredDataReferenceKindHandler handler;

  DataObject parent;
  StructuredDataContainer container;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    handler = new StructuredDataReferenceKindHandler();
    handler.structuredDataReferenceDAO = structuredDataReferenceDAO;
    handler.structuredDataReferenceService = structuredDataReferenceService;
    handler.structuredDataContainerService = structuredDataContainerService;
    handler.dataObjectDAO = dataObjectDAO;
    handler.dateHelper = dateHelper;
    handler.userService = userService;

    var coll = new Collection(1L);
    coll.setShepardId(1L);
    parent = new DataObject(42L);
    parent.setAppId(DO_APP_ID);
    parent.setShepardId(101L);
    parent.setCollection(coll);

    container = new StructuredDataContainer(99L);
    container.setAppId(CONTAINER_APP_ID);
  }

  private StructuredDataReference sdRef() {
    var ref = new StructuredDataReference(7L);
    ref.setAppId(REF_APP_ID);
    ref.setName("my-sd-ref");
    ref.setShepardId(7L);
    ref.setDataObject(parent);
    ref.setStructuredDataContainer(container);
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
    assertEquals("structured-data", handler.kind());
    assertTrue(handler.owns(sdRef()));
    assertFalse(handler.owns(otherRef()));
  }

  // ─── toIO ───────────────────────────────────────────────────────────────────

  @Test
  void toIO_flattensContainerAppIdAndOids() {
    var ref = sdRef();
    var sd = new StructuredData();
    sd.setOid("oid-1");
    ref.getStructuredDatas().add(sd);

    var io = handler.toIO(ref);

    assertEquals("structured-data", io.getKind());
    assertEquals(CONTAINER_APP_ID, io.getPayload().get("structuredDataContainerAppId"));
    var oids = (String[]) io.getPayload().get("structuredDataOids");
    assertEquals(1, oids.length);
    assertEquals("oid-1", oids[0]);
  }

  @Test
  void toIO_nullContainer_yieldsNullContainerAppId() {
    var ref = sdRef();
    ref.setStructuredDataContainer(null);
    var io = handler.toIO(ref);
    assertNull(io.getPayload().get("structuredDataContainerAppId"));
  }

  @Test
  void toIO_nullStructuredDatas_yieldsEmptyOids() {
    var ref = sdRef();
    ref.setStructuredDatas(null);
    var io = handler.toIO(ref);
    var oids = (String[]) io.getPayload().get("structuredDataOids");
    assertEquals(0, oids.length);
  }

  // ─── findByAppId ────────────────────────────────────────────────────────────

  @Test
  void findByAppId_delegates() {
    when(structuredDataReferenceDAO.findByAppId(REF_APP_ID)).thenReturn(sdRef());
    BasicReference found = handler.findByAppId(REF_APP_ID);
    assertTrue(found instanceof StructuredDataReference);
  }

  // ─── create ─────────────────────────────────────────────────────────────────

  @Test
  void create_success() {
    when(dataObjectDAO.findByAppId(DO_APP_ID)).thenReturn(parent);
    when(structuredDataContainerService.getContainerByAppId(CONTAINER_APP_ID)).thenReturn(container);
    when(structuredDataReferenceService.createReference(eq(1L), eq(101L), any())).thenReturn(sdRef());

    var io = handler.create(
      DO_APP_ID,
      Map.of("name", "new-ref", "structuredDataContainerAppId", CONTAINER_APP_ID)
    );

    assertEquals("structured-data", io.getKind());
    verify(structuredDataReferenceService).createReference(eq(1L), eq(101L), any());
  }

  @Test
  void create_withOids_passesThrough() {
    when(dataObjectDAO.findByAppId(DO_APP_ID)).thenReturn(parent);
    when(structuredDataContainerService.getContainerByAppId(CONTAINER_APP_ID)).thenReturn(container);
    when(structuredDataReferenceService.createReference(eq(1L), eq(101L), any())).thenReturn(sdRef());

    handler.create(
      DO_APP_ID,
      Map.of(
        "name", "with-oids",
        "structuredDataContainerAppId", CONTAINER_APP_ID,
        "structuredDataOids", List.of("oid-a", "oid-b")
      )
    );

    verify(structuredDataReferenceService).createReference(eq(1L), eq(101L), any());
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
      () -> handler.create(DO_APP_ID, Map.of("structuredDataContainerAppId", CONTAINER_APP_ID))
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
      () -> handler.create(DO_APP_ID, Map.of("name", "x", "structuredDataContainerAppId", CONTAINER_APP_ID))
    );
  }

  // ─── patch ──────────────────────────────────────────────────────────────────

  @Test
  void patch_name_updates() {
    var ref = sdRef();
    when(structuredDataReferenceDAO.findByAppId(REF_APP_ID)).thenReturn(ref);
    when(dateHelper.getDate()).thenReturn(new Date());
    when(userService.getCurrentUser()).thenReturn(new User());

    var io = handler.patch(REF_APP_ID, Map.of("name", "renamed"));

    assertEquals("structured-data", io.getKind());
    verify(structuredDataReferenceDAO).createOrUpdate(ref);
  }

  @Test
  void patch_missing_throws404() {
    when(structuredDataReferenceDAO.findByAppId(REF_APP_ID)).thenReturn(null);
    assertThrows(NotFoundException.class, () -> handler.patch(REF_APP_ID, Map.of("name", "x")));
  }

  @Test
  void patch_blankName_throws400() {
    when(structuredDataReferenceDAO.findByAppId(REF_APP_ID)).thenReturn(sdRef());
    assertThrows(BadRequestException.class, () -> handler.patch(REF_APP_ID, Map.of("name", "  ")));
  }

  // ─── delete ─────────────────────────────────────────────────────────────────

  @Test
  void delete_delegates() {
    when(structuredDataReferenceDAO.findByAppId(REF_APP_ID)).thenReturn(sdRef());

    handler.delete(REF_APP_ID);

    verify(structuredDataReferenceService).deleteReference(eq(1L), eq(101L), eq(7L));
  }

  @Test
  void delete_missing_throws404() {
    when(structuredDataReferenceDAO.findByAppId(REF_APP_ID)).thenReturn(null);
    assertThrows(NotFoundException.class, () -> handler.delete(REF_APP_ID));
  }

  // ─── listByDataObject ───────────────────────────────────────────────────────

  @Test
  void list_delegates() {
    // APISIMP-SDR-LIST-APPID-PATH: new path calls findByDataObjectAppId directly
    when(structuredDataReferenceDAO.findByDataObjectAppId(DO_APP_ID)).thenReturn(List.of(sdRef()));

    var out = handler.listByDataObject(DO_APP_ID, null);

    assertEquals(1, out.size());
    assertEquals("structured-data", out.get(0).getKind());
  }

  @Test
  void list_unknownDataObject_returnsEmpty() {
    // APISIMP-SDR-LIST-APPID-PATH: non-paged path (consistent with paged overload)
    // returns empty list for an unknown DataObject instead of throwing 404.
    when(structuredDataReferenceDAO.findByDataObjectAppId(DO_APP_ID)).thenReturn(List.of());

    var out = handler.listByDataObject(DO_APP_ID, null);

    assertTrue(out.isEmpty());
  }

  // ─── uploadContent ──────────────────────────────────────────────────────────

  @Test
  void uploadContent_success() {
    var ref = sdRef();
    var sd = new StructuredData();
    sd.setOid("oid-new");
    when(structuredDataReferenceDAO.findByAppId(REF_APP_ID)).thenReturn(ref);
    when(dateHelper.getDate()).thenReturn(new Date());
    when(structuredDataContainerService.createStructuredData(eq(99L), any())).thenReturn(sd);

    InputStream body = new ByteArrayInputStream("{\"key\":\"val\"}".getBytes(StandardCharsets.UTF_8));
    var io = handler.uploadContent(REF_APP_ID, body, "entry.json", 13L);

    assertEquals("structured-data", io.getKind());
    verify(structuredDataContainerService).createStructuredData(eq(99L), any());
    verify(structuredDataReferenceDAO).createOrUpdate(ref);
  }

  @Test
  void uploadContent_missing_throws404() {
    when(structuredDataReferenceDAO.findByAppId(REF_APP_ID)).thenReturn(null);
    InputStream body = new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8));
    assertThrows(NotFoundException.class, () -> handler.uploadContent(REF_APP_ID, body, "x.json", 2L));
  }

  @Test
  void uploadContent_nullContainer_throws400() {
    var ref = sdRef();
    ref.setStructuredDataContainer(null);
    when(structuredDataReferenceDAO.findByAppId(REF_APP_ID)).thenReturn(ref);
    InputStream body = new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8));
    assertThrows(BadRequestException.class, () -> handler.uploadContent(REF_APP_ID, body, "x.json", 2L));
  }

  @Test
  void uploadContent_nullInput_throws400() {
    when(structuredDataReferenceDAO.findByAppId(REF_APP_ID)).thenReturn(sdRef());
    assertThrows(BadRequestException.class, () -> handler.uploadContent(REF_APP_ID, null, "x.json", 0L));
  }

  @Test
  void uploadContent_emptyBody_throws400() {
    when(structuredDataReferenceDAO.findByAppId(REF_APP_ID)).thenReturn(sdRef());
    InputStream body = new ByteArrayInputStream("   ".getBytes(StandardCharsets.UTF_8));
    assertThrows(BadRequestException.class, () -> handler.uploadContent(REF_APP_ID, body, "x.json", 3L));
  }
}
