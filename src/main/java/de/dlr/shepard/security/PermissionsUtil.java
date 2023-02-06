package de.dlr.shepard.security;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import de.dlr.shepard.neo4Core.entities.Permissions;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.entities.UserGroup;
import de.dlr.shepard.neo4Core.io.RolesIO;
import de.dlr.shepard.neo4Core.services.PermissionsService;
import de.dlr.shepard.neo4Core.services.UserGroupService;
import de.dlr.shepard.util.AccessType;
import de.dlr.shepard.util.Constants;
import jakarta.ws.rs.core.PathSegment;

public class PermissionsUtil {

	private PermissionsService permissionsService = new PermissionsService();
	private UserGroupService userGroupService = new UserGroupService();

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
			if (pathSegments.get(0).getPath().equals(Constants.SEARCH)
					&& ((pathSegments.get(1).getPath().equals(Constants.USERS)
							|| pathSegments.get(1).getPath().equals(Constants.CONTAINERS)) && pathSegments.size() == 2))
				return true;
			// non-numeric id
			else if (pathSegments.get(0).getPath().equals(Constants.USERS)) {
				if (pathSegments.size() <= 2 && AccessType.Read.equals(accessType))
					// it is allowed to read all users
					return true;
				else if (username.equals(idSegment))
					// it is allowed to access yourself
					return true;
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
		var perms = permissionsService.getPermissionsByEntity(entityId);
		if (perms == null)
			// No permissions
			return true;

		if (isOwner(perms, username))
			// Is owner
			return true;

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

	public RolesIO getRoles(long entityId, String username) {
		var perms = permissionsService.getPermissionsByEntity(entityId);
		if (perms == null) {
			// Legacy entity without permissions
			return new RolesIO(false, true, true, true);
		}
		var roles = new RolesIO(isOwner(perms, username), isManager(perms, username), isWriter(perms, username),
				isReader(perms, username));
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
