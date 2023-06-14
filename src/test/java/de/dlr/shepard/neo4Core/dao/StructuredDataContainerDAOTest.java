package de.dlr.shepard.neo4Core.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.session.Session;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.entities.StructuredDataContainer;
import de.dlr.shepard.neo4Core.orderBy.ContainerAttributes;
import de.dlr.shepard.util.QueryParamHelper;

public class StructuredDataContainerDAOTest extends BaseTestCase {
	@Mock
	private Session session;

	@InjectMocks
	private StructuredDataContainerDAO dao;

	@Test
	public void getEntityTypeTest() {
		var type = dao.getEntityType();
		assertEquals(StructuredDataContainer.class, type);
	}

	@Test
	public void findAll_WithoutName() {
		var col1 = new StructuredDataContainer(1L);
		col1.setName("Yes");
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", null);

		var query = """
				MATCH (c:StructuredDataContainer { deleted: FALSE }) WHERE (NOT exists((c)-[:has_permissions]->(:Permissions)) \
				OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: "bob" })) \
				OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "Public"})) \
				OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "PublicReadable"})) \
				OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: "bob"}))) \
				WITH c MATCH path=(c)-[*0..1]->(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN c, nodes(path), relationships(path)""";
		when(session.query(StructuredDataContainer.class, query, paramsMap)).thenReturn(List.of(col1));

		var params = new QueryParamHelper();
		var actual = dao.findAllStructuredDataContainers(params, "bob");
		verify(session).query(StructuredDataContainer.class, query, paramsMap);
		assertEquals(List.of(col1), actual);
	}

	@Test
	public void findAll_WithoutNameOrderByNameDesc() {
		var col1 = new StructuredDataContainer(1L);
		col1.setName("Yes");
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", null);

		var query = """
				MATCH (c:StructuredDataContainer { deleted: FALSE }) WHERE (NOT exists((c)-[:has_permissions]->(:Permissions)) \
				OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: "bob" })) \
				OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "Public"})) \
				OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "PublicReadable"})) \
				OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: "bob"}))) \
				WITH c ORDER BY toLower(c.name) DESC \
				MATCH path=(c)-[*0..1]->(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN c, nodes(path), relationships(path)""";
		when(session.query(StructuredDataContainer.class, query, paramsMap)).thenReturn(List.of(col1));

		var params = new QueryParamHelper();
		var attr = ContainerAttributes.name;
		params = params.withOrderByAttribute(attr, true);
		var actual = dao.findAllStructuredDataContainers(params, "bob");
		verify(session).query(StructuredDataContainer.class, query, paramsMap);
		assertEquals(List.of(col1), actual);
	}

	@Test
	public void findAll_WithName() {
		var col1 = new StructuredDataContainer(1L);
		col1.setName("Yes");
		var col2 = new StructuredDataContainer(2L);
		col2.setName("No");
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", "Yes");

		var query = """
				MATCH (c:StructuredDataContainer { name : $name, deleted: FALSE }) WHERE (NOT exists((c)-[:has_permissions]->(:Permissions)) \
				OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: "bob" })) \
				OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "Public"})) \
				OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "PublicReadable"})) \
				OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: "bob"}))) \
				WITH c MATCH path=(c)-[*0..1]->(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN c, nodes(path), relationships(path)""";
		when(session.query(StructuredDataContainer.class, query, paramsMap)).thenReturn(List.of(col1, col2));

		var params = new QueryParamHelper().withName("Yes");
		var actual = dao.findAllStructuredDataContainers(params, "bob");
		verify(session).query(StructuredDataContainer.class, query, paramsMap);
		assertEquals(List.of(col1), actual);
	}

	@Test
	public void findAll_WithNameOrderByNameDesc() {
		var col1 = new StructuredDataContainer(1L);
		col1.setName("Yes");
		var col2 = new StructuredDataContainer(2L);
		col2.setName("No");
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", "Yes");

		var query = """
				MATCH (c:StructuredDataContainer { name : $name, deleted: FALSE }) WHERE (NOT exists((c)-[:has_permissions]->(:Permissions)) \
				OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: "bob" })) \
				OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "Public"})) \
				OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "PublicReadable"})) \
				OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: "bob"}))) \
				WITH c ORDER BY toLower(c.name) DESC \
				MATCH path=(c)-[*0..1]->(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN c, nodes(path), relationships(path)""";
		when(session.query(StructuredDataContainer.class, query, paramsMap)).thenReturn(List.of(col1, col2));

		var params = new QueryParamHelper().withName("Yes");
		var attr = ContainerAttributes.name;
		params = params.withOrderByAttribute(attr, true);
		var actual = dao.findAllStructuredDataContainers(params, "bob");
		verify(session).query(StructuredDataContainer.class, query, paramsMap);
		assertEquals(List.of(col1), actual);
	}

