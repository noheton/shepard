package de.dlr.shepard.neo4Core.dao;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import de.dlr.shepard.neo4Core.entities.TimeseriesReference;

public class TimeseriesReferenceDAO extends GenericDAO<TimeseriesReference> {

	/**
	 * Searches the database for references.
	 *
	 * @param dataObjectId identifies the dataObject
	 * @return a List of references
	 */
	public List<TimeseriesReference> findByDataObject(long dataObjectId) {
		String query = String.format("MATCH (d:DataObject)-[hr:has_reference]->%s WHERE ID(d)=%d ",
				getObjectPart("r", "TimeseriesReference", null), dataObjectId) + getReturnPart("r");

		var queryResult = findByQuery(query);

		List<TimeseriesReference> result = StreamSupport.stream(queryResult.spliterator(), false)
				.filter(r -> r.getDataObject() != null).filter(r -> r.getDataObject().getId().equals(dataObjectId))
				.collect(Collectors.toList());

		return result;
	}

	@Override
	public Class<TimeseriesReference> getEntityType() {
		return TimeseriesReference.class;
	}

}
