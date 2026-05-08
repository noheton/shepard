package de.dlr.shepard.common.search.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.dlr.shepard.common.exceptions.ShepardParserException;
import de.dlr.shepard.common.neo4j.entities.ContainerType;
import de.dlr.shepard.common.search.endpoints.BasicContainerAttributes;
import de.dlr.shepard.common.search.io.SearchScope;
import de.dlr.shepard.common.util.SortingHelper;
import de.dlr.shepard.common.util.TraversalRules;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class Neo4jQueryBuilderTest {

  private static final String userName = "userName";

  private static final String READABLE_BY_USER =
    " AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"})))";

  private static String readableByUser(String alias, String user) {
    return (
      " AND (NOT exists((" +
      alias +
      ")-[:has_permissions]->(:Permissions)) OR exists((" +
      alias +
      ")-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"" +
      user +
      "\" })) OR exists((" +
      alias +
      ")-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((" +
      alias +
      ")-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((" +
      alias +
      ")-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"" +
      user +
      "\"})))"
    );
  }

  private static Stream<Arguments> emitCollectionDataObjectReferenceQuerySelectionWithNeo4jIdTest() {
    var queryEq = Arguments.of(
      """
      {
        "property": "name",
        "value": "MyName",
        "operator": "eq"
      }
      """,
      "MATCH (col:Collection)-[:has_dataobject]->(do:DataObject)-[:has_reference]->(br:BasicReference) WHERE (toLower(br.`name`) = toLower($p0)) AND (id(col) = $p1 AND id(do) = $p2) AND (br.deleted = FALSE)" +
      READABLE_BY_USER,
      Map.of("p0", "MyName", "p1", 1L, "p2", 2L)
    );
    var queryNe = Arguments.of(
      """
      {
        "property": "name",
        "value": "MyName",
        "operator": "ne"
      }
      """,
      "MATCH (col:Collection)-[:has_dataobject]->(do:DataObject)-[:has_reference]->(br:BasicReference) WHERE (toLower(br.`name`) <> toLower($p0)) AND (id(col) = $p1 AND id(do) = $p2) AND (br.deleted = FALSE)" +
      READABLE_BY_USER,
      Map.of("p0", "MyName", "p1", 1L, "p2", 2L)
    );

    return Stream.of(queryEq, queryNe);
  }

  @ParameterizedTest
  @MethodSource
  public void emitCollectionDataObjectReferenceQuerySelectionWithNeo4jIdTest(
    String input,
    String expectedCypher,
    Map<String, Object> expectedParams
  ) {
    SearchScope scope = new SearchScope();
    scope.setCollectionId(1L);
    scope.setDataObjectId(2L);
    Neo4jQuery neo4jQuery = Neo4jQueryBuilder.collectionDataObjectReferenceSelectionQueryWithNeo4jId(
      scope,
      input,
      userName
    );
    assertEquals(expectedCypher, neo4jQuery.cypher());
    assertEquals(expectedParams, neo4jQuery.params());
  }

  private static Stream<Arguments> emitCollectionDataObjectBasicReferenceQuerySelectionWithNeo4jIdTest() {
    String selectionTemplate =
      "MATCH (col:Collection)-[:has_dataobject]->(d:DataObject)%s(do:DataObject)-[:has_reference]->(br:BasicReference) WHERE (toLower(br.`name`) = toLower($p0)) AND (id(col) = $p1 AND id(d) = $p2) AND (br.deleted = FALSE)" +
      READABLE_BY_USER;
    Map<String, Object> commonParams = Map.of("p0", "MyName", "p1", 1L, "p2", 2L);
    var queryChildren = Arguments.of(
      """
      { "property": "name", "value": "MyName", "operator": "eq" }
      """,
      TraversalRules.children,
      selectionTemplate.formatted("-[:has_child*0..]->"),
      commonParams
    );
    var queryParents = Arguments.of(
      """
      { "property": "name", "value": "MyName", "operator": "eq" }
      """,
      TraversalRules.parents,
      selectionTemplate.formatted("<-[:has_child*0..]-"),
      commonParams
    );
    var querySuccessors = Arguments.of(
      """
      { "property": "name", "value": "MyName", "operator": "eq" }
      """,
      TraversalRules.successors,
      selectionTemplate.formatted("-[:has_successor*0..]->"),
      commonParams
    );
    var queryPredecessors = Arguments.of(
      """
      { "property": "name", "value": "MyName", "operator": "eq" }
      """,
      TraversalRules.predecessors,
      selectionTemplate.formatted("<-[:has_successor*0..]-"),
      commonParams
    );

    return Stream.of(queryChildren, queryParents, querySuccessors, queryPredecessors);
  }

  @ParameterizedTest
  @MethodSource
  public void emitCollectionDataObjectBasicReferenceQuerySelectionWithNeo4jIdTest(
    String input,
    TraversalRules traversalRules,
    String expectedCypher,
    Map<String, Object> expectedParams
  ) {
    SearchScope scope = new SearchScope();
    scope.setCollectionId(1L);
    scope.setDataObjectId(2L);
    Neo4jQuery neo4jQuery = Neo4jQueryBuilder.collectionDataObjectBasicReferenceSelectionQueryWithNeo4jId(
      scope,
      traversalRules,
      input,
      userName
    );
    assertEquals(expectedCypher, neo4jQuery.cypher());
    assertEquals(expectedParams, neo4jQuery.params());
  }

  @Test
  public void emitCollectionBasicReferenceSelectionQueryWithNeo4jIdTest() {
    String searchBodyQuery =
      """
      { "property": "name", "value": "MyName", "operator": "eq" }""";
    Neo4jQuery neo4jQuery = Neo4jQueryBuilder.collectionBasicReferenceSelectionQueryWithNeo4jId(
      searchBodyQuery,
      4L,
      userName
    );
    String expected =
      "MATCH (col:Collection)-[:has_dataobject]->(do:DataObject)-[:has_reference]->(br:BasicReference) WHERE (toLower(br.`name`) = toLower($p0)) AND (id(col) = $p1) AND (br.deleted = FALSE)" +
      READABLE_BY_USER;
    assertEquals(expected, neo4jQuery.cypher());
    assertEquals(Map.of("p0", "MyName", "p1", 4L), neo4jQuery.params());
  }

  @Test
  public void emitCollectionBasicReferenceSelectionQueryValueIRIWithNeo4jIdTest() {
    String searchBodyQuery =
      """
      { "property": "valueIRI", "value": "MyName", "operator": "eq" }""";
    Neo4jQuery neo4jQuery = Neo4jQueryBuilder.collectionBasicReferenceSelectionQueryWithNeo4jId(
      searchBodyQuery,
      4L,
      userName
    );
    String expected =
      "MATCH (col:Collection)-[:has_dataobject]->(do:DataObject)-[:has_reference]->(br:BasicReference) WHERE (EXISTS {MATCH (br) - [] -> (sem:SemanticAnnotation) WHERE (sem.valueIRI = $p0)}) AND (id(col) = $p1) AND (br.deleted = FALSE)" +
      READABLE_BY_USER;
    assertEquals(expected, neo4jQuery.cypher());
    assertEquals(Map.of("p0", "MyName", "p1", 4L), neo4jQuery.params());
  }

  @Test
  public void emitCollectionBasicReferenceSelectionQueryPropertyIRIWithNeo4jIdTest() {
    String searchBodyQuery =
      """
      { "property": "propertyIRI", "value": "MyName", "operator": "eq" }""";
    Neo4jQuery neo4jQuery = Neo4jQueryBuilder.collectionBasicReferenceSelectionQueryWithNeo4jId(
      searchBodyQuery,
      4L,
      userName
    );
    String expected =
      "MATCH (col:Collection)-[:has_dataobject]->(do:DataObject)-[:has_reference]->(br:BasicReference) WHERE (EXISTS {MATCH (br) - [] -> (sem:SemanticAnnotation) WHERE (sem.propertyIRI = $p0)}) AND (id(col) = $p1) AND (br.deleted = FALSE)" +
      READABLE_BY_USER;
    assertEquals(expected, neo4jQuery.cypher());
    assertEquals(Map.of("p0", "MyName", "p1", 4L), neo4jQuery.params());
  }

  @Test
  public void emitBasicReferenceSelectionQueryWithNeo4jIdTest() {
    String searchBodyQuery =
      """
      { "property": "name", "value": "MyName", "operator": "eq" }""";
    Neo4jQuery neo4jQuery = Neo4jQueryBuilder.basicReferenceSelectionQueryWithNeo4jId(searchBodyQuery, userName);
    String expected =
      "MATCH (col:Collection)-[:has_dataobject]->(do:DataObject)-[:has_reference]->(br:BasicReference) WHERE (toLower(br.`name`) = toLower($p0)) AND (br.deleted = FALSE)" +
      READABLE_BY_USER;
    assertEquals(expected, neo4jQuery.cypher());
    assertEquals(Map.of("p0", "MyName"), neo4jQuery.params());
  }

  @Test
  public void emitDataObjectQueryTest() {
    String searchBodyQuery =
      """
      {
      "property": "name", "value": "MyName", "operator": "eq" }""";
    Neo4jQuery neo4jQuery = Neo4jQueryBuilder.dataObjectSelectionQueryWithNeo4jId(searchBodyQuery, userName);
    String expected =
      "MATCH (col:Collection)-[:has_dataobject]->(do:DataObject) WHERE (toLower(do.`name`) = toLower($p0)) AND (do.deleted = FALSE)" +
      READABLE_BY_USER;
    assertEquals(expected, neo4jQuery.cypher());
    assertEquals(Map.of("p0", "MyName"), neo4jQuery.params());
  }

  @Test
  public void emitCollectionDataObjectDataObjectSelectionQueryWithNeo4jIdTest() {
    SearchScope scope = new SearchScope();
    scope.setCollectionId(1L);
    scope.setDataObjectId(2L);
    String searchBodyQuery =
      """
      { "property": "name", "value": "MyName", "operator": "eq" }""";
    Neo4jQuery neo4jQuery = Neo4jQueryBuilder.collectionDataObjectDataObjectSelectionQueryWithNeo4jId(
      scope,
      searchBodyQuery,
      userName
    );
    String expected =
      "MATCH (col:Collection)-[:has_dataobject]->(do:DataObject) WHERE (toLower(do.`name`) = toLower($p0)) AND (id(col) = $p1 AND id(do) = $p2) AND (do.deleted = FALSE)" +
      READABLE_BY_USER;
    assertEquals(expected, neo4jQuery.cypher());
    assertEquals(Map.of("p0", "MyName", "p1", 1L, "p2", 2L), neo4jQuery.params());
  }

  @Test
  public void emitCollectionDataObjectDataObjectSelectionQueryChildrenWithNeo4jIdTest() {
    SearchScope scope = new SearchScope();
    scope.setCollectionId(1L);
    scope.setDataObjectId(2L);
    String searchBodyQuery =
      """
      { "property": "name", "value": "MyName", "operator": "eq" }""";
    Neo4jQuery neo4jQuery = Neo4jQueryBuilder.collectionDataObjectDataObjectSelectionQueryWithNeo4jId(
      scope,
      TraversalRules.children,
      searchBodyQuery,
      userName
    );
    String expected =
      "MATCH (col:Collection)-[:has_dataobject]->(d:DataObject)-[:has_child*0..]->(do:DataObject) WHERE (toLower(do.`name`) = toLower($p0)) AND (id(col) = $p1 AND id(d) = $p2) AND (do.deleted = FALSE)" +
      READABLE_BY_USER;
    assertEquals(expected, neo4jQuery.cypher());
    assertEquals(Map.of("p0", "MyName", "p1", 1L, "p2", 2L), neo4jQuery.params());
  }

  @Test
  public void emitCollectionDataObjectDataObjectSelectionQueryPredecessorsWithNeo4jTest() {
    SearchScope scope = new SearchScope();
    scope.setCollectionId(1L);
    scope.setDataObjectId(2L);
    String searchBodyQuery =
      """
      { "property": "name", "value": "MyName", "operator": "eq" }""";
    Neo4jQuery neo4jQuery = Neo4jQueryBuilder.collectionDataObjectDataObjectSelectionQueryWithNeo4jId(
      scope,
      TraversalRules.predecessors,
      searchBodyQuery,
      userName
    );
    String expected =
      "MATCH (col:Collection)-[:has_dataobject]->(d:DataObject)<-[:has_successor*0..]-(do:DataObject) WHERE (toLower(do.`name`) = toLower($p0)) AND (id(col) = $p1 AND id(d) = $p2) AND (do.deleted = FALSE)" +
      READABLE_BY_USER;
    assertEquals(expected, neo4jQuery.cypher());
    assertEquals(Map.of("p0", "MyName", "p1", 1L, "p2", 2L), neo4jQuery.params());
  }

  @Test
  public void emitCollectionDataObjectQueryWithNeo4jIdTest() {
    SearchScope scope = new SearchScope();
    scope.setCollectionId(1L);
    scope.setDataObjectId(2L);
    String searchBodyQuery =
      """
      { "property": "name", "value": "MyName", "operator": "eq" }""";
    Neo4jQuery neo4jQuery = Neo4jQueryBuilder.collectionDataObjectSelectionQueryWithNeo4jId(
      scope.getCollectionId(),
      searchBodyQuery,
      userName
    );
    String expected =
      "MATCH (col:Collection)-[:has_dataobject]->(do:DataObject) WHERE (toLower(do.`name`) = toLower($p0)) AND (id(col) = $p1) AND (do.deleted = FALSE)" +
      READABLE_BY_USER;
    assertEquals(expected, neo4jQuery.cypher());
    assertEquals(Map.of("p0", "MyName", "p1", 1L), neo4jQuery.params());
  }

  private static Stream<Arguments> emitCollectionQueryWithNeo4jIdTest() {
    var queryEq = Arguments.of(
      """
      {
         "property":"name",
         "value":"MyName",
         "operator":"eq"
      }""",
      "MATCH (col:Collection) WHERE (toLower(col.`name`) = toLower($p0)) AND (col.deleted = FALSE)" + READABLE_BY_USER,
      Map.of("p0", "MyName")
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
      "MATCH (col:Collection) WHERE ((col.createdAt > toLower($p0)) OR (toLower(col.`attributes||b`) contains toLower($p1))) AND (col.deleted = FALSE)" +
      READABLE_BY_USER,
      Map.of("p0", "2021-05-12", "p1", "abc")
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
      "MATCH (col:Collection) WHERE ((col.createdAt > toLower($p0)) AND (toLower(col.`attributes||b`) contains toLower($p1))) AND (col.deleted = FALSE)" +
      READABLE_BY_USER,
      Map.of("p0", "2021-05-12", "p1", "abc")
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
      "MATCH (col:Collection) WHERE (NOT((toLower(col.`attributes||b`) contains toLower($p0)))) AND (col.deleted = FALSE)" +
      READABLE_BY_USER,
      Map.of("p0", "abc")
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
      "MATCH (col:Collection) WHERE ((EXISTS {MATCH (col)-[:points_to]->(refCol:Collection) WHERE id(refCol) <= $p0 }) AND (EXISTS {MATCH (col)-[:points_to]->(refDo:DataObject) WHERE id(refDo) contains $p1 })) AND (col.deleted = FALSE)" +
      READABLE_BY_USER,
      Map.of("p0", "2021-05-12", "p1", "abc")
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
      "MATCH (col:Collection) WHERE ((EXISTS {MATCH (col) - [:created_by] -> (u) WHERE toLower(u.username) > toLower($p0) }) AND (EXISTS {MATCH (col) - [:updated_by] -> (u) WHERE toLower(u.username) contains toLower($p1) })) AND (col.deleted = FALSE)" +
      READABLE_BY_USER,
      Map.of("p0", "2021-05-12", "p1", "abc")
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
      "MATCH (col:Collection) WHERE ((EXISTS {MATCH (col)-[:is_in_container]->(refCon:FileContainer) WHERE id(refCon) < $p0 }) AND (EXISTS {MATCH (col)-[:is_in_container]->(refCon:StructuredDataContainer) WHERE id(refCon) >= $p1 })) AND (col.deleted = FALSE)" +
      READABLE_BY_USER,
      Map.of("p0", 23, "p1", "abc")
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
      "MATCH (col:Collection) WHERE ((EXISTS {MATCH (col)-[:is_in_container]->(refCon:TimeseriesContainer) WHERE id(refCon) > $p0 }) AND (id(col) contains $p1)) AND (col.deleted = FALSE)" +
      READABLE_BY_USER,
      Map.of("p0", "2021-05-12", "p1", "abc")
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
      "MATCH (col:Collection) WHERE (NOT((toLower(col.`attributes||b`) IN [toLower($p0), toLower($p1), toLower($p2)]))) AND (col.deleted = FALSE)" +
      READABLE_BY_USER,
      Map.of("p0", 1, "p1", 2, "p2", "e")
    );
    var queryIn = Arguments.of(
      """
      {
         "property":"attributes||b",
         "value": [],
         "operator":"in"
      }""",
      "MATCH (col:Collection) WHERE (toLower(col.`attributes||b`) IN []) AND (col.deleted = FALSE)" + READABLE_BY_USER,
      Map.<String, Object>of()
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
  public void emitCollectionQueryWithNeo4jIdTest(String input, String expectedCypher, Map<String, Object> expectedParams) {
    Neo4jQuery neo4jQuery = Neo4jQueryBuilder.collectionSelectionQueryWithNeo4jId(
      input,
      userName,
      new SortingHelper(null, null)
    );
    assertEquals(expectedCypher, neo4jQuery.cypher());
    assertEquals(expectedParams, neo4jQuery.params());
  }

  private static Stream<Arguments> emitCollectionDataObjectDataObjectSelectionQueryTraversalWithNeo4jIdTest() {
    String selectionTemplate =
      "MATCH (col:Collection)-[:has_dataobject]->(d:DataObject)%s(do:DataObject) WHERE (toLower(do.`name`) = toLower($p0)) AND (id(col) = $p1 AND id(d) = $p2) AND (do.deleted = FALSE)" +
      READABLE_BY_USER;
    Map<String, Object> commonParams = Map.of("p0", "MyName", "p1", 1L, "p2", 2L);
    var queryParents = Arguments.of(
      """
      { "property": "name", "value": "MyName", "operator": "eq" }
      """,
      TraversalRules.parents,
      selectionTemplate.formatted("<-[:has_child*0..]-"),
      commonParams
    );
    var queryChildren = Arguments.of(
      """
      { "property": "name", "value": "MyName", "operator": "eq" }
      """,
      TraversalRules.children,
      selectionTemplate.formatted("-[:has_child*0..]->"),
      commonParams
    );
    var queryPredecessors = Arguments.of(
      """
      { "property": "name", "value": "MyName", "operator": "eq"}
      """,
      TraversalRules.predecessors,
      selectionTemplate.formatted("<-[:has_successor*0..]-"),
      commonParams
    );
    var querySuccessors = Arguments.of(
      """
      { "property": "name", "value": "MyName", "operator": "eq" }
      """,
      TraversalRules.successors,
      selectionTemplate.formatted("-[:has_successor*0..]->"),
      commonParams
    );
    return Stream.of(queryParents, queryChildren, queryPredecessors, querySuccessors);
  }

  @ParameterizedTest
  @MethodSource
  public void emitCollectionDataObjectDataObjectSelectionQueryTraversalWithNeo4jIdTest(
    String input,
    TraversalRules traversalRules,
    String expectedCypher,
    Map<String, Object> expectedParams
  ) {
    SearchScope scope = new SearchScope();
    scope.setCollectionId(1L);
    scope.setDataObjectId(2L);
    Neo4jQuery neo4jQuery = Neo4jQueryBuilder.collectionDataObjectDataObjectSelectionQueryWithNeo4jId(
      scope,
      traversalRules,
      input,
      userName
    );
    assertEquals(expectedCypher, neo4jQuery.cypher());
    assertEquals(expectedParams, neo4jQuery.params());
  }

  @Test
  public void emitStructuredDataContainerSelectionQueryWithNeo4jIdTest() {
    String JSONQuery = "{\"property\": \"name\", \"value\": \"MyName\", \"operator\": \"eq\"}";
    String userName = "MarxKarl";
    Neo4jQuery neo4jQuery = Neo4jQueryBuilder.containerSelectionQueryWithNeo4jId(
      JSONQuery,
      ContainerType.STRUCTUREDDATA,
      new SortingHelper(null, null),
      userName
    );
    String expected =
      "MATCH (sdc:StructuredDataContainer) WHERE (toLower(sdc.`name`) = toLower($p0)) AND (sdc.deleted = FALSE)" +
      readableByUser("sdc", userName);
    assertEquals(expected, neo4jQuery.cypher());
    assertEquals(Map.of("p0", "MyName"), neo4jQuery.params());
  }

  @Test
  public void emitTimeseriesContainerSelectionQueryWithNeo4jIdTest() {
    String JSONQuery = "{\"property\": \"name\", \"value\": \"MyName\", \"operator\": \"eq\"}";
    String userName = "MarxKarl";
    Neo4jQuery neo4jQuery = Neo4jQueryBuilder.containerSelectionQueryWithNeo4jId(
      JSONQuery,
      ContainerType.TIMESERIES,
      new SortingHelper(null, null),
      userName
    );
    String expected =
      "MATCH (tsc:TimeseriesContainer) WHERE (toLower(tsc.`name`) = toLower($p0)) AND (tsc.deleted = FALSE)" +
      readableByUser("tsc", userName);
    assertEquals(expected, neo4jQuery.cypher());
    assertEquals(Map.of("p0", "MyName"), neo4jQuery.params());
  }

  @Test
  public void emitFileContainerSelectionQueryWithNeo4jIdTest() {
    String JSONQuery = "{\"property\": \"name\", \"value\": \"MyName\", \"operator\": \"eq\"}";
    String userName = "GatesWilliam";
    Neo4jQuery neo4jQuery = Neo4jQueryBuilder.containerSelectionQueryWithNeo4jId(
      JSONQuery,
      ContainerType.FILE,
      new SortingHelper(null, null),
      userName
    );
    String expected =
      "MATCH (fc:FileContainer) WHERE (toLower(fc.`name`) = toLower($p0)) AND (fc.deleted = FALSE)" +
      readableByUser("fc", userName);
    assertEquals(expected, neo4jQuery.cypher());
    assertEquals(Map.of("p0", "MyName"), neo4jQuery.params());
  }

  @Test
  public void emitFileContainerSelectionQueryWithNeo4jIdIdTest() {
    String JSONQuery = "{\"property\": \"id\", \"value\": \"MyName\", \"operator\": \"eq\"}";
    String userName = "GatesWilliam";
    Neo4jQuery neo4jQuery = Neo4jQueryBuilder.containerSelectionQueryWithNeo4jId(
      JSONQuery,
      ContainerType.FILE,
      new SortingHelper(null, null),
      userName
    );
    String expected =
      "MATCH (fc:FileContainer) WHERE (id(fc) = $p0) AND (fc.deleted = FALSE)" + readableByUser("fc", userName);
    assertEquals(expected, neo4jQuery.cypher());
    assertEquals(Map.of("p0", "MyName"), neo4jQuery.params());
  }

  @Test
  public void emitFileContainerSelectionQueryInWithNeo4jIdTest() {
    String JSONQuery = "{\"property\": \"id\", \"value\": [1,2], \"operator\": \"in\"}";
    String userName = "GatesWilliam";
    Neo4jQuery neo4jQuery = Neo4jQueryBuilder.containerSelectionQueryWithNeo4jId(
      JSONQuery,
      ContainerType.FILE,
      new SortingHelper(null, null),
      userName
    );
    String expected =
      "MATCH (fc:FileContainer) WHERE (id(fc) IN [$p0, $p1]) AND (fc.deleted = FALSE)" + readableByUser("fc", userName);
    assertEquals(expected, neo4jQuery.cypher());
    assertEquals(Map.of("p0", 1, "p1", 2), neo4jQuery.params());
  }

  @Test
  public void emitFileContainerSelectionQueryInEmptyWithNeo4jIdTest() {
    String JSONQuery = "{\"property\": \"id\", \"value\": [], \"operator\": \"in\"}";
    String userName = "GatesWilliam";
    Neo4jQuery neo4jQuery = Neo4jQueryBuilder.containerSelectionQueryWithNeo4jId(
      JSONQuery,
      ContainerType.FILE,
      new SortingHelper(null, null),
      userName
    );
    String expected =
      "MATCH (fc:FileContainer) WHERE (id(fc) IN []) AND (fc.deleted = FALSE)" + readableByUser("fc", userName);
    assertEquals(expected, neo4jQuery.cypher());
    assertEquals(Map.<String, Object>of(), neo4jQuery.params());
  }

  @Test
  public void emitFileContainerSelectionQueryNotWithNeo4jIdTest() {
    String JSONQuery = "{\"NOT\":{\"property\": \"name\", \"value\": \"MyName\", \"operator\": \"eq\"}}";
    String userName = "GatesWilliam";
    Neo4jQuery neo4jQuery = Neo4jQueryBuilder.containerSelectionQueryWithNeo4jId(
      JSONQuery,
      ContainerType.FILE,
      new SortingHelper(null, null),
      userName
    );
    String expected =
      "MATCH (fc:FileContainer) WHERE (NOT((toLower(fc.`name`) = toLower($p0)))) AND (fc.deleted = FALSE)" +
      readableByUser("fc", userName);
    assertEquals(expected, neo4jQuery.cypher());
    assertEquals(Map.of("p0", "MyName"), neo4jQuery.params());
  }

  @Test
  public void emitFileContainerSelectionQueryAndWithNeo4jIdTest() {
    String JSONQuery =
      "{\"AND\":[{\"property\": \"createdBy\", \"value\": \"MyName\", \"operator\": \"eq\"}," +
      "{\"property\": \"updatedBy\", \"value\": \"MyName\", \"operator\": \"eq\"}]}";
    String userName = "GatesWilliam";
    Neo4jQuery neo4jQuery = Neo4jQueryBuilder.containerSelectionQueryWithNeo4jId(
      JSONQuery,
      ContainerType.FILE,
      new SortingHelper(null, null),
      userName
    );
    String expected =
      "MATCH (fc:FileContainer) WHERE ((EXISTS {MATCH (fc) - [:created_by] -> (u) WHERE toLower(u.username) = toLower($p0) }) AND (EXISTS {MATCH (fc) - [:updated_by] -> (u) WHERE toLower(u.username) = toLower($p1) })) AND (fc.deleted = FALSE)" +
      readableByUser("fc", userName);
    assertEquals(expected, neo4jQuery.cypher());
    assertEquals(Map.of("p0", "MyName", "p1", "MyName"), neo4jQuery.params());
  }

  @Test
  public void emitFileContainerSelectionQueryOrWithNeo4jIdTest() {
    String JSONQuery =
      "{\"OR\":[{\"property\": \"valueIRI\", \"value\": \"MyName\", \"operator\": \"eq\"}," +
      "{\"property\": \"propertyIRI\", \"value\": \"MyName\", \"operator\": \"eq\"}]}";
    String userName = "GatesWilliam";
    Neo4jQuery neo4jQuery = Neo4jQueryBuilder.containerSelectionQueryWithNeo4jId(
      JSONQuery,
      ContainerType.FILE,
      new SortingHelper(null, null),
      userName
    );
    String expected =
      "MATCH (fc:FileContainer) WHERE ((EXISTS {MATCH (fc) - [] -> (sem:SemanticAnnotation) WHERE (sem.valueIRI = $p0)}) OR (EXISTS {MATCH (fc) - [] -> (sem:SemanticAnnotation) WHERE (sem.propertyIRI = $p1)})) AND (fc.deleted = FALSE)" +
      readableByUser("fc", userName);
    assertEquals(expected, neo4jQuery.cypher());
    assertEquals(Map.of("p0", "MyName", "p1", "MyName"), neo4jQuery.params());
  }

  @Test
  public void emitFileContainerSelectionQueryCollectionDataObjectWithNeo4jIdTest() {
    String JSONQuery =
      "{\"OR\":[{\"property\": \"referencedCollectionId\", \"value\": \"5\", \"operator\": \"eq\"}," +
      "{\"property\": \"referencedDataObjectId\", \"value\": \"6\", \"operator\": \"eq\"}]}";
    String userName = "GatesWilliam";
    Neo4jQuery neo4jQuery = Neo4jQueryBuilder.containerSelectionQueryWithNeo4jId(
      JSONQuery,
      ContainerType.FILE,
      new SortingHelper(null, null),
      userName
    );
    String expected =
      "MATCH (fc:FileContainer) WHERE ((EXISTS {MATCH (fc)-[:points_to]->(refCol:Collection) WHERE id(refCol) = $p0 }) OR (EXISTS {MATCH (fc)-[:points_to]->(refDo:DataObject) WHERE id(refDo) = $p1 })) AND (fc.deleted = FALSE)" +
      readableByUser("fc", userName);
    assertEquals(expected, neo4jQuery.cypher());
    assertEquals(Map.of("p0", "5", "p1", "6"), neo4jQuery.params());
  }

  @Test
  public void emitFileContainerSelectionContainerTest() {
    String JSONQuery =
      "{\"OR\":[{\"property\": \"fileContainerId\", \"value\": \"5\", \"operator\": \"eq\"}," +
      "{\"property\": \"structuredDataContainerId\", \"value\": \"6\", \"operator\": \"eq\"}," +
      "{\"property\": \"timeseriesContainerId\", \"value\": \"7\", \"operator\": \"eq\"}]}";
    String userName = "GatesWilliam";
    Neo4jQuery neo4jQuery = Neo4jQueryBuilder.containerSelectionQueryWithNeo4jId(
      JSONQuery,
      ContainerType.FILE,
      new SortingHelper(null, null),
      userName
    );
    String expected =
      "MATCH (fc:FileContainer) WHERE ((EXISTS {MATCH (fc)-[:is_in_container]->(refCon:FileContainer) WHERE id(refCon) = $p0 }) OR (EXISTS {MATCH (fc)-[:is_in_container]->(refCon:StructuredDataContainer) WHERE id(refCon) = $p1 }) OR (EXISTS {MATCH (fc)-[:is_in_container]->(refCon:TimeseriesContainer) WHERE id(refCon) = $p2 })) AND (fc.deleted = FALSE)" +
      readableByUser("fc", userName);
    assertEquals(expected, neo4jQuery.cypher());
    assertEquals(Map.of("p0", "5", "p1", "6", "p2", "7"), neo4jQuery.params());
  }

  @Test
  public void emitBasicContainerSortedSelectionContainerTest() {
    String JSONQuery =
      "{\"OR\":[{\"property\": \"fileContainerId\", \"value\": \"5\", \"operator\": \"eq\"}," +
      "{\"property\": \"structuredDataContainerId\", \"value\": \"6\", \"operator\": \"eq\"}," +
      "{\"property\": \"timeseriesContainerId\", \"value\": \"7\", \"operator\": \"eq\"}]}";
    String userName = "GatesWilliam";
    Neo4jQuery neo4jQuery = Neo4jQueryBuilder.containerSelectionQueryWithNeo4jId(
      JSONQuery,
      ContainerType.BASIC,
      new SortingHelper(BasicContainerAttributes.name, null),
      userName
    );
    String expected =
      "MATCH (bc:BasicContainer) WHERE ((EXISTS {MATCH (bc)-[:is_in_container]->(refCon:FileContainer) WHERE id(refCon) = $p0 }) OR (EXISTS {MATCH (bc)-[:is_in_container]->(refCon:StructuredDataContainer) WHERE id(refCon) = $p1 }) OR (EXISTS {MATCH (bc)-[:is_in_container]->(refCon:TimeseriesContainer) WHERE id(refCon) = $p2 })) AND (bc.deleted = FALSE)" +
      readableByUser("bc", userName) +
      " ORDER BY toLower(bc.name)";
    assertEquals(expected, neo4jQuery.cypher());
    assertEquals(Map.of("p0", "5", "p1", "6", "p2", "7"), neo4jQuery.params());
  }

  @Test
  public void emitUserSelectionQueryTest() {
    String JSONQuery = "{\"property\": \"username\", \"value\": \"user\", \"operator\": \"eq\"}";
    Neo4jQuery neo4jQuery = Neo4jQueryBuilder.userSelectionQuery(JSONQuery);
    String expected = "MATCH (user:User) WHERE (toLower(user.`username`) = toLower($p0))";
    assertEquals(expected, neo4jQuery.cypher());
    assertEquals(Map.of("p0", "user"), neo4jQuery.params());
  }

  @Test
  public void emitUserGroupSelectionQueryTest() {
    String JSONQuery = "{\"property\": \"name\", \"value\": \"group\", \"operator\": \"contains\"}";
    Neo4jQuery neo4jQuery = Neo4jQueryBuilder.userGroupSelectionQuery(JSONQuery);
    String expected = "MATCH (userGroup:UserGroup) WHERE (toLower(userGroup.`name`) contains toLower($p0))";
    assertEquals(expected, neo4jQuery.cypher());
    assertEquals(Map.of("p0", "group"), neo4jQuery.params());
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
