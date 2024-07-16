package de.dlr.shepard.neo4Core.services;

import de.dlr.shepard.neo4Core.dao.CollectionDAO;
import de.dlr.shepard.neo4Core.dao.PermissionsDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.neo4Core.entities.Permissions;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.io.CollectionIO;
import de.dlr.shepard.util.DateHelper;
import de.dlr.shepard.util.PermissionType;
import de.dlr.shepard.util.QueryParamHelper;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CollectionService {

  private CollectionDAO collectionDAO = new CollectionDAO();
  private UserDAO userDAO = new UserDAO();
  private PermissionsDAO permissionsDAO = new PermissionsDAO();
  private DateHelper dateHelper = new DateHelper();

  /**
   * Creates a Collection and stores it in Neo4J
   *
   * @param collection to be stored
   * @param username   of the related user
   * @return the created collection
   */
  public Collection createCollection(CollectionIO collection, String username) {
    User user = userDAO.find(username);
    Collection toCreate = new Collection();
    toCreate.setAttributes(collection.getAttributes());
    toCreate.setCreatedBy(user);
    toCreate.setCreatedAt(dateHelper.getDate());
    toCreate.setDescription(collection.getDescription());
    toCreate.setName(collection.getName());
    Collection created = collectionDAO.createOrUpdate(toCreate);
    created.setShepardId(created.getId());
    created = collectionDAO.createOrUpdate(created);
    permissionsDAO.createOrUpdate(new Permissions(created, user, PermissionType.Private));
    return created;
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

  public Collection getCollectionByShepardId(long shepardId) {
    Collection collection = collectionDAO.findByShepardId(shepardId);
    if (collection == null || collection.isDeleted()) {
      log.error("Collection with id {} is null or deleted", shepardId);
      return null;
    }
    cutDeleted(collection);
    return collection;
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
    old.setUpdatedAt(dateHelper.getDate());
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
    var date = dateHelper.getDate();
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
