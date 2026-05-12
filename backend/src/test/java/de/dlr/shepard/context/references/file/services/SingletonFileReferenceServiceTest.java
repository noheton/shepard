package de.dlr.shepard.context.references.file.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.mongoDB.NamedInputStream;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.file.daos.SingletonFileReferenceDAO;
import de.dlr.shepard.context.references.file.entities.FileReference;
import de.dlr.shepard.data.file.entities.ShepardFile;
import de.dlr.shepard.data.file.services.FileService;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Plain-Mockito unit tests for {@link SingletonFileReferenceService}
 * (FR1b, see {@code aidocs/53 §1.8}). Same shape as
 * {@code FileGroupServiceTest} — wires the service by hand, exercises
 * every public method.
 */
class SingletonFileReferenceServiceTest {

  private static final String DO_APP_ID = "do-app-7";
  private static final long DO_OGM_ID = 7L;
  private static final String SINGLETON_APP_ID = "singleton-app-1";

  @Mock
  SingletonFileReferenceDAO singletonDao;

  @Mock
  DataObjectDAO dataObjectDAO;

  @Mock
  FileService fileService;

  @Mock
  UserService userService;

  @Mock
  DateHelper dateHelper;

  @Mock
  EntityIdResolver entityIdResolver;

  SingletonFileReferenceService service;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new SingletonFileReferenceService();
    service.singletonFileReferenceDAO = singletonDao;
    service.dataObjectDAO = dataObjectDAO;
    service.fileService = fileService;
    service.userService = userService;
    service.dateHelper = dateHelper;
    service.entityIdResolver = entityIdResolver;
    when(dateHelper.getDate()).thenReturn(new Date(1_700_000_000L));
    when(userService.getCurrentUser()).thenReturn(new User("alice", "Alice", "Tester", "alice@example.org"));
  }

  // ─── getByAppId ───────────────────────────────────────────────────────────

  @Test
  void getByAppId_returnsRow() {
    FileReference row = new FileReference(1L);
    row.setAppId(SINGLETON_APP_ID);
    when(singletonDao.findByAppId(SINGLETON_APP_ID)).thenReturn(row);
    FileReference actual = service.getByAppId(SINGLETON_APP_ID);
    assertEquals(row, actual);
  }

  @Test
  void getByAppId_returnsNullWhenMissing() {
    when(singletonDao.findByAppId(SINGLETON_APP_ID)).thenReturn(null);
    assertNull(service.getByAppId(SINGLETON_APP_ID));
  }

  // ─── listByDataObject ─────────────────────────────────────────────────────

  @Test
  void listByDataObject_returnsRows() {
    var s1 = new FileReference(1L);
    var s2 = new FileReference(2L);
    when(singletonDao.findByDataObjectAppId(DO_APP_ID)).thenReturn(List.of(s1, s2));
    var actual = service.listByDataObject(DO_APP_ID);
    assertEquals(2, actual.size());
  }

  // ─── createSingleton ──────────────────────────────────────────────────────

  @Test
  void createSingleton_returnsSavedRow() {
    var parent = new DataObject(DO_OGM_ID);
    parent.setAppId(DO_APP_ID);
    parent.setShepardId(101L);
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenReturn(DO_OGM_ID);
    when(dataObjectDAO.findByNeo4jId(DO_OGM_ID)).thenReturn(parent);

    var savedFile = new ShepardFile(new Date(), "doc.pdf", "deadbeef");
    savedFile.setOid("file-oid-1");
    when(fileService.createFile(eq(SingletonFileReferenceService.SHARED_FILES_NAMESPACE), eq("doc.pdf"), any(InputStream.class)))
      .thenReturn(savedFile);

    // The DAO's createOrUpdate sets a shepardId on the row.
    when(singletonDao.createOrUpdate(any(FileReference.class))).thenAnswer(invocation -> {
      FileReference r = invocation.getArgument(0);
      // first save: assign appId + shepardId
      if (r.getAppId() == null) r.setAppId(SINGLETON_APP_ID);
      r.setShepardId(123L);
      return r;
    });

    InputStream payload = new ByteArrayInputStream(new byte[] { 1, 2, 3 });
    FileReference created = service.createSingleton(DO_APP_ID, "PDF protocol", "doc.pdf", payload);
    assertNotNull(created);
    assertEquals("PDF protocol", created.getName());
    assertEquals(savedFile, created.getFile());
    assertEquals(parent, created.getDataObject());
    verify(fileService).createFile(eq(SingletonFileReferenceService.SHARED_FILES_NAMESPACE), eq("doc.pdf"), any(InputStream.class));
    // Two createOrUpdate calls (FR1a idiom: save once then set shepardId + save again)
    verify(singletonDao, times(2)).createOrUpdate(any(FileReference.class));
  }

  @Test
  void createSingleton_throwsBadRequestOnBlankName() {
    InputStream payload = new ByteArrayInputStream(new byte[] { 1 });
    assertThrows(BadRequestException.class, () ->
      service.createSingleton(DO_APP_ID, "  ", "doc.pdf", payload)
    );
  }

  @Test
  void createSingleton_throwsBadRequestOnNullName() {
    InputStream payload = new ByteArrayInputStream(new byte[] { 1 });
    assertThrows(BadRequestException.class, () ->
      service.createSingleton(DO_APP_ID, null, "doc.pdf", payload)
    );
  }

  @Test
  void createSingleton_throwsBadRequestOnNullFilename() {
    InputStream payload = new ByteArrayInputStream(new byte[] { 1 });
    assertThrows(BadRequestException.class, () ->
      service.createSingleton(DO_APP_ID, "name", null, payload)
    );
  }

  @Test
  void createSingleton_throwsBadRequestOnNullPayload() {
    assertThrows(BadRequestException.class, () ->
      service.createSingleton(DO_APP_ID, "name", "doc.pdf", null)
    );
  }

  @Test
  void createSingleton_throwsNotFoundWhenDataObjectMissing() {
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenThrow(new NotFoundException("nope"));
    InputStream payload = new ByteArrayInputStream(new byte[] { 1 });
    assertThrows(NotFoundException.class, () ->
      service.createSingleton(DO_APP_ID, "name", "doc.pdf", payload)
    );
  }

  @Test
  void createSingleton_throwsNotFoundWhenDataObjectDeleted() {
    var parent = new DataObject(DO_OGM_ID);
    parent.setAppId(DO_APP_ID);
    parent.setDeleted(true);
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenReturn(DO_OGM_ID);
    when(dataObjectDAO.findByNeo4jId(DO_OGM_ID)).thenReturn(parent);
    InputStream payload = new ByteArrayInputStream(new byte[] { 1 });
    assertThrows(NotFoundException.class, () ->
      service.createSingleton(DO_APP_ID, "name", "doc.pdf", payload)
    );
  }

  // ─── patchSingleton ───────────────────────────────────────────────────────

  @Test
  void patchSingleton_updatesName() {
    var existing = new FileReference(1L);
    existing.setName("old");
    existing.setAppId(SINGLETON_APP_ID);
    when(singletonDao.findByAppId(SINGLETON_APP_ID)).thenReturn(existing);
    when(singletonDao.createOrUpdate(any(FileReference.class))).thenAnswer(inv -> inv.getArgument(0));

    var updated = service.patchSingleton(SINGLETON_APP_ID, Map.of("name", "new"));
    assertEquals("new", updated.getName());
  }

  @Test
  void patchSingleton_ignoresUnknownFields() {
    var existing = new FileReference(1L);
    existing.setName("keep-me");
    existing.setAppId(SINGLETON_APP_ID);
    when(singletonDao.findByAppId(SINGLETON_APP_ID)).thenReturn(existing);
    when(singletonDao.createOrUpdate(any(FileReference.class))).thenAnswer(inv -> inv.getArgument(0));

    var updated = service.patchSingleton(SINGLETON_APP_ID, Map.of("unknown", "x"));
    assertEquals("keep-me", updated.getName());
  }

  @Test
  void patchSingleton_throwsBadRequestOnBlankName() {
    var existing = new FileReference(1L);
    existing.setAppId(SINGLETON_APP_ID);
    when(singletonDao.findByAppId(SINGLETON_APP_ID)).thenReturn(existing);
    assertThrows(BadRequestException.class, () ->
      service.patchSingleton(SINGLETON_APP_ID, java.util.Collections.singletonMap("name", "  "))
    );
  }

  @Test
  void patchSingleton_throwsBadRequestOnNullName() {
    var existing = new FileReference(1L);
    existing.setAppId(SINGLETON_APP_ID);
    when(singletonDao.findByAppId(SINGLETON_APP_ID)).thenReturn(existing);
    var patch = new java.util.HashMap<String, Object>();
    patch.put("name", null);
    assertThrows(BadRequestException.class, () ->
      service.patchSingleton(SINGLETON_APP_ID, patch)
    );
  }

  @Test
  void patchSingleton_throwsNotFoundWhenMissing() {
    when(singletonDao.findByAppId(SINGLETON_APP_ID)).thenReturn(null);
    assertThrows(NotFoundException.class, () -> service.patchSingleton(SINGLETON_APP_ID, Map.of("name", "x")));
  }

  @Test
  void patchSingleton_throwsBadRequestOnNullPatch() {
    var existing = new FileReference(1L);
    existing.setAppId(SINGLETON_APP_ID);
    when(singletonDao.findByAppId(SINGLETON_APP_ID)).thenReturn(existing);
    assertThrows(BadRequestException.class, () -> service.patchSingleton(SINGLETON_APP_ID, null));
  }

  // ─── deleteSingleton ──────────────────────────────────────────────────────

  @Test
  void deleteSingleton_softDeletesAndRemovesBytes() {
    var existing = new FileReference(1L);
    existing.setAppId(SINGLETON_APP_ID);
    var file = new ShepardFile(new Date(), "doc.pdf", "abc");
    file.setOid("file-oid-1");
    existing.setFile(file);
    when(singletonDao.findByAppId(SINGLETON_APP_ID)).thenReturn(existing);
    when(singletonDao.createOrUpdate(any(FileReference.class))).thenAnswer(inv -> inv.getArgument(0));

    assertDoesNotThrow(() -> service.deleteSingleton(SINGLETON_APP_ID));
    verify(fileService).deleteFile(SingletonFileReferenceService.SHARED_FILES_NAMESPACE, "file-oid-1");
  }

  @Test
  void deleteSingleton_swallowsAlreadyMissingGridFSBlob() {
    var existing = new FileReference(1L);
    existing.setAppId(SINGLETON_APP_ID);
    var file = new ShepardFile(new Date(), "doc.pdf", "abc");
    file.setOid("file-oid-1");
    existing.setFile(file);
    when(singletonDao.findByAppId(SINGLETON_APP_ID)).thenReturn(existing);
    when(singletonDao.createOrUpdate(any(FileReference.class))).thenAnswer(inv -> inv.getArgument(0));
    org.mockito.Mockito.doThrow(new NotFoundException("gone")).when(fileService).deleteFile(anyString(), anyString());

    // Should still complete cleanly — the Neo4j-side soft-delete is the source of truth.
    assertDoesNotThrow(() -> service.deleteSingleton(SINGLETON_APP_ID));
  }

  @Test
  void deleteSingleton_handlesMissingFile() {
    var existing = new FileReference(1L);
    existing.setAppId(SINGLETON_APP_ID);
    existing.setFile(null);
    when(singletonDao.findByAppId(SINGLETON_APP_ID)).thenReturn(existing);
    when(singletonDao.createOrUpdate(any(FileReference.class))).thenAnswer(inv -> inv.getArgument(0));
    assertDoesNotThrow(() -> service.deleteSingleton(SINGLETON_APP_ID));
    verify(fileService, never()).deleteFile(anyString(), anyString());
  }

  @Test
  void deleteSingleton_throwsNotFoundWhenMissing() {
    when(singletonDao.findByAppId(SINGLETON_APP_ID)).thenReturn(null);
    assertThrows(NotFoundException.class, () -> service.deleteSingleton(SINGLETON_APP_ID));
  }

  // ─── getPayload ───────────────────────────────────────────────────────────

  @Test
  void getPayload_returnsStream() {
    var existing = new FileReference(1L);
    existing.setAppId(SINGLETON_APP_ID);
    var file = new ShepardFile(new Date(), "doc.pdf", "abc");
    file.setOid("file-oid-1");
    existing.setFile(file);
    when(singletonDao.findByAppId(SINGLETON_APP_ID)).thenReturn(existing);
    var nis = new NamedInputStream("file-oid-1", new ByteArrayInputStream(new byte[] { 1, 2 }), "doc.pdf", 2L);
    when(fileService.getPayload(SingletonFileReferenceService.SHARED_FILES_NAMESPACE, "file-oid-1")).thenReturn(nis);
    var actual = service.getPayload(SINGLETON_APP_ID);
    assertEquals("doc.pdf", actual.getName());
  }

  @Test
  void getPayload_throwsNotFoundWhenMissing() {
    when(singletonDao.findByAppId(SINGLETON_APP_ID)).thenReturn(null);
    assertThrows(NotFoundException.class, () -> service.getPayload(SINGLETON_APP_ID));
  }

  @Test
  void getPayload_throwsNotFoundWhenNoFile() {
    var existing = new FileReference(1L);
    existing.setAppId(SINGLETON_APP_ID);
    existing.setFile(null);
    when(singletonDao.findByAppId(SINGLETON_APP_ID)).thenReturn(existing);
    assertThrows(NotFoundException.class, () -> service.getPayload(SINGLETON_APP_ID));
  }

  // ─── getDataObjectOgmId ───────────────────────────────────────────────────

  @Test
  void getDataObjectOgmId_returnsResolved() {
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenReturn(DO_OGM_ID);
    assertEquals(DO_OGM_ID, service.getDataObjectOgmId(DO_APP_ID));
  }

  @Test
  void getDataObjectOgmId_returnsNullForBlank() {
    assertNull(service.getDataObjectOgmId(""));
    assertNull(service.getDataObjectOgmId(null));
  }

  @Test
  void getDataObjectOgmId_returnsNullWhenUnknown() {
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenThrow(new NotFoundException("nope"));
    assertNull(service.getDataObjectOgmId(DO_APP_ID));
  }
}
