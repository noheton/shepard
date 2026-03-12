package de.dlr.shepard.context.collection.services;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.exceptions.InvalidRequestException;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.references.dataobject.daos.DataObjectReferenceDAO;
import de.dlr.shepard.context.references.dataobject.entities.DataObjectReference;
import de.dlr.shepard.context.version.services.VersionService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RequestScoped
public class DataObjectService {

  @Inject
  DataObjectDAO dataObjectDAO;

  @Inject
  DataObjectReferenceDAO dataObjectReferenceDAO;

  @Inject
  UserService userService;

  @Inject
  DateHelper dateHelper;

  @Inject
  VersionService versionService;

  @Inject
  CollectionService collectionService;

  @Inject
  PermissionsService permissionsService;

  @Inject
  AuthenticationContext authenticationContext;

  /**
   * Creates a DataObject
   *
   * @param collectionShepardId identifies the Collection
   * @param dataObject          to be stored
   * @return the stored DataObject with the auto generated id
   * @throws InvalidPathException if collection with collectionShepardId does not
   *                              exist
   * @throws InvalidPathException if collection with collectionShepardId does not
   *                              exist
   * @throws InvalidBodyException if the list of successors is not null or not empty
   */
  public DataObject createDataObject(long collectionShepardId, DataObjectIO dataObject) throws InvalidBodyException {
    Collection collection = collectionService.getCollection(collectionShepardId);
    collectionService.assertIsAllowedToEditCollection(collectionShepardId);

    User user = userService.getCurrentUser();
    DataObject parent = findRelatedDataObject(collection.getShepardId(), dataObject.getParentId(), null);
    if (dataObject.getSuccessorIds() != null) if (
      dataObject.getSuccessorIds().length != 0
    ) throw new InvalidBodyException(
      "when creating a new dataObject the list of successors must not be specified or be empty"
    );
    List<DataObject> predecessors = findRelatedDataObjects(
      collection.getShepardId(),
      dataObject.getPredecessorIds(),
      null
    );
    DataObject toCreate = new DataObject();
    toCreate.setAttributes(dataObject.getAttributes());
    toCreate.setDescription(dataObject.getDescription());
    toCreate.setName(dataObject.getName());
    toCreate.setCollection(collection);
    toCreate.setParent(parent);
    toCreate.setPredecessors(predecessors);
    toCreate.setCreatedAt(dateHelper.getDate());
    toCreate.setCreatedBy(user);
    DataObject created = dataObjectDAO.createOrUpdate(toCreate);
    created.setShepardId(created.getId());
    created = dataObjectDAO.createOrUpdate(created);
    versionService.attachToVersionOfVersionableEntityAndReturnVersion(collectionShepardId, created.getShepardId());
    return created;
  }

  /**
   * Get DataObject
   *
   * @param shepardId identifies the searched dataObject
   * @return the DataObject with the given id
   * @throws InvalidPathException if the DataObject cannot be found
   * @throws InvalidAuthException if user does not have read permissions on the
   *                              data object's collection
   */
  public DataObject getDataObject(long shepardId) {
    return getDataObject(shepardId, null);
  }

  /**
   * Get DataObject
   *
   * @param shepardId  identifies the searched dataObject
   * @param versionUID the dataobject's version UUID
   * @return an Optional containing the DataObject with the given id
   * @throws InvalidPathException if DataObject (with version UUID) cannot be
   *                              found
   * @throws InvalidAuthException if user does not have read permissions on the
   *                              data object's collection
   */
  public DataObject getDataObject(long shepardId, UUID versionUID) {
    DataObject ret;
    String errorMsg;
    if (versionUID == null) {
      ret = dataObjectDAO.findByShepardId(shepardId);
      errorMsg = String.format("DataObject with id %s is null or deleted", shepardId);
    } else {
      ret = dataObjectDAO.findByShepardId(shepardId, versionUID);
      errorMsg = String.format("DataObject with id %s and versionUID %s is null or deleted", shepardId, versionUID);
    }
    if (ret == null || ret.isDeleted()) {
      Log.error(errorMsg);
      throw new InvalidPathException("ID ERROR - " + errorMsg);
    }

    collectionService.assertIsAllowedToReadCollection(ret.getCollection().getShepardId());
    cutDeleted(ret);

    HashSet<Long> incomingReferencesIdList = new HashSet<Long>();
    for (DataObjectReference reference : ret.getIncoming()) incomingReferencesIdList.add(reference.getId());
    List<DataObjectReference> completeIncomingReferences = new ArrayList<DataObjectReference>();
    for (Long id : incomingReferencesIdList) completeIncomingReferences.add(dataObjectReferenceDAO.findByNeo4jId(id));

    HashSet<Long> childrenIdList = new HashSet<Long>();
    for (DataObject child : ret.getChildren()) childrenIdList.add(child.getId());
    List<DataObject> completeChildren = new ArrayList<DataObject>();
    for (Long id : childrenIdList) completeChildren.add(dataObjectDAO.findByNeo4jId(id));

    HashSet<Long> predecessorsIdList = new HashSet<Long>();
    for (DataObject predecessor : ret.getPredecessors()) predecessorsIdList.add(predecessor.getId());
    List<DataObject> completePredecessors = new ArrayList<DataObject>();
    for (Long id : predecessorsIdList) completePredecessors.add(dataObjectDAO.findByNeo4jId(id));

    HashSet<Long> successorsIdList = new HashSet<Long>();
    for (DataObject successor : ret.getSuccessors()) successorsIdList.add(successor.getId());
    List<DataObject> completeSuccessors = new ArrayList<DataObject>();
    for (Long id : successorsIdList) completeSuccessors.add(dataObjectDAO.findByNeo4jId(id));

    ret.setChildren(completeChildren);
    ret.setIncoming(completeIncomingReferences);
    ret.setPredecessors(completePredecessors);
    ret.setSuccessors(completeSuccessors);
    if (ret.getParent() != null) ret.setParent(dataObjectDAO.findByNeo4jId(ret.getParent().getId()));
    return ret;
  }

