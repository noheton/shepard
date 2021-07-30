package de.dlr.shepard.neo4Core.dao;

import java.util.ArrayList;
import java.util.List;

import de.dlr.shepard.neo4Core.entities.StructuredDataContainer;
import de.dlr.shepard.util.QueryParamHelper;

public class StructuredDataContainerDAO extends GenericDAO<StructuredDataContainer> {

	public List<StructuredDataContainer> findAllStructuredDataContainers(QueryParamHelper params) {
		String query;

		if (params.hasPagination())
			query = String.format("MATCH %s WITH c %s %s",
					getObjectPart("c", "StructuredDataContainer", params.getName()),
					getPaginationPart(params.getPagination()), getReturnPart("c"));
		else
			query = String.format("MATCH %s WITH c %s", getObjectPart("c", "StructuredDataContainer", params.getName()),
					getReturnPart("c"));
		if (params.hasOrderByAttribute())
			query = query + getOrderByPart("c", params.getOrderByAttribute(), params.getOrderDesc());

		var result = new ArrayList<StructuredDataContainer>();
		for (var container : findByQuery(query)) {
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
