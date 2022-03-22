package de.dlr.shepard.neo4Core.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.entities.BasicReference;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.orderBy.BasicReferenceAttributes;
import de.dlr.shepard.util.QueryParamHelper;

public class BasicReferenceDAOTest extends BaseTestCase {
	@Mock
	private Session session;

	@InjectMocks
	private BasicReferenceDAO dao;

	@Test
	public void getEntityTypeTest() {
		var type = dao.getEntityType();
		assertEquals(BasicReference.class, type);
	}

	@Test
	public void findByDataObjectTest_WithoutName() {
		var obj = new DataObject(1L);
		var obj2 = new DataObject(100L);
		var ref = new BasicReference(2L);
		var ref3 = new BasicReference(3L);
		var ref4 = new BasicReference(4L);
		ref.setDataObject(obj);
		ref.setName("Yes");
		ref4.setDataObject(obj2);
		ref4.setName("Yes");
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", null);

		var query = """
				MATCH (d:DataObject)-[hr:has_reference]->(r:BasicReference { deleted: FALSE }) \
				WHERE ID(d)=1 WITH r MATCH path=(r)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL \
				RETURN r, nodes(path), relationships(path)""";
		when(session.query(BasicReference.class, query, paramsMap)).thenReturn(List.of(ref, ref3, ref4));

		var params = new QueryParamHelper();
		var actual = dao.findByDataObject(1L, params);
		verify(session).query(BasicReference.class, query, paramsMap);
		assertEquals(List.of(ref), actual);
	}

	@Test
	public void findByDataObjectTest_WithName() {
		var obj = new DataObject(1L);
		var obj2 = new DataObject(100L);
		var ref = new BasicReference(2L);
		var ref3 = new BasicReference(3L);
		var ref4 = new BasicReference(4L);
		var ref5 = new BasicReference(5L);
		ref.setDataObject(obj);
		ref.setName("Yes");
		ref4.setDataObject(obj2);
		ref4.setName("Yes");
		ref5.setDataObject(obj);
		ref5.setName("No");
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", "Yes");

		var query = """
				MATCH (d:DataObject)-[hr:has_reference]->(r:BasicReference { name : $name, deleted: FALSE }) \
				WHERE ID(d)=1 WITH r MATCH path=(r)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL \
				RETURN r, nodes(path), relationships(path)""";
		when(session.query(BasicReference.class, query, paramsMap)).thenReturn(List.of(ref, ref3, ref4, ref5));

		var params = new QueryParamHelper().withName("Yes");
		var actual = dao.findByDataObject(1L, params);
		verify(session).query(BasicReference.class, query, paramsMap);
		assertEquals(List.of(ref), actual);
	}

	@Test
	public void findByDataObjectTest_WithNameOrderByNameDesc() {
		var obj = new DataObject(1L);
		var obj2 = new DataObject(100L);
		var ref = new BasicReference(2L);
		var ref3 = new BasicReference(3L);
		var ref4 = new BasicReference(4L);
		var ref5 = new BasicReference(5L);
		ref.setDataObject(obj);
		ref.setName("Yes");
		ref4.setDataObject(obj2);
		ref4.setName("Yes");
		ref5.setDataObject(obj);
		ref5.setName("No");
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", "Yes");

		var query = """
				MATCH (d:DataObject)-[hr:has_reference]->(r:BasicReference { name : $name, deleted: FALSE }) \
				WHERE ID(d)=1 WITH r ORDER BY toLower(r.name) DESC \
				MATCH path=(r)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL \
				RETURN r, nodes(path), relationships(path)""";
		when(session.query(BasicReference.class, query, paramsMap)).thenReturn(List.of(ref, ref3, ref4, ref5));

		var params = new QueryParamHelper().withName("Yes");
		var basicReferenceAttribute = BasicReferenceAttributes.name;
		params = params.withOrderByAttribute(basicReferenceAttribute, true);
		var actual = dao.findByDataObject(1L, params);
		verify(session).query(BasicReference.class, query, paramsMap);
		assertEquals(List.of(ref), actual);
	}

	@Test
	public void findByDataObjectTest_WithPage() {
		var obj = new DataObject(1L);
		var obj2 = new DataObject(100L);
		var ref = new BasicReference(2L);
		var ref3 = new BasicReference(3L);
		var ref4 = new BasicReference(4L);
		ref.setDataObject(obj);
		ref.setName("Yes");
		ref4.setDataObject(obj2);
		ref4.setName("Yes");
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("offset", 300);
		paramsMap.put("size", 100);
		paramsMap.put("name", null);

		var query = """
				MATCH (d:DataObject)-[hr:has_reference]->(r:BasicReference { deleted: FALSE }) \
				WHERE ID(d)=1 WITH r SKIP $offset LIMIT $size \
				MATCH path=(r)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL \
				RETURN r, nodes(path), relationships(path)""";
		when(session.query(BasicReference.class, query, paramsMap)).thenReturn(List.of(ref, ref3, ref4));

		var params = new QueryParamHelper().withPageAndSize(3, 100);
		var actual = dao.findByDataObject(1L, params);
		verify(session).query(BasicReference.class, query, paramsMap);
		assertEquals(List.of(ref), actual);
	}

