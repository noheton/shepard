package de.dlr.shepard.neo4Core.services;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.neo4Core.dao.BasicReferenceDAO;
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
	private BasicReferenceDAO referenceDAO = new BasicReferenceDAO();
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

		var parent = getDataObject(dataObject.getParentId());
		var predecessors = getDataObjects(dataObject.getPredecessorIds());

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

		var dataObjects = unfiltered.stream().peek(this::cutDeleted).collect(Collectors.toList());

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

		var parent = getDataObject(dataObject.getParentId());
		var predecessors = getDataObjects(dataObject.getPredecessorIds());

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

		var dataObject = dataObjectDAO.find(dataObjectId);
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

		return true;
	}

	private void cutDeleted(DataObject dataObject) {
		var incoming = dataObject.getIncoming().stream().filter(i -> !i.isDeleted()).collect(Collectors.toList());
		dataObject.setIncoming(incoming);
		if (dataObject.getParent() != null && dataObject.getParent().isDeleted()) {
			dataObject.setParent(null);
		}
		var children = dataObject.getChildren().stream().filter(s -> !s.isDeleted()).collect(Collectors.toList());
		dataObject.setChildren(children);
		var predecessors = dataObject.getPredecessors().stream().filter(s -> !s.isDeleted())
				.collect(Collectors.toList());
		dataObject.setPredecessors(predecessors);
		var sucessors = dataObject.getSuccessors().stream().filter(s -> !s.isDeleted()).collect(Collectors.toList());
		dataObject.setSuccessors(sucessors);
		var references = dataObject.getReferences().stream().filter(ref -> !ref.isDeleted())
				.collect(Collectors.toList());
		dataObject.setReferences(references);
	}

	private List<DataObject> getDataObjects(long[] ids) throws InvalidBodyException {
		var result = new ArrayList<DataObject>();
		if (ids == null) {
			return result;
		}

		result.ensureCapacity(ids.length);
		for (var id : ids) {
			var dataObject = dataObjectDAO.find(id);
			if (dataObject == null || dataObject.isDeleted()) {
				throw new InvalidBodyException(String.format("The DataObject with id %d could not be found.", id));
			}
			result.add(dataObject);
		}
		return result;
	}

	private DataObject getDataObject(Long id) throws InvalidBodyException {
		DataObject dataObject = null;
		if (id != null) {
			dataObject = dataObjectDAO.find(id);
			if (dataObject == null || dataObject.isDeleted()) {
				throw new InvalidBodyException(String.format("The DataObject with id %d could not be found.", id));
			}
		}
		return dataObject;
	}
}
