package de.dlr.shepard.neo4Core.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
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
import org.neo4j.ogm.session.Session;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.orderBy.CollectionAttributes;
import de.dlr.shepard.neo4Core.orderBy.OrderByAttribute;
import de.dlr.shepard.util.PaginationHelper;
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
	public void getReturnPartTest() {
		var actual = dao.getReturnPart("entity");
		assertEquals("MATCH path=(entity)-[*0..1]-() RETURN entity, nodes(path), relationships(path)", actual);
	}

	@Test
	public void getOrderByPartTestDesc() {
		String variable = "c";
		OrderByAttribute orderByAttribute = CollectionAttributes.createdAt;
		Boolean orderDesc = true;
		var actual = dao.getOrderByPart(variable, orderByAttribute, orderDesc);
		assertEquals(" ORDER BY c.createdAt DESC", actual);
	}

	@Test
	public void getOrderByPartTestNull() {
		String variable = "c";
		OrderByAttribute orderByAttribute = CollectionAttributes.createdAt;
		Boolean orderDesc = null;
		var actual = dao.getOrderByPart(variable, orderByAttribute, orderDesc);
		assertEquals(" ORDER BY c.createdAt", actual);
	}

	@Test
	public void getOrderByPartTestAsc() {
		String variable = "c";
		OrderByAttribute orderByAttribute = CollectionAttributes.createdAt;
		Boolean orderDesc = null;
		var actual = dao.getOrderByPart(variable, orderByAttribute, orderDesc);
		assertEquals(" ORDER BY c.createdAt", actual);
	}

	@Test
	public void getOrderByPartTestStringDesc() {
		String variable = "c";
		OrderByAttribute orderByAttribute = CollectionAttributes.createdBy;
		Boolean orderDesc = true;
		var actual = dao.getOrderByPart(variable, orderByAttribute, orderDesc);
		assertEquals(" ORDER BY toLower(c.createdBy) DESC", actual);
	}

	@Test
	public void getOrderByPartTestStringNull() {
		String variable = "c";
		OrderByAttribute orderByAttribute = CollectionAttributes.createdBy;
		Boolean orderDesc = null;
		var actual = dao.getOrderByPart(variable, orderByAttribute, orderDesc);
		assertEquals(" ORDER BY toLower(c.createdBy)", actual);
	}

	@Test
	public void getOrderByPartTestStringAsc() {
		String variable = "c";
		OrderByAttribute orderByAttribute = CollectionAttributes.createdBy;
		Boolean orderDesc = null;
		var actual = dao.getOrderByPart(variable, orderByAttribute, orderDesc);
		assertEquals(" ORDER BY toLower(c.createdBy)", actual);
	}

	@Test
	public void getParameterizedObjectPartTest_WithName() {
		String variable = "c";
		String type = "Collection";
		var actual = dao.getParameterizedObjectPart(variable, type, true);
		assertEquals("(c:Collection { name : $name, deleted: false })", actual);
	}

	@Test
	public void getParameterizedObjectPartTest_WithoutName() {
		String variable = "c";
		String type = "Collection";
		var actual = dao.getParameterizedObjectPart(variable, type, false);
		assertEquals("(c:Collection { deleted: false })", actual);
	}

	@Test
	public void getParameterizedPaginationPartTest_WithPagination() {
		var actual = dao.getParameterizedPaginationPart(true);
		assertEquals("SKIP $offset LIMIT $size", actual);
	}

	@Test
	public void getParameterizedPaginationPartTest_WithoutPagination() {
		var actual = dao.getParameterizedPaginationPart(false);
		assertEquals("", actual);
	}

}
