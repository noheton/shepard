package de.dlr.shepard.neo4Core.services;

import java.util.List;

import de.dlr.shepard.neo4Core.entities.AbstractContainer;
import de.dlr.shepard.neo4Core.io.AbstractContainerIO;
import de.dlr.shepard.util.QueryParamHelper;

public interface IContainerService<T extends AbstractContainer, S extends AbstractContainerIO> {
	List<T> getAllContainers(QueryParamHelper params, String username);

	T getContainer(long id);

	T createContainer(S containerIO, String username);

	boolean deleteContainer(long containerId, String username);

}
