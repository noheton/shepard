package de.dlr.shepard.neo4Core.converter;

import de.dlr.shepard.mongoDB.StructuredData;

public class StructuredDataConverter extends JsonListConverter<StructuredData> {

	@Override
	Class<StructuredData> getEntityType() {
		return StructuredData.class;
	}
}
