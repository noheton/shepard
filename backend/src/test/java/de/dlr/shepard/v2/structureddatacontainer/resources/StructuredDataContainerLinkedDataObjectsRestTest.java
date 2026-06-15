package de.dlr.shepard.v2.structureddatacontainer.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.data.structureddata.entities.StructuredDataContainer;
import de.dlr.shepard.data.structureddata.services.StructuredDataContainerService;
import de.dlr.shepard.v2.integrity.SafeDeleteConflict;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the StructuredDataContainer DI1 safe-delete:
 * {@code DELETE /v2/structured-data-containers/{containerAppId}}
 * — keyed on appId (UUID string). The {@code linked-data-objects} GET was
 * collapsed onto {@code GET /v2/containers/{appId}/linked-data-objects}
 * (APISIMP-CONT-LDO-UNIFY); its coverage now lives in {@code ContainersV2RestTest}.
 */
public class StructuredDataContainerLinkedDataObjectsRestTest {

  private StructuredDataContainerLinkedDataObjectsRest resource;
  private StructuredDataContainerService containerServiceMock;

  private static final String APP_ID = "01234567-abcd-7000-8000-000000000022";
  private static final long   OGM_ID = 22L;

  @BeforeEach
  void setUp() throws Exception {
    resource             = new StructuredDataContainerLinkedDataObjectsRest();
    containerServiceMock = mock(StructuredDataContainerService.class);
    inject(resource, "structuredDataContainerService", containerServiceMock);
  }

  // ── DELETE (safe delete) ─────────────────────────────────────────────────

  @Test
  void safeDelete_withActiveRefs_returns409() {
    StructuredDataContainer c = containerWithOgmId(OGM_ID);
    DataObject d = new DataObject();
    d.setAppId("ref-appid");
    when(containerServiceMock.getContainerByAppId(APP_ID)).thenReturn(c);
    when(containerServiceMock.findLinkedDataObjectsByAppId(APP_ID)).thenReturn(List.of(d));

    Response resp = resource.safeDeleteContainer(APP_ID, false);

    assertEquals(409, resp.getStatus());
    SafeDeleteConflict body = (SafeDeleteConflict) resp.getEntity();
    assertEquals(1, body.referenceCount());
    verify(containerServiceMock, never()).deleteContainer(OGM_ID);
  }

  @Test
  void safeDelete_withActiveRefsAndForce_returns204() {
    StructuredDataContainer c = containerWithOgmId(OGM_ID);
    DataObject d = new DataObject();
    d.setAppId("ref-appid");
    when(containerServiceMock.getContainerByAppId(APP_ID)).thenReturn(c);

    Response resp = resource.safeDeleteContainer(APP_ID, true);

    assertEquals(204, resp.getStatus());
    verify(containerServiceMock).deleteContainer(OGM_ID);
  }

  @Test
  void safeDelete_noRefs_returns204() {
    StructuredDataContainer c = containerWithOgmId(OGM_ID);
    when(containerServiceMock.getContainerByAppId(APP_ID)).thenReturn(c);
    when(containerServiceMock.findLinkedDataObjectsByAppId(APP_ID)).thenReturn(List.of());

    Response resp = resource.safeDeleteContainer(APP_ID, false);

    assertEquals(204, resp.getStatus());
    verify(containerServiceMock).deleteContainer(OGM_ID);
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private static StructuredDataContainer containerWithOgmId(long id) {
    StructuredDataContainer c = new StructuredDataContainer();
    c.setId(id);
    return c;
  }

  private static void inject(Object target, String fieldName, Object value) throws Exception {
    Class<?> clazz = target.getClass();
    while (clazz != null) {
      try {
        Field f = clazz.getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
        return;
      } catch (NoSuchFieldException e) {
        clazz = clazz.getSuperclass();
      }
    }
    throw new NoSuchFieldException("No field '" + fieldName + "' on " + target.getClass());
  }
}
