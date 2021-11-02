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
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.orderBy.DataObjectAttributes;
import de.dlr.shepard.util.QueryParamHelper;

public class DataObjectDAOTest extends BaseTestCase {

	@Mock
	private Session session;

	@InjectMocks
	private DataObjectDAO dao;

	@Test
	public void getEntityTypeTest() {
		var type = dao.getEntityType();
		assertEquals(DataObject.class, type);
	}

	@Test
	public void findAllTest() {
		var c1 = new Collection(100L);
		var c2 = new Collection(200L);

		var d1 = new DataObject(1L);
		var d2 = new DataObject(2L);
		var d3 = new DataObject(3L);
		d1.setCollection(c1);
		d3.setCollection(c2);
		d1.setChildren(List.of(d2, d3));
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", null);

		String query = "MATCH (c:Collection)-[hdo:has_dataobject]->"
				+ "(d:DataObject { deleted: false }) WHERE ID(c)=100 WITH d "
				+ "MATCH path=(d)-[*0..1]-(n) WHERE n.deleted = false or n.deleted IS NULL "
				+ "RETURN d, nodes(path), relationships(path)";
		when(session.query(DataObject.class, query, paramsMap)).thenReturn(List.of(d1, d2, d3));

		var params = new QueryParamHelper();
		var actual = dao.findByCollection(100L, params);
		verify(session).query(DataObject.class, query, paramsMap);
		assertEquals(List.of(d1), actual);
	}

	@Test
	public void findAllTestOrderByNameDesc() {
		var c1 = new Collection(100L);
		var c2 = new Collection(200L);

		var d1 = new DataObject(1L);
		var d2 = new DataObject(2L);
		var d3 = new DataObject(3L);
		d1.setCollection(c1);
		d3.setCollection(c2);
		d1.setChildren(List.of(d2, d3));
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", null);

		String query = "MATCH (c:Collection)-[hdo:has_dataobject]->(d:DataObject { deleted: false }) "
				+ "WHERE ID(c)=100 WITH d ORDER BY toLower(d.name) DESC "
				+ "MATCH path=(d)-[*0..1]-(n) WHERE n.deleted = false or n.deleted IS NULL "
				+ "RETURN d, nodes(path), relationships(path)";
		when(session.query(DataObject.class, query, paramsMap)).thenReturn(List.of(d1, d2, d3));

		var params = new QueryParamHelper();
		var dataObjectAttribute = DataObjectAttributes.name;
		params = params.withOrderByAttribute(dataObjectAttribute, true);
		var actual = dao.findByCollection(100L, params);
		verify(session).query(DataObject.class, query, paramsMap);
		assertEquals(List.of(d1), actual);
	}

	@Test
	public void findByPageTest() {
		var c1 = new Collection(100L);
		var c2 = new Collection(200L);

		var d1 = new DataObject(1L);
		var d2 = new DataObject(2L);
		var d3 = new DataObject(3L);
		d1.setCollection(c1);
		d3.setCollection(c2);
		d1.setChildren(List.of(d2, d3));
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", null);
		paramsMap.put("offset", 300);
		paramsMap.put("size", 100);

		String query = "MATCH (c:Collection)-[hdo:has_dataobject]->(d:DataObject { deleted: false }) "
				+ "WHERE ID(c)=100 WITH d SKIP $offset LIMIT $size "
				+ "MATCH path=(d)-[*0..1]-(n) WHERE n.deleted = false or n.deleted IS NULL "
				+ "RETURN d, nodes(path), relationships(path)";
		when(session.query(DataObject.class, query, paramsMap)).thenReturn(List.of(d1, d2, d3));

		var params = new QueryParamHelper().withPageAndSize(3, 100);
		var actual = dao.findByCollection(100L, params);
		verify(session).query(DataObject.class, query, paramsMap);
		assertEquals(List.of(d1), actual);
	}

