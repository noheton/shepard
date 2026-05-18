package de.dlr.shepard.v2.dataobject.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.collection.services.DataObjectService;
import jakarta.validation.Validator;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * L2d Phase A.2 — unit tests for the {@code /v2/collections/{appId}/data-objects}
 * resource. Same six-shape grid as {@code CollectionV2RestTest}: 401, 403,
 * 404, plus 200 / 201 / 204 happy paths.
 */
class DataObjectV2RestTest {

  static final String COLL_APP_ID = "018f9c5a-7e26-7000-a000-000000000010";
  static final String DO_APP_ID = "018f9c5a-7e26-7000-a000-000000000020";
  static final long COLL_OGM_ID = 42L;
  static final long DO_OGM_ID = 84L;
  static final String CALLER = "alice";

  @Mock
  DataObjectService dataObjectService;

  @Mock
  PermissionsService permissionsService;

  @Mock
  EntityIdResolver entityIdResolver;

  @Mock
  Validator validator;

  @Mock
  SecurityContext securityContext;

  @Mock
  Principal principal;

  DataObjectV2Rest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new DataObjectV2Rest();
    resource.dataObjectService = dataObjectService;
    resource.permissionsService = permissionsService;
    resource.entityIdResolver = entityIdResolver;
    resource.validator = validator;
    resource.objectMapper = new ObjectMapper();
    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
    when(validator.validate(any())).thenReturn(Collections.emptySet());
  }

  // ── list ──────────────────────────────────────────────────────────────────

  @Test
  void listReturns404WhenCollectionUnknown() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenThrow(new NotFoundException());
    Response r = resource.list(COLL_APP_ID, null, 0, 50, securityContext);
    assertEquals(404, r.getStatus());
    verify(dataObjectService, never()).getAllDataObjectsByShepardIds(anyLong(), any(), any());
  }

  @Test
  void listReturns403WhenNoReadOnCollection() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER), anyLong()))
      .thenReturn(false);
    Response r = resource.list(COLL_APP_ID, null, 0, 50, securityContext);
    assertEquals(403, r.getStatus());
  }

  @Test
  void listReturns200WithRows() {
    DataObject d = new DataObject();
    d.setShepardId(DO_OGM_ID);
    d.setAppId(DO_APP_ID);
    d.setName("sensor-track-1");
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER), anyLong()))
      .thenReturn(true);
    when(dataObjectService.getAllDataObjectsByShepardIds(eq(COLL_OGM_ID), any(), eq(null)))
      .thenReturn(List.of(d));

    Response r = resource.list(COLL_APP_ID, null, 0, 50, securityContext);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<DataObjectIO> body = (List<DataObjectIO>) r.getEntity();
    assertEquals(1, body.size());
    assertEquals(DO_APP_ID, body.get(0).getAppId());
  }

  // ── get ───────────────────────────────────────────────────────────────────

  @Test
  void getReturns404WhenDataObjectUnknown() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenThrow(new NotFoundException());
    Response r = resource.get(COLL_APP_ID, DO_APP_ID, securityContext);
    assertEquals(404, r.getStatus());
    verify(permissionsService, never()).isAccessTypeAllowedForUser(anyLong(), any(), any(), anyLong());
  }

  @Test
  void getReturns403WhenNoRead() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenReturn(DO_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(DO_OGM_ID), eq(AccessType.Read), eq(CALLER), anyLong()))
      .thenReturn(false);
    Response r = resource.get(COLL_APP_ID, DO_APP_ID, securityContext);
    assertEquals(403, r.getStatus());
  }

  @Test
  void getReturns200WithDataObject() {
    DataObject d = new DataObject();
    d.setShepardId(DO_OGM_ID);
    d.setAppId(DO_APP_ID);
    d.setName("sensor-track-1");
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenReturn(DO_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(DO_OGM_ID), eq(AccessType.Read), eq(CALLER), anyLong()))
      .thenReturn(true);
    when(dataObjectService.getDataObject(COLL_OGM_ID, DO_OGM_ID)).thenReturn(d);

    Response r = resource.get(COLL_APP_ID, DO_APP_ID, securityContext);

    assertEquals(200, r.getStatus());
    DataObjectIO io = (DataObjectIO) r.getEntity();
    assertNotNull(io);
    assertEquals(DO_APP_ID, io.getAppId());
  }

  // ── create ────────────────────────────────────────────────────────────────

  @Test
  void createReturns404WhenCollectionUnknown() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenThrow(new NotFoundException());
    DataObjectIO body = new DataObjectIO();
    body.setName("new do");
    Response r = resource.create(COLL_APP_ID, body, securityContext);
    assertEquals(404, r.getStatus());
  }

  @Test
  void createReturns403WhenNoWriteOnCollection() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Write), eq(CALLER), anyLong()))
      .thenReturn(false);
    DataObjectIO body = new DataObjectIO();
    body.setName("new do");
    Response r = resource.create(COLL_APP_ID, body, securityContext);
    assertEquals(403, r.getStatus());
    verify(dataObjectService, never()).createDataObject(anyLong(), any());
  }

  @Test
  void createReturns201WithMintedAppId() {
    DataObject created = new DataObject();
    created.setShepardId(99L);
    created.setAppId("018f9c5a-9999-7000-a000-000000000099");
    created.setName("new do");
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Write), eq(CALLER), anyLong()))
      .thenReturn(true);
    DataObjectIO body = new DataObjectIO();
    body.setName("new do");
    when(dataObjectService.createDataObject(eq(COLL_OGM_ID), eq(body))).thenReturn(created);

    Response r = resource.create(COLL_APP_ID, body, securityContext);

    assertEquals(201, r.getStatus());
    DataObjectIO io = (DataObjectIO) r.getEntity();
    assertEquals("018f9c5a-9999-7000-a000-000000000099", io.getAppId());
  }

  // ── patch ─────────────────────────────────────────────────────────────────

  @Test
  void patchReturns400WhenBodyIsNotAnObject() {
    assertThrows(InvalidBodyException.class, () ->
      resource.patch(COLL_APP_ID, DO_APP_ID, JsonNodeFactory.instance.arrayNode(), securityContext)
    );
  }

  @Test
  void patchReturns404WhenDataObjectUnknown() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenThrow(new NotFoundException());
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("description", "x");
    Response r = resource.patch(COLL_APP_ID, DO_APP_ID, body, securityContext);
    assertEquals(404, r.getStatus());
  }

  @Test
  void patchReturns403WhenNoWrite() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenReturn(DO_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(DO_OGM_ID), eq(AccessType.Write), eq(CALLER), anyLong()))
      .thenReturn(false);
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("description", "x");
    Response r = resource.patch(COLL_APP_ID, DO_APP_ID, body, securityContext);
    assertEquals(403, r.getStatus());
    verify(dataObjectService, never()).updateDataObject(anyLong(), anyLong(), any());
  }

  @Test
  void patchReturns200WithMergedBody() {
    DataObject existing = new DataObject();
    existing.setShepardId(DO_OGM_ID);
    existing.setAppId(DO_APP_ID);
    existing.setName("sensor-track-1");
    existing.setDescription("old");

    DataObject updated = new DataObject();
    updated.setShepardId(DO_OGM_ID);
    updated.setAppId(DO_APP_ID);
    updated.setName("sensor-track-1");
    updated.setDescription("new");

    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("description", "new");

    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenReturn(DO_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(DO_OGM_ID), eq(AccessType.Write), eq(CALLER), anyLong()))
      .thenReturn(true);
    when(dataObjectService.getDataObject(COLL_OGM_ID, DO_OGM_ID)).thenReturn(existing);
    when(dataObjectService.updateDataObject(eq(COLL_OGM_ID), eq(DO_OGM_ID), any())).thenReturn(updated);

    Response r = resource.patch(COLL_APP_ID, DO_APP_ID, body, securityContext);

    assertEquals(200, r.getStatus());
    DataObjectIO io = (DataObjectIO) r.getEntity();
    assertEquals("new", io.getDescription());
  }

  // ── delete ────────────────────────────────────────────────────────────────

  @Test
  void deleteReturns404WhenDataObjectUnknown() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenThrow(new NotFoundException());
    Response r = resource.delete(COLL_APP_ID, DO_APP_ID, securityContext);
    assertEquals(404, r.getStatus());
    verify(dataObjectService, never()).deleteDataObject(anyLong(), anyLong());
  }

  @Test
  void deleteReturns403WhenNoWrite() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenReturn(DO_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(DO_OGM_ID), eq(AccessType.Write), eq(CALLER), anyLong()))
      .thenReturn(false);
    Response r = resource.delete(COLL_APP_ID, DO_APP_ID, securityContext);
    assertEquals(403, r.getStatus());
    verify(dataObjectService, never()).deleteDataObject(anyLong(), anyLong());
  }

  @Test
  void deleteReturns204OnSuccess() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenReturn(DO_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(DO_OGM_ID), eq(AccessType.Write), eq(CALLER), anyLong()))
      .thenReturn(true);
    Response r = resource.delete(COLL_APP_ID, DO_APP_ID, securityContext);
    assertEquals(204, r.getStatus());
    verify(dataObjectService).deleteDataObject(COLL_OGM_ID, DO_OGM_ID);
  }
}
