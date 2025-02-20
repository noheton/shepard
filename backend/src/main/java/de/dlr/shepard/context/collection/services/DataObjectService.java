package de.dlr.shepard.context.collection.services;

import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.collection.daos.CollectionDAO;
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
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@RequestScoped
public class DataObjectService {

  private DataObjectDAO dataObjectDAO;
  private DataObjectReferenceDAO dataObjectReferenceDAO;
  private CollectionDAO collectionDAO;
  private UserDAO userDAO;
  private DateHelper dateHelper;
  private VersionService versionService;

  DataObjectService() {}

  @Inject
  public DataObjectService(
    DataObjectDAO dataObjectDAO,
    DataObjectReferenceDAO dataObjectReferenceDAO,
    CollectionDAO collectionDAO,
    UserDAO userDAO,
    DateHelper dateHelper,
    VersionService versionService
  ) {
    this.dataObjectDAO = dataObjectDAO;
    this.dataObjectReferenceDAO = dataObjectReferenceDAO;
    this.collectionDAO = collectionDAO;
    this.userDAO = userDAO;
    this.dateHelper = dateHelper;
    this.versionService = versionService;
  }

  /**
   * Creates a DataObject and stores it in Neo4J
   *
   * @param collectionShepardId identifies the Collection
   * @param dataObject          to be stored
   * @param username            of the related user
   * @return the stored DataObject with the auto generated id
   */
  public DataObject createDataObjectByCollectionShepardId(
    long collectionShepardId,
    DataObjectIO dataObject,
    String username
  ) {
    Collection collection = collectionDAO.findByShepardId(collectionShepardId, true);
    User user = userDAO.find(username);
    DataObject parent = findRelatedDataObjectByShepardId(collection.getShepardId(), dataObject.getParentId(), null);
    List<DataObject> predecessors = findRelatedDataObjectsByShepardIds(
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
   * Searches the neo4j database for a dataObject
   *
   * @param shepardId identifies the searched dataObject
   * @return the DataObject with the given id or null
   */
  public DataObject getDataObjectByShepardId(long shepardId, UUID versionUID) {
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
      return null;
    }
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

  public DataObject getDataObjectByShepardId(long shepardId) {
    return getDataObjectByShepardId(shepardId, null);
  }

  /**
   * Searches the database for DataObjects.
   *
   * @param collectionShepardId  identifies the collection
   * @param paramsWithShepardIds encapsulates possible parameters
   * @param versionUID identifies the version
   * @return a List of DataObjects
   */

  public List<DataObject> getAllDataObjectsByShepardIds(
    long collectionShepardId,
    QueryParamHelper paramsWithShepardIds,
    UUID versionUID
  ) {
    var unfiltered = dataObjectDAO.findByCollectionByShepardIds(collectionShepardId, paramsWithShepardIds, versionUID);
    var dataObjects = unfiltered.stream().map(this::cutDeleted).toList();
    return dataObjects;
  }

  /**
   * Updates a DataObject with new attributes. Hereby only not null attributes
   * will replace the old attributes.
   *
   * @param dataObjectShepardId Identifies the dataObject
   * @param dataObject          DataObject entity for updating.
   * @param username            of the related user
   * @return updated DataObject.
   */
  public DataObject updateDataObjectByShepardId(long dataObjectShepardId, DataObjectIO dataObject, String username) {
    DataObject old = dataObjectDAO.findByShepardId(dataObjectShepardId);
    User user = userDAO.find(username);

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

    DataObject newParent = findRelatedDataObjectByShepardId(
      old.getCollection().getShepardId(),
      dataObject.getParentId(),
      dataObjectShepardId
    );
    List<DataObject> newPredecessors = findRelatedDataObjectsByShepardIds(
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
   * @param dataObjectShepardId identifies the DataObject to be deleted
   * @param username            of the related user
   * @return a boolean to identify if the DataObject was successfully removed
   */
  public boolean deleteDataObjectByShepardId(long dataObjectShepardId, String username) {
    Date date = dateHelper.getDate();
    User user = userDAO.find(username);
    boolean result = dataObjectDAO.deleteDataObjectByShepardId(dataObjectShepardId, user, date);
    return result;
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
    var sucessors = dataObject.getSuccessors().stream().filter(s -> !s.isDeleted()).toList();
    dataObject.setSuccessors(sucessors);
    var references = dataObject.getReferences().stream().filter(ref -> !ref.isDeleted()).toList();
    dataObject.setReferences(references);
    return dataObject;
  }

  private List<DataObject> findRelatedDataObjectsByShepardIds(
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
      result.add(findRelatedDataObjectByShepardId(collectionShepardId, shepardId, dataObjectShepardId));
    }
    return result;
  }

  private DataObject findRelatedDataObjectByShepardId(
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

  public Long getCollectionId(Long dataObjectId) {
    DataObject dataObject = dataObjectDAO.findByShepardId(dataObjectId);
    if (null == dataObject) return null;
    return dataObject.getCollection().getShepardId();
  }
}
