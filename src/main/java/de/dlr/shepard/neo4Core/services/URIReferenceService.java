package de.dlr.shepard.neo4Core.services;

import java.util.List;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.neo4Core.dao.DataObjectDAO;
import de.dlr.shepard.neo4Core.dao.URIReferenceDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.URIReference;
import de.dlr.shepard.neo4Core.io.URIReferenceIO;
import de.dlr.shepard.util.DateHelper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class URIReferenceService {

	private URIReferenceDAO dao = new URIReferenceDAO();
	private DataObjectDAO dataObjectDAO = new DataObjectDAO();
	private UserDAO userDAO = new UserDAO();
	private DateHelper dateHelper = new DateHelper();

	public List<URIReference> getAllURIReferences(long dataObjectId) {
		var references = dao.findByDataObject(dataObjectId);
		return references;
	}

	public URIReference getURIReference(long uriReferenceId) {
		var reference = dao.find(uriReferenceId);
		if (reference == null || reference.isDeleted()) {
			log.error("URI Reference with id {} is null or deleted", uriReferenceId);
			return null;
		}
		return reference;
	}

	public URIReference createURIReference(long dataObjectId, URIReferenceIO uriReference, String username)
			throws InvalidBodyException {
		var user = userDAO.find(username);
		var dataObject = dataObjectDAO.find(dataObjectId);

		var toCreate = new URIReference();
		toCreate.setCreatedAt(dateHelper.getDate());
		toCreate.setCreatedBy(user);
		toCreate.setDataObject(dataObject);
		toCreate.setName(uriReference.getName());
		toCreate.setUri(uriReference.getUri());

		var created = dao.createOrUpdate(toCreate);
		return created;
	}

	public boolean deleteURIReference(long uriReferenceId, String username) {
		var user = userDAO.find(username);

		var old = dao.find(uriReferenceId);
		old.setDeleted(true);
		old.setUpdatedAt(dateHelper.getDate());
		old.setUpdatedBy(user);

		dao.createOrUpdate(old);
		return true;
	}

}