	@Test
	public void findByPageTestOrderByNameDesc() {
		var c1 = new Collection(100L);
		var c2 = new Collection(200L);

		var d1 = new DataObject(1L);
		var d2 = new DataObject(2L);
		var d3 = new DataObject(3L);
		d1.setCollection(c1);
		d3.setCollection(c2);
		d1.setChildren(List.of(d2, d3));
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", null);
		paramsMap.put("offset", 300);
		paramsMap.put("size", 100);

		String query = "MATCH (c:Collection)-[hdo:has_dataobject]->(d:DataObject { deleted: false }) "
				+ "WHERE ID(c)=100 WITH d ORDER BY toLower(d.name) DESC SKIP $offset LIMIT $size "
				+ "MATCH path=(d)-[*0..1]-(n) WHERE n.deleted = false or n.deleted IS NULL "
				+ "RETURN d, nodes(path), relationships(path)";
		when(session.query(DataObject.class, query, paramsMap)).thenReturn(List.of(d1, d2, d3));

		var params = new QueryParamHelper().withPageAndSize(3, 100);
		var dataObjectAttribute = DataObjectAttributes.name;
		params = params.withOrderByAttribute(dataObjectAttribute, true);
		var actual = dao.findByCollection(100L, params);
		verify(session).query(DataObject.class, query, paramsMap);
		assertEquals(List.of(d1), actual);
	}

	@Test
	public void findByNameTest() {
		var c1 = new Collection(100L);
		var d1 = new DataObject(1L);
		var d2 = new DataObject(2L);
		d1.setCollection(c1);
		d1.setName("Yes");
		d2.setCollection(c1);
		d2.setName("No");
		Map<String, Object> paramsMap = Map.of("name", "Yes");

		String query = "MATCH (c:Collection)-[hdo:has_dataobject]->"
				+ "(d:DataObject { name : $name, deleted: false }) WHERE ID(c)=100 WITH d "
				+ "MATCH path=(d)-[*0..1]-(n) WHERE n.deleted = false or n.deleted IS NULL "
				+ "RETURN d, nodes(path), relationships(path)";
		when(session.query(DataObject.class, query, paramsMap)).thenReturn(List.of(d1, d2));

		var params = new QueryParamHelper().withName("Yes");
		var actual = dao.findByCollection(100L, params);
		verify(session).query(DataObject.class, query, paramsMap);
		assertEquals(List.of(d1), actual);
	}

	@Test
	public void findByNameTestOrderByNameDesc() {
		var c1 = new Collection(100L);
		var d1 = new DataObject(1L);
		var d2 = new DataObject(2L);
		d1.setCollection(c1);
		d1.setName("Yes");
		d2.setCollection(c1);
		d2.setName("No");
		Map<String, Object> paramsMap = Map.of("name", "Yes");

		String query = "MATCH (c:Collection)-[hdo:has_dataobject]->(d:DataObject { name : $name, deleted: false }) "
				+ "WHERE ID(c)=100 WITH d ORDER BY toLower(d.name) DESC "
				+ "MATCH path=(d)-[*0..1]-(n) WHERE n.deleted = false or n.deleted IS NULL "
				+ "RETURN d, nodes(path), relationships(path)";
		when(session.query(DataObject.class, query, paramsMap)).thenReturn(List.of(d1, d2));

		var params = new QueryParamHelper().withName("Yes");
		var dataObjectAttribute = DataObjectAttributes.name;
		params = params.withOrderByAttribute(dataObjectAttribute, true);
		var actual = dao.findByCollection(100L, params);
		verify(session).query(DataObject.class, query, paramsMap);
		assertEquals(List.of(d1), actual);
	}

	@Test
	public void findByNameAndPageTest() {
		var c1 = new Collection(100L);
		var d1 = new DataObject(1L);
		var d2 = new DataObject(2L);
		d1.setCollection(c1);
		d1.setName("Yes");
		d2.setCollection(c1);
		d2.setName("No");
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", "Yes");
		paramsMap.put("offset", 300);
		paramsMap.put("size", 100);

		String query = "MATCH (c:Collection)-[hdo:has_dataobject]->(d:DataObject { name : $name, deleted: false }) "
				+ "WHERE ID(c)=100 WITH d SKIP $offset LIMIT $size "
				+ "MATCH path=(d)-[*0..1]-(n) WHERE n.deleted = false or n.deleted IS NULL "
				+ "RETURN d, nodes(path), relationships(path)";
		when(session.query(DataObject.class, query, paramsMap)).thenReturn(List.of(d1, d2));

		var params = new QueryParamHelper().withPageAndSize(3, 100).withName("Yes");
		var actual = dao.findByCollection(100L, params);
		verify(session).query(DataObject.class, query, paramsMap);
		assertEquals(List.of(d1), actual);
	}

