package de.dlr.shepard.data.structureddata.services;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.PermissionType;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.data.IContainerService;
import de.dlr.shepard.data.structureddata.daos.StructuredDataContainerDAO;
import de.dlr.shepard.data.structureddata.entities.StructuredData;
import de.dlr.shepard.data.structureddata.entities.StructuredDataContainer;
import de.dlr.shepard.data.structureddata.entities.StructuredDataPayload;
import de.dlr.shepard.data.structureddata.io.StructuredDataContainerIO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.List;

@RequestScoped
public class StructuredDataContainerService
  implements IContainerService<StructuredDataContainer, StructuredDataContainerIO> {

  private StructuredDataContainerDAO structuredDataContainerDAO;
  private StructuredDataService structuredDataService;
  private PermissionsService permissionsService;
  private UserDAO userDAO;
  private DateHelper dateHelper;

  StructuredDataContainerService() {}

  @Inject
  public StructuredDataContainerService(
    StructuredDataContainerDAO structuredDataContainerDAO,
    StructuredDataService structuredDataService,
    PermissionsService permissionsService,
    UserDAO userDAO,
    DateHelper dateHelper
  ) {
    this.structuredDataContainerDAO = structuredDataContainerDAO;
    this.structuredDataService = structuredDataService;
    this.permissionsService = permissionsService;
    this.userDAO = userDAO;
    this.dateHelper = dateHelper;
  }

  /**
   * Creates a StructuredDataContainer and stores it in Neo4J
   *
   * @param structuredDataContainerIO to be stored
   * @param username                  of the related user
   * @return the created StructuredDataContainer
   */
  @Override
  public StructuredDataContainer createContainer(StructuredDataContainerIO structuredDataContainerIO, String username) {
    var user = userDAO.find(username);
    String mongoid = structuredDataService.createStructuredDataContainer();
    var toCreate = new StructuredDataContainer();
    toCreate.setCreatedAt(dateHelper.getDate());
    toCreate.setCreatedBy(user);
    toCreate.setMongoId(mongoid);
    toCreate.setName(structuredDataContainerIO.getName());

    var created = structuredDataContainerDAO.createOrUpdate(toCreate);
    permissionsService.createPermissions(created, user, PermissionType.Private);
    return created;
  }

  /**
   * Searches the StructuredDataContainer in Neo4j
   *
   * @param id identifies the searched StructuredDataContainer
   * @return the StructuredDataContainer with matching id or null
   */
  @Override
  public StructuredDataContainer getContainer(long id) {
    StructuredDataContainer structuredDataContainer = structuredDataContainerDAO.findByNeo4jId(id);
    if (structuredDataContainer == null || structuredDataContainer.isDeleted()) {
      Log.errorf("Structured Data Container with id %s is null or deleted", id);
      return null;
    }
    return structuredDataContainer;
  }

  /**
   * Searches the database for all StructuredDataContainers
   *
   * @param params   QueryParamsHelper
   * @param username the name of the user
   * @return a list of StructuredDataContainers
   */
  @Override
  public List<StructuredDataContainer> getAllContainers(QueryParamHelper params, String username) {
    var containers = structuredDataContainerDAO.findAllStructuredDataContainers(params, username);
    return containers;
  }

  /**
   * Deletes a StructuredDataContainer in Neo4j
   *
   * @param structuredDataId identifies the StructuredDataContainer
   * @param username         identifies the deleting user
   * @return a boolean to determine if StructuredDataContainer was successfully
   *         deleted
   */
  @Override
  public boolean deleteContainer(long structuredDataId, String username) {
    var user = userDAO.find(username);
    StructuredDataContainer structuredDataContainer = structuredDataContainerDAO.findByNeo4jId(structuredDataId);
    if (structuredDataContainer == null) {
      return false;
    }
    String mongoid = structuredDataContainer.getMongoId();
    structuredDataContainer.setDeleted(true);
    structuredDataContainer.setUpdatedAt(dateHelper.getDate());
    structuredDataContainer.setUpdatedBy(user);
    structuredDataContainerDAO.createOrUpdate(structuredDataContainer);
    return structuredDataService.deleteStructuredDataContainer(mongoid);
  }

  /**
   * Upload structured data
   *
   * @param structuredDataContainerID identifies the container
   * @param payload                   the payload to upload
   * @return StructuredData with the new oid
   */
  public StructuredData createStructuredData(long structuredDataContainerID, StructuredDataPayload payload) {
    var structuredDataContainer = structuredDataContainerDAO.findByNeo4jId(structuredDataContainerID);
    if (structuredDataContainer == null || structuredDataContainer.isDeleted()) {
      Log.errorf("Structured Data Container with id %s is null or deleted", structuredDataContainerID);
      return null;
    }
    var result = structuredDataService.createStructuredData(structuredDataContainer.getMongoId(), payload);
    if (result == null) {
      Log.error("Failed to create structured data");
      return null;
    }
    structuredDataContainer.addStructuredData(result);
    structuredDataContainerDAO.createOrUpdate(structuredDataContainer);
    return result;
  }

  /**
   * Get uploaded structured data
   *
   * @param structuredDataContainerID identifies the container
   * @param oid                       identifies the structured data within the
   *                                  container
   * @return StructuredDataPayload
   */
  public StructuredDataPayload getStructuredData(long structuredDataContainerID, String oid) {
    var structuredDataContainer = structuredDataContainerDAO.findLightByNeo4jId(structuredDataContainerID);
    if (structuredDataContainer == null || structuredDataContainer.isDeleted()) {
      Log.errorf("Structured Data Container with id %s is null or deleted", structuredDataContainerID);
      return null;
    }
    var result = structuredDataService.getPayload(structuredDataContainer.getMongoId(), oid);
    return result;
  }

  /**
   * Delete one single structured data object
   *
   * @param structuredDataContainerID identifies the container
   * @param oid                       identifies the structured data within the
   *                                  container
   * @return Whether the deletion was successful or not
   */
  public boolean deleteStructuredData(long structuredDataContainerID, String oid) {
    var structuredDataContainer = structuredDataContainerDAO.findByNeo4jId(structuredDataContainerID);
    if (structuredDataContainer == null || structuredDataContainer.isDeleted()) return false;
    var result = structuredDataService.deletePayload(structuredDataContainer.getMongoId(), oid);
    if (result) {
      var newStructuredDatas = structuredDataContainer
        .getStructuredDatas()
        .stream()
        .filter(f -> !f.getOid().equals(oid))
        .toList();
      structuredDataContainer.setStructuredDatas(newStructuredDatas);
      structuredDataContainerDAO.createOrUpdate(structuredDataContainer);
    }
    return result;
  }
}