  /**
   * Get DataObject
   *
   * @param collectionShepardId collection's shepardId
   * @param shepardId           identifies the searched dataObject
   * @return the DataObject with the given id
   * @throws InvalidPathException if dataobject or collection cannot be found or
   *                              the dataobject does not match the collection
   * @throws InvalidAuthException if user does not have read permissions on the
   *                              collection
   */
  public DataObject getDataObject(long collectionShepardId, long shepardId) {
    return getDataObject(collectionShepardId, shepardId, null);
  }

  /**
   * Get DataObject
   *
   * @param collectionShepardId collection's shepardId
   * @param shepardId           identifies the searched dataObject
   * @param versionUID          the DataObject's version UUID
   * @return the DataObject with the given id
   * @throws InvalidPathException if DataObject or collection cannot be found or
   *                              the DataObject does not match the collection
   * @throws InvalidAuthException if user does not have read permissions on the
   *                              collection
   */
  public DataObject getDataObject(long shepardCollectionId, long shepardId, UUID versionUID) {
    collectionService.getCollection(shepardCollectionId);

    DataObject dataObject;
    try {
      // This may throw a 403 if the data object is in a different collection for
      // which the user does not have permissions -> handle that exception
      // specifically
      dataObject = getDataObject(shepardId, versionUID);
    } catch (InvalidAuthException ex) {
      throw new InvalidPathException("ID ERROR - There is no association between collection and dataObject");
    }

    if (!dataObject.getCollection().getShepardId().equals(shepardCollectionId)) {
      throw new InvalidPathException("ID ERROR - There is no association between collection and dataObject");
    }
    return dataObject;
  }

  /**
   * Searches the database for DataObjects.
   *
   * @param collectionShepardId  identifies the collection
   * @param paramsWithShepardIds encapsulates possible parameters
   * @param versionUID           identifies the version
   * @return a List of DataObjects
   * @throws InvalidPathException if collection with collectionShepardId does not
   *                              exist
   * @throws InvalidAuthException if user does not have read permissions on the
   *                              collection
   */
  public List<DataObject> getAllDataObjectsByShepardIds(
    long collectionShepardId,
    QueryParamHelper paramsWithShepardIds,
    UUID versionUID
  ) {
    collectionService.getCollection(collectionShepardId, versionUID);

    var unfiltered = dataObjectDAO.findByCollectionByShepardIds(collectionShepardId, paramsWithShepardIds, versionUID);
    var dataObjects = unfiltered.stream().map(this::cutDeleted).toList();
    return dataObjects;
  }

