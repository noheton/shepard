package de.dlr.shepard.neo4Core.orderBy;

import java.util.List;

public enum DataObjectAttributes implements OrderByAttribute {
	createdAt, createdBy, updatedAt, updatedBy, name, parentId;

	private static List<DataObjectAttributes> stringList = List.of(DataObjectAttributes.createdBy,
			DataObjectAttributes.name, DataObjectAttributes.updatedBy);

	private boolean isString(DataObjectAttributes dataObjectAttribute) {
		if (stringList.contains(dataObjectAttribute))
			return true;
		return false;
	}

	@Override
	public boolean isString() {
		return isString(this);
	}
}
