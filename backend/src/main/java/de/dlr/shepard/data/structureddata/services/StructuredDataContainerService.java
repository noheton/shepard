package de.dlr.shepard.data.structureddata.services;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.exceptions.InvalidRequestException;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.PermissionType;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.data.AbstractContainerService;
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
  extends AbstractContainerService<StructuredDataContainer, StructuredDataContainerIO> {

  @Inject
  StructuredDataContainerDAO structuredDataContainerDAO;

  @Inject
  StructuredDataService structuredDataService;

  @Inject
  PermissionsService permissionsService;

  @Inject
  UserService userService;

  @Inject
  DateHelper dateHelper;

  /**
   * Creates a StructuredDataContainer and stores it in Neo4J
   *
   * @param structuredDataContainerIO to be stored
   * @param username                  of the related user
   * @return the created StructuredDataContainer
   */
  @Override
  public StructuredDataContainer createContainer(StructuredDataContainerIO structuredDataContainerIO) {
    User user = userService.getCurrentUser();
    String mongoId = structuredDataService.createStructuredDataContainer();

    var toCreate = new StructuredDataContainer();
    toCreate.setCreatedAt(dateHelper.getDate());
    toCreate.setCreatedBy(user);
    toCreate.setMongoId(mongoId);
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
      String errorMsg = "ID ERROR - Structured Data Container with id %s is null or deleted".formatted(id);
      Log.error(errorMsg);
      throw new InvalidPathException(errorMsg);
    }
    assertIsAllowedToReadContainer(id);
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
  public List<StructuredDataContainer> getAllContainers(QueryParamHelper params) {
    User user = userService.getCurrentUser();
    var containers = structuredDataContainerDAO.findAllStructuredDataContainers(params, user.getUsername());
    return containers;
  }

  /**
   * Deletes a StructuredDataContainer in Neo4j
   *
   * @param structuredDataId identifies the StructuredDataContainer
   * @param username         identifies the deleting user
   * @return a boolean to determine if StructuredDataContainer was successfully
   *         deleted
   * @throws InvalidRequestException If StructuredDataContainer could not be deleted
   */
  @Override
  public void deleteContainer(long structuredDataId) {
    User user = userService.getCurrentUser();
    StructuredDataContainer structuredDataContainer = getContainer(structuredDataId);
    assertIsAllowedToDeleteContainer(structuredDataId);

    String mongoId = structuredDataContainer.getMongoId();
    structuredDataContainer.setDeleted(true);
    structuredDataContainer.setUpdatedAt(dateHelper.getDate());
    structuredDataContainer.setUpdatedBy(user);
    structuredDataContainerDAO.createOrUpdate(structuredDataContainer);

    structuredDataService.deleteStructuredDataContainer(mongoId);
  }

  /**
   * Upload structured data
   *
   * @param structuredDataContainerID identifies the container
   * @param payload                   the payload to upload
   * @return StructuredData with the new oid
   */
  public StructuredData createStructuredData(long structuredDataContainerID, StructuredDataPayload payload) {
    StructuredDataContainer structuredDataContainer = getContainer(structuredDataContainerID);
    assertIsAllowedToEditContainer(structuredDataContainerID);

    StructuredData result = structuredDataService.createStructuredData(structuredDataContainer.getMongoId(), payload);

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
    StructuredDataContainer structuredDataContainer = getContainer(structuredDataContainerID);

    return structuredDataService.getPayload(structuredDataContainer.getMongoId(), oid);
  }

  /**
   * Delete one single structured data object
   *
   * @param structuredDataContainerID identifies the container
   * @param oid                       identifies the structured data within the
   *                                  container
   */
  public void deleteStructuredData(long structuredDataContainerID, String oid) {
    StructuredDataContainer structuredDataContainer = getContainer(structuredDataContainerID);
    assertIsAllowedToEditContainer(structuredDataContainerID);

    structuredDataService.deletePayload(structuredDataContainer.getMongoId(), oid);

    List<StructuredData> newStructuredDatas = structuredDataContainer
      .getStructuredDatas()
      .stream()
      .filter(f -> !f.getOid().equals(oid))
      .toList();
    structuredDataContainer.setStructuredDatas(newStructuredDatas);
    structuredDataContainerDAO.createOrUpdate(structuredDataContainer);
  }
}
