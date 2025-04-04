package de.dlr.shepard.context.references.dataobject.services;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.references.IReferenceService;
import de.dlr.shepard.context.references.dataobject.daos.DataObjectReferenceDAO;
import de.dlr.shepard.context.references.dataobject.entities.DataObjectReference;
import de.dlr.shepard.context.references.dataobject.io.DataObjectReferenceIO;
import de.dlr.shepard.context.version.entities.Version;
import de.dlr.shepard.context.version.services.VersionService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.UUID;

@RequestScoped
public class DataObjectReferenceService implements IReferenceService<DataObjectReference, DataObjectReferenceIO> {

  @Inject
  DataObjectReferenceDAO dataObjectReferenceDAO;

  @Inject
  DataObjectService dataObjectService;

  @Inject
  VersionService versionService;

  @Inject
  UserService userService;

  @Inject
  DateHelper dateHelper;

  @Inject
  CollectionService collectionService;

  /**
   * Gets DataObjectReference list for a given dataobject.
   *
   * @param collectionShepardId
   * @param dataObjectShepardId
   * @param versionUID          the version UUID
   * @return List<DataObjectReference>
   * @throws InvalidPathException If collection or dataobject cannot be found, or
   *                              no association between dataobject and collection
   *                              exists
   * @throws InvalidAuthException If user has no read permissions on collection or
   *                              dataobject specified by request path
   */
  @Override
  public List<DataObjectReference> getAllReferencesByDataObjectId(
    long collectionShepardId,
    long dataObjectShepardId,
    UUID versionUID
  ) {
    dataObjectService.getDataObject(collectionShepardId, dataObjectShepardId, versionUID);
    var references = dataObjectReferenceDAO.findByDataObjectShepardId(dataObjectShepardId, versionUID);
    return references;
  }

  /**
   * Gets DataObjectReference by dataObjectReference Id.
   *
   * @param collectionShepardId
   * @param dataObjectShepardId
   * @param dataObjectReferenceShepardId
   * @param versionUID                   the collections UUID
   * @return CollectionReference
   * @throws InvalidPathException If reference with Id does not exist or is
   *                              deleted, or if collection or dataobject Id of
   *                              path is not valid
   * @throws InvalidAuthException If user has no read permissions on collection or
   *                              dataobject specified by request path
   */
  @Override
  public DataObjectReference getReference(
    long collectionShepardId,
    long dataObjectShepardId,
    long dataObjectReferenceShepardId,
    UUID versionUID
  ) {
    dataObjectService.getDataObject(collectionShepardId, dataObjectShepardId, versionUID);

    DataObjectReference reference = dataObjectReferenceDAO.findByShepardId(dataObjectReferenceShepardId, versionUID);
    if (reference == null || reference.isDeleted()) {
      String errorMsg = String.format(
        "ID ERROR - Data Object Reference with id %s is null or deleted",
        dataObjectReferenceShepardId
      );
      Log.error(errorMsg);
      throw new InvalidPathException(errorMsg);
    }

    if (reference.getDataObject() == null || !reference.getDataObject().getShepardId().equals(dataObjectShepardId)) {
      String errorMsg = "ID ERROR - There is no association between dataObject and reference";
      Log.error(errorMsg);
      throw new InvalidPathException(errorMsg);
    }

    return reference;
  }

