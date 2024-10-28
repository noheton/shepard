package de.dlr.shepard.timeseries.services;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.exceptions.InvalidRequestException;
import de.dlr.shepard.influxDB.FillOption;
import de.dlr.shepard.neo4Core.dao.PermissionsDAO;
import de.dlr.shepard.neo4Core.dao.TimeseriesContainerDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.Permissions;
import de.dlr.shepard.neo4Core.entities.TimeseriesContainer;
import de.dlr.shepard.timeseries.io.ExperimentalTimeseriesPayloadDataPointIO;
import de.dlr.shepard.timeseries.io.TimeseriesPayloadIOMapper;
import de.dlr.shepard.timeseries.model.AggregateFunctions;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseries;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseriesData;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseriesDataPointEntity;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseriesEntity;
import de.dlr.shepard.timeseries.repositories.ExperimentalTimeseriesDataPointRepository;
import de.dlr.shepard.timeseries.repositories.ExperimentalTimeseriesRepository;
import de.dlr.shepard.timeseries.utilities.CsvConverter;
import de.dlr.shepard.timeseries.utilities.ObjectTypeEvaluator;
import de.dlr.shepard.timeseries.utilities.TimeseriesValidator;
import de.dlr.shepard.util.DateHelper;
import de.dlr.shepard.util.PermissionType;
import de.dlr.shepard.util.QueryParamHelper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.apache.commons.lang3.NotImplementedException;

@RequestScoped
public class ExperimentalTimeseriesContainerService {

  private TimeseriesContainerDAO timeseriesContainerDAO;
  private UserDAO userDAO;
  private DateHelper dateHelper;
  private PermissionsDAO permissionsDAO;
  private ExperimentalTimeseriesRepository timeseriesRepository;
  private ExperimentalTimeseriesDataPointRepository timeseriesPayloadRepository;
  private CsvConverter csvConverter;

  ExperimentalTimeseriesContainerService() {}

  @Inject
  public ExperimentalTimeseriesContainerService(
    TimeseriesContainerDAO timeseriesContainerDAO,
    UserDAO userDAO,
    DateHelper dateHelper,
    PermissionsDAO permissionsDAO,
    ExperimentalTimeseriesRepository timeseriesRepository,
    ExperimentalTimeseriesDataPointRepository timeseriesPayloadRepository,
    CsvConverter csvConverter
  ) {
    this.timeseriesContainerDAO = timeseriesContainerDAO;
    this.userDAO = userDAO;
    this.dateHelper = dateHelper;
    this.permissionsDAO = permissionsDAO;
    this.timeseriesRepository = timeseriesRepository;
    this.timeseriesPayloadRepository = timeseriesPayloadRepository;
    this.csvConverter = csvConverter;
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
  ) {
    var timeseriesContainer = timeseriesContainerDAO.findByNeo4jId(containerId);
    if (timeseriesContainer == null || timeseriesContainer.isDeleted()) {
      throw new InvalidBodyException("Timeseries container with id %s is null or deleted.", containerId);
    }

    TimeseriesValidator.validate(timeseries);
    var valueType = ObjectTypeEvaluator.evaluate(dataPoints.get(0).getValue());

    // try to find timeseries in db
    var matchingTimeseries = timeseriesRepository.findByTimeseries(containerId, timeseries);
    ExperimentalTimeseriesEntity timeseriesEntity = null;
    if (matchingTimeseries.isEmpty()) {
      // create new timeseries because it does not exist
      timeseriesEntity = new ExperimentalTimeseriesEntity(containerId, timeseries, valueType);
      this.timeseriesRepository.persist(timeseriesEntity);
    } else {
      timeseriesEntity = matchingTimeseries.get(0);
      throwIfDataTypesAreDifferent(timeseriesEntity, dataPoints);
    }

    // parse points to correct model ExperimentalTimeseriesPayload
    var timeseriesPayloadDataPoints = TimeseriesPayloadIOMapper.map(timeseriesEntity.getId(), valueType, dataPoints);

    timeseriesPayloadRepository.persist(timeseriesPayloadDataPoints);
    return timeseriesEntity;
  }

