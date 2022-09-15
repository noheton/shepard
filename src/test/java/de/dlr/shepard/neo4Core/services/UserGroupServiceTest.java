package de.dlr.shepard.neo4Core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.session.Session;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.dao.PermissionsDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.dao.UserGroupDAO;
import de.dlr.shepard.neo4Core.entities.Permissions;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.entities.UserGroup;
import de.dlr.shepard.neo4Core.io.UserGroupIO;
import de.dlr.shepard.util.DateHelper;
import de.dlr.shepard.util.QueryParamHelper;

public class UserGroupServiceTest extends BaseTestCase {

	@Mock
	private UserGroupDAO userGroupDAO = new UserGroupDAO();

	@Mock
	private UserDAO userDAO;

	@Mock
	private PermissionsDAO permissionsDAO;

	@Mock
	private Session session;

	@Mock
	private DateHelper dateHelper;

	@InjectMocks
	private UserGroupService service;

	@Test
	public void createUserGroupTest() {
		var creator = new User("creator");
		var date = new Date(23);

		UserGroupIO input = new UserGroupIO();
		input.setName("group");
		input.setUsernames(new String[] { "user", null });

		UserGroup toCreate = new UserGroup();
		toCreate.setName("group");
		var user = new User("user");
		ArrayList<User> users = new ArrayList<User>();
		users.add(user);
		toCreate.setUsers(users);
		toCreate.setCreatedBy(creator);
		toCreate.setCreatedAt(date);

		var created = new UserGroup();
		created.setName("group");
		created.setUsers(users);
		created.setCreatedBy(creator);
		created.setCreatedAt(date);
		created.setId(1L);

		when(userDAO.find("creator")).thenReturn(creator);
		when(userDAO.find("user")).thenReturn(user);
		when(dateHelper.getDate()).thenReturn(date);
		when(userGroupDAO.createOrUpdate(toCreate)).thenReturn(created);
		when(permissionsDAO.createOrUpdate(any())).thenReturn(null);

		UserGroup actual = service.createUserGroup(input, "creator");
		assertEquals(created, actual);
	}

	@Test
	public void getUserGroupTest() {
		UserGroup userGroup = new UserGroup();
		userGroup.setName("group");
		Long userGroupId = 1L;
		when(userGroupDAO.find(userGroupId)).thenReturn(userGroup);
		UserGroup actual = service.getUserGroup(1L);
		assertEquals(userGroup, actual);
	}

	@Test
	public void getAllUserGroupsTest() {
		List<UserGroup> allUserGroups = new ArrayList<UserGroup>();
		QueryParamHelper params = new QueryParamHelper();
		when(userGroupDAO.findAllUserGroups(params, "user1")).thenReturn(allUserGroups);
		assertEquals(0, service.getAllUserGroups(params, "user1").size());
	}

	@Test
	public void updateUserGroupTest() {
		var creator = new User("creator");
		var date = new Date(23);
		var updateUser = new User("updater");
		var updateDate = new Date(43);

		UserGroupIO input = new UserGroupIO();
		input.setName("group");
		input.setUsernames(new String[] { "listUser" });
		input.setId(1L);

		UserGroup oldGroup = new UserGroup();
		oldGroup.setName("group");
		var user = new User("user");
		ArrayList<User> users = new ArrayList<User>();
		users.add(user);
		oldGroup.setUsers(users);
		oldGroup.setCreatedBy(creator);
		oldGroup.setCreatedAt(date);
		oldGroup.setId(1L);

		UserGroup newGroup = new UserGroup();
		newGroup.setName("newName");
		newGroup.setUsers(users);
		newGroup.setCreatedBy(creator);
		newGroup.setCreatedAt(date);
		newGroup.setId(1L);
		newGroup.setUpdatedAt(updateDate);
		newGroup.setUpdatedBy(updateUser);

		when(userGroupDAO.find(1L)).thenReturn(oldGroup);
		when(userDAO.find("updater")).thenReturn(updateUser);
		when(dateHelper.getDate()).thenReturn(updateDate);
		when(userGroupDAO.createOrUpdate(oldGroup)).thenReturn(newGroup);

		var actual = service.updateUserGroup(input.getId(), input, "updater");
		assertEquals(newGroup, actual);
	}

	@Test
	public void deleteUserGroupTest() {
		var userGroup = new UserGroup();
		userGroup.setId(1L);
		var permissions = new Permissions();
		permissions.setId(2L);

		when(userGroupDAO.find(1L)).thenReturn(userGroup);
		when(userGroupDAO.delete(1L)).thenReturn(true);
		when(permissionsDAO.findByEntity(1L)).thenReturn(permissions);
		when(permissionsDAO.delete(2L)).thenReturn(true);

		var result = service.deleteUserGroup(1L);
		assertTrue(result);
	}

	@Test
	public void deleteUserGroupTest_noPermissions() {
		var userGroup = new UserGroup();
		userGroup.setId(1L);

		when(userGroupDAO.find(1L)).thenReturn(userGroup);
		when(userGroupDAO.delete(1L)).thenReturn(true);
		when(permissionsDAO.findByEntity(1L)).thenReturn(null);

		var result = service.deleteUserGroup(1L);
		assertTrue(result);
	}

	@Test
	public void deleteUserGroupTest_permissionsFailed() {
		var userGroup = new UserGroup();
		userGroup.setId(1L);
		var permissions = new Permissions();
		permissions.setId(2L);

		when(userGroupDAO.find(1L)).thenReturn(userGroup);
		when(permissionsDAO.findByEntity(1L)).thenReturn(permissions);
		when(permissionsDAO.delete(2L)).thenReturn(false);

		var result = service.deleteUserGroup(1L);
		assertFalse(result);
		verify(userGroupDAO, never()).delete(1L);
	}

	@Test
	public void deleteUserGroupTest_notFound() {
		when(userGroupDAO.find(1L)).thenReturn(null);

		var result = service.deleteUserGroup(1L);
		assertFalse(result);
		verify(userGroupDAO, never()).delete(1L);
	}

	@Test
	public void deleteUserGroupTest_failed() {
		var userGroup = new UserGroup();
		userGroup.setId(1L);
		var permissions = new Permissions();
		permissions.setId(2L);

		when(userGroupDAO.find(1L)).thenReturn(userGroup);
		when(userGroupDAO.delete(1L)).thenReturn(false);
		when(permissionsDAO.findByEntity(1L)).thenReturn(permissions);
		when(permissionsDAO.delete(2L)).thenReturn(true);

		var result = service.deleteUserGroup(1L);
		assertFalse(result);
	}

}
