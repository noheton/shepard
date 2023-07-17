package de.dlr.shepard.neo4Core.services;

import java.util.List;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.neo4Core.dao.CollectionDAO;
import de.dlr.shepard.neo4Core.dao.CollectionReferenceDAO;
import de.dlr.shepard.neo4Core.dao.DataObjectDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.neo4Core.entities.CollectionReference;
import de.dlr.shepard.neo4Core.io.CollectionReferenceIO;
import de.dlr.shepard.util.DateHelper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CollectionReferenceService implements IReferenceService<CollectionReference, CollectionReferenceIO> {
	private CollectionReferenceDAO collectionReferenceDAO = new CollectionReferenceDAO();
	private DataObjectDAO dataObjectDAO = new DataObjectDAO();
	private CollectionDAO collectionDAO = new CollectionDAO();
	private UserDAO userDAO = new UserDAO();
	private DateHelper dateHelper = new DateHelper();

	@Override
	public List<CollectionReference> getAllReferences(long dataObjectId) {
		var references = collectionReferenceDAO.findByDataObject(dataObjectId);
		return references;
	}

	@Override
	public CollectionReference getReference(long collectionReferenceId) {
		var reference = collectionReferenceDAO.find(collectionReferenceId);
		if (reference == null || reference.isDeleted()) {
			log.error("Collection Reference with id {} is null or deleted", collectionReferenceId);
			return null;
		}
		return reference;
	}

	@Override
	public CollectionReference createReference(long dataObjectId, CollectionReferenceIO collectionReference,
			String username) {
		var user = userDAO.find(username);
		var dataObject = dataObjectDAO.findLight(dataObjectId);

		var referenced = collectionDAO.findLight(collectionReference.getReferencedCollectionId());
		if (referenced == null || referenced.isDeleted()) {
			throw new InvalidBodyException(String.format("The referenced collection with id %d could not be found.",
					collectionReference.getReferencedCollectionId()));
		}

		var toCreate = new CollectionReference();
		toCreate.setCreatedAt(dateHelper.getDate());
		toCreate.setCreatedBy(user);
		toCreate.setDataObject(dataObject);
		toCreate.setName(collectionReference.getName());
		toCreate.setReferencedCollection(referenced);
		toCreate.setRelationship(collectionReference.getRelationship());

		var created = collectionReferenceDAO.createOrUpdate(toCreate);
		return created;
	}

	@Override
	public boolean deleteReference(long dataObjectReferenceId, String username) {
		var user = userDAO.find(username);

		var old = collectionReferenceDAO.find(dataObjectReferenceId);
		old.setDeleted(true);
		old.setUpdatedAt(dateHelper.getDate());
		old.setUpdatedBy(user);

		collectionReferenceDAO.createOrUpdate(old);
		return true;
	}

	public Collection getPayload(long dataObjectReferenceId) {
		var reference = collectionReferenceDAO.find(dataObjectReferenceId);
		var collection = collectionDAO.find(reference.getReferencedCollection().getId());
		if (collection.isDeleted()) {
			log.error("Collection with id {} is deleted", reference.getReferencedCollection().getId());
			return null;
		}
		return collection;
	}

}
