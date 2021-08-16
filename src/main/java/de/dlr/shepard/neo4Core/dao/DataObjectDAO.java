package de.dlr.shepard.neo4Core.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.orderBy.OrderByAttribute;
import de.dlr.shepard.util.PaginationHelper;
import de.dlr.shepard.util.QueryParamHelper;

public class DataObjectDAO extends GenericDAO<DataObject> {

	@Override
	public Class<DataObject> getEntityType() {
		return DataObject.class;
	}

	/**
	 * Searches the database for DataObjects.
	 *
	 * @param collectionId identifies the Collection
	 * @param params       encapsulates possible parameters
	 * @return a List of DataObjects
	 */
	public List<DataObject> findByCollection(long collectionId, QueryParamHelper params) {
		if (params.hasParentId()) {
			return findByParentAndName(collectionId, params.getParentId(), params.getPagination(), params.getName(),
					params.getOrderByAttribute(), params.getOrderDesc());
		} else {
			return findByName(collectionId, params.getPagination(), params.getName(), params.getOrderByAttribute(),
					params.getOrderDesc());
		}
	}

	/**
	 * Searches the database for DataObjects.
	 *
	 * @param collectionId identifies the collection
	 * @param page         which page should be fetched
	 * @param name         filter dataObjects by name, this is allowed to be null
	 * @return a List of DataObjects
	 */
	private List<DataObject> findByName(long collectionId, PaginationHelper page, String name,
			OrderByAttribute orderByAttribute, Boolean desc) {
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", name);
		if (page != null) {
			paramsMap.put("offset", page.getOffset());
			paramsMap.put("size", page.getSize());
		}
		String query = String.format("MATCH (c:Collection)-[hdo:has_dataobject]->%s WHERE ID(c)=%d WITH d %s %s",
				getParameterizedObjectPart("d", "DataObject", name != null), collectionId,
				getParameterizedPaginationPart(page != null), getReturnPart("d"));
		if (orderByAttribute != null) {
			query = query + getOrderByPart("d", orderByAttribute, desc);
		}
		var result = new ArrayList<DataObject>();
		for (var obj : findByQuery(query, paramsMap)) {
			if (matchCollection(obj, collectionId) && matchName(obj, name)) {
				result.add(obj);
			}
		}

		return result;
	}

	/**
	 * Searches the database for DataObjects.
	 *
	 * @param collectionId identifies the collection
	 * @param parentId     identifies the parent step
	 * @param page         which page should be fetched
	 * @param name         filter dataObjects by name, this is allowed to be null
	 * @return a List of DataObjects
	 */
	private List<DataObject> findByParentAndName(long collectionId, long parentId, PaginationHelper page, String name,
			OrderByAttribute orderByAttribute, Boolean desc) {
		String query;
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", name);
		if (page != null) {
			paramsMap.put("offset", page.getOffset());
			paramsMap.put("size", page.getSize());
		}
		if (parentId == -1) {
			query = String.format(
					"MATCH (c:Collection)-[hdo:has_dataobject]->%s "
							+ "WHERE ID(c)=%d AND NOT (d)<-[:has_child]-(:DataObject {deleted: false}) WITH d %s %s",
					getParameterizedObjectPart("d", "DataObject", name != null), collectionId,
					getParameterizedPaginationPart(page != null), getReturnPart("d"));
		} else {
			query = String.format(
					"MATCH (c:Collection)-[hdo:has_dataobject]->(parent:DataObject)-[hc:has_child]->%s "
							+ "WHERE ID(c)=%d AND ID(parent)=%d WITH child %s %s",
					getParameterizedObjectPart("child", "DataObject", name != null), collectionId, parentId,
					getParameterizedPaginationPart(page != null), getReturnPart("child"));
		}
		if (orderByAttribute != null) {
			query = query + getOrderByPart("d", orderByAttribute, desc);
		}
		var result = new ArrayList<DataObject>();
		for (var obj : findByQuery(query, paramsMap)) {
			if (matchCollection(obj, collectionId) && matchParent(obj, parentId) && matchName(obj, name)) {
				result.add(obj);
			}
		}

		return result;
	}

	private boolean matchName(DataObject obj, String name) {
		if (name == null || obj.getName().equalsIgnoreCase(name))
			return true;
		return false;
	}

	private boolean matchParent(DataObject obj, long parentId) {
		if (parentId == -1) {
			// return true if parent is null or parent is deleted
			return obj.getParent() == null || (obj.getParent() != null && obj.getParent().isDeleted());
		} else {
			// return true if parent is not deleted and parent id equals parentId
			return obj.getParent() != null && !obj.getParent().isDeleted() && obj.getParent().getId().equals(parentId);
		}
	}

	private boolean matchCollection(DataObject obj, long collectionId) {
		if (obj.getCollection() != null && obj.getCollection().getId().equals(collectionId))
			return true;
		return false;
	}
}
