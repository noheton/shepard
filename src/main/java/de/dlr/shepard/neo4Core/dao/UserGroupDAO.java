package de.dlr.shepard.neo4Core.dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.dlr.shepard.neo4Core.entities.UserGroup;

public class UserGroupDAO extends GenericDAO<UserGroup> {

	@Override
	public Class<UserGroup> getEntityType() {
		return UserGroup.class;
	}

	public List<UserGroup> findAllUserGroups(String username) {
		var query = String.format("MATCH %s %s %s", getObjectPart("ug", "UserGroup", false),
				getReadableByPart("ug", username), getReturnPart("ug"));
		var result = new ArrayList<UserGroup>();
		for (var userGroup : findByQuery(query, Collections.emptyMap())) {
			result.add(userGroup);
		}
		return result;
	}

}