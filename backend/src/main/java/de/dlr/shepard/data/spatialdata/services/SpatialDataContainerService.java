package de.dlr.shepard.data.spatialdata.services;

import de.dlr.shepard.auth.permission.daos.PermissionsDAO;
import de.dlr.shepard.auth.permission.entities.Permissions;
import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.PermissionType;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.data.spatialdata.daos.SpatialDataContainerDAO;
import de.dlr.shepard.data.spatialdata.model.SpatialDataContainer;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.Optional;

@RequestScoped
public class SpatialDataContainerService {

  SpatialDataPointService spatialDataPointService;
  SpatialDataContainerDAO containerDao;
  UserDAO userDao;
  PermissionsDAO permissionsDao;
  DateHelper dateHelper;

  @Inject
  public SpatialDataContainerService(
    SpatialDataPointService spatialDataPointService,
    SpatialDataContainerDAO containerDao,
    UserDAO userDao,
    PermissionsDAO permissionsDao,
    DateHelper dateHelper
  ) {
    this.spatialDataPointService = spatialDataPointService;
    this.containerDao = containerDao;
    this.userDao = userDao;
    this.permissionsDao = permissionsDao;
    this.dateHelper = dateHelper;
  }

  SpatialDataContainerService() {}

  public List<SpatialDataContainer> getContainers(QueryParamHelper params, String username) {
    var containers = containerDao.findAllSpatialContainers(params, username);
    return containers;
  }

  public SpatialDataContainer getContainer(long containerId) {
    var containerOptional = this.getContainerOptional(containerId);

    if (containerOptional.isEmpty()) {
      Log.errorf("Spatial data container with id %s is null or deleted.", containerId);
      throw new NotFoundException("Spatial data container with id " + containerId + " not found.");
    }
    return containerOptional.get();
  }

  public Optional<SpatialDataContainer> getContainerOptional(long containerId) {
    var container = containerDao.findByNeo4jId(containerId);
    if (container == null || container.isDeleted()) {
      return Optional.empty();
    }
    return Optional.of(container);
  }

  public SpatialDataContainer createContainer(String name, String username) {
    var user = userDao.find(username);
    var toCreate = new SpatialDataContainer();
    toCreate.setCreatedAt(dateHelper.getDate());
    toCreate.setCreatedBy(user);
    toCreate.setName(name);
    var created = containerDao.createOrUpdate(toCreate);
    permissionsDao.createOrUpdate(new Permissions(created, user, PermissionType.Private));
    return created;
  }

  public void deleteContainer(long containerId, String username) {
    var user = userDao.find(username);
    var container = this.getContainer(containerId);

    spatialDataPointService.deleteByContainerId(containerId);

    container.setDeleted(true);
    container.setUpdatedAt(dateHelper.getDate());
    container.setUpdatedBy(user);
    containerDao.createOrUpdate(container);
  }
}
