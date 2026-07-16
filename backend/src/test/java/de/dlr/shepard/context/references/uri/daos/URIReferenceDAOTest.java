package de.dlr.shepard.context.references.uri.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.uri.entities.URIReference;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

public class URIReferenceDAOTest extends BaseTestCase {

  @Mock
  private Session session;

  @Mock
  private EntityIdResolver entityIdResolver;

  @InjectMocks
  private URIReferenceDAO dao;

  @Test
  public void getEntityTypeTest() {
    var type = dao.getEntityType();
    assertEquals(URIReference.class, type);
  }

  @Test
  public void findByDataObjectTest() {
    var obj = new DataObject(1L);
    var obj2 = new DataObject(100L);
    var ref = new URIReference(2L);
    var ref2 = new URIReference(3L);
    var ref3 = new URIReference(4L);
    ref.setDataObject(obj);
    ref2.setDataObject(obj2);

    // L2c: WHERE ID(d) flipped to WHERE d.appId; resolver translates the
    // OGM long to its appId at the DAO boundary.
    when(entityIdResolver.resolveAppId(1L)).thenReturn("appid-do-1");
    var query =
      """
      MATCH (d:DataObject)-[hr:has_reference]->(r:URIReference { deleted: FALSE }) WHERE d.appId=$dataObjectAppId \
      MATCH path=(r)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL \
      RETURN r, nodes(path), relationships(path)""";
    var paramsMap = Map.<String, Object>of("dataObjectAppId", "appid-do-1");
    when(session.query(URIReference.class, query, paramsMap)).thenReturn(List.of(ref, ref2, ref3));

    var actual = dao.findByDataObjectNeo4jId(1L);
    verify(session).query(URIReference.class, query, paramsMap);
    assertEquals(List.of(ref), actual);
  }

  // ── APISIMP-REFS-INMEM-PAGING ────────────────────────────────────────────

  @Test
  public void countByDataObjectAppIdTest() {
    String doAppId = "do-app-uri-1";
    String expectedQuery =
      "MATCH (d:DataObject {appId: $aid})-[:has_reference]->(r:URIReference) " +
      "WHERE (r.deleted IS NULL OR r.deleted = false) " +
      "RETURN count(r) AS cnt";
    Result r = mock(Result.class);
    when(r.iterator()).thenReturn(
      List.<Map<String, Object>>of(Map.<String, Object>of("cnt", 7L)).iterator()
    );
    when(session.query(expectedQuery, Map.of("aid", doAppId))).thenReturn(r);

    int count = dao.countByDataObjectAppId(doAppId);

    verify(session).query(expectedQuery, Map.of("aid", doAppId));
    assertEquals(7, count);
  }

  @Test
  public void countByDataObjectAppIdEmptyResultTest() {
    String doAppId = "do-app-uri-empty";
    String expectedQuery =
      "MATCH (d:DataObject {appId: $aid})-[:has_reference]->(r:URIReference) " +
      "WHERE (r.deleted IS NULL OR r.deleted = false) " +
      "RETURN count(r) AS cnt";
    Result r = mock(Result.class);
    when(r.iterator()).thenReturn(Collections.<Map<String, Object>>emptyList().iterator());
    when(session.query(expectedQuery, Map.of("aid", doAppId))).thenReturn(r);

    assertEquals(0, dao.countByDataObjectAppId(doAppId));
  }

  @Test
  public void findByDataObjectAppIdTest_pushesSkipLimit() {
    String doAppId = "do-app-uri-2";
    int skip = 5;
    int limit = 3;
    String expectedQuery =
      "MATCH (d:DataObject {appId: $aid})-[hr:has_reference]->(r:URIReference) " +
      "WHERE (r.deleted IS NULL OR r.deleted = false) " +
      "RETURN r, d, hr " +
      "ORDER BY r.createdAt ASC " +
      "SKIP $skip LIMIT $limit";
    Map<String, Object> params = Map.of("aid", doAppId, "skip", 5L, "limit", 3L);
    var ref = new URIReference(10L);
    when(session.query(URIReference.class, expectedQuery, params)).thenReturn(List.of(ref));

    List<URIReference> result = dao.findByDataObjectAppId(doAppId, skip, limit);

    verify(session).query(URIReference.class, expectedQuery, params);
    assertEquals(1, result.size());
    assertEquals(ref, result.get(0));
  }

  @Test
  public void findByDataObjectAppIdTest_emptyPage() {
    String doAppId = "do-app-uri-3";
    String expectedQuery =
      "MATCH (d:DataObject {appId: $aid})-[hr:has_reference]->(r:URIReference) " +
      "WHERE (r.deleted IS NULL OR r.deleted = false) " +
      "RETURN r, d, hr " +
      "ORDER BY r.createdAt ASC " +
      "SKIP $skip LIMIT $limit";
    Map<String, Object> params = Map.of("aid", doAppId, "skip", 0L, "limit", 10L);
    when(session.query(URIReference.class, expectedQuery, params)).thenReturn(List.of());

    List<URIReference> result = dao.findByDataObjectAppId(doAppId, 0, 10);

    assertTrue(result.isEmpty());
  }

  @Test
  public void findByDataObjectShepardIdTest() {
    var obj = new DataObject(1L);
    obj.setShepardId(11L);
    var obj2 = new DataObject(100L);
    obj2.setShepardId(1001L);
    var ref = new URIReference(2L);
    ref.setShepardId(21L);
    var ref2 = new URIReference(3L);
    ref2.setShepardId(31L);
    var ref3 = new URIReference(4L);
    ref3.setShepardId(41L);
    ref.setDataObject(obj);
    ref2.setDataObject(obj2);

    var query =
      """
      MATCH (d:DataObject)-[hr:has_reference]->(r:URIReference { deleted: FALSE }) WHERE d.shepardId=11 \
      MATCH path=(r)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL \
      RETURN r, nodes(path), relationships(path)""";
    when(session.query(URIReference.class, query, Collections.emptyMap())).thenReturn(List.of(ref, ref2, ref3));

    var actual = dao.findByDataObjectShepardId(obj.getShepardId());
    verify(session).query(URIReference.class, query, Collections.emptyMap());
    assertEquals(List.of(ref), actual);
  }
}
