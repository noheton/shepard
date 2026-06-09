package de.dlr.shepard.data.structureddata.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.data.ContainerAttributes;
import de.dlr.shepard.data.structureddata.entities.StructuredDataContainer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.session.Session;

public class StructuredDataContainerDAOTest extends BaseTestCase {

  @Mock
  private Session session;

  @InjectMocks
  private StructuredDataContainerDAO dao;

  @Test
  public void getEntityTypeTest() {
    var type = dao.getEntityType();
    assertEquals(StructuredDataContainer.class, type);
  }

  @Test
  public void findAll_WithoutName() {
    var col1 = new StructuredDataContainer(1L);
    col1.setName("Yes");
    Map<String, Object> paramsMap = new HashMap<>();
    paramsMap.put("name", null);

    var query =
      """
      MATCH (c:StructuredDataContainer { deleted: FALSE }) WHERE (NOT exists((c)-[:has_permissions]->(:Permissions)) \
      OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: "bob" })) \
      OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "Public"})) \
      OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "PublicReadable"})) \
      OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: "bob"}))) \
      WITH c MATCH path=(c)-[*0..1]->(n) WHERE n:Permission OR n:User RETURN c, nodes(path), relationships(path)""";
    when(session.query(StructuredDataContainer.class, query, paramsMap)).thenReturn(List.of(col1));

    var params = new QueryParamHelper();
    var actual = dao.findAllStructuredDataContainers(params, "bob");
    verify(session).query(StructuredDataContainer.class, query, paramsMap);
    assertEquals(List.of(col1), actual);
  }

  @Test
  public void findAll_WithoutNameOrderByNameDesc() {
    var col1 = new StructuredDataContainer(1L);
    col1.setName("Yes");
    Map<String, Object> paramsMap = new HashMap<>();
    paramsMap.put("name", null);

    var query =
      """
      MATCH (c:StructuredDataContainer { deleted: FALSE }) WHERE (NOT exists((c)-[:has_permissions]->(:Permissions)) \
      OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: "bob" })) \
      OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "Public"})) \
      OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "PublicReadable"})) \
      OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: "bob"}))) \
      WITH c ORDER BY toLower(c.name) DESC \
      MATCH path=(c)-[*0..1]->(n) WHERE n:Permission OR n:User RETURN c, nodes(path), relationships(path)""";
    when(session.query(StructuredDataContainer.class, query, paramsMap)).thenReturn(List.of(col1));

    var params = new QueryParamHelper();
    var attr = ContainerAttributes.name;
    params = params.withOrderByAttribute(attr, true);
    var actual = dao.findAllStructuredDataContainers(params, "bob");
    verify(session).query(StructuredDataContainer.class, query, paramsMap);
    assertEquals(List.of(col1), actual);
  }

  @Test
  public void findAll_WithName() {
    var col1 = new StructuredDataContainer(1L);
    col1.setName("Yes");
    var col2 = new StructuredDataContainer(2L);
    col2.setName("No");
    Map<String, Object> paramsMap = new HashMap<>();
    paramsMap.put("name", "Yes");

    var query =
      """
      MATCH (c:StructuredDataContainer { name : $name, deleted: FALSE }) WHERE (NOT exists((c)-[:has_permissions]->(:Permissions)) \
      OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: "bob" })) \
      OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "Public"})) \
      OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "PublicReadable"})) \
      OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: "bob"}))) \
      WITH c MATCH path=(c)-[*0..1]->(n) WHERE n:Permission OR n:User RETURN c, nodes(path), relationships(path)""";
    when(session.query(StructuredDataContainer.class, query, paramsMap)).thenReturn(List.of(col1, col2));

    var params = new QueryParamHelper().withName("Yes");
    var actual = dao.findAllStructuredDataContainers(params, "bob");
    verify(session).query(StructuredDataContainer.class, query, paramsMap);
    assertEquals(List.of(col1), actual);
  }

