package de.dlr.shepard.context.references.dataobject.services;

import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.DataObject;
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

  private DataObjectReferenceDAO dataObjectReferenceDAO;
  private DataObjectDAO dataObjectDAO;
  private VersionService versionService;
  private UserDAO userDAO;
  private DateHelper dateHelper;

  DataObjectReferenceService() {}

  @Inject
  public DataObjectReferenceService(
    DataObjectReferenceDAO dataObjectReferenceDAO,
    DataObjectDAO dataObjectDAO,
    VersionService versionService,
    UserDAO userDAO,
    DateHelper dateHelper
  ) {
    this.dataObjectReferenceDAO = dataObjectReferenceDAO;
    this.dataObjectDAO = dataObjectDAO;
    this.versionService = versionService;
    this.userDAO = userDAO;
    this.dateHelper = dateHelper;
  }

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
    DataObject dataObject = dataObjectDAO.findLightByShepardId(dataObjectShepardId);
    DataObject referenced = dataObjectDAO.findLightByShepardId(dataObjectReference.getReferencedDataObjectId());
    if (referenced == null || referenced.isDeleted()) {
      throw new InvalidBodyException(
        String.format(
          "The referenced dataObject with id %d could not be found.",
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
      return dataObjectDAO.findByNeo4jId(reference.getReferencedDataObject().getId());
    }
    Log.errorf("Data Object referenced by Data Object reference with id %s is deleted", reference.getShepardId());
    return null;
  }
}
