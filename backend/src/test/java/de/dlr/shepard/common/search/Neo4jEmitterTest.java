package de.dlr.shepard.common.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.dlr.shepard.common.exceptions.ShepardParserException;
import de.dlr.shepard.common.search.unified.SearchScope;
import de.dlr.shepard.common.util.TraversalRules;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class Neo4jEmitterTest {

  private static final String userName = "userName";

  private static Stream<Arguments> emitCollectionDataObjectReferenceQuerySelectionTest() {
    var queryEq = Arguments.of(
      """
      {
        "property": "name",
        "value": "MyName",
        "operator": "eq"
      }
      """,
      "MATCH (col:Collection)-[:has_dataobject]->(do:DataObject)-[:has_reference]->(br:BasicReference) WHERE (br.`name` = \"MyName\") AND (col.shepardId = 1 AND do.shepardId = 2) AND (br.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))"
    );
    var queryNe = Arguments.of(
      """
      {
        "property": "name",
        "value": "MyName",
        "operator": "ne"
      }
      """,
      "MATCH (col:Collection)-[:has_dataobject]->(do:DataObject)-[:has_reference]->(br:BasicReference) WHERE (br.`name` <> \"MyName\") AND (col.shepardId = 1 AND do.shepardId = 2) AND (br.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))"
    );

    return Stream.of(queryEq, queryNe);
  }

  @ParameterizedTest
  @MethodSource
  public void emitCollectionDataObjectReferenceQuerySelectionTest(String input, String expected) {
    SearchScope scope = new SearchScope();
    scope.setCollectionId(1L);
    scope.setDataObjectId(2L);
    String neo4jQuery = Neo4jEmitter.emitCollectionDataObjectReferenceSelectionQuery(scope, input, userName);
    assertEquals(expected, neo4jQuery);
  }

  private static Stream<Arguments> emitCollectionDataObjectBasicReferenceQuerySelectionTest() {
    var queryChildren = Arguments.of(
      """
      { "property": "name", "value": "MyName", "operator": "eq" }
      """,
      TraversalRules.children,
      "MATCH (col:Collection)-[:has_dataobject]->(d:DataObject)-[:has_child*0..]->(do:DataObject)-[:has_reference]->(br:BasicReference) WHERE (br.`name` = \"MyName\") AND (col.shepardId = 1 AND d.shepardId = 2) AND (br.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))"
    );
    var queryParents = Arguments.of(
      """
      { "property": "name", "value": "MyName", "operator": "eq" }
      """,
      TraversalRules.parents,
      "MATCH (col:Collection)-[:has_dataobject]->(d:DataObject)<-[:has_child*0..]-(do:DataObject)-[:has_reference]->(br:BasicReference) WHERE (br.`name` = \"MyName\") AND (col.shepardId = 1 AND d.shepardId = 2) AND (br.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))"
    );
    var querySuccessors = Arguments.of(
      """
      { "property": "name", "value": "MyName", "operator": "eq" }
      """,
      TraversalRules.successors,
      "MATCH (col:Collection)-[:has_dataobject]->(d:DataObject)-[:has_successor*0..]->(do:DataObject)-[:has_reference]->(br:BasicReference) WHERE (br.`name` = \"MyName\") AND (col.shepardId = 1 AND d.shepardId = 2) AND (br.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))"
    );
    var queryPredecessors = Arguments.of(
      """
      { "property": "name", "value": "MyName", "operator": "eq" }
      """,
      TraversalRules.predecessors,
      "MATCH (col:Collection)-[:has_dataobject]->(d:DataObject)<-[:has_successor*0..]-(do:DataObject)-[:has_reference]->(br:BasicReference) WHERE (br.`name` = \"MyName\") AND (col.shepardId = 1 AND d.shepardId = 2) AND (br.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))"
    );

    return Stream.of(queryChildren, queryParents, querySuccessors, queryPredecessors);
  }

  @ParameterizedTest
  @MethodSource
  public void emitCollectionDataObjectBasicReferenceQuerySelectionTest(
    String input,
    TraversalRules traversalRules,
    String expected
  ) {
    SearchScope scope = new SearchScope();
    scope.setCollectionId(1L);
    scope.setDataObjectId(2L);
    String neo4jQuery = Neo4jEmitter.emitCollectionDataObjectBasicReferenceSelectionQuery(
      scope,
      traversalRules,
      input,
      userName
    );
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitCollectionBasicReferenceSelectionQueryTest() {
    String searchBodyQuery =
      """
      { "property": "name", "value": "MyName", "operator": "eq" }""";
    String neo4jQuery = Neo4jEmitter.emitCollectionBasicReferenceSelectionQuery(searchBodyQuery, 4L, userName);
    String expected =
      "MATCH (col:Collection)-[:has_dataobject]->(do:DataObject)-[:has_reference]->(br:BasicReference) WHERE (br.`name` = \"MyName\") AND (col.shepardId = 4) AND (br.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))";
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitCollectionBasicReferenceSelectionQueryValueIRITest() {
    String searchBodyQuery =
      """
      { "property": "valueIRI", "value": "MyName", "operator": "eq" }""";
    String neo4jQuery = Neo4jEmitter.emitCollectionBasicReferenceSelectionQuery(searchBodyQuery, 4L, userName);
    String expected =
      "MATCH (col:Collection)-[:has_dataobject]->(do:DataObject)-[:has_reference]->(br:BasicReference) WHERE (EXISTS {MATCH (br) - [] -> (sem:SemanticAnnotation) WHERE (sem.valueIRI = \"MyName\")}) AND (col.shepardId = 4) AND (br.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))";
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitCollectionBasicReferenceSelectionQueryPropertyIRITest() {
    String searchBodyQuery =
      """
      { "property": "propertyIRI", "value": "MyName", "operator": "eq" }""";
    String neo4jQuery = Neo4jEmitter.emitCollectionBasicReferenceSelectionQuery(searchBodyQuery, 4L, userName);
    String expected =
      "MATCH (col:Collection)-[:has_dataobject]->(do:DataObject)-[:has_reference]->(br:BasicReference) WHERE (EXISTS {MATCH (br) - [] -> (sem:SemanticAnnotation) WHERE (sem.propertyIRI = \"MyName\")}) AND (col.shepardId = 4) AND (br.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))";
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitBasicReferenceSelectionQueryTest() {
    String searchBodyQuery =
      """
      { "property": "name", "value": "MyName", "operator": "eq" }""";
    String neo4jQuery = Neo4jEmitter.emitBasicReferenceSelectionQuery(searchBodyQuery, userName);
    String expected =
      "MATCH (col:Collection)-[:has_dataobject]->(do:DataObject)-[:has_reference]->(br:BasicReference) WHERE (br.`name` = \"MyName\") AND (br.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))";
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitDataObjectQueryTest() {
    String searchBodyQuery =
      """
      {
      "property": "name", "value": "MyName", "operator": "eq" }""";
    String neo4jQuery = Neo4jEmitter.emitDataObjectSelectionQuery(searchBodyQuery, userName);
    String expected =
      "MATCH (col:Collection)-[:has_dataobject]->(do:DataObject) WHERE (do.`name` = \"MyName\") AND (do.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))";
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitCollectionDataObjectDataObjectSelectionQueryTest() {
    SearchScope scope = new SearchScope();
    scope.setCollectionId(1L);
    scope.setDataObjectId(2L);
    String searchBodyQuery =
      """
      { "property": "name", "value": "MyName", "operator": "eq" }""";
    String neo4jQuery = Neo4jEmitter.emitCollectionDataObjectDataObjectSelectionQuery(scope, searchBodyQuery, userName);
    String expected =
      "MATCH (col:Collection)-[:has_dataobject]->(do:DataObject) WHERE (do.`name` = \"MyName\") AND (col.shepardId = 1 AND do.shepardId = 2) AND (do.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))";
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitCollectionDataObjectDataObjectSelectionQueryChildrenTest() {
    SearchScope scope = new SearchScope();
    scope.setCollectionId(1L);
    scope.setDataObjectId(2L);
    String searchBodyQuery =
      """
      { "property": "name", "value": "MyName", "operator": "eq" }""";
    String neo4jQuery = Neo4jEmitter.emitCollectionDataObjectDataObjectSelectionQuery(
      scope,
      TraversalRules.children,
      searchBodyQuery,
      userName
    );
    String expected =
      "MATCH (col:Collection)-[:has_dataobject]->(d:DataObject)-[:has_child*0..]->(do:DataObject) WHERE (do.`name` = \"MyName\") AND (col.shepardId = 1 AND d.shepardId = 2) AND (do.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))";
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitCollectionDataObjectDataObjectSelectionQueryPredecessorsTest() {
    SearchScope scope = new SearchScope();
    scope.setCollectionId(1L);
    scope.setDataObjectId(2L);
    String searchBodyQuery =
      """
      { "property": "name", "value": "MyName", "operator": "eq" }""";
    String neo4jQuery = Neo4jEmitter.emitCollectionDataObjectDataObjectSelectionQuery(
      scope,
      TraversalRules.predecessors,
      searchBodyQuery,
      userName
    );
    String expected =
      "MATCH (col:Collection)-[:has_dataobject]->(d:DataObject)<-[:has_successor*0..]-(do:DataObject) WHERE (do.`name` = \"MyName\") AND (col.shepardId = 1 AND d.shepardId = 2) AND (do.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))";
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitCollectionDataObjectQueryTest() {
    SearchScope scope = new SearchScope();
    scope.setCollectionId(1L);
    scope.setDataObjectId(2L);
    String searchBodyQuery =
      """
      { "property": "name", "value": "MyName", "operator": "eq" }""";
    String neo4jQuery = Neo4jEmitter.emitCollectionDataObjectSelectionQuery(
      scope.getCollectionId(),
      searchBodyQuery,
      userName
    );
    String expected =
      "MATCH (col:Collection)-[:has_dataobject]->(do:DataObject) WHERE (do.`name` = \"MyName\") AND (col.shepardId = 1) AND (do.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))";
    assertEquals(expected, neo4jQuery);
  }

  private static Stream<Arguments> emitCollectionQueryTest() {
    var queryEq = Arguments.of(
      """
      {
         "property":"name",
         "value":"MyName",
         "operator":"eq"
      }""",
      "MATCH (col:Collection) WHERE (col.`name` = \"MyName\") AND (col.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))"
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
      "MATCH (col:Collection) WHERE ((col.`createdAt` > \"2021-05-12\") OR (col.`attributes||b` contains \"abc\")) AND (col.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))"
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
      "MATCH (col:Collection) WHERE ((col.`createdAt` > \"2021-05-12\") AND (col.`attributes||b` contains \"abc\")) AND (col.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))"
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
      "MATCH (col:Collection) WHERE (NOT((col.`attributes||b` contains \"abc\"))) AND (col.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))"
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
      "MATCH (col:Collection) WHERE ((EXISTS {MATCH (col)-[:points_to]->(refCol:Collection) WHERE refCol.shepardId <= \"2021-05-12\" }) AND (EXISTS {MATCH (col)-[:points_to]->(refDo:DataObject) WHERE refDo.shepardId contains \"abc\" })) AND (col.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))"
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
      "MATCH (col:Collection) WHERE ((EXISTS {MATCH (col) - [:created_by] -> (u) WHERE u.username > \"2021-05-12\" }) AND (EXISTS {MATCH (col) - [:updated_by] -> (u) WHERE u.username contains \"abc\" })) AND (col.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))"
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
      "MATCH (col:Collection) WHERE ((EXISTS {MATCH (col)-[:is_in_container]->(refCon:TimeseriesContainer) WHERE id(refCon) > \"2021-05-12\" }) AND (col.shepardId contains \"abc\")) AND (col.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))"
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
      "MATCH (col:Collection) WHERE (NOT((col.`attributes||b` IN [1, 2, \"e\"]))) AND (col.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))"
    );
    var queryIn = Arguments.of(
      """
      {
         "property":"attributes||b",
         "value": [],
         "operator":"in"
      }""",
      "MATCH (col:Collection) WHERE (col.`attributes||b` IN []) AND (col.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))"
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
  public void emitCollectionQueryTest(String input, String expected) {
    String neo4jQuery = Neo4jEmitter.emitCollectionSelectionQuery(input, userName);
    assertEquals(expected, neo4jQuery);
  }

  private static Stream<Arguments> emitCollectionDataObjectDataObjectSelectionQueryTraversalTest() {
    var queryParents = Arguments.of(
      """
      { "property": "name", "value": "MyName", "operator": "eq" }
      """,
      TraversalRules.parents,
      "MATCH (col:Collection)-[:has_dataobject]->(d:DataObject)<-[:has_child*0..]-(do:DataObject) WHERE (do.`name` = \"MyName\") AND (col.shepardId = 1 AND d.shepardId = 2) AND (do.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))"
    );
    var queryChildren = Arguments.of(
      """
      { "property": "name", "value": "MyName", "operator": "eq" }
      """,
      TraversalRules.children,
      "MATCH (col:Collection)-[:has_dataobject]->(d:DataObject)-[:has_child*0..]->(do:DataObject) WHERE (do.`name` = \"MyName\") AND (col.shepardId = 1 AND d.shepardId = 2) AND (do.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))"
    );
    var queryPredecessors = Arguments.of(
      """
      { "property": "name", "value": "MyName", "operator": "eq"}
      """,
      TraversalRules.predecessors,
      "MATCH (col:Collection)-[:has_dataobject]->(d:DataObject)<-[:has_successor*0..]-(do:DataObject) WHERE (do.`name` = \"MyName\") AND (col.shepardId = 1 AND d.shepardId = 2) AND (do.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))"
    );
    var querySuccessors = Arguments.of(
      """
      { "property": "name", "value": "MyName", "operator": "eq" }
      """,
      TraversalRules.successors,
      "MATCH (col:Collection)-[:has_dataobject]->(d:DataObject)-[:has_successor*0..]->(do:DataObject) WHERE (do.`name` = \"MyName\") AND (col.shepardId = 1 AND d.shepardId = 2) AND (do.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))"
    );
    return Stream.of(queryParents, queryChildren, queryPredecessors, querySuccessors);
  }

  @ParameterizedTest
  @MethodSource
  public void emitCollectionDataObjectDataObjectSelectionQueryTraversalTest(
    String input,
    TraversalRules traversalRules,
    String expected
  ) {
    SearchScope scope = new SearchScope();
    scope.setCollectionId(1L);
    scope.setDataObjectId(2L);
    String neo4jQuery = Neo4jEmitter.emitCollectionDataObjectDataObjectSelectionQuery(
      scope,
      traversalRules,
      input,
      userName
    );
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitStructuredDataContainerSelectionQueryTest() {
    String JSONQuery = "{\"property\": \"name\", \"value\": \"MyName\", \"operator\": \"eq\"}";
    String userName = "MarxKarl";
    String neo4jQuery = Neo4jEmitter.emitStructuredDataContainerSelectionQuery(JSONQuery, userName);
    String expected =
      "MATCH (sdc:StructuredDataContainer) WHERE (sdc.`name` = \"MyName\") AND (sdc.deleted = FALSE) AND (NOT exists((sdc)-[:has_permissions]->(:Permissions)) OR exists((sdc)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"MarxKarl\" })) OR exists((sdc)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((sdc)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((sdc)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"MarxKarl\"})))";
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitTimeseriesContainerSelectionQueryTest() {
    String JSONQuery = "{\"property\": \"name\", \"value\": \"MyName\", \"operator\": \"eq\"}";
    String userName = "MarxKarl";
    String neo4jQuery = Neo4jEmitter.emitTimeseriesContainerSelectionQuery(JSONQuery, userName);
    String expected =
      "MATCH (tsc:TimeseriesContainer) WHERE (tsc.`name` = \"MyName\") AND (tsc.deleted = FALSE) AND (NOT exists((tsc)-[:has_permissions]->(:Permissions)) OR exists((tsc)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"MarxKarl\" })) OR exists((tsc)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((tsc)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((tsc)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"MarxKarl\"})))";
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitFileContainerSelectionQueryTest() {
    String JSONQuery = "{\"property\": \"name\", \"value\": \"MyName\", \"operator\": \"eq\"}";
    String userName = "GatesWilliam";
    String neo4jQuery = Neo4jEmitter.emitFileContainerSelectionQuery(JSONQuery, userName);
    String expected =
      "MATCH (fc:FileContainer) WHERE (fc.`name` = \"MyName\") AND (fc.deleted = FALSE) AND (NOT exists((fc)-[:has_permissions]->(:Permissions)) OR exists((fc)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"GatesWilliam\" })) OR exists((fc)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((fc)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((fc)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"GatesWilliam\"})))";
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitFileContainerSelectionQueryIdTest() {
    String JSONQuery = "{\"property\": \"id\", \"value\": \"MyName\", \"operator\": \"eq\"}";
    String userName = "GatesWilliam";
    String neo4jQuery = Neo4jEmitter.emitFileContainerSelectionQuery(JSONQuery, userName);
    String expected =
      "MATCH (fc:FileContainer) WHERE (id(fc)= \"MyName\") AND (fc.deleted = FALSE) AND (NOT exists((fc)-[:has_permissions]->(:Permissions)) OR exists((fc)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"GatesWilliam\" })) OR exists((fc)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((fc)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((fc)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"GatesWilliam\"})))";
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitFileContainerSelectionQueryInTest() {
    String JSONQuery = "{\"property\": \"id\", \"value\": [1,2], \"operator\": \"in\"}";
    String userName = "GatesWilliam";
    String neo4jQuery = Neo4jEmitter.emitFileContainerSelectionQuery(JSONQuery, userName);
    String expected =
      "MATCH (fc:FileContainer) WHERE (id(fc)IN [1, 2]) AND (fc.deleted = FALSE) AND (NOT exists((fc)-[:has_permissions]->(:Permissions)) OR exists((fc)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"GatesWilliam\" })) OR exists((fc)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((fc)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((fc)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"GatesWilliam\"})))";
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitFileContainerSelectionQueryInEmptyTest() {
    String JSONQuery = "{\"property\": \"id\", \"value\": [], \"operator\": \"in\"}";
    String userName = "GatesWilliam";
    String neo4jQuery = Neo4jEmitter.emitFileContainerSelectionQuery(JSONQuery, userName);
    String expected =
      "MATCH (fc:FileContainer) WHERE (id(fc)IN []) AND (fc.deleted = FALSE) AND (NOT exists((fc)-[:has_permissions]->(:Permissions)) OR exists((fc)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"GatesWilliam\" })) OR exists((fc)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((fc)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((fc)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"GatesWilliam\"})))";
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitFileContainerSelectionQueryNotTest() {
    String JSONQuery = "{\"NOT\":{\"property\": \"name\", \"value\": \"MyName\", \"operator\": \"eq\"}}";
    String userName = "GatesWilliam";
    String neo4jQuery = Neo4jEmitter.emitFileContainerSelectionQuery(JSONQuery, userName);
    String expected =
      "MATCH (fc:FileContainer) WHERE (NOT((fc.`name` = \"MyName\"))) AND (fc.deleted = FALSE) AND (NOT exists((fc)-[:has_permissions]->(:Permissions)) OR exists((fc)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"GatesWilliam\" })) OR exists((fc)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((fc)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((fc)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"GatesWilliam\"})))";
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitFileContainerSelectionQueryAndTest() {
    String JSONQuery =
      "{\"AND\":[{\"property\": \"createdBy\", \"value\": \"MyName\", \"operator\": \"eq\"}," +
      "{\"property\": \"updatedBy\", \"value\": \"MyName\", \"operator\": \"eq\"}]}";
    String userName = "GatesWilliam";
    String neo4jQuery = Neo4jEmitter.emitFileContainerSelectionQuery(JSONQuery, userName);
    String expected =
      "MATCH (fc:FileContainer) WHERE ((EXISTS {MATCH (fc) - [:created_by] -> (u) WHERE u.username = \"MyName\" }) AND (EXISTS {MATCH (fc) - [:updated_by] -> (u) WHERE u.username = \"MyName\" })) AND (fc.deleted = FALSE) AND (NOT exists((fc)-[:has_permissions]->(:Permissions)) OR exists((fc)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"GatesWilliam\" })) OR exists((fc)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((fc)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((fc)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"GatesWilliam\"})))";
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitFileContainerSelectionQueryOrTest() {
    String JSONQuery =
      "{\"OR\":[{\"property\": \"valueIRI\", \"value\": \"MyName\", \"operator\": \"eq\"}," +
      "{\"property\": \"propertyIRI\", \"value\": \"MyName\", \"operator\": \"eq\"}]}";
    String userName = "GatesWilliam";
    String neo4jQuery = Neo4jEmitter.emitFileContainerSelectionQuery(JSONQuery, userName);
    String expected =
      "MATCH (fc:FileContainer) WHERE ((EXISTS {MATCH (fc) - [] -> (sem:SemanticAnnotation) WHERE (sem.valueIRI = \"MyName\")}) OR (EXISTS {MATCH (fc) - [] -> (sem:SemanticAnnotation) WHERE (sem.propertyIRI = \"MyName\")})) AND (fc.deleted = FALSE) AND (NOT exists((fc)-[:has_permissions]->(:Permissions)) OR exists((fc)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"GatesWilliam\" })) OR exists((fc)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((fc)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((fc)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"GatesWilliam\"})))";
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitFileContainerSelectionQueryCollectionDataObjectTest() {
    String JSONQuery =
      "{\"OR\":[{\"property\": \"referencedCollectionId\", \"value\": \"5\", \"operator\": \"eq\"}," +
      "{\"property\": \"referencedDataObjectId\", \"value\": \"6\", \"operator\": \"eq\"}]}";
    String userName = "GatesWilliam";
    String neo4jQuery = Neo4jEmitter.emitFileContainerSelectionQuery(JSONQuery, userName);
    String expected =
      "MATCH (fc:FileContainer) WHERE ((EXISTS {MATCH (fc)-[:points_to]->(refCol:Collection) WHERE refCol.shepardId = \"5\" }) OR (EXISTS {MATCH (fc)-[:points_to]->(refDo:DataObject) WHERE refDo.shepardId = \"6\" })) AND (fc.deleted = FALSE) AND (NOT exists((fc)-[:has_permissions]->(:Permissions)) OR exists((fc)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"GatesWilliam\" })) OR exists((fc)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((fc)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((fc)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"GatesWilliam\"})))";
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitFileContainerSelectionContainerTest() {
    String JSONQuery =
      "{\"OR\":[{\"property\": \"fileContainerId\", \"value\": \"5\", \"operator\": \"eq\"}," +
      "{\"property\": \"structuredDataContainerId\", \"value\": \"6\", \"operator\": \"eq\"}," +
      "{\"property\": \"timeseriesContainerId\", \"value\": \"7\", \"operator\": \"eq\"}]}";
    String userName = "GatesWilliam";
    String neo4jQuery = Neo4jEmitter.emitFileContainerSelectionQuery(JSONQuery, userName);
    String expected =
      "MATCH (fc:FileContainer) WHERE ((EXISTS {MATCH (fc)-[:is_in_container]->(refCon:FileContainer) WHERE id(refCon) = \"5\" }) OR (EXISTS {MATCH (fc)-[:is_in_container]->(refCon:StructuredDataContainer) WHERE id(refCon) = \"6\" }) OR (EXISTS {MATCH (fc)-[:is_in_container]->(refCon:TimeseriesContainer) WHERE id(refCon) = \"7\" })) AND (fc.deleted = FALSE) AND (NOT exists((fc)-[:has_permissions]->(:Permissions)) OR exists((fc)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"GatesWilliam\" })) OR exists((fc)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((fc)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((fc)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"GatesWilliam\"})))";
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void emitUserSelectionQueryTest() {
    String JSONQuery = "{\"property\": \"username\", \"value\": \"user\", \"operator\": \"eq\"}";
    String neo4jQuery = Neo4jEmitter.emitUserSelectionQuery(JSONQuery);
    String expected = "MATCH (user:User) WHERE (user.`username` = \"user\")";
    assertEquals(expected, neo4jQuery);
  }

  @Test
  public void invalidJsonTest() {
    String JSONQuery = "}";
    assertThrows(ShepardParserException.class, () -> Neo4jEmitter.emitCollectionSelectionQuery(JSONQuery, userName));
  }

  @Test
  public void emptyJsonTest() {
    String JSONQuery = "{}";
    assertThrows(ShepardParserException.class, () -> Neo4jEmitter.emitCollectionSelectionQuery(JSONQuery, userName));
  }

  @Test
  public void invalidOperatorTest() {
    String searchBodyQuery =
      """
      { "property": "name", "value": "MyName", "operator": "bla" }""";
    assertThrows(ShepardParserException.class, () ->
      Neo4jEmitter.emitCollectionSelectionQuery(searchBodyQuery, userName)
    );
  }

  @Test
  public void invalidBooleanOperatorTest() {
    String JSONQuery =
      """
      { "invalid": { "property": "name", "value": "MyName", "operator": "eq" } }""";
    assertThrows(ShepardParserException.class, () -> Neo4jEmitter.emitCollectionSelectionQuery(JSONQuery, userName));
  }
}
