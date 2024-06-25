package de.dlr.shepard.neo4Core.services;

import java.util.List;

import de.dlr.shepard.neo4Core.entities.BasicContainer;
import de.dlr.shepard.neo4Core.io.BasicContainerIO;
import de.dlr.shepard.util.QueryParamHelper;

public interface IContainerService<T extends BasicContainer, S extends BasicContainerIO> {
	List<T> getAllContainers(QueryParamHelper params, String username);

	T getContainer(long id);

	T createContainer(S containerIO, String username);

	boolean deleteContainer(long containerId, String username);

}
