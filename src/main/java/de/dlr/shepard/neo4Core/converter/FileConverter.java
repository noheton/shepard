package de.dlr.shepard.neo4Core.converter;

import de.dlr.shepard.mongoDB.ShepardFile;

public class FileConverter extends JsonListConverter<ShepardFile> {

	@Override
	Class<ShepardFile> getEntityType() {
		return ShepardFile.class;
	}

}
