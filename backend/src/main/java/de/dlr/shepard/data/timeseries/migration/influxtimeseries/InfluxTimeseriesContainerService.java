package de.dlr.shepard.data.timeseries.migration.influxtimeseries;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.PermissionType;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.data.IContainerService;
import de.dlr.shepard.data.timeseries.daos.TimeseriesContainerDAO;
import de.dlr.shepard.data.timeseries.io.TimeseriesContainerIO;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

@RequestScoped
public class InfluxTimeseriesContainerService implements IContainerService<TimeseriesContainer, TimeseriesContainerIO> {

  private TimeseriesContainerDAO timeseriesContainerDAO;
  private InfluxTimeseriesService timeseriesService;
  private PermissionsService permissionsService;
  private UserDAO userDAO;
  private DateHelper dateHelper;

  InfluxTimeseriesContainerService() {}

  @Inject
  public InfluxTimeseriesContainerService(
    TimeseriesContainerDAO timeseriesContainerDAO,
    InfluxTimeseriesService timeseriesService,
    PermissionsService permissionsService,
    UserDAO userDAO,
    DateHelper dateHelper
  ) {
    this.timeseriesContainerDAO = timeseriesContainerDAO;
    this.timeseriesService = timeseriesService;
    this.permissionsService = permissionsService;
    this.userDAO = userDAO;
    this.dateHelper = dateHelper;
  }

  /**
   * Creates a TimeseriesContainer and stores it in Neo4J
   *
   * @param timeseriesContainer to be stored
   * @param username            of the related user
   * @return the created timeseriesContainer
   */
  @Override
  public TimeseriesContainer createContainer(TimeseriesContainerIO timeseriesContainer, String username) {
    var user = userDAO.find(username);

    var toCreate = new TimeseriesContainer();
    toCreate.setCreatedAt(dateHelper.getDate());
    toCreate.setCreatedBy(user);
    toCreate.setDatabase(timeseriesService.createDatabase());
    toCreate.setName(timeseriesContainer.getName());

    var created = timeseriesContainerDAO.createOrUpdate(toCreate);
    permissionsService.createPermissions(created, user, PermissionType.Private);
    return created;
  }

  /**
   * Searches the TimeseriesContainer in Neo4j
   *
   * @param timeSeriesContainerId identifies the searched TimeseriesContainer
   * @return the TimeseriesContainer with matching id or null
   */
  @Override
  public TimeseriesContainer getContainer(long timeSeriesContainerId) {
    TimeseriesContainer timeseriesContainer = timeseriesContainerDAO.findByNeo4jId(timeSeriesContainerId);
    if (timeseriesContainer == null || timeseriesContainer.isDeleted()) {
      Log.errorf("Timeseries Container with id %s is null or deleted", timeSeriesContainerId);
      return null;
    }
    return timeseriesContainer;
  }

  /**
   * Searches the database for all TimeseriesContainers
   *
   * @param params   QueryParamsHelper
   * @param username the name of the user
   * @return a list of TimeseriesContainers
   */
  @Override
  public List<TimeseriesContainer> getAllContainers(QueryParamHelper params, String username) {
    var containers = timeseriesContainerDAO.findAllTimeseriesContainers(params, username);
    return containers;
  }

  /**
   * Deletes a TimeseriesContainer in Neo4j
   *
   * @param timeSeriesContainerId identifies the TimeseriesContainer
   * @param username              of the related user
   * @return a boolean to determine if TimeseriesContainer was successfully
   *         deleted
   */

  @Override
  public boolean deleteContainer(long timeSeriesContainerId, String username) {
    var user = userDAO.find(username);
    TimeseriesContainer timeseriesContainer = timeseriesContainerDAO.findByNeo4jId(timeSeriesContainerId);
    if (timeseriesContainer == null) {
      return false;
    }

    timeseriesContainer.setDeleted(true);
    timeseriesContainer.setUpdatedAt(dateHelper.getDate());
    timeseriesContainer.setUpdatedBy(user);
    timeseriesContainerDAO.createOrUpdate(timeseriesContainer);
    timeseriesService.deleteDatabase(timeseriesContainer.getDatabase());
    return true;
  }

