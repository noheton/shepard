package de.dlr.shepard.neo4Core.services;

import java.util.ArrayList;
import java.util.List;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.neo4Core.dao.CollectionDAO;
import de.dlr.shepard.neo4Core.dao.DataObjectDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.io.DataObjectIO;
import de.dlr.shepard.util.DateHelper;
import de.dlr.shepard.util.QueryParamHelper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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
	 */
	public DataObject createDataObject(long collectionId, DataObjectIO dataObject, String username) {
		var collection = collectionDAO.find(collectionId);
		var user = userDAO.find(username);

		var parent = findRelatedDataObject(collection.getId(), dataObject.getParentId(), null);
		var predecessors = findRelatedDataObjects(collection.getId(), dataObject.getPredecessorIds(), null);

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
			log.error("Data Object with id {} is null or deleted", id);
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

		var dataObjects = unfiltered.stream().map(this::cutDeleted).toList();

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
	 */
	public DataObject updateDataObject(long dataObjectId, DataObjectIO dataObject, String username) {
		var old = dataObjectDAO.find(dataObjectId);
		var user = userDAO.find(username);

		var parent = findRelatedDataObject(old.getCollection().getId(), dataObject.getParentId(), dataObjectId);
		var predecessors = findRelatedDataObjects(old.getCollection().getId(), dataObject.getPredecessorIds(),
				dataObjectId);

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

	private List<DataObject> findRelatedDataObjects(long collectionId, long[] referencedIds, Long dataObjectId) {
		if (referencedIds == null)
			return new ArrayList<>();

		var result = new ArrayList<DataObject>(referencedIds.length);
		for (var id : referencedIds) {
			result.add(findRelatedDataObject(collectionId, id, dataObjectId));
		}
		return result;
	}

	private DataObject findRelatedDataObject(long collectionId, Long referencedId, Long dataObjectId) {
		if (referencedId == null)
			return null;
		else if (referencedId.equals(dataObjectId))
			throw new InvalidBodyException("Self references are not allowed.");

		var dataObject = dataObjectDAO.find(referencedId);
		if (dataObject == null || dataObject.isDeleted())
			throw new InvalidBodyException(
					String.format("The DataObject with id %d could not be found.", referencedId));

		// Prevent cross collection references
		if (!dataObject.getCollection().getId().equals(collectionId))
			throw new InvalidBodyException(
					"Related data objects must belong to the same collection as the new data object");

		return dataObject;
	}
}
