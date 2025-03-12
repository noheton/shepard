package de.dlr.shepard.auth.permission.services;

import de.dlr.shepard.auth.permission.daos.PermissionsDAO;
import de.dlr.shepard.auth.permission.io.PermissionsIO;
import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.auth.permission.model.Roles;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.entities.UserGroup;
import de.dlr.shepard.auth.users.services.UserGroupService;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.neo4j.entities.BasicEntity;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.PermissionType;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.PathSegment;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

@RequestScoped
public class PermissionsService {

  private PermissionsDAO permissionsDAO;
  private UserService userService;
  private UserGroupService userGroupService;

  PermissionsService() {}

  @Inject
  public PermissionsService(PermissionsDAO permissionsDAO, UserService userService, UserGroupService userGroupService) {
    this.permissionsDAO = permissionsDAO;
    this.userService = userService;
    this.userGroupService = userGroupService;
  }

  /**
   * @param entity the entity the permissions belong to
   * @param user the user creating the permissions
   * @param permissionType the initial permission type
   * @return the newly created permissions
   */
  public Permissions createPermissions(BasicEntity entity, User user, PermissionType permissionType) {
    return permissionsDAO.createOrUpdate(new Permissions(entity, user, PermissionType.Private));
  }

  /**
   * Searches for permissions in Neo4j.
   *
   * @param entityId identifies the entity that the permissions object belongs to
   * @return Permissions with matching entity or null
   */
  public Permissions getPermissionsOfEntity(long entityId) {
    var permissions = permissionsDAO.findByEntityNeo4jId(entityId);
    if (permissions == null) {
      Log.errorf("Permissions with entity id %s is null", entityId);
      return null;
    }
    return permissions;
  }

  /**
   * @param entityId identifies the entity on which the user has the roles
   * @param username the user whose roles are checked
   * @return an object describing the roles of the user on the entity
   */
  public Roles getUserRolesOnEntity(long entityId, String username) {
    var perms = getPermissionsOfEntity(entityId);
    return getRoles(perms, username);
  }

  /**
   * Check whether a request is allowed or not
   *
   * @param entityId   the entity that is to be accessed
   * @param accessType the access type (read, write, manage)
   * @param username   the user that wants access
   * @return whether the access is allowed or not
   */
  public boolean isAccessTypeAllowedForUser(long entityId, AccessType accessType, String username) {
    Roles userRolesOnEntity = getUserRolesOnEntity(entityId, username);

    if (userRolesOnEntity.isOwner()) return true;

    return switch (accessType) {
      case Read -> userRolesOnEntity.isReader();
      case Write -> userRolesOnEntity.isWriter();
      case Manage -> userRolesOnEntity.isManager();
      case None -> false;
    };
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
    var old = getPermissionsOfEntity(id);
    if (old == null) {
      // There is no old permissions object
      permissions.setEntities(List.of(new BasicEntity(id)));
      return permissionsDAO.createOrUpdate(permissions);
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

  public boolean deletePermissions(Permissions permissions) {
    return permissionsDAO.deleteByNeo4jId(permissions.getId());
  }

  /**
   * Checks if the request is allowed based on access type and user name. The check is performed by checking the path segments, and request body.
   * @param requestContext
   * @param accessType
   * @param userName
   */
  public boolean isAllowed(ContainerRequestContext requestContext, AccessType accessType, String userName) {
    List<PathSegment> pathSegments = requestContext.getUriInfo().getPathSegments();
    var idSegment = pathSegments.size() > 1 ? pathSegments.get(1).getPath() : null;

    // migration state endpoints
    if (pathSegments.get(0).getPath().equals("temp") && pathSegments.get(1).getPath().equals("migrations")) {
      return true;
    }

    // Paths with length 1
    if (idSegment == null || idSegment.isBlank()) {
      // No id in path
      return true;
    }

    // lab journal entries
    if (pathSegments.get(0).getPath().equals(Constants.LAB_JOURNAL_ENTRIES)) {
      // Lab journal permissions are already checked inside LabJournalEntryService
      return true;
    }

    // entity paths
    if (StringUtils.isNumeric(idSegment)) {
      var entityId = Long.parseLong(idSegment);
      return isAccessTypeAllowedForUser(entityId, accessType, userName);
    }

    // usersearch and containersearch
    if (
      pathSegments.get(0).getPath().equals(Constants.SEARCH) &&
      List.of(Constants.USERS, Constants.CONTAINERS, Constants.COLLECTIONS).contains(pathSegments.get(1).getPath()) &&
      pathSegments.size() == 2
    ) {
      return true;
    }

    // users
    if (pathSegments.get(0).getPath().equals(Constants.USERS)) {
      if (pathSegments.size() <= 2 && AccessType.Read.equals(accessType)) return true; // it is allowed to read all users
      else if (userName.equals(idSegment)) return true; // it is allowed to access yourself
    }

    return false;
  }

  private Set<String> fetchUserNames(List<UserGroup> userGroups) {
    Set<String> ret = new HashSet<>();
    for (UserGroup userGroup : userGroups) {
      UserGroup fullUserGroup = userGroupService.getUserGroup(userGroup.getId());
      for (User user : fullUserGroup.getUsers()) {
        ret.add(user.getUsername());
      }
    }
    return ret;
  }

  private Roles getRoles(Permissions perms, String username) {
    if (perms == null) {
      // Legacy entity without permissions
      return new Roles(false, true, true, true);
    }
    var roles = new Roles(
      isOwner(perms, username),
      isManager(perms, username),
      isWriter(perms, username),
      isReader(perms, username)
    );
    return roles;
  }

  private boolean isOwner(Permissions perms, String username) {
    return perms.getOwner() != null && username.equals(perms.getOwner().getUsername());
  }

  private boolean isManager(Permissions perms, String username) {
    return perms.getManager().stream().anyMatch(u -> username.equals(u.getUsername()));
  }

  private boolean isReader(Permissions perms, String username) {
    var pub = PermissionType.Public.equals(perms.getPermissionType());
    var pubRead = PermissionType.PublicReadable.equals(perms.getPermissionType());
    var reader = perms.getReader().stream().anyMatch(u -> username.equals(u.getUsername()));
    var readerGroup = fetchUserNames(perms.getReaderGroups()).contains(username);
    return pub || pubRead || reader || readerGroup;
  }

  private boolean isWriter(Permissions perms, String username) {
    var pub = PermissionType.Public.equals(perms.getPermissionType());
    var writer = perms.getWriter().stream().anyMatch(u -> username.equals(u.getUsername()));
    var writerGroup = fetchUserNames(perms.getWriterGroups()).contains(username);
    return pub || writer || writerGroup;
  }

  /**
   * Fetches all missing data and transforms PermissionsIO to a Permissions object.
   */
  private Permissions convertPermissionsIO(PermissionsIO permissions) {
    var owner = permissions.getOwner() != null ? userService.getUser(permissions.getOwner()) : null;
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

      var user = userService.getUser(username);
      if (user != null) {
        result.add(user);
      }
    }
    return result;
  }

  private List<UserGroup> fetchUserGroups(long[] userGroupIds) {
    var result = new ArrayList<UserGroup>(userGroupIds.length);
    for (var userGroupId : userGroupIds) {
      var userGroup = userGroupService.getUserGroup(userGroupId);
      if (userGroup != null) {
        result.add(userGroup);
      }
    }
    return result;
  }
}