  /**
   * Saves timeseries payload in a timeseries container.
   *
   * @param timeseriesContainerId identifies the TimeseriesContainer
   * @param payload               TimeseriesPayload to be created
   * @return created timeseries
   */
  public InfluxTimeseries createTimeseries(long timeseriesContainerId, InfluxTimeseriesPayload payload) {
    var timeseriesContainer = timeseriesContainerDAO.findByNeo4jId(timeseriesContainerId);
    if (timeseriesContainer == null || timeseriesContainer.isDeleted()) {
      Log.errorf("Timeseries Container with id %s is null or deleted", timeseriesContainerId);
      return null;
    }
    var result = timeseriesService.createTimeseries(timeseriesContainer.getDatabase(), payload);
    if (!result.isBlank()) {
      Log.errorf("Failed to create timeseries with error: %s", result);
      return null;
    }
    return payload.getTimeseries();
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
  public InfluxTimeseriesPayload getTimeseriesPayload(
    long timeseriesContainerId,
    InfluxTimeseries timeseries,
    long start,
    long end,
    InfluxSingleValuedUnaryFunction function,
    Long groupBy,
    InfluxFillOption fillOption
  ) {
    var timeseriesContainer = timeseriesContainerDAO.findLightByNeo4jId(timeseriesContainerId);
    if (timeseriesContainer == null || timeseriesContainer.isDeleted()) {
      Log.errorf("Timeseries Container with id %s is null or deleted", timeseriesContainerId);
      return null;
    }
    var result = timeseriesService.getTimeseriesPayload(
      start,
      end,
      timeseriesContainer.getDatabase(),
      timeseries,
      function,
      groupBy,
      fillOption
    );
    return result;
  }

  /**
   * Returns a list of timeseries objects that are in the given database.
   *
   * @param timeseriesContainerId the given timeseries container
   * @return a list of timeseries objects
   */
  public List<InfluxTimeseries> getTimeseriesAvailable(long timeseriesContainerId) {
    var timeseriesContainer = timeseriesContainerDAO.findLightByNeo4jId(timeseriesContainerId);
    if (timeseriesContainer == null || timeseriesContainer.isDeleted()) {
      Log.errorf("Timeseries Container with id %s is null or deleted", timeseriesContainerId);
      return Collections.emptyList();
    }
    return timeseriesService.getTimeseriesAvailable(timeseriesContainer.getDatabase());
  }

  public InputStream exportTimeseriesPayload(
    long timeseriesContainerId,
    InfluxTimeseries timeseries,
    long start,
    long end,
    InfluxSingleValuedUnaryFunction function,
    Long groupBy,
    InfluxFillOption fillOption
  ) throws IOException {
    var timeseriesContainer = timeseriesContainerDAO.findLightByNeo4jId(timeseriesContainerId);
    if (timeseriesContainer == null || timeseriesContainer.isDeleted()) {
      Log.errorf("Timeseries Container with id %s is null or deleted", timeseriesContainerId);
      return null;
    }
    var result = timeseriesService.exportTimeseriesPayload(
      start,
      end,
      timeseriesContainer.getDatabase(),
      List.of(timeseries),
      function,
      groupBy,
      fillOption,
      Collections.emptySet(),
      Collections.emptySet(),
      Collections.emptySet()
    );
    return result;
  }

  public boolean importTimeseries(long timeseriesContainerId, InputStream stream) throws IOException {
    var timeseriesContainer = timeseriesContainerDAO.findLightByNeo4jId(timeseriesContainerId);
    if (timeseriesContainer == null || timeseriesContainer.isDeleted()) {
      Log.errorf("Timeseries Container with id %s is null or deleted", timeseriesContainerId);
      return false;
    }
    var result = timeseriesService.importTimeseries(timeseriesContainer.getDatabase(), stream);
    if (!result.isBlank()) {
      Log.errorf("Failed to import timeseries with error: %s", result);
      return false;
    }
    return true;
  }
}
