package de.dlr.shepard.neo4Core.dao;

import java.util.ArrayList;
import java.util.List;

import de.dlr.shepard.neo4Core.entities.TimeseriesContainer;
import de.dlr.shepard.util.QueryParamHelper;

public class TimeseriesContainerDAO extends GenericDAO<TimeseriesContainer> {

	public List<TimeseriesContainer> findAllTimeseriesContainers(QueryParamHelper params) {
		String query;

		if (params.hasPagination())
			query = String.format("MATCH %s WITH c %s %s", getObjectPart("c", "TimeseriesContainer", params.getName()),
					getPaginationPart(params.getPagination()), getReturnPart("c"));
		else
			query = String.format("MATCH %s WITH c %s", getObjectPart("c", "TimeseriesContainer", params.getName()),
					getReturnPart("c"));
		if (params.hasOrderByAttribute())
			query = query + getOrderByPart("c", params.getOrderByAttribute(), params.getOrderDesc());

		var result = new ArrayList<TimeseriesContainer>();
		for (var container : findByQuery(query)) {
			if (matchName(container, params.getName())) {
				result.add(container);
			}
		}

		return result;
	}

	private boolean matchName(TimeseriesContainer container, String name) {
		if (name == null || container.getName().equalsIgnoreCase(name))
			return true;
		return false;
	}

	@Override
	public Class<TimeseriesContainer> getEntityType() {
		return TimeseriesContainer.class;
	}

}