	@Test
	public void findByNameAndPageTestOrderByNameDesc() {
		var c1 = new Collection(100L);
		var d1 = new DataObject(1L);
		var d2 = new DataObject(2L);
		d1.setCollection(c1);
		d1.setName("Yes");
		d2.setCollection(c1);
		d2.setName("No");
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", "Yes");
		paramsMap.put("offset", 300);
		paramsMap.put("size", 100);

		String query = "MATCH (c:Collection)-[hdo:has_dataobject]->(d:DataObject { name : $name, deleted: false }) "
				+ "WHERE ID(c)=100 WITH d ORDER BY toLower(d.name) DESC SKIP $offset LIMIT $size "
				+ "MATCH path=(d)-[*0..1]-(n) WHERE n.deleted = false or n.deleted IS NULL "
				+ "RETURN d, nodes(path), relationships(path)";
		when(session.query(DataObject.class, query, paramsMap)).thenReturn(List.of(d1, d2));

		var params = new QueryParamHelper().withPageAndSize(3, 100).withName("Yes");
		var dataObjectAttribute = DataObjectAttributes.name;
		params = params.withOrderByAttribute(dataObjectAttribute, true);
		var actual = dao.findByCollection(100L, params);
		verify(session).query(DataObject.class, query, paramsMap);
		assertEquals(List.of(d1), actual);
	}

	@Test
	public void findByParentTest() {
		var c1 = new Collection(100L);
		var c2 = new Collection(200L);
		var d1 = new DataObject(1L);
		var d2 = new DataObject(2L);
		var d3 = new DataObject(3L);
		var d4 = new DataObject(4L);
		var d5 = new DataObject(5L);
		d1.setCollection(c1);
		d2.setCollection(c1);
		d2.setParent(d1);
		d3.setCollection(c2);
		d3.setParent(d1);
		d4.setParent(d1);
		d5.setCollection(c1);
		d5.setParent(d2);
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", null);

		String query = "MATCH (c:Collection)-[hdo:has_dataobject]->(d:DataObject { deleted: false })"
				+ "<-[:has_child]-(parent:DataObject {deleted: false}) WHERE ID(c)=100 AND ID(parent)=1 WITH d "
				+ "MATCH path=(d)-[*0..1]-(n) WHERE n.deleted = false or n.deleted IS NULL "
				+ "RETURN d, nodes(path), relationships(path)";
		when(session.query(DataObject.class, query, paramsMap)).thenReturn(List.of(d1, d2, d3, d4, d5));

		var params = new QueryParamHelper().withParentId(1L);
		var actual = dao.findByCollection(100L, params);
		verify(session).query(DataObject.class, query, paramsMap);
		verify(session).query(DataObject.class, query, paramsMap);
		assertEquals(List.of(d2), actual);
	}

	@Test
	public void findByParentDeletedTest() {
		var c1 = new Collection(100L);
		var d1 = new DataObject(1L);
		var d2 = new DataObject(2L);
		d1.setCollection(c1);
		d1.setDeleted(true);
		d2.setCollection(c1);
		d2.setParent(d1);
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", null);

		String query = "MATCH (c:Collection)-[hdo:has_dataobject]->(d:DataObject { deleted: false })"
				+ "<-[:has_child]-(parent:DataObject {deleted: false}) WHERE ID(c)=100 AND ID(parent)=1 WITH d "
				+ "MATCH path=(d)-[*0..1]-(n) WHERE n.deleted = false or n.deleted IS NULL "
				+ "RETURN d, nodes(path), relationships(path)";
		when(session.query(DataObject.class, query, paramsMap)).thenReturn(List.of(d1, d2));

		var params = new QueryParamHelper().withParentId(1L);
		var actual = dao.findByCollection(100L, params);
		verify(session).query(DataObject.class, query, paramsMap);
		verify(session).query(DataObject.class, query, paramsMap);
		assertEquals(Collections.EMPTY_LIST, actual);
	}