  /**
   * Returns a list of timeseries objects that are in the given database.
   *
   * @param containerId the given timeseries container
   * @return a list of timeseries objects
   */
  public List<ExperimentalTimeseriesEntity> getTimeseriesAvailable(long containerId) {
    var container = timeseriesContainerDAO.findLightByNeo4jId(containerId);
    if (container == null || container.isDeleted()) {
      Log.errorf("Timeseries Container with id %s is null or deleted", containerId);
      return Collections.emptyList();
    }
    var timeseriesList = timeseriesRepository.list("containerId", container.getId());
    return timeseriesList;
  }

  /**
   * Loads timeseries data points of a timeseries.
   *
   * @param containerId           identifies the TimeseriesContainer
   * @param timeseries            The timeseries to load
   * @param start                 The beginning of the timeseries
   * @param end                   The end of the timeseries
   * @return ExperimentalTimeseriesDataPointEntity
   */
  public List<ExperimentalTimeseriesDataPointEntity> getDataPoints(
    long containerId,
    ExperimentalTimeseries timeseries,
    long start,
    long end
  ) {
    var result = this.timeseriesRepository.find("containerId", containerId).firstResultOptional();
    if (!result.isPresent()) throw new InvalidRequestException("Timeseries not found.");

    var timeseriesId = result.get().getId();
    var retVal =
      this.timeseriesPayloadRepository.find(
          "timeseriesId = ?1 AND time >= ?2 AND time <= ?3",
          timeseriesId,
          start,
          end
        ).list();
    return retVal;
  }

  public List<ExperimentalTimeseriesPayloadDataPointIO> getDataPointsAggregated(
    // TODO:change return type
    long containerId,
    ExperimentalTimeseries timeseries,
    long startNanoseconds,
    long endNanoseconds,
    long timeIntervalMicroseconds,
    AggregateFunctions aggregateFunction
  ) {
    // var result = this.timeseriesRepository.find("containerId", containerId).firstResultOptional();
    var result = this.timeseriesRepository.findByTimeseries(containerId, timeseries);

    if (result.isEmpty()) throw new InvalidRequestException("Timeseries not found.");

    var timeseriesId = result.get(0).getId();

    // var query = TimescaleQueryBuilder.buildQuery(timeseriesId, startNanoseconds, endNanoseconds, timeIntervalMicroseconds, aggregateFunction);
    var retVal =
      this.timeseriesPayloadRepository.getDataPoints(
          timeseriesId,
          startNanoseconds,
          endNanoseconds,
          timeIntervalMicroseconds,
          aggregateFunction
        );
    //var retVal = this.timeseriesPayloadRepository.find("timeseriesId", timeseriesId).list();
    return retVal;
  }

  private ExperimentalTimeseriesData getTimeseriesData(
    long containerId,
    ExperimentalTimeseries timeseries,
    AggregateFunctions function,
    Long groupBy,
    FillOption fillOption,
    long start,
    long end
  ) {
    var timeseriesData = new ExperimentalTimeseriesData();
    timeseriesData.setTimeseries(timeseries);
    timeseriesData.setDataPoints(getDataPointsAggregated(containerId, timeseries, start, end, end, function));
    return timeseriesData;
  }

  /**
   * Export one timeseries as CSV File if found.
   *
   * @param containerId           Id of the container in Neo4j
   * @param timeseriesList        The list of timeseries whose points are queried
   * @param function              The aggregate function
   * @param groupBy               The time interval measurements get grouped by
   * @param fillOption            The fill option for missing values
   * @param start                 The beginning of the timeseries
   * @param end                   The end of the timeseries
   * @return InputStream containing the CSV file
   * @throws IOException When the CSV file could not be written
   */
  public InputStream exportTimeseriesData(
    long containerId,
    ExperimentalTimeseries timeseries,
    long start,
    long end,
    AggregateFunctions function,
    Long groupBy,
    FillOption fillOption
  ) throws IOException {
    var timeseriesContainer = timeseriesContainerDAO.findLightByNeo4jId(containerId);
    if (timeseriesContainer == null || timeseriesContainer.isDeleted()) {
      throw new InvalidBodyException("Timeseries container with id %s is null or deleted.", containerId);
    }

    var timeseriesData = getTimeseriesData(containerId, timeseries, function, groupBy, fillOption, start, end);
    var stream = csvConverter.convertToCsv(List.of(timeseriesData));
    return stream;
  }

