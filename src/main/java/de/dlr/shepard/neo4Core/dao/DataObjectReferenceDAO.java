package de.dlr.shepard.neo4Core.dao;

import java.util.Collections;
import java.util.List;
import java.util.stream.StreamSupport;

import de.dlr.shepard.neo4Core.entities.DataObjectReference;
import de.dlr.shepard.util.CypherQueryHelper;

public class DataObjectReferenceDAO extends GenericDAO<DataObjectReference> {

	/**
	 * Searches the database for references.
	 *
	 * @param dataObjectId identifies the dataObject
	 * @return a List of references
	 */
	public List<DataObjectReference> findByDataObject(long dataObjectId) {
		String query = String.format("MATCH (d:DataObject)-[hr:has_reference]->%s WHERE ID(d)=%d ",
				CypherQueryHelper.getObjectPart("r", "DataObjectReference", false), dataObjectId)
				+ CypherQueryHelper.getReturnPart("r");

		var queryResult = findByQuery(query, Collections.emptyMap());

		List<DataObjectReference> result = StreamSupport.stream(queryResult.spliterator(), false)
				.filter(r -> r.getDataObject() != null).filter(r -> r.getDataObject().getId().equals(dataObjectId))
				.toList();

		return result;
	}

	@Override
	public Class<DataObjectReference> getEntityType() {
		return DataObjectReference.class;
	}

}
