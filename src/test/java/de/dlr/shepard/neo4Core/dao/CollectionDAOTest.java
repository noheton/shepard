package de.dlr.shepard.neo4Core.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.session.Session;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.orderBy.DataObjectAttributes;
import de.dlr.shepard.util.QueryParamHelper;

public class CollectionDAOTest extends BaseTestCase {
	@Mock
	private Session session;

	@InjectMocks
	private CollectionDAO dao;

	@Test
	public void getEntityTypeTest() {
		var type = dao.getEntityType();
		assertEquals(Collection.class, type);
	}

	@Test
	public void findAll_WithoutName() {
		var col1 = new Collection(1L);
		col1.setName("Yes");
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", null);

		var query = "MATCH (c:Collection { deleted: false }) WITH c "
				+ "MATCH path=(:User)<-[]-(c)-[*0..1]-({deleted: False}) "
				+ "RETURN c, nodes(path), relationships(path)";
		when(session.query(Collection.class, query, paramsMap)).thenReturn(List.of(col1));

		var params = new QueryParamHelper();
		var actual = dao.findAllCollections(params);
		verify(session).query(Collection.class, query, paramsMap);
		assertEquals(List.of(col1), actual);
	}

	@Test
	public void findAll_WithoutNameOrderByNameDesc() {
		var col1 = new Collection(1L);
		col1.setName("Yes");
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", null);

		var query = "MATCH (c:Collection { deleted: false }) WITH c ORDER BY toLower(c.name) DESC "
				+ "MATCH path=(:User)<-[]-(c)-[*0..1]-({deleted: False}) "
				+ "RETURN c, nodes(path), relationships(path)";
		when(session.query(Collection.class, query, paramsMap)).thenReturn(List.of(col1));

		var params = new QueryParamHelper();
		var collectionAttribute = DataObjectAttributes.name;
		params = params.withOrderByAttribute(collectionAttribute, true);
		var actual = dao.findAllCollections(params);
		verify(session).query(Collection.class, query, paramsMap);
		assertEquals(List.of(col1), actual);
	}

	@Test
	public void findAll_WithName() {
		var col1 = new Collection(1L);
		col1.setName("Yes");
		var col2 = new Collection(2L);
		col2.setName("No");
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", "Yes");

		var query = "MATCH (c:Collection { name : $name, deleted: false }) WITH c "
				+ "MATCH path=(:User)<-[]-(c)-[*0..1]-({deleted: False}) "
				+ "RETURN c, nodes(path), relationships(path)";
		when(session.query(Collection.class, query, paramsMap)).thenReturn(List.of(col1, col2));

		var params = new QueryParamHelper().withName("Yes");
		var actual = dao.findAllCollections(params);
		verify(session).query(Collection.class, query, paramsMap);
		assertEquals(List.of(col1), actual);
	}

	@Test
	public void findAll_WithNameOrderByNameDesc() {
		var col1 = new Collection(1L);
		col1.setName("Yes");
		var col2 = new Collection(2L);
		col2.setName("No");
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", "Yes");

		var query = "MATCH (c:Collection { name : $name, deleted: false }) WITH c ORDER BY toLower(c.name) DESC "
				+ "MATCH path=(:User)<-[]-(c)-[*0..1]-({deleted: False}) "
				+ "RETURN c, nodes(path), relationships(path)";
		when(session.query(Collection.class, query, paramsMap)).thenReturn(List.of(col1, col2));

		var params = new QueryParamHelper().withName("Yes");
		var collectionAttribute = DataObjectAttributes.name;
		params = params.withOrderByAttribute(collectionAttribute, true);
		var actual = dao.findAllCollections(params);
		verify(session).query(Collection.class, query, paramsMap);
		assertEquals(List.of(col1), actual);
	}

	@Test
	public void findAll_WithPage() {
		var col1 = new Collection(1L);
		col1.setName("Yes");
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", null);
		paramsMap.put("offset", 300);
		paramsMap.put("size", 100);

		var query = "MATCH (c:Collection { deleted: false }) WITH c SKIP $offset LIMIT $size "
				+ "MATCH path=(:User)<-[]-(c)-[*0..1]-({deleted: False}) "
				+ "RETURN c, nodes(path), relationships(path)";
		when(session.query(Collection.class, query, paramsMap)).thenReturn(List.of(col1));

		var params = new QueryParamHelper().withPageAndSize(3, 100);
		var actual = dao.findAllCollections(params);
		verify(session).query(Collection.class, query, paramsMap);
		assertEquals(List.of(col1), actual);
	}

