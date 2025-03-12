package de.dlr.shepard.context.collection.services;

import de.dlr.shepard.auth.permission.io.PermissionsIO;
import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.auth.permission.model.Roles;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.PermissionType;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.collection.daos.CollectionDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.version.daos.VersionDAO;
import de.dlr.shepard.context.version.entities.Version;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RequestScoped
public class CollectionService {

  @Inject
  CollectionDAO collectionDAO;

  @Inject
  UserDAO userDAO;

  @Inject
  PermissionsService permissionsService;

  @Inject
  DateHelper dateHelper;

  @Inject
  VersionDAO versionDAO;

  /**
   * Creates a Collection and stores it in Neo4J
   *
   * @param collection to be stored
   * @param username   of the related user
   * @return the created collection
   */
  public Collection createCollection(CollectionIO collection, String username) {
    Date date = dateHelper.getDate();
    var user = userDAO.find(username);
    var toCreate = new Collection();
    toCreate.setAttributes(collection.getAttributes());
    toCreate.setCreatedBy(user);
    toCreate.setCreatedAt(date);
    toCreate.setDescription(collection.getDescription());
    toCreate.setName(collection.getName());
    var createdCollection = collectionDAO.createOrUpdate(toCreate);

    Version nullVersion = new Version(Constants.HEAD, Constants.HEAD_VERSION, date, user);
    Version savedNullVersion = versionDAO.createOrUpdate(nullVersion);

    long collectionId = createdCollection.getId();
    createdCollection.setShepardId(collectionId);
    createdCollection.setVersion(savedNullVersion);
    var updated = collectionDAO.createOrUpdate(createdCollection);
    permissionsService.createPermissions(updated, user, PermissionType.Private);

    return updated;
  }

  /**
   * Searches the database for all Collections
   *
   * @param params encapsulates possible parameters
   * @param username the name of the user
   * @return a list of Collections
   */
  public List<Collection> getAllCollectionsByShepardId(QueryParamHelper params, String username) {
    List<Collection> queryResult = collectionDAO.findAllCollectionsByShepardId(params, username);
    List<Collection> collections = queryResult.stream().map(this::cutDeleted).toList();
    return collections;
  }

  /**
   * Retrieves a collection by shepardId.
   * The returned collection is in 'light' format, so dataobjects and incoming references are excluded.
   *
   * @param shepardId long
   * @return Optional<Collection>
   */
  public Optional<Collection> getCollectionOptional(long shepardId) {
    return getCollectionOptional(shepardId, null, true);
  }

  /**
   * Retrieves a collection by shepardId.
   * The returned collection is in 'light' format, so dataobjects and incoming references are excluded.
   *
   * @param shepardId long
   * @return Collection
   * @throws InvalidPathException if no collection could be found by shepardId
   */
  public Collection getCollection(long shepardId) {
    return getCollection(shepardId, null, true);
  }

  /**
   * Retrieves a collection by shepardId and versionUID.
   * The returned collection is in 'light' format, so dataobjects and incoming references are excluded.
   *
   * @param shepardId long
   * @param versionUID UUID
   * @return Collection
   * @throws InvalidPathException if no collection (with specified version) could be found by shepardId
   */
  public Collection getCollection(long shepardId, UUID versionUID) {
    return getCollection(shepardId, versionUID, true);
  }

  /**
   * Fetches a collection including permissions, attributes, contained data objects and incoming references.
   * @param shepardId shepardId of the desired collection
   * @return Collection
   * @throws InvalidPathException if no collection could be found by shepardId
   */
  public Collection getCollectionWithDataObjectsAndIncomingReferences(long shepardId) {
    return getCollection(shepardId, null, false);
  }

  /**
   * Fetches a collection including permissions, attributes, contained data objects and incoming references.
   * @param shepardId shepardId of the desired collection
   * @return Collection if available
   */
  public Optional<Collection> getCollectionOptionalWithDataObjectsAndIncomingReferences(long shepardId) {
    return getCollectionOptional(shepardId, null, false);
  }

  /**
   * Fetches a collection including permissions, attributes, contained data objects and incoming references.
   * @param shepardId shepardId of the desired collection
   * @param versionUID ID of the version to retrieve
   * @return Collection
   * @throws InvalidPathException if no collection could be found by shepardId
   */
  public Collection getCollectionWithDataObjectsAndIncomingReferences(long shepardId, UUID versionUID) {
    return getCollection(shepardId, versionUID, false);
  }

