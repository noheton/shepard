package de.dlr.shepard.v2.collection.resources;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.daos.CollectionPropertiesDAO;
import de.dlr.shepard.context.export.ExportService;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.Principal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class CollectionStreamExportRestTest {

  static final String COLL_APP_ID = "018f9c5a-7e26-7000-a000-000000000042";
  static final long COLL_OGM_ID = 77L;
  static final String CALLER = "bob";

  @Mock CollectionPropertiesDAO collectionPropertiesDAO;
  @Mock PermissionsService permissionsService;
  @Mock ExportService exportService;
  @Mock SecurityContext securityContext;
  @Mock Principal principal;

  CollectionStreamExportRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new CollectionStreamExportRest();
    resource.collectionPropertiesDAO = collectionPropertiesDAO;
    resource.permissionsService = permissionsService;
    resource.exportService = exportService;

    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
  }

  @Test
  void returns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = resource.streamExport(COLL_APP_ID, securityContext);
    assertEquals(401, r.getStatus());
    verify(collectionPropertiesDAO, never()).findCollectionIdByAppId(anyString());
  }

  @Test
  void returns404WhenCollectionMissing() {
    when(collectionPropertiesDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.empty());
    Response r = resource.streamExport(COLL_APP_ID, securityContext);
    assertEquals(404, r.getStatus());
    verify(permissionsService, never()).isAccessTypeAllowedForUser(anyLong(), any(), any(), anyLong());
  }

  @Test
  void returns403WhenCallerLacksReadPermission() {
    when(collectionPropertiesDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(
      eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER), anyLong())).thenReturn(false);
    Response r = resource.streamExport(COLL_APP_ID, securityContext);
    assertEquals(403, r.getStatus());
  }

  @Test
  void returns200AndStreamsZipBytesOnHappyPath() throws Exception {
    byte[] zipBytes = {0x50, 0x4B, 0x03, 0x04, 1, 2, 3};
    when(collectionPropertiesDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(
      eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER), anyLong())).thenReturn(true);
    when(exportService.exportCollectionByShepardId(eq(COLL_OGM_ID)))
      .thenReturn(new ByteArrayInputStream(zipBytes));

    Response r = resource.streamExport(COLL_APP_ID, securityContext);

    assertEquals(200, r.getStatus());
    // Exercise the StreamingOutput and verify bytes round-trip correctly
    var sink = new ByteArrayOutputStream();
    ((StreamingOutput) r.getEntity()).write(sink);
    assertArrayEquals(zipBytes, sink.toByteArray());
  }

  @Test
  void contentDispositionHeaderPresentWithSafeFileName() throws Exception {
    when(collectionPropertiesDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(
      eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER), anyLong())).thenReturn(true);
    when(exportService.exportCollectionByShepardId(eq(COLL_OGM_ID)))
      .thenReturn(new ByteArrayInputStream(new byte[0]));

    Response r = resource.streamExport(COLL_APP_ID, securityContext);

    String disposition = (String) r.getHeaders().getFirst("Content-Disposition");
    assertNotNull(disposition, "Content-Disposition header must be present");
    assertFalse(disposition.isEmpty());
    assertEquals("attachment; filename=\"" + COLL_APP_ID + "-export.zip\"", disposition);
  }

  @Test
  void sanitizesPathUnsafeCharsInFilename() throws Exception {
    String unsafeAppId = "018f9c5a/evil/../id";
    when(collectionPropertiesDAO.findCollectionIdByAppId(unsafeAppId)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(
      eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER), anyLong())).thenReturn(true);
    when(exportService.exportCollectionByShepardId(eq(COLL_OGM_ID)))
      .thenReturn(new ByteArrayInputStream(new byte[0]));

    Response r = resource.streamExport(unsafeAppId, securityContext);

    String disposition = (String) r.getHeaders().getFirst("Content-Disposition");
    assertNotNull(disposition);
    assertFalse(disposition.contains("/"), "filename must not contain path separators");
    assertFalse(disposition.contains(".."), "filename must not contain path traversal sequences");
  }
}
