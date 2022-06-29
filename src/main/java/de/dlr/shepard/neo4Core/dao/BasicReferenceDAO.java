package de.dlr.shepard.neo4Core.dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import org.neo4j.ogm.model.Result;

import de.dlr.shepard.neo4Core.entities.BasicReference;
import de.dlr.shepard.util.CypherQueryHelper;
import de.dlr.shepard.util.QueryParamHelper;

public class BasicReferenceDAO extends GenericDAO<BasicReference> {

	@Override
	public Class<BasicReference> getEntityType() {
		return BasicReference.class;
	}

	/**
	 * Searches the database for references.
	 *
	 * @param dataObjectId identifies the dataObject
	 * @param params       encapsulates possible parameters
	 * @return a List of references
	 */
	public List<BasicReference> findByDataObject(long dataObjectId, QueryParamHelper params) {
		String query;
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", params.getName());
		if (params.hasPagination()) {
			paramsMap.put("offset", params.getPagination().getOffset());
			paramsMap.put("size", params.getPagination().getSize());
		}
		query = String.format("MATCH (d:DataObject)-[hr:has_reference]->%s WHERE ID(d)=%d WITH r",
				CypherQueryHelper.getObjectPart("r", "BasicReference", params.hasName()), dataObjectId);
		if (params.hasOrderByAttribute()) {
			query += " " + CypherQueryHelper.getOrderByPart("r", params.getOrderByAttribute(), params.getOrderDesc());
		}
		if (params.hasPagination()) {
			query += " " + CypherQueryHelper.getPaginationPart();
		}
		query += " " + CypherQueryHelper.getReturnPart("r");
		var result = new ArrayList<BasicReference>();
		for (var ref : findByQuery(query, paramsMap)) {
			if (matchDataObject(ref, dataObjectId) && matchName(ref, params.getName())) {
				result.add(ref);
			}
		}
		return result;
	}

	private boolean matchDataObject(BasicReference ref, long dataObjectId) {
		if (ref.getDataObject() != null && ref.getDataObject().getId().equals(dataObjectId))
			return true;
		return false;
	}

	private boolean matchName(BasicReference ref, String name) {
		if (name == null || ref.getName().equalsIgnoreCase(name))
			return true;
		return false;
	}

	public long getDataObjectId(long referenceId) {
		String query = "MATCH (d:DataObject)-[has_reference]->(r) WHERE id(r) = " + referenceId + " RETURN id(d)";
		Result idResult = session.query(query, Collections.emptyMap());
		Map<String, Object> map = idResult.iterator().next();
		Object o = map.get("id(d)");
		return (Long) o;
	}

	public List<BasicReference> getBasicReferencesByQuery(String query) {
		var queryResult = findByQuery(query, Collections.emptyMap());
		List<BasicReference> ret = StreamSupport.stream(queryResult.spliterator(), false).toList();
		return ret;
	}

}
