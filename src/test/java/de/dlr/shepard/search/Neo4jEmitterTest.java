package de.dlr.shepard.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.exceptions.ShepardParserException;
import de.dlr.shepard.util.TraversalRules;

public class Neo4jEmitterTest extends BaseTestCase {

	private static final String userName = "userName";

	private static Stream<Arguments> emitCollectionDataObjectReferenceQueryTest() {
		var queryEq = Arguments.of("""
				{
				  "property": "name",
				  "value": "MyName",
				  "operator": "eq"
				}
				""",
				"MATCH (col:Collection)-[:has_dataobject]->(do:DataObject)-[:has_reference]->(br:BasicReference) WHERE (br.`name` = \"MyName\") AND (id(col) = 1 AND id(do) = 2) AND (br.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(do), id(br)");
		var queryNe = Arguments.of("""
				{
				  "property": "name",
				  "value": "MyName",
				  "operator": "ne"
				}
				""",
				"MATCH (col:Collection)-[:has_dataobject]->(do:DataObject)-[:has_reference]->(br:BasicReference) WHERE (br.`name` <> \"MyName\") AND (id(col) = 1 AND id(do) = 2) AND (br.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(do), id(br)");

		return Stream.of(queryEq, queryNe);
	}

	@ParameterizedTest
	@MethodSource
	public void emitCollectionDataObjectReferenceQueryTest(String input, String expected) {
		SearchScope scope = new SearchScope();
		scope.setCollectionId(1L);
		scope.setDataObjectId(2L);
		String neo4jQuery = Neo4jEmitter.emitCollectionDataObjectReferenceQuery(scope, input, userName);
		assertEquals(expected, neo4jQuery);
	}

	private static Stream<Arguments> emitCollectionDataObjectBasicReferenceQueryTest() {
		var queryChildren = Arguments.of("""
				{
				  "property": "name",
				  "value": "MyName",
				  "operator": "eq"
				}
				""", TraversalRules.children,
				"MATCH (col:Collection)-[:has_dataobject]->(d:DataObject)-[:has_child*0..]->(do:DataObject)-[:has_reference]->(br:BasicReference)  WHERE (br.`name` = \"MyName\") AND (id(col) = 1 AND id(d) = 2) AND (br.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(do), id(br)");
		var queryParents = Arguments.of("""
				{
				  "property": "name",
				  "value": "MyName",
				  "operator": "eq"
				}""", TraversalRules.parents,
				"MATCH (col:Collection)-[:has_dataobject]->(d:DataObject)<-[:has_child*0..]-(do:DataObject)-[:has_reference]->(br:BasicReference)  WHERE (br.`name` = \"MyName\") AND (id(col) = 1 AND id(d) = 2) AND (br.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(do), id(br)");
		var querySuccessors = Arguments.of("""
				{
				  "property": "name",
				  "value": "MyName",
				  "operator": "eq"
				}""", TraversalRules.successors,
				"MATCH (col:Collection)-[:has_dataobject]->(d:DataObject)-[:has_successor*0..]->(do:DataObject)-[:has_reference]->(br:BasicReference)  WHERE (br.`name` = \"MyName\") AND (id(col) = 1 AND id(d) = 2) AND (br.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(do), id(br)");
		var queryPredecessors = Arguments.of("""
				{
				  "property": "name",
				  "value": "MyName",
				  "operator": "eq"
				}""", TraversalRules.predecessors,
				"MATCH (col:Collection)-[:has_dataobject]->(d:DataObject)<-[:has_successor*0..]-(do:DataObject)-[:has_reference]->(br:BasicReference)  WHERE (br.`name` = \"MyName\") AND (id(col) = 1 AND id(d) = 2) AND (br.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(do), id(br)");

		return Stream.of(queryChildren, queryParents, querySuccessors, queryPredecessors);
	}

	@ParameterizedTest
	@MethodSource
	public void emitCollectionDataObjectBasicReferenceQueryTest(String input, TraversalRules traversalRules,
			String expected) {
		SearchScope scope = new SearchScope();
		scope.setCollectionId(1L);
		scope.setDataObjectId(2L);
		String neo4jQuery = Neo4jEmitter.emitCollectionDataObjectBasicReferenceQuery(scope, traversalRules, input,
				userName);
		assertEquals(expected, neo4jQuery);
	}

	@Test
	public void emitCollectionBasicReferenceQueryTest() {
		String searchBodyQuery = """
				{
				  "property": "name",
				  "value": "MyName",
				  "operator": "eq"
				}
				""";
		String neo4jQuery = Neo4jEmitter.emitCollectionBasicReferenceQuery(searchBodyQuery, 4L, userName);
		String expected = "MATCH (col:Collection)-[:has_dataobject]->(do:DataObject)-[:has_reference]->(br:BasicReference) WHERE (br.`name` = \"MyName\") AND (id(col) = 4) AND (br.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(do), id(br)";
		assertEquals(expected, neo4jQuery);
	}