  /**
   * Export list of timeseries as CSV File. If the filter sets are empty, no filtering
   * takes place.
   *
   * @param containerId           Id of the container in Neo4j
   * @param timeseriesList        The list of timeseries whose points are queried
   * @param function              The aggregate function
   * @param groupBy               The time interval measurements get grouped by
   * @param fillOption            The fill option for missing values
   * @param start                 The beginning of the timeseries
   * @param end                   The end of the timeseries
   * @param devicesFilterSet      A set of allowed devices or an empty set
   * @param locationsFilterSet    A set of allowed locations or an empty set
   * @param symbolicNameFilterSet A set of allowed symbolic names or an empty set
   * @return InputStream containing the CSV file
   * @throws IOException When the CSV file could not be written
   */
  public InputStream exportTimeseriesPayload(
    long containerId,
    List<ExperimentalTimeseries> timeseriesList,
    AggregateFunctions function,
    Long groupBy,
    long start,
    long end,
    FillOption fillOption,
    Set<String> devicesFilterSet,
    Set<String> locationsFilterSet,
    Set<String> symbolicNameFilterSet
  ) throws IOException {
    var timeseriesDataList = getTimeseriesDataList(
      containerId,
      timeseriesList,
      function,
      groupBy,
      fillOption,
      start,
      end,
      devicesFilterSet,
      locationsFilterSet,
      symbolicNameFilterSet
    );
    var stream = csvConverter.convertToCsv(timeseriesDataList);
    return stream;
  }

  /**
   * Queries the database for many timeseries in parallel. Returns a list of
   * timeseries. If the filter sets are empty, no filtering takes place.
   *
   * @param containerId           Id of the container in Neo4j
   * @param timeseriesList        The list of timeseries whose points are queried
   * @param function              The aggregate function
   * @param groupBy               The time interval measurements get grouped by
   * @param fillOption            The fill option for missing values
   * @param start                 The beginning of the timeseries
   * @param end                   The end of the timeseries
   * @param devicesFilterSet      A set of allowed devices or an empty set
   * @param locationsFilterSet    A set of allowed locations or an empty set
   * @param symbolicNameFilterSet A set of allowed symbolic names or an empty set
   * @return a list of timeseries with influx points
   */
  public List<ExperimentalTimeseriesData> getTimeseriesDataList(
    long containerId,
    List<ExperimentalTimeseries> timeseriesList,
    AggregateFunctions function,
    Long groupBy,
    FillOption fillOption,
    long start,
    long end,
    Set<String> devicesFilterSet,
    Set<String> locationsFilterSet,
    Set<String> symbolicNameFilterSet
  ) {
    var timeseriesDataQueue = new ConcurrentLinkedQueue<ExperimentalTimeseriesData>();
    timeseriesList
      .parallelStream()
      .forEach(timeseries -> {
        ExperimentalTimeseriesData timeseriesData = null;
        if (matchFilter(timeseries, devicesFilterSet, locationsFilterSet, symbolicNameFilterSet)) {
          timeseriesData = getTimeseriesData(containerId, timeseries, function, groupBy, fillOption, start, end);
        }
        if (timeseriesData != null) {
          timeseriesDataQueue.add(timeseriesData);
        }
      });
    return new ArrayList<>(timeseriesDataQueue);
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

  private boolean matchFilter(
    ExperimentalTimeseries timeseries,
    Set<String> device,
    Set<String> location,
    Set<String> symName
  ) {
    var deviceMatches = true;
    var locatioMatches = true;
    var symbolicNameMatches = true;
    if (!device.isEmpty()) {
      deviceMatches = device.contains(timeseries.getDevice());
    }
    if (!location.isEmpty()) {
      locatioMatches = location.contains(timeseries.getLocation());
    }
    if (!symName.isEmpty()) {
      symbolicNameMatches = symName.contains(timeseries.getSymbolicName());
    }
    return deviceMatches && locatioMatches && symbolicNameMatches;
  }

  private static void throwIfDataTypesAreDifferent(
    ExperimentalTimeseriesEntity timeseriesEntity,
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints
  ) {
    for (var dataPoint : dataPoints) {
      var expectedType = ObjectTypeEvaluator.evaluate(dataPoint.getValue());
      if (timeseriesEntity.getValueType() != expectedType) {
        throw new InvalidBodyException(
          "Timeseries already exists for data type %s but new data points are of type %s",
          timeseriesEntity.getValueType(),
          expectedType
        );
      }
    }
  }
}
