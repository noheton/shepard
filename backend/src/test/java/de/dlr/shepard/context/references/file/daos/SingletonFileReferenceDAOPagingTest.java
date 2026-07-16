package de.dlr.shepard.context.references.file.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.context.references.file.entities.FileReference;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

/**
 * APISIMP-REFS-INMEM-PAGING — unit tests for the Cypher COUNT + SKIP/LIMIT
 * methods added to {@link SingletonFileReferenceDAO}. Verifies that both methods
 * push predicates and pagination to Neo4j rather than loading all rows.
 */
class SingletonFileReferenceDAOPagingTest extends BaseTestCase {

  @Mock
  private Session session;

  @Mock
  private EntityIdResolver entityIdResolver;

  @InjectMocks
  private SingletonFileReferenceDAO dao;

  // ── countByDataObjectAppId ────────────────────────────────────────────────

  @Test
  void countByDataObjectAppId_noSubKind_returnsCount() {
    String doAppId = "do-app-file-1";
    String expectedQuery =
      "MATCH (d:DataObject {appId: $aid})-[:has_reference]->(r:SingletonFileReference) " +
      "WHERE (r.deleted IS NULL OR r.deleted = false) " +
      "RETURN count(r) AS cnt";
    Result r = mock(Result.class);
    when(r.iterator()).thenReturn(
      List.<Map<String, Object>>of(Map.<String, Object>of("cnt", 12L)).iterator()
    );
    when(session.query(expectedQuery, Map.of("aid", doAppId))).thenReturn(r);

    int count = dao.countByDataObjectAppId(doAppId, null);

    verify(session).query(expectedQuery, Map.of("aid", doAppId));
    assertEquals(12, count);
  }

  @Test
  void countByDataObjectAppId_withSubKind_includesFileKindClause() {
    String doAppId = "do-app-file-2";
    String subKind = "urdf";
    String expectedQuery =
      "MATCH (d:DataObject {appId: $aid})-[:has_reference]->(r:SingletonFileReference) " +
      "WHERE (r.deleted IS NULL OR r.deleted = false) " +
      "AND r.fileKind = $subKind " +
      "RETURN count(r) AS cnt";
    Result r = mock(Result.class);
    when(r.iterator()).thenReturn(
      List.<Map<String, Object>>of(Map.<String, Object>of("cnt", 3L)).iterator()
    );
    when(session.query(expectedQuery, Map.of("aid", doAppId, "subKind", subKind))).thenReturn(r);

    int count = dao.countByDataObjectAppId(doAppId, subKind);

    verify(session).query(expectedQuery, Map.of("aid", doAppId, "subKind", subKind));
    assertEquals(3, count);
  }

  @Test
  void countByDataObjectAppId_emptyResult_returnsZero() {
    String doAppId = "do-app-file-3";
    String expectedQuery =
      "MATCH (d:DataObject {appId: $aid})-[:has_reference]->(r:SingletonFileReference) " +
      "WHERE (r.deleted IS NULL OR r.deleted = false) " +
      "RETURN count(r) AS cnt";
    Result r = mock(Result.class);
    when(r.iterator()).thenReturn(Collections.<Map<String, Object>>emptyList().iterator());
    when(session.query(expectedQuery, Map.of("aid", doAppId))).thenReturn(r);

    assertEquals(0, dao.countByDataObjectAppId(doAppId, null));
  }

  // ── findByDataObjectAppId (paginated) ────────────────────────────────────

  @Test
  void findByDataObjectAppId_noSubKind_pushesSkipLimit() {
    String doAppId = "do-app-file-4";
    String expectedQuery =
      "MATCH (d:DataObject {appId: $aid})-[hr:has_reference]->(r:SingletonFileReference) " +
      "WHERE (r.deleted IS NULL OR r.deleted = false) " +
      "OPTIONAL MATCH (r)-[:has_payload]->(f:ShepardFile) " +
      "RETURN r, f, d, hr, [(r)-[r_p:has_payload]->(f) | [r_p, f]] AS rels " +
      "ORDER BY r.createdAt ASC " +
      "SKIP $skip LIMIT $limit";
    Map<String, Object> params = Map.of("aid", doAppId, "skip", 10L, "limit", 5L);
    var ref = new FileReference();
    when(session.query(FileReference.class, expectedQuery, params)).thenReturn(List.of(ref));

    List<FileReference> result = dao.findByDataObjectAppId(doAppId, null, 10, 5);

    verify(session).query(FileReference.class, expectedQuery, params);
    assertEquals(1, result.size());
    assertEquals(ref, result.get(0));
  }

  @Test
  void findByDataObjectAppId_withSubKind_includesFileKindClause() {
    String doAppId = "do-app-file-5";
    String subKind = "krl";
    String expectedQuery =
      "MATCH (d:DataObject {appId: $aid})-[hr:has_reference]->(r:SingletonFileReference) " +
      "WHERE (r.deleted IS NULL OR r.deleted = false) " +
      "AND r.fileKind = $subKind " +
      "OPTIONAL MATCH (r)-[:has_payload]->(f:ShepardFile) " +
      "RETURN r, f, d, hr, [(r)-[r_p:has_payload]->(f) | [r_p, f]] AS rels " +
      "ORDER BY r.createdAt ASC " +
      "SKIP $skip LIMIT $limit";
    Map<String, Object> params = Map.of("aid", doAppId, "subKind", subKind, "skip", 0L, "limit", 20L);
    when(session.query(FileReference.class, expectedQuery, params)).thenReturn(List.of());

    List<FileReference> result = dao.findByDataObjectAppId(doAppId, subKind, 0, 20);

    verify(session).query(FileReference.class, expectedQuery, params);
    assertTrue(result.isEmpty());
  }

  @Test
  void findByDataObjectAppId_emptyPage_returnsEmpty() {
    String doAppId = "do-app-file-6";
    String expectedQuery =
      "MATCH (d:DataObject {appId: $aid})-[hr:has_reference]->(r:SingletonFileReference) " +
      "WHERE (r.deleted IS NULL OR r.deleted = false) " +
      "OPTIONAL MATCH (r)-[:has_payload]->(f:ShepardFile) " +
      "RETURN r, f, d, hr, [(r)-[r_p:has_payload]->(f) | [r_p, f]] AS rels " +
      "ORDER BY r.createdAt ASC " +
      "SKIP $skip LIMIT $limit";
    Map<String, Object> params = Map.of("aid", doAppId, "skip", 100L, "limit", 10L);
    when(session.query(FileReference.class, expectedQuery, params)).thenReturn(List.of());

    List<FileReference> result = dao.findByDataObjectAppId(doAppId, null, 100, 10);

    assertTrue(result.isEmpty());
  }
}
