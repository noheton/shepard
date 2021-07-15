package de.dlr.shepard.neo4Core.services;

import java.util.List;
import java.util.stream.Collectors;

import de.dlr.shepard.neo4Core.dao.BasicReferenceDAO;
import de.dlr.shepard.neo4Core.dao.CollectionDAO;
import de.dlr.shepard.neo4Core.dao.DataObjectDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.neo4Core.io.CollectionIO;
import de.dlr.shepard.util.DateHelper;
import de.dlr.shepard.util.QueryParamHelper;

public class CollectionService {

	private CollectionDAO collectionDAO = new CollectionDAO();
	private DataObjectDAO dataObjectDAO = new DataObjectDAO();
	private BasicReferenceDAO referenceDAO = new BasicReferenceDAO();
	private UserDAO userDAO = new UserDAO();
	private DateHelper dateHelper = new DateHelper();

	/**
	 * Creates a Collection and stores it in Neo4J
	 *
	 * @param collection to be stored
	 * @param username   of the related user
	 * @return the created collection
	 */
	public Collection createCollection(CollectionIO collection, String username) {
		var user = userDAO.find(username);

		var toCreate = new Collection();
		toCreate.setAttributes(collection.getAttributes());
		toCreate.setCreatedBy(user);
		toCreate.setCreatedAt(dateHelper.getDate());
		toCreate.setDescription(collection.getDescription());
		toCreate.setName(collection.getName());

		var created = collectionDAO.createOrUpdate(toCreate);
		return created;
	}

	/**
	 * Searches the Collection in Neo4j
	 *
	 * @param id identifies the searched Collection
	 * @return the Collection with matching id or null
	 */
	public Collection getCollection(long id) {
		var collection = collectionDAO.find(id);
		if (collection == null || collection.isDeleted()) {
			return null;
		}
		cutDeleted(collection);
		return collection;
	}

	/**
	 * Searches the database for all Collections
	 *
	 * @param params encapsulates possible parameters
	 * @return a list of Collections
	 */
	public List<Collection> getAllCollections(QueryParamHelper params) {
		var queryResult = collectionDAO.findAllCollections(params);

		var collections = queryResult.stream().filter(c -> !c.isDeleted()).peek(this::cutDeleted)
				.collect(Collectors.toList());

		return collections;
	}

	/**
	 * Updates a Collection with new Attributes.
	 *
	 * @param collectionId identifies the Collection
	 * @param collection   which contains the new Attributes
	 * @param username     of the related user
	 * @return updated Collection
	 */
	public Collection updateCollection(long collectionId, CollectionIO collection, String username) {
		var old = collectionDAO.find(collectionId);

		old.setUpdatedBy(userDAO.find(username));
		old.setUpdatedAt(dateHelper.getDate());

		old.setAttributes(collection.getAttributes());
		old.setDescription(collection.getDescription());
		old.setName(collection.getName());

		var updated = collectionDAO.createOrUpdate(old);
		cutDeleted(updated);
		return updated;
	}

	/**
	 * Deletes a Collection in Neo4j
	 *
	 * @param collectionId identifies the Collection
	 * @param username     of the related user
	 * @return a boolean to determine if Collection was successfully deleted
	 */
	public boolean deleteCollection(long collectionId, String username) {
		var date = dateHelper.getDate();
		var user = userDAO.find(username);

		// TODO: Improve this later
		var collection = collectionDAO.find(collectionId);
		for (var dataObjectLazy : collection.getDataObjects()) {
			var dataObject = dataObjectDAO.find(dataObjectLazy.getId());
			for (var referenceLazy : dataObject.getReferences()) {
				var reference = referenceDAO.find(referenceLazy.getId());
				reference.setUpdatedAt(date);
				reference.setUpdatedBy(user);
				reference.setDeleted(true);
				referenceDAO.createOrUpdate(reference);
			}
			dataObject.setUpdatedAt(date);
			dataObject.setUpdatedBy(user);
			dataObject.setDeleted(true);
			dataObjectDAO.createOrUpdate(dataObject);
		}
		collection.setUpdatedBy(user);
		collection.setUpdatedAt(date);
		collection.setDeleted(true);
		collectionDAO.createOrUpdate(collection);

		return true;
	}

	/**
	 * Remove deleted DataObjects from collection
	 *
	 * @param collection The collection to remove deleted DataObjects from
	 */
	private void cutDeleted(Collection collection) {
		var dataObjects = collection.getDataObjects().stream().filter(d -> !d.isDeleted()).collect(Collectors.toList());
		collection.setDataObjects(dataObjects);
	}
}
