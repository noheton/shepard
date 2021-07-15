package de.dlr.shepard.neo4Core.services;

import java.util.List;
import java.util.stream.Collectors;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.neo4Core.dao.DataObjectDAO;
import de.dlr.shepard.neo4Core.dao.DataObjectReferenceDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.DataObjectReference;
import de.dlr.shepard.neo4Core.io.DataObjectReferenceIO;
import de.dlr.shepard.util.DateHelper;

public class DataObjectReferenceService {
	private DataObjectReferenceDAO dataObjectReferenceDAO = new DataObjectReferenceDAO();
	private DataObjectDAO dataObjectDAO = new DataObjectDAO();
	private UserDAO userDAO = new UserDAO();
	private DateHelper dateHelper = new DateHelper();

	public List<DataObjectReference> getAllDataObjectReferences(long dataObjectId) {
		var references = dataObjectReferenceDAO.findByDataObject(dataObjectId);
		var result = references.stream().filter(r -> !r.isDeleted()).collect(Collectors.toList());
		return result;
	}

	public DataObjectReference getDataObjectReference(long dataObjectReferenceId) {
		var reference = dataObjectReferenceDAO.find(dataObjectReferenceId);
		if (reference == null || reference.isDeleted()) {
			return null;
		}
		return reference;
	}

	public DataObjectReference createDataObjectReference(long dataObjectId, DataObjectReferenceIO dataObjectReference,
			String username) throws InvalidBodyException {
		var user = userDAO.find(username);
		var dataObject = dataObjectDAO.find(dataObjectId);

		var referenced = dataObjectDAO.find(dataObjectReference.getReferencedDataObjectId());
		if (referenced == null || referenced.isDeleted()) {
			throw new InvalidBodyException(String.format("The referenced dataObject with id %d could not be found.",
					dataObjectReference.getReferencedDataObjectId()));
		}

		var toCreate = new DataObjectReference();
		toCreate.setCreatedAt(dateHelper.getDate());
		toCreate.setCreatedBy(user);
		toCreate.setDataObject(dataObject);
		toCreate.setName(dataObjectReference.getName());
		toCreate.setReferencedDataObject(referenced);
		toCreate.setRelationship(dataObjectReference.getRelationship());

		var created = dataObjectReferenceDAO.createOrUpdate(toCreate);
		return created;
	}

	public boolean deleteDataObjectReference(long dataObjectReferenceId, String username) {
		var user = userDAO.find(username);

		var old = dataObjectReferenceDAO.find(dataObjectReferenceId);
		old.setDeleted(true);
		old.setUpdatedAt(dateHelper.getDate());
		old.setUpdatedBy(user);

		dataObjectReferenceDAO.createOrUpdate(old);
		return true;
	}

	public DataObject getPayload(long dataObjectReferenceId) {
		var reference = dataObjectReferenceDAO.find(dataObjectReferenceId);
		var dataObject = dataObjectDAO.find(reference.getReferencedDataObject().getId());
		return dataObject;
	}

}
