package de.dlr.shepard.data.file.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.data.file.entities.ShepardFile;
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

public class ShepardFileDAOTest extends BaseTestCase {

  @Mock
  private Session session;

  @Mock
  private EntityIdResolver entityIdResolver;

  @InjectMocks
  private ShepardFileDAO dao = new ShepardFileDAO();

  @BeforeEach
  public void primeResolver() {
    org.mockito.Mockito.lenient().when(entityIdResolver.resolveAppId(123L)).thenReturn("appid-fc-123");
  }

  @Test
  public void findTest() {
    // L2c: ID(c) flipped to {appId: $containerAppId}; resolver translates the OGM long.
    var f = new ShepardFile("oid", new Date(), "filename", "md5");
    var query =
      """
      MATCH (c:FileContainer {appId: $containerAppId})-[:file_in_container]->(f:ShepardFile {oid: $oid}) \
      MATCH path=(f)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL \
      RETURN f, nodes(path), relationships(path)""";
    var paramsMap = Map.<String, Object>of("oid", "oid", "containerAppId", "appid-fc-123");

    when(session.query(ShepardFile.class, query, paramsMap)).thenReturn(List.of(f));
    var actual = dao.find(123L, "oid");
    assertEquals(f, actual);
    verify(session).query(ShepardFile.class, query, paramsMap);
  }

  @Test
  public void findTest_notFound() {
    var query =
      """
      MATCH (c:FileContainer {appId: $containerAppId})-[:file_in_container]->(f:ShepardFile {oid: $oid}) \
      MATCH path=(f)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL \
      RETURN f, nodes(path), relationships(path)""";
    var paramsMap = Map.<String, Object>of("oid", "oid", "containerAppId", "appid-fc-123");

    when(session.query(ShepardFile.class, query, paramsMap)).thenReturn(Collections.emptyList());
    var actual = dao.find(123L, "oid");
    assertNull(actual);
  }

  // APISIMP-CONTAINER-STATS-OGM-COUNT

  @Test
  public void countByContainerAppId_returnsCountFromCypher() {
    String query = "MATCH (:FileContainer {appId: $cid})-[:file_in_container]->(f:ShepardFile) " +
        "RETURN count(f) AS total";
    Map<String, Object> row = Map.of("total", 7L);
    Result result = mock(Result.class);
    Iterator<Map<String, Object>> iter = List.<Map<String, Object>>of(row).iterator();
    when(result.iterator()).thenReturn(iter);
    when(session.query(query, Map.of("cid", "fc-app-1"))).thenReturn(result);

    assertEquals(7L, dao.countByContainerAppId("fc-app-1"));
    verify(session).query(query, Map.of("cid", "fc-app-1"));
  }

  @Test
  public void countByContainerAppId_emptyResult_returnsZero() {
    String query = "MATCH (:FileContainer {appId: $cid})-[:file_in_container]->(f:ShepardFile) " +
        "RETURN count(f) AS total";
    Result result = mock(Result.class);
    when(result.iterator()).thenReturn(Collections.emptyIterator());
    when(session.query(query, Map.of("cid", "fc-empty"))).thenReturn(result);

    assertEquals(0L, dao.countByContainerAppId("fc-empty"));
  }

  // APISIMP-BUNDLE-KIND-TOIO-OGM

  @Test
  public void countByBundleReferenceAppId_returnsCountFromCypher() {
    var mockResult = org.mockito.Mockito.mock(Result.class);
    var row = Map.<String, Object>of("total", 5L);
    when(mockResult.iterator()).thenReturn(List.of(row).iterator());
    when(session.query(contains("FileBundleReference"), anyMap())).thenReturn(mockResult);

    long count = dao.countByBundleReferenceAppId("bundle-appid-1");
    assertEquals(5L, count);
  }

  @Test
  public void countByBundleReferenceAppId_emptyResult_returnsZero() {
    var mockResult = org.mockito.Mockito.mock(Result.class);
    when(mockResult.iterator()).thenReturn(Collections.emptyIterator());
    when(session.query(contains("FileBundleReference"), anyMap())).thenReturn(mockResult);

    long count = dao.countByBundleReferenceAppId("bundle-appid-empty");
    assertEquals(0L, count);
  }
}
