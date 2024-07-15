package de.dlr.shepard.neo4Core.orderBy;

import java.util.List;

public enum ContainerAttributes implements OrderByAttribute {
	createdAt, updatedAt, name;

	private static List<ContainerAttributes> stringList = List.of(ContainerAttributes.name);

	private boolean isString(ContainerAttributes containerAttribute) {
		return stringList.contains(containerAttribute);
	}

	@Override
	public boolean isString() {
		return isString(this);
	}
}