  /**
   * Updates a DataObject with new attributes. Hereby only not null attributes
   * will replace the old attributes.
   *
   * @param collectionShepardId ShepardId of the collection the dataobject is
   *                            assigned to
   * @param dataObjectShepardId Identifies the dataObject
   * @param dataObject          DataObject entity for updating.
   * @return updated DataObject.
   * @throws InvalidPathException if dataobject cannot be found or collection with
   *                              collectionShepardId does not exist
   * @throws InvalidAuthException if user does not have read or write permissions
   *                              on the collection
   * @throws InvalidBodyException if the list of successors is not admitted
   */
  public DataObject updateDataObject(long collectionShepardId, long dataObjectShepardId, DataObjectIO dataObject) {
    DataObject old = getDataObject(collectionShepardId, dataObjectShepardId);
    collectionService.assertIsAllowedToEditCollection(collectionShepardId);

    User user = userService.getCurrentUser();

    if (old.getParent() != null) {
      dataObjectDAO.deleteHasChildRelation(old.getParent().getShepardId(), old.getShepardId());
    }
    if (old.getPredecessors() != null) {
      old
        .getPredecessors()
        .forEach(predecessor -> {
          dataObjectDAO.deleteHasSuccessorRelation(predecessor.getShepardId(), old.getShepardId());
        });
    }
    if (dataObject.getSuccessorIds() != null) {
      Set<Long> givenSuccessorIds = Arrays.stream(dataObject.getSuccessorIds()).boxed().collect(Collectors.toSet());
      Set<Long> foundSuccessorIds = new HashSet<Long>();
      old.getSuccessors().forEach(successor -> foundSuccessorIds.add(successor.getId()));
      if (!givenSuccessorIds.equals(foundSuccessorIds)) throw new InvalidBodyException(
        "the given list of successors does not match the current list of successors"
      );
    }

    DataObject newParent = findRelatedDataObject(
      old.getCollection().getShepardId(),
      dataObject.getParentId(),
      dataObjectShepardId
    );
    List<DataObject> newPredecessors = findRelatedDataObjects(
      old.getCollection().getShepardId(),
      dataObject.getPredecessorIds(),
      dataObjectShepardId
    );

    old.setShepardId(old.getShepardId());
    old.setName(dataObject.getName());
    old.setDescription(dataObject.getDescription());
    old.setAttributes(dataObject.getAttributes());
    old.setParent(newParent);
    old.setPredecessors(newPredecessors);
    old.setUpdatedAt(dateHelper.getDate());
    old.setUpdatedBy(user);
    DataObject updated = dataObjectDAO.createOrUpdate(old);
    cutDeleted(updated);
    return updated;
  }

  /**
   * set the deleted flag for the DataObject
   *
   * @param collectionShepardId ShepardId of the collection the dataobject is
   *                            assigned to
   * @param dataObjectShepardId identifies the DataObject to be deleted
   * @return a boolean to identify if the DataObject was successfully removed
   * @throws InvalidPathException if dataobject cannot be found or collection with
   *                              collectionShepardId does not exist
   * @throws InvalidAuthException if user does not have read or write permissions
   *                              on the collection
   */
  public void deleteDataObject(long collectionShepardId, long dataObjectShepardId) {
    getDataObject(collectionShepardId, dataObjectShepardId);
    collectionService.assertIsAllowedToEditCollection(collectionShepardId);

    Date date = dateHelper.getDate();
    User user = userService.getCurrentUser();

    if (!dataObjectDAO.deleteDataObjectByShepardId(dataObjectShepardId, user, date)) {
      throw new InvalidRequestException(
        String.format("Could not delete DataObject with ShepardId %s", dataObjectShepardId)
      );
    }
  }

  private DataObject cutDeleted(DataObject dataObject) {
    var incoming = dataObject.getIncoming().stream().filter(i -> !i.isDeleted()).toList();
    dataObject.setIncoming(incoming);
    if (dataObject.getParent() != null && dataObject.getParent().isDeleted()) {
      dataObject.setParent(null);
    }
    var children = dataObject.getChildren().stream().filter(s -> !s.isDeleted()).toList();
    dataObject.setChildren(children);
    var predecessors = dataObject.getPredecessors().stream().filter(s -> !s.isDeleted()).toList();
    dataObject.setPredecessors(predecessors);
    var successors = dataObject.getSuccessors().stream().filter(s -> !s.isDeleted()).toList();
    dataObject.setSuccessors(successors);
    var references = dataObject.getReferences().stream().filter(ref -> !ref.isDeleted()).toList();
    dataObject.setReferences(references);
    return dataObject;
  }

  private List<DataObject> findRelatedDataObjects(
    long collectionShepardId,
    long[] referencedShepardIds,
    Long dataObjectShepardId
  ) {
    if (referencedShepardIds == null) return new ArrayList<>();

    var result = new ArrayList<DataObject>(referencedShepardIds.length);
    /*
     * TODO: seems to be inefficient since this loops generates referencedIds.length
     * calls to Neo4j this could possibly be packed into one query (or in chunks of
     * queries in case of a large referencedIds array)
     */
    for (var shepardId : referencedShepardIds) {
      result.add(findRelatedDataObject(collectionShepardId, shepardId, dataObjectShepardId));
    }
    return result;
  }

  private DataObject findRelatedDataObject(
    long collectionShepardId,
    Long referencedShepardId,
    Long dataObjectShepardId
  ) {
    if (referencedShepardId == null) return null;
    else if (referencedShepardId.equals(dataObjectShepardId)) throw new InvalidBodyException(
      "Self references are not allowed."
    );

    var dataObject = dataObjectDAO.findByShepardId(referencedShepardId);
    if (dataObject == null || dataObject.isDeleted()) throw new InvalidBodyException(
      String.format("The DataObject with id %d could not be found.", referencedShepardId)
    );

    // Prevent cross collection references
    if (!dataObject.getCollection().getShepardId().equals(collectionShepardId)) throw new InvalidBodyException(
      "Related data objects must belong to the same collection as the new data object"
    );

    return dataObject;
  }

  /**
   * Only needed for fixing session problems in unit tests
   */
  public void clearSession() {
    dataObjectDAO.clearSession();
  }
}
