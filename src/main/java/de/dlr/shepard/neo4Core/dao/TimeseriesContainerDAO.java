package de.dlr.shepard.neo4Core.dao;

import de.dlr.shepard.neo4Core.entities.TimeseriesContainer;

public class TimeseriesContainerDAO extends GenericDAO<TimeseriesContainer> {

	@Override
	public Class<TimeseriesContainer> getEntityType() {
		return TimeseriesContainer.class;
	}

}
