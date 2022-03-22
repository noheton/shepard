package de.dlr.shepard.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.exceptions.ShepardParserException;
import de.dlr.shepard.util.TraversalRules;

public class Neo4jEmitterTest extends BaseTestCase {

	private static final String userName = "userName";

	@Test
	public void emitCollectionDataObjectReferenceQueryEqTest() throws ShepardParserException {
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
		String neo4jQuery = Neo4jEmitter.emitCollectionDataObjectReferenceQuery(scope, searchBodyQuery, userName);
		String expected = "MATCH (col:Collection)-[:has_dataobject]->(do:DataObject)-[:has_reference]->(br:BasicReference) WHERE (br.`name` = \"MyName\") AND (id(col) = 1 AND id(do) = 2) AND (br.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(do), id(br)";
		assertEquals(expected, neo4jQuery);
	}

	@Test
	public void emitCollectionDataObjectBasicReferenceQueryChildrenTest() throws ShepardParserException {
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
		String neo4jQuery = Neo4jEmitter.emitCollectionDataObjectBasicReferenceQuery(scope, TraversalRules.children,
				searchBodyQuery, userName);
		String expected = "MATCH (col:Collection)-[:has_dataobject]->(d:DataObject)-[:has_child*0..]->(do:DataObject)-[:has_reference]->(br:BasicReference)  WHERE (br.`name` = \"MyName\") AND (id(col) = 1 AND id(d) = 2) AND (br.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(do), id(br)";
		assertEquals(expected, neo4jQuery);
	}