	@Test
	public void emitCollectionBasicReferenceQueryValueIRITest() {
		String searchBodyQuery = """
				{
				  "property": "valueIRI",
				  "value": "MyName",
				  "operator": "eq"
				}
				""";
		String neo4jQuery = Neo4jEmitter.emitCollectionBasicReferenceQuery(searchBodyQuery, 4L, userName);
		String expected = "MATCH (col:Collection)-[:has_dataobject]->(do:DataObject)-[:has_reference]->(br:BasicReference) WHERE (EXISTS {MATCH (br) - [] -> (sem:SemanticAnnotation) WHERE (sem.valueIRI = \"MyName\" AND sem.deleted = FALSE)}) AND (id(col) = 4) AND (br.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(do), id(br)";
		assertEquals(expected, neo4jQuery);
	}

	@Test
	public void emitBasicReferenceQueryTest() {
		String searchBodyQuery = """
				{
				  "property": "name",
				  "value": "MyName",
				  "operator": "eq"
				}
				""";
		String neo4jQuery = Neo4jEmitter.emitBasicReferenceQuery(searchBodyQuery, userName);
		String expected = "MATCH (col:Collection)-[:has_dataobject]->(do:DataObject)-[:has_reference]->(br:BasicReference) WHERE (br.`name` = \"MyName\") AND (br.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(col), id(do), id(br)";
		assertEquals(expected, neo4jQuery);
	}

	@Test
	public void emitDataObjectQueryTest() {
		String searchBodyQuery = """
				{
				  "property": "name",
				  "value": "MyName",
				  "operator": "eq"
				}
				""";
		String neo4jQuery = Neo4jEmitter.emitDataObjectQuery(searchBodyQuery, userName);
		String expected = "MATCH (col:Collection)-[:has_dataobject]->(do:DataObject)  WHERE (do.`name` = \"MyName\") AND (do.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(col), id(do)";
		assertEquals(expected, neo4jQuery);
	}

	@Test
	public void emitCollectionDataObjectDataObjectQueryTest() {
		SearchScope scope = new SearchScope();
		scope.setCollectionId(1L);
		scope.setDataObjectId(2L);
		String searchBodyQuery = """
				{
				  "property": "name",
				  "value": "MyName",
				  "operator": "eq"
				}
				""";
		String neo4jQuery = Neo4jEmitter.emitCollectionDataObjectDataObjectQuery(scope, searchBodyQuery, userName);
		String expected = "MATCH (col:Collection)-[:has_dataobject]->(do:DataObject)  WHERE (do.`name` = \"MyName\") AND (id(col) = 1 AND id(do) = 2) AND (do.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(do)";
		assertEquals(expected, neo4jQuery);
	}

	@Test
	public void emitCollectionDataObjectDataObjectQueryChildrenTest() {
		SearchScope scope = new SearchScope();
		scope.setCollectionId(1L);
		scope.setDataObjectId(2L);
		String searchBodyQuery = """
				{
				  "property": "name",
				  "value": "MyName",
				  "operator": "eq"
				}
				""";
		String neo4jQuery = Neo4jEmitter.emitCollectionDataObjectDataObjectQuery(scope, TraversalRules.children,
				searchBodyQuery, userName);
		String expected = "MATCH (col:Collection)-[:has_dataobject]->(d:DataObject)-[:has_child*0..]->(do:DataObject)  WHERE (do.`name` = \"MyName\") AND (id(col) = 1 AND id(d) = 2) AND (do.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(do)";
		assertEquals(expected, neo4jQuery);
	}

	@Test
	public void emitCollectionDataObjectQueryTest() {
		SearchScope scope = new SearchScope();
		scope.setCollectionId(1L);
		scope.setDataObjectId(2L);
		String searchBodyQuery = """
				{
				  "property": "name",
				  "value": "MyName",
				  "operator": "eq"
				}""";
		String neo4jQuery = Neo4jEmitter.emitCollectionDataObjectQuery(scope.getCollectionId(), searchBodyQuery,
				userName);
		String expected = "MATCH (col:Collection)-[:has_dataobject]->(do:DataObject)  WHERE (do.`name` = \"MyName\") AND (id(col) = 1) AND (do.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(do)";
		assertEquals(expected, neo4jQuery);
	}

