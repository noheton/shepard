package de.dlr.shepard.data.file.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.collection.endpoints.ContainerAttributes;
import de.dlr.shepard.data.file.entities.FileContainer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.session.Session;

public class FileContainerDAOTest extends BaseTestCase {

  @Mock
  private Session session;

  @InjectMocks
  private FileContainerDAO dao;

  @Test
  public void getEntityTypeTest() {
    var type = dao.getEntityType();
    assertEquals(FileContainer.class, type);
  }

  @Test
  public void findAll_WithoutName() {
    var col1 = new FileContainer(1L);
    col1.setName("Yes");
    Map<String, Object> paramsMap = new HashMap<>();
    paramsMap.put("name", null);

    var query =
      """
      MATCH (c:FileContainer { deleted: FALSE }) WHERE (NOT exists((c)-[:has_permissions]->(:Permissions)) \
      OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: "bob" })) \
      OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "Public"})) \
      OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "PublicReadable"})) \
      OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: "bob"}))) \
      WITH c MATCH path=(c)-[*0..1]->(n) WHERE n:Permission OR n:User RETURN c, nodes(path), relationships(path)""";
    when(session.query(FileContainer.class, query, paramsMap)).thenReturn(List.of(col1));

    var params = new QueryParamHelper();
    var actual = dao.findAllFileContainers(params, "bob");
    verify(session).query(FileContainer.class, query, paramsMap);
    assertEquals(List.of(col1), actual);
  }

  @Test
  public void findAll_WithoutNameOrderByNameDesc() {
    var col1 = new FileContainer(1L);
    col1.setName("Yes");
    Map<String, Object> paramsMap = new HashMap<>();
    paramsMap.put("name", null);

    var query =
      """
      MATCH (c:FileContainer { deleted: FALSE }) WHERE (NOT exists((c)-[:has_permissions]->(:Permissions)) \
      OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: "bob" })) \
      OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "Public"})) \
      OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "PublicReadable"})) \
      OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: "bob"}))) \
      WITH c ORDER BY toLower(c.name) DESC \
      MATCH path=(c)-[*0..1]->(n) WHERE n:Permission OR n:User RETURN c, nodes(path), relationships(path)""";
    when(session.query(FileContainer.class, query, paramsMap)).thenReturn(List.of(col1));

    var params = new QueryParamHelper();
    var attr = ContainerAttributes.name;
    params = params.withOrderByAttribute(attr, true);
    var actual = dao.findAllFileContainers(params, "bob");
    verify(session).query(FileContainer.class, query, paramsMap);
    assertEquals(List.of(col1), actual);
  }

  @Test
  public void findAll_WithName() {
    var col1 = new FileContainer(1L);
    col1.setName("Yes");
    var col2 = new FileContainer(2L);
    col2.setName("No");
    Map<String, Object> paramsMap = new HashMap<>();
    paramsMap.put("name", "Yes");

    var query =
      """
      MATCH (c:FileContainer { name : $name, deleted: FALSE }) WHERE (NOT exists((c)-[:has_permissions]->(:Permissions)) \
      OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: "bob" })) \
      OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "Public"})) \
      OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "PublicReadable"})) \
      OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: "bob"}))) \
      WITH c MATCH path=(c)-[*0..1]->(n) WHERE n:Permission OR n:User RETURN c, nodes(path), relationships(path)""";
    when(session.query(FileContainer.class, query, paramsMap)).thenReturn(List.of(col1, col2));

    var params = new QueryParamHelper().withName("Yes");
    var actual = dao.findAllFileContainers(params, "bob");
    verify(session).query(FileContainer.class, query, paramsMap);
    assertEquals(List.of(col1), actual);
  }

  @Test
  public void findAll_WithNameOrderByNameDesc() {
    var col1 = new FileContainer(1L);
    col1.setName("Yes");
    var col2 = new FileContainer(2L);
    col2.setName("No");
    Map<String, Object> paramsMap = new HashMap<>();
    paramsMap.put("name", "Yes");

    var query =
      """
      MATCH (c:FileContainer { name : $name, deleted: FALSE }) WHERE (NOT exists((c)-[:has_permissions]->(:Permissions)) \
      OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: "bob" })) \
      OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "Public"})) \
      OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "PublicReadable"})) \
      OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: "bob"}))) \
      WITH c ORDER BY toLower(c.name) DESC \
      MATCH path=(c)-[*0..1]->(n) WHERE n:Permission OR n:User RETURN c, nodes(path), relationships(path)""";
    when(session.query(FileContainer.class, query, paramsMap)).thenReturn(List.of(col1, col2));

    var params = new QueryParamHelper().withName("Yes");
    var attr = ContainerAttributes.name;
    params = params.withOrderByAttribute(attr, true);
    var actual = dao.findAllFileContainers(params, "bob");
    verify(session).query(FileContainer.class, query, paramsMap);
    assertEquals(List.of(col1), actual);
  }

