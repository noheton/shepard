package de.dlr.shepard.auth.permission.services;

import de.dlr.shepard.auth.permission.daos.PermissionsDAO;
import de.dlr.shepard.auth.permission.io.PermissionsIO;
import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.auth.permission.model.Roles;
import de.dlr.shepard.auth.security.PermissionLastSeenCache;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.entities.UserGroup;
import de.dlr.shepard.auth.users.services.UserGroupService;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidRequestException;
import de.dlr.shepard.common.neo4j.entities.BasicEntity;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.PermissionType;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.PathSegment;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

@RequestScoped
public class PermissionsService {

  @Inject
  PermissionsDAO permissionsDAO;

  @Inject
  UserService userService;

  @Inject
  UserGroupService userGroupService;

  @Inject
  PermissionLastSeenCache permissionLastSeenCache;

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
   * This function does NOT perform a check if the user is allowed to query the permissions of an entity.
   *
   * @param entityId identifies the entity that the permissions object belongs to
   * @return Optional<Permissions> with matching entity
   */
  public Optional<Permissions> getPermissionsOfEntityOptional(long entityId) {
    var permissions = permissionsDAO.findByEntityNeo4jId(entityId);
    if (permissions == null) {
      Log.errorf("Permissions with entity id %s is null", entityId);
      return Optional.empty();
    }
    return Optional.of(permissions);
  }

  /**
   * Searches for permissions in Neo4j.
   *
   * This function does perform a check if the user is allowed to query the permissions of an entity.
   *
   * @param entityId identifies the entity that the permissions object belongs to
   * @return Permissions with matching entity
   * @throws NotFoundException if permission could not be found
   * @throws InvalidAuthException if user has to rights to read permissions
   */
  public Permissions getPermissionsOfEntity(long entityId) {
    User user = userService.getCurrentUser();
    isAccessTypeAllowedForUser(entityId, AccessType.Manage, user.getUsername());
    return getPermissionsOfEntityOptional(entityId).orElseThrow(() ->
      new NotFoundException(String.format("Permissions with entity %s is null", entityId))
    );
  }

  /**
   * @param entityId identifies the entity on which the user has the roles
   * @param username the user whose roles are checked
   * @return an object describing the roles of the user on the entity
   */
  public Roles getUserRolesOnEntity(long entityId, String username) {
    var perms = getPermissionsOfEntityOptional(entityId);
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
    String cacheKey = String.format("%s,%s,%s", entityId, accessType.toString(), username);
    if (permissionLastSeenCache.isKeyCached(cacheKey)) return true;

    Roles userRolesOnEntity = getUserRolesOnEntity(entityId, username);

    boolean isAllowed;
    if (userRolesOnEntity.isOwner()) {
      isAllowed = true;
    } else {
      isAllowed = switch (accessType) {
        case Read -> userRolesOnEntity.isReader() || userRolesOnEntity.isWriter() || userRolesOnEntity.isManager();
        case Write -> userRolesOnEntity.isWriter() || userRolesOnEntity.isManager();
        case Manage -> userRolesOnEntity.isManager();
        case None -> false;
      };
    }

    if (isAllowed) {
      permissionLastSeenCache.cacheKey(cacheKey);
    }
    return isAllowed;
  }

  /**
   * Checks if the current user is owner of the object specified by its entity id.
   *
   * @param entityId
   * @return boolean, true if current user is owner
   * @throws InvalidRequestException if user could not be extracted from authentication context
   */
  public boolean isCurrentUserOwner(long entityId) {
    Roles roles = getUserRolesOnEntity(entityId, userService.getCurrentUser().getUsername());
    return roles.isOwner();
  }

  /**
   * Updates the Permissions in Neo4j
   *
   * @param permissionsIo the new Permissions object
   * @param id            identifies the entity
   * @return the updated Permissions object
   */
  public Permissions updatePermissionsByNeo4jId(PermissionsIO permissionsIo, long id) {
    var newPermissions = convertPermissionsIO(permissionsIo);
    Optional<Permissions> old = getPermissionsOfEntityOptional(id);
    if (old.isEmpty()) {
      // There is no old permissions object
      newPermissions.setEntities(List.of(new BasicEntity(id)));
      return permissionsDAO.createOrUpdate(newPermissions);
    }
    var oldPermissions = old.get();

    if (newPermissions.getOwner() != null && oldPermissions.getOwner() != newPermissions.getOwner()) {
      // only the existing owner is able to change the ownership
      if (isOwner(oldPermissions, userService.getCurrentUser().getUsername()) == false) {
        throw new InvalidAuthException("Action not allowed. Only Owners are allowed to change ownership.");
      }
      // check that new owner actually exists
      userService.getUser(newPermissions.getOwner().getUsername());
      oldPermissions.setOwner(newPermissions.getOwner());
    } else {
      oldPermissions.setOwner(oldPermissions.getOwner());
    }
    oldPermissions.setReader(newPermissions.getReader());
    oldPermissions.setWriter(newPermissions.getWriter());
    oldPermissions.setReaderGroups(newPermissions.getReaderGroups());
    oldPermissions.setWriterGroups(newPermissions.getWriterGroups());
    oldPermissions.setManager(newPermissions.getManager());
    oldPermissions.setPermissionType(newPermissions.getPermissionType());
    var res = permissionsDAO.createOrUpdate(oldPermissions);
    return res;
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

    // users, apiKeys, subscriptions
    if (pathSegments.get(0).getPath().equals(Constants.USERS)) {
      // Permissions are already checked inside User- ApiKey- and SubscriptionService
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
      List.of(Constants.USERS, Constants.CONTAINERS, Constants.COLLECTIONS, Constants.USERGROUPS).contains(
        pathSegments.get(1).getPath()
      ) &&
      pathSegments.size() == 2
    ) {
      return true;
    }

    return false;
  }

  private Set<String> fetchUserNames(List<UserGroup> userGroups) {
    Set<String> ret = new HashSet<>();
    for (UserGroup userGroup : userGroups) {
      Optional<UserGroup> fullUserGroup = userGroupService.getUserGroupOptional(userGroup.getId());
      if (fullUserGroup.isPresent()) {
        for (User user : fullUserGroup.get().getUsers()) {
          ret.add(user.getUsername());
        }
      }
    }
    return ret;
  }

  private Roles getRoles(Optional<Permissions> perms, String username) {
    if (perms.isEmpty()) {
      // Legacy entity without permissions
      return new Roles(false, true, true, true);
    }
    var roles = new Roles(
      isOwner(perms.get(), username),
      isManager(perms.get(), username),
      isWriter(perms.get(), username),
      isReader(perms.get(), username)
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
    var owner = permissions.getOwner() != null
      ? userService.getUserOptional(permissions.getOwner()).orElseGet(null)
      : null;
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

      Optional<User> user = userService.getUserOptional(username);
      user.ifPresent((User u) -> result.add(u));
    }
    return result;
  }

  private List<UserGroup> fetchUserGroups(long[] userGroupIds) {
    var result = new ArrayList<UserGroup>(userGroupIds.length);
    for (var userGroupId : userGroupIds) {
      Optional<UserGroup> userGroup = userGroupService.getUserGroupOptional(userGroupId);
      if (userGroup.isPresent()) {
        result.add(userGroup.get());
      }
    }
    return result;
  }
}
