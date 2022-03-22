package de.dlr.shepard.neo4Core.dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.User;
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

		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", params.getName());
		if (params.hasPagination()) {
			paramsMap.put("offset", params.getPagination().getOffset());
			paramsMap.put("size", params.getPagination().getSize());
		}
		String match = "MATCH (c:Collection)-[hdo:has_dataobject]->"
				+ getObjectPart("d", "DataObject", params.hasName());
		String where = " WHERE ID(c)=" + collectionId;

		if (params.hasParentId()) {
			if (params.getParentId() == -1) {
				where += " AND NOT EXISTS((d)<-[:has_child]-(:DataObject {deleted: FALSE}))";
			} else {
				match += "<-[:has_child]-(parent:DataObject {deleted: FALSE})";
				where += " AND ID(parent)=" + params.getParentId();
			}
		}

		if (params.hasPredecessorId()) {
			if (params.getPredecessorId() == -1) {
				where += " AND NOT EXISTS((d)<-[:has_successor]-(:DataObject {deleted: FALSE}))";
			} else {
				match += "<-[:has_successor]-(predecessor:DataObject {deleted: FALSE})";
				where += " AND ID(predecessor)=" + params.getPredecessorId();
			}
		}
		if (params.hasSuccessorId()) {
			if (params.getSuccessorId() == -1) {
				where += " AND NOT EXISTS((d)-[:has_successor]->(:DataObject {deleted: FALSE}))";
			} else {
				match += "-[:has_successor]->(successor:DataObject {deleted: FALSE})";
				where += " AND ID(successor)=" + params.getSuccessorId();
			}
		}

		String query = match + where + " WITH d";
		if (params.hasOrderByAttribute()) {
			query += " " + getOrderByPart("d", params.getOrderByAttribute(), params.getOrderDesc());
		}
		if (params.hasPagination()) {
			query += " " + getPaginationPart();
		}
		query += " " + getReturnPart("d");
		var result = new ArrayList<DataObject>();
		for (var obj :

		findByQuery(query, paramsMap)) {
			List<DataObject> parentList = obj.getParent() != null ? List.of(obj.getParent()) : Collections.emptyList();
			if (matchCollection(obj, collectionId) && matchName(obj, params.getName())
					&& matchRelated(parentList, params.getParentId())
					&& matchRelated(obj.getSuccessors(), params.getSuccessorId())
					&& matchRelated(obj.getPredecessors(), params.getPredecessorId())) {
				result.add(obj);
			}
		}

		return result;
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

	private boolean matchName(DataObject obj, String name) {
		if (name == null || name.equalsIgnoreCase(obj.getName()))
			return true;
		return false;
	}

	private boolean matchRelated(List<DataObject> related, Long id) {
		if (id == null) {
			return true;
		} else if (id == -1) {
			// return true if there is no related object or all objects are deleted
			return related.stream().allMatch(DataObject::isDeleted);
		} else {
			// return true if at least one related object that is not deleted matches the ID
			return related.stream().anyMatch(d -> !d.isDeleted() && d.getId().equals(id));
		}
	}

	private boolean matchCollection(DataObject obj, long collectionId) {
		if (obj.getCollection() != null && obj.getCollection().getId().equals(collectionId))
			return true;
		return false;
	}

	public List<DataObject> getDataObjectsByQuery(String query) {
		var queryResult = findByQuery(query, Collections.emptyMap());
		List<DataObject> ret = StreamSupport.stream(queryResult.spliterator(), false).toList();
		return ret;
	}
}
