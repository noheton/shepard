package de.dlr.shepard.neo4Core.orderBy;

import java.util.List;

public enum CollectionAttributes implements OrderByAttribute {
	createdAt, createdBy, updatedAt, updatedBy, name;

	private static List<CollectionAttributes> stringList = List.of(CollectionAttributes.createdBy,
			CollectionAttributes.name, CollectionAttributes.updatedBy);

	private boolean isString(CollectionAttributes collectionAttribute) {
		if (stringList.contains(collectionAttribute))
			return true;
		return false;
	}

	@Override
	public boolean isString() {
		return isString(this);
	}
}
