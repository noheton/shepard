package de.dlr.shepard.neo4Core.services;

import java.util.ArrayList;
import java.util.List;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.mongoDB.StructuredData;
import de.dlr.shepard.mongoDB.StructuredDataPayload;
import de.dlr.shepard.mongoDB.StructuredDataService;
import de.dlr.shepard.neo4Core.dao.DataObjectDAO;
import de.dlr.shepard.neo4Core.dao.StructuredDataContainerDAO;
import de.dlr.shepard.neo4Core.dao.StructuredDataReferenceDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.StructuredDataReference;
import de.dlr.shepard.neo4Core.io.StructuredDataReferenceIO;
import de.dlr.shepard.util.DateHelper;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class StructuredDataReferenceService {

	private StructuredDataReferenceDAO structuredDataReferenceDAO = new StructuredDataReferenceDAO();
	private DataObjectDAO dataObjectDAO = new DataObjectDAO();
	private StructuredDataContainerDAO containerDAO = new StructuredDataContainerDAO();
	private UserDAO userDAO = new UserDAO();
	private DateHelper dateHelper = new DateHelper();
	private StructuredDataService structuredDataService = new StructuredDataService();

	public StructuredDataReference createStructuredDataReference(long dataObjectId,
			StructuredDataReferenceIO structuredDataReference, String username) throws InvalidBodyException {
		var user = userDAO.find(username);
		var dataObject = dataObjectDAO.find(dataObjectId);
		var container = containerDAO.find(structuredDataReference.getStructuredDataContainerId());
		if (container == null || container.isDeleted())
			throw new InvalidBodyException("invalid container");
		var toCreate = new StructuredDataReference();
		toCreate.setCreatedAt(dateHelper.getDate());
		toCreate.setCreatedBy(user);
		toCreate.setDataObject(dataObject);
		toCreate.setName(structuredDataReference.getName());
		toCreate.setStructuredDataContainer(container);

		// Get structured data
		for (var structuredData : structuredDataReference.getStructuredDataOids()) {
			var structuredDataPayload = structuredDataService.getPayload(container.getMongoId(), structuredData);
			if (structuredDataPayload != null) {
				toCreate.addStructuredData(structuredDataPayload.getStructuredData());
			} else {
				log.warn("Could not find structured data with oid: {}", structuredData);
			}
		}

		return structuredDataReferenceDAO.createOrUpdate(toCreate);
	}

	public List<StructuredDataReference> getAllStructuredDataReferences(long dataObjectId) {
		var references = structuredDataReferenceDAO.findByDataObject(dataObjectId);
		return references;
	}

	/**
	 * Searches the neo4j database for a StructuredDataReference
	 *
	 * @param id identifies the searched StructuredDataReference
	 *
	 * @return the StructuredDataReference with the given id or null
	 */
	public StructuredDataReference getStructuredDataReference(long id) {
		StructuredDataReference structuredDataReference = structuredDataReferenceDAO.find(id);
		if (structuredDataReference == null || structuredDataReference.isDeleted()) {
			return null;
		}
		return structuredDataReference;
	}

	/**
	 * set the deleted flag for the Reference
	 *
	 * @param structuredDataReferenceId identifies the StructuredDataReference to be
	 *                                  deleted
	 * @param username                  the deleting user
	 * @return a boolean to identify if the StructuredDataReference was successfully
	 *         removed
	 */
	public boolean deleteReference(long structuredDataReferenceId, String username) {
		StructuredDataReference structuredDataReference = structuredDataReferenceDAO.find(structuredDataReferenceId);
		var user = userDAO.find(username);
		structuredDataReference.setDeleted(true);
		structuredDataReference.setUpdatedBy(user);
		structuredDataReference.setUpdatedAt(dateHelper.getDate());
		structuredDataReferenceDAO.createOrUpdate(structuredDataReference);
		return true;
	}

	public List<StructuredDataPayload> getAllPayloads(long structuredDataReferenceId) {
		StructuredDataReference reference = structuredDataReferenceDAO.find(structuredDataReferenceId);
		String mongoId = reference.getStructuredDataContainer().getMongoId();
		List<StructuredData> structuredDatas = reference.getStructuredDatas();
		var result = new ArrayList<StructuredDataPayload>(structuredDatas.size());
		for (var structuredData : structuredDatas) {
			var payload = structuredDataService.getPayload(mongoId, structuredData.getOid());
			if (payload != null)
				result.add(payload);
			else
				result.add(new StructuredDataPayload(structuredData, null));
		}
		return result;
	}

	public StructuredDataPayload getPayload(long structuredDataReferenceId, String oid) {
		StructuredDataReference reference = structuredDataReferenceDAO.find(structuredDataReferenceId);
		String mongoId = reference.getStructuredDataContainer().getMongoId();
		var result = structuredDataService.getPayload(mongoId, oid);
		return result;
	}
}
