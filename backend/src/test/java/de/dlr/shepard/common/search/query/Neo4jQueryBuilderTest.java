package de.dlr.shepard.common.search.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.dlr.shepard.common.exceptions.ShepardParserException;
import de.dlr.shepard.common.neo4j.entities.ContainerType;
import de.dlr.shepard.common.search.endpoints.BasicContainerAttributes;
import de.dlr.shepard.common.search.io.SearchScope;
import de.dlr.shepard.common.util.SortingHelper;
import de.dlr.shepard.common.util.TraversalRules;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class Neo4jQueryBuilderTest {

  private static final String userName = "userName";

  private static Stream<Arguments> emitCollectionDataObjectReferenceQuerySelectionWithNeo4jIdTest() {
    var queryEq = Arguments.of(
      """
      {
        "property": "name",
        "value": "MyName",
        "operator": "eq"
      }
      """,
      "MATCH (col:Collection)-[:has_dataobject]->(do:DataObject)-[:has_reference]->(br:BasicReference) WHERE (toLower(br.`name`) = \"myname\") AND (id(col) = 1 AND id(do) = 2) AND (br.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))"
    );
    var queryNe = Arguments.of(
      """
      {
        "property": "name",
        "value": "MyName",
        "operator": "ne"
      }
      """,
      "MATCH (col:Collection)-[:has_dataobject]->(do:DataObject)-[:has_reference]->(br:BasicReference) WHERE (toLower(br.`name`) <> \"myname\") AND (id(col) = 1 AND id(do) = 2) AND (br.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))"
    );

    return Stream.of(queryEq, queryNe);
  }

  @ParameterizedTest
  @MethodSource
  public void emitCollectionDataObjectReferenceQuerySelectionWithNeo4jIdTest(String input, String expected) {
    SearchScope scope = new SearchScope();
    scope.setCollectionId(1L);
    scope.setDataObjectId(2L);
    String neo4jQuery = Neo4jQueryBuilder.collectionDataObjectReferenceSelectionQueryWithNeo4jId(
      scope,
      input,
      userName
    );
    assertEquals(expected, neo4jQuery);
  }

  private static Stream<Arguments> emitCollectionDataObjectBasicReferenceQuerySelectionWithNeo4jIdTest() {
    var queryChildren = Arguments.of(
      """
      { "property": "name", "value": "MyName", "operator": "eq" }
      """,
      TraversalRules.children,
      "MATCH (col:Collection)-[:has_dataobject]->(d:DataObject)-[:has_child*0..]->(do:DataObject)-[:has_reference]->(br:BasicReference) WHERE (toLower(br.`name`) = \"myname\") AND (id(col) = 1 AND id(d) = 2) AND (br.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))"
    );
    var queryParents = Arguments.of(
      """
      { "property": "name", "value": "MyName", "operator": "eq" }
      """,
      TraversalRules.parents,
      "MATCH (col:Collection)-[:has_dataobject]->(d:DataObject)<-[:has_child*0..]-(do:DataObject)-[:has_reference]->(br:BasicReference) WHERE (toLower(br.`name`) = \"myname\") AND (id(col) = 1 AND id(d) = 2) AND (br.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))"
    );
    var querySuccessors = Arguments.of(
      """
      { "property": "name", "value": "MyName", "operator": "eq" }
      """,
      TraversalRules.successors,
      "MATCH (col:Collection)-[:has_dataobject]->(d:DataObject)-[:has_successor*0..]->(do:DataObject)-[:has_reference]->(br:BasicReference) WHERE (toLower(br.`name`) = \"myname\") AND (id(col) = 1 AND id(d) = 2) AND (br.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))"
    );
    var queryPredecessors = Arguments.of(
      """
      { "property": "name", "value": "MyName", "operator": "eq" }
      """,
      TraversalRules.predecessors,
      "MATCH (col:Collection)-[:has_dataobject]->(d:DataObject)<-[:has_successor*0..]-(do:DataObject)-[:has_reference]->(br:BasicReference) WHERE (toLower(br.`name`) = \"myname\") AND (id(col) = 1 AND id(d) = 2) AND (br.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))"
    );

    return Stream.of(queryChildren, queryParents, querySuccessors, queryPredecessors);
  }

  @ParameterizedTest
  @MethodSource
  public void emitCollectionDataObjectBasicReferenceQuerySelectionWithNeo4jIdTest(
    String input,
    TraversalRules traversalRules,
    String expected
  ) {
    SearchScope scope = new SearchScope();
    scope.setCollectionId(1L);
    scope.setDataObjectId(2L);
    String neo4jQuery = Neo4jQueryBuilder.collectionDataObjectBasicReferenceSelectionQueryWithNeo4jId(
      scope,
      traversalRules,
      input,
      userName
    );
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitCollectionBasicReferenceSelectionQueryWithNeo4jIdTest() {
    String searchBodyQuery =
      """
      { "property": "name", "value": "MyName", "operator": "eq" }""";
    String neo4jQuery = Neo4jQueryBuilder.collectionBasicReferenceSelectionQueryWithNeo4jId(
      searchBodyQuery,
      4L,
      userName
    );
    String expected =
      "MATCH (col:Collection)-[:has_dataobject]->(do:DataObject)-[:has_reference]->(br:BasicReference) WHERE (toLower(br.`name`) = \"myname\") AND (id(col) = 4) AND (br.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))";
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitCollectionBasicReferenceSelectionQueryValueIRIWithNeo4jIdTest() {
    String searchBodyQuery =
      """
      { "property": "valueIRI", "value": "MyName", "operator": "eq" }""";
    String neo4jQuery = Neo4jQueryBuilder.collectionBasicReferenceSelectionQueryWithNeo4jId(
      searchBodyQuery,
      4L,
      userName
    );
    String expected =
      "MATCH (col:Collection)-[:has_dataobject]->(do:DataObject)-[:has_reference]->(br:BasicReference) WHERE (EXISTS {MATCH (br) - [] -> (sem:SemanticAnnotation) WHERE (sem.valueIRI = \"MyName\")}) AND (id(col) = 4) AND (br.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))";
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitCollectionBasicReferenceSelectionQueryPropertyIRIWithNeo4jIdTest() {
    String searchBodyQuery =
      """
      { "property": "propertyIRI", "value": "MyName", "operator": "eq" }""";
    String neo4jQuery = Neo4jQueryBuilder.collectionBasicReferenceSelectionQueryWithNeo4jId(
      searchBodyQuery,
      4L,
      userName
    );
    String expected =
      "MATCH (col:Collection)-[:has_dataobject]->(do:DataObject)-[:has_reference]->(br:BasicReference) WHERE (EXISTS {MATCH (br) - [] -> (sem:SemanticAnnotation) WHERE (sem.propertyIRI = \"MyName\")}) AND (id(col) = 4) AND (br.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))";
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitBasicReferenceSelectionQueryWithNeo4jIdTest() {
    String searchBodyQuery =
      """
      { "property": "name", "value": "MyName", "operator": "eq" }""";
    String neo4jQuery = Neo4jQueryBuilder.basicReferenceSelectionQueryWithNeo4jId(searchBodyQuery, userName);
    String expected =
      "MATCH (col:Collection)-[:has_dataobject]->(do:DataObject)-[:has_reference]->(br:BasicReference) WHERE (toLower(br.`name`) = \"myname\") AND (br.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))";
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitDataObjectQueryTest() {
    String searchBodyQuery =
      """
      {
      "property": "name", "value": "MyName", "operator": "eq" }""";
    String neo4jQuery = Neo4jQueryBuilder.dataObjectSelectionQueryWithNeo4jId(searchBodyQuery, userName);
    String expected =
      "MATCH (col:Collection)-[:has_dataobject]->(do:DataObject) WHERE (toLower(do.`name`) = \"myname\") AND (do.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))";
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitCollectionDataObjectDataObjectSelectionQueryWithNeo4jIdTest() {
    SearchScope scope = new SearchScope();
    scope.setCollectionId(1L);
    scope.setDataObjectId(2L);
    String searchBodyQuery =
      """
      { "property": "name", "value": "MyName", "operator": "eq" }""";
    String neo4jQuery = Neo4jQueryBuilder.collectionDataObjectDataObjectSelectionQueryWithNeo4jId(
      scope,
      searchBodyQuery,
      userName
    );
    String expected =
      "MATCH (col:Collection)-[:has_dataobject]->(do:DataObject) WHERE (toLower(do.`name`) = \"myname\") AND (id(col) = 1 AND id(do) = 2) AND (do.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))";
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitCollectionDataObjectDataObjectSelectionQueryChildrenWithNeo4jIdTest() {
    SearchScope scope = new SearchScope();
    scope.setCollectionId(1L);
    scope.setDataObjectId(2L);
    String searchBodyQuery =
      """
      { "property": "name", "value": "MyName", "operator": "eq" }""";
    String neo4jQuery = Neo4jQueryBuilder.collectionDataObjectDataObjectSelectionQueryWithNeo4jId(
      scope,
      TraversalRules.children,
      searchBodyQuery,
      userName
    );
    String expected =
      "MATCH (col:Collection)-[:has_dataobject]->(d:DataObject)-[:has_child*0..]->(do:DataObject) WHERE (toLower(do.`name`) = \"myname\") AND (id(col) = 1 AND id(d) = 2) AND (do.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))";
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitCollectionDataObjectDataObjectSelectionQueryPredecessorsWithNeo4jTest() {
    SearchScope scope = new SearchScope();
    scope.setCollectionId(1L);
    scope.setDataObjectId(2L);
    String searchBodyQuery =
      """
      { "property": "name", "value": "MyName", "operator": "eq" }""";
    String neo4jQuery = Neo4jQueryBuilder.collectionDataObjectDataObjectSelectionQueryWithNeo4jId(
      scope,
      TraversalRules.predecessors,
      searchBodyQuery,
      userName
    );
    String expected =
      "MATCH (col:Collection)-[:has_dataobject]->(d:DataObject)<-[:has_successor*0..]-(do:DataObject) WHERE (toLower(do.`name`) = \"myname\") AND (id(col) = 1 AND id(d) = 2) AND (do.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))";
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitCollectionDataObjectQueryWithNeo4jIdTest() {
    SearchScope scope = new SearchScope();
    scope.setCollectionId(1L);
    scope.setDataObjectId(2L);
    String searchBodyQuery =
      """
      { "property": "name", "value": "MyName", "operator": "eq" }""";
    String neo4jQuery = Neo4jQueryBuilder.collectionDataObjectSelectionQueryWithNeo4jId(
      scope.getCollectionId(),
      searchBodyQuery,
      userName
    );
    String expected =
      "MATCH (col:Collection)-[:has_dataobject]->(do:DataObject) WHERE (toLower(do.`name`) = \"myname\") AND (id(col) = 1) AND (do.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))";
    assertEquals(expected, neo4jQuery);
  }

  private static Stream<Arguments> emitCollectionQueryWithNeo4jIdTest() {
    var queryEq = Arguments.of(
      """
      {
         "property":"name",
         "value":"MyName",
         "operator":"eq"
      }""",
      "MATCH (col:Collection) WHERE (toLower(col.`name`) = \"myname\") AND (col.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))"
    );
    var queryOrGtContains = Arguments.of(
      """
      {
         "OR":[
            {
               "property":"createdAt",
               "value":"2021-05-12",
               "operator":"gt"
            },
            {
               "property":"attributes||b",
               "value":"abc",
               "operator":"contains"
            }
         ]
      }""",
      "MATCH (col:Collection) WHERE ((col.createdAt > \"2021-05-12\") OR (toLower(col.`attributes||b`) contains \"abc\")) AND (col.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))"
    );
    var queryAndGtContains = Arguments.of(
      """
      {
         "AND":[
            {
               "property":"createdAt",
               "value":"2021-05-12",
               "operator":"gt"
            },
            {
               "property":"attributes||b",
               "value":"abc",
               "operator":"contains"
            }
         ]
      }""",
      "MATCH (col:Collection) WHERE ((col.createdAt > \"2021-05-12\") AND (toLower(col.`attributes||b`) contains \"abc\")) AND (col.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))"
    );
    var queryNot = Arguments.of(
      """
      {
         "NOT":{
            "property":"attributes||b",
            "value":"abc",
            "operator":"contains"
         }
      }""",
      "MATCH (col:Collection) WHERE (NOT((toLower(col.`attributes||b`) contains \"abc\"))) AND (col.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))"
    );
    var queryAndReferencedCollectionIdReferencedDataObjectId = Arguments.of(
      """
      {
         "AND":[
            {
               "property":"referencedCollectionId",
               "value":"2021-05-12",
               "operator":"le"
            },
            {
               "property":"referencedDataObjectId",
               "value":"abc",
               "operator":"contains"
            }
         ]
      }""",
      "MATCH (col:Collection) WHERE ((EXISTS {MATCH (col)-[:points_to]->(refCol:Collection) WHERE id(refCol) <= \"2021-05-12\" }) AND (EXISTS {MATCH (col)-[:points_to]->(refDo:DataObject) WHERE id(refDo) contains \"abc\" })) AND (col.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))"
    );
    var queryAndCreatedByGtUpdatedBy = Arguments.of(
      """
      {
         "AND":[
            {
               "property":"createdBy",
               "value":"2021-05-12",
               "operator":"gt"
            },
            {
               "property":"updatedBy",
               "value":"abc",
               "operator":"contains"
            }
         ]
      }""",
      "MATCH (col:Collection) WHERE ((EXISTS {MATCH (col) - [:created_by] -> (u) WHERE toLower(u.username) > \"2021-05-12\" }) AND (EXISTS {MATCH (col) - [:updated_by] -> (u) WHERE toLower(u.username) contains \"abc\" })) AND (col.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))"
    );
    var queryAndFileContainerIdStructuredDataContainerIdLtGe = Arguments.of(
      """
      {
         "AND":[
            {
               "property":"fileContainerId",
               "value":23,
               "operator":"lt"
            },
            {
               "property":"structuredDataContainerId",
               "value":"abc",
               "operator":"ge"
            }
         ]
      }""",
      "MATCH (col:Collection) WHERE ((EXISTS {MATCH (col)-[:is_in_container]->(refCon:FileContainer) WHERE id(refCon) < 23 }) AND (EXISTS {MATCH (col)-[:is_in_container]->(refCon:StructuredDataContainer) WHERE id(refCon) >= \"abc\" })) AND (col.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))"
    );
    var querytimeseriesContainerIdId = Arguments.of(
      """
      {
         "AND":[
            {
               "property":"timeseriesContainerId",
               "value":"2021-05-12",
               "operator":"gt"
            },
            {
               "property":"id",
               "value":"abc",
               "operator":"contains"
            }
         ]
      }""",
      "MATCH (col:Collection) WHERE ((EXISTS {MATCH (col)-[:is_in_container]->(refCon:TimeseriesContainer) WHERE id(refCon) > \"2021-05-12\" }) AND (id(col) contains \"abc\")) AND (col.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))"
    );
    var queryNotIn = Arguments.of(
      """
      {
         "NOT":{
            "property":"attributes||b",
            "value":[
               1,
               2,
               "e"
            ],
            "operator":"in"
         }
      }""",
      "MATCH (col:Collection) WHERE (NOT((toLower(col.`attributes||b`) IN [1, 2, \"e\"]))) AND (col.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))"
    );
    var queryIn = Arguments.of(
      """
      {
         "property":"attributes||b",
         "value": [],
         "operator":"in"
      }""",
      "MATCH (col:Collection) WHERE (toLower(col.`attributes||b`) IN []) AND (col.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))"
    );

    return Stream.of(
      queryEq,
      queryOrGtContains,
      queryAndGtContains,
      queryNot,
      queryAndReferencedCollectionIdReferencedDataObjectId,
      queryAndCreatedByGtUpdatedBy,
      queryAndFileContainerIdStructuredDataContainerIdLtGe,
      querytimeseriesContainerIdId,
      queryNotIn,
      queryIn
    );
  }

  @ParameterizedTest
  @MethodSource
  public void emitCollectionQueryWithNeo4jIdTest(String input, String expected) {
    String neo4jQuery = Neo4jQueryBuilder.collectionSelectionQueryWithNeo4jId(
      input,
      userName,
      new SortingHelper(null, null)
    );
    assertEquals(expected, neo4jQuery);
  }

  private static Stream<Arguments> emitCollectionDataObjectDataObjectSelectionQueryTraversalWithNeo4jIdTest() {
    var queryParents = Arguments.of(
      """
      { "property": "name", "value": "MyName", "operator": "eq" }
      """,
      TraversalRules.parents,
      "MATCH (col:Collection)-[:has_dataobject]->(d:DataObject)<-[:has_child*0..]-(do:DataObject) WHERE (toLower(do.`name`) = \"myname\") AND (id(col) = 1 AND id(d) = 2) AND (do.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))"
    );
    var queryChildren = Arguments.of(
      """
      { "property": "name", "value": "MyName", "operator": "eq" }
      """,
      TraversalRules.children,
      "MATCH (col:Collection)-[:has_dataobject]->(d:DataObject)-[:has_child*0..]->(do:DataObject) WHERE (toLower(do.`name`) = \"myname\") AND (id(col) = 1 AND id(d) = 2) AND (do.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))"
    );
    var queryPredecessors = Arguments.of(
      """
      { "property": "name", "value": "MyName", "operator": "eq"}
      """,
      TraversalRules.predecessors,
      "MATCH (col:Collection)-[:has_dataobject]->(d:DataObject)<-[:has_successor*0..]-(do:DataObject) WHERE (toLower(do.`name`) = \"myname\") AND (id(col) = 1 AND id(d) = 2) AND (do.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))"
    );
    var querySuccessors = Arguments.of(
      """
      { "property": "name", "value": "MyName", "operator": "eq" }
      """,
      TraversalRules.successors,
      "MATCH (col:Collection)-[:has_dataobject]->(d:DataObject)-[:has_successor*0..]->(do:DataObject) WHERE (toLower(do.`name`) = \"myname\") AND (id(col) = 1 AND id(d) = 2) AND (do.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))"
    );
    return Stream.of(queryParents, queryChildren, queryPredecessors, querySuccessors);
  }

  @ParameterizedTest
  @MethodSource
  public void emitCollectionDataObjectDataObjectSelectionQueryTraversalWithNeo4jIdTest(
    String input,
    TraversalRules traversalRules,
    String expected
  ) {
    SearchScope scope = new SearchScope();
    scope.setCollectionId(1L);
    scope.setDataObjectId(2L);
    String neo4jQuery = Neo4jQueryBuilder.collectionDataObjectDataObjectSelectionQueryWithNeo4jId(
      scope,
      traversalRules,
      input,
      userName
    );
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitStructuredDataContainerSelectionQueryWithNeo4jIdTest() {
    String JSONQuery = "{\"property\": \"name\", \"value\": \"MyName\", \"operator\": \"eq\"}";
    String userName = "MarxKarl";
    String neo4jQuery = Neo4jQueryBuilder.containerSelectionQueryWithNeo4jId(
      JSONQuery,
      ContainerType.STRUCTUREDDATA,
      new SortingHelper(null, null),
      userName
    );
    String expected =
      "MATCH (sdc:StructuredDataContainer) WHERE (toLower(sdc.`name`) = \"myname\") AND (sdc.deleted = FALSE) AND (NOT exists((sdc)-[:has_permissions]->(:Permissions)) OR exists((sdc)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"MarxKarl\" })) OR exists((sdc)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((sdc)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((sdc)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"MarxKarl\"})))";
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitTimeseriesContainerSelectionQueryWithNeo4jIdTest() {
    String JSONQuery = "{\"property\": \"name\", \"value\": \"MyName\", \"operator\": \"eq\"}";
    String userName = "MarxKarl";
    String neo4jQuery = Neo4jQueryBuilder.containerSelectionQueryWithNeo4jId(
      JSONQuery,
      ContainerType.TIMESERIES,
      new SortingHelper(null, null),
      userName
    );
    String expected =
      "MATCH (tsc:TimeseriesContainer) WHERE (toLower(tsc.`name`) = \"myname\") AND (tsc.deleted = FALSE) AND (NOT exists((tsc)-[:has_permissions]->(:Permissions)) OR exists((tsc)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"MarxKarl\" })) OR exists((tsc)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((tsc)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((tsc)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"MarxKarl\"})))";
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitFileContainerSelectionQueryWithNeo4jIdTest() {
    String JSONQuery = "{\"property\": \"name\", \"value\": \"MyName\", \"operator\": \"eq\"}";
    String userName = "GatesWilliam";
    String neo4jQuery = Neo4jQueryBuilder.containerSelectionQueryWithNeo4jId(
      JSONQuery,
      ContainerType.FILE,
      new SortingHelper(null, null),
      userName
    );
    String expected =
      "MATCH (fc:FileContainer) WHERE (toLower(fc.`name`) = \"myname\") AND (fc.deleted = FALSE) AND (NOT exists((fc)-[:has_permissions]->(:Permissions)) OR exists((fc)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"GatesWilliam\" })) OR exists((fc)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((fc)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((fc)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"GatesWilliam\"})))";
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitFileContainerSelectionQueryWithNeo4jIdIdTest() {
    String JSONQuery = "{\"property\": \"id\", \"value\": \"MyName\", \"operator\": \"eq\"}";
    String userName = "GatesWilliam";
    String neo4jQuery = Neo4jQueryBuilder.containerSelectionQueryWithNeo4jId(
      JSONQuery,
      ContainerType.FILE,
      new SortingHelper(null, null),
      userName
    );
    String expected =
      "MATCH (fc:FileContainer) WHERE (id(fc) = \"MyName\") AND (fc.deleted = FALSE) AND (NOT exists((fc)-[:has_permissions]->(:Permissions)) OR exists((fc)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"GatesWilliam\" })) OR exists((fc)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((fc)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((fc)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"GatesWilliam\"})))";
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitFileContainerSelectionQueryInWithNeo4jIdTest() {
    String JSONQuery = "{\"property\": \"id\", \"value\": [1,2], \"operator\": \"in\"}";
    String userName = "GatesWilliam";
    String neo4jQuery = Neo4jQueryBuilder.containerSelectionQueryWithNeo4jId(
      JSONQuery,
      ContainerType.FILE,
      new SortingHelper(null, null),
      userName
    );
    String expected =
      "MATCH (fc:FileContainer) WHERE (id(fc) IN [1, 2]) AND (fc.deleted = FALSE) AND (NOT exists((fc)-[:has_permissions]->(:Permissions)) OR exists((fc)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"GatesWilliam\" })) OR exists((fc)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((fc)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((fc)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"GatesWilliam\"})))";
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitFileContainerSelectionQueryInEmptyWithNeo4jIdTest() {
    String JSONQuery = "{\"property\": \"id\", \"value\": [], \"operator\": \"in\"}";
    String userName = "GatesWilliam";
    String neo4jQuery = Neo4jQueryBuilder.containerSelectionQueryWithNeo4jId(
      JSONQuery,
      ContainerType.FILE,
      new SortingHelper(null, null),
      userName
    );
    String expected =
      "MATCH (fc:FileContainer) WHERE (id(fc) IN []) AND (fc.deleted = FALSE) AND (NOT exists((fc)-[:has_permissions]->(:Permissions)) OR exists((fc)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"GatesWilliam\" })) OR exists((fc)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((fc)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((fc)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"GatesWilliam\"})))";
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitFileContainerSelectionQueryNotWithNeo4jIdTest() {
    String JSONQuery = "{\"NOT\":{\"property\": \"name\", \"value\": \"MyName\", \"operator\": \"eq\"}}";
    String userName = "GatesWilliam";
    String neo4jQuery = Neo4jQueryBuilder.containerSelectionQueryWithNeo4jId(
      JSONQuery,
      ContainerType.FILE,
      new SortingHelper(null, null),
      userName
    );
    String expected =
      "MATCH (fc:FileContainer) WHERE (NOT((toLower(fc.`name`) = \"myname\"))) AND (fc.deleted = FALSE) AND (NOT exists((fc)-[:has_permissions]->(:Permissions)) OR exists((fc)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"GatesWilliam\" })) OR exists((fc)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((fc)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((fc)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"GatesWilliam\"})))";
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitFileContainerSelectionQueryAndWithNeo4jIdTest() {
    String JSONQuery =
      "{\"AND\":[{\"property\": \"createdBy\", \"value\": \"MyName\", \"operator\": \"eq\"}," +
      "{\"property\": \"updatedBy\", \"value\": \"MyName\", \"operator\": \"eq\"}]}";
    String userName = "GatesWilliam";
    String neo4jQuery = Neo4jQueryBuilder.containerSelectionQueryWithNeo4jId(
      JSONQuery,
      ContainerType.FILE,
      new SortingHelper(null, null),
      userName
    );
    String expected =
      "MATCH (fc:FileContainer) WHERE ((EXISTS {MATCH (fc) - [:created_by] -> (u) WHERE toLower(u.username) = \"myname\" }) AND (EXISTS {MATCH (fc) - [:updated_by] -> (u) WHERE toLower(u.username) = \"myname\" })) AND (fc.deleted = FALSE) AND (NOT exists((fc)-[:has_permissions]->(:Permissions)) OR exists((fc)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"GatesWilliam\" })) OR exists((fc)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((fc)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((fc)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"GatesWilliam\"})))";
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitFileContainerSelectionQueryOrWithNeo4jIdTest() {
    String JSONQuery =
      "{\"OR\":[{\"property\": \"valueIRI\", \"value\": \"MyName\", \"operator\": \"eq\"}," +
      "{\"property\": \"propertyIRI\", \"value\": \"MyName\", \"operator\": \"eq\"}]}";
    String userName = "GatesWilliam";
    String neo4jQuery = Neo4jQueryBuilder.containerSelectionQueryWithNeo4jId(
      JSONQuery,
      ContainerType.FILE,
      new SortingHelper(null, null),
      userName
    );
    String expected =
      "MATCH (fc:FileContainer) WHERE ((EXISTS {MATCH (fc) - [] -> (sem:SemanticAnnotation) WHERE (sem.valueIRI = \"MyName\")}) OR (EXISTS {MATCH (fc) - [] -> (sem:SemanticAnnotation) WHERE (sem.propertyIRI = \"MyName\")})) AND (fc.deleted = FALSE) AND (NOT exists((fc)-[:has_permissions]->(:Permissions)) OR exists((fc)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"GatesWilliam\" })) OR exists((fc)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((fc)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((fc)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"GatesWilliam\"})))";
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitFileContainerSelectionQueryCollectionDataObjectWithNeo4jIdTest() {
    String JSONQuery =
      "{\"OR\":[{\"property\": \"referencedCollectionId\", \"value\": \"5\", \"operator\": \"eq\"}," +
      "{\"property\": \"referencedDataObjectId\", \"value\": \"6\", \"operator\": \"eq\"}]}";
    String userName = "GatesWilliam";
    String neo4jQuery = Neo4jQueryBuilder.containerSelectionQueryWithNeo4jId(
      JSONQuery,
      ContainerType.FILE,
      new SortingHelper(null, null),
      userName
    );
    String expected =
      "MATCH (fc:FileContainer) WHERE ((EXISTS {MATCH (fc)-[:points_to]->(refCol:Collection) WHERE id(refCol) = \"5\" }) OR (EXISTS {MATCH (fc)-[:points_to]->(refDo:DataObject) WHERE id(refDo) = \"6\" })) AND (fc.deleted = FALSE) AND (NOT exists((fc)-[:has_permissions]->(:Permissions)) OR exists((fc)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"GatesWilliam\" })) OR exists((fc)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((fc)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((fc)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"GatesWilliam\"})))";
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitFileContainerSelectionContainerTest() {
    String JSONQuery =
      "{\"OR\":[{\"property\": \"fileContainerId\", \"value\": \"5\", \"operator\": \"eq\"}," +
      "{\"property\": \"structuredDataContainerId\", \"value\": \"6\", \"operator\": \"eq\"}," +
      "{\"property\": \"timeseriesContainerId\", \"value\": \"7\", \"operator\": \"eq\"}]}";
    String userName = "GatesWilliam";
    String neo4jQuery = Neo4jQueryBuilder.containerSelectionQueryWithNeo4jId(
      JSONQuery,
      ContainerType.FILE,
      new SortingHelper(null, null),
      userName
    );
    String expected =
      "MATCH (fc:FileContainer) WHERE ((EXISTS {MATCH (fc)-[:is_in_container]->(refCon:FileContainer) WHERE id(refCon) = \"5\" }) OR (EXISTS {MATCH (fc)-[:is_in_container]->(refCon:StructuredDataContainer) WHERE id(refCon) = \"6\" }) OR (EXISTS {MATCH (fc)-[:is_in_container]->(refCon:TimeseriesContainer) WHERE id(refCon) = \"7\" })) AND (fc.deleted = FALSE) AND (NOT exists((fc)-[:has_permissions]->(:Permissions)) OR exists((fc)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"GatesWilliam\" })) OR exists((fc)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((fc)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((fc)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"GatesWilliam\"})))";
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitBasicContainerSortedSelectionContainerTest() {
    String JSONQuery =
      "{\"OR\":[{\"property\": \"fileContainerId\", \"value\": \"5\", \"operator\": \"eq\"}," +
      "{\"property\": \"structuredDataContainerId\", \"value\": \"6\", \"operator\": \"eq\"}," +
      "{\"property\": \"timeseriesContainerId\", \"value\": \"7\", \"operator\": \"eq\"}]}";
    String userName = "GatesWilliam";
    String neo4jQuery = Neo4jQueryBuilder.containerSelectionQueryWithNeo4jId(
      JSONQuery,
      ContainerType.BASIC,
      new SortingHelper(BasicContainerAttributes.name, null),
      userName
    );
    String expected =
      "MATCH (bc:BasicContainer) WHERE ((EXISTS {MATCH (bc)-[:is_in_container]->(refCon:FileContainer) WHERE id(refCon) = \"5\" }) OR (EXISTS {MATCH (bc)-[:is_in_container]->(refCon:StructuredDataContainer) WHERE id(refCon) = \"6\" }) OR (EXISTS {MATCH (bc)-[:is_in_container]->(refCon:TimeseriesContainer) WHERE id(refCon) = \"7\" })) AND (bc.deleted = FALSE) AND (NOT exists((bc)-[:has_permissions]->(:Permissions)) OR exists((bc)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"GatesWilliam\" })) OR exists((bc)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((bc)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((bc)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"GatesWilliam\"}))) ORDER BY toLower(bc.name)";
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitUserSelectionQueryTest() {
    String JSONQuery = "{\"property\": \"username\", \"value\": \"user\", \"operator\": \"eq\"}";
    String neo4jQuery = Neo4jQueryBuilder.userSelectionQuery(JSONQuery);
    String expected = "MATCH (user:User) WHERE (toLower(user.`username`) = \"user\")";
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitUserGroupSelectionQueryTest() {
    String JSONQuery = "{\"property\": \"name\", \"value\": \"group\", \"operator\": \"contains\"}";
    String neo4jQuery = Neo4jQueryBuilder.userGroupSelectionQuery(JSONQuery);
    String expected = "MATCH (userGroup:UserGroup) WHERE (toLower(userGroup.`name`) contains \"group\")";
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void invalidJsonTest() {
    String JSONQuery = "}";
    assertThrows(ShepardParserException.class, () ->
      Neo4jQueryBuilder.collectionSelectionQueryWithNeo4jId(JSONQuery, userName, new SortingHelper(null, null))
    );
  }

  @Test
  public void emptyJsonTest() {
    String JSONQuery = "{}";
    assertThrows(ShepardParserException.class, () ->
      Neo4jQueryBuilder.collectionSelectionQueryWithNeo4jId(JSONQuery, userName, new SortingHelper(null, null))
    );
  }

  @Test
  public void invalidOperatorTest() {
    String searchBodyQuery =
      """
      { "property": "name", "value": "MyName", "operator": "bla" }""";
    assertThrows(ShepardParserException.class, () ->
      Neo4jQueryBuilder.collectionSelectionQueryWithNeo4jId(searchBodyQuery, userName, new SortingHelper(null, null))
    );
  }

  @Test
  public void invalidBooleanOperatorTest() {
    String JSONQuery =
      """
      { "invalid": { "property": "name", "value": "MyName", "operator": "eq" } }""";
    assertThrows(ShepardParserException.class, () ->
      Neo4jQueryBuilder.collectionSelectionQueryWithNeo4jId(JSONQuery, userName, new SortingHelper(null, null))
    );
  }
}
