package de.dlr.shepard.context.references.structureddata.services;

import de.dlr.shepard.auth.security.PermissionsUtil;
import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.exceptions.InvalidRequestException;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.references.IReferenceService;
import de.dlr.shepard.context.references.structureddata.daos.StructuredDataReferenceDAO;
import de.dlr.shepard.context.references.structureddata.entities.StructuredDataReference;
import de.dlr.shepard.context.references.structureddata.io.StructuredDataReferenceIO;
import de.dlr.shepard.context.version.daos.VersionDAO;
import de.dlr.shepard.context.version.entities.Version;
import de.dlr.shepard.data.structureddata.daos.StructuredDataContainerDAO;
import de.dlr.shepard.data.structureddata.daos.StructuredDataDAO;
import de.dlr.shepard.data.structureddata.entities.StructuredDataPayload;
import de.dlr.shepard.data.structureddata.services.StructuredDataService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;

@RequestScoped
public class StructuredDataReferenceService
  implements IReferenceService<StructuredDataReference, StructuredDataReferenceIO> {

  private StructuredDataReferenceDAO structuredDataReferenceDAO;
  private DataObjectDAO dataObjectDAO;
  private StructuredDataContainerDAO containerDAO;
  private StructuredDataDAO structuredDataDAO;
  private UserDAO userDAO;
  private VersionDAO versionDAO;
  private DateHelper dateHelper;
  private StructuredDataService structuredDataService;
  private PermissionsUtil permissionsUtil;

  StructuredDataReferenceService() {}

  @Inject
  public StructuredDataReferenceService(
    StructuredDataReferenceDAO structuredDataReferenceDAO,
    DataObjectDAO dataObjectDAO,
    StructuredDataContainerDAO containerDAO,
    StructuredDataDAO structuredDataDAO,
    UserDAO userDAO,
    VersionDAO versionDAO,
    DateHelper dateHelper,
    StructuredDataService structuredDataService,
    PermissionsUtil permissionsUtil
  ) {
    this.structuredDataReferenceDAO = structuredDataReferenceDAO;
    this.dataObjectDAO = dataObjectDAO;
    this.containerDAO = containerDAO;
    this.structuredDataDAO = structuredDataDAO;
    this.userDAO = userDAO;
    this.versionDAO = versionDAO;
    this.dateHelper = dateHelper;
    this.structuredDataService = structuredDataService;
    this.permissionsUtil = permissionsUtil;
  }

  @Override
  public StructuredDataReference createReferenceByShepardId(
    long dataObjectShepardId,
    StructuredDataReferenceIO structuredDataReference,
    String username
  ) {
    var user = userDAO.find(username);
    var dataObject = dataObjectDAO.findLightByShepardId(dataObjectShepardId);
    var container = containerDAO.findLightByNeo4jId(structuredDataReference.getStructuredDataContainerId());
    if (container == null || container.isDeleted()) throw new InvalidBodyException("invalid container");
    var toCreate = new StructuredDataReference();
    toCreate.setCreatedAt(dateHelper.getDate());
    toCreate.setCreatedBy(user);
    toCreate.setDataObject(dataObject);
    toCreate.setName(structuredDataReference.getName());
    toCreate.setStructuredDataContainer(container);

    // Get existing structured data
    for (var oid : structuredDataReference.getStructuredDataOids()) {
      var structuredData = structuredDataDAO.find(container.getId(), oid);
      if (structuredData != null) {
        toCreate.addStructuredData(structuredData);
      } else {
        Log.warnf("Could not find structured data with oid: %s", oid);
      }
    }

    StructuredDataReference created = structuredDataReferenceDAO.createOrUpdate(toCreate);
    created.setShepardId(created.getId());
    created = structuredDataReferenceDAO.createOrUpdate(created);
    Version version = versionDAO.findVersionLightByNeo4jId(dataObject.getId());
    versionDAO.createLink(created.getId(), version.getUid());
    return created;
  }

  @Override
  public List<StructuredDataReference> getAllReferencesByDataObjectShepardId(long dataObjectShepardId) {
    var references = structuredDataReferenceDAO.findByDataObjectShepardId(dataObjectShepardId);
    return references;
  }

  /**
   * Searches the neo4j database for a StructuredDataReference
   *
   * @param shepardId identifies the searched StructuredDataReference
   *
   * @return the StructuredDataReference with the given id or null
   */
  @Override
  public StructuredDataReference getReferenceByShepardId(long shepardId) {
    StructuredDataReference structuredDataReference = structuredDataReferenceDAO.findByShepardId(shepardId);
    if (structuredDataReference == null || structuredDataReference.isDeleted()) {
      Log.errorf("Structured Data Reference with id %s is null or deleted", shepardId);
      return null;
    }
    return structuredDataReference;
  }

  /**
   * set the deleted flag for the Reference
   *
   * @param structuredDataReferenceShepardId identifies the StructuredDataReference to be deleted
   * @param username the deleting user
   * @return a boolean to identify if the StructuredDataReference was successfully removed
   */
  @Override
  public boolean deleteReferenceByShepardId(long structuredDataReferenceShepardId, String username) {
    StructuredDataReference structuredDataReference = structuredDataReferenceDAO.findByShepardId(
      structuredDataReferenceShepardId
    );
    var user = userDAO.find(username);
    structuredDataReference.setDeleted(true);
    structuredDataReference.setUpdatedBy(user);
    structuredDataReference.setUpdatedAt(dateHelper.getDate());
    structuredDataReferenceDAO.createOrUpdate(structuredDataReference);
    return true;
  }

  /**
   * Returns all structured data objects with payload. The payload attribute is null when the container is not accessible.
   *
   * @param structuredDataReferenceShepardId identifies the sd reference
   * @param username the current user
   * @return a list of StructuredDataPayload
   */
  public List<StructuredDataPayload> getAllPayloadsByShepardId(long structuredDataReferenceShepardId, String username) {
    StructuredDataReference reference = structuredDataReferenceDAO.findByShepardId(structuredDataReferenceShepardId);

    // Return empty structured data objects when the container is not accessible
    if (
      reference.getStructuredDataContainer() == null ||
      reference.getStructuredDataContainer().isDeleted() ||
      !permissionsUtil.isAccessTypeAllowedForUser(
        reference.getStructuredDataContainer().getId(),
        AccessType.Read,
        username
      )
    ) return reference.getStructuredDatas().stream().map(sd -> new StructuredDataPayload(sd, null)).toList();

    String mongoId = reference.getStructuredDataContainer().getMongoId();
    var result = new ArrayList<StructuredDataPayload>(reference.getStructuredDatas().size());
    for (var structuredData : reference.getStructuredDatas()) {
      var payload = structuredDataService.getPayload(mongoId, structuredData.getOid());
      if (payload != null) result.add(payload);
      else result.add(new StructuredDataPayload(structuredData, null));
    }
    return result;
  }

  /**
   * Returns a specific StructuredDataPayload
   *
   * @param structuredDataReferenceShepardId identifies the sd reference
   * @param oid identifies the structured data
   * @param username the current user
   * @return StructuredDataPayload
   * @throws InvalidRequestException when container is not accessible
   * @throws InvalidAuthException when the user is not authorized to access the container
   */
  public StructuredDataPayload getPayloadByShepardId(
    long structuredDataReferenceShepardId,
    String oid,
    String username
  ) {
    StructuredDataReference reference = structuredDataReferenceDAO.findByShepardId(structuredDataReferenceShepardId);
    if (
      reference.getStructuredDataContainer() == null || reference.getStructuredDataContainer().isDeleted()
    ) throw new InvalidRequestException("The structured data container in question is not accessible");

    long containerId = reference.getStructuredDataContainer().getId();
    if (
      !permissionsUtil.isAccessTypeAllowedForUser(containerId, AccessType.Read, username)
    ) throw new InvalidAuthException("You are not authorized to access this structured data");

    String mongoId = reference.getStructuredDataContainer().getMongoId();
    return structuredDataService.getPayload(mongoId, oid);
  }
}