	@Test
	public void findAll_WithPageOrderByNameDesc() {
		var col1 = new Collection(1L);
		col1.setName("Yes");
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", null);
		paramsMap.put("offset", 300);
		paramsMap.put("size", 100);

		var query = "MATCH (c:Collection { deleted: false }) "
				+ "WITH c ORDER BY toLower(c.name) DESC SKIP $offset LIMIT $size "
				+ "MATCH path=(:User)<-[]-(c)-[*0..1]-({deleted: False}) "
				+ "RETURN c, nodes(path), relationships(path)";
		when(session.query(Collection.class, query, paramsMap)).thenReturn(List.of(col1));

		var params = new QueryParamHelper().withPageAndSize(3, 100);
		var collectionAttribute = DataObjectAttributes.name;
		params = params.withOrderByAttribute(collectionAttribute, true);
		var actual = dao.findAllCollections(params);
		verify(session).query(Collection.class, query, paramsMap);
		assertEquals(List.of(col1), actual);
	}

	@Test
	public void findAll_WithNameAndPage() {
		var col1 = new Collection(1L);
		col1.setName("Yes");
		var col2 = new Collection(2L);
		col2.setName("No");
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", "Yes");
		paramsMap.put("offset", 300);
		paramsMap.put("size", 100);

		var query = "MATCH (c:Collection { name : $name, deleted: false }) WITH c SKIP $offset LIMIT $size "
				+ "MATCH path=(:User)<-[]-(c)-[*0..1]-({deleted: False}) "
				+ "RETURN c, nodes(path), relationships(path)";
		when(session.query(Collection.class, query, paramsMap)).thenReturn(List.of(col1, col2));

		var params = new QueryParamHelper().withPageAndSize(3, 100).withName("Yes");
		var actual = dao.findAllCollections(params);
		verify(session).query(Collection.class, query, paramsMap);
		assertEquals(List.of(col1), actual);
	}

	@Test
	public void findAll_WithNameAndPageOrderByNameDesc() {
		var col1 = new Collection(1L);
		col1.setName("Yes");
		var col2 = new Collection(2L);
		col2.setName("No");
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", "Yes");
		paramsMap.put("offset", 300);
		paramsMap.put("size", 100);

		var query = "MATCH (c:Collection { name : $name, deleted: false }) "
				+ "WITH c ORDER BY toLower(c.name) DESC SKIP $offset LIMIT $size "
				+ "MATCH path=(:User)<-[]-(c)-[*0..1]-({deleted: False}) "
				+ "RETURN c, nodes(path), relationships(path)";
		when(session.query(Collection.class, query, paramsMap)).thenReturn(List.of(col1, col2));

		var params = new QueryParamHelper().withPageAndSize(3, 100).withName("Yes");
		var collectionAttribute = DataObjectAttributes.name;
		params = params.withOrderByAttribute(collectionAttribute, true);
		var actual = dao.findAllCollections(params);
		verify(session).query(Collection.class, query, paramsMap);
		assertEquals(List.of(col1), actual);
	}

	@Test
	public void deleteCollectionsTest() {
		CollectionDAO spy = spy(CollectionDAO.class);

		var collection = new Collection(1L);
		var user = new User("bob");
		var date = new Date();

		var updated = new Collection(1L);
		updated.setUpdatedBy(user);
		updated.setUpdatedAt(date);
		updated.setDeleted(true);

		doReturn(collection).when(spy).find(1L);
		doReturn(updated).when(spy).createOrUpdate(updated);
		doReturn(true).when(spy)
				.runQuery("MATCH (c:Collection) WHERE ID(c) = 1 OPTIONAL MATCH (c)-[:has_dataobject]->(d:DataObject) "
						+ "OPTIONAL MATCH (d)-[:has_reference]->(r:BasicReference) "
						+ "FOREACH (n in [c,d,r] | SET n.deleted = true)", Collections.emptyMap());

		var result = spy.deleteCollection(1L, user, date);
		verify(spy).createOrUpdate(updated);
		assertTrue(result);
	}

}
