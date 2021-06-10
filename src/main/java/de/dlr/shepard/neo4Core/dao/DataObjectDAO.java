package de.dlr.shepard.neo4Core.dao;

import java.util.ArrayList;
import java.util.List;

import de.dlr.shepard.neo4Core.entities.DataObject;
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
			return findByParentAndName(collectionId, params.getParentId(), params.getPagination(), params.getName());
		} else {
			return findByName(collectionId, params.getPagination(), params.getName());
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
	private List<DataObject> findByName(long collectionId, PaginationHelper page, String name) {
		String query = String.format("MATCH (c:Collection)-[hdo:has_dataobject]->%s WHERE ID(c)=%d WITH d %s %s",
				getObjectPart("d", "DataObject", name), collectionId, getPaginationPart(page), getReturnPart("d"));

		var result = new ArrayList<DataObject>();
		for (var obj : findByQuery(query)) {
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
	private List<DataObject> findByParentAndName(long collectionId, long parentId, PaginationHelper page, String name) {
		String query;
		if (parentId == -1) {
			query = String.format(
					"MATCH (c:Collection)-[hdo:has_dataobject]->%s "
							+ "WHERE ID(c)=%d AND NOT (d)<-[:has_child]-(:DataObject) WITH d %s %s",
					getObjectPart("d", "DataObject", name), collectionId, getPaginationPart(page), getReturnPart("d"));
		} else {
			query = String.format(
					"MATCH (c:Collection)-[hdo:has_dataobject]->(parent:DataObject)-[hc:has_child]->%s "
							+ "WHERE ID(c)=%d AND ID(parent)=%d WITH child %s %s",
					getObjectPart("child", "DataObject", name), collectionId, parentId, getPaginationPart(page),
					getReturnPart("child"));
		}

		var result = new ArrayList<DataObject>();
		for (var obj : findByQuery(query)) {
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
		if (obj.getParent() != null && obj.getParent().getId().equals(parentId))
			return true;
		else if (obj.getParent() == null && parentId == -1)
			return true;
		return false;
	}

	private boolean matchCollection(DataObject obj, long collectionId) {
		if (obj.getCollection() != null && obj.getCollection().getId().equals(collectionId))
			return true;
		return false;
	}
}
