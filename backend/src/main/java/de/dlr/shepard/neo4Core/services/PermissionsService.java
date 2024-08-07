package de.dlr.shepard.neo4Core.services;

import de.dlr.shepard.neo4Core.dao.PermissionsDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.dao.UserGroupDAO;
import de.dlr.shepard.neo4Core.entities.Permissions;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.entities.UserGroup;
import de.dlr.shepard.neo4Core.io.PermissionsIO;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PermissionsService {

  private PermissionsDAO permissionsDAO = new PermissionsDAO();
  private UserDAO userDAO = new UserDAO();
  private UserGroupDAO userGroupDAO = new UserGroupDAO();

  /**
   * Searches for permissions in Neo4j.
   *
   * @param id identifies the entity that the permissions object belongs to
   * @return Permissions with matching entity or null
   */
  public Permissions getPermissionsByNeo4jId(long id) {
    var permissions = permissionsDAO.findByEntityNeo4jId(id);
    if (permissions == null) {
      log.error("Permissions with entity id {} is null", id);
      return null;
    }
    return permissions;
  }

  /**
   * Searches for permissions in Neo4j.
   *
   * @param shepardId identifies the entity that the permissions object belongs to
   * @return Permissions with matching entity or null
   */
  public Permissions getPermissionsByShepardId(long shepardId) {
    var permissions = permissionsDAO.findByEntityShepardId(shepardId);
    if (permissions == null) {
      log.error("Permissions with shepardId {} is null", shepardId);
      return null;
    }
    return permissions;
  }

  /**
   * Searches for permissions in Neo4j.
   *
   * @param shepardId identifies the entity that the permissions object belongs to
   * @return Permissions with matching entity or null
   */
  public Permissions getPermissionsByCollectionShepardId(long shepardId) {
    var permissions = permissionsDAO.findByCollectionShepardId(shepardId);
    if (permissions == null) {
      log.error("Permissions with shepardId {} is null", shepardId);
      return null;
    }
    return permissions;
  }

  /**
   * Create Permissions based on an entity and the owner
   *
   * @param entityId identifies the entity
   * @return The created Permissions object
   */
  public Permissions createPermissionsByNeo4jId(long entityId) {
    var permissions = new Permissions();
    return permissionsDAO.createWithEntityNeo4jId(permissions, entityId);
  }

  /**
   * Updates the Permissions in Neo4j
   *
   * @param permissionsIo the new Permissions object
   * @param id            identifies the entity
   * @return the updated Permissions object
   */
  public Permissions updatePermissionsByNeo4jId(PermissionsIO permissionsIo, long id) {
    var permissions = convertPermissionsIO(permissionsIo);
    var old = getPermissionsByNeo4jId(id);
    if (old == null) {
      // There is no old permissions object
      return permissionsDAO.createWithEntityNeo4jId(permissions, id);
    }
    old.setOwner(permissions.getOwner());
    old.setReader(permissions.getReader());
    old.setWriter(permissions.getWriter());
    old.setReaderGroups(permissions.getReaderGroups());
    old.setWriterGroups(permissions.getWriterGroups());
    old.setManager(permissions.getManager());
    old.setPermissionType(permissions.getPermissionType());
    return permissionsDAO.createOrUpdate(old);
  }

  /**
   * Updates the Permissions in Neo4j
   *
   * @param permissionsIo the new Permissions object
   * @param shepardId     identifies the entity
   * @return the updated Permissions object
   */
  public Permissions updatePermissionsByShepardId(PermissionsIO permissionsIo, long shepardId) {
    var permissions = convertPermissionsIO(permissionsIo);
    var old = getPermissionsByShepardId(shepardId);
    if (old == null) {
      // There is no old permissions object
      return permissionsDAO.createWithEntityShepardId(permissions, shepardId);
    }
    old.setOwner(permissions.getOwner());
    old.setReader(permissions.getReader());
    old.setWriter(permissions.getWriter());
    old.setReaderGroups(permissions.getReaderGroups());
    old.setWriterGroups(permissions.getWriterGroups());
    old.setManager(permissions.getManager());
    old.setPermissionType(permissions.getPermissionType());
    var ret = permissionsDAO.createOrUpdate(old);
    return ret;
  }

  private Permissions convertPermissionsIO(PermissionsIO permissions) {
    var owner = permissions.getOwner() != null ? userDAO.find(permissions.getOwner()) : null;
    var permissionType = permissions.getPermissionType();
    var reader = fetchUsers(permissions.getReader());
    var writer = fetchUsers(permissions.getWriter());
    var readerGroups = fetchUserGroups(permissions.getReaderGroupIds());
    var writerGroups = fetchUserGroups(permissions.getWriterGroupIds());
    var manager = fetchUsers(permissions.getManager());
    return new Permissions(owner, reader, writer, readerGroups, writerGroups, manager, permissionType);
  }

  private List<User> fetchUsers(String[] usernames) {
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

  private List<UserGroup> fetchUserGroups(long[] userGroupIds) {
    var result = new ArrayList<UserGroup>(userGroupIds.length);
    for (var userGroupId : userGroupIds) {
      var userGroup = userGroupDAO.findByNeo4jId(userGroupId);
      if (userGroup != null) {
        result.add(userGroup);
      }
    }
    return result;
  }
}