  @Test
  public void findAll_WithPage() {
    var col1 = new FileContainer(1L);
    col1.setName("Yes");
    Map<String, Object> paramsMap = new HashMap<>();
    paramsMap.put("name", null);
    paramsMap.put("offset", 300);
    paramsMap.put("size", 100);

    var query =
      """
      MATCH (c:FileContainer { deleted: FALSE }) WHERE (NOT exists((c)-[:has_permissions]->(:Permissions)) \
      OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: "bob" })) \
      OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "Public"})) \
      OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "PublicReadable"})) \
      OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: "bob"}))) \
      WITH c SKIP $offset LIMIT $size \
      MATCH path=(c)-[*0..1]->(n) WHERE n:Permission OR n:User RETURN c, nodes(path), relationships(path)""";
    when(session.query(FileContainer.class, query, paramsMap)).thenReturn(List.of(col1));

    var params = new QueryParamHelper().withPageAndSize(3, 100);
    var actual = dao.findAllFileContainers(params, "bob");
    verify(session).query(FileContainer.class, query, paramsMap);
    assertEquals(List.of(col1), actual);
  }

  @Test
  public void findAll_WithPageOrderByNameDesc() {
    var col1 = new FileContainer(1L);
    col1.setName("Yes");
    Map<String, Object> paramsMap = new HashMap<>();
    paramsMap.put("name", null);
    paramsMap.put("offset", 300);
    paramsMap.put("size", 100);

    var query =
      """
      MATCH (c:FileContainer { deleted: FALSE }) WHERE (NOT exists((c)-[:has_permissions]->(:Permissions)) \
      OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: "bob" })) \
      OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "Public"})) \
      OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "PublicReadable"})) \
      OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: "bob"}))) \
      WITH c ORDER BY toLower(c.name) DESC SKIP $offset LIMIT $size \
      MATCH path=(c)-[*0..1]->(n) WHERE n:Permission OR n:User RETURN c, nodes(path), relationships(path)""";
    when(session.query(FileContainer.class, query, paramsMap)).thenReturn(List.of(col1));

    var params = new QueryParamHelper().withPageAndSize(3, 100);
    var attr = ContainerAttributes.name;
    params = params.withOrderByAttribute(attr, true);
    var actual = dao.findAllFileContainers(params, "bob");
    verify(session).query(FileContainer.class, query, paramsMap);
    assertEquals(List.of(col1), actual);
  }

  @Test
  public void findAll_WithNameAndPage() {
    var col1 = new FileContainer(1L);
    col1.setName("Yes");
    var col2 = new FileContainer(2L);
    col2.setName("No");
    Map<String, Object> paramsMap = new HashMap<>();
    paramsMap.put("name", "Yes");
    paramsMap.put("offset", 300);
    paramsMap.put("size", 100);

    var query =
      """
      MATCH (c:FileContainer { name : $name, deleted: FALSE }) WHERE (NOT exists((c)-[:has_permissions]->(:Permissions)) \
      OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: "bob" })) \
      OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "Public"})) \
      OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "PublicReadable"})) \
      OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: "bob"}))) \
      WITH c SKIP $offset LIMIT $size \
      MATCH path=(c)-[*0..1]->(n) WHERE n:Permission OR n:User RETURN c, nodes(path), relationships(path)""";
    when(session.query(FileContainer.class, query, paramsMap)).thenReturn(List.of(col1, col2));

    var params = new QueryParamHelper().withPageAndSize(3, 100).withName("Yes");
    var actual = dao.findAllFileContainers(params, "bob");
    verify(session).query(FileContainer.class, query, paramsMap);
    assertEquals(List.of(col1), actual);
  }

  @Test
  public void findAll_WithNameAndPageOrderByNameDesc() {
    var col1 = new FileContainer(1L);
    col1.setName("Yes");
    var col2 = new FileContainer(2L);
    col2.setName("No");
    Map<String, Object> paramsMap = new HashMap<>();
    paramsMap.put("name", "Yes");
    paramsMap.put("offset", 300);
    paramsMap.put("size", 100);

    var query =
      """
      MATCH (c:FileContainer { name : $name, deleted: FALSE }) WHERE (NOT exists((c)-[:has_permissions]->(:Permissions)) \
      OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: "bob" })) \
      OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "Public"})) \
      OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "PublicReadable"})) \
      OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: "bob"}))) \
      WITH c ORDER BY toLower(c.name) DESC SKIP $offset LIMIT $size \
      MATCH path=(c)-[*0..1]->(n) WHERE n:Permission OR n:User RETURN c, nodes(path), relationships(path)""";
    when(session.query(FileContainer.class, query, paramsMap)).thenReturn(List.of(col1, col2));

    var params = new QueryParamHelper().withPageAndSize(3, 100).withName("Yes");
    var attr = ContainerAttributes.name;
    params = params.withOrderByAttribute(attr, true);
    var actual = dao.findAllFileContainers(params, "bob");
    verify(session).query(FileContainer.class, query, paramsMap);
    assertEquals(List.of(col1), actual);
  }
}