  /**
   * Return collection by shepard Id or shepard id + versionUId.
   *
   * @return Collection
   * @throws InvalidPathException if no collection could be found by shepardId
   */
  private Collection getCollection(long shepardId, UUID versionUID, boolean excludeDataObjectsAndIncomingReferences) {
    return getCollectionOptional(shepardId, versionUID, excludeDataObjectsAndIncomingReferences).orElseThrow(() ->
      new InvalidPathException(String.format("ID ERROR - Collection with id %s does not exist", shepardId))
    );
  }

  private Optional<Collection> getCollectionOptional(
    long shepardId,
    UUID versionUID,
    boolean excludeDataObjectsAndIncomingReferences
  ) {
    Collection ret;
    String errorMsg;
    if (versionUID == null) {
      ret = collectionDAO.findByShepardId(shepardId, excludeDataObjectsAndIncomingReferences);
      errorMsg = String.format("Collection with id %s is null or deleted", shepardId);
    } else {
      ret = collectionDAO.findByShepardId(shepardId, versionUID, excludeDataObjectsAndIncomingReferences);
      errorMsg = String.format("Collection with id %s and versionUID %s is null or deleted", shepardId, versionUID);
    }
    if (ret == null || ret.isDeleted()) {
      Log.error(errorMsg);
      return Optional.empty();
    }
    cutDeleted(ret);
    return Optional.of(ret);
  }

  /**
   * Updates a Collection with new Attributes.
   *
   * @param shepardId  collection's shepardID
   * @param collection which contains the new Attributes
   * @param username   of the related user
   * @return updated Collection
   * @throws InvalidPathException if no collection could be found by shepardId
   */
  public Collection updateCollectionByShepardId(long shepardId, CollectionIO collection, String username) {
    Collection old = getCollectionWithDataObjectsAndIncomingReferences(shepardId);
    old.setUpdatedBy(userDAO.find(username));
    old.setUpdatedAt(dateHelper.getDate());
    old.setAttributes(collection.getAttributes());
    old.setDescription(collection.getDescription());
    old.setName(collection.getName());
    Collection updated = collectionDAO.createOrUpdate(old);
    cutDeleted(updated);
    return updated;
  }

  /**
   * Deletes a Collection in Neo4j.
   * Before a collection is deleted, a check is run to test if collection exists.
   *
   * @param shepardId identifies the Collection
   * @param username  of the related user
   * @return a boolean to determine if Collection was successfully deleted
   * @throws InvalidPathException if no collection could be found by shepardId
   */
  public boolean deleteCollection(long shepardId, String username) {
    getCollection(shepardId, null, false);
    var date = dateHelper.getDate();
    var user = userDAO.find(username);
    var result = collectionDAO.deleteCollectionByShepardId(shepardId, user, date);
    return result;
  }

  public Roles getCollectionRoles(long collectionId, String username) {
    // We can use the collectionId as neo4jId here since permissions are global for all versions and shepardId and neo4jId are equal for the head version.
    return permissionsService.getUserRolesOnEntity(collectionId, username);
  }

  // TODO: Use assertions in all relevant methods
  public void assertUserIsAllowedToReadCollection(long collectionId, String username) {
    if (!permissionsService.isAccessTypeAllowedForUser(collectionId, AccessType.Read, username)) {
      throw new InvalidAuthException("The requested action is forbidden by the permission policies");
    }
  }

  public void assertUserIsAllowedToEditCollection(long collectionId, String username) {
    if (!permissionsService.isAccessTypeAllowedForUser(collectionId, AccessType.Write, username)) {
      throw new InvalidAuthException("The requested action is forbidden by the permission policies");
    }
  }

  public void assertUserIsAllowedToManageCollection(long collectionId, String username) {
    if (!permissionsService.isAccessTypeAllowedForUser(collectionId, AccessType.Manage, username)) {
      throw new InvalidAuthException("The requested action is forbidden by the permission policies");
    }
  }

  public Permissions getCollectionPermissions(long collectionId, String username) {
    assertUserIsAllowedToManageCollection(collectionId, username);

    // We can use the collectionId as neo4jId here since permissions are global for all versions and shepardId and neo4jId are equal for the head version.
    return permissionsService.getPermissionsOfEntity(collectionId);
  }

  public Permissions updateCollectionPermissions(PermissionsIO newPermissions, long collectionId, String username) {
    assertUserIsAllowedToManageCollection(collectionId, username);

    // We can use the collectionId as neo4jId here since permissions are global for all versions and shepardId and neo4jId are equal for the head version.
    return permissionsService.updatePermissionsByNeo4jId(newPermissions, collectionId);
  }

  private Collection cutDeleted(Collection collection) {
    var dataObjects = collection.getDataObjects().stream().filter(d -> !d.isDeleted()).toList();
    collection.setDataObjects(dataObjects);
    return collection;
  }
}
