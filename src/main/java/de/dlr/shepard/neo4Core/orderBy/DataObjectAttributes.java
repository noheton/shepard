package de.dlr.shepard.neo4Core.orderBy;

public enum DataObjectAttributes implements OrderByAttribute {
	createdAt, createdBy, updatedAt, updatedBy, name, parentId;

	public boolean isString(DataObjectAttributes dataObjectAttribute) {
		switch (dataObjectAttribute) {
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
