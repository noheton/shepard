package de.dlr.shepard.neo4Core.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.dlr.shepard.neo4Core.entities.BasicReference;
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
		query = String.format("MATCH (d:DataObject)-[hr:has_reference]->%s WHERE ID(d)=%d WITH r %s %s",
				getParameterizedObjectPart("r", "BasicReference", params.hasName()), dataObjectId,
				getParameterizedPaginationPart(params.hasPagination()), getReturnPart("r"));
		if (params.hasOrderByAttribute())
			query = query + getOrderByPart("r", params.getOrderByAttribute(), params.getOrderDesc());
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

}
