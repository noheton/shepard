package de.dlr.shepard.security;

import java.util.HashSet;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.entities.UserGroup;
import de.dlr.shepard.neo4Core.services.PermissionsService;
import de.dlr.shepard.neo4Core.services.UserGroupService;
import de.dlr.shepard.util.AccessType;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.PermissionType;
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
					|| perms.getReader().stream().anyMatch(u -> username.equals(u.getUsername()))
					|| fetchUserNames(perms.getReaderGroups()).contains(username);
		} else if (AccessType.Write.equals(accessType)) {
			return PermissionType.Public.equals(perms.getPermissionType())
					|| perms.getWriter().stream().anyMatch(u -> username.equals(u.getUsername()))
					|| fetchUserNames(perms.getWriterGroups()).contains(username);
		}

		return false;
	}

	private HashSet<String> fetchUserNames(List<UserGroup> UserGroups) {
		HashSet<String> ret = new HashSet<String>();
		for (UserGroup userGroup : UserGroups) {
			UserGroup fullUserGroup = userGroupService.getUserGroup(userGroup.getId());
			for (User user : fullUserGroup.getUsers()) {
				ret.add(user.getUsername());
			}
		}
		return ret;
	}

	public static String getReadableByQuery(String variable, String username) {
		String ret = String.format(
				"""
						(NOT exists((%s)-[:has_permissions]->(:Permissions)) \
						OR exists((%s)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: "%s" })) \
						OR exists((%s)-[:has_permissions]->(:Permissions {permissionType: "Public"})) \
						OR exists((%s)-[:has_permissions]->(:Permissions {permissionType: "PublicReadable"})) \
						OR exists((%s)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: "%s"})))""",
				variable, variable, username, variable, variable, variable, username);
		return ret;
	}
}
