package de.dlr.shepard.neo4Core.dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.User;
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
	 * Delete dataObject and all related references
	 *
	 * @param id        identifies the dataObject
	 * @param updatedBy current date
	 * @param updatedAt current user
	 * @return whether the deletion was successful or not
	 */
	public boolean deleteDataObject(long id, User updatedBy, Date updatedAt) {
		var dataObject = find(id);
		dataObject.setUpdatedBy(updatedBy);
		dataObject.setUpdatedAt(updatedAt);
		dataObject.setDeleted(true);
		createOrUpdate(dataObject);
		String query = String
				.format("MATCH (d:DataObject) WHERE ID(d) = %d OPTIONAL MATCH (d)-[:has_reference]->(r:BasicReference) "
						+ "FOREACH (n in [d,r] | SET n.deleted = true)", id);
		var result = runQuery(query, Collections.emptyMap());
		return result;
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
		String query = String.format("MATCH (c:Collection)-[hdo:has_dataobject]->%s WHERE ID(c)=%d WITH d",
				getObjectPart("d", "DataObject", name != null), collectionId);
		if (orderByAttribute != null) {
			query += " " + getOrderByPart("d", orderByAttribute, desc);
		}
		if (page != null) {
			query += " " + getPaginationPart();
		}
		query += " " + getReturnPart("d");
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
							+ "WHERE ID(c)=%d AND NOT EXISTS((d)<-[:has_child]-(:DataObject {deleted: false})) WITH d",
					getObjectPart("d", "DataObject", name != null), collectionId);
		} else {
			query = String.format(
					"MATCH (c:Collection)-[hdo:has_dataobject]->(parent:DataObject)-[hc:has_child]->%s "
							+ "WHERE ID(c)=%d AND ID(parent)=%d WITH d",
					getObjectPart("d", "DataObject", name != null), collectionId, parentId);
		}
		if (orderByAttribute != null) {
			query += " " + getOrderByPart("d", orderByAttribute, desc);
		}
		if (page != null) {
			query += " " + getPaginationPart();
		}
		query += " " + getReturnPart("d");
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
			return obj.getParent() == null || obj.getParent().isDeleted();
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
