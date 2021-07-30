package de.dlr.shepard.neo4Core.orderBy;

import java.util.List;

public enum BasicReferenceAttributes implements OrderByAttribute {
	createdAt, createdBy, updatedAt, updatedBy, name, type;

	private static List<BasicReferenceAttributes> stringList = List.of(BasicReferenceAttributes.createdBy,
			BasicReferenceAttributes.name, BasicReferenceAttributes.updatedBy, BasicReferenceAttributes.type);

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
