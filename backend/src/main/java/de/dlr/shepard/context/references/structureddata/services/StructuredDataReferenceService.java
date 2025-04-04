package de.dlr.shepard.context.references.structureddata.services;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.exceptions.InvalidRequestException;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.references.IReferenceService;
import de.dlr.shepard.context.references.structureddata.daos.StructuredDataReferenceDAO;
import de.dlr.shepard.context.references.structureddata.entities.StructuredDataReference;
import de.dlr.shepard.context.references.structureddata.io.StructuredDataReferenceIO;
import de.dlr.shepard.context.version.services.VersionService;
import de.dlr.shepard.data.structureddata.daos.StructuredDataDAO;
import de.dlr.shepard.data.structureddata.entities.StructuredDataContainer;
import de.dlr.shepard.data.structureddata.entities.StructuredDataPayload;
import de.dlr.shepard.data.structureddata.services.StructuredDataContainerService;
import de.dlr.shepard.data.structureddata.services.StructuredDataService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RequestScoped
public class StructuredDataReferenceService
  implements IReferenceService<StructuredDataReference, StructuredDataReferenceIO> {

  @Inject
  StructuredDataReferenceDAO structuredDataReferenceDAO;

  @Inject
  DataObjectService dataObjectService;

  @Inject
  StructuredDataDAO structuredDataDAO;

  @Inject
  StructuredDataContainerService structuredDataContainerService;

  @Inject
  VersionService versionService;

  @Inject
  DateHelper dateHelper;

  @Inject
  UserService userService;

  @Inject
  StructuredDataService structuredDataService;

  @Inject
  PermissionsService permissionsService;

  @Inject
  AuthenticationContext authenticationContext;

  @Inject
  CollectionService collectionService;

  @Override
  public StructuredDataReference createReference(
    long collectionShepardId,
    long dataObjectShepardId,
    StructuredDataReferenceIO structuredDataReference
  ) {
    DataObject dataObject = dataObjectService.getDataObject(collectionShepardId, dataObjectShepardId);
    collectionService.assertIsAllowedToEditCollection(collectionShepardId);

    User user = userService.getCurrentUser();

    StructuredDataContainer container;
    try {
      container = structuredDataContainerService.getContainer(structuredDataReference.getStructuredDataContainerId());
    } catch (InvalidPathException e) {
      throw new InvalidBodyException(
        String.format(
          "ID ERROR - Structured Data Container with id %s is null or deleted",
          structuredDataReference.getStructuredDataContainerId()
        )
      );
    }

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
    versionService.attachToVersionOfVersionableEntityAndReturnVersion(dataObject.getId(), created.getId());
    return created;
  }

  /**
   * Gets Structured Data Reference list for a given dataobject.
   *
   * @param collectionShepardId
   * @param dataObjectShepardId
   * @param versionUID the version UUID
   * @return List<StructuredDataReference>
   * @throws InvalidPathException If collection or dataobject cannot be found, or no association between dataobject and collection exists
   * @throws InvalidAuthException If user has no read permissions on collection or dataobject specified by request path
   */
  @Override
  public List<StructuredDataReference> getAllReferencesByDataObjectId(
    long collectionShepardId,
    long dataObjectShepardId,
    UUID versionUID
  ) {
    dataObjectService.getDataObject(collectionShepardId, dataObjectShepardId, versionUID);

    List<StructuredDataReference> references = structuredDataReferenceDAO.findByDataObjectShepardId(
      dataObjectShepardId
    );
    return references;
  }

  /**
   * Searches the neo4j database for a StructuredDataReference
   *
   * @param collectionShepardId
   * @param dataObjectShepardId
   * @param shepardId  identifies the searched StructuredDataReference
   * @param versionUID the collections UUID
   * @return CollectionReference
   * @throws InvalidPathException If collection reference with Id does not exist or is deleted, or if collection or dataobject Id of path is not valid
   * @throws InvalidAuthException If user has no read permissions on collection or dataobject specified by request path
   */
  @Override
  public StructuredDataReference getReference(
    long collectionShepardId,
    long dataObjectShepardId,
    long shepardId,
    UUID versionUID
  ) {
    dataObjectService.getDataObject(collectionShepardId, dataObjectShepardId, versionUID);

    StructuredDataReference structuredDataReference = structuredDataReferenceDAO.findByShepardId(shepardId, versionUID);
    if (structuredDataReference == null || structuredDataReference.isDeleted()) {
      String errorMsg = String.format("ID ERROR - Structured Data Reference with id %s is null or deleted", shepardId);
      Log.errorf(errorMsg);
      throw new InvalidPathException(errorMsg);
    }

    if (
      structuredDataReference.getDataObject() == null ||
      !structuredDataReference.getDataObject().getShepardId().equals(dataObjectShepardId)
    ) {
      Log.error("ID ERROR - There is no association between dataObject and reference");
      throw new InvalidPathException("ID ERROR - There is no association between dataObject and reference");
    }

    return structuredDataReference;
  }

  /**
   * Deletes the Structured Data reference.
   *
   * @param collectionShepardId
   * @param dataObjectShepardId
   * @param dataObjectReferenceShepardId
   * @throws InvalidPathException if collection or dataobject specified by their Ids are null or deleted
   * @throws InvalidAuthException if user has no permissions to request the collection, which the reference is assigned to
   * @throws InvalidBodyException If user has no permissions to access the referenced Structured Data or the referenced collection cannot be found
   */
  @Override
  public void deleteReference(
    long collectionShepardId,
    long dataObjectShepardId,
    long structuredDataReferenceShepardId
  ) {
    StructuredDataReference structuredDataReference = getReference(
      collectionShepardId,
      dataObjectShepardId,
      structuredDataReferenceShepardId,
      null
    );
    collectionService.assertIsAllowedToEditCollection(collectionShepardId);

    User user = userService.getCurrentUser();
    structuredDataReference.setDeleted(true);
    structuredDataReference.setUpdatedBy(user);
    structuredDataReference.setUpdatedAt(dateHelper.getDate());
    structuredDataReferenceDAO.createOrUpdate(structuredDataReference);
  }

  /**
   * Returns all structured data objects with payload.
   *
   * The payload attribute is null when the container is not accessible.
   *
   * @param structuredDataReferenceShepardId identifies the sd reference
   * @return a list of StructuredDataPayload
   */
  public List<StructuredDataPayload> getAllPayloads(
    long collectionShepardId,
    long dataObjectShepardId,
    long structuredDataReferenceShepardId
  ) {
    StructuredDataReference reference = getReference(
      collectionShepardId,
      dataObjectShepardId,
      structuredDataReferenceShepardId,
      null
    );

    if (reference.getStructuredDataContainer() == null || reference.getStructuredDataContainer().isDeleted()) {
      String errorMsg = String.format(
        "StructuredData Container referenced by StructuredData Reference with Id %s is null or deleted",
        structuredDataReferenceShepardId
      );
      Log.error(errorMsg);
      throw new NotFoundException(errorMsg);
    }

    try {
      // check that referenced container is actually accessible
      structuredDataContainerService.getContainer(reference.getStructuredDataContainer().getId());
    } catch (InvalidPathException ex) {
      throw new InvalidRequestException(ex.getMessage());
    }

    String mongoId = reference.getStructuredDataContainer().getMongoId();
    var result = new ArrayList<StructuredDataPayload>(reference.getStructuredDatas().size());
    for (var structuredData : reference.getStructuredDatas()) {
      try {
        StructuredDataPayload payload = structuredDataService.getPayload(mongoId, structuredData.getOid());
        result.add(payload);
      } catch (NotFoundException ex) {
        result.add(new StructuredDataPayload(structuredData, null));
      }
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
  public StructuredDataPayload getPayload(
    long collectionShepardId,
    long dataObjectShepardId,
    long structuredDataReferenceShepardId,
    String oid
  ) {
    StructuredDataReference reference = getReference(
      collectionShepardId,
      dataObjectShepardId,
      structuredDataReferenceShepardId,
      null
    );

    if (reference.getStructuredDataContainer() == null || reference.getStructuredDataContainer().isDeleted()) {
      String errorMsg = String.format(
        "Structured Data Container referenced by StructuredDataReference with id %s is deleted",
        reference.getShepardId()
      );
      Log.errorf(errorMsg);
      throw new InvalidRequestException(errorMsg);
    }

    try {
      structuredDataContainerService.getContainer(reference.getStructuredDataContainer().getId());
    } catch (InvalidPathException e) {
      throw new NotFoundException(
        String.format(
          "The StructuredData Container with id %d could not be found.",
          reference.getStructuredDataContainer().getId()
        )
      );
    }

    String mongoId = reference.getStructuredDataContainer().getMongoId();
    return structuredDataService.getPayload(mongoId, oid);
  }
}
