package de.dlr.shepard.neo4Core.services;

import de.dlr.shepard.neo4Core.dao.CollectionDAO;
import de.dlr.shepard.neo4Core.dao.PermissionsDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.dao.VersionDAO;
import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.neo4Core.entities.Permissions;
import de.dlr.shepard.neo4Core.entities.Version;
import de.dlr.shepard.neo4Core.io.CollectionIO;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.DateHelper;
import de.dlr.shepard.util.PermissionType;
import de.dlr.shepard.util.QueryParamHelper;
import java.util.Date;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CollectionService {

  private CollectionDAO collectionDAO = new CollectionDAO();
  private UserDAO userDAO = new UserDAO();
  private PermissionsDAO permissionsDAO = new PermissionsDAO();
  private DateHelper dateHelper = new DateHelper();
  private VersionDAO versionDAO = new VersionDAO();

  /**
   * Creates a Collection and stores it in Neo4J
   *
   * @param collection to be stored
   * @param username   of the related user
   * @return the created collection
   */
  public Collection createCollection(CollectionIO collection, String username) {
    Date date = DateHelper.getDate();
    var user = userDAO.find(username);
    var toCreate = new Collection();
    toCreate.setAttributes(collection.getAttributes());
    toCreate.setCreatedBy(user);
    toCreate.setCreatedAt(date);
    toCreate.setDescription(collection.getDescription());
    toCreate.setName(collection.getName());
    var createdCollection = collectionDAO.createOrUpdate(toCreate);
    permissionsDAO.createOrUpdate(new Permissions(createdCollection, user, PermissionType.Private));

    Version nullVersion = new Version(Constants.INITIAL_VERSION, Constants.INITIAL_VERSION, date, user);
    Version savedNullVersion = versionDAO.createOrUpdate(nullVersion);

    long collectionId = createdCollection.getId();
    createdCollection.setShepardId(collectionId);
    createdCollection.setVersion(savedNullVersion);
    var updated = collectionDAO.createOrUpdate(createdCollection);
    return updated;
  }

  /**
   * Searches the database for all Collections
   *
   * @param params   encapsulates possible parameters
   * @param username the name of the user
   * @return a list of Collections
   */
  public List<Collection> getAllCollectionsByShepardId(QueryParamHelper params, String username) {
    List<Collection> queryResult = collectionDAO.findAllCollectionsByShepardId(params, username);
    List<Collection> collections = queryResult.stream().map(this::cutDeleted).toList();
    return collections;
  }

  public Collection getCollectionByShepardId(long shepardId, String versionUID) {
    Collection ret;
    String errorMsg;
    if (versionUID == null) {
      ret = collectionDAO.findByShepardId(shepardId);
      errorMsg = String.format("Collection with id {} is null or deleted", shepardId);
    } else {
      ret = collectionDAO.findByShepardId(shepardId, versionUID);
      errorMsg = String.format("Collection with id {} and versionUID {} is null or deleted", shepardId, versionUID);
    }
    if (ret == null || ret.isDeleted()) {
      log.error(errorMsg);
      return null;
    }
    cutDeleted(ret);
    return ret;
  }

  public Collection getCollectionByShepardId(long shepardId) {
    return getCollectionByShepardId(shepardId, null);
  }

  /**
   * Updates a Collection with new Attributes.
   *
   * @param shepardId  identifies the Collection
   * @param collection which contains the new Attributes
   * @param username   of the related user
   * @return updated Collection
   */

  public Collection updateCollectionByShepardId(long shepardId, CollectionIO collection, String username) {
    Collection old = collectionDAO.findByShepardId(shepardId);
    old.setUpdatedBy(userDAO.find(username));
    old.setUpdatedAt(DateHelper.getDate());
    old.setAttributes(collection.getAttributes());
    old.setDescription(collection.getDescription());
    old.setName(collection.getName());
    Collection updated = collectionDAO.createOrUpdate(old);
    cutDeleted(updated);
    return updated;
  }

  /**
   * Deletes a Collection in Neo4j
   *
   * @param shepardId identifies the Collection
   * @param username  of the related user
   * @return a boolean to determine if Collection was successfully deleted
   */
  public boolean deleteCollectionByShepardId(long shepardId, String username) {
    var date = DateHelper.getDate();
    var user = userDAO.find(username);
    var result = collectionDAO.deleteCollectionByShepardId(shepardId, user, date);
    return result;
  }

  private Collection cutDeleted(Collection collection) {
    var dataObjects = collection.getDataObjects().stream().filter(d -> !d.isDeleted()).toList();
    collection.setDataObjects(dataObjects);
    return collection;
  }
}