	@Test
	public void findAll_WithPage() {
		var col1 = new StructuredDataContainer(1L);
		col1.setName("Yes");
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("offset", 300);
		paramsMap.put("size", 100);
		paramsMap.put("name", null);

		var query = """
				MATCH (c:StructuredDataContainer { deleted: FALSE }) WHERE (NOT exists((c)-[:has_permissions]->(:Permissions)) \
				OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: "bob" })) \
				OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "Public"})) \
				OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "PublicReadable"})) \
				OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: "bob"}))) \
				WITH c SKIP $offset LIMIT $size \
				MATCH path=(c)-[*0..1]->(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN c, nodes(path), relationships(path)""";
		when(session.query(StructuredDataContainer.class, query, paramsMap)).thenReturn(List.of(col1));

		var params = new QueryParamHelper().withPageAndSize(3, 100);
		var actual = dao.findAllStructuredDataContainers(params, "bob");
		verify(session).query(StructuredDataContainer.class, query, paramsMap);
		assertEquals(List.of(col1), actual);
	}

	@Test
	public void findAll_WithPageOrderByNameDesc() {
		var col1 = new StructuredDataContainer(1L);
		col1.setName("Yes");
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("offset", 300);
		paramsMap.put("size", 100);
		paramsMap.put("name", null);

		var query = """
				MATCH (c:StructuredDataContainer { deleted: FALSE }) WHERE (NOT exists((c)-[:has_permissions]->(:Permissions)) \
				OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: "bob" })) \
				OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "Public"})) \
				OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "PublicReadable"})) \
				OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: "bob"}))) \
				WITH c ORDER BY toLower(c.name) DESC SKIP $offset LIMIT $size \
				MATCH path=(c)-[*0..1]->(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN c, nodes(path), relationships(path)""";
		when(session.query(StructuredDataContainer.class, query, paramsMap)).thenReturn(List.of(col1));

		var params = new QueryParamHelper().withPageAndSize(3, 100);
		var attr = ContainerAttributes.name;
		params = params.withOrderByAttribute(attr, true);
		var actual = dao.findAllStructuredDataContainers(params, "bob");
		verify(session).query(StructuredDataContainer.class, query, paramsMap);
		assertEquals(List.of(col1), actual);
	}

	@Test
	public void findAll_WithNameAndPage() {
		var col1 = new StructuredDataContainer(1L);
		col1.setName("Yes");
		var col2 = new StructuredDataContainer(2L);
		col2.setName("No");
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("offset", 300);
		paramsMap.put("size", 100);
		paramsMap.put("name", "Yes");

		var query = """
				MATCH (c:StructuredDataContainer { name : $name, deleted: FALSE }) WHERE (NOT exists((c)-[:has_permissions]->(:Permissions)) \
				OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: "bob" })) \
				OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "Public"})) \
				OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "PublicReadable"})) \
				OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: "bob"}))) \
				WITH c SKIP $offset LIMIT $size \
				MATCH path=(c)-[*0..1]->(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN c, nodes(path), relationships(path)""";
		when(session.query(StructuredDataContainer.class, query, paramsMap)).thenReturn(List.of(col1, col2));

		var params = new QueryParamHelper().withPageAndSize(3, 100).withName("Yes");
		var actual = dao.findAllStructuredDataContainers(params, "bob");
		verify(session).query(StructuredDataContainer.class, query, paramsMap);
		assertEquals(List.of(col1), actual);
	}

	@Test
	public void findAll_WithNameAndPageOrderByNameDesc() {
		var col1 = new StructuredDataContainer(1L);
		col1.setName("Yes");
		var col2 = new StructuredDataContainer(2L);
		col2.setName("No");
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("offset", 300);
		paramsMap.put("size", 100);
		paramsMap.put("name", "Yes");

		var query = """
				MATCH (c:StructuredDataContainer { name : $name, deleted: FALSE }) WHERE (NOT exists((c)-[:has_permissions]->(:Permissions)) \
				OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: "bob" })) \
				OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "Public"})) \
				OR exists((c)-[:has_permissions]->(:Permissions {permissionType: "PublicReadable"})) \
				OR exists((c)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: "bob"}))) \
				WITH c ORDER BY toLower(c.name) DESC SKIP $offset LIMIT $size \
				MATCH path=(c)-[*0..1]->(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN c, nodes(path), relationships(path)""";
		when(session.query(StructuredDataContainer.class, query, paramsMap)).thenReturn(List.of(col1, col2));

		var params = new QueryParamHelper().withPageAndSize(3, 100).withName("Yes");
		var attr = ContainerAttributes.name;
		params = params.withOrderByAttribute(attr, true);
		var actual = dao.findAllStructuredDataContainers(params, "bob");
		verify(session).query(StructuredDataContainer.class, query, paramsMap);
		assertEquals(List.of(col1), actual);
	}

}
