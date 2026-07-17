package de.dlr.shepard.data.structureddata.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.data.structureddata.entities.StructuredData;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

public class StructuredDataDAOTest extends BaseTestCase {

  @Mock
  private Session session;

  @Mock
  private EntityIdResolver entityIdResolver;

  @InjectMocks
  private StructuredDataDAO dao = new StructuredDataDAO();

  @BeforeEach
  public void primeResolver() {
    org.mockito.Mockito.lenient().when(entityIdResolver.resolveAppId(123L)).thenReturn("appid-sdc-123");
  }

  @Test
  public void findTest() {
    // L2c: ID(c) flipped to {appId: $containerAppId}; resolver translates the OGM long.
    var sd = new StructuredData("oid", new Date(), "name");
    var query =
      """
      MATCH (c:StructuredDataContainer {appId: $containerAppId})-[:structureddata_in_container]->(s:StructuredData {oid: $oid}) \
      MATCH path=(s)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL \
      RETURN s, nodes(path), relationships(path)""";
    var paramsMap = Map.<String, Object>of("oid", "oid", "containerAppId", "appid-sdc-123");

    when(session.query(StructuredData.class, query, paramsMap)).thenReturn(List.of(sd));
    var actual = dao.find(123L, "oid");
    assertEquals(sd, actual);
    verify(session).query(StructuredData.class, query, paramsMap);
  }

  @Test
  public void findTest_notFound() {
    var query =
      """
      MATCH (c:StructuredDataContainer {appId: $containerAppId})-[:structureddata_in_container]->(s:StructuredData {oid: $oid}) \
      MATCH path=(s)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL \
      RETURN s, nodes(path), relationships(path)""";
    var paramsMap = Map.<String, Object>of("oid", "oid", "containerAppId", "appid-sdc-123");

    when(session.query(StructuredData.class, query, paramsMap)).thenReturn(Collections.emptyList());
    var actual = dao.find(123L, "oid");
    assertNull(actual);
  }

  // APISIMP-CONTAINER-STATS-OGM-COUNT

  @Test
  public void countByContainerAppId_returnsCountFromCypher() {
    String query =
        "MATCH (:StructuredDataContainer {appId: $cid})-[:structureddata_in_container]->(s:StructuredData) " +
        "RETURN count(s) AS total";
    Map<String, Object> row = Map.of("total", 12L);
    Result result = mock(Result.class);
    Iterator<Map<String, Object>> iter = List.<Map<String, Object>>of(row).iterator();
    when(result.iterator()).thenReturn(iter);
    when(session.query(query, Map.of("cid", "sdc-app-1"))).thenReturn(result);

    assertEquals(12L, dao.countByContainerAppId("sdc-app-1"));
    verify(session).query(query, Map.of("cid", "sdc-app-1"));
  }

  @Test
  public void countByContainerAppId_emptyResult_returnsZero() {
    String query =
        "MATCH (:StructuredDataContainer {appId: $cid})-[:structureddata_in_container]->(s:StructuredData) " +
        "RETURN count(s) AS total";
    Result result = mock(Result.class);
    when(result.iterator()).thenReturn(Collections.emptyIterator());
    when(session.query(query, Map.of("cid", "sdc-empty"))).thenReturn(result);

    assertEquals(0L, dao.countByContainerAppId("sdc-empty"));
  }
}
