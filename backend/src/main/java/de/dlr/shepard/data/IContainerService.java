package de.dlr.shepard.data;

import de.dlr.shepard.common.neo4j.entities.BasicContainer;
import de.dlr.shepard.common.neo4j.io.BasicContainerIO;
import de.dlr.shepard.common.util.QueryParamHelper;
import java.util.List;

public interface IContainerService<T extends BasicContainer, S extends BasicContainerIO> {
  List<T> getAllContainers(QueryParamHelper params, String username);

  T getContainer(long id);

  T createContainer(S containerIO, String username);

  boolean deleteContainer(long containerId, String username);
}
