package de.dlr.shepard.neo4Core.services;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.neo4Core.dao.CollectionDAO;
import de.dlr.shepard.neo4Core.dao.DataObjectDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.io.DataObjectIO;
import de.dlr.shepard.util.DateHelper;
import de.dlr.shepard.util.QueryParamHelper;

public class DataObjectService {
	private DataObjectDAO dataObjectDAO = new DataObjectDAO();
	private CollectionDAO collectionDAO = new CollectionDAO();
	private UserDAO userDAO = new UserDAO();
	private DateHelper dateHelper = new DateHelper();

	/**
	 * Creates a DataObject and stores it in Neo4J
	 *
	 * @param collectionId identifies the Collection
	 * @param dataObject   to be stored
	 * @param username     of the related user
	 * @return the stored DataObject with the auto generated id
	 * @throws InvalidBodyException in case of an invalid dataObject
	 */
	public DataObject createDataObject(long collectionId, DataObjectIO dataObject, String username)
			throws InvalidBodyException {
		var collection = collectionDAO.find(collectionId);
		var user = userDAO.find(username);

		var parent = findRelatedDataObject(collection.getId(), dataObject.getParentId());
		var predecessors = findRelatedDataObjects(collection.getId(), dataObject.getPredecessorIds());

		var toCreate = new DataObject();
		toCreate.setAttributes(dataObject.getAttributes());
		toCreate.setDescription(dataObject.getDescription());
		toCreate.setName(dataObject.getName());
		toCreate.setCollection(collection);
		toCreate.setParent(parent);
		toCreate.setPredecessors(predecessors);
		toCreate.setCreatedAt(dateHelper.getDate());
		toCreate.setCreatedBy(user);

		var created = dataObjectDAO.createOrUpdate(toCreate);
		return created;
	}

	/**
	 * Searches the neo4j database for a dataObject
	 *
	 * @param id identifies the searched dataObject
	 * @return the DataObject with the given id or null
	 */
	public DataObject getDataObject(long id) {
		var dataObject = dataObjectDAO.find(id);
		if (dataObject == null || dataObject.isDeleted()) {
			return null;
		}
		cutDeleted(dataObject);
		return dataObject;
	}

	/**
	 * Searches the database for DataObjects.
	 *
	 * @param collectionId identifies the collection
	 * @param params       encapsulates possible parameters
	 * @return a List of DataObjects
	 */
	public List<DataObject> getAllDataObjects(long collectionId, QueryParamHelper params) {
		var unfiltered = dataObjectDAO.findByCollection(collectionId, params);

		var dataObjects = unfiltered.stream().peek(this::cutDeleted).toList();

		return dataObjects;
	}

	/**
	 * Updates a DataObject with new attributes. Hereby only not null attributes
	 * will replace the old attributes.
	 *
	 * @param dataObjectId Identifies the dataObject
	 * @param dataObject   DataObject entity for updating.
	 * @param username     of the related user
	 * @return updated DataObject.
	 * @throws InvalidBodyException in case of an invalid dataObject
	 */
	public DataObject updateDataObject(long dataObjectId, DataObjectIO dataObject, String username)
			throws InvalidBodyException {
		var old = dataObjectDAO.find(dataObjectId);
		var user = userDAO.find(username);

		var parent = findRelatedDataObject(old.getCollection().getId(), dataObject.getParentId());
		var predecessors = findRelatedDataObjects(old.getCollection().getId(), dataObject.getPredecessorIds());

		old.setAttributes(dataObject.getAttributes());
		old.setDescription(dataObject.getDescription());
		old.setName(dataObject.getName());
		old.setParent(parent);
		old.setPredecessors(predecessors);
		old.setUpdatedAt(dateHelper.getDate());
		old.setUpdatedBy(user);

		var updated = dataObjectDAO.createOrUpdate(old);
		cutDeleted(updated);
		return updated;
	}

	/**
	 * set the deleted flag for the DataObject
	 *
	 * @param dataObjectId identifies the DataObject to be deleted
	 * @param username     of the related user
	 * @return a boolean to identify if the DataObject was successfully removed
	 */
	public boolean deleteDataObject(long dataObjectId, String username) {
		var date = dateHelper.getDate();
		var user = userDAO.find(username);

		var result = dataObjectDAO.deleteDataObject(dataObjectId, user, date);
		return result;
	}

	private void cutDeleted(DataObject dataObject) {
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
	}

	private List<DataObject> findRelatedDataObjects(long collectionId, long[] ids) throws InvalidBodyException {
		if (ids == null)
			return new ArrayList<DataObject>();

		var result = new ArrayList<DataObject>(ids.length);
		for (var id : ids) {
			result.add(findRelatedDataObject(collectionId, id));
		}
		return result;
	}

	private DataObject findRelatedDataObject(long collectionId, Long id) throws InvalidBodyException {
		if (id == null)
			return null;

		DataObject dataObject = dataObjectDAO.find(id);
		if (dataObject == null || dataObject.isDeleted())
			throw new InvalidBodyException(String.format("The DataObject with id %d could not be found.", id));

		// Prevent cross collection references
		if (!dataObject.getCollection().getId().equals(collectionId))
			throw new InvalidBodyException(
					"Related data objects must belong to the same collection as the new data object");

		return dataObject;
	}
}
