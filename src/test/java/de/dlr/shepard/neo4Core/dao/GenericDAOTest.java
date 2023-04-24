package de.dlr.shepard.neo4Core.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.cypher.query.Pagination;
import org.neo4j.ogm.model.QueryStatistics;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.util.PaginationHelper;
import de.dlr.shepard.util.TraversalRules;
import lombok.Data;

public class GenericDAOTest extends BaseTestCase {

	@Data
	private static class TestObject {
		private final int a;
	}

	private static class TestDAO extends GenericDAO<TestObject> {

		@Override
		public Class<TestObject> getEntityType() {
			return TestObject.class;
		}

	}

	@Mock
	private Session session;

	@InjectMocks
	private TestDAO dao = new TestDAO();

	@Captor
	ArgumentCaptor<Pagination> paginationCaptor;

	@Test
	public void findAllTest() {
		var a = new TestObject(1);
		var b = new TestObject(2);

		when(session.loadAll(TestObject.class, 1)).thenReturn(List.of(a, b));
		var actual = dao.findAll();
		assertEquals(List.of(a, b), actual);
	}

	@Test
	public void findAllPaginationTest() {
		var a = new TestObject(1);
		var b = new TestObject(2);
		var pagination = new PaginationHelper(3, 100);

		when(session.loadAll(eq(TestObject.class), paginationCaptor.capture(), eq(1))).thenReturn(List.of(a, b));
		var actual = dao.findAll(pagination);
		assertEquals(List.of(a, b), actual);
		assertEquals(" SKIP 300 LIMIT 100", paginationCaptor.getValue().toString());
	}

	@Test
	public void findMatchingTest() {
		var a = new TestObject(1);
		var filter = new Filter("a", ComparisonOperator.EQUALS, 1);

		when(session.loadAll(TestObject.class, filter, 1)).thenReturn(List.of(a));
		var actual = dao.findMatching(filter);
		assertEquals(List.of(a), actual);
	}

	@Test
	public void findTest() {
		var a = new TestObject(1);

		when(session.load(TestObject.class, 1L, 1)).thenReturn(a);
		var actual = dao.find(1L);
		assertEquals(a, actual);
	}

	@Test
	public void deleteTest_Successful() {
		var a = new TestObject(1);

		when(session.load(TestObject.class, 1L)).thenReturn(a);
		doNothing().when(session).delete(a);
		var actual = dao.delete(1L);
		assertTrue(actual);
	}

	@Test
	public void deleteTest_Error() {
		var a = new TestObject(1);

		when(session.load(TestObject.class, 1L)).thenReturn(null);
		doNothing().when(session).delete(a);
		var actual = dao.delete(1L);
		assertFalse(actual);
	}

	@Test
	public void createOrUpdateTest() {
		var a = new TestObject(1);

		doNothing().when(session).save(a, 1);
		var actual = dao.createOrUpdate(a);
		assertEquals(a, actual);
	}

	@Test
	public void findByQueryTest() {
		var a = new TestObject(1);
		var query = "MATCH (n {a: 1}) RETURN n";
		Map<String, Object> params = Map.of("a", "b", "c", "d");

		when(session.query(TestObject.class, query, params)).thenReturn(List.of(a));
		var actual = dao.findByQuery(query, params);
		assertEquals(List.of(a), actual);
	}

	@Test
	public void runQueryTest() {
		var query = "MATCH (n {a: 1}) RETURN n";
		Map<String, Object> params = Map.of("a", "b", "c", "d");
		Result result = mock(Result.class);
		QueryStatistics stat = mock(QueryStatistics.class);

		when(session.query(query, params)).thenReturn(result);
		when(result.queryStatistics()).thenReturn(stat);
		when(stat.containsUpdates()).thenReturn(true);

		var actual = dao.runQuery(query, params);
		assertTrue(actual);
	}

	@Test
	public void getQueryTest() {
		var query = "MATCH (n {a: 1}) RETURN n";
		Map<String, Object> params = Map.of("a", "b", "c", "d");
		Result result = mock(Result.class);

		when(session.query(query, params)).thenReturn(result);

		var actual = dao.getQuery(query, params);
		assertEquals(result, actual);
	}

