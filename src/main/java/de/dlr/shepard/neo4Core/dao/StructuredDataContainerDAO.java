package de.dlr.shepard.neo4Core.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.dlr.shepard.neo4Core.entities.StructuredDataContainer;
import de.dlr.shepard.util.QueryParamHelper;

public class StructuredDataContainerDAO extends GenericDAO<StructuredDataContainer> {

	public List<StructuredDataContainer> findAllStructuredDataContainers(QueryParamHelper params) {
		String query;
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", params.getName());
		if (params.hasPagination()) {
			paramsMap.put("offset", params.getPagination().getOffset());
			paramsMap.put("size", params.getPagination().getSize());
		}
		query = String.format("MATCH %s WITH c",
				getParameterizedObjectPart("c", "StructuredDataContainer", params.hasName()));
		if (params.hasOrderByAttribute()) {
			query += " " + getOrderByPart("c", params.getOrderByAttribute(), params.getOrderDesc());
		}
		if (params.hasPagination()) {
			query += " " + getParameterizedPaginationPart();
		}
		query += " " + getReturnPart("c");
		var result = new ArrayList<StructuredDataContainer>();
		for (var container : findByQuery(query, paramsMap)) {
			if (matchName(container, params.getName())) {
				result.add(container);
			}
		}

		return result;
	}

	private boolean matchName(StructuredDataContainer container, String name) {
		if (name == null || container.getName().equalsIgnoreCase(name))
			return true;
		return false;
	}

	@Override
	public Class<StructuredDataContainer> getEntityType() {
		return StructuredDataContainer.class;
	}

}
