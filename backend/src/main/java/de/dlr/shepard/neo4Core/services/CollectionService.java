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
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@RequestScoped
public class CollectionService {

  private CollectionDAO collectionDAO;
  private UserDAO userDAO;
  private PermissionsDAO permissionsDAO;
  private DateHelper dateHelper;
  private VersionDAO versionDAO;

  CollectionService() {}

  @Inject
  public CollectionService(
    CollectionDAO collectionDAO,
    UserDAO userDAO,
    PermissionsDAO permissionsDAO,
    DateHelper dateHelper,
    VersionDAO versionDAO
  ) {
    this.collectionDAO = collectionDAO;
    this.userDAO = userDAO;
    this.permissionsDAO = permissionsDAO;
    this.dateHelper = dateHelper;
    this.versionDAO = versionDAO;
  }

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
    permissionsDAO.createOrUpdate(new Permissions(updated, user, PermissionType.Private));

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

  public Collection getCollectionByShepardId(long shepardId, UUID versionUID) {
    Collection ret;
    String errorMsg;
    if (versionUID == null) {
      ret = collectionDAO.findByShepardId(shepardId);
      errorMsg = String.format("Collection with id %s is null or deleted", shepardId);
    } else {
      ret = collectionDAO.findByShepardId(shepardId, versionUID);
      errorMsg = String.format("Collection with id %s and versionUID %s is null or deleted", shepardId, versionUID);
    }
    if (ret == null || ret.isDeleted()) {
      Log.error(errorMsg);
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
