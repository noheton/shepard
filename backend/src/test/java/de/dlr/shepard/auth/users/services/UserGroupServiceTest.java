package de.dlr.shepard.auth.users.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.entities.Permissions;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.auth.users.daos.UserGroupDAO;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.entities.UserGroup;
import de.dlr.shepard.auth.users.io.UserGroupIO;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.QueryParamHelper;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.neo4j.ogm.session.Session;

@QuarkusComponentTest
public class UserGroupServiceTest {

  @InjectMock
  UserGroupDAO userGroupDAO;

  @InjectMock
  UserDAO userDAO;

  @InjectMock
  PermissionsService permissionsService;

  @InjectMock
  Session session;

  @InjectMock
  DateHelper dateHelper;

  @Inject
  UserGroupService service;

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
    ArrayList<User> users = new ArrayList<>();
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
    when(permissionsService.createPermissions(any(), any(), any())).thenReturn(null);

    UserGroup actual = service.createUserGroup(input, "creator");
    assertEquals(created, actual);
  }

  @Test
  public void getUserGroupTest() {
    UserGroup userGroup = new UserGroup();
    userGroup.setName("group");
    Long userGroupId = 1L;
    when(userGroupDAO.findByNeo4jId(userGroupId)).thenReturn(userGroup);
    UserGroup actual = service.getUserGroup(1L);
    assertEquals(userGroup, actual);
  }

  @Test
  public void getAllUserGroupsTest() {
    List<UserGroup> allUserGroups = new ArrayList<>();
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
    ArrayList<User> users = new ArrayList<>();
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

    when(userGroupDAO.findByNeo4jId(1L)).thenReturn(oldGroup);
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

    when(userGroupDAO.findByNeo4jId(1L)).thenReturn(userGroup);
    when(userGroupDAO.deleteByNeo4jId(1L)).thenReturn(true);
    when(permissionsService.getPermissionsOfEntity(1L)).thenReturn(permissions);
    when(permissionsService.deletePermissions(permissions)).thenReturn(true);

    var result = service.deleteUserGroup(1L);
    assertTrue(result);
  }

  @Test
  public void deleteUserGroupTest_noPermissions() {
    var userGroup = new UserGroup();
    userGroup.setId(1L);

    when(userGroupDAO.findByNeo4jId(1L)).thenReturn(userGroup);
    when(userGroupDAO.deleteByNeo4jId(1L)).thenReturn(true);
    when(permissionsService.getPermissionsOfEntity(1L)).thenReturn(null);

    var result = service.deleteUserGroup(1L);
    assertTrue(result);
  }

  @Test
  public void deleteUserGroupTest_permissionsFailed() {
    var userGroup = new UserGroup();
    userGroup.setId(1L);
    var permissions = new Permissions();
    permissions.setId(2L);

    when(userGroupDAO.findByNeo4jId(1L)).thenReturn(userGroup);
    when(permissionsService.getPermissionsOfEntity(1L)).thenReturn(permissions);
    when(permissionsService.deletePermissions(permissions)).thenReturn(false);

    var result = service.deleteUserGroup(1L);
    assertFalse(result);
    verify(userGroupDAO, never()).deleteByNeo4jId(1L);
  }

  @Test
  public void deleteUserGroupTest_notFound() {
    when(userGroupDAO.findByNeo4jId(1L)).thenReturn(null);

    var result = service.deleteUserGroup(1L);
    assertFalse(result);
    verify(userGroupDAO, never()).deleteByNeo4jId(1L);
  }

  @Test
  public void deleteUserGroupTest_failed() {
    var userGroup = new UserGroup();
    userGroup.setId(1L);
    var permissions = new Permissions();
    permissions.setId(2L);

    when(userGroupDAO.findByNeo4jId(1L)).thenReturn(userGroup);
    when(userGroupDAO.deleteByNeo4jId(1L)).thenReturn(false);
    when(permissionsService.getPermissionsOfEntity(1L)).thenReturn(permissions);
    when(permissionsService.deletePermissions(permissions)).thenReturn(true);

    var result = service.deleteUserGroup(1L);
    assertFalse(result);
  }
}
