package de.dlr.shepard.neo4Core.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.session.Session;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.entities.UserGroup;

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
		var actual = dao.delete(1L);
		assertTrue(actual);
	}

	@Test
	public void deleteTest_NotSuccessful() {
		var userGroup = new UserGroup();
		userGroup.setName("AKP");
		userGroup.setId(1L);
		when(session.load(UserGroup.class, 2L)).thenReturn(null);
		doNothing().when(session).delete(userGroup);
		var actual = dao.delete(2L);
		assertFalse(actual);
	}

	@Test
	public void findAllUserGroupsTest() {
		var userGroup = new UserGroup();
		userGroup.setName("AKP");
		userGroup.setId(1L);
		String username = "user";
		String query = "MATCH (ug:UserGroup { deleted: FALSE }) WHERE (NOT exists((ug)-[:has_permissions]->(:Permissions)) OR exists((ug)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"user\" })) OR exists((ug)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((ug)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((ug)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"user\"}))) MATCH path=(ug)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN ug, nodes(path), relationships(path)";
		when(session.query(UserGroup.class, query, Collections.emptyMap())).thenReturn(List.of(userGroup));
		var actual = dao.findAllUserGroups(username);
		verify(session).query(UserGroup.class, query, Collections.emptyMap());
		assertEquals(List.of(userGroup), actual);
	}

}
