package de.dlr.shepard.neo4Core.orderBy;

import java.util.List;

public enum ContainerAttributes implements OrderByAttribute {
	createdAt, createdBy, updatedAt, updatedBy, name;

	private static List<ContainerAttributes> stringList = List.of(ContainerAttributes.createdBy,
			ContainerAttributes.name, ContainerAttributes.updatedBy);

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
