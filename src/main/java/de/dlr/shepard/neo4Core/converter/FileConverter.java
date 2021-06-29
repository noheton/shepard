package de.dlr.shepard.neo4Core.converter;

import de.dlr.shepard.mongoDB.File;

public class FileConverter extends JsonListConverter<File> {

	@Override
	Class<File> getEntityType() {
		return File.class;
	}

}
