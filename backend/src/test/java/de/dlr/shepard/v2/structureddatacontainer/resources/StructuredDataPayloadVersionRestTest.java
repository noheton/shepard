package de.dlr.shepard.v2.structureddatacontainer.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.data.file.daos.PayloadVersionDAO;
import de.dlr.shepard.data.file.entities.PayloadVersion;
import de.dlr.shepard.data.structureddata.entities.StructuredDataContainer;
import de.dlr.shepard.data.structureddata.services.StructuredDataContainerService;
import de.dlr.shepard.v2.file.io.PayloadVersionIO;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * PV1b — plain-Mockito unit tests for {@link StructuredDataPayloadVersionRest}.
 * Wires the resource by hand; no CDI or Neo4j required.
 */
@SuppressWarnings("unchecked")
class StructuredDataPayloadVersionRestTest {

  static final String CONTAINER_APP_ID = "01900000-0000-7000-8000-000000000002";
  static final long   CONTAINER_OGM_ID = 42L;
  static final String ENTRY_NAME       = "sensor-readings";

  @Mock
  StructuredDataContainerService service;

  @Mock
  PayloadVersionDAO dao;

  StructuredDataPayloadVersionRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new StructuredDataPayloadVersionRest();
    resource.structuredDataContainerService = service;
    resource.payloadVersionDAO = dao;
  }

  private StructuredDataContainer container() {
    var c = new StructuredDataContainer(CONTAINER_OGM_ID);
    c.setAppId(CONTAINER_APP_ID);
    return c;
  }

  // ─── listVersions ─────────────────────────────────────────────────────────

  @Test
  void listVersions_returns200WithEmptyArrayWhenNoVersionsExist() {
    when(service.getContainerByAppId(CONTAINER_APP_ID)).thenReturn(container());
    when(dao.findByContainerAndName(CONTAINER_APP_ID, ENTRY_NAME)).thenReturn(List.of());

    Response resp = resource.listVersions(CONTAINER_APP_ID, ENTRY_NAME);

    assertEquals(200, resp.getStatus());
    List<PayloadVersionIO> body = (List<PayloadVersionIO>) resp.getEntity();
    assertEquals(0, body.size());
  }

  @Test
  void listVersions_returns200WithOneVersionAfterSingleUpload() {
    when(service.getContainerByAppId(CONTAINER_APP_ID)).thenReturn(container());
    PayloadVersion v1 = buildVersion("appId-1", 1L, "mongoOid1",
      "E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855", 512L, "alice");
    when(dao.findByContainerAndName(CONTAINER_APP_ID, ENTRY_NAME)).thenReturn(List.of(v1));

    Response resp = resource.listVersions(CONTAINER_APP_ID, ENTRY_NAME);

    assertEquals(200, resp.getStatus());
    List<PayloadVersionIO> body = (List<PayloadVersionIO>) resp.getEntity();
    assertEquals(1, body.size());
    PayloadVersionIO io = body.get(0);
    assertEquals("appId-1", io.appId());
    assertEquals(1L, io.versionNumber());
    assertEquals("mongoOid1", io.fileOid());
    assertEquals("E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855", io.sha256());
    assertEquals(512L, io.sizeBytes());
    assertEquals("alice", io.uploadedBy());
    assertEquals("2026-05-17T00:00:00Z", io.uploadedAt());
  }

  @Test
  void listVersions_returns200WithTwoVersionsAfterTwoUploads() {
    when(service.getContainerByAppId(CONTAINER_APP_ID)).thenReturn(container());
    PayloadVersion v1 = buildVersion("appId-1", 1L, "oid1", "SHA1", 100L, "alice");
    PayloadVersion v2 = buildVersion("appId-2", 2L, "oid2", "SHA2", 200L, "bob");
    when(dao.findByContainerAndName(CONTAINER_APP_ID, ENTRY_NAME)).thenReturn(List.of(v1, v2));

    Response resp = resource.listVersions(CONTAINER_APP_ID, ENTRY_NAME);

    assertEquals(200, resp.getStatus());
    List<PayloadVersionIO> body = (List<PayloadVersionIO>) resp.getEntity();
    assertEquals(2, body.size());
    assertEquals(1L, body.get(0).versionNumber());
    assertEquals(2L, body.get(1).versionNumber());
    assertEquals("alice", body.get(0).uploadedBy());
    assertEquals("bob",   body.get(1).uploadedBy());
  }

  @Test
  void listVersions_callsPermissionCheckViaGetContainerByAppId() {
    when(service.getContainerByAppId(CONTAINER_APP_ID)).thenReturn(container());
    when(dao.findByContainerAndName(CONTAINER_APP_ID, ENTRY_NAME)).thenReturn(List.of());

    resource.listVersions(CONTAINER_APP_ID, ENTRY_NAME);

    // Verify that the permission check (delegated to getContainerByAppId) was invoked.
    verify(service).getContainerByAppId(CONTAINER_APP_ID);
  }

  // ─── helpers ─────────────────────────────────────────────────────────────

  private static PayloadVersion buildVersion(
    String appId, long versionNumber, String fileOid,
    String sha256, Long sizeBytes, String uploadedBy
  ) {
    PayloadVersion v = new PayloadVersion();
    v.setAppId(appId);
    v.setVersionNumber(versionNumber);
    v.setFileOid(fileOid);
    v.setSha256(sha256);
    v.setSizeBytes(sizeBytes);
    v.setUploadedBy(uploadedBy);
    v.setUploadedAt("2026-05-17T00:00:00Z");
    v.setContainerAppId(CONTAINER_APP_ID);
    v.setOriginalName(ENTRY_NAME);
    return v;
  }
}
