package de.dlr.shepard.context.references.dataobject.services;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
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
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.Map;
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
  UserService userService;

  @Inject
  DateHelper dateHelper;

  @Inject
  CollectionService collectionService;

  /**
   * Gets CollectionReference list for a given dataobject.
   *
   * @param collectionShepardId
   * @param dataObjectShepardId
   * @param versionUID          the collections UUID
   * @return List<CollectionReference>
   * @throws InvalidPathException If collection or dataobject cannot be found, or
   *                              no association between dataobject and collection
   *                              exists
   * @throws InvalidAuthException If user has no read permissions on collection or
   *                              dataobject specified by request path
   */
  @Override
  public List<CollectionReference> getAllReferencesByDataObjectId(
    long collectionShepardId,
    long dataObjectShepardId,
    UUID versionUID
  ) {
    dataObjectService.getDataObject(collectionShepardId, dataObjectShepardId, versionUID);
    List<CollectionReference> references = collectionReferenceDAO.findByDataObjectShepardId(dataObjectShepardId);
    return references;
  }

  /**
   * Gets CollectionReference by Collection Id.
   *
   * @param collectionShepardId
   * @param dataObjectShepardId
   * @param collectionReferenceShepardId
   * @param versionUID                   the collections UUID
   * @return CollectionReference
   * @throws InvalidPathException If collection reference with Id does not exist
   *                              or is deleted, or if collection or dataobject Id
   *                              of path is not valid
   * @throws InvalidAuthException If user has no read permissions on collection or
   *                              dataobject specified by request path
   */
  @Override
  public CollectionReference getReference(
    long collectionShepardId,
    long dataObjectShepardId,
    long collectionReferenceShepardId,
    UUID versionUID
  ) {
    dataObjectService.getDataObject(collectionShepardId, dataObjectShepardId, versionUID);

    CollectionReference reference = collectionReferenceDAO.findByShepardId(collectionReferenceShepardId, versionUID);
    if (reference == null || reference.isDeleted()) {
      String errorMsg =
        "ID ERROR - Collection Reference with id %s is null or deleted".formatted(collectionReferenceShepardId);
      Log.error(errorMsg);
      throw new InvalidPathException(errorMsg);
    }

    if (reference.getDataObject() == null || !reference.getDataObject().getShepardId().equals(dataObjectShepardId)) {
      Log.error("ID ERROR - There is no association between dataObject and reference");
      throw new InvalidPathException("ID ERROR - There is no association between dataObject and reference");
    }

    return reference;
  }

  /**
   * Creates a new collection reference.
   *
   * @param collectionShepardId
   * @param dataObjectShepardId
   * @return CollectionReference
   * @throws InvalidPathException if collection or dataobject specified by their
   *                              Ids are null or deleted
   * @throws InvalidAuthException if user has no permissions to request the
   *                              collection, which the reference is assigned to
   * @throws InvalidBodyException if user has no permissions to access the
   *                              referenced collection or the referenced
   *                              collection cannot be found
   */
  @Override
  public CollectionReference createReference(
    long collectionShepardId,
    long dataObjectShepardId,
    CollectionReferenceIO collectionReference
  ) {
    DataObject dataObject = dataObjectService.getDataObject(collectionShepardId, dataObjectShepardId);
    collectionService.assertIsAllowedToEditCollection(collectionShepardId);

    User user = userService.getCurrentUser();
    Collection referenced;
    try {
      referenced = collectionService.getCollection(collectionReference.getReferencedCollectionId());
    } catch (InvalidPathException e) {
      throw new InvalidBodyException(
        "The referenced collection with id %d could not be found.".formatted(
            collectionReference.getReferencedCollectionId()
          )
      );
    } catch (InvalidAuthException e) {
      throw new InvalidAuthException(
        "You do not have permissions to access the referenced collection with id %d.".formatted(
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
    versionService.attachToVersionOfVersionableEntityAndReturnVersion(dataObject.getId(), created.getId());
    return created;
  }

  /**
   * Deletes the collection reference.
   *
   * @param collectionShepardId
   * @param dataObjectShepardId
   * @param collectionReferenceShepardId
   * @throws InvalidPathException if collection or dataobject specified by their
   *                              Ids are null or deleted
   * @throws InvalidAuthException if user has no permissions to request the
   *                              collection, which the reference is assigned to
   * @throws InvalidBodyException If user has no permissions to access the
   *                              referenced collection or the referenced
   *                              collection cannot be found
   */
  @Override
  public void deleteReference(long collectionShepardId, long dataObjectShepardId, long collectionReferenceShepardId) {
    CollectionReference old = getReference(
      collectionShepardId,
      dataObjectShepardId,
      collectionReferenceShepardId,
      null
    );
    collectionService.assertIsAllowedToEditCollection(collectionShepardId);

    User user = userService.getCurrentUser();
    old.setDeleted(true);
    old.setUpdatedAt(dateHelper.getDate());
    old.setUpdatedBy(user);
    collectionReferenceDAO.createOrUpdate(old);
  }

  /**
   * Returns the payload specified in the collection reference.
   *
   * @param collectionShepardId
   * @param dataObjectShepardId
   * @param collectionReferenceShepardId
   * @param versionUID
   * @return Collection
   * @throws InvalidPathException if collection or dataobject specified by their
   *                              Ids are null or deleted, or if the referenced
   *                              collection has been deleted
   * @throws InvalidAuthException if user has no permissions to request the
   *                              collection, which the reference is assigned to
   * @throws InvalidBodyException If user has no permissions to access the
   *                              referenced collection or the referenced
   *                              collection cannot be found
   */
  /**
   * Looks up a CollectionReference by its application-level UUID v7 without permission
   * checks. Used by the v2 REST handler to resolve the parent DataObject for access
   * gating before invoking mutating operations.
   * V2-SWEEP-004-1.
   *
   * @param appId UUID v7 of the reference
   * @return the matching {@link CollectionReference}, or {@code null}
   */
  public CollectionReference findByAppId(String appId) {
    return collectionReferenceDAO.findByAppId(appId);
  }

  /**
   * Applies a partial (RFC 7396 merge-patch) update to a CollectionReference looked up
   * by its application-level UUID v7 ({@code appId}).
   *
   * <p>Mutable fields: {@code name}, {@code relationship}.
   * Absent keys are left unchanged. {@code name} must not be set to {@code null} or
   * blank. {@code relationship} may be set to {@code null} to clear it.
   * V2-SWEEP-004-1.
   *
   * @param appId UUID v7 of the reference to patch
   * @param patch key/value map of fields to update (RFC 7396 semantics)
   * @return the updated {@link CollectionReference}
   * @throws InvalidPathException if no reference with that appId exists
   * @throws IllegalArgumentException if a required field is set to blank or null
   */
  public CollectionReference patchReferenceByAppId(String appId, Map<String, Object> patch) {
    CollectionReference ref = collectionReferenceDAO.findByAppId(appId);
    if (ref == null) {
      String msg = "CollectionReference with appId %s not found".formatted(appId);
      Log.error(msg);
      throw new InvalidPathException(msg);
    }

    if (patch.containsKey("name")) {
      Object v = patch.get("name");
      if (v == null || v.toString().isBlank()) {
        throw new IllegalArgumentException("name must not be blank");
      }
      ref.setName(v.toString());
    }
    if (patch.containsKey("relationship")) {
      Object v = patch.get("relationship");
      ref.setRelationship(v != null ? v.toString() : null);
    }

    User user = userService.getCurrentUser();
    ref.setUpdatedAt(dateHelper.getDate());
    ref.setUpdatedBy(user);

    return collectionReferenceDAO.createOrUpdate(ref);
  }

  public Collection getPayload(
    long collectionShepardId,
    long dataObjectShepardId,
    long collectionReferenceShepardId,
    UUID versionUID
  ) {
    CollectionReference reference = getReference(
      collectionShepardId,
      dataObjectShepardId,
      collectionReferenceShepardId,
      versionUID
    );

    if (reference.getReferencedCollection() == null || reference.getReferencedCollection().isDeleted()) {
      String errorMsg =
        "Collection referenced by CollectionReference with id %s cannot be found or is deleted".formatted(
            reference.getShepardId()
          );
      Log.errorf(errorMsg);
      throw new NotFoundException(errorMsg);
    }

    try {
      return collectionService.getCollectionWithDataObjectsAndIncomingReferences(
        reference.getReferencedCollection().getShepardId()
      );
    } catch (InvalidPathException e) {
      throw new NotFoundException(
        "The referenced collection with id %d could not be found.".formatted(
            reference.getReferencedCollection().getShepardId()
          )
      );
    }
  }
}
