package de.dlr.shepard.neo4Core.services;

import java.util.List;

import de.dlr.shepard.neo4Core.dao.BasicReferenceDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.BasicReference;
import de.dlr.shepard.util.DateHelper;
import de.dlr.shepard.util.QueryParamHelper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BasicReferenceService {

	private BasicReferenceDAO basicReferenceDAO = new BasicReferenceDAO();
	private UserDAO userDAO = new UserDAO();
	private DateHelper dateHelper = new DateHelper();

	/**
	 * Searches the neo4j database for a BasicReference
	 *
	 * @param shepardId identifies the searched BasicReference
	 *
	 * @return the BasicReference with the given id or null
	 */
	public BasicReference getReferenceByShepardId(long shepardId) {
		BasicReference basicReference = basicReferenceDAO.findByShepardId(shepardId);
		if (basicReference == null || basicReference.isDeleted()) {
			log.error("Basic Reference with id {} is null or deleted", shepardId);
			return null;
		}
		return basicReference;
	}

	/**
	 * Searches the database for BasicReferences.
	 *
	 * @param dataObjectShepardId identifies the DataObject
	 * @param params              encapsulates possible parameters
	 * @return a List of BasicReferences
	 */
	public List<BasicReference> getAllBasicReferencesByDataObjectShepardId(long dataObjectShepardId,
			QueryParamHelper params) {
		var references = basicReferenceDAO.findByDataObjectShepardId(dataObjectShepardId, params);
		return references;
	}

	/**
	 * Set the deleted flag for the Reference
	 *
	 * @param basicReferenceShepardId identifies the BasicReference to be deleted
	 * @param username                identifies the user
	 * @return a boolean to identify if the BasicReference was successfully removed
	 */
	public boolean deleteReferenceByShepardId(long basicReferenceShepardId, String username) {
		var user = userDAO.find(username);

		var basicReference = basicReferenceDAO.findByShepardId(basicReferenceShepardId);
		basicReference.setDeleted(true);
		basicReference.setUpdatedAt(dateHelper.getDate());
		basicReference.setUpdatedBy(user);

		basicReferenceDAO.createOrUpdate(basicReference);
		return true;
	}

}
