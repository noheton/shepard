package de.dlr.shepard.neo4Core.dao;

import java.util.Collections;
import java.util.List;
import java.util.stream.StreamSupport;

import de.dlr.shepard.neo4Core.entities.StructuredDataReference;
import de.dlr.shepard.util.TraversalRules;

public class StructuredDataReferenceDAO extends GenericDAO<StructuredDataReference> {

	/**
	 * Searches the database for references.
	 *
	 * @param dataObjectId identifies the dataObject
	 * @return a List of references
	 */
	public List<StructuredDataReference> findByDataObject(long dataObjectId) {
		String query = String.format("MATCH (d:DataObject)-[hr:has_reference]->%s WHERE ID(d)=%d ",
				getObjectPart("r", "StructuredDataReference", false), dataObjectId) + getReturnPart("r");
		var queryResult = findByQuery(query, Collections.emptyMap());
		List<StructuredDataReference> result = StreamSupport.stream(queryResult.spliterator(), false)
				.filter(r -> r.getDataObject() != null).filter(r -> r.getDataObject().getId().equals(dataObjectId))
				.toList();
		return result;
	}

	@Override
	public Class<StructuredDataReference> getEntityType() {
		return StructuredDataReference.class;
	}

	public List<StructuredDataReference> findReachableReferences(TraversalRules traversalRule, long startId) {
		String query = getSearchForReachableReferencesQuery(traversalRule, startId);
		var queryResult = findByQuery(query, Collections.emptyMap());
		List<StructuredDataReference> ret = StreamSupport.stream(queryResult.spliterator(), false).toList();
		return ret;
	}

	public List<StructuredDataReference> findReachableReferences(long startId) {
		String query = getSearchForReachableReferencesQuery(startId);
		var queryResult = findByQuery(query, Collections.emptyMap());
		List<StructuredDataReference> ret = StreamSupport.stream(queryResult.spliterator(), false).toList();
		return ret;
	}
}
