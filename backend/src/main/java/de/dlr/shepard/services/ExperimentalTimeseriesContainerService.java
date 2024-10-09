package de.dlr.shepard.services;

import de.dlr.shepard.influxDB.FillOption;
import de.dlr.shepard.influxDB.SingleValuedUnaryFunction;
import de.dlr.shepard.influxDB.Timeseries;
import de.dlr.shepard.influxDB.TimeseriesPayload;
import de.dlr.shepard.neo4Core.dao.TimeseriesContainerDAO;
import de.dlr.shepard.neo4Core.entities.TimeseriesContainer;
import de.dlr.shepard.neo4Core.io.TimeseriesContainerIO;
import de.dlr.shepard.util.QueryParamHelper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
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

  /**
   * Returns a list of timeseries objects that are in the given database.
   *
   * @param timeseriesContainerId the given timeseries container
   * @return a list of timeseries objects
   */
  public List<Timeseries> getTimeseriesAvailable(long timeseriesContainerId) {
    throw new NotImplementedException();
    // var timeseriesContainer = timeseriesContainerDAO.findLightByNeo4jId(timeseriesContainerId);
    // if (timeseriesContainer == null || timeseriesContainer.isDeleted()) {
    //   Log.errorf("Timeseries Container with id %s is null or deleted", timeseriesContainerId);
    //   return Collections.emptyList();
    // }
    // return timeseriesService.getTimeseriesAvailable(timeseriesContainer.getDatabase());
  }

  /**
   * Loads timeseries payload from a timeseries container.
   *
   * @param timeseriesContainerId identifies the TimeseriesContainer
   * @param timeseries            The timeseries to load
   * @param start                 The beginning of the timeseries
   * @param end                   The end of the timeseries
   * @param function              The aggregate function
   * @param groupBy               The time interval measurements get grouped by
   * @param fillOption            The fill option for missing values
   * @return TimeseriesPayload
   */
  public TimeseriesPayload getTimeseriesPayload(
    long timeseriesContainerId,
    Timeseries timeseries,
    long start,
    long end,
    SingleValuedUnaryFunction function,
    Long groupBy,
    FillOption fillOption
  ) {
    throw new NotImplementedException();
    // var timeseriesContainer = timeseriesContainerDAO.findLightByNeo4jId(timeseriesContainerId);
    // if (timeseriesContainer == null || timeseriesContainer.isDeleted()) {
    //   Log.errorf("Timeseries Container with id %s is null or deleted", timeseriesContainerId);
    //   return null;
    // }
    // var result = timeseriesService.getTimeseriesPayload(
    //   start,
    //   end,
    //   timeseriesContainer.getDatabase(),
    //   timeseries,
    //   function,
    //   groupBy,
    //   fillOption
    // );
    // return result;
  }

  public InputStream exportTimeseriesPayload(
    long timeseriesContainerId,
    Timeseries timeseries,
    long start,
    long end,
    SingleValuedUnaryFunction function,
    Long groupBy,
    FillOption fillOption
  ) throws IOException {
    throw new NotImplementedException();
    // var timeseriesContainer = timeseriesContainerDAO.findLightByNeo4jId(timeseriesContainerId);
    // if (timeseriesContainer == null || timeseriesContainer.isDeleted()) {
    //   Log.errorf("Timeseries Container with id %s is null or deleted", timeseriesContainerId);
    //   return null;
    // }
    // var result = timeseriesService.exportTimeseriesPayload(
    //   start,
    //   end,
    //   timeseriesContainer.getDatabase(),
    //   List.of(timeseries),
    //   function,
    //   groupBy,
    //   fillOption,
    //   Collections.emptySet(),
    //   Collections.emptySet(),
    //   Collections.emptySet()
    // );
    // return result;
  }

  public boolean importTimeseries(long timeseriesContainerId, InputStream stream) throws IOException {
    throw new NotImplementedException();
    // var timeseriesContainer = timeseriesContainerDAO.findLightByNeo4jId(timeseriesContainerId);
    // if (timeseriesContainer == null || timeseriesContainer.isDeleted()) {
    //   Log.errorf("Timeseries Container with id %s is null or deleted", timeseriesContainerId);
    //   return false;
    // }
    // var result = timeseriesService.importTimeseries(timeseriesContainer.getDatabase(), stream);
    // if (!result.isBlank()) {
    //   Log.errorf("Failed to import timeseries with error: %s", result);
    //   return false;
    // }
    // return true;
  }
}
