package de.dlr.shepard.neo4Core.services;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.neo4Core.dao.CollectionDAO;
import de.dlr.shepard.neo4Core.dao.CollectionReferenceDAO;
import de.dlr.shepard.neo4Core.dao.DataObjectDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.dao.VersionDAO;
import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.neo4Core.entities.CollectionReference;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.entities.Version;
import de.dlr.shepard.neo4Core.io.CollectionReferenceIO;
import de.dlr.shepard.util.DateHelper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.List;

@RequestScoped
public class CollectionReferenceService implements IReferenceService<CollectionReference, CollectionReferenceIO> {

  private CollectionReferenceDAO collectionReferenceDAO;
  private DataObjectDAO dataObjectDAO;
  private CollectionDAO collectionDAO;
  private VersionDAO versionDAO;
  private UserDAO userDAO;
  private DateHelper dateHelper;

  CollectionReferenceService() {}

  @Inject
  public CollectionReferenceService(
    CollectionReferenceDAO collectionReferenceDAO,
    DataObjectDAO dataObjectDAO,
    CollectionDAO collectionDAO,
    VersionDAO versionDAO,
    UserDAO userDAO,
    DateHelper dateHelper
  ) {
    this.collectionReferenceDAO = collectionReferenceDAO;
    this.dataObjectDAO = dataObjectDAO;
    this.collectionDAO = collectionDAO;
    this.versionDAO = versionDAO;
    this.userDAO = userDAO;
    this.dateHelper = dateHelper;
  }

  @Override
  public List<CollectionReference> getAllReferencesByDataObjectShepardId(long dataObjectShepardId) {
    var references = collectionReferenceDAO.findByDataObjectShepardId(dataObjectShepardId);
    return references;
  }

  @Override
  public CollectionReference getReferenceByShepardId(long collectionReferenceShepardId) {
    var reference = collectionReferenceDAO.findByShepardId(collectionReferenceShepardId);
    if (reference == null || reference.isDeleted()) {
      Log.errorf("Collection Reference with id %s is null or deleted", collectionReferenceShepardId);
      return null;
    }
    return reference;
  }

  @Override
  public CollectionReference createReferenceByShepardId(
    long dataObjectShepardId,
    CollectionReferenceIO collectionReference,
    String username
  ) {
    User user = userDAO.find(username);
    DataObject dataObject = dataObjectDAO.findLightByShepardId(dataObjectShepardId);
    Collection referenced = collectionDAO.findLightByShepardId(collectionReference.getReferencedCollectionId());
    if (referenced == null || referenced.isDeleted()) {
      throw new InvalidBodyException(
        String.format(
          "The referenced collection with id %d could not be found.",
          collectionReference.getReferencedCollectionId()
        )
      );
    }

    CollectionReference toCreate = new CollectionReference();
    toCreate.setCreatedAt(dateHelper.getDate());
    toCreate.setCreatedBy(user);
    toCreate.setDataObject(dataObject);
    toCreate.setName(collectionReference.getName());
    toCreate.setReferencedCollection(referenced);
    toCreate.setRelationship(collectionReference.getRelationship());

    CollectionReference created = collectionReferenceDAO.createOrUpdate(toCreate);
    created.setShepardId(created.getId());
    created = collectionReferenceDAO.createOrUpdate(created);
    Version version = versionDAO.findVersionLightByNeo4jId(dataObject.getId());
    versionDAO.createLink(created.getId(), version.getUid());
    return created;
  }

  @Override
  public boolean deleteReferenceByShepardId(long collectionReferenceShepardId, String username) {
    User user = userDAO.find(username);
    CollectionReference old = collectionReferenceDAO.findByShepardId(collectionReferenceShepardId);
    old.setDeleted(true);
    old.setUpdatedAt(dateHelper.getDate());
    old.setUpdatedBy(user);
    collectionReferenceDAO.createOrUpdate(old);
    return true;
  }

  public Collection getPayloadByShepardId(long collectionReferenceShepardId) {
    var reference = collectionReferenceDAO.findByShepardId(collectionReferenceShepardId);
    var collection = collectionDAO.findByShepardId(reference.getReferencedCollection().getShepardId());
    if (collection.isDeleted()) {
      Log.errorf("Collection with id %s is deleted", reference.getReferencedCollection().getId());
      return null;
    }
    return collection;
  }
}