	private static Stream<Arguments> emitCollectionQueryTest() {
		var queryEq = Arguments.of("""
				{
				  "property": "name",
				  "value": "MyName",
				  "operator": "eq"
				}
				""",
				"MATCH (col:Collection) WHERE (col.`name` = \"MyName\") AND (col.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(col)");
		var queryOrGtContains = Arguments.of("""
				{
				   "OR":[
				      {
				         "property":"createdAt",
				         "value":"2021-05-12",
				         "operator":"gt"
				      },
				      {
				         "property":"attributes.b",
				         "value":"abc",
				         "operator":"contains"
				      }
				   ]
				}
				""",
				"MATCH (col:Collection) WHERE ((col.`createdAt` > \"2021-05-12\") OR (col.`attributes.b` contains \"abc\")) AND (col.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(col)");
		var queryAndGtContains = Arguments.of("""
				{
				   "AND":[
				      {
				         "property":"createdAt",
				         "value":"2021-05-12",
				         "operator":"gt"
				      },
				      {
				         "property":"attributes.b",
				         "value":"abc",
				         "operator":"contains"
				      }
				   ]
				}
				""",
				"MATCH (col:Collection) WHERE ((col.`createdAt` > \"2021-05-12\") AND (col.`attributes.b` contains \"abc\")) AND (col.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(col)");
		var queryNot = Arguments.of("""
				{
				  "NOT": {
				    "property": "attributes.b",
				    "value": "abc",
				    "operator": "contains"
				  }
				}
				""",
				"MATCH (col:Collection) WHERE (NOT((col.`attributes.b` contains \"abc\"))) AND (col.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(col)");
		var queryAndReferencedCollectionIdReferencedDataObjectId = Arguments.of("""
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
				}
				""",
				"MATCH (col:Collection) WHERE ((EXISTS {MATCH (col)-[:points_to]->(refCol:Collection) WHERE id(refCol) <= \"2021-05-12\" }) AND (EXISTS {MATCH (col)-[:points_to]->(refDo:DataObject) WHERE id(refDo) contains \"abc\" })) AND (col.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(col)");
		var queryAndCreatedByGtUpdatedBy = Arguments.of("""
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
				}
				""",
				"MATCH (col:Collection) WHERE ((EXISTS {MATCH (col) - [:created_by] -> (u) WHERE u.username > \"2021-05-12\" }) AND (EXISTS {MATCH (col) - [:updated_by] -> (u) WHERE u.username contains \"abc\" })) AND (col.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(col)");
		var queryAndFileContainerIdStructuredDataContainerIdLtGe = Arguments.of("""
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
				}
				""",
				"MATCH (col:Collection) WHERE ((EXISTS {MATCH (col)-[:is_in_container]->(refCon:FileContainer) WHERE id(refCon) < 23 }) AND (EXISTS {MATCH (col)-[:is_in_container]->(refCon:StructuredDataContainer) WHERE id(refCon) >= \"abc\" })) AND (col.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(col)");
		var querytimeseriesContainerIdId = Arguments.of("""
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
				}
				""",
				"MATCH (col:Collection) WHERE ((EXISTS {MATCH (col)-[:is_in_container]->(refCon:TimeseriesContainer) WHERE id(refCon) > \"2021-05-12\" }) AND (id(col)contains \"abc\")) AND (col.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(col)");
		var queryNotIn = Arguments.of("""
				{
				  "NOT": {
				    "property": "attributes.b",
				    "value": [1,2,"e"],
				    "operator": "in"
				  }
				}
				""",
				"MATCH (col:Collection) WHERE (NOT((col.`attributes.b` IN [1, 2, \"e\"]))) AND (col.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(col)");
		var queryIn = Arguments.of("""
				{
				     "property": "attributes.b",
				     "value": [],
				     "operator": "in"
				}
				""",
				"MATCH (col:Collection) WHERE (col.`attributes.b` IN []) AND (col.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(col)");

		return Stream.of(queryEq, queryOrGtContains, queryAndGtContains, queryNot,
				queryAndReferencedCollectionIdReferencedDataObjectId, queryAndCreatedByGtUpdatedBy,
				queryAndFileContainerIdStructuredDataContainerIdLtGe, querytimeseriesContainerIdId, queryNotIn,
				queryIn);
	}

	@ParameterizedTest
	@MethodSource
	public void emitCollectionQueryTest(String input, String expected) {
		String neo4jQuery = Neo4jEmitter.emitCollectionQuery(input, userName);
		assertEquals(expected, neo4jQuery);
	}