	@Test
	public void findByParentTestOrderByNameDesc() {
		var c1 = new Collection(100L);
		var c2 = new Collection(200L);
		var d1 = new DataObject(1L);
		var d2 = new DataObject(2L);
		var d3 = new DataObject(3L);
		var d4 = new DataObject(4L);
		var d5 = new DataObject(5L);
		d1.setCollection(c1);
		d2.setCollection(c1);
		d2.setParent(d1);
		d3.setCollection(c2);
		d3.setParent(d1);
		d4.setParent(d1);
		d5.setCollection(c1);
		d5.setParent(d2);
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", null);

		String query = "MATCH (c:Collection)-[hdo:has_dataobject]->(d:DataObject { deleted: false })"
				+ "<-[:has_child]-(parent:DataObject {deleted: false}) "
				+ "WHERE ID(c)=100 AND ID(parent)=1 WITH d ORDER BY toLower(d.name) DESC "
				+ "MATCH path=(d)-[*0..1]-(n) WHERE n.deleted = false or n.deleted IS NULL "
				+ "RETURN d, nodes(path), relationships(path)";
		when(session.query(DataObject.class, query, paramsMap)).thenReturn(List.of(d1, d2, d3, d4, d5));

		var params = new QueryParamHelper().withParentId(1L);
		var dataObjectAttribute = DataObjectAttributes.name;
		params = params.withOrderByAttribute(dataObjectAttribute, true);
		var actual = dao.findByCollection(100L, params);
		verify(session).query(DataObject.class, query, paramsMap);
		verify(session).query(DataObject.class, query, paramsMap);
		assertEquals(List.of(d2), actual);
	}

	@Test
	public void findWithoutParentTest() {
		var c1 = new Collection(100L);
		var c2 = new Collection(200L);
		var d1 = new DataObject(1L);
		var d2 = new DataObject(2L);
		var d3 = new DataObject(3L);
		var d4 = new DataObject(4L);
		var d5 = new DataObject(5L);
		d1.setCollection(c1);
		d2.setCollection(c1);
		d2.setParent(d1);
		d2.setDeleted(true);
		d3.setCollection(c2);
		d3.setParent(d1);
		d4.setParent(d1);
		d5.setCollection(c1);
		d5.setParent(d2);
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", null);

		String query = "MATCH (c:Collection)-[hdo:has_dataobject]->(d:DataObject { deleted: false }) "
				+ "WHERE ID(c)=100 AND NOT EXISTS((d)<-[:has_child]-(:DataObject {deleted: false})) WITH d "
				+ "MATCH path=(d)-[*0..1]-(n) WHERE n.deleted = false or n.deleted IS NULL "
				+ "RETURN d, nodes(path), relationships(path)";
		when(session.query(DataObject.class, query, paramsMap)).thenReturn(List.of(d1, d2, d3, d4, d5));

		var params = new QueryParamHelper().withParentId(-1L);
		var actual = dao.findByCollection(100L, params);
		verify(session).query(DataObject.class, query, paramsMap);
		assertEquals(List.of(d1, d5), actual);
	}

	@Test
	public void findWithoutParentTestOrderByNameDesc() {
		var c1 = new Collection(100L);
		var c2 = new Collection(200L);
		var d1 = new DataObject(1L);
		var d2 = new DataObject(2L);
		var d3 = new DataObject(3L);
		var d4 = new DataObject(4L);
		var d5 = new DataObject(5L);
		d1.setCollection(c1);
		d2.setCollection(c1);
		d2.setParent(d1);
		d3.setCollection(c2);
		d3.setParent(d1);
		d4.setParent(d1);
		d5.setCollection(c1);
		d5.setParent(d2);
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", null);

		String query = "MATCH (c:Collection)-[hdo:has_dataobject]->(d:DataObject { deleted: false }) "
				+ "WHERE ID(c)=100 AND NOT EXISTS((d)<-[:has_child]-(:DataObject {deleted: false})) "
				+ "WITH d ORDER BY toLower(d.name) DESC MATCH path=(d)-[*0..1]-(n) WHERE n.deleted = false or n.deleted IS NULL "
				+ "RETURN d, nodes(path), relationships(path)";
		when(session.query(DataObject.class, query, paramsMap)).thenReturn(List.of(d1, d2, d3, d4, d5));

		var params = new QueryParamHelper().withParentId(-1L);
		var dataObjectAttribute = DataObjectAttributes.name;
		params = params.withOrderByAttribute(dataObjectAttribute, true);
		var actual = dao.findByCollection(100L, params);
		verify(session).query(DataObject.class, query, paramsMap);
		assertEquals(List.of(d1), actual);
	}

