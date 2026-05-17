package de.dlr.shepard.data.timeseries.services;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.PermissionType;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.data.AbstractContainerService;
import de.dlr.shepard.data.timeseries.daos.TimeseriesContainerDAO;
import de.dlr.shepard.data.timeseries.io.TimeseriesContainerIO;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheName;
import io.quarkus.cache.CacheResult;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;

@RequestScoped
public class TimeseriesContainerService extends AbstractContainerService<TimeseriesContainer, TimeseriesContainerIO> {

  @Inject
  TimeseriesContainerDAO timeseriesContainerDAO;

  @Inject
  UserService userService;

  @Inject
  DateHelper dateHelper;

  @Inject
  @CacheName("container-cache")
  Cache cache;

  @Inject
  PermissionsService permissionsService;

  @Inject
  TimeseriesService timeseriesService;

  public List<TimeseriesContainer> getContainers() {
    return timeseriesContainerDAO.findAll().stream().filter(c -> !c.isDeleted()).toList();
  }

  @Override
  public List<TimeseriesContainer> getAllContainers(QueryParamHelper params) {
    User user = userService.getCurrentUser();
    return timeseriesContainerDAO.findAllTimeseriesContainers(params, user.getUsername());
  }

  /**
   * Get Timeseries Container by Id
   *
   * @throws InvalidPathException if container is null or deleted
   * @throws InvalidAuthException if user has no read permissions on container
   * @return timeseries container
   */
  @Override
  public TimeseriesContainer getContainer(long timeseriesContainerId) {
    Optional<TimeseriesContainer> containerOptional = getContainerOptional(timeseriesContainerId);
    if (containerOptional.isEmpty()) {
      String errorMsg =
        "ID ERROR - Timeseries Container with id %s is null or deleted".formatted(timeseriesContainerId);
      Log.errorf(errorMsg);
      throw new InvalidPathException(errorMsg);
    }
    assertIsAllowedToReadContainer(timeseriesContainerId);
    return containerOptional.get();
  }

  @CacheResult(cacheName = "container-cache")
  Optional<TimeseriesContainer> getContainerOptional(long timeseriesContainerId) {
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
  @Override
  public TimeseriesContainer createContainer(TimeseriesContainerIO timeseriesContainerIO) {
    User user = userService.getCurrentUser();
    var toCreate = new TimeseriesContainer();
    toCreate.setCreatedAt(dateHelper.getDate());
    toCreate.setCreatedBy(user);
    toCreate.setName(timeseriesContainerIO.getName());

    var created = timeseriesContainerDAO.createOrUpdate(toCreate);
    permissionsService.createPermissions(created, user, PermissionType.Private);
    return created;
  }

  /**
   * CC1b — look up a TimeseriesContainer by its appId with a read-permission
   * check. Used by the linked-data-objects REST endpoint.
   *
   * @throws InvalidPathException if no container with that appId exists
   */
  public TimeseriesContainer getContainerByAppId(String appId) {
    TimeseriesContainer c = timeseriesContainerDAO.findByAppId(appId)
      .orElseThrow(() -> new InvalidPathException(
        "TimeseriesContainer with appId '" + appId + "' not found"));
    if (c.isDeleted()) {
      throw new InvalidPathException("TimeseriesContainer '" + appId + "' is deleted");
    }
    assertIsAllowedToReadContainer(c.getId());
    return c;
  }

  /**
   * CC1b — return the list of non-deleted DataObjects that reference this
   * TimeseriesContainer via a TimeseriesReference.
   *
   * @param containerId numeric OGM id of the TimeseriesContainer
   * @return distinct DataObjects linked to this container
   */
  public List<DataObject> findLinkedDataObjectsById(long containerId) {
    TimeseriesContainer container = getContainer(containerId);
    String appId = container.getAppId();
    if (appId == null) {
      return java.util.Collections.emptyList();
    }
    return timeseriesContainerDAO.findLinkedDataObjectsByContainerAppId(appId);
  }

  /**
   * Deletes a TimeseriesContainer in Neo4j
   *
   * @param timeSeriesContainerId identifies the TimeseriesContainer
   * @param username              of the related user
   */
  @Override
  public void deleteContainer(long timeSeriesContainerId) {
    User user = userService.getCurrentUser();
    TimeseriesContainer timeseriesContainer = this.getContainer(timeSeriesContainerId);
    timeseriesService.deleteTimeseriesByContainerId(timeSeriesContainerId);

    timeseriesContainer.setDeleted(true);
    timeseriesContainer.setUpdatedAt(dateHelper.getDate());
    timeseriesContainer.setUpdatedBy(user);
    timeseriesContainerDAO.createOrUpdate(timeseriesContainer);
    cache.invalidate(timeSeriesContainerId).await().indefinitely();
  }
}
