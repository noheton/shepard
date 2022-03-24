package de.dlr.shepard.neo4Core.dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.dlr.shepard.neo4Core.entities.UserGroup;
import de.dlr.shepard.security.PermissionsUtil;

public class UserGroupDAO extends GenericDAO<UserGroup> {

	@Override
	public Class<UserGroup> getEntityType() {
		return UserGroup.class;
	}

	public List<UserGroup> findAllUserGroups(String username) {
		var query = String.format("MATCH %s WHERE %s %s", getObjectPart("ug", "UserGroup", false),
				PermissionsUtil.getReadableByQuery("ug", username), getReturnPart("ug"));
		var result = new ArrayList<UserGroup>();
		for (var userGroup : findByQuery(query, Collections.emptyMap())) {
			result.add(userGroup);
		}
		return result;
	}

}