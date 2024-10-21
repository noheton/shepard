package de.dlr.shepard.timeseries.services;

import de.dlr.shepard.influxDB.FillOption;
import de.dlr.shepard.influxDB.SingleValuedUnaryFunction;
import de.dlr.shepard.neo4Core.dao.PermissionsDAO;
import de.dlr.shepard.neo4Core.dao.TimeseriesContainerDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.Permissions;
import de.dlr.shepard.neo4Core.entities.TimeseriesContainer;
import de.dlr.shepard.timeseries.io.ExperimentalTimeseriesPayloadDataPointIO;
import de.dlr.shepard.timeseries.io.TimeseriesPayloadIOMapper;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseries;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseriesDataPointEntity;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseriesEntity;
import de.dlr.shepard.timeseries.repositories.ExperimentalTimeseriesDataPointRepository;
import de.dlr.shepard.timeseries.repositories.ExperimentalTimeseriesRepository;
import de.dlr.shepard.timeseries.utilities.ObjectTypeEvaluator;
import de.dlr.shepard.util.DateHelper;
import de.dlr.shepard.util.PermissionType;
import de.dlr.shepard.util.QueryParamHelper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.NotImplementedException;

@RequestScoped
public class ExperimentalTimeseriesContainerService {

  private TimeseriesContainerDAO timeseriesContainerDAO;
  private UserDAO userDAO;
  private DateHelper dateHelper;
  private PermissionsDAO permissionsDAO;
  private ExperimentalTimeseriesRepository timeseriesRepository;
  private ExperimentalTimeseriesDataPointRepository timeseriesPayloadRepository;

  ExperimentalTimeseriesContainerService() {}

  @Inject
  public ExperimentalTimeseriesContainerService(
    TimeseriesContainerDAO timeseriesContainerDAO,
    UserDAO userDAO,
    DateHelper dateHelper,
    PermissionsDAO permissionsDAO,
    ExperimentalTimeseriesRepository timeseriesRepository,
    ExperimentalTimeseriesDataPointRepository timeseriesPayloadRepository
  ) {
    this.timeseriesContainerDAO = timeseriesContainerDAO;
    this.userDAO = userDAO;
    this.dateHelper = dateHelper;
    this.permissionsDAO = permissionsDAO;
    this.timeseriesRepository = timeseriesRepository;
    this.timeseriesPayloadRepository = timeseriesPayloadRepository;
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
   * @param name name of the container
   * @param username of the related user
   * @return the created timeseriesContainer
   */
  @Transactional
  public TimeseriesContainer createContainer(String name, String username) {
    var user = userDAO.find(username);
    var toCreate = new TimeseriesContainer();
    toCreate.setCreatedAt(dateHelper.getDate());
    toCreate.setCreatedBy(user);
    toCreate.setDatabase(null); // This is not needed anymore after the migration to TSDB
    toCreate.setName(name);
    var created = timeseriesContainerDAO.createOrUpdate(toCreate);
    permissionsDAO.createOrUpdate(new Permissions(created, user, PermissionType.Private));
    return created;
  }

  /**
   * Deletes a TimeseriesContainer in Neo4j
   *
   * @param timeSeriesContainerId identifies the TimeseriesContainer
   * @param username              of the related user
   * @return a boolean to determine if TimeseriesContainer was successfully
   *         deleted
   */
  @Transactional
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
    timeseriesRepository.delete("containerId", timeSeriesContainerId);
    return true;
  }

  /**
   * Adds payload to a timeseries.
   *
   * @param timeseriesContainerId identifies the TimeseriesContainer
   * @param payload               payload to be added
   * @return created timeseries
   */
  @Transactional
  public ExperimentalTimeseriesEntity addPayload(
    long containerId,
    ExperimentalTimeseries timeseries,
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints
  ) throws Exception {
    var timeseriesContainer = timeseriesContainerDAO.findByNeo4jId(containerId);
    if (timeseriesContainer == null || timeseriesContainer.isDeleted()) {
      String errorMessage = String.format("Timeseries Container with id %s is null or deleted", containerId);
      Log.errorf(errorMessage);
      throw new Exception(errorMessage);
    }

    // timeseries sanity check
    // TimeseriesSanitizer.sanitizeMetadata(payload);
    // points sanity check

    // try to find timeseries in db
    var matchingTimeseries = timeseriesRepository.find("measurement", timeseries.getMeasurement()).list(); // Todo: finish that query
    if (matchingTimeseries.size() > 1) throw new Exception("found more than one timeseries for parameters");

    ExperimentalTimeseriesEntity timeseriesEntity = null;

    if (matchingTimeseries.isEmpty()) {
      // create new timeseries because it does not exist
      timeseriesEntity = new ExperimentalTimeseriesEntity(
        containerId,
        timeseries.getMeasurement(),
        timeseries.getField(),
        timeseries.getDevice(),
        timeseries.getLocation(),
        timeseries.getSymbolicName()
      );
      this.timeseriesRepository.persist(timeseriesEntity);
    } else {
      timeseriesEntity = matchingTimeseries.get(0);
    }

    // timeseries is persisted, now we persist the payload
    // get type of payload points
    var expectedType = ObjectTypeEvaluator.evaluate(dataPoints.get(0).getValue());
    // parse points to correct model ExperimentalTimeseriesPayload
    var timeseriesPayloadDataPoints = TimeseriesPayloadIOMapper.map(timeseriesEntity.getId(), expectedType, dataPoints);

    timeseriesPayloadRepository.persist(timeseriesPayloadDataPoints);
    return timeseriesEntity;
  }

  /**
   * Returns a list of timeseries objects that are in the given database.
   *
   * @param timeseriesContainerId the given timeseries container
   * @return a list of timeseries objects
   */
  public List<ExperimentalTimeseriesEntity> getTimeseriesAvailable(long timeseriesContainerId) {
    Log.infof("getTimeseriesAvailable(%s) called", timeseriesContainerId);
    var timeseriesContainer = timeseriesContainerDAO.findLightByNeo4jId(timeseriesContainerId);
    if (timeseriesContainer == null || timeseriesContainer.isDeleted()) {
      Log.errorf("Timeseries Container with id %s is null or deleted", timeseriesContainerId);
      return Collections.emptyList();
    }
    var timeseriesList = timeseriesRepository.list("containerId", timeseriesContainer.getId());
    Log.infof("getTimeseriesAvailable(%s) returns %s records", timeseriesContainerId, timeseriesList.size());
    return timeseriesList;
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
  public List<ExperimentalTimeseriesDataPointEntity> getDataPoints(
    ExperimentalTimeseries timeseries,
    long start,
    long end,
    long groupBy
  ) {
    var result =
      this.timeseriesRepository.find("containerId", timeseries.getTimeseriesContainerId()).firstResultOptional();
    if (result.isPresent()) {
      var timeseriesId = result.get().getId();
      var retVal = this.timeseriesPayloadRepository.find("timeseriesId", timeseriesId).list();
      Log.info(String.format("getDataPoints returns: %s", retVal.get(0)));
      return retVal;
    }

    // Todo: Nothing found, throw an exception
    return null;
  }

  public InputStream exportTimeseriesPayload(
    ExperimentalTimeseries timeseries,
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
