package de.dlr.shepard.neo4Core.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.dlr.shepard.neo4Core.entities.UserGroup;
import de.dlr.shepard.security.PermissionsUtil;
import de.dlr.shepard.util.QueryParamHelper;

public class UserGroupDAO extends GenericDAO<UserGroup> {

	@Override
	public Class<UserGroup> getEntityType() {
		return UserGroup.class;
	}

	public List<UserGroup> findAllUserGroups(QueryParamHelper params, String username) {
		Map<String, Object> paramsMap = new HashMap<>();
		if (params.hasPagination()) {
			paramsMap.put("offset", params.getPagination().getOffset());
			paramsMap.put("size", params.getPagination().getSize());
		}
		var query = String.format("MATCH %s WHERE %s WITH ug", getObjectPart("ug", "UserGroup", false),
				PermissionsUtil.getReadableByQuery("ug", username));
		if (params.hasOrderByAttribute()) {
			query += " " + getOrderByPart("ug", params.getOrderByAttribute(), params.getOrderDesc());
		}
		if (params.hasPagination()) {
			query += " " + getPaginationPart();
		}
		query += " " + getReturnPart("ug");
		var result = new ArrayList<UserGroup>();
		for (var userGroup : findByQuery(query, paramsMap)) {
			result.add(userGroup);
		}
		return result;
	}

}