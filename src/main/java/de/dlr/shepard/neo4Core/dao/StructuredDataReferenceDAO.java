package de.dlr.shepard.neo4Core.dao;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import de.dlr.shepard.neo4Core.entities.StructuredDataReference;

public class StructuredDataReferenceDAO extends GenericDAO<StructuredDataReference> {

	/**
	 * Searches the database for references.
	 *
	 * @param dataObjectId identifies the dataObject
	 * @return a List of references
	 */
	public List<StructuredDataReference> findByDataObject(long dataObjectId) {
		String query = String.format("MATCH (d:DataObject)-[hr:has_reference]->%s WHERE ID(d)=%d ",
				getObjectPart("r", "StructuredDataReference", null), dataObjectId) + getReturnPart("r");

		var queryResult = findByQuery(query);

		List<StructuredDataReference> result = StreamSupport.stream(queryResult.spliterator(), false)
				.filter(r -> r.getDataObject() != null).filter(r -> r.getDataObject().getId().equals(dataObjectId))
				.collect(Collectors.toList());
		return result;
	}

	@Override
	public Class<StructuredDataReference> getEntityType() {
		return StructuredDataReference.class;
	}

}
