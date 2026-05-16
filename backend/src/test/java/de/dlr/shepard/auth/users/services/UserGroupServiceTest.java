package de.dlr.shepard.auth.users.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.users.daos.UserGroupDAO;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.entities.UserGroup;
import de.dlr.shepard.auth.users.io.UserGroupIO;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.QueryParamHelper;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.neo4j.ogm.session.Session;

@QuarkusComponentTest
public class UserGroupServiceTest {

  @InjectMock
  UserGroupDAO userGroupDAO;

  @InjectMock
  UserService userService;

  @InjectMock
  PermissionsService permissionsService;

  @InjectMock
  Session session;

  @InjectMock
  DateHelper dateHelper;

  @InjectMock
  AuthenticationContext authenticationContext;

  @Inject
  UserGroupService service;

  private final User user = new User("Testuser");

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

    when(userService.getCurrentUser()).thenReturn(creator);
    when(userService.getUserOptional("user")).thenReturn(Optional.of(user));
    when(dateHelper.getDate()).thenReturn(date);
    when(userGroupDAO.createOrUpdate(toCreate)).thenReturn(created);
    when(permissionsService.createPermissions(any(), any(), any())).thenReturn(null);

    UserGroup actual = service.createUserGroup(input);
    assertEquals(created, actual);
  }

  @Test
  public void getUserGroupTest() {
    UserGroup userGroup = new UserGroup();
    userGroup.setName("group");
    Long userGroupId = 1L;

    when(userGroupDAO.findByNeo4jId(userGroupId)).thenReturn(userGroup);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    when(
      permissionsService.isAccessTypeAllowedForUser(
        userGroupId,
        AccessType.Read,
        authenticationContext.getCurrentUserName(),
        anyLong()
      )
    ).thenReturn(true);

    UserGroup actual = service.getUserGroup(1L);
    assertEquals(userGroup, actual);
  }

  @Test
  public void getAllUserGroupsTest() {
    List<UserGroup> allUserGroups = new ArrayList<>();
    QueryParamHelper params = new QueryParamHelper();
    when(userGroupDAO.findAllUserGroups(params, "user1")).thenReturn(allUserGroups);
    assertEquals(0, service.getAllUserGroups(params).size());
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
    when(userService.getUser("updater")).thenReturn(updateUser);
    when(dateHelper.getDate()).thenReturn(updateDate);
    when(userGroupDAO.createOrUpdate(oldGroup)).thenReturn(newGroup);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    when(
      permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Read, authenticationContext.getCurrentUserName(), anyLong())
    ).thenReturn(true);
    when(
      permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Write, authenticationContext.getCurrentUserName(), anyLong())
    ).thenReturn(true);

    var actual = service.updateUserGroup(input.getId(), input);
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
    when(permissionsService.getPermissionsOfEntityOptional(1L)).thenReturn(Optional.of(permissions));
    when(permissionsService.deletePermissions(permissions)).thenReturn(true);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    when(
      permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Read, authenticationContext.getCurrentUserName(), anyLong())
    ).thenReturn(true);
    when(
      permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Write, authenticationContext.getCurrentUserName(), anyLong())
    ).thenReturn(true);

    assertDoesNotThrow(() -> service.deleteUserGroup(1L));
  }

  @Test
  public void deleteUserGroupTest_noPermissions() {
    var userGroup = new UserGroup();
    userGroup.setId(1L);

    when(userGroupDAO.findByNeo4jId(1L)).thenReturn(userGroup);
    when(userGroupDAO.deleteByNeo4jId(1L)).thenReturn(true);
    when(permissionsService.getPermissionsOfEntityOptional(1L)).thenReturn(Optional.empty());
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    when(
      permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Read, authenticationContext.getCurrentUserName(), anyLong())
    ).thenReturn(true);
    when(
      permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Write, authenticationContext.getCurrentUserName(), anyLong())
    ).thenReturn(true);

    assertDoesNotThrow(() -> service.deleteUserGroup(1L));
  }

  @Test
  public void deleteUserGroupTest_permissionsFailed() {
    var userGroup = new UserGroup();
    userGroup.setId(1L);
    var permissions = new Permissions();
    permissions.setId(2L);

    when(userGroupDAO.findByNeo4jId(1L)).thenReturn(userGroup);
    when(permissionsService.getPermissionsOfEntityOptional(1L)).thenReturn(Optional.of(permissions));
    when(permissionsService.deletePermissions(permissions)).thenReturn(false);
    when(
      permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Read, authenticationContext.getCurrentUserName(), anyLong())
    ).thenReturn(true);
    when(
      permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Write, authenticationContext.getCurrentUserName(), anyLong())
    ).thenReturn(true);

    assertThrows(NotFoundException.class, () -> service.deleteUserGroup(1L));

    verify(userGroupDAO, never()).deleteByNeo4jId(1L);
  }

  @Test
  public void deleteUserGroupTest_notFound() {
    when(userGroupDAO.findByNeo4jId(1L)).thenReturn(null);
    when(
      permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Read, authenticationContext.getCurrentUserName(), anyLong())
    ).thenReturn(true);
    when(
      permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Write, authenticationContext.getCurrentUserName(), anyLong())
    ).thenReturn(true);

    assertThrows(InvalidPathException.class, () -> service.deleteUserGroup(1L));
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
    when(permissionsService.getPermissionsOfEntityOptional(1L)).thenReturn(Optional.of(permissions));
    when(permissionsService.deletePermissions(permissions)).thenReturn(true);
    when(
      permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Read, authenticationContext.getCurrentUserName(), anyLong())
    ).thenReturn(true);
    when(
      permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Write, authenticationContext.getCurrentUserName(), anyLong())
    ).thenReturn(true);

    assertThrows(NotFoundException.class, () -> service.deleteUserGroup(1L));
  }
}
