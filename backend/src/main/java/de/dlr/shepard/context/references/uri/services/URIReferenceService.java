package de.dlr.shepard.context.references.uri.services;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.references.IReferenceService;
import de.dlr.shepard.context.references.uri.daos.URIReferenceDAO;
import de.dlr.shepard.context.references.uri.entities.URIReference;
import de.dlr.shepard.context.references.uri.io.URIReferenceIO;
import de.dlr.shepard.context.version.services.VersionService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RequestScoped
public class URIReferenceService implements IReferenceService<URIReference, URIReferenceIO> {

  @Inject
  URIReferenceDAO uRIReferenceDAO;

  @Inject
  DataObjectService dataObjectService;

  @Inject
  CollectionService collectionService;

  @Inject
  UserService userService;

  @Inject
  VersionService versionService;

  @Inject
  DateHelper dateHelper;

  @Inject
  AuthenticationContext authenticationContext;

  /**
   * Gets URIReference list for a given dataobject.
   *
   * @param collectionShepardId
   * @param dataObjectShepardId
   * @param versionUID the version UUID
   * @return List<URIReference>
   * @throws InvalidPathException If collection or dataobject cannot be found, or no association between dataobject and collection exists
   * @throws InvalidAuthException If user has no read permissions on collection or dataobject specified by request path
   */
  @Override
  public List<URIReference> getAllReferencesByDataObjectId(
    long collectionShepardId,
    long dataObjectShepardId,
    UUID versionUID
  ) {
    dataObjectService.getDataObject(collectionShepardId, dataObjectShepardId, versionUID);

    var references = uRIReferenceDAO.findByDataObjectShepardId(dataObjectShepardId);
    return references;
  }

