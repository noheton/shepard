package de.dlr.shepard.context.references.dataobject.services;

import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.references.IReferenceService;
import de.dlr.shepard.context.references.dataobject.daos.CollectionReferenceDAO;
import de.dlr.shepard.context.references.dataobject.entities.CollectionReference;
import de.dlr.shepard.context.references.dataobject.io.CollectionReferenceIO;
import de.dlr.shepard.context.version.services.VersionService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;

@RequestScoped
public class CollectionReferenceService implements IReferenceService<CollectionReference, CollectionReferenceIO> {

  @Inject
  CollectionReferenceDAO collectionReferenceDAO;

  @Inject
  DataObjectService dataObjectService;

  @Inject
  VersionService versionService;

  @Inject
  UserDAO userDAO;

  @Inject
  DateHelper dateHelper;

  @Inject
  CollectionService collectionService;

  @Override
  public List<CollectionReference> getAllReferencesByDataObjectShepardId(long dataObjectShepardId, UUID versionUID) {
    var references = collectionReferenceDAO.findByDataObjectShepardId(dataObjectShepardId);
    return references;
  }

  @Override
  public CollectionReference getReferenceByShepardId(long collectionReferenceShepardId, UUID versionUID) {
    var reference = collectionReferenceDAO.findByShepardId(collectionReferenceShepardId, versionUID);
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
    DataObject dataObject = dataObjectService.getDataObject(dataObjectShepardId);
    Collection referenced = collectionService
      .getCollectionOptional(collectionReference.getReferencedCollectionId())
      .orElseThrow(() ->
        new InvalidBodyException(
          String.format(
            "The referenced collection with id %d could not be found.",
            collectionReference.getReferencedCollectionId()
          )
        )
      );

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
    versionService.attachToVersionOfVersionableEntityAndReturnVersion(dataObject.getId(), created.getId());
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

  public Collection getPayloadByShepardId(long collectionReferenceShepardId, UUID versionUID) {
    var reference = collectionReferenceDAO.findByShepardId(collectionReferenceShepardId, versionUID);
    if (reference.getReferencedCollection() != null) {
      var referencedCollectionOptional = collectionService.getCollectionOptionalWithDataObjectsAndIncomingReferences(
        reference.getReferencedCollection().getShepardId()
      );
      if (referencedCollectionOptional.isPresent()) {
        return referencedCollectionOptional.get();
      }
    }

    Log.errorf("Collection referenced by collection reference with id %s is deleted", reference.getShepardId());
    throw new InvalidPathException(
      String.format(
        "ID Error - Collection referenced by collection reference with id %s is deleted",
        reference.getShepardId()
      )
    );
  }
}