	private static Stream<Arguments> emitCollectionDataObjectDataObjectQueryTraversalTest() {
		var queryParents = Arguments.of("""
				{
				  "property": "name",
				  "value": "MyName",
				  "operator": "eq"
				}
				""", TraversalRules.parents,
				"MATCH (col:Collection)-[:has_dataobject]->(d:DataObject)<-[:has_child*0..]-(do:DataObject)  WHERE (do.`name` = \"MyName\") AND (id(col) = 1 AND id(d) = 2) AND (do.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(do)");
		var queryChildren = Arguments.of("""
				{
				  "property": "name",
				  "value": "MyName",
				  "operator": "eq"
				}
				""", TraversalRules.children,
				"MATCH (col:Collection)-[:has_dataobject]->(d:DataObject)-[:has_child*0..]->(do:DataObject)  WHERE (do.`name` = \"MyName\") AND (id(col) = 1 AND id(d) = 2) AND (do.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(do)");
		var queryPredecessors = Arguments.of("""
				{
				  "property": "name",
				  "value": "MyName",
				  "operator": "eq"
				}
				""", TraversalRules.predecessors,
				"MATCH (col:Collection)-[:has_dataobject]->(d:DataObject)<-[:has_successor*0..]-(do:DataObject)  WHERE (do.`name` = \"MyName\") AND (id(col) = 1 AND id(d) = 2) AND (do.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(do)");
		var querySuccessors = Arguments.of("""
				{
				  "property": "name",
				  "value": "MyName",
				  "operator": "eq"
				}
				""", TraversalRules.successors,
				"MATCH (col:Collection)-[:has_dataobject]->(d:DataObject)-[:has_successor*0..]->(do:DataObject)  WHERE (do.`name` = \"MyName\") AND (id(col) = 1 AND id(d) = 2) AND (do.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(do)");

		return Stream.of(queryParents, queryChildren, queryPredecessors, querySuccessors);
	}

	@ParameterizedTest
	@MethodSource
	public void emitCollectionDataObjectDataObjectQueryTraversalTest(String input, TraversalRules traversalRules,
			String expected) {
		SearchScope scope = new SearchScope();
		scope.setCollectionId(1L);
		scope.setDataObjectId(2L);
		String neo4jQuery = Neo4jEmitter.emitCollectionDataObjectDataObjectQuery(scope, traversalRules, input,
				userName);
		assertEquals(expected, neo4jQuery);
	}

	@Test
	public void invalidOperatorTest() {
		String searchBodyQuery = """
				{
				  "property": "name",
				  "value": "MyName",
				  "operator": "bla"
				}
				""";

		assertThrows(ShepardParserException.class, () -> Neo4jEmitter.emitCollectionQuery(searchBodyQuery, userName));
	}

	@Test
	public void emitStructuredDataContainerQueryTest() {
		String JSONQuery = "{\"property\": \"name\", \"value\": \"MyName\", \"operator\": \"eq\"}";
		String userName = "MarxKarl";
		String neo4jQuery = Neo4jEmitter.emitStructuredDataContainerQuery(JSONQuery, userName);
		String expected = "MATCH (sdc:StructuredDataContainer) WHERE (sdc.`name` = \"MyName\") AND (sdc.deleted = FALSE) AND (NOT exists((sdc)-[:has_permissions]->(:Permissions)) OR exists((sdc)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"MarxKarl\" })) OR exists((sdc)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((sdc)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((sdc)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"MarxKarl\"}))) WITH sdc MATCH path=(sdc)-[*0..1]->(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN sdc, nodes(path), relationships(path)";
		assertEquals(expected, neo4jQuery);
	}

	@Test
	public void emitTimeseriesContainerQueryTest() {
		String JSONQuery = "{\"property\": \"name\", \"value\": \"MyName\", \"operator\": \"eq\"}";
		String userName = "MarxKarl";
		String neo4jQuery = Neo4jEmitter.emitTimeseriesContainerQuery(JSONQuery, userName);
		String expected = "MATCH (tsc:TimeseriesContainer) WHERE (tsc.`name` = \"MyName\") AND (tsc.deleted = FALSE) AND (NOT exists((tsc)-[:has_permissions]->(:Permissions)) OR exists((tsc)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"MarxKarl\" })) OR exists((tsc)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((tsc)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((tsc)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"MarxKarl\"}))) WITH tsc MATCH path=(tsc)-[*0..1]->(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN tsc, nodes(path), relationships(path)";
		assertEquals(expected, neo4jQuery);
	}

	@Test
	public void emitFileContainerQueryTest() {
		String JSONQuery = "{\"property\": \"name\", \"value\": \"MyName\", \"operator\": \"eq\"}";
		String userName = "GatesWilliam";
		String neo4jQuery = Neo4jEmitter.emitFileContainerQuery(JSONQuery, userName);
		String expected = "MATCH (fc:FileContainer) WHERE (fc.`name` = \"MyName\") AND (fc.deleted = FALSE) AND (NOT exists((fc)-[:has_permissions]->(:Permissions)) OR exists((fc)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"GatesWilliam\" })) OR exists((fc)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((fc)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((fc)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"GatesWilliam\"}))) WITH fc MATCH path=(fc)-[*0..1]->(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN fc, nodes(path), relationships(path)";
		assertEquals(expected, neo4jQuery);
	}

}
