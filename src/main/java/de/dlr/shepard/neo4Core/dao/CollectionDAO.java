package de.dlr.shepard.neo4Core.dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.neo4Core.entities.User;
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
		query = String.format("MATCH %s WITH c", getObjectPart("c", "Collection", params.hasName()));
		if (params.hasOrderByAttribute()) {
			query += " " + getOrderByPart("c", params.getOrderByAttribute(), params.getOrderDesc());
		}
		if (params.hasPagination()) {
			query += " " + getPaginationPart();
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

	/**
	 * Delete collection and all related dataObjects and references
	 *
	 * @param id        identifies the collection
	 * @param updatedBy current date
	 * @param updatedAt current user
	 * @return whether the deletion was successful or not
	 */
	public boolean deleteCollection(long id, User updatedBy, Date updatedAt) {
		var collection = find(id);
		collection.setUpdatedBy(updatedBy);
		collection.setUpdatedAt(updatedAt);
		collection.setDeleted(true);
		createOrUpdate(collection);
		String query = String
				.format("MATCH (c:Collection) WHERE ID(c) = %d OPTIONAL MATCH (c)-[:has_dataobject]->(d:DataObject) "
						+ "OPTIONAL MATCH (d)-[:has_reference]->(r:BasicReference) "
						+ "FOREACH (n in [c,d,r] | SET n.deleted = true)", id);
		var result = runQuery(query, Collections.emptyMap());
		return result;
	}

	private boolean matchName(Collection col, String name) {
		if (name == null || col.getName().equalsIgnoreCase(name))
			return true;
		return false;
	}

}
