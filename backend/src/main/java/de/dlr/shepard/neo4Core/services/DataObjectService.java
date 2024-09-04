package de.dlr.shepard.neo4Core.services;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.neo4Core.dao.CollectionDAO;
import de.dlr.shepard.neo4Core.dao.DataObjectDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.dao.VersionDAO;
import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.entities.Version;
import de.dlr.shepard.neo4Core.io.DataObjectIO;
import de.dlr.shepard.util.DateHelper;
import de.dlr.shepard.util.QueryParamHelper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@RequestScoped
public class DataObjectService {

  private DataObjectDAO dataObjectDAO;
  private CollectionDAO collectionDAO;
  private UserDAO userDAO;
  private DateHelper dateHelper;
  private VersionDAO versionDAO;

  DataObjectService() {}

  @Inject
  public DataObjectService(
    DataObjectDAO dataObjectDAO,
    CollectionDAO collectionDAO,
    UserDAO userDAO,
    DateHelper dateHelper,
    VersionDAO versionDAO
  ) {
    this.dataObjectDAO = dataObjectDAO;
    this.collectionDAO = collectionDAO;
    this.userDAO = userDAO;
    this.dateHelper = dateHelper;
    this.versionDAO = versionDAO;
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
    Collection collection = collectionDAO.findByShepardId(collectionShepardId);
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
    Version version = collection.getVersion();
    versionDAO.createLink(created.getId(), version.getUid());
    return created;
  }

  /**
   * Searches the neo4j database for a dataObject
   *
   * @param shepardId identifies the searched dataObject
   * @return the DataObject with the given id or null
   */
  public DataObject getDataObjectByShepardId(long shepardId) {
    DataObject dataObject = dataObjectDAO.findByShepardId(shepardId);
    if (dataObject == null || dataObject.isDeleted()) {
      Log.errorf("Data Object with id %s is null or deleted", shepardId);
      return null;
    }
    cutDeleted(dataObject);
    return dataObject;
  }

  /**
   * Searches the database for DataObjects.
   *
   * @param collectionShepardId  identifies the collection
   * @param paramsWithShepardIds encapsulates possible parameters
   * @return a List of DataObjects
   */
  public List<DataObject> getAllDataObjectsByShepardIds(
    long collectionShepardId,
    QueryParamHelper paramsWithShepardIds
  ) {
    var unfiltered = dataObjectDAO.findByCollectionByShepardIds(collectionShepardId, paramsWithShepardIds);
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
    DataObject parent = findRelatedDataObjectByShepardId(
      old.getCollection().getShepardId(),
      dataObject.getParentId(),
      dataObjectShepardId
    );
    List<DataObject> predecessors = findRelatedDataObjectsByShepardIds(
      old.getCollection().getShepardId(),
      dataObject.getPredecessorIds(),
      dataObjectShepardId
    );
    old.setAttributes(dataObject.getAttributes());
    old.setDescription(dataObject.getDescription());
    old.setName(dataObject.getName());
    old.setParent(parent);
    old.setPredecessors(predecessors);
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
}
