package de.dlr.shepard.neo4Core.orderBy;

import java.util.List;

public enum UserGroupAttributes implements OrderByAttribute {
	createdAt, updatedAt, name;

	private static List<UserGroupAttributes> stringList = List.of(UserGroupAttributes.name);

	private boolean isString(UserGroupAttributes userGroupAttributes) {
		return stringList.contains(userGroupAttributes);
	}

	@Override
	public boolean isString() {
		return isString(this);
	}

}
