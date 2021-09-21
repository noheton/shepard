package de.dlr.shepard.neo4Core.orderBy;

import java.util.List;

public enum ContainerAttributes implements OrderByAttribute {
	createdAt, updatedAt, name;

	private static List<ContainerAttributes> stringList = List.of(ContainerAttributes.name);

	private boolean isString(ContainerAttributes containerAttribute) {
		if (stringList.contains(containerAttribute))
			return true;
		return false;
	}

	@Override
	public boolean isString() {
		return isString(this);
	}
}
