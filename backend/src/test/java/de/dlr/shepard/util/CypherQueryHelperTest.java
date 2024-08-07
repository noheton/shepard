package de.dlr.shepard.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.orderBy.DataObjectAttributes;
import de.dlr.shepard.neo4Core.orderBy.OrderByAttribute;
import de.dlr.shepard.util.CypherQueryHelper.Neighborhood;
import org.junit.jupiter.api.Test;

public class CypherQueryHelperTest extends BaseTestCase {

  @Test
  public void getReturnPartTest() {
    var actual = CypherQueryHelper.getReturnPart("entity");
    assertEquals(
      "MATCH path=(entity)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN entity, nodes(path), relationships(path)",
      actual
    );
  }

  @Test
  public void getReturnPartTest_omitIncoming() {
    var actual = CypherQueryHelper.getReturnPart("entity", Neighborhood.OUTGOING);
    assertEquals(
      "MATCH path=(entity)-[*0..1]->(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN entity, nodes(path), relationships(path)",
      actual
    );
  }

  @Test
  public void getReturnPartTest_noNeighbors() {
    var actual = CypherQueryHelper.getReturnPart("entity", Neighborhood.ESSENTIAL);
    assertEquals(
      "MATCH path=(entity)-[*0..1]->(n) WHERE n:Permission OR n:User RETURN entity, nodes(path), relationships(path)",
      actual
    );
  }

  @Test
  public void getOrderByPartTestDesc() {
    String variable = "c";
    OrderByAttribute orderByAttribute = DataObjectAttributes.createdAt;
    Boolean orderDesc = true;
    var actual = CypherQueryHelper.getOrderByPart(variable, orderByAttribute, orderDesc);
    assertEquals("ORDER BY c.createdAt DESC", actual);
  }

  @Test
  public void getOrderByPartTestNull() {
    String variable = "c";
    OrderByAttribute orderByAttribute = DataObjectAttributes.createdAt;
    Boolean orderDesc = null;
    var actual = CypherQueryHelper.getOrderByPart(variable, orderByAttribute, orderDesc);
    assertEquals("ORDER BY c.createdAt", actual);
  }

  @Test
  public void getOrderByPartTestAsc() {
    String variable = "c";
    OrderByAttribute orderByAttribute = DataObjectAttributes.createdAt;
    Boolean orderDesc = false;
    var actual = CypherQueryHelper.getOrderByPart(variable, orderByAttribute, orderDesc);
    assertEquals("ORDER BY c.createdAt", actual);
  }

  @Test
  public void getOrderByPartTestStringDesc() {
    String variable = "c";
    OrderByAttribute orderByAttribute = DataObjectAttributes.name;
    Boolean orderDesc = true;
    var actual = CypherQueryHelper.getOrderByPart(variable, orderByAttribute, orderDesc);
    assertEquals("ORDER BY toLower(c.name) DESC", actual);
  }

  @Test
  public void getOrderByPartTestStringNull() {
    String variable = "c";
    OrderByAttribute orderByAttribute = DataObjectAttributes.name;
    Boolean orderDesc = null;
    var actual = CypherQueryHelper.getOrderByPart(variable, orderByAttribute, orderDesc);
    assertEquals("ORDER BY toLower(c.name)", actual);
  }

  @Test
  public void getOrderByPartTestStringAsc() {
    String variable = "c";
    OrderByAttribute orderByAttribute = DataObjectAttributes.name;
    Boolean orderDesc = null;
    var actual = CypherQueryHelper.getOrderByPart(variable, orderByAttribute, orderDesc);
    assertEquals("ORDER BY toLower(c.name)", actual);
  }

  @Test
  public void getObjectPartTest_WithName() {
    String variable = "c";
    String type = "Collection";
    var actual = CypherQueryHelper.getObjectPart(variable, type, true);
    assertEquals("(c:Collection { name : $name, deleted: FALSE })", actual);
  }

  @Test
  public void getObjectPartTest_WithoutName() {
    String variable = "c";
    String type = "Collection";
    var actual = CypherQueryHelper.getObjectPart(variable, type, false);
    assertEquals("(c:Collection { deleted: FALSE })", actual);
  }

  @Test
  public void getPaginationPartTest_WithPagination() {
    var actual = CypherQueryHelper.getPaginationPart();
    assertEquals("SKIP $offset LIMIT $size", actual);
  }

  @Test
  public void getReadableByQueryTest() {
    var expected =
      """
      (NOT exists((var)-[:has_permissions]->(:Permissions)) \
      OR exists((var)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"bob\" })) \
      OR exists((var)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) \
      OR exists((var)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) \
      OR exists((var)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"bob\"})))""";
    var actual = CypherQueryHelper.getReadableByQuery("var", "bob");

    assertEquals(expected, actual);
  }
}
