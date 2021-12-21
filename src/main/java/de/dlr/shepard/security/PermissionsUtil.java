package de.dlr.shepard.security;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import de.dlr.shepard.neo4Core.services.PermissionsService;
import de.dlr.shepard.util.AccessType;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.PermissionType;
import jakarta.ws.rs.core.PathSegment;

public class PermissionsUtil {

	private PermissionsService permissionsService = new PermissionsService();

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
			// non-numeric id
			if (pathSegments.get(0).getPath().equals(Constants.USERS)) {
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

		if (perms.getOwner() != null && username.equals(perms.getOwner().getUsername()))
			// Is owner
			return true;

		if (AccessType.Manage.equals(accessType)) {
			return perms.getManager().stream().anyMatch(u -> username.equals(u.getUsername()));
		} else if (AccessType.Read.equals(accessType)) {
			return PermissionType.Public.equals(perms.getPermissionType())
					|| PermissionType.PublicReadable.equals(perms.getPermissionType())
					|| perms.getReader().stream().anyMatch(u -> username.equals(u.getUsername()));
		} else if (AccessType.Write.equals(accessType)) {
			return PermissionType.Public.equals(perms.getPermissionType())
					|| perms.getWriter().stream().anyMatch(u -> username.equals(u.getUsername()));
		}

		return false;
	}
}
