package de.dlr.shepard.v2.file.resources;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.mongoDB.NamedInputStream;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.file.entities.FileReference;
import de.dlr.shepard.context.references.file.services.SingletonFileReferenceService;
import de.dlr.shepard.data.file.entities.ShepardFile;
import de.dlr.shepard.v2.file.io.FileReferenceV2IO;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.Principal;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Plain-Mockito unit tests for {@link FileReferenceV2Rest} (FR1b, see
 * {@code aidocs/53 §1.8.4}). Same shape as
 * {@code FileBundleReferenceRestTest} — wires the resource by hand
 * and exercises every endpoint plus its 400 / 401 / 403 / 404 branches.
 */
class FileReferenceV2RestTest {

  private static final String SINGLETON_APP_ID = "singleton-app-1";
  private static final String PARENT_DO_APP_ID = "parent-do-app";
  private static final long PARENT_DO_OGM_ID = 42L;
  private static final String CALLER = "alice";

  @Mock
  SingletonFileReferenceService singletonService;

  @Mock
  PermissionsService permissionsService;

  @Mock
  SecurityContext securityContext;

  @Mock
  Principal principal;

  FileReferenceV2Rest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new FileReferenceV2Rest();
    resource.singletonService = singletonService;
    resource.permissionsService = permissionsService;
    resource.objectMapper = new ObjectMapper();
    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
    when(permissionsService.isAccessTypeAllowedForUser(eq(PARENT_DO_OGM_ID), any(AccessType.class), eq(CALLER), anyLong()))
      .thenReturn(true);
  }

  private FileReference existing() {
    var parent = new DataObject(PARENT_DO_OGM_ID);
    parent.setAppId(PARENT_DO_APP_ID);
    parent.setShepardId(101L);
    var ref = new FileReference(7L);
    ref.setAppId(SINGLETON_APP_ID);
    ref.setName("doc");
    ref.setDataObject(parent);
    var file = new ShepardFile(new Date(), "doc.pdf", "deadbeef");
    file.setOid("file-oid-1");
    file.setFileSize(1024L);
    ref.setFile(file);
    return ref;
  }

  // ─── GET /v2/files/{appId} ────────────────────────────────────────────────

  @Test
  void getSingleton_returns200() {
    when(singletonService.getByAppId(SINGLETON_APP_ID)).thenReturn(existing());
    var r = resource.getSingleton(SINGLETON_APP_ID, securityContext);
    assertEquals(200, r.getStatus());
    var io = (FileReferenceV2IO) r.getEntity();
    assertEquals(SINGLETON_APP_ID, io.getAppId());
    assertEquals("doc.pdf", io.getFile().getFilename());
  }

  @Test
  void getSingleton_returns404WhenMissing() {
    when(singletonService.getByAppId(SINGLETON_APP_ID)).thenReturn(null);
    assertEquals(404, resource.getSingleton(SINGLETON_APP_ID, securityContext).getStatus());
  }

  @Test
  void getSingleton_returns401Unauthenticated() {
    when(singletonService.getByAppId(SINGLETON_APP_ID)).thenReturn(existing());
    when(securityContext.getUserPrincipal()).thenReturn(null);
    assertEquals(401, resource.getSingleton(SINGLETON_APP_ID, securityContext).getStatus());
  }

  @Test
  void getSingleton_returns403WhenNoRead() {
    when(singletonService.getByAppId(SINGLETON_APP_ID)).thenReturn(existing());
    when(permissionsService.isAccessTypeAllowedForUser(PARENT_DO_OGM_ID, AccessType.Read, CALLER, anyLong())).thenReturn(false);
    assertEquals(403, resource.getSingleton(SINGLETON_APP_ID, securityContext).getStatus());
  }

  @Test
  void getSingleton_returns404WhenNoDataObject() {
    var orphan = existing();
    orphan.setDataObject(null);
    when(singletonService.getByAppId(SINGLETON_APP_ID)).thenReturn(orphan);
    assertEquals(404, resource.getSingleton(SINGLETON_APP_ID, securityContext).getStatus());
  }

  // ─── GET /v2/files/{appId}/content (no range) ─────────────────────────────

  @Test
  void getContent_returns200FullBody() throws Exception {
    when(singletonService.getByAppId(SINGLETON_APP_ID)).thenReturn(existing());
    var nis = new NamedInputStream("file-oid-1", new ByteArrayInputStream(new byte[] { 1, 2, 3, 4 }), "doc.pdf", 4L);
    when(singletonService.getPayload(SINGLETON_APP_ID)).thenReturn(nis);
    var r = resource.getContent(SINGLETON_APP_ID, null, securityContext);
    assertEquals(200, r.getStatus());
    assertEquals(4L, r.getHeaderString("Content-Length") == null ? null : Long.parseLong(r.getHeaderString("Content-Length")));
    assertEquals("bytes", r.getHeaderString("Accept-Ranges"));
  }

  @Test
  void getContent_returns404WhenMissing() {
    when(singletonService.getByAppId(SINGLETON_APP_ID)).thenReturn(null);
    assertEquals(404, resource.getContent(SINGLETON_APP_ID, null, securityContext).getStatus());
  }

  @Test
  void getContent_returns404WhenPayloadMissing() {
    when(singletonService.getByAppId(SINGLETON_APP_ID)).thenReturn(existing());
    when(singletonService.getPayload(SINGLETON_APP_ID)).thenThrow(new NotFoundException("gone"));
    assertEquals(404, resource.getContent(SINGLETON_APP_ID, null, securityContext).getStatus());
  }

  // ─── GET /v2/files/{appId}/content (with range) ───────────────────────────

  @Test
  void getContent_returns206PartialContent() throws Exception {
    when(singletonService.getByAppId(SINGLETON_APP_ID)).thenReturn(existing());
    byte[] body = new byte[] { 10, 20, 30, 40, 50 };
    var nis = new NamedInputStream("file-oid-1", new ByteArrayInputStream(body), "doc.pdf", 5L);
    when(singletonService.getPayload(SINGLETON_APP_ID)).thenReturn(nis);

    var r = resource.getContent(SINGLETON_APP_ID, "bytes=1-3", securityContext);
    assertEquals(206, r.getStatus());
    assertEquals("bytes 1-3/5", r.getHeaderString("Content-Range"));
    assertEquals(3L, Long.parseLong(r.getHeaderString("Content-Length")));

    // Drain the StreamingOutput and verify the bytes.
    StreamingOutput so = (StreamingOutput) r.getEntity();
    var baos = new ByteArrayOutputStream();
    so.write(baos);
    assertArrayEquals(new byte[] { 20, 30, 40 }, baos.toByteArray());
  }

  @Test
  void getContent_returns416OutOfRange() {
    when(singletonService.getByAppId(SINGLETON_APP_ID)).thenReturn(existing());
    var nis = new NamedInputStream("file-oid-1", new ByteArrayInputStream(new byte[] { 1, 2 }), "doc.pdf", 2L);
    when(singletonService.getPayload(SINGLETON_APP_ID)).thenReturn(nis);
    var r = resource.getContent(SINGLETON_APP_ID, "bytes=10-20", securityContext);
    assertEquals(416, r.getStatus());
    assertEquals("bytes */2", r.getHeaderString("Content-Range"));
  }

  // ─── parseRange (helper unit tests) ───────────────────────────────────────

  @Test
  void parseRange_acceptsClosedRange() {
    var actual = FileReferenceV2Rest.parseRange("bytes=2-4", 10L);
    assertNotNull(actual);
    assertEquals(2L, actual[0]);
    assertEquals(4L, actual[1]);
  }

  @Test
  void parseRange_acceptsOpenEnd() {
    var actual = FileReferenceV2Rest.parseRange("bytes=2-", 10L);
    assertNotNull(actual);
    assertEquals(2L, actual[0]);
    assertEquals(9L, actual[1]);
  }

  @Test
  void parseRange_rejectsSuffix() {
    assertNull(FileReferenceV2Rest.parseRange("bytes=-3", 10L));
  }

  @Test
  void parseRange_rejectsMultiRange() {
    assertNull(FileReferenceV2Rest.parseRange("bytes=0-1,3-4", 10L));
  }

  @Test
  void parseRange_rejectsMissingBytesPrefix() {
    assertNull(FileReferenceV2Rest.parseRange("kilobytes=0-3", 10L));
  }

  @Test
  void parseRange_rejectsStartGteTotal() {
    assertNull(FileReferenceV2Rest.parseRange("bytes=10-15", 10L));
  }

  @Test
  void parseRange_rejectsEndLessThanStart() {
    assertNull(FileReferenceV2Rest.parseRange("bytes=5-2", 10L));
  }

  @Test
  void parseRange_rejectsGarbage() {
    assertNull(FileReferenceV2Rest.parseRange("bytes=abc-def", 10L));
    assertNull(FileReferenceV2Rest.parseRange("bytes=", 10L));
    assertNull(FileReferenceV2Rest.parseRange("", 10L));
    assertNull(FileReferenceV2Rest.parseRange(null, 10L));
  }

  @Test
  void parseRange_clampsEndAtTotalMinusOne() {
    var actual = FileReferenceV2Rest.parseRange("bytes=2-999", 10L);
    assertNotNull(actual);
    assertEquals(9L, actual[1]);
  }

  // ─── POST /v2/files (upload) ──────────────────────────────────────────────

  @Test
  void createSingleton_returns400WhenParentMissing() {
    var r = resource.createSingleton(null, "name", null, securityContext);
    assertEquals(400, r.getStatus());
  }

  @Test
  void createSingleton_returns400WhenBlankParent() {
    var r = resource.createSingleton("  ", "name", null, securityContext);
    assertEquals(400, r.getStatus());
  }

  @Test
  void createSingleton_returns400WhenNoUpload() {
    var r = resource.createSingleton(PARENT_DO_APP_ID, "name", null, securityContext);
    assertEquals(400, r.getStatus());
  }

  @Test
  void createSingleton_returns401Unauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    var r = resource.createSingleton(PARENT_DO_APP_ID, "name", null, securityContext);
    assertEquals(401, r.getStatus());
  }

  @Test
  void createSingleton_happyPath_returns201() throws Exception {
    // FileUpload mock backed by a real tmp file (the resource opens
    // a FileInputStream over it).
    var tmp = java.nio.file.Files.createTempFile("fr1b-test-upload-", ".bin");
    java.nio.file.Files.write(tmp, new byte[] { 1, 2, 3, 4 });
    try {
      var fileUpload = org.mockito.Mockito.mock(org.jboss.resteasy.reactive.multipart.FileUpload.class);
      when(fileUpload.uploadedFile()).thenReturn(tmp);
      when(fileUpload.fileName()).thenReturn("doc.pdf");

      when(singletonService.getDataObjectOgmId(PARENT_DO_APP_ID)).thenReturn(PARENT_DO_OGM_ID);
      var created = existing();
      when(singletonService.createSingleton(eq(PARENT_DO_APP_ID), anyString(), eq("doc.pdf"), any()))
        .thenReturn(created);

      var r = resource.createSingleton(PARENT_DO_APP_ID, "custom name", fileUpload, securityContext);
      assertEquals(201, r.getStatus());
      assertEquals(SINGLETON_APP_ID, ((FileReferenceV2IO) r.getEntity()).getAppId());
    } finally {
      java.nio.file.Files.deleteIfExists(tmp);
    }
  }

  @Test
  void createSingleton_returns404WhenParentDataObjectMissing() throws Exception {
    var tmp = java.nio.file.Files.createTempFile("fr1b-test-upload-", ".bin");
    java.nio.file.Files.write(tmp, new byte[] { 1 });
    try {
      var fileUpload = org.mockito.Mockito.mock(org.jboss.resteasy.reactive.multipart.FileUpload.class);
      when(fileUpload.uploadedFile()).thenReturn(tmp);
      when(fileUpload.fileName()).thenReturn("doc.pdf");
      when(singletonService.getDataObjectOgmId(PARENT_DO_APP_ID)).thenReturn(null);

      var r = resource.createSingleton(PARENT_DO_APP_ID, null, fileUpload, securityContext);
      assertEquals(404, r.getStatus());
    } finally {
      java.nio.file.Files.deleteIfExists(tmp);
    }
  }

  @Test
  void createSingleton_returns403WhenNoWritePermission() throws Exception {
    var tmp = java.nio.file.Files.createTempFile("fr1b-test-upload-", ".bin");
    java.nio.file.Files.write(tmp, new byte[] { 1 });
    try {
      var fileUpload = org.mockito.Mockito.mock(org.jboss.resteasy.reactive.multipart.FileUpload.class);
      when(fileUpload.uploadedFile()).thenReturn(tmp);
      when(fileUpload.fileName()).thenReturn("doc.pdf");
      when(singletonService.getDataObjectOgmId(PARENT_DO_APP_ID)).thenReturn(PARENT_DO_OGM_ID);
      when(permissionsService.isAccessTypeAllowedForUser(PARENT_DO_OGM_ID, AccessType.Write, CALLER, anyLong())).thenReturn(false);

      var r = resource.createSingleton(PARENT_DO_APP_ID, null, fileUpload, securityContext);
      assertEquals(403, r.getStatus());
    } finally {
      java.nio.file.Files.deleteIfExists(tmp);
    }
  }

  @Test
  void createSingleton_returns400WhenServiceRejects() throws Exception {
    var tmp = java.nio.file.Files.createTempFile("fr1b-test-upload-", ".bin");
    java.nio.file.Files.write(tmp, new byte[] { 1 });
    try {
      var fileUpload = org.mockito.Mockito.mock(org.jboss.resteasy.reactive.multipart.FileUpload.class);
      when(fileUpload.uploadedFile()).thenReturn(tmp);
      when(fileUpload.fileName()).thenReturn("doc.pdf");
      when(singletonService.getDataObjectOgmId(PARENT_DO_APP_ID)).thenReturn(PARENT_DO_OGM_ID);
      when(singletonService.createSingleton(anyString(), anyString(), anyString(), any()))
        .thenThrow(new BadRequestException("bad"));

      var r = resource.createSingleton(PARENT_DO_APP_ID, null, fileUpload, securityContext);
      assertEquals(400, r.getStatus());
    } finally {
      java.nio.file.Files.deleteIfExists(tmp);
    }
  }

  @Test
  void createSingleton_returns404WhenServiceRaisesNotFound() throws Exception {
    var tmp = java.nio.file.Files.createTempFile("fr1b-test-upload-", ".bin");
    java.nio.file.Files.write(tmp, new byte[] { 1 });
    try {
      var fileUpload = org.mockito.Mockito.mock(org.jboss.resteasy.reactive.multipart.FileUpload.class);
      when(fileUpload.uploadedFile()).thenReturn(tmp);
      when(fileUpload.fileName()).thenReturn("doc.pdf");
      when(singletonService.getDataObjectOgmId(PARENT_DO_APP_ID)).thenReturn(PARENT_DO_OGM_ID);
      when(singletonService.createSingleton(anyString(), anyString(), anyString(), any()))
        .thenThrow(new NotFoundException("race"));

      var r = resource.createSingleton(PARENT_DO_APP_ID, null, fileUpload, securityContext);
      assertEquals(404, r.getStatus());
    } finally {
      java.nio.file.Files.deleteIfExists(tmp);
    }
  }

  // ─── PATCH /v2/files/{appId} ──────────────────────────────────────────────

  @Test
  void patchSingleton_returns200WithUpdated() throws Exception {
    when(singletonService.getByAppId(SINGLETON_APP_ID)).thenReturn(existing());
    var updated = existing();
    updated.setName("new-name");
    when(singletonService.patchSingleton(eq(SINGLETON_APP_ID), any())).thenReturn(updated);

    JsonNode body = new ObjectMapper().readTree("{\"name\": \"new-name\"}");
    var r = resource.patchSingleton(SINGLETON_APP_ID, body, securityContext);
    assertEquals(200, r.getStatus());
    var io = (FileReferenceV2IO) r.getEntity();
    assertEquals("new-name", io.getName());
  }

  @Test
  void patchSingleton_returns400OnNullBody() {
    when(singletonService.getByAppId(SINGLETON_APP_ID)).thenReturn(existing());
    assertEquals(400, resource.patchSingleton(SINGLETON_APP_ID, null, securityContext).getStatus());
  }

  @Test
  void patchSingleton_returns400OnArrayBody() throws Exception {
    when(singletonService.getByAppId(SINGLETON_APP_ID)).thenReturn(existing());
    JsonNode body = new ObjectMapper().readTree("[1,2,3]");
    assertEquals(400, resource.patchSingleton(SINGLETON_APP_ID, body, securityContext).getStatus());
  }

  @Test
  void patchSingleton_returns404WhenMissing() throws Exception {
    when(singletonService.getByAppId(SINGLETON_APP_ID)).thenReturn(null);
    JsonNode body = new ObjectMapper().readTree("{\"name\":\"x\"}");
    assertEquals(404, resource.patchSingleton(SINGLETON_APP_ID, body, securityContext).getStatus());
  }

  @Test
  void patchSingleton_returns403WhenNoWrite() throws Exception {
    when(singletonService.getByAppId(SINGLETON_APP_ID)).thenReturn(existing());
    when(permissionsService.isAccessTypeAllowedForUser(PARENT_DO_OGM_ID, AccessType.Write, CALLER, anyLong())).thenReturn(false);
    JsonNode body = new ObjectMapper().readTree("{\"name\":\"x\"}");
    assertEquals(403, resource.patchSingleton(SINGLETON_APP_ID, body, securityContext).getStatus());
  }

  @Test
  void patchSingleton_returns400WhenServiceRejects() throws Exception {
    when(singletonService.getByAppId(SINGLETON_APP_ID)).thenReturn(existing());
    when(singletonService.patchSingleton(eq(SINGLETON_APP_ID), any()))
      .thenThrow(new BadRequestException("name must not be null or blank"));
    JsonNode body = new ObjectMapper().readTree("{\"name\":\"  \"}");
    assertEquals(400, resource.patchSingleton(SINGLETON_APP_ID, body, securityContext).getStatus());
  }

  // ─── DELETE /v2/files/{appId} ─────────────────────────────────────────────

  @Test
  void deleteSingleton_returns204() {
    when(singletonService.getByAppId(SINGLETON_APP_ID)).thenReturn(existing());
    var r = resource.deleteSingleton(SINGLETON_APP_ID, securityContext);
    assertEquals(204, r.getStatus());
    verify(singletonService).deleteSingleton(SINGLETON_APP_ID);
  }

  @Test
  void deleteSingleton_returns404WhenMissing() {
    when(singletonService.getByAppId(SINGLETON_APP_ID)).thenReturn(null);
    assertEquals(404, resource.deleteSingleton(SINGLETON_APP_ID, securityContext).getStatus());
  }

  @Test
  void deleteSingleton_returns403WhenNoWrite() {
    when(singletonService.getByAppId(SINGLETON_APP_ID)).thenReturn(existing());
    when(permissionsService.isAccessTypeAllowedForUser(PARENT_DO_OGM_ID, AccessType.Write, CALLER, anyLong())).thenReturn(false);
    assertEquals(403, resource.deleteSingleton(SINGLETON_APP_ID, securityContext).getStatus());
  }

  @Test
  void deleteSingleton_returns404WhenServiceRaisesAfterGate() {
    when(singletonService.getByAppId(SINGLETON_APP_ID)).thenReturn(existing());
    doThrow(new NotFoundException("race")).when(singletonService).deleteSingleton(SINGLETON_APP_ID);
    assertEquals(404, resource.deleteSingleton(SINGLETON_APP_ID, securityContext).getStatus());
  }

  // ─── jsonNodeToMap (helper) ────────────────────────────────────────────────

  @Test
  void jsonNodeToMap_preservesScalars() throws Exception {
    JsonNode body = new ObjectMapper().readTree(
      "{\"s\":\"x\",\"i\":42,\"d\":3.14,\"b\":true,\"n\":null,\"obj\":{\"k\":\"v\",\"e\":null}}"
    );
    Map<String, Object> out = resource.jsonNodeToMap(body);
    assertEquals("x", out.get("s"));
    assertEquals(42L, out.get("i"));
    assertEquals(3.14, (double) out.get("d"), 0.0001);
    assertEquals(true, out.get("b"));
    assertTrue(out.containsKey("n"));
    assertNull(out.get("n"));
    @SuppressWarnings("unchecked")
    Map<String, Object> sub = (Map<String, Object>) out.get("obj");
    assertEquals("v", sub.get("k"));
    assertNull(sub.get("e"));
  }
}
