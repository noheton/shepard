package de.dlr.shepard.neo4Core.services;

import java.util.ArrayList;
import java.util.List;

import de.dlr.shepard.exceptions.InvalidAuthException;
import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.mongoDB.StructuredData;
import de.dlr.shepard.mongoDB.StructuredDataPayload;
import de.dlr.shepard.mongoDB.StructuredDataService;
import de.dlr.shepard.neo4Core.dao.DataObjectDAO;
import de.dlr.shepard.neo4Core.dao.StructuredDataContainerDAO;
import de.dlr.shepard.neo4Core.dao.StructuredDataDAO;
import de.dlr.shepard.neo4Core.dao.StructuredDataReferenceDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.StructuredDataReference;
import de.dlr.shepard.neo4Core.io.StructuredDataReferenceIO;
import de.dlr.shepard.security.PermissionsUtil;
import de.dlr.shepard.util.AccessType;
import de.dlr.shepard.util.DateHelper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StructuredDataReferenceService
		implements IReferenceService<StructuredDataReference, StructuredDataReferenceIO> {

	private StructuredDataReferenceDAO structuredDataReferenceDAO = new StructuredDataReferenceDAO();
	private DataObjectDAO dataObjectDAO = new DataObjectDAO();
	private StructuredDataContainerDAO containerDAO = new StructuredDataContainerDAO();
	private StructuredDataDAO structuredDataDAO = new StructuredDataDAO();
	private UserDAO userDAO = new UserDAO();
	private DateHelper dateHelper = new DateHelper();
	private StructuredDataService structuredDataService = new StructuredDataService();
	private PermissionsUtil permissionsUtil = new PermissionsUtil();

	@Override
	public StructuredDataReference createReference(long dataObjectId, StructuredDataReferenceIO structuredDataReference,
			String username) {
		var user = userDAO.find(username);
		var dataObject = dataObjectDAO.findLight(dataObjectId);
		var container = containerDAO.findLight(structuredDataReference.getStructuredDataContainerId());
		if (container == null || container.isDeleted())
			throw new InvalidBodyException("invalid container");
		var toCreate = new StructuredDataReference();
		toCreate.setCreatedAt(dateHelper.getDate());
		toCreate.setCreatedBy(user);
		toCreate.setDataObject(dataObject);
		toCreate.setName(structuredDataReference.getName());
		toCreate.setStructuredDataContainer(container);

		// Get existing structured data
		for (var oid : structuredDataReference.getStructuredDataOids()) {
			var structuredData = structuredDataDAO.find(container.getId(), oid);
			if (structuredData != null) {
				toCreate.addStructuredData(structuredData);
			} else {
				log.warn("Could not find structured data with oid: {}", oid);
			}
		}

		return structuredDataReferenceDAO.createOrUpdate(toCreate);
	}

	@Override
	public List<StructuredDataReference> getAllReferences(long dataObjectId) {
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
	@Override
	public StructuredDataReference getReference(long id) {
		StructuredDataReference structuredDataReference = structuredDataReferenceDAO.find(id);
		if (structuredDataReference == null || structuredDataReference.isDeleted()) {
			log.error("Structured Data Reference with id {} is null or deleted", id);
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
	@Override
	public boolean deleteReference(long structuredDataReferenceId, String username) {
		StructuredDataReference structuredDataReference = structuredDataReferenceDAO.find(structuredDataReferenceId);
		var user = userDAO.find(username);
		structuredDataReference.setDeleted(true);
		structuredDataReference.setUpdatedBy(user);
		structuredDataReference.setUpdatedAt(dateHelper.getDate());
		structuredDataReferenceDAO.createOrUpdate(structuredDataReference);
		return true;
	}

	public List<StructuredDataPayload> getAllPayloads(long structuredDataReferenceId, String username) {
		StructuredDataReference reference = structuredDataReferenceDAO.find(structuredDataReferenceId);
		long containerId = reference.getStructuredDataContainer().getId();
		String mongoId = reference.getStructuredDataContainer().getMongoId();
		List<StructuredData> structuredDatas = reference.getStructuredDatas();

		if (!permissionsUtil.isAllowed(containerId, AccessType.Read, username))
			return structuredDatas.stream().map(sd -> new StructuredDataPayload(sd, null)).toList();

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

	public StructuredDataPayload getPayload(long structuredDataReferenceId, String oid, String username) {
		StructuredDataReference reference = structuredDataReferenceDAO.find(structuredDataReferenceId);
		long containerId = reference.getStructuredDataContainer().getId();
		String mongoId = reference.getStructuredDataContainer().getMongoId();

		if (!permissionsUtil.isAllowed(containerId, AccessType.Read, username))
			throw new InvalidAuthException("You are not authorized to access this structured data");

		return structuredDataService.getPayload(mongoId, oid);
	}
}