  /**
   * Creates a new DataObject reference
   *
   * @param collectionShepardId
   * @param dataObjectShepardId DataObject id for the reference to be created
   * @param dataObjectReference Reference object for DataObjects
   * @return DataObjectReference
   * @throws InvalidPathException if collection or dataobject specified by their
   *                              Ids are null or deleted
   * @throws InvalidAuthException if user has no permissions to request the
   *                              collection, which the reference is assigned to
   * @throws InvalidBodyException if referenced DataObject cannot be found or
   *                              requester does not have enough permissions
   */
  @Override
  public DataObjectReference createReference(
    long collectionShepardId,
    long dataObjectShepardId,
    DataObjectReferenceIO dataObjectReference
  ) {
    DataObject dataObject = dataObjectService.getDataObject(collectionShepardId, dataObjectShepardId);
    collectionService.assertIsAllowedToEditCollection(collectionShepardId);

    User user = userService.getCurrentUser();
    DataObject referenced;

    try {
      referenced = dataObjectService.getDataObject(dataObjectReference.getReferencedDataObjectId());
    } catch (InvalidPathException e) {
      throw new InvalidBodyException(
        String.format(
          "The referenced DataObject with id %d could not be found.",
          dataObjectReference.getReferencedDataObjectId()
        )
      );
    } catch (InvalidAuthException e) {
      throw new InvalidBodyException(
        String.format(
          "You do not have permissions to access the referenced DataObject with id %d.",
          dataObjectReference.getReferencedDataObjectId()
        )
      );
    }

    DataObjectReference toCreate = new DataObjectReference();
    toCreate.setCreatedAt(dateHelper.getDate());
    toCreate.setCreatedBy(user);
    toCreate.setDataObject(dataObject);
    toCreate.setName(dataObjectReference.getName());
    toCreate.setReferencedDataObject(referenced);
    toCreate.setRelationship(dataObjectReference.getRelationship());
    DataObjectReference created = dataObjectReferenceDAO.createOrUpdate(toCreate);
    created.setShepardId(created.getId());
    created = dataObjectReferenceDAO.createOrUpdate(created);
    Version version = versionService.attachToVersionOfVersionableEntityAndReturnVersion(
      dataObject.getId(),
      created.getId()
    );
    created.setVersion(version);
    return created;
  }

  /**
   * Deletes the dataobject reference.
   *
   * @param collectionShepardId
   * @param dataObjectShepardId
   * @param dataObjectReferenceShepardId
   * @throws InvalidPathException if collection or dataobject specified by their
   *                              Ids are null or deleted
   * @throws InvalidAuthException if user has no permissions to request the
   *                              collection, which the reference is assigned to
   * @throws InvalidBodyException If user has no permissions to access the
   *                              referenced collection or the referenced
   *                              collection cannot be found
   */
  @Override
  public void deleteReference(long collectionShepardId, long dataObjectShepardId, long dataObjectReferenceShepardId) {
    DataObjectReference old = getReference(
      collectionShepardId,
      dataObjectShepardId,
      dataObjectReferenceShepardId,
      null
    );
    collectionService.assertIsAllowedToEditCollection(collectionShepardId);

    User user = userService.getCurrentUser();
    old.setDeleted(true);
    old.setUpdatedAt(dateHelper.getDate());
    old.setUpdatedBy(user);
    dataObjectReferenceDAO.createOrUpdate(old);
  }

  /**
   * Returns the payload of a referenced DataObject
   *
   * @param collectionShepardId
   * @param dataObjectShepardId
   * @param dataObjectReferenceShepardId
   * @param versionUID
   * @return DataObject
   * @throws InvalidPathException if collection or dataobject specified by their
   *                              Ids are null or deleted, or if DataObject
   *                              referenced by DataObject reference is deleted
   * @throws InvalidAuthException if user has no permissions to request the
   *                              collection, which the reference is assigned to
   * @throws InvalidBodyException if the referenced DataObject cannot be found or
   *                              is deleted or the requester do not have
   *                              permissions to access the referenced DataObject
   */
  public DataObject getPayload(
    long collectionShepardId,
    long dataObjectShepardId,
    long dataObjectReferenceShepardId,
    UUID versionUID
  ) {
    DataObjectReference reference = getReference(
      collectionShepardId,
      dataObjectShepardId,
      dataObjectReferenceShepardId,
      versionUID
    );

    if (reference.getReferencedDataObject() == null || reference.getReferencedDataObject().isDeleted()) {
      String errorMsg = String.format(
        "DataObject referenced by DataObject reference with id %s is not accessible",
        reference.getShepardId()
      );
      Log.errorf(errorMsg);
      throw new NotFoundException(errorMsg);
    }

    try {
      return dataObjectService.getDataObject(reference.getReferencedDataObject().getShepardId());
    } catch (InvalidPathException e) {
      throw new NotFoundException(
        String.format(
          "The referenced DataObject with id %d could not be found.",
          reference.getReferencedDataObject().getShepardId()
        )
      );
    }
  }
}