  @Test
  public void findAll_WithNameOrderByNameDesc() {
    var col1 = new StructuredDataContainer(1L);
    col1.setName("Yes");
    var col2 = new StructuredDataContainer(2L);
    col2.setName("No");
    Map<String, Object> paramsMap = new HashMap<>();
    paramsMap.put("name", "Yes");

    var query =
      """
      MATCH (c:StructuredDataContainer { name : $name, deleted: FALSE }) WHERE (NOT exists((c)-[:has_permissions]->(:Permissions)) \
      OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: "bob" })) \
      OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "Public"})) \
      OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "PublicReadable"})) \
      OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: "bob"}))) \
      WITH c ORDER BY toLower(c.name) DESC \
      MATCH path=(c)-[*0..1]->(n) WHERE n:Permission OR n:User RETURN c, nodes(path), relationships(path)""";
    when(session.query(StructuredDataContainer.class, query, paramsMap)).thenReturn(List.of(col1, col2));

    var params = new QueryParamHelper().withName("Yes");
    var attr = ContainerAttributes.name;
    params = params.withOrderByAttribute(attr, true);
    var actual = dao.findAllStructuredDataContainers(params, "bob");
    verify(session).query(StructuredDataContainer.class, query, paramsMap);
    assertEquals(List.of(col1), actual);
  }

  @Test
  public void findAll_WithPage() {
    var col1 = new StructuredDataContainer(1L);
    col1.setName("Yes");
    Map<String, Object> paramsMap = new HashMap<>();
    paramsMap.put("offset", 300);
    paramsMap.put("size", 100);
    paramsMap.put("name", null);

    var query =
      """
      MATCH (c:StructuredDataContainer { deleted: FALSE }) WHERE (NOT exists((c)-[:has_permissions]->(:Permissions)) \
      OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: "bob" })) \
      OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "Public"})) \
      OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "PublicReadable"})) \
      OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: "bob"}))) \
      WITH c SKIP $offset LIMIT $size \
      MATCH path=(c)-[*0..1]->(n) WHERE n:Permission OR n:User RETURN c, nodes(path), relationships(path)""";
    when(session.query(StructuredDataContainer.class, query, paramsMap)).thenReturn(List.of(col1));

    var params = new QueryParamHelper().withPageAndSize(3, 100);
    var actual = dao.findAllStructuredDataContainers(params, "bob");
    verify(session).query(StructuredDataContainer.class, query, paramsMap);
    assertEquals(List.of(col1), actual);
  }

  @Test
  public void findAll_WithPageOrderByNameDesc() {
    var col1 = new StructuredDataContainer(1L);
    col1.setName("Yes");
    Map<String, Object> paramsMap = new HashMap<>();
    paramsMap.put("offset", 300);
    paramsMap.put("size", 100);
    paramsMap.put("name", null);

    var query =
      """
      MATCH (c:StructuredDataContainer { deleted: FALSE }) WHERE (NOT exists((c)-[:has_permissions]->(:Permissions)) \
      OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: "bob" })) \
      OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "Public"})) \
      OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "PublicReadable"})) \
      OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: "bob"}))) \
      WITH c ORDER BY toLower(c.name) DESC SKIP $offset LIMIT $size \
      MATCH path=(c)-[*0..1]->(n) WHERE n:Permission OR n:User RETURN c, nodes(path), relationships(path)""";
    when(session.query(StructuredDataContainer.class, query, paramsMap)).thenReturn(List.of(col1));

    var params = new QueryParamHelper().withPageAndSize(3, 100);
    var attr = ContainerAttributes.name;
    params = params.withOrderByAttribute(attr, true);
    var actual = dao.findAllStructuredDataContainers(params, "bob");
    verify(session).query(StructuredDataContainer.class, query, paramsMap);
    assertEquals(List.of(col1), actual);
  }

  @Test
  public void findAll_WithNameAndPage() {
    var col1 = new StructuredDataContainer(1L);
    col1.setName("Yes");
    var col2 = new StructuredDataContainer(2L);
    col2.setName("No");
    Map<String, Object> paramsMap = new HashMap<>();
    paramsMap.put("offset", 300);
    paramsMap.put("size", 100);
    paramsMap.put("name", "Yes");

    var query =
      """
      MATCH (c:StructuredDataContainer { name : $name, deleted: FALSE }) WHERE (NOT exists((c)-[:has_permissions]->(:Permissions)) \
      OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: "bob" })) \
      OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "Public"})) \
      OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "PublicReadable"})) \
      OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: "bob"}))) \
      WITH c SKIP $offset LIMIT $size \
      MATCH path=(c)-[*0..1]->(n) WHERE n:Permission OR n:User RETURN c, nodes(path), relationships(path)""";
    when(session.query(StructuredDataContainer.class, query, paramsMap)).thenReturn(List.of(col1, col2));

    var params = new QueryParamHelper().withPageAndSize(3, 100).withName("Yes");
    var actual = dao.findAllStructuredDataContainers(params, "bob");
    verify(session).query(StructuredDataContainer.class, query, paramsMap);
    assertEquals(List.of(col1), actual);
  }

