package de.dlr.shepard.neo4Core.orderBy;

public enum CollectionAttributes implements OrderByAttribute {
	createdAt, createdBy, updatedAt, updatedBy, name;

	public boolean isString(CollectionAttributes collectionAttribute) {
		switch (collectionAttribute) {
		case createdAt:
			return false;
		case updatedAt:
			return false;
		case createdBy:
			return true;
		case name:
			return true;
		case updatedBy:
			return true;
		default:
			return false;
		}
	}

	@Override
	public boolean isString() {
		return isString(this);
	}
}
