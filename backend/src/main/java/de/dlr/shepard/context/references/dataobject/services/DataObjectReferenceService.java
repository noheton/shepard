package de.dlr.shepard.context.references.dataobject.services;

import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.entities.DataObject;
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
  UserDAO userDAO;

  @Inject
  DateHelper dateHelper;

  @Override
  public List<DataObjectReference> getAllReferencesByDataObjectShepardId(long dataObjectShepardId, UUID versionUID) {
    var references = dataObjectReferenceDAO.findByDataObjectShepardId(dataObjectShepardId, versionUID);
    return references;
  }

  @Override
  public DataObjectReference getReferenceByShepardId(long dataObjectReferenceShepardId, UUID versionUID) {
    var reference = dataObjectReferenceDAO.findByShepardId(dataObjectReferenceShepardId, versionUID);
    if (reference == null || reference.isDeleted()) {
      Log.errorf("Data Object Reference with id %s is null or deleted", dataObjectReferenceShepardId);
      return null;
    }
    return reference;
  }

  @Override
  public DataObjectReference createReferenceByShepardId(
    long dataObjectShepardId,
    DataObjectReferenceIO dataObjectReference,
    String username
  ) {
    User user = userDAO.find(username);
    DataObject dataObject = dataObjectService.getDataObject(dataObjectShepardId);
    DataObject referenced = dataObjectService
      .getDataObjectOptional(dataObjectReference.getReferencedDataObjectId())
      .orElseThrow(() ->
        new InvalidBodyException(
          String.format(
            "The referenced dataObject with id %d could not be found.",
            dataObjectReference.getReferencedDataObjectId()
          )
        )
      );

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

  @Override
  public boolean deleteReferenceByShepardId(long dataObjectReferenceShepardId, String username) {
    User user = userDAO.find(username);
    DataObjectReference old = dataObjectReferenceDAO.findByShepardId(dataObjectReferenceShepardId);
    old.setDeleted(true);
    old.setUpdatedAt(dateHelper.getDate());
    old.setUpdatedBy(user);
    dataObjectReferenceDAO.createOrUpdate(old);
    return true;
  }

  public DataObject getPayloadByShepardId(long dataObjectReferenceShepardId, UUID versionUID) {
    DataObjectReference reference = dataObjectReferenceDAO.findByShepardId(dataObjectReferenceShepardId, versionUID);
    if (reference.getReferencedDataObject() != null) {
      return dataObjectService.getDataObject(reference.getReferencedDataObject().getShepardId());
    }
    Log.errorf("Data Object referenced by Data Object reference with id %s is deleted", reference.getShepardId());
    return null;
  }
}
