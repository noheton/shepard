package de.dlr.shepard.timeseries.services;

import de.dlr.shepard.neo4Core.dao.PermissionsDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.Permissions;
import de.dlr.shepard.timeseries.daos.TimeseriesContainerDAO;
import de.dlr.shepard.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.util.DateHelper;
import de.dlr.shepard.util.PermissionType;
import de.dlr.shepard.util.QueryParamHelper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.Optional;

@RequestScoped
public class ExperimentalTimeseriesContainerService {

  private TimeseriesContainerDAO timeseriesContainerDAO;
  private UserDAO userDAO;
  private DateHelper dateHelper;
  private PermissionsDAO permissionsDAO;
  private ExperimentalTimeseriesService timeseriesService;

  ExperimentalTimeseriesContainerService() {}

  @Inject
  public ExperimentalTimeseriesContainerService(
    TimeseriesContainerDAO timeseriesContainerDAO,
    UserDAO userDAO,
    DateHelper dateHelper,
    PermissionsDAO permissionsDAO,
    ExperimentalTimeseriesService timeseriesService
  ) {
    this.timeseriesContainerDAO = timeseriesContainerDAO;
    this.userDAO = userDAO;
    this.dateHelper = dateHelper;
    this.permissionsDAO = permissionsDAO;
    this.timeseriesService = timeseriesService;
  }

  public List<TimeseriesContainer> getContainers(QueryParamHelper params, String username) {
    var containers = timeseriesContainerDAO.findAllTimeseriesContainers(params, username);
    return containers;
  }

  /**
   * @throws NotFoundException if container is null or deleted
   * @return timeseries container
   */
  public TimeseriesContainer getContainer(long timeseriesContainerId) {
    Optional<TimeseriesContainer> containerOptional = this.getContainerOptional(timeseriesContainerId);

    if (containerOptional.isEmpty()) {
      Log.errorf("Timeseries Container with id %s is null or deleted", timeseriesContainerId);
      throw new NotFoundException("Timeseries container with id " + timeseriesContainerId + " not found.");
    }

    return containerOptional.get();
  }

  public Optional<TimeseriesContainer> getContainerOptional(long timeseriesContainerId) {
    TimeseriesContainer timeseriesContainer = timeseriesContainerDAO.findByNeo4jId(timeseriesContainerId);
    if (timeseriesContainer == null || timeseriesContainer.isDeleted()) {
      return Optional.empty();
    }
    return Optional.of(timeseriesContainer);
  }

  /**
   * Creates a TimeseriesContainer and stores it in Neo4J
   *
   * @param name name of the container
   * @param username of the related user
   * @return the created timeseriesContainer
   */
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
   */
  public void deleteContainer(long timeSeriesContainerId, String username) {
    var user = userDAO.find(username);
    TimeseriesContainer timeseriesContainer = this.getContainer(timeSeriesContainerId);

    timeseriesContainer.setDeleted(true);
    timeseriesContainer.setUpdatedAt(dateHelper.getDate());
    timeseriesContainer.setUpdatedBy(user);
    timeseriesContainerDAO.createOrUpdate(timeseriesContainer);
    timeseriesService.deleteTimeseriesByContainerId(timeSeriesContainerId);
  }
}