	@Test
	public void findByParentAndNameTest() {
		var c1 = new Collection(100L);
		var d1 = new DataObject(1L);
		var d2 = new DataObject(2L);
		var d3 = new DataObject(3L);
		d1.setCollection(c1);
		d2.setCollection(c1);
		d2.setParent(d1);
		d2.setName("Yes");
		d3.setCollection(c1);
		d3.setParent(d1);
		d3.setName("No");
		Map<String, Object> paramsMap = Map.of("name", "Yes");

		String query = "MATCH (c:Collection)-[hdo:has_dataobject]->(d:DataObject { name : $name, deleted: false })"
				+ "<-[:has_child]-(parent:DataObject {deleted: false}) WHERE ID(c)=100 AND ID(parent)=1 WITH d "
				+ "MATCH path=(d)-[*0..1]-(n) WHERE n.deleted = false or n.deleted IS NULL "
				+ "RETURN d, nodes(path), relationships(path)";
		when(session.query(DataObject.class, query, paramsMap)).thenReturn(List.of(d1, d2, d3));

		var params = new QueryParamHelper().withParentId(1L).withName("Yes");
		var actual = dao.findByCollection(100L, params);
		verify(session).query(DataObject.class, query, paramsMap);
		assertEquals(List.of(d2), actual);
	}

	@Test
	public void findByParentAndNameTestOrderByNameDesc() {
		var c1 = new Collection(100L);
		var d1 = new DataObject(1L);
		var d2 = new DataObject(2L);
		var d3 = new DataObject(3L);
		d1.setCollection(c1);
		d2.setCollection(c1);
		d2.setParent(d1);
		d2.setName("Yes");
		d3.setCollection(c1);
		d3.setParent(d1);
		d3.setName("No");
		Map<String, Object> paramsMap = Map.of("name", "Yes");

		String query = "MATCH (c:Collection)-[hdo:has_dataobject]->(d:DataObject { name : $name, deleted: false })"
				+ "<-[:has_child]-(parent:DataObject {deleted: false}) "
				+ "WHERE ID(c)=100 AND ID(parent)=1 WITH d ORDER BY toLower(d.name) DESC "
				+ "MATCH path=(d)-[*0..1]-(n) WHERE n.deleted = false or n.deleted IS NULL "
				+ "RETURN d, nodes(path), relationships(path)";
		when(session.query(DataObject.class, query, paramsMap)).thenReturn(List.of(d1, d2, d3));

		var params = new QueryParamHelper().withParentId(1L).withName("Yes");
		var dataObjectAttribute = DataObjectAttributes.name;
		params = params.withOrderByAttribute(dataObjectAttribute, true);
		var actual = dao.findByCollection(100L, params);
		verify(session).query(DataObject.class, query, paramsMap);
		assertEquals(List.of(d2), actual);
	}

	@Test
	public void findByParentAndPageTest() {
		var c1 = new Collection(100L);
		var d1 = new DataObject(1L);
		var d2 = new DataObject(2L);
		d1.setCollection(c1);
		d2.setCollection(c1);
		d2.setParent(d1);
		d2.setName("Yes");
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", null);
		paramsMap.put("offset", 300);
		paramsMap.put("size", 100);

		String query = "MATCH (c:Collection)-[hdo:has_dataobject]->(d:DataObject { deleted: false })"
				+ "<-[:has_child]-(parent:DataObject {deleted: false}) "
				+ "WHERE ID(c)=100 AND ID(parent)=1 WITH d SKIP $offset LIMIT $size "
				+ "MATCH path=(d)-[*0..1]-(n) WHERE n.deleted = false or n.deleted IS NULL "
				+ "RETURN d, nodes(path), relationships(path)";
		when(session.query(DataObject.class, query, paramsMap)).thenReturn(List.of(d1, d2));

		var params = new QueryParamHelper().withParentId(1L).withPageAndSize(3, 100);
		var actual = dao.findByCollection(100L, params);
		verify(session).query(DataObject.class, query, paramsMap);
		assertEquals(List.of(d2), actual);
	}

