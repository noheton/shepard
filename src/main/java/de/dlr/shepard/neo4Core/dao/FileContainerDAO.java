package de.dlr.shepard.neo4Core.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.dlr.shepard.neo4Core.entities.FileContainer;
import de.dlr.shepard.util.QueryParamHelper;

public class FileContainerDAO extends GenericDAO<FileContainer> {

	public List<FileContainer> findAllFileContainers(QueryParamHelper params) {
		String query;
		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("name", params.getName());
		if (params.hasPagination()) {
			paramsMap.put("offset", params.getPagination().getOffset());
			paramsMap.put("size", params.getPagination().getSize());
		}
		query = String.format("MATCH %s WITH c %s %s",
				getParameterizedObjectPart("c", "FileContainer", params.hasName()),
				getParameterizedPaginationPart(params.hasPagination()), getReturnPart("c"));
		if (params.hasOrderByAttribute())
			query = query + getOrderByPart("c", params.getOrderByAttribute(), params.getOrderDesc());

		var result = new ArrayList<FileContainer>();
		for (var container : findByQuery(query, paramsMap)) {
			if (matchName(container, params.getName())) {
				result.add(container);
			}
		}

		return result;
	}

	private boolean matchName(FileContainer container, String name) {
		if (name == null || container.getName().equalsIgnoreCase(name))
			return true;
		return false;
	}

	@Override
	public Class<FileContainer> getEntityType() {
		return FileContainer.class;
	}

}
