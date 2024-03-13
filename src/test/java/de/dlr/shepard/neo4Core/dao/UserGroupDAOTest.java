package de.dlr.shepard.neo4Core.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
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
import de.dlr.shepard.neo4Core.entities.UserGroup;
import de.dlr.shepard.neo4Core.orderBy.DataObjectAttributes;
import de.dlr.shepard.util.QueryParamHelper;

public class UserGroupDAOTest extends BaseTestCase {

	@Mock
	private Session session;
	@InjectMocks
	private UserGroupDAO dao;

	@Test
	public void getEntityTypeTest() {
		var type = dao.getEntityType();
		assertEquals(UserGroup.class, type);
	}

	@Test
	public void deleteTest_Successful() {
		var userGroup = new UserGroup();
		userGroup.setName("AKP");
		userGroup.setId(1L);
		when(session.load(UserGroup.class, 1L)).thenReturn(userGroup);
		doNothing().when(session).delete(userGroup);
		var actual = dao.deleteByNeo4jId(1L);
		assertTrue(actual);
	}

	@Test
	public void deleteTest_NotSuccessful() {
		var userGroup = new UserGroup();
		userGroup.setName("AKP");
		userGroup.setId(1L);
		when(session.load(UserGroup.class, 2L)).thenReturn(null);
		doNothing().when(session).delete(userGroup);
		var actual = dao.deleteByNeo4jId(2L);
		assertFalse(actual);
	}

	@Test
	public void findAllUserGroupsTestFullParams() {
		QueryParamHelper params = new QueryParamHelper();
		params = params.withPageAndSize(3, 4);
		params = params.withOrderByAttribute(DataObjectAttributes.name, false);
		var userGroup = new UserGroup();
		userGroup.setName("AKP");
		userGroup.setId(1L);
		String username = "user";
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("offset", 12);
		paramsMap.put("size", 4);
		String query = """
				MATCH (ug:UserGroup { deleted: FALSE }) WHERE (NOT exists((ug)-[:has_permissions]->(:Permissions)) \
				OR exists((ug)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"user\" })) \
				OR exists((ug)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) \
				OR exists((ug)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) \
				OR exists((ug)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"user\"}))) \
				WITH ug ORDER BY toLower(ug.name) SKIP $offset LIMIT $size \
				MATCH path=(ug)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN ug, nodes(path), relationships(path)""";
		when(session.query(UserGroup.class, query, paramsMap)).thenReturn(List.of(userGroup));
		var actual = dao.findAllUserGroups(params, username);
		verify(session).query(UserGroup.class, query, paramsMap);
		assertEquals(List.of(userGroup), actual);
	}

	@Test
	public void findAllUserGroupsTestNoPagination() {
		QueryParamHelper params = new QueryParamHelper();
		params = params.withOrderByAttribute(DataObjectAttributes.name, false);
		var userGroup = new UserGroup();
		userGroup.setName("AKP");
		userGroup.setId(1L);
		String username = "user";
		Map<String, Object> paramsMap = new HashMap<>();
		String query = """
				MATCH (ug:UserGroup { deleted: FALSE }) WHERE (NOT exists((ug)-[:has_permissions]->(:Permissions)) \
				OR exists((ug)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"user\" })) \
				OR exists((ug)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) \
				OR exists((ug)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) \
				OR exists((ug)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"user\"}))) \
				WITH ug ORDER BY toLower(ug.name) \
				MATCH path=(ug)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN ug, nodes(path), relationships(path)""";
		when(session.query(UserGroup.class, query, paramsMap)).thenReturn(List.of(userGroup));
		var actual = dao.findAllUserGroups(params, username);
		verify(session).query(UserGroup.class, query, paramsMap);
		assertEquals(List.of(userGroup), actual);
	}

	@Test
	public void findAllUserGroupsTestNoOrderBy() {
		QueryParamHelper params = new QueryParamHelper();
		params = params.withPageAndSize(3, 4);
		var userGroup = new UserGroup();
		userGroup.setName("AKP");
		userGroup.setId(1L);
		String username = "user";
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("offset", 12);
		paramsMap.put("size", 4);
		String query = """
				MATCH (ug:UserGroup { deleted: FALSE }) WHERE (NOT exists((ug)-[:has_permissions]->(:Permissions)) \
				OR exists((ug)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"user\" })) \
				OR exists((ug)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) \
				OR exists((ug)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) \
				OR exists((ug)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"user\"}))) \
				WITH ug SKIP $offset LIMIT $size \
				MATCH path=(ug)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN ug, nodes(path), relationships(path)""";
		when(session.query(UserGroup.class, query, paramsMap)).thenReturn(List.of(userGroup));
		var actual = dao.findAllUserGroups(params, username);
		verify(session).query(UserGroup.class, query, paramsMap);
		assertEquals(List.of(userGroup), actual);
	}

}