	@Test
	public void findByParentAndPageTestOrderByNameDesc() {
		var c1 = new Collection(100L);
		var d1 = new DataObject(1L);
		var d2 = new DataObject(2L);
		d1.setCollection(c1);
		d2.setCollection(c1);
		d2.setParent(d1);
		d2.setName("Yes");
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", null);
		paramsMap.put("offset", 300);
		paramsMap.put("size", 100);

		String query = "MATCH (c:Collection)-[hdo:has_dataobject]->(d:DataObject { deleted: false })"
				+ "<-[:has_child]-(parent:DataObject {deleted: false}) WHERE ID(c)=100 AND ID(parent)=1 "
				+ "WITH d ORDER BY toLower(d.name) DESC SKIP $offset LIMIT $size "
				+ "MATCH path=(d)-[*0..1]-(n) WHERE n.deleted = false or n.deleted IS NULL "
				+ "RETURN d, nodes(path), relationships(path)";
		when(session.query(DataObject.class, query, paramsMap)).thenReturn(List.of(d1, d2));

		var params = new QueryParamHelper().withParentId(1L).withPageAndSize(3, 100);
		var dataObjectAttribute = DataObjectAttributes.name;
		params = params.withOrderByAttribute(dataObjectAttribute, true);
		var actual = dao.findByCollection(100L, params);
		verify(session).query(DataObject.class, query, paramsMap);
		assertEquals(List.of(d2), actual);
	}

	@Test
	public void findByParentAndPageAndNameTest() {
		var c1 = new Collection(100L);
		var d1 = new DataObject(1L);
		var d2 = new DataObject(2L);
		var d3 = new DataObject(3L);
		var d4 = new DataObject(4L);
		d1.setCollection(c1);
		d2.setCollection(c1);
		d2.setParent(d1);
		d2.setName("Yes");
		d3.setCollection(c1);
		d3.setParent(d1);
		d3.setName("No");
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", "Yes");
		paramsMap.put("offset", 300);
		paramsMap.put("size", 100);

		String query = "MATCH (c:Collection)-[hdo:has_dataobject]->(d:DataObject { name : $name, deleted: false })"
				+ "<-[:has_child]-(parent:DataObject {deleted: false}) "
				+ "WHERE ID(c)=100 AND ID(parent)=1 WITH d SKIP $offset LIMIT $size "
				+ "MATCH path=(d)-[*0..1]-(n) WHERE n.deleted = false or n.deleted IS NULL "
				+ "RETURN d, nodes(path), relationships(path)";
		when(session.query(DataObject.class, query, paramsMap)).thenReturn(List.of(d1, d2, d3, d4));

		var params = new QueryParamHelper().withParentId(1L).withPageAndSize(3, 100).withName("Yes");
		var actual = dao.findByCollection(100L, params);
		verify(session).query(DataObject.class, query, paramsMap);
		assertEquals(List.of(d2), actual);
	}

	@Test
	public void findByParentAndPageAndNameTestOrderByNameDesc() {
		var c1 = new Collection(100L);
		var d1 = new DataObject(1L);
		var d2 = new DataObject(2L);
		var d3 = new DataObject(3L);
		var d4 = new DataObject(4L);
		d1.setCollection(c1);
		d2.setCollection(c1);
		d2.setParent(d1);
		d2.setName("Yes");
		d3.setCollection(c1);
		d3.setParent(d1);
		d3.setName("No");
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", "Yes");
		paramsMap.put("offset", 300);
		paramsMap.put("size", 100);

		String query = "MATCH (c:Collection)-[hdo:has_dataobject]->(d:DataObject { name : $name, deleted: false })"
				+ "<-[:has_child]-(parent:DataObject {deleted: false}) WHERE ID(c)=100 AND ID(parent)=1 "
				+ "WITH d ORDER BY toLower(d.name) DESC SKIP $offset LIMIT $size "
				+ "MATCH path=(d)-[*0..1]-(n) WHERE n.deleted = false or n.deleted IS NULL "
				+ "RETURN d, nodes(path), relationships(path)";
		when(session.query(DataObject.class, query, paramsMap)).thenReturn(List.of(d1, d2, d3, d4));

		var params = new QueryParamHelper().withParentId(1L).withPageAndSize(3, 100).withName("Yes");
		var dataObjectAttribute = DataObjectAttributes.name;
		params = params.withOrderByAttribute(dataObjectAttribute, true);
		var actual = dao.findByCollection(100L, params);
		verify(session).query(DataObject.class, query, paramsMap);
		assertEquals(List.of(d2), actual);
	}

