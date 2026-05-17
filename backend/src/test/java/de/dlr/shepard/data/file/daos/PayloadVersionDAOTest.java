package de.dlr.shepard.data.file.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.data.file.entities.PayloadVersion;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

/**
 * PV1a — unit tests for {@link PayloadVersionDAO}.
 *
 * <p>All Neo4j interaction is mocked via a {@link Session} mock so no live
 * database is needed.
 */
class PayloadVersionDAOTest extends BaseTestCase {

  static final String CONTAINER_APP_ID = "01900000-0000-7000-8000-000000000001";
  static final String ORIGINAL_NAME = "sensor-data.csv";

  @Mock
  Session session;

  @InjectMocks
  PayloadVersionDAO dao;

  @Test
  void getEntityType_returnsPayloadVersionClass() {
    assertEquals(PayloadVersion.class, dao.getEntityType());
  }

  @Test
  void findByContainerAndName_returnsEmptyListWhenNoVersionsExist() {
    String query =
      "MATCH (v:PayloadVersion) " +
      "WHERE v.containerAppId = $containerAppId " +
      "  AND v.originalName   = $originalName " +
      "  AND (v.deleted IS NULL OR v.deleted = false) " +
      "RETURN v " +
      "ORDER BY v.versionNumber ASC";
    Map<String, Object> params = Map.of("containerAppId", CONTAINER_APP_ID, "originalName", ORIGINAL_NAME);

    when(session.query(PayloadVersion.class, query, params)).thenReturn(List.of());

    List<PayloadVersion> result = dao.findByContainerAndName(CONTAINER_APP_ID, ORIGINAL_NAME);
    assertTrue(result.isEmpty());
  }

  @Test
  void findByContainerAndName_returnsTwoVersionsInOrder() {
    String query =
      "MATCH (v:PayloadVersion) " +
      "WHERE v.containerAppId = $containerAppId " +
      "  AND v.originalName   = $originalName " +
      "  AND (v.deleted IS NULL OR v.deleted = false) " +
      "RETURN v " +
      "ORDER BY v.versionNumber ASC";
    Map<String, Object> params = Map.of("containerAppId", CONTAINER_APP_ID, "originalName", ORIGINAL_NAME);

    PayloadVersion v1 = buildVersion(1L, "oid1", "sha1");
    PayloadVersion v2 = buildVersion(2L, "oid2", "sha2");

    when(session.query(PayloadVersion.class, query, params)).thenReturn(List.of(v1, v2));

    List<PayloadVersion> result = dao.findByContainerAndName(CONTAINER_APP_ID, ORIGINAL_NAME);
    assertEquals(2, result.size());
    assertEquals(1L, result.get(0).getVersionNumber());
    assertEquals(2L, result.get(1).getVersionNumber());
  }

  @Test
  void findByAppId_returnsEmptyWhenNotFound() {
    String appId = "01900000-0000-7000-8000-000000000099";
    String query =
      "MATCH (v:PayloadVersion) " +
      "WHERE v.appId = $appId " +
      "  AND (v.deleted IS NULL OR v.deleted = false) " +
      "RETURN v";

    when(session.query(PayloadVersion.class, query, Map.of("appId", appId))).thenReturn(List.of());

    var result = dao.findByAppId(appId);
    assertFalse(result.isPresent());
  }

  @Test
  void findByAppId_returnsPresentWhenFound() {
    String appId = "01900000-0000-7000-8000-000000000099";
    String query =
      "MATCH (v:PayloadVersion) " +
      "WHERE v.appId = $appId " +
      "  AND (v.deleted IS NULL OR v.deleted = false) " +
      "RETURN v";

    PayloadVersion v = buildVersion(1L, "oid1", "sha1");
    v.setAppId(appId);

    when(session.query(PayloadVersion.class, query, Map.of("appId", appId))).thenReturn(List.of(v));

    var result = dao.findByAppId(appId);
    assertTrue(result.isPresent());
    assertEquals(appId, result.get().getAppId());
  }

  @Test
  void findMaxVersionNumber_returnsZeroWhenNoVersionsExist() {
    String query =
      "MATCH (v:PayloadVersion) " +
      "WHERE v.containerAppId = $containerAppId " +
      "  AND v.originalName   = $originalName " +
      "  AND (v.deleted IS NULL OR v.deleted = false) " +
      "RETURN coalesce(max(v.versionNumber), 0) AS maxVersion";
    Map<String, Object> params = Map.of("containerAppId", CONTAINER_APP_ID, "originalName", ORIGINAL_NAME);

    Result mockResult = buildResult(Map.of("maxVersion", 0L));
    when(session.query(query, params)).thenReturn(mockResult);

    long max = dao.findMaxVersionNumber(CONTAINER_APP_ID, ORIGINAL_NAME);
    assertEquals(0L, max);
  }

  @Test
  void findMaxVersionNumber_returnsThreeAfterThreeUploads() {
    String query =
      "MATCH (v:PayloadVersion) " +
      "WHERE v.containerAppId = $containerAppId " +
      "  AND v.originalName   = $originalName " +
      "  AND (v.deleted IS NULL OR v.deleted = false) " +
      "RETURN coalesce(max(v.versionNumber), 0) AS maxVersion";
    Map<String, Object> params = Map.of("containerAppId", CONTAINER_APP_ID, "originalName", ORIGINAL_NAME);

    Result mockResult = buildResult(Map.of("maxVersion", 3L));
    when(session.query(query, params)).thenReturn(mockResult);

    long max = dao.findMaxVersionNumber(CONTAINER_APP_ID, ORIGINAL_NAME);
    assertEquals(3L, max);
  }

  // ─── helpers ─────────────────────────────────────────────────────────────

  private static PayloadVersion buildVersion(long versionNumber, String fileOid, String sha256) {
    PayloadVersion v = new PayloadVersion();
    v.setVersionNumber(versionNumber);
    v.setFileOid(fileOid);
    v.setSha256(sha256);
    v.setContainerAppId(CONTAINER_APP_ID);
    v.setOriginalName(ORIGINAL_NAME);
    v.setUploadedBy("alice");
    v.setUploadedAt("2026-05-17T00:00:00Z");
    return v;
  }

  /**
   * Build a minimal {@link Result} stub that yields one row with the given map.
   */
  private static Result buildResult(Map<String, Object> row) {
    Result mockResult = mock(Result.class);
    when(mockResult.iterator()).thenReturn(List.of(row).iterator());
    return mockResult;
  }
}
