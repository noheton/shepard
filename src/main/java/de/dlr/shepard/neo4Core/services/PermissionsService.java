package de.dlr.shepard.neo4Core.services;

import java.util.ArrayList;
import java.util.List;

import de.dlr.shepard.neo4Core.dao.PermissionsDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.dao.UserGroupDAO;
import de.dlr.shepard.neo4Core.entities.Permissions;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.entities.UserGroup;
import de.dlr.shepard.neo4Core.io.PermissionsIO;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PermissionsService {

	private PermissionsDAO permissionsDAO = new PermissionsDAO();
	private UserDAO userDAO = new UserDAO();
	private UserGroupDAO userGroupDAO = new UserGroupDAO();

	/**
	 * Searches for permissions in Neo4j.
	 *
	 * @param entityId identifies the entity that the permissions object belongs to
	 * @return Permissions with matching entity or null
	 */
	public Permissions getPermissionsByEntity(long entityId) {
		var permissions = permissionsDAO.findByEntity(entityId);
		if (permissions == null) {
			log.error("Permissions with entity id {} is null", entityId);
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
	public Permissions createPermissions(long entityId) {
		var permissions = new Permissions();
		return permissionsDAO.createWithEntity(permissions, entityId);
	}

	/**
	 * Updates the Permissions in Neo4j
	 *
	 * @param permissions the new Permissions object
	 * @param entityId    identifies the entity
	 * @return the updated Permissions object
	 */
	public Permissions updatePermissions(PermissionsIO permissions, long entityId) {
		var owner = permissions.getOwner() != null ? userDAO.find(permissions.getOwner()) : null;
		var permissionType = permissions.getPermissionType();
		var reader = fetchUsers(permissions.getReader());
		var writer = fetchUsers(permissions.getWriter());
		var readerGroups = fetchUserGroups(permissions.getReaderGroupIds());
		var writerGroups = fetchUserGroups(permissions.getWriterGroupIds());
		var manager = fetchUsers(permissions.getManager());
		var old = getPermissionsByEntity(entityId);
		if (old == null) {
			// There is no old permissions object
			var toCreate = new Permissions(owner, reader, writer, readerGroups, writerGroups, manager,
					permissions.getPermissionType());
			return permissionsDAO.createWithEntity(toCreate, entityId);
		}
		old.setOwner(owner);
		old.setReader(reader);
		old.setWriter(writer);
		old.setReaderGroups(readerGroups);
		old.setWriterGroups(writerGroups);
		old.setManager(manager);
		old.setPermissionType(permissionType);
		return permissionsDAO.createOrUpdate(old);

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
			var userGroup = userGroupDAO.find(userGroupId);
			if (userGroup != null) {
				result.add(userGroup);
			}
		}
		return result;
	}
}
