package de.dlr.shepard.security;

import de.dlr.shepard.labJournal.services.LabJournalEntryService;
import de.dlr.shepard.neo4Core.entities.Permissions;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.entities.UserGroup;
import de.dlr.shepard.neo4Core.io.RolesIO;
import de.dlr.shepard.neo4Core.services.DataObjectService;
import de.dlr.shepard.neo4Core.services.PermissionsService;
import de.dlr.shepard.neo4Core.services.UserGroupService;
import de.dlr.shepard.util.AccessType;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.PermissionType;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.PathSegment;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

@RequestScoped
public class PermissionsUtil {

  private PermissionsService permissionsService;
  private UserGroupService userGroupService;
  private LabJournalEntryService labJournalService;
  private DataObjectService dataObjectService;

  PermissionsUtil() {}

  @Inject
  public PermissionsUtil(
    PermissionsService permissionsService,
    UserGroupService userGroupService,
    LabJournalEntryService labJournalService,
    DataObjectService dataObjectService
  ) {
    this.permissionsService = permissionsService;
    this.userGroupService = userGroupService;
    this.labJournalService = labJournalService;
    this.dataObjectService = dataObjectService;
  }

  public boolean isAllowed(ContainerRequestContext requestContext, AccessType accessType, String userName) {
    List<PathSegment> pathSegments = requestContext.getUriInfo().getPathSegments();
    var idSegment = pathSegments.size() > 1 ? pathSegments.get(1).getPath() : null;
    // Check initially for lab journal entries requests, then pass it to the generic check
    if (pathSegments.get(0).getPath().equals(Constants.LAB_JOURNAL_ENTRIES)) {
      String dataObjectId = requestContext.getUriInfo().getQueryParameters().getFirst(Constants.DATA_OBJECT_ID);
      // If the labjournalEntry request has objectId parameter [in GET/labJournals and POST /labJournals]
      if (dataObjectId != null && !dataObjectId.isEmpty() && StringUtils.isNumeric(dataObjectId)) {
        Long collectionId = dataObjectService.getCollectionId(Long.parseLong(dataObjectId));
        if (collectionId == null) return true;
        return isAllowed(collectionId, accessType, userName);
      }
      if (idSegment == null || idSegment.isBlank()) {
        return true;
      }

      Long labJournalId = Long.parseLong(idSegment);
      Long collectionId = labJournalService.getCollectionId(labJournalId);
      if (collectionId == null) return true;
      // If the labjournalEntry request has labjournalId as path segment [in GET/labJournals/{labjournalId}, PUT/labJournals/{labjournalId}, DELETE/labJournals/{labjournalId} ]
      return isAllowed(collectionId, accessType, userName);
    }
    return isAllowed(requestContext.getUriInfo().getPathSegments(), accessType, userName);
  }

  /**
   * Check whether a request is allowed or not
   *
   * @param pathSegments the path segments of the request uri
   * @param accessType   the access type (read, write, manage)
   * @param username     the user that wants access
   * @return whether the request is allowed or not
   */
  public boolean isAllowed(List<PathSegment> pathSegments, AccessType accessType, String username) {
    var idSegment = pathSegments.size() > 1 ? pathSegments.get(1).getPath() : null;
    if (idSegment == null || idSegment.isBlank()) {
      // No id in path
      return true;
    } else if (!StringUtils.isNumeric(idSegment)) {
      // usersearch and containersearch
      if (
        pathSegments.get(0).getPath().equals(Constants.SEARCH) &&
        List.of(Constants.USERS, Constants.CONTAINERS).contains(pathSegments.get(1).getPath()) &&
        pathSegments.size() == 2
      ) return true;
      // non-numeric id
      else if (pathSegments.get(0).getPath().equals(Constants.USERS)) {
        if (pathSegments.size() <= 2 && AccessType.Read.equals(accessType)) return true; // it is allowed to read all users
        else if (username.equals(idSegment)) return true; // it is allowed to access yourself
      }
      return false;
    }

    var entityId = Long.parseLong(idSegment);
    return isAllowed(entityId, accessType, username);
  }

  /**
   * Check whether a request is allowed or not
   *
   * @param entityId   the entity that is to be accessed
   * @param accessType the access type (read, write, manage)
   * @param username   the user that wants access
   * @return whether the access is allowed or not
   */
  public boolean isAllowed(long entityId, AccessType accessType, String username) {
    var perms = permissionsService.getPermissionsByNeo4jId(entityId);
    if (perms == null) return true; // No permissions

    if (isOwner(perms, username)) return true; // Is owner

    if (AccessType.Manage.equals(accessType)) {
      return isManager(perms, username);
    } else if (AccessType.Read.equals(accessType)) {
      return isReader(perms, username);
    } else if (AccessType.Write.equals(accessType)) {
      return isWriter(perms, username);
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

  public RolesIO getRolesByNeo4jId(long id, String username) {
    var perms = permissionsService.getPermissionsByNeo4jId(id);
    return getRoles(perms, username);
  }

  public RolesIO getRolesByShepardId(long shepardId, String username) {
    var perms = permissionsService.getPermissionsByShepardId(shepardId);
    return getRoles(perms, username);
  }

  private RolesIO getRoles(Permissions perms, String username) {
    if (perms == null) {
      // Legacy entity without permissions
      return new RolesIO(false, true, true, true);
    }
    var roles = new RolesIO(
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
}