	@Test
	public void emitCollectionBasicReferenceQueryTest() throws ShepardParserException {
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
	public void emitBasicReferenceQueryTest() throws ShepardParserException {
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
	public void emitDataObjectQueryTest() throws ShepardParserException {
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
	public void emitCollectionDataObjectDataObjectQueryTest() throws ShepardParserException {
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
	public void emitCollectionDataObjectDataObjectQueryChildrenTest() throws ShepardParserException {
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
	public void emitCollectionDataObjectQueryTest() throws ShepardParserException {
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

	@Test
	public void emitCollectionQueryTest() throws ShepardParserException {
		String searchBodyQuery = """
				{
				  "property": "name",
				  "value": "MyName",
				  "operator": "eq"
				}
				""";
		String neo4jQuery = Neo4jEmitter.emitCollectionQuery(searchBodyQuery, userName);
		String expected = "MATCH (col:Collection) WHERE (col.`name` = \"MyName\") AND (col.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(col)";
		assertEquals(expected, neo4jQuery);
	}

	@Test
	public void emitCollectionDataObjectBasicReferenceQueryParentsTest() throws ShepardParserException {
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
		String neo4jQuery = Neo4jEmitter.emitCollectionDataObjectBasicReferenceQuery(scope, TraversalRules.parents,
				searchBodyQuery, userName);
		String expected = "MATCH (col:Collection)-[:has_dataobject]->(d:DataObject)<-[:has_child*0..]-(do:DataObject)-[:has_reference]->(br:BasicReference)  WHERE (br.`name` = \"MyName\") AND (id(col) = 1 AND id(d) = 2) AND (br.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(do), id(br)";
		assertEquals(expected, neo4jQuery);
	}

	@Test
	public void emitCollectionDataObjectBasicReferenceQuerySuccessorsTest() throws ShepardParserException {
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
		String neo4jQuery = Neo4jEmitter.emitCollectionDataObjectBasicReferenceQuery(scope, TraversalRules.successors,
				searchBodyQuery, userName);
		String expected = "MATCH (col:Collection)-[:has_dataobject]->(d:DataObject)-[:has_successor*0..]->(do:DataObject)-[:has_reference]->(br:BasicReference)  WHERE (br.`name` = \"MyName\") AND (id(col) = 1 AND id(d) = 2) AND (br.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(do), id(br)";
		assertEquals(expected, neo4jQuery);
	}

	@Test
	public void emitCollectionDataObjectBasicReferenceQueryPredecessorsTest() throws ShepardParserException {
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
		String neo4jQuery = Neo4jEmitter.emitCollectionDataObjectBasicReferenceQuery(scope, TraversalRules.predecessors,
				searchBodyQuery, userName);
		String expected = "MATCH (col:Collection)-[:has_dataobject]->(d:DataObject)<-[:has_successor*0..]-(do:DataObject)-[:has_reference]->(br:BasicReference)  WHERE (br.`name` = \"MyName\") AND (id(col) = 1 AND id(d) = 2) AND (br.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(do), id(br)";
		assertEquals(expected, neo4jQuery);
	}

	@Test
	public void emitCollectionQueryOrGtContainsTest() throws ShepardParserException {
		String searchBodyQuery = """
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
				""";
		String neo4jQuery = Neo4jEmitter.emitCollectionQuery(searchBodyQuery, userName);
		String expected = "MATCH (col:Collection) WHERE ((col.`createdAt` > \"2021-05-12\") OR (col.`attributes.b` contains \"abc\")) AND (col.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(col)";
		assertEquals(expected, neo4jQuery);
	}

	@Test
	public void emitCollectionQueryAndGtContainsTest() throws ShepardParserException {
		String searchBodyQuery = """
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
				""";
		String neo4jQuery = Neo4jEmitter.emitCollectionQuery(searchBodyQuery, userName);
		String expected = "MATCH (col:Collection) WHERE ((col.`createdAt` > \"2021-05-12\") AND (col.`attributes.b` contains \"abc\")) AND (col.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(col)";
		assertEquals(expected, neo4jQuery);
	}

	@Test
	public void emitCollectionQueryNotTest() throws ShepardParserException {
		String searchBodyQuery = """
				{
				  "NOT": {
				    "property": "attributes.b",
				    "value": "abc",
				    "operator": "contains"
				  }
				}
				""";
		String neo4jQuery = Neo4jEmitter.emitCollectionQuery(searchBodyQuery, userName);
		String expected = "MATCH (col:Collection) WHERE (NOT((col.`attributes.b` contains \"abc\"))) AND (col.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(col)";
		assertEquals(expected, neo4jQuery);
	}

	@Test
	public void emitCollectionQueryAndReferencedCollectionIdReferencedDataObjectIdTest() throws ShepardParserException {
		String searchBodyQuery = """
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
				""";
		String neo4jQuery = Neo4jEmitter.emitCollectionQuery(searchBodyQuery, userName);
		String expected = "MATCH (col:Collection) WHERE ((EXISTS {MATCH (col)-[:points_to]->(refCol:Collection) WHERE id(refCol) <= \"2021-05-12\" }) AND (EXISTS {MATCH (col)-[:points_to]->(refDo:DataObject) WHERE id(refDo) contains \"abc\" })) AND (col.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(col)";
		assertEquals(expected, neo4jQuery);
	}

	@Test
	public void temitCollectionQueryAndCreatedByGtUpdatedByTest() throws ShepardParserException {
		String searchBodyQuery = """
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
				""";
		String neo4jQuery = Neo4jEmitter.emitCollectionQuery(searchBodyQuery, userName);
		String expected = "MATCH (col:Collection) WHERE ((EXISTS {MATCH (col) - [:created_by] -> (u) WHERE u.username > \"2021-05-12\" }) AND (EXISTS {MATCH (col) - [:updated_by] -> (u) WHERE u.username contains \"abc\" })) AND (col.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(col)";
		assertEquals(expected, neo4jQuery);
	}

	@Test
	public void emitCollectionQueryAndFileContainerIdStructuredDataContainerIdLtGeTest() throws ShepardParserException {
		String searchBodyQuery = """
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
				""";
		String neo4jQuery = Neo4jEmitter.emitCollectionQuery(searchBodyQuery, userName);
		String expected = "MATCH (col:Collection) WHERE ((EXISTS {MATCH (col)-[:is_in_container]->(refCon:FileContainer) WHERE id(refCon) < 23 }) AND (EXISTS {MATCH (col)-[:is_in_container]->(refCon:StructuredDataContainer) WHERE id(refCon) >= \"abc\" })) AND (col.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(col)";
		assertEquals(expected, neo4jQuery);
	}

	@Test
	public void emitCollectionQuerytimeseriesContainerIdIdTest() throws ShepardParserException {
		String searchBodyQuery = """
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
				""";
		String neo4jQuery = Neo4jEmitter.emitCollectionQuery(searchBodyQuery, userName);
		String expected = "MATCH (col:Collection) WHERE ((EXISTS {MATCH (col)-[:is_in_container]->(refCon:TimeseriesContainer) WHERE id(refCon) > \"2021-05-12\" }) AND (id(col)contains \"abc\")) AND (col.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(col)";
		assertEquals(expected, neo4jQuery);
	}

	@Test
	public void emitCollectionQueryNotInTest() throws ShepardParserException {
		String searchBodyQuery = """
				{
				  "NOT": {
				    "property": "attributes.b",
				    "value": [1,2,"e"],
				    "operator": "in"
				  }
				}
				""";
		String neo4jQuery = Neo4jEmitter.emitCollectionQuery(searchBodyQuery, userName);
		String expected = "MATCH (col:Collection) WHERE (NOT((col.`attributes.b`   IN [1, 2, \"e\"]))) AND (col.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(col)";
		assertEquals(expected, neo4jQuery);
	}

	@Test
	public void emitCollectionQueryInTest() throws ShepardParserException {
		String searchBodyQuery = """
				{
				     "property": "attributes.b",
				     "value": [],
				     "operator": "in"
				}
				""";
		String neo4jQuery = Neo4jEmitter.emitCollectionQuery(searchBodyQuery, userName);
		String expected = "MATCH (col:Collection) WHERE (col.`attributes.b`   IN []) AND (col.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(col)";
		assertEquals(expected, neo4jQuery);
	}

	@Test
	public void emitCollectionDataObjectDataObjectQueryParentsTest() throws ShepardParserException {
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
		String neo4jQuery = Neo4jEmitter.emitCollectionDataObjectDataObjectQuery(scope, TraversalRules.parents,
				searchBodyQuery, userName);
		String expected = "MATCH (col:Collection)-[:has_dataobject]->(d:DataObject)<-[:has_child*0..]-(do:DataObject)  WHERE (do.`name` = \"MyName\") AND (id(col) = 1 AND id(d) = 2) AND (do.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(do)";
		assertEquals(expected, neo4jQuery);
	}

	@Test
	public void emitCollectionDataObjectDataObjectQueryPredecessorsTest() throws ShepardParserException {
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
		String neo4jQuery = Neo4jEmitter.emitCollectionDataObjectDataObjectQuery(scope, TraversalRules.predecessors,
				searchBodyQuery, userName);
		String expected = "MATCH (col:Collection)-[:has_dataobject]->(d:DataObject)<-[:has_successor*0..]-(do:DataObject)  WHERE (do.`name` = \"MyName\") AND (id(col) = 1 AND id(d) = 2) AND (do.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(do)";
		assertEquals(expected, neo4jQuery);
	}

	@Test
	public void emitCollectionDataObjectDataObjectQuerySuccessorsTest() throws ShepardParserException {
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
		String neo4jQuery = Neo4jEmitter.emitCollectionDataObjectDataObjectQuery(scope, TraversalRules.successors,
				searchBodyQuery, userName);
		String expected = "MATCH (col:Collection)-[:has_dataobject]->(d:DataObject)-[:has_successor*0..]->(do:DataObject)  WHERE (do.`name` = \"MyName\") AND (id(col) = 1 AND id(d) = 2) AND (do.deleted = FALSE) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"userName\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"userName\"}))) RETURN id(do)";
		assertEquals(expected, neo4jQuery);
	}
}
