package de.dlr.shepard.neo4Core.services;

import java.util.List;

import de.dlr.shepard.neo4Core.dao.DataObjectDAO;
import de.dlr.shepard.neo4Core.dao.URIReferenceDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.URIReference;
import de.dlr.shepard.neo4Core.io.URIReferenceIO;
import de.dlr.shepard.util.DateHelper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class URIReferenceService implements IReferenceService<URIReference, URIReferenceIO> {

	private URIReferenceDAO dao = new URIReferenceDAO();
	private DataObjectDAO dataObjectDAO = new DataObjectDAO();
	private UserDAO userDAO = new UserDAO();
	private DateHelper dateHelper = new DateHelper();

	@Override
	public List<URIReference> getAllReferences(long dataObjectId) {
		var references = dao.findByDataObject(dataObjectId);
		return references;
	}

	@Override
	public URIReference getReference(long uriReferenceId) {
		var reference = dao.find(uriReferenceId);
		if (reference == null || reference.isDeleted()) {
			log.error("URI Reference with id {} is null or deleted", uriReferenceId);
			return null;
		}
		return reference;
	}

	@Override
	public URIReference createReference(long dataObjectId, URIReferenceIO uriReference, String username) {
		var user = userDAO.find(username);
		var dataObject = dataObjectDAO.findLight(dataObjectId);

		var toCreate = new URIReference();
		toCreate.setCreatedAt(dateHelper.getDate());
		toCreate.setCreatedBy(user);
		toCreate.setDataObject(dataObject);
		toCreate.setName(uriReference.getName());
		toCreate.setUri(uriReference.getUri());

		var created = dao.createOrUpdate(toCreate);
		return created;
	}

	@Override
	public boolean deleteReference(long uriReferenceId, String username) {
		var user = userDAO.find(username);

		var old = dao.find(uriReferenceId);
		old.setDeleted(true);
		old.setUpdatedAt(dateHelper.getDate());
		old.setUpdatedBy(user);

		dao.createOrUpdate(old);
		return true;
	}

}