	@Test
	public void findWithoutParentByPageAndNameTest() {
		var c1 = new Collection(100L);
		var c2 = new Collection(200L);
		var d1 = new DataObject(1L);
		var d2 = new DataObject(2L);
		var d3 = new DataObject(3L);
		var d4 = new DataObject(4L);
		var d5 = new DataObject(5L);
		d1.setCollection(c1);
		d1.setName("Yes");
		d2.setCollection(c1);
		d2.setName("No");
		d3.setCollection(c2);
		d3.setName("Yes");
		d4.setCollection(c1);
		d4.setParent(d1);
		d4.setName("Yes");
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", "Yes");
		paramsMap.put("offset", 300);
		paramsMap.put("size", 100);

		String query = "MATCH (c:Collection)-[hdo:has_dataobject]->(d:DataObject { name : $name, deleted: false }) "
				+ "WHERE ID(c)=100 AND NOT EXISTS((d)<-[:has_child]-(:DataObject {deleted: false})) "
				+ "WITH d SKIP $offset LIMIT $size "
				+ "MATCH path=(d)-[*0..1]-(n) WHERE n.deleted = false or n.deleted IS NULL "
				+ "RETURN d, nodes(path), relationships(path)";
		when(session.query(DataObject.class, query, paramsMap)).thenReturn(List.of(d1, d2, d3, d4, d5));

		var params = new QueryParamHelper().withParentId(-1L).withPageAndSize(3, 100).withName("Yes");
		var actual = dao.findByCollection(100L, params);
		verify(session).query(DataObject.class, query, paramsMap);
		assertEquals(List.of(d1), actual);
	}

	@Test
	public void findWithoutParentByPageAndNameTestOrderByNameDesc() {
		var c1 = new Collection(100L);
		var c2 = new Collection(200L);
		var d1 = new DataObject(1L);
		var d2 = new DataObject(2L);
		var d3 = new DataObject(3L);
		var d4 = new DataObject(4L);
		var d5 = new DataObject(5L);
		d1.setCollection(c1);
		d1.setName("Yes");
		d2.setCollection(c1);
		d2.setName("No");
		d3.setCollection(c2);
		d3.setName("Yes");
		d4.setCollection(c1);
		d4.setParent(d1);
		d4.setName("Yes");
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", "Yes");
		paramsMap.put("offset", 300);
		paramsMap.put("size", 100);

		String query = "MATCH (c:Collection)-[hdo:has_dataobject]->(d:DataObject { name : $name, deleted: false }) "
				+ "WHERE ID(c)=100 AND NOT EXISTS((d)<-[:has_child]-(:DataObject {deleted: false})) "
				+ "WITH d ORDER BY toLower(d.name) DESC SKIP $offset LIMIT $size "
				+ "MATCH path=(d)-[*0..1]-(n) WHERE n.deleted = false or n.deleted IS NULL "
				+ "RETURN d, nodes(path), relationships(path)";
		when(session.query(DataObject.class, query, paramsMap)).thenReturn(List.of(d1, d2, d3, d4, d5));

		var params = new QueryParamHelper().withParentId(-1L).withPageAndSize(3, 100).withName("Yes");
		var dataObjectAttribute = DataObjectAttributes.name;
		params = params.withOrderByAttribute(dataObjectAttribute, true);
		var actual = dao.findByCollection(100L, params);
		verify(session).query(DataObject.class, query, paramsMap);
		assertEquals(List.of(d1), actual);
	}

	@Test
	public void findByPredecessor() {
		var c = new Collection(100L);
		var pre = new DataObject(201L);
		var d = new DataObject(200L);
		pre.setCollection(c);
		pre.addSuccessor(d);
		d.addPredecessor(pre);
		d.setCollection(c);

		var query = "MATCH (c:Collection)-[hdo:has_dataobject]->(d:DataObject { deleted: false })"
				+ "<-[:has_successor]-(predecessor:DataObject {deleted: false}) "
				+ "WHERE ID(c)=100 AND ID(predecessor)=201 WITH d "
				+ "MATCH path=(d)-[*0..1]-(n) WHERE n.deleted = false or n.deleted IS NULL "
				+ "RETURN d, nodes(path), relationships(path)";
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", null);

		var params = new QueryParamHelper().withPredecessorId(201L);
		when(session.query(DataObject.class, query, paramsMap)).thenReturn(List.of(d, pre));

		var actual = dao.findByCollection(100L, params);
		verify(session).query(DataObject.class, query, paramsMap);
		assertEquals(List.of(d), actual);
	}

