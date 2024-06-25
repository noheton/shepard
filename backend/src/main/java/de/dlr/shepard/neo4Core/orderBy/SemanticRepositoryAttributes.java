package de.dlr.shepard.neo4Core.orderBy;

import java.util.List;

public enum SemanticRepositoryAttributes implements OrderByAttribute {
	createdAt, updatedAt, name;

	private static List<SemanticRepositoryAttributes> stringList = List.of(SemanticRepositoryAttributes.name);

	private boolean isString(SemanticRepositoryAttributes semanticRepositoryAttributes) {
		return stringList.contains(semanticRepositoryAttributes);
	}

	@Override
	public boolean isString() {
		return isString(this);
	}

}
