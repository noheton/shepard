package de.dlr.shepard.auth.users.services;

import de.dlr.shepard.auth.permission.io.PermissionsIO;
import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.auth.permission.model.Roles;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.users.daos.UserGroupDAO;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.entities.UserGroup;
import de.dlr.shepard.auth.users.io.UserGroupIO;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.PermissionType;
import de.dlr.shepard.common.util.QueryParamHelper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RequestScoped
public class UserGroupService {

  @Inject
  AuthenticationContext authenticationContext;

  @Inject
  UserGroupDAO userGroupDAO;

  @Inject
  UserService userService;

  @Inject
  PermissionsService permissionsService;

  @Inject
  DateHelper dateHelper;

  /**
   * Gets userGroup by userGroupId
   *
   * @param userGroupId
   * @return UserGroup
   * @throws InvalidPathException if user group could not be found by id
   * @throws InvalidAuthException if user has no read permissions on usergroup
   */
  public UserGroup getUserGroup(Long userGroupId) {
    UserGroup group = getUserGroupOptional(userGroupId).orElseThrow(() ->
      new InvalidPathException("ID ERROR - User Group with id %s is null or deleted".formatted(userGroupId))
    );
    assertIsAllowedToReadUserGroup(userGroupId);
    return group;
  }

  /**
   * Gets userGroup by userGroupId.
   *
   * No additional checks like a read permission check are performed.
   * @param userGroupId
   * @return Optional<UserGroup>
   */
  public Optional<UserGroup> getUserGroupOptional(Long userGroupId) {
    UserGroup group = userGroupDAO.findByNeo4jId(userGroupId);
    if (group == null || group.isDeleted()) {
      return Optional.empty();
    }
    return Optional.of(group);
  }

  public List<UserGroup> getAllUserGroups(QueryParamHelper params) {
    return userGroupDAO.findAllUserGroups(params, authenticationContext.getCurrentUserName());
  }

  public UserGroup createUserGroup(UserGroupIO userGroup) {
    var user = userService.getCurrentUser();
    var toCreate = new UserGroup();
    toCreate.setName(userGroup.getName());
    toCreate.setCreatedBy(user);
    toCreate.setCreatedAt(dateHelper.getDate());
    toCreate.setUsers(fetchUsers(userGroup.getUsernames()));
    var created = userGroupDAO.createOrUpdate(toCreate);
    permissionsService.createPermissions(created, user, PermissionType.Private);
    return created;
  }

  /**
   * Updates usergroup by id
   *
   * @param id
   * @param userGroup
   * @return UserGroup
   * @throws InvalidPathException if user group could not be found by id
   * @throws InvalidAuthException if user has no read or edit permissions on usergroup
   */
  public UserGroup updateUserGroup(Long id, UserGroupIO userGroup) {
    getUserGroup(id);
    assertIsAllowedToEditUserGroup(id);

    var user = userService.getCurrentUser();
    var old = userGroupDAO.findByNeo4jId(id);
    old.setUpdatedBy(user);
    old.setUpdatedAt(dateHelper.getDate());
    old.setName(userGroup.getName());
    old.setUsers(fetchUsers(userGroup.getUsernames()));
    var updated = userGroupDAO.createOrUpdate(old);
    return updated;
  }

  /**
   * Deletes a user group and removes the permissions
   * @param id
   * @throws InvalidPathException if user group could not be found by id
   * @throws InvalidAuthException if user has no read or edit permissions on usergroup
   * @throws NotFoundException if the usergroup's permissions could not be retrieved or deleted, or if permissions could not be found on entity with id
   */
  public void deleteUserGroup(Long id) {
    getUserGroup(id);
    assertIsAllowedToEditUserGroup(id);

    Optional<Permissions> permissions = permissionsService.getPermissionsOfEntityOptional(id);
    if (permissions.isPresent() && !permissionsService.deletePermissions(permissions.get())) {
      String errorMsg = "Could not delete permissions %s".formatted(permissions.toString());
      Log.error(errorMsg);
      throw new NotFoundException(errorMsg);
    }
    if (!userGroupDAO.deleteByNeo4jId(id)) {
      String errorMsg = "Could not delete userGroup with id %s".formatted(id);
      Log.error(errorMsg);
      throw new NotFoundException(errorMsg);
    }
  }

  public Roles getUserGroupRoles(long groupId) {
    getUserGroup(groupId);

    return permissionsService.getUserRolesOnEntity(groupId, authenticationContext.getCurrentUserName());
  }

  public Permissions getUserGroupPermissions(long groupId) {
    getUserGroup(groupId);
    assertIsAllowedToManageUserGroup(groupId);

    return permissionsService.getPermissionsOfEntity(groupId);
  }

  public Permissions updateUserGroupPermissions(PermissionsIO newPermissions, long groupId) {
    getUserGroup(groupId);
    assertIsAllowedToManageUserGroup(groupId);

    return permissionsService.updatePermissionsByNeo4jId(newPermissions, groupId);
  }

  /**
   * Checks if the user requested the UserGroup is allowed to read it
   *
   * @throws InvalidAuthException when user is not allowed to read the UserGroup
   */
  public void assertIsAllowedToReadUserGroup(long groupId) {
    if (
      !permissionsService.isAccessTypeAllowedForUser(
        groupId,
        AccessType.Read,
        authenticationContext.getCurrentUserName()
      )
    ) {
      throw new InvalidAuthException("The requested action is forbidden by the permission policies");
    }
  }

  /**
   * Checks if the user requested the UserGroup is allowed to edit it
   *
   * @throws InvalidAuthException when user is not allowed to edit the UserGroup
   */
  public void assertIsAllowedToEditUserGroup(long groupId) {
    if (
      !permissionsService.isAccessTypeAllowedForUser(
        groupId,
        AccessType.Write,
        authenticationContext.getCurrentUserName()
      )
    ) {
      throw new InvalidAuthException("The requested action is forbidden by the permission policies");
    }
  }

  /**
   * Checks if the user requested the UserGroup is allowed to manage it
   *
   * @throws InvalidAuthException when user is not allowed to manage the UserGroup
   */
  public void assertIsAllowedToManageUserGroup(long groupId) {
    if (
      !permissionsService.isAccessTypeAllowedForUser(
        groupId,
        AccessType.Manage,
        authenticationContext.getCurrentUserName()
      )
    ) {
      throw new InvalidAuthException("The requested action is forbidden by the permission policies");
    }
  }

  private ArrayList<User> fetchUsers(String[] usernames) {
    var result = new ArrayList<User>(usernames.length);
    for (var username : usernames) {
      if (username == null) {
        continue;
      }
      userService.getUserOptional(username).ifPresent(u -> result.add(u));
    }
    return result;
  }
}
