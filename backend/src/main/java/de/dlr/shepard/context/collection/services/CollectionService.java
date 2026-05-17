package de.dlr.shepard.context.collection.services;

import de.dlr.shepard.auth.permission.io.PermissionsIO;
import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.auth.permission.model.Roles;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.exceptions.InvalidRequestException;
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
import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.file.services.FileContainerService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@RequestScoped
public class CollectionService {

  @Inject
  CollectionDAO collectionDAO;

  @Inject
  UserService userService;

  @Inject
  PermissionsService permissionsService;

  @Inject
  DateHelper dateHelper;

  @Inject
  VersionDAO versionDAO;

  @Inject
  AuthenticationContext authenticationContext;

  @Inject
  FileContainerService fileContainerService;

  /**
   * Creates a Collection and stores it in Neo4J
   *
   * @param collection to be stored
   * @return the created collection
   * @throws InvalidPathException if default FileContainer is specified, but the FileContainer cannot be found
   * @throws InvalidAuthException if default FileContainer is specified, but user has no read permission on FileContainer
   */
  public Collection createCollection(CollectionIO collection) {
    Date date = dateHelper.getDate();
    var user = userService.getCurrentUser();

    var toCreate = new Collection();
    toCreate.setAttributes(collection.getAttributes());
    toCreate.setCreatedBy(user);
    toCreate.setCreatedAt(date);
    toCreate.setDescription(collection.getDescription());
    toCreate.setStatus(collection.getStatus());
    toCreate.setName(collection.getName());

    if (collection.getDefaultFileContainerId() != null) {
      FileContainer fileContainer = fileContainerService.getContainer(collection.getDefaultFileContainerId());
      toCreate.setFileContainer(fileContainer);
    } else {
      toCreate.setFileContainer(null);
    }

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
  public List<Collection> getAllCollections(QueryParamHelper params) {
    List<Collection> queryResult = collectionDAO.findAllCollectionsByShepardId(
      params,
      authenticationContext.getCurrentUserName()
    );
    List<Collection> collections = queryResult.stream().map(this::cutDeleted).toList();
    return collections;
  }

  /**
   * Retrieves a collection by shepardId.
   * The returned collection is in 'light' format, so dataobjects and incoming references are excluded.
   *
   * @param shepardId long
   * @return Collection
   * @throws InvalidPathException if no collection could be found by shepardId
   * @throws InvalidAuthException if the user does not have permissions to read the collection
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
   * @throws InvalidAuthException if the user does not have permissions to read the collection
   */
  public Collection getCollection(long shepardId, UUID versionUID) {
    return getCollection(shepardId, versionUID, true);
  }

  /**
   * Fetches a collection including permissions, attributes, contained data objects and incoming references.
   * @param shepardId shepardId of the desired collection
   * @return Collection
   * @throws InvalidPathException if no collection could be found by shepardId
   * @throws InvalidAuthException if the user does not have permissions to read the collection
   */
  public Collection getCollectionWithDataObjectsAndIncomingReferences(long shepardId) {
    return getCollection(shepardId, null, false);
  }

  /**
   * Fetches a collection including permissions, attributes, contained data objects and incoming references.
   * @param shepardId shepardId of the desired collection
   * @param versionUID ID of the version to retrieve
   * @return Collection
   * @throws InvalidPathException if no collection could be found by shepardId
   * @throws InvalidAuthException if the user does not have permissions to read the collection
   */
  public Collection getCollectionWithDataObjectsAndIncomingReferences(long shepardId, UUID versionUID) {
    return getCollection(shepardId, versionUID, false);
  }

  /**
   * Return collection by shepard Id or shepard id + versionUId.
   *
   * @return Collection
   * @throws InvalidPathException if no collection could be found by shepardId
   * @throws InvalidAuthException if the user does not have permissions to read the collection
   */
  private Collection getCollection(long shepardId, UUID versionUID, boolean excludeDataObjectsAndIncomingReferences) {
    Collection ret;
    String errorMsg;
    if (versionUID == null) {
      ret = collectionDAO.findByShepardId(shepardId, excludeDataObjectsAndIncomingReferences);
      errorMsg = "Collection with id %s is null or deleted".formatted(shepardId);
    } else {
      ret = collectionDAO.findByShepardId(shepardId, versionUID, excludeDataObjectsAndIncomingReferences);
      errorMsg = "Collection with id %s and versionUID %s is null or deleted".formatted(shepardId, versionUID);
    }
    if (ret == null || ret.isDeleted()) {
      throw new InvalidPathException("ID ERROR - " + errorMsg);
    }
    assertIsAllowedToReadCollection(shepardId);
    cutDeleted(ret);

    return ret;
  }

  /**
   * Updates a Collection with new Attributes.
   *
   * @param shepardId  collection's shepardID
   * @param collection which contains the new Attributes
   * @param username   of the related user
   * @return updated Collection
   * @throws InvalidPathException if no collection could be found by shepardId
   * @throws InvalidAuthException if the user does not have permissions to read or edit the collection
   * @throws InvalidPathException if default FileContainer is specified, but the FileContainer cannot be found
   * @throws InvalidAuthException if default FileContainer is specified, but user has no read permission on FileContainer
   */
  public Collection updateCollectionByShepardId(long shepardId, CollectionIO collection) {
    Collection old = getCollectionWithDataObjectsAndIncomingReferences(shepardId);
    assertIsAllowedToEditCollection(shepardId);

    old.setUpdatedBy(userService.getCurrentUser());
    old.setUpdatedAt(dateHelper.getDate());
    old.setAttributes(collection.getAttributes());
    old.setDescription(collection.getDescription());
    old.setStatus(collection.getStatus());
    old.setName(collection.getName());

    if (collection.getDefaultFileContainerId() != null) {
      FileContainer fileContainer = fileContainerService.getContainer(collection.getDefaultFileContainerId());
      old.setFileContainer(fileContainer);
    } else {
      old.setFileContainer(null);
    }

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
   * @throws InvalidAuthException if the user does not have permissions to read or edit the collection
   */
  public void deleteCollection(long shepardId) {
    getCollection(shepardId);
    assertIsAllowedToEditCollection(shepardId);

    var date = dateHelper.getDate();
    var user = userService.getCurrentUser();
    if (!collectionDAO.deleteCollectionByShepardId(shepardId, user, date)) {
      throw new InvalidRequestException("Could not delete Collection with ShepardId %s".formatted(shepardId));
    }
  }

  /**
   * Gets roles for collection specified by id
   *
   * @param collectionId
   * @return Roles
   * @throws InvalidPathException if collection with collectionId does not exist
   * @throws InvalidAuthException if user has no read permissions on specified collection
   */
  public Roles getCollectionRoles(long collectionId) {
    getCollection(collectionId);

    // We can use the collectionId as neo4jId here since permissions are global for all versions and shepardId and neo4jId are equal for the head version.
    return permissionsService.getUserRolesOnEntity(collectionId, authenticationContext.getCurrentUserName());
  }

  /**
   * Gets Permissions for collection specified by id
   *
   * @param collectionId
   * @return Permissions
   * @throws InvalidPathException if collection with collectionId does not exist
   * @throws InvalidAuthException if user has no read permissions on specified collection, or is not allowed to manage permissions on collection
   */
  public Permissions getCollectionPermissions(long collectionId) {
    getCollection(collectionId);
    assertIsAllowedToManageCollection(collectionId);

    // We can use the collectionId as neo4jId here since permissions are global for all versions and shepardId and neo4jId are equal for the head version.
    return permissionsService.getPermissionsOfEntity(collectionId);
  }

  /**
   * Updates Permissions for collection specified by id
   *
   * @param collectionId
   * @return Permissions
   * @throws InvalidPathException if collection with collectionId does not exist
   * @throws InvalidAuthException if user has no read permissions on specified collection, or is not allowed to manage permissions on collection
   */
  public Permissions updateCollectionPermissions(PermissionsIO newPermissions, long collectionId) {
    getCollection(collectionId);
    assertIsAllowedToManageCollection(collectionId);

    // We can use the collectionId as neo4jId here since permissions are global for all versions and shepardId and neo4jId are equal for the head version.
    return permissionsService.updatePermissionsByNeo4jId(newPermissions, collectionId);
  }

  /**
   * Checks if the user requested the Collection is allowed to read it
   *
   * @throws InvalidAuthException when user is not allowed to read the Collection
   */
  public void assertIsAllowedToReadCollection(long collectionId) {
    if (
      !permissionsService.isAccessTypeAllowedForUser(
        collectionId,
        AccessType.Read,
        authenticationContext.getCurrentUserName(),
        currentIat()
      )
    ) {
      throw new InvalidAuthException("The requested action is forbidden by the permission policies");
    }
  }

  /**
   * Checks if the user requested the Collection is allowed to edit it
   *
   * @throws InvalidAuthException when user is not allowed to edit the Collection
   */
  public void assertIsAllowedToEditCollection(long collectionId) {
    if (
      !permissionsService.isAccessTypeAllowedForUser(
        collectionId,
        AccessType.Write,
        authenticationContext.getCurrentUserName(),
        currentIat()
      )
    ) {
      throw new InvalidAuthException("The requested action is forbidden by the permission policies");
    }
  }

  /**
   * Checks if the user requested the Collection is allowed to manage it
   *
   * @throws InvalidAuthException when user is not allowed to manage the Collection
   */
  public void assertIsAllowedToManageCollection(long collectionId) {
    if (
      !permissionsService.isAccessTypeAllowedForUser(
        collectionId,
        AccessType.Manage,
        authenticationContext.getCurrentUserName(),
        currentIat()
      )
    ) {
      throw new InvalidAuthException("The requested action is forbidden by the permission policies");
    }
  }

  /** F4 — current JWT iat for cache-key construction. */
  private long currentIat() {
    var principal = authenticationContext.getPrincipal();
    return principal != null ? principal.getIat() : 0L;
  }

  private Collection cutDeleted(Collection collection) {
    var dataObjects = collection.getDataObjects().stream().filter(d -> !d.isDeleted()).toList();
    collection.setDataObjects(dataObjects);
    return collection;
  }
}
