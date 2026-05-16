package de.dlr.shepard.v2.collection.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.daos.CollectionPropertiesDAO;
import de.dlr.shepard.context.export.ExportService;
import de.dlr.shepard.storage.FileStorage;
import de.dlr.shepard.storage.FileStorageRegistry;
import de.dlr.shepard.storage.StorageException;
import de.dlr.shepard.v2.collection.io.ExportUrlIO;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class CollectionExportUrlRestTest {

  static final String COLL_APP_ID = "018f9c5a-7e26-7000-a000-000000000020";
  static final long COLL_OGM_ID = 99L;
  static final String CALLER = "alice";
  static final URI PRESIGNED_URI = URI.create("https://storage.example.com/shepard/exports/uuid.zip?X-Amz=sig");

  @Mock CollectionPropertiesDAO collectionPropertiesDAO;
  @Mock PermissionsService permissionsService;
  @Mock ExportService exportService;
  @Mock FileStorageRegistry fileStorageRegistry;
  @Mock FileStorage fileStorage;
  @Mock SecurityContext securityContext;
  @Mock Principal principal;

  CollectionExportUrlRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new CollectionExportUrlRest();
    resource.collectionPropertiesDAO = collectionPropertiesDAO;
    resource.permissionsService = permissionsService;
    resource.exportService = exportService;
    resource.fileStorageRegistry = fileStorageRegistry;

    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
  }

  @Test
  void returns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = resource.getExportUrl(COLL_APP_ID, securityContext, null);
    assertEquals(401, r.getStatus());
    verify(collectionPropertiesDAO, never()).findCollectionIdByAppId(anyString());
  }

  @Test
  void returns404WhenCollectionMissing() {
    when(collectionPropertiesDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.empty());
    Response r = resource.getExportUrl(COLL_APP_ID, securityContext, null);
    assertEquals(404, r.getStatus());
    verify(permissionsService, never()).isAccessTypeAllowedForUser(any(), any(), any());
  }

  @Test
  void returns403WhenCallerLacksReadPermission() {
    when(collectionPropertiesDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID, AccessType.Read, CALLER)).thenReturn(false);
    Response r = resource.getExportUrl(COLL_APP_ID, securityContext, null);
    assertEquals(403, r.getStatus());
  }

  @Test
  void throws503WhenNoActiveStorageProvider() {
    when(collectionPropertiesDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID, AccessType.Read, CALLER)).thenReturn(true);
    when(fileStorageRegistry.activeStorage()).thenReturn(Optional.empty());

    org.junit.jupiter.api.Assertions.assertThrows(
      ServiceUnavailableException.class,
      () -> resource.getExportUrl(COLL_APP_ID, securityContext, null)
    );
  }

  @Test
  void throws503WhenAdapterDoesNotSupportPresignedExport() throws Exception {
    when(collectionPropertiesDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID, AccessType.Read, CALLER)).thenReturn(true);
    when(fileStorageRegistry.activeStorage()).thenReturn(Optional.of(fileStorage));
    when(fileStorage.id()).thenReturn("gridfs");
    when(exportService.exportCollectionByShepardId(eq(COLL_OGM_ID), any()))
      .thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));
    when(fileStorage.presignedExportUrl(anyString(), any(byte[].class), anyString(), any()))
      .thenReturn(Optional.empty());

    org.junit.jupiter.api.Assertions.assertThrows(
      ServiceUnavailableException.class,
      () -> resource.getExportUrl(COLL_APP_ID, securityContext, null)
    );
  }

  @Test
  void returns200WithPresignedUrlOnHappyPath() throws Exception {
    when(collectionPropertiesDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID, AccessType.Read, CALLER)).thenReturn(true);
    when(fileStorageRegistry.activeStorage()).thenReturn(Optional.of(fileStorage));
    when(fileStorage.id()).thenReturn("s3");
    when(exportService.exportCollectionByShepardId(eq(COLL_OGM_ID), any()))
      .thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3, 4, 5}));
    when(fileStorage.presignedExportUrl(anyString(), any(byte[].class), anyString(), any()))
      .thenReturn(Optional.of(PRESIGNED_URI));

    Response r = resource.getExportUrl(COLL_APP_ID, securityContext, null);

    assertEquals(200, r.getStatus());
    ExportUrlIO io = (ExportUrlIO) r.getEntity();
    assertEquals(PRESIGNED_URI.toString(), io.getDownloadUrl());
    assertNotNull(io.getFileName());
    assertTrue(io.getFileName().endsWith("-export.zip"));
    assertNotNull(io.getExpiresAt());
  }

  @Test
  void throws500WhenExportBuildFails() throws Exception {
    when(collectionPropertiesDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID, AccessType.Read, CALLER)).thenReturn(true);
    when(fileStorageRegistry.activeStorage()).thenReturn(Optional.of(fileStorage));
    when(exportService.exportCollectionByShepardId(eq(COLL_OGM_ID), any()))
      .thenThrow(new IOException("disk full"));

    org.junit.jupiter.api.Assertions.assertThrows(
      InternalServerErrorException.class,
      () -> resource.getExportUrl(COLL_APP_ID, securityContext, null)
    );
  }

  @Test
  void throws500WhenStorageThrows() throws Exception {
    when(collectionPropertiesDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID, AccessType.Read, CALLER)).thenReturn(true);
    when(fileStorageRegistry.activeStorage()).thenReturn(Optional.of(fileStorage));
    when(fileStorage.id()).thenReturn("s3");
    when(exportService.exportCollectionByShepardId(eq(COLL_OGM_ID), any()))
      .thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));
    when(fileStorage.presignedExportUrl(anyString(), any(byte[].class), anyString(), any()))
      .thenThrow(new StorageException("S3 unreachable"));

    org.junit.jupiter.api.Assertions.assertThrows(
      InternalServerErrorException.class,
      () -> resource.getExportUrl(COLL_APP_ID, securityContext, null)
    );
  }
}