  /**
   * Gets URIReference by shepard id.
   *
   * @param collectionShepardId
   * @param dataObjectShepardId
   * @param uriReferenceShepardId
   * @param versionUID the version UUID
   * @return URIReference
   * @throws InvalidPathException If reference with Id does not exist or is deleted, or if collection or dataObject Id of path is not valid
   * @throws InvalidAuthException If user has no read permissions on collection or dataobject specified by request path
   */
  public URIReference getReference(
    long collectionShepardId,
    long dataObjectShepardId,
    long uriReferenceShepardId,
    UUID versionUID
  ) {
    dataObjectService.getDataObject(collectionShepardId, dataObjectShepardId, versionUID);

    URIReference reference = uRIReferenceDAO.findByShepardId(uriReferenceShepardId, versionUID);
    if (reference == null || reference.isDeleted()) {
      String errorMsg = "ID ERROR - URI Reference with id %s is null or deleted".formatted(uriReferenceShepardId);
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
   * Creates a new URIReference
   *
   * @param collectionShepardId
   * @param dataObjectShepardId DataObject id for the reference to be created
   * @param uriReference Reference object
   * @return URIReference
   * @throws InvalidPathException if collection or dataobject specified by their Ids are null or deleted
   * @throws InvalidAuthException if user has no permission to edit referencing collection or no read permissions on referenced container
   */
  @Override
  public URIReference createReference(long collectionShepardId, long dataObjectShepardId, URIReferenceIO uriReference) {
    DataObject dataObject = dataObjectService.getDataObject(collectionShepardId, dataObjectShepardId);
    collectionService.assertIsAllowedToEditCollection(collectionShepardId);

    User user = userService.getCurrentUser();

    var toCreate = new URIReference();
    toCreate.setCreatedAt(dateHelper.getDate());
    toCreate.setCreatedBy(user);
    toCreate.setDataObject(dataObject);
    toCreate.setName(uriReference.getName());
    toCreate.setUri(uriReference.getUri());
    toCreate.setRelationship(uriReference.getRelationship());

    var created = uRIReferenceDAO.createOrUpdate(toCreate);
    created.setShepardId(created.getId());
    created = uRIReferenceDAO.createOrUpdate(created);
    versionService.attachToVersionOfVersionableEntityAndReturnVersion(dataObject.getId(), created.getId());
    return created;
  }

  /**
   * Applies a partial (RFC 7396 merge-patch) update to a URIReference looked up by its
   * application-level UUID v7 ({@code appId}).
   *
   * <p>Mutable fields: {@code name}, {@code uri}, {@code relationship}.
   * Absent keys are left unchanged. {@code name} and {@code uri} must not be set to
   * {@code null} or blank — both are required non-null fields on the entity.
   * {@code relationship} may be set to {@code null} to clear it.
   *
   * @param appId UUID v7 of the reference to patch
   * @param patch key/value map of fields to update (RFC 7396 semantics)
   * @return the updated {@link URIReference}
   * @throws InvalidPathException if no reference with that appId exists
   * @throws IllegalArgumentException if a required field is set to blank or null
   */
  public URIReference patchReferenceByAppId(String appId, Map<String, Object> patch) {
    URIReference ref = uRIReferenceDAO.findByAppId(appId);
    if (ref == null) {
      String msg = "URIReference with appId %s not found".formatted(appId);
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
    if (patch.containsKey("uri")) {
      Object v = patch.get("uri");
      if (v == null || v.toString().isBlank()) {
        throw new IllegalArgumentException("uri must not be blank");
      }
      ref.setUri(v.toString());
    }
    if (patch.containsKey("relationship")) {
      Object v = patch.get("relationship");
      ref.setRelationship(v != null ? v.toString() : null);
    }

    User user = userService.getCurrentUser();
    ref.setUpdatedAt(dateHelper.getDate());
    ref.setUpdatedBy(user);

    return uRIReferenceDAO.createOrUpdate(ref);
  }

  /**
   * Looks up a URIReference by its application-level UUID v7 without permission checks.
   * Used by the v2 REST resource to resolve the parent DataObject for access gating.
   *
   * @param appId UUID v7 of the reference
   * @return the matching {@link URIReference}, or {@code null}
   */
  public URIReference findByAppId(String appId) {
    return uRIReferenceDAO.findByAppId(appId);
  }

  /**
   * APISIMP-REFS-INMEM-PAGING — count of non-deleted URIReferences under a DataObject
   * without loading them all. Delegates the COUNT to Neo4j via the DAO.
   *
   * @param dataObjectAppId the parent DataObject's appId.
   * @return total count of matching, non-deleted URIReferences.
   */
  public int countByDataObjectAppId(String dataObjectAppId) {
    return uRIReferenceDAO.countByDataObjectAppId(dataObjectAppId);
  }

  /**
   * APISIMP-REFS-INMEM-PAGING — paginated list of non-deleted URIReferences under a
   * DataObject. Delegates SKIP/LIMIT to Neo4j via the DAO.
   *
   * @param dataObjectAppId the parent DataObject's appId.
   * @param skip 0-based offset.
   * @param limit maximum rows (must be &gt; 0).
   * @return the URIReferences for the requested page; never null.
   */
  public List<URIReference> listByDataObjectAppId(String dataObjectAppId, int skip, int limit) {
    return uRIReferenceDAO.findByDataObjectAppId(dataObjectAppId, skip, limit);
  }

  /**
   * Deletes the URI reference.
   *
   * @param collectionShepardId
   * @param dataObjectShepardId
   * @param uriReferenceShepardId
   * @throws InvalidPathException if collection or dataobject specified by their Ids are null or deleted
   * @throws InvalidAuthException if user has no permissions to edit the collection, which the reference is assigned to
   */
  @Override
  public void deleteReference(long collectionShepardId, long dataObjectShepardId, long uriReferenceShepardId) {
    var old = getReference(collectionShepardId, dataObjectShepardId, uriReferenceShepardId, null);
    collectionService.assertIsAllowedToEditCollection(collectionShepardId);

    User user = userService.getCurrentUser();

    old.setDeleted(true);
    old.setUpdatedAt(dateHelper.getDate());
    old.setUpdatedBy(user);

    uRIReferenceDAO.createOrUpdate(old);
  }
}
