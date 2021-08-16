package de.dlr.shepard.neo4Core.dao;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import de.dlr.shepard.neo4Core.entities.FileReference;

public class FileReferenceDAO extends GenericDAO<FileReference> {

	public List<FileReference> findByDataObject(long dataObjectId) {
		String query = String.format("MATCH (d:DataObject)-[hr:has_reference]->%s WHERE ID(d)=%d ",
				getParameterizedObjectPart("r", "FileReference", false), dataObjectId) + getReturnPart("r");

		var queryResult = findByQuery(query, Collections.emptyMap());

		List<FileReference> result = StreamSupport.stream(queryResult.spliterator(), false)
				.filter(r -> r.getDataObject() != null).filter(r -> r.getDataObject().getId().equals(dataObjectId))
				.collect(Collectors.toList());
		return result;
	}

	@Override
	public Class<FileReference> getEntityType() {
		return FileReference.class;
	}

}
