package de.dlr.shepard.data.spatialdata.services;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.PermissionType;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.data.AbstractContainerService;
import de.dlr.shepard.data.spatialdata.daos.SpatialDataContainerDAO;
import de.dlr.shepard.data.spatialdata.io.SpatialDataContainerIO;
import de.dlr.shepard.data.spatialdata.model.SpatialDataContainer;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;

@RequestScoped
public class SpatialDataContainerService
  extends AbstractContainerService<SpatialDataContainer, SpatialDataContainerIO> {

  @Inject
  SpatialDataPointService spatialDataPointService;

  @Inject
  SpatialDataContainerDAO containerDao;

  @Inject
  UserService userService;

  @Inject
  PermissionsService permissionsService;

  @Inject
  DateHelper dateHelper;

  /**
   * Gets the SpatialDataContainer
   *
   * @param id identifies the searched SpatialDataContainer
   * @return the SpatialDataContainer with matching id or null
   * @throws InvalidPathException if the SpatialData container cannot be found
   * @throws InvalidAuthException if user has no read permission on container
   */
  @Override
  public SpatialDataContainer getContainer(long containerId) {
    var containerOptional = this.getContainerOptional(containerId);

    if (containerOptional.isEmpty()) {
      String errorMsg = String.format("ID ERROR - Spatial data container with id %s is null or deleted", containerId);
      Log.error(errorMsg);
      throw new InvalidPathException(errorMsg);
    }
    assertIsAllowedToReadContainer(containerId);
    return containerOptional.get();
  }

  private Optional<SpatialDataContainer> getContainerOptional(long containerId) {
    var container = containerDao.findByNeo4jId(containerId);
    if (container == null || container.isDeleted()) {
      return Optional.empty();
    }
    return Optional.of(container);
  }

  @Override
  public List<SpatialDataContainer> getAllContainers(QueryParamHelper params) {
    User user = userService.getCurrentUser();
    var containers = containerDao.findAllSpatialContainers(params, user.getUsername());
    return containers;
  }

  @Override
  public SpatialDataContainer createContainer(SpatialDataContainerIO containerIO) {
    User user = userService.getCurrentUser();

    SpatialDataContainer toCreate = new SpatialDataContainer();
    toCreate.setCreatedAt(dateHelper.getDate());
    toCreate.setCreatedBy(user);
    toCreate.setName(containerIO.getName());

    var created = containerDao.createOrUpdate(toCreate);
    permissionsService.createPermissions(created, user, PermissionType.Private);
    return created;
  }

  @Override
  public void deleteContainer(long containerId) {
    SpatialDataContainer container = getContainer(containerId);
    assertIsAllowedToEditContainer(containerId);

    User user = userService.getCurrentUser();
    spatialDataPointService.deleteByContainerId(containerId);
    container.setDeleted(true);
    container.setUpdatedAt(dateHelper.getDate());
    container.setUpdatedBy(user);
    containerDao.createOrUpdate(container);
  }
}