	@Test
	public void findByDataObjectTest_WithPageOrderByNameDesc() {
		var obj = new DataObject(1L);
		var obj2 = new DataObject(100L);
		var ref = new BasicReference(2L);
		var ref3 = new BasicReference(3L);
		var ref4 = new BasicReference(4L);
		ref.setDataObject(obj);
		ref.setName("Yes");
		ref4.setDataObject(obj2);
		ref4.setName("Yes");
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("offset", 300);
		paramsMap.put("size", 100);
		paramsMap.put("name", null);

		var query = """
				MATCH (d:DataObject)-[hr:has_reference]->(r:BasicReference { deleted: FALSE }) \
				WHERE ID(d)=1 WITH r ORDER BY toLower(r.name) DESC SKIP $offset LIMIT $size \
				MATCH path=(r)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL \
				RETURN r, nodes(path), relationships(path)""";
		when(session.query(BasicReference.class, query, paramsMap)).thenReturn(List.of(ref, ref3, ref4));

		var params = new QueryParamHelper().withPageAndSize(3, 100);
		var basicReferenceAttribute = BasicReferenceAttributes.name;
		params = params.withOrderByAttribute(basicReferenceAttribute, true);
		var actual = dao.findByDataObject(1L, params);
		verify(session).query(BasicReference.class, query, paramsMap);
		assertEquals(List.of(ref), actual);
	}

	@Test
	public void findByDataObjectTest_WithNameAndPage() {
		var obj = new DataObject(1L);
		var obj2 = new DataObject(100L);
		var ref = new BasicReference(2L);
		var ref3 = new BasicReference(3L);
		var ref4 = new BasicReference(4L);
		var ref5 = new BasicReference(5L);
		ref.setDataObject(obj);
		ref.setName("Yes");
		ref4.setDataObject(obj2);
		ref4.setName("Yes");
		ref5.setDataObject(obj);
		ref5.setName("No");
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("offset", 300);
		paramsMap.put("size", 100);
		paramsMap.put("name", "Yes");

		var query = """
				MATCH (d:DataObject)-[hr:has_reference]->(r:BasicReference { name : $name, deleted: FALSE }) \
				WHERE ID(d)=1 WITH r SKIP $offset LIMIT $size \
				MATCH path=(r)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL \
				RETURN r, nodes(path), relationships(path)""";
		when(session.query(BasicReference.class, query, paramsMap)).thenReturn(List.of(ref, ref3, ref4, ref5));

		var params = new QueryParamHelper().withPageAndSize(3, 100).withName("Yes");
		var actual = dao.findByDataObject(1L, params);
		verify(session).query(BasicReference.class, query, paramsMap);
		assertEquals(List.of(ref), actual);
	}

	@Test
	public void findByDataObjectTest_WithNameAndPageOrderByNameDesc() {
		var obj = new DataObject(1L);
		var obj2 = new DataObject(100L);
		var ref = new BasicReference(2L);
		var ref3 = new BasicReference(3L);
		var ref4 = new BasicReference(4L);
		var ref5 = new BasicReference(5L);
		ref.setDataObject(obj);
		ref.setName("Yes");
		ref4.setDataObject(obj2);
		ref4.setName("Yes");
		ref5.setDataObject(obj);
		ref5.setName("No");
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("offset", 300);
		paramsMap.put("size", 100);
		paramsMap.put("name", "Yes");

		var query = """
				MATCH (d:DataObject)-[hr:has_reference]->(r:BasicReference { name : $name, deleted: FALSE }) \
				WHERE ID(d)=1 WITH r ORDER BY toLower(r.name) DESC SKIP $offset LIMIT $size \
				MATCH path=(r)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL \
				RETURN r, nodes(path), relationships(path)""";
		when(session.query(BasicReference.class, query, paramsMap)).thenReturn(List.of(ref, ref3, ref4, ref5));

		var params = new QueryParamHelper().withPageAndSize(3, 100).withName("Yes");
		var basicReferenceAttribute = BasicReferenceAttributes.name;
		params = params.withOrderByAttribute(basicReferenceAttribute, true);
		var actual = dao.findByDataObject(1L, params);
		verify(session).query(BasicReference.class, query, paramsMap);
		assertEquals(List.of(ref), actual);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void getDataObjectIdTest() {
		var query = "MATCH (d:DataObject)-[has_reference]->(r) WHERE id(r) = 5 RETURN id(d)";

		var iterator = mock(Iterator.class);
		when(iterator.next()).thenReturn(Map.of("id(d)", 123L));
		var result = mock(Result.class);
		when(result.iterator()).thenReturn(iterator);
		when(session.query(query, Collections.emptyMap())).thenReturn(result);

		var actual = dao.getDataObjectId(5L);
		assertEquals(123L, actual);
	}

	@Test
	public void getDataObjectsByQueryTest() {
		BasicReferenceDAO spy = spy(BasicReferenceDAO.class);

		var basicReference = new BasicReference(1L);

		doReturn(List.of(basicReference)).when(spy).findByQuery("query", Collections.emptyMap());

		var result = spy.getBasicReferencesByQuery("query");
		assertEquals(List.of(basicReference), result);

	}
}
