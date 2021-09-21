package de.dlr.shepard.neo4Core.orderBy;

import java.util.List;

public enum BasicReferenceAttributes implements OrderByAttribute {
	createdAt, updatedAt, name, type;

	private static List<BasicReferenceAttributes> stringList = List.of(BasicReferenceAttributes.name,
			BasicReferenceAttributes.type);

	private boolean isString(BasicReferenceAttributes referenceAttribute) {
		if (stringList.contains(referenceAttribute))
			return true;
		return false;
	}

	@Override
	public boolean isString() {
		return isString(this);
	}
}
