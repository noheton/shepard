package de.dlr.shepard.neo4Core.dao;

import java.util.Collections;
import java.util.List;
import java.util.stream.StreamSupport;

import de.dlr.shepard.neo4Core.entities.URIReference;

public class URIReferenceDAO extends GenericDAO<URIReference> {

	/**
	 * Searches the database for references.
	 *
	 * @param dataObjectId identifies the dataObject
	 * @return a List of references
	 */
	public List<URIReference> findByDataObject(long dataObjectId) {
		String query = String.format("MATCH (d:DataObject)-[hr:has_reference]->%s WHERE ID(d)=%d ",
				getObjectPart("r", "URIReference", false), dataObjectId) + getReturnPart("r");

		var queryResult = findByQuery(query, Collections.emptyMap());

		List<URIReference> result = StreamSupport.stream(queryResult.spliterator(), false)
				.filter(r -> r.getDataObject() != null).filter(r -> r.getDataObject().getId().equals(dataObjectId))
				.toList();

		return result;
	}

	@Override
	public Class<URIReference> getEntityType() {
		return URIReference.class;
	}

}
