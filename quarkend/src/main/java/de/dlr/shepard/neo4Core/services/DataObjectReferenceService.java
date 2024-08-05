package de.dlr.shepard.neo4Core.services;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.neo4Core.dao.DataObjectDAO;
import de.dlr.shepard.neo4Core.dao.DataObjectReferenceDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.dao.VersionDAO;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.DataObjectReference;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.entities.Version;
import de.dlr.shepard.neo4Core.io.DataObjectReferenceIO;
import de.dlr.shepard.util.DateHelper;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DataObjectReferenceService implements IReferenceService<DataObjectReference, DataObjectReferenceIO> {

  private DataObjectReferenceDAO dataObjectReferenceDAO = new DataObjectReferenceDAO();
  private DataObjectDAO dataObjectDAO = new DataObjectDAO();
  private VersionDAO versionDAO = new VersionDAO();
  private UserDAO userDAO = new UserDAO();
  private DateHelper dateHelper = new DateHelper();

  @Override
  public List<DataObjectReference> getAllReferencesByDataObjectShepardId(long dataObjectShepardId) {
    var references = dataObjectReferenceDAO.findByDataObjectShepardId(dataObjectShepardId);
    return references;
  }

  @Override
  public DataObjectReference getReferenceByShepardId(long dataObjectReferenceShepardId) {
    var reference = dataObjectReferenceDAO.findByShepardId(dataObjectReferenceShepardId);
    if (reference == null || reference.isDeleted()) {
      log.error("Data Object Reference with id {} is null or deleted", dataObjectReferenceShepardId);
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
    toCreate.setCreatedAt(DateHelper.getDate());
    toCreate.setCreatedBy(user);
    toCreate.setDataObject(dataObject);
    toCreate.setName(dataObjectReference.getName());
    toCreate.setReferencedDataObject(referenced);
    toCreate.setRelationship(dataObjectReference.getRelationship());
    DataObjectReference created = dataObjectReferenceDAO.createOrUpdate(toCreate);
    created.setShepardId(created.getId());
    created = dataObjectReferenceDAO.createOrUpdate(created);
    Version version = versionDAO.findVersionByNeo4jId(dataObject.getId());
    versionDAO.createLink(created.getId(), version.getUid().toString());
    return created;
  }

  @Override
  public boolean deleteReferenceByShepardId(long dataObjectReferenceShepardId, String username) {
    User user = userDAO.find(username);
    DataObjectReference old = dataObjectReferenceDAO.findByShepardId(dataObjectReferenceShepardId);
    old.setDeleted(true);
    old.setUpdatedAt(DateHelper.getDate());
    old.setUpdatedBy(user);
    dataObjectReferenceDAO.createOrUpdate(old);
    return true;
  }

  public DataObject getPayloadByShepardId(long dataObjectReferenceShepardId) {
    DataObjectReference reference = dataObjectReferenceDAO.findByShepardId(dataObjectReferenceShepardId);
    DataObject dataObject = dataObjectDAO.findByShepardId(reference.getReferencedDataObject().getShepardId());
    if (dataObject.isDeleted()) {
      log.error("Data Object with id {} is deleted", reference.getReferencedDataObject().getId());
      return null;
    }
    return dataObject;
  }
}