	@Test
	public void findWithoutPredecessor() {
		var c = new Collection(100L);
		var d = new DataObject(200L);
		var d2 = new DataObject(201L);
		d.setCollection(c);
		d.addSuccessor(d2);
		d2.addPredecessor(d2);
		d2.setCollection(c);

		var query = "MATCH (c:Collection)-[hdo:has_dataobject]->(d:DataObject { deleted: false }) "
				+ "WHERE ID(c)=100 AND NOT EXISTS((d)<-[:has_successor]-(:DataObject {deleted: false})) WITH d "
				+ "MATCH path=(d)-[*0..1]-(n) WHERE n.deleted = false or n.deleted IS NULL "
				+ "RETURN d, nodes(path), relationships(path)";
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", null);

		var params = new QueryParamHelper().withPredecessorId(-1L);
		when(session.query(DataObject.class, query, paramsMap)).thenReturn(List.of(d, d2));

		var actual = dao.findByCollection(100L, params);
		verify(session).query(DataObject.class, query, paramsMap);
		assertEquals(List.of(d), actual);
	}

	@Test
	public void findBySuccessor() {
		var c = new Collection(100L);
		var suc = new DataObject(201L);
		var d = new DataObject(200L);
		suc.setCollection(c);
		suc.addPredecessor(d);
		d.addSuccessor(suc);
		d.setCollection(c);

		var query = "MATCH (c:Collection)-[hdo:has_dataobject]->(d:DataObject { deleted: false })"
				+ "-[:has_successor]->(successor:DataObject {deleted: false}) "
				+ "WHERE ID(c)=100 AND ID(successor)=201 WITH d "
				+ "MATCH path=(d)-[*0..1]-(n) WHERE n.deleted = false or n.deleted IS NULL "
				+ "RETURN d, nodes(path), relationships(path)";
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", null);

		var params = new QueryParamHelper().withSuccessorId(201L);
		when(session.query(DataObject.class, query, paramsMap)).thenReturn(List.of(d, suc));

		var actual = dao.findByCollection(100L, params);
		verify(session).query(DataObject.class, query, paramsMap);
		assertEquals(List.of(d), actual);
	}

	@Test
	public void findWithoutSuccessor() {
		var c = new Collection(100L);
		var d = new DataObject(200L);
		var d2 = new DataObject(201L);
		d2.setCollection(c);
		d2.addSuccessor(d);
		d.addPredecessor(d2);
		d.setCollection(c);

		var query = "MATCH (c:Collection)-[hdo:has_dataobject]->(d:DataObject { deleted: false }) "
				+ "WHERE ID(c)=100 AND NOT EXISTS((d)-[:has_successor]->(:DataObject {deleted: false})) WITH d "
				+ "MATCH path=(d)-[*0..1]-(n) WHERE n.deleted = false or n.deleted IS NULL "
				+ "RETURN d, nodes(path), relationships(path)";
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", null);

		var params = new QueryParamHelper().withSuccessorId(-1L);
		when(session.query(DataObject.class, query, paramsMap)).thenReturn(List.of(d, d2));

		var actual = dao.findByCollection(100L, params);
		verify(session).query(DataObject.class, query, paramsMap);
		assertEquals(List.of(d), actual);
	}

	@Test
	public void deleteDataObjectTest() {
		DataObjectDAO spy = spy(DataObjectDAO.class);

		var dataObject = new DataObject(1L);
		var user = new User("bob");
		var date = new Date();

		var updated = new DataObject(1L);
		updated.setUpdatedBy(user);
		updated.setUpdatedAt(date);
		updated.setDeleted(true);

		doReturn(dataObject).when(spy).find(1L);
		doReturn(updated).when(spy).createOrUpdate(updated);
		doReturn(true).when(spy).runQuery(
				"MATCH (d:DataObject) WHERE ID(d) = 1 OPTIONAL MATCH (d)-[:has_reference]->(r:BasicReference) "
						+ "FOREACH (n in [d,r] | SET n.deleted = true)",
				Collections.emptyMap());

		var result = spy.deleteDataObject(1L, user, date);
		verify(spy).createOrUpdate(updated);
		assertTrue(result);
	}

}
