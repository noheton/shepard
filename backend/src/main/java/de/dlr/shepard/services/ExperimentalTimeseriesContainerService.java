package de.dlr.shepard.services;

import de.dlr.shepard.influxDB.Timeseries;
import de.dlr.shepard.influxDB.TimeseriesPayload;
import de.dlr.shepard.neo4Core.dao.TimeseriesContainerDAO;
import de.dlr.shepard.neo4Core.entities.TimeseriesContainer;
import de.dlr.shepard.neo4Core.io.TimeseriesContainerIO;
import de.dlr.shepard.util.QueryParamHelper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.List;
import org.apache.commons.lang3.NotImplementedException;

@RequestScoped
public class ExperimentalTimeseriesContainerService {

  private TimeseriesContainerDAO timeseriesContainerDAO;

  ExperimentalTimeseriesContainerService() {}

  @Inject
  public ExperimentalTimeseriesContainerService(TimeseriesContainerDAO timeseriesContainerDAO) {
    this.timeseriesContainerDAO = timeseriesContainerDAO;
  }

  public List<TimeseriesContainer> getAllContainers(QueryParamHelper params, String username) {
    var containers = timeseriesContainerDAO.findAllTimeseriesContainers(params, username);
    return containers;
  }

  public TimeseriesContainer getContainer(long timeseriesContainerId) {
    TimeseriesContainer timeseriesContainer = timeseriesContainerDAO.findByNeo4jId(timeseriesContainerId);
    if (timeseriesContainer == null || timeseriesContainer.isDeleted()) {
      Log.errorf("Timeseries Container with id %s is null or deleted", timeseriesContainerId);
      return null;
    }
    return timeseriesContainer;
  }

  /**
   * Creates a TimeseriesContainer and stores it in Neo4J
   *
   * @param timeseriesContainer to be stored
   * @param username            of the related user
   * @return the created timeseriesContainer
   */
  public TimeseriesContainer createContainer(TimeseriesContainerIO timeseriesContainer, String username) {
    throw new NotImplementedException();
    // var user = userDAO.find(username);

    // var toCreate = new TimeseriesContainer();
    // toCreate.setCreatedAt(dateHelper.getDate());
    // toCreate.setCreatedBy(user);
    // toCreate.setDatabase(timeseriesService.createDatabase());
    // toCreate.setName(timeseriesContainer.getName());

    // var created = timeseriesContainerDAO.createOrUpdate(toCreate);
    // permissionsDAO.createOrUpdate(new Permissions(created, user, PermissionType.Private));
    // return created;
  }

  /**
   * Deletes a TimeseriesContainer in Neo4j
   *
   * @param timeSeriesContainerId identifies the TimeseriesContainer
   * @param username              of the related user
   * @return a boolean to determine if TimeseriesContainer was successfully
   *         deleted
   */
  public boolean deleteContainer(long timeSeriesContainerId, String username) {
    throw new NotImplementedException();
    // var user = userDAO.find(username);
    // TimeseriesContainer timeseriesContainer = timeseriesContainerDAO.findByNeo4jId(timeSeriesContainerId);
    // if (timeseriesContainer == null) {
    //   return false;
    // }

    // timeseriesContainer.setDeleted(true);
    // timeseriesContainer.setUpdatedAt(dateHelper.getDate());
    // timeseriesContainer.setUpdatedBy(user);
    // timeseriesContainerDAO.createOrUpdate(timeseriesContainer);
    // timeseriesService.deleteDatabase(timeseriesContainer.getDatabase());
    // return true;
  }

  /**
   * Saves timeseries payload in a timeseries container.
   *
   * @param timeseriesContainerId identifies the TimeseriesContainer
   * @param payload               TimeseriesPayload to be created
   * @return created timeseries
   */
  public Timeseries createTimeseries(long timeseriesContainerId, TimeseriesPayload payload) {
    throw new NotImplementedException();
    // var timeseriesContainer = timeseriesContainerDAO.findByNeo4jId(timeseriesContainerId);
    // if (timeseriesContainer == null || timeseriesContainer.isDeleted()) {
    //   Log.errorf("Timeseries Container with id %s is null or deleted", timeseriesContainerId);
    //   return null;
    // }
    // var result = timeseriesService.createTimeseries(timeseriesContainer.getDatabase(), payload);
    // if (!result.isBlank()) {
    //   Log.errorf("Failed to create timeseries with error: %s", result);
    //   return null;
    // }
    // return payload.getTimeseries();
  }
}
