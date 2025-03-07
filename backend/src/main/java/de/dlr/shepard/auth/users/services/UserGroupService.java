package de.dlr.shepard.auth.users.services;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.auth.users.daos.UserGroupDAO;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.entities.UserGroup;
import de.dlr.shepard.auth.users.io.UserGroupIO;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.PermissionType;
import de.dlr.shepard.common.util.QueryParamHelper;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;

@RequestScoped
public class UserGroupService {

  private UserGroupDAO userGroupDAO;
  private UserDAO userDAO;
  private PermissionsService permissionsService;
  private DateHelper dateHelper;

  UserGroupService() {}

  @Inject
  public UserGroupService(
    UserGroupDAO userGroupDAO,
    UserDAO userDAO,
    PermissionsService permissionsService,
    DateHelper dateHelper
  ) {
    this.userGroupDAO = userGroupDAO;
    this.userDAO = userDAO;
    this.permissionsService = permissionsService;
    this.dateHelper = dateHelper;
  }

  public List<UserGroup> getAllUserGroups(QueryParamHelper params, String username) {
    return userGroupDAO.findAllUserGroups(params, username);
  }

  public UserGroup getUserGroup(Long userGroupId) {
    return userGroupDAO.findByNeo4jId(userGroupId);
  }

  public UserGroup createUserGroup(UserGroupIO userGroup, String username) {
    var user = userDAO.find(username);
    var toCreate = new UserGroup();
    toCreate.setName(userGroup.getName());
    toCreate.setCreatedBy(user);
    toCreate.setCreatedAt(dateHelper.getDate());
    toCreate.setUsers(fetchUsers(userGroup.getUsernames()));
    var created = userGroupDAO.createOrUpdate(toCreate);
    permissionsService.createPermissions(created, user, PermissionType.Private);
    return created;
  }

  public UserGroup updateUserGroup(Long id, UserGroupIO userGroup, String username) {
    var user = userDAO.find(username);
    var old = userGroupDAO.findByNeo4jId(id);
    old.setUpdatedBy(user);
    old.setUpdatedAt(dateHelper.getDate());
    old.setName(userGroup.getName());
    old.setUsers(fetchUsers(userGroup.getUsernames()));
    var updated = userGroupDAO.createOrUpdate(old);
    return updated;
  }

  public boolean deleteUserGroup(Long id) {
    var old = userGroupDAO.findByNeo4jId(id);
    if (old == null) return false;

    var permissions = permissionsService.getPermissionsOfEntity(id);
    var permissionsResult = permissions == null || permissionsService.deletePermissions(permissions);
    if (!permissionsResult) return false;

    return userGroupDAO.deleteByNeo4jId(id);
  }

  private ArrayList<User> fetchUsers(String[] usernames) {
    var result = new ArrayList<User>(usernames.length);
    for (var username : usernames) {
      if (username == null) {
        continue;
      }
      var user = userDAO.find(username);
      if (user != null) {
        result.add(user);
      }
    }
    return result;
  }
}