	@Test
	public void getSearchForReachableReferencesQueryChildrenTest() {
		TraversalRules children = TraversalRules.children;
		long StartId = 1L;
		long CollectionId = 2L;
		String userName = "user";
		String childrenQuery = "MATCH path = (col:Collection)-[:has_dataobject]->(d:DataObject)-[:has_child*0..]->(e:DataObject)-[hr:has_reference]->(r:TestObject) WITH nodes(path) as ns, r as ret WHERE id(d) = 1 AND id(col) = 2 AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"user\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"user\"}))) AND NONE(node IN ns WHERE (node.deleted = TRUE)) MATCH path=(ret)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN ret, nodes(path), relationships(path)";
		var actual = dao.getSearchForReachableReferencesQuery(children, CollectionId, StartId, userName);
		assertEquals(childrenQuery, actual);
	}

	@Test
	public void getSearchForReachableReferencesQueryParentsTest() {
		TraversalRules parents = TraversalRules.parents;
		long StartId = 1L;
		long CollectionId = 2L;
		String userName = "user";
		String parentsQuery = "MATCH path = (col:Collection)-[:has_dataobject]->(d:DataObject)<-[:has_child*0..]-(e:DataObject)-[hr:has_reference]->(r:TestObject) WITH nodes(path) as ns, r as ret WHERE id(d) = 1 AND id(col) = 2 AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"user\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"user\"}))) AND NONE(node IN ns WHERE (node.deleted = TRUE)) MATCH path=(ret)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN ret, nodes(path), relationships(path)";
		var actual = dao.getSearchForReachableReferencesQuery(parents, CollectionId, StartId, userName);
		assertEquals(parentsQuery, actual);
	}

	@Test
	public void getSearchForReachableReferencesQueryPredecessorsTest() {
		TraversalRules predecessors = TraversalRules.predecessors;
		long StartId = 1L;
		long CollectionId = 2L;
		String userName = "user";
		String predecessorsQuery = "MATCH path = (col:Collection)-[:has_dataobject]->(d:DataObject)<-[:has_successor*0..]-(e:DataObject)-[hr:has_reference]->(r:TestObject) WITH nodes(path) as ns, r as ret WHERE id(d) = 1 AND id(col) = 2 AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"user\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"user\"}))) AND NONE(node IN ns WHERE (node.deleted = TRUE)) MATCH path=(ret)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN ret, nodes(path), relationships(path)";
		var actual = dao.getSearchForReachableReferencesQuery(predecessors, CollectionId, StartId, userName);
		assertEquals(predecessorsQuery, actual);
	}

	@Test
	public void getSearchForReachableReferencesQuerySuccessorsTest() {
		TraversalRules successors = TraversalRules.successors;
		long StartId = 1L;
		long CollectionId = 2L;
		String userName = "user";
		String successorsQuery = "MATCH path = (col:Collection)-[:has_dataobject]->(d:DataObject)-[:has_successor*0..]->(e:DataObject)-[hr:has_reference]->(r:TestObject) WITH nodes(path) as ns, r as ret WHERE id(d) = 1 AND id(col) = 2 AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"user\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"user\"}))) AND NONE(node IN ns WHERE (node.deleted = TRUE)) MATCH path=(ret)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN ret, nodes(path), relationships(path)";
		var actual = dao.getSearchForReachableReferencesQuery(successors, CollectionId, StartId, userName);
		assertEquals(successorsQuery, actual);
	}

	@Test
	public void getSearchForReachableReferencesQueryStartIdTest() {
		long StartId = 1L;
		long CollectionId = 2L;
		String userName = "user";
		String startIdQuery = "MATCH path = (col:Collection)-[:has_dataobject]->(d:DataObject)-[hr:has_reference]->(r:TestObject) WITH nodes(path) as ns, r as ret WHERE id(d) = 1 AND id(col) = 2 AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"user\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"user\"}))) AND NONE(node IN ns WHERE (node.deleted = TRUE)) MATCH path=(ret)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN ret, nodes(path), relationships(path)";
		var actual = dao.getSearchForReachableReferencesQuery(CollectionId, StartId, userName);
		assertEquals(startIdQuery, actual);
	}

}
