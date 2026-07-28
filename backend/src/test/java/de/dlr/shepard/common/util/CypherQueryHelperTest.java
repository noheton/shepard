package de.dlr.shepard.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.common.neo4j.endpoints.OrderByAttribute;
import de.dlr.shepard.common.search.endpoints.BasicContainerAttributes;
import de.dlr.shepard.common.util.CypherQueryHelper.Neighborhood;
import de.dlr.shepard.context.collection.endpoints.DataObjectAttributes;
import org.junit.jupiter.api.Test;

public class CypherQueryHelperTest {

  @Test
  public void getReturnPartTest() {
    var actual = CypherQueryHelper.getReturnPart("entity");
    assertEquals(
      "MATCH path=(entity)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN entity, nodes(path), relationships(path)",
      actual
    );
  }

  @Test
  public void getReturnPartForDetail_positiveEdgeAllowlistExcludesHasReference() {
    // GETDO-DETAIL-TARGETED: the detail load pushes the wanted edge types INTO the
    // pattern (positive allowlist) instead of expanding all edges + a NONE(has_reference)
    // post-filter. This makes a large-fanout DO's detail neighborhood O(1) in reference
    // degree (PROFILE: 259,406 -> 599 db-hits) — the expand skips the has_reference
    // supernode group entirely rather than walking it to discard it. The allowlist is
    // the DataObject class hierarchy's declared @Relationship set MINUS has_reference,
    // so OGM hydrates a byte-identical entity (OGM only maps declared @Relationship types).
    var actual = CypherQueryHelper.getReturnPartForDetail("o");
    assertEquals(
      "MATCH path=(o)-[:has_dataobject|has_successor|has_child|points_to|has_labjournalentry|" +
      "has_annotation|has_version|created_by|updated_by*0..1]-(n) " +
      "WHERE (n.deleted = FALSE OR n.deleted IS NULL) RETURN o, nodes(path), relationships(path)",
      actual
    );
    // The fan-out edge must never appear in the traversal pattern.
    org.junit.jupiter.api.Assertions.assertFalse(
      actual.contains(Constants.HAS_REFERENCE),
      "detail return part must never traverse the has_reference fan-out edge"
    );
  }

  @Test
  public void getReturnPartForCollectionDetail_excludesOnlyHasDataobject() {
    // SUPERNODE-F2-COLLECTION-DETAIL: collection detail/list load excludes the
    // has_dataobject fan-out (up to 8,483 members on mffd-afp-tapelaying) but KEEPS
    // every other edge (permissions, version, default file container, incoming refs).
    var actual = CypherQueryHelper.getReturnPartForCollectionDetail("o");
    assertEquals(
      "MATCH path=(o)-[*0..1]-(n) WHERE (n.deleted = FALSE OR n.deleted IS NULL) AND " +
      "NONE(rel IN relationships(path) WHERE type(rel) = 'has_dataobject') RETURN o, nodes(path), relationships(path)",
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
  public void getOrderByPartTestByType() {
    String variable = "c";
    OrderByAttribute orderByAttribute = BasicContainerAttributes.type;
    Boolean orderDesc = null;
    var actual = CypherQueryHelper.getOrderByPart(variable, orderByAttribute, orderDesc);
    assertEquals("ORDER BY LABELS(c)", actual);
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
  public void getPaginationPartTest_NoParams() {
    var actual = CypherQueryHelper.getPaginationPart();
    assertEquals("SKIP $offset LIMIT $size", actual);
  }

  @Test
  public void getPaginationPartTest_WithParams() {
    PaginationHelper pagnationParam = new PaginationHelper(1, 10);
    var actual = CypherQueryHelper.getPaginationPart(pagnationParam);
    assertEquals("SKIP 10 LIMIT 10", actual);
  }

  @Test
  public void getReturnCountPartTest() {
    var actual = CypherQueryHelper.getReturnCountPart("c", Neighborhood.ESSENTIAL);
    assertEquals("MATCH path=(c)-[*0..1]->(n) WHERE n:Permission OR n:User RETURN COUNT(c)", actual);
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
