package de.dlr.shepard.neo4Core.orderBy;

import java.util.List;

public enum DataObjectAttributes implements OrderByAttribute {
	createdAt, updatedAt, name;

	private static List<DataObjectAttributes> stringList = List.of(DataObjectAttributes.name);

	private boolean isString(DataObjectAttributes dataObjectAttribute) {
		return stringList.contains(dataObjectAttribute);
	}

	@Override
	public boolean isString() {
		return isString(this);
	}
}
