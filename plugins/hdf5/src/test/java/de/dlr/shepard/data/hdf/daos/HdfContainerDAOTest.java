package de.dlr.shepard.data.hdf.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.data.ContainerAttributes;
import de.dlr.shepard.data.hdf.entities.HdfContainer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.session.Session;

/**
 * Mirrors {@code FileContainerDAOTest} — the Cypher payload is the
 * load-bearing contract, so we snapshot the expected query strings
 * exactly. Same {@code @InjectMocks} idiom as the upstream DAO tests.
 */
public class HdfContainerDAOTest extends BaseTestCase {

  @Mock
  private Session session;

  @InjectMocks
  private HdfContainerDAO dao;

  @Test
  public void getEntityTypeReturnsHdfContainerClass() {
    assertEquals(HdfContainer.class, dao.getEntityType());
  }

  @Test
  public void findByAppIdReturnsNullForBlankInputWithoutQueryingSession() {
    assertNull(dao.findByAppId(null));
    assertNull(dao.findByAppId(""));
    assertNull(dao.findByAppId("   "));
    verify(session, never()).query(
      org.mockito.ArgumentMatchers.any(Class.class),
      org.mockito.ArgumentMatchers.anyString(),
      org.mockito.ArgumentMatchers.<Map<String, Object>>any()
    );
  }

  @Test
  public void findByAppIdEmitsExpectedCypher() {
    var c = new HdfContainer(7L);
    c.setAppId("app-7");
    String expectedQuery =
      "MATCH (c:HdfContainer { deleted: FALSE }) WHERE c.appId = $appId " +
      "MATCH path=(c)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN c, nodes(path), relationships(path)";
    when(session.query(HdfContainer.class, expectedQuery, Map.of("appId", "app-7"))).thenReturn(List.of(c));

    var found = dao.findByAppId("app-7");
    assertEquals(c, found);
  }

  @Test
  public void findByAppIdMissingReturnsNull() {
    String expectedQuery =
      "MATCH (c:HdfContainer { deleted: FALSE }) WHERE c.appId = $appId " +
      "MATCH path=(c)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN c, nodes(path), relationships(path)";
    when(session.query(HdfContainer.class, expectedQuery, Map.of("appId", "ghost"))).thenReturn(List.of());
    assertNull(dao.findByAppId("ghost"));
  }

  @Test
  public void findAllHdfContainersWithoutNameFiltersBoth() {
    var col1 = new HdfContainer(1L);
    col1.setName("primary");

    Map<String, Object> paramsMap = new HashMap<>();
    paramsMap.put("name", null);

    String expectedQuery =
      """
      MATCH (c:HdfContainer { deleted: FALSE }) WHERE (NOT exists((c)-[:has_permissions]->(:Permissions)) \
      OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: "bob" })) \
      OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "Public"})) \
      OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "PublicReadable"})) \
      OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: "bob"}))) \
      WITH c MATCH path=(c)-[*0..1]->(n) WHERE n:Permission OR n:User RETURN c, nodes(path), relationships(path)""";
    when(session.query(HdfContainer.class, expectedQuery, paramsMap)).thenReturn(List.of(col1));

    var actual = dao.findAllHdfContainers(new QueryParamHelper(), "bob");
    assertEquals(List.of(col1), actual);
  }

  @Test
  public void findAllHdfContainersNullParamsToleratedAsEmptyHelper() {
    Map<String, Object> paramsMap = new HashMap<>();
    paramsMap.put("name", null);
    String expectedQuery =
      """
      MATCH (c:HdfContainer { deleted: FALSE }) WHERE (NOT exists((c)-[:has_permissions]->(:Permissions)) \
      OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: "bob" })) \
      OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "Public"})) \
      OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "PublicReadable"})) \
      OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: "bob"}))) \
      WITH c MATCH path=(c)-[*0..1]->(n) WHERE n:Permission OR n:User RETURN c, nodes(path), relationships(path)""";
    when(session.query(HdfContainer.class, expectedQuery, paramsMap)).thenReturn(List.of());
    var actual = dao.findAllHdfContainers(null, "bob");
    assertEquals(List.of(), actual);
  }

  @Test
  public void findAllHdfContainersWithNameFiltersByEqualsIgnoreCase() {
    var col1 = new HdfContainer(1L);
    col1.setName("Primary");
    var col2 = new HdfContainer(2L);
    col2.setName("Secondary");

    Map<String, Object> paramsMap = new HashMap<>();
    paramsMap.put("name", "primary");

    String expectedQuery =
      """
      MATCH (c:HdfContainer { name : $name, deleted: FALSE }) WHERE (NOT exists((c)-[:has_permissions]->(:Permissions)) \
      OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: "bob" })) \
      OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "Public"})) \
      OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "PublicReadable"})) \
      OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: "bob"}))) \
      WITH c MATCH path=(c)-[*0..1]->(n) WHERE n:Permission OR n:User RETURN c, nodes(path), relationships(path)""";
    when(session.query(HdfContainer.class, expectedQuery, paramsMap)).thenReturn(List.of(col1, col2));

    var actual = dao.findAllHdfContainers(new QueryParamHelper().withName("primary"), "bob");
    // matchName drops the non-matching row even though Cypher might return it.
    assertEquals(List.of(col1), actual);
  }

  @Test
  public void findAllHdfContainersWithPageAndOrder() {
    var col1 = new HdfContainer(1L);
    col1.setName("primary");

    Map<String, Object> paramsMap = new HashMap<>();
    paramsMap.put("name", null);
    paramsMap.put("offset", 300);
    paramsMap.put("size", 100);

    String expectedQuery =
      """
      MATCH (c:HdfContainer { deleted: FALSE }) WHERE (NOT exists((c)-[:has_permissions]->(:Permissions)) \
      OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: "bob" })) \
      OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "Public"})) \
      OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "PublicReadable"})) \
      OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: "bob"}))) \
      WITH c ORDER BY toLower(c.name) DESC SKIP $offset LIMIT $size \
      MATCH path=(c)-[*0..1]->(n) WHERE n:Permission OR n:User RETURN c, nodes(path), relationships(path)""";
    when(session.query(HdfContainer.class, expectedQuery, paramsMap)).thenReturn(List.of(col1));

    var params = new QueryParamHelper().withPageAndSize(3, 100).withOrderByAttribute(ContainerAttributes.name, true);
    var actual = dao.findAllHdfContainers(params, "bob");
    assertEquals(List.of(col1), actual);
  }
}
