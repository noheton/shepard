package de.dlr.shepard.neo4Core.orderBy;

public enum BasicReferenceAttributes implements OrderByAttribute {
	createdAt, createdBy, updatedAt, updatedBy, name, type;

	private boolean isString(BasicReferenceAttributes basicReferenceAttribute) {
		switch (basicReferenceAttribute) {
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
		case type:
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
