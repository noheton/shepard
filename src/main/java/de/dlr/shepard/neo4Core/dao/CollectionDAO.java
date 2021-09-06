package de.dlr.shepard.neo4Core.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", params.getName());
		if (params.hasPagination()) {
			paramsMap.put("offset", params.getPagination().getOffset());
			paramsMap.put("size", params.getPagination().getSize());
		}
		query = String.format("MATCH %s WITH c", getParameterizedObjectPart("c", "Collection", params.hasName()));
		if (params.hasOrderByAttribute()) {
			query += " " + getOrderByPart("c", params.getOrderByAttribute(), params.getOrderDesc());
		}
		if (params.hasPagination()) {
			query += " " + getParameterizedPaginationPart();
		}
		query += " " + getReturnPart("c");
		var result = new ArrayList<Collection>();
		for (var col : findByQuery(query, paramsMap)) {
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