  @Test
  public void findAll_WithNameAndPageOrderByNameDesc() {
    var col1 = new StructuredDataContainer(1L);
    col1.setName("Yes");
    var col2 = new StructuredDataContainer(2L);
    col2.setName("No");
    Map<String, Object> paramsMap = new HashMap<>();
    paramsMap.put("offset", 300);
    paramsMap.put("size", 100);
    paramsMap.put("name", "Yes");

    var query =
      """
      MATCH (c:StructuredDataContainer { name : $name, deleted: FALSE }) WHERE (NOT exists((c)-[:has_permissions]->(:Permissions)) \
      OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: "bob" })) \
      OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "Public"})) \
      OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "PublicReadable"})) \
      OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: "bob"}))) \
      WITH c ORDER BY toLower(c.name) DESC SKIP $offset LIMIT $size \
      MATCH path=(c)-[*0..1]->(n) WHERE n:Permission OR n:User RETURN c, nodes(path), relationships(path)""";
    when(session.query(StructuredDataContainer.class, query, paramsMap)).thenReturn(List.of(col1, col2));

    var params = new QueryParamHelper().withPageAndSize(3, 100).withName("Yes");
    var attr = ContainerAttributes.name;
    params = params.withOrderByAttribute(attr, true);
    var actual = dao.findAllStructuredDataContainers(params, "bob");
    verify(session).query(StructuredDataContainer.class, query, paramsMap);
    assertEquals(List.of(col1), actual);
  }

  // ─── CC1b: findLinkedDataObjectsByContainerAppId ──────────────────────────

  @Test
  public void findLinkedDataObjects_returnsEmptyListWhenNoneLinked() {
    String containerAppId = "01900000-0000-7000-8000-000000000001";
    // Production returns rows of {neo4jId}; an empty result means no links.
    org.neo4j.ogm.model.Result empty = resultOf(List.of());
    when(session.query(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyMap()))
      .thenReturn(empty);

    List<DataObject> result = dao.findLinkedDataObjectsByContainerAppId(containerAppId);

    assertTrue(result.isEmpty());
  }

  @Test
  public void findLinkedDataObjects_returnsTwoDataObjects() {
    String containerAppId = "01900000-0000-7000-8000-000000000002";

    DataObject do1 = new DataObject();
    do1.setName("Config A");
    DataObject do2 = new DataObject();
    do2.setName("Config B");

    // Production resolves DataObjects in two steps: a row query that yields
    // neo4jIds, then session.load per id. Build the Result mock first to avoid
    // nesting stubbing inside the outer when(...) call.
    org.neo4j.ogm.model.Result rows =
      resultOf(List.of(Map.of("neo4jId", 1L), Map.of("neo4jId", 2L)));
    when(session.query(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyMap()))
      .thenReturn(rows);
    when(session.load(DataObject.class, 1L, 1)).thenReturn(do1);
    when(session.load(DataObject.class, 2L, 1)).thenReturn(do2);

    List<DataObject> result = dao.findLinkedDataObjectsByContainerAppId(containerAppId);

    assertEquals(2, result.size());
    assertEquals("Config A", result.get(0).getName());
    assertEquals("Config B", result.get(1).getName());
  }

  /** Build an OGM Result over the given rows (it is just an Iterable of maps). */
  private static org.neo4j.ogm.model.Result resultOf(List<Map<String, Object>> rows) {
    org.neo4j.ogm.model.Result result = org.mockito.Mockito.mock(org.neo4j.ogm.model.Result.class);
    when(result.iterator()).thenReturn(rows.iterator());
    return result;
  }
}
