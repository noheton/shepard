package de.dlr.shepard.neo4Core.dao;

import java.util.ArrayList;
import java.util.List;

import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.util.QueryParamHelper;

public class CollectionDAO extends GenericDAO<Collection> {

	@Override
	public Class<Collection> getEntityType() {
		return Collection.class;
	}

	/**
	 * Searches the database for collections.
	 * 
	 * @param params encapsulates possible parameters
	 * @return a list of collections
	 */
	public List<Collection> findAllCollections(QueryParamHelper params) {
		String query;
		if (params.hasPagination()) {
			query = String.format("MATCH %s WITH c %s %s", getObjectPart("c", "Collection", params.getName()),
					getPaginationPart(params.getPagination()), getReturnPart("c"));
		} else {
			query = String.format("MATCH %s WITH c %s", getObjectPart("c", "Collection", params.getName()),
					getReturnPart("c"));
		}

		var result = new ArrayList<Collection>();
		for (var col : findByQuery(query)) {
			if (matchName(col, params.getName())) {
				result.add(col);
			}
		}

		return result;
	}

	private boolean matchName(Collection col, String name) {
		if (name == null || col.getName().equalsIgnoreCase(name))
			return true;
		return false;
	}

}
