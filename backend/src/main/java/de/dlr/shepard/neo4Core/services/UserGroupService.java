package de.dlr.shepard.neo4Core.services;

import de.dlr.shepard.neo4Core.dao.PermissionsDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.dao.UserGroupDAO;
import de.dlr.shepard.neo4Core.entities.Permissions;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.entities.UserGroup;
import de.dlr.shepard.neo4Core.io.UserGroupIO;
import de.dlr.shepard.util.DateHelper;
import de.dlr.shepard.util.PermissionType;
import de.dlr.shepard.util.QueryParamHelper;
import java.util.ArrayList;
import java.util.List;

public class UserGroupService {

  private UserGroupDAO userGroupDAO = new UserGroupDAO();
  private UserDAO userDAO = new UserDAO();
  private PermissionsDAO permissionsDAO = new PermissionsDAO();
  private DateHelper dateHelper = new DateHelper();

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
    permissionsDAO.createOrUpdate(new Permissions(created, user, PermissionType.Private));
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

    var permissions = permissionsDAO.findByEntityNeo4jId(id);
    var permissionsResult = permissions == null || permissionsDAO.deleteByNeo4jId(permissions.getId());
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
