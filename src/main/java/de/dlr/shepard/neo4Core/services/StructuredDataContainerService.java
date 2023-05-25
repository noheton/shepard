package de.dlr.shepard.neo4Core.services;

import java.util.List;

import de.dlr.shepard.mongoDB.StructuredData;
import de.dlr.shepard.mongoDB.StructuredDataPayload;
import de.dlr.shepard.mongoDB.StructuredDataService;
import de.dlr.shepard.neo4Core.dao.PermissionsDAO;
import de.dlr.shepard.neo4Core.dao.StructuredDataContainerDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.Permissions;
import de.dlr.shepard.neo4Core.entities.StructuredDataContainer;
import de.dlr.shepard.neo4Core.io.StructuredDataContainerIO;
import de.dlr.shepard.util.DateHelper;
import de.dlr.shepard.util.PermissionType;
import de.dlr.shepard.util.QueryParamHelper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StructuredDataContainerService
		implements IContainerService<StructuredDataContainer, StructuredDataContainerIO> {

	private StructuredDataContainerDAO structuredDataContainerDAO = new StructuredDataContainerDAO();
	private StructuredDataService structuredDataService = new StructuredDataService();
	private PermissionsDAO permissionsDAO = new PermissionsDAO();
	private UserDAO userDAO = new UserDAO();
	private DateHelper dateHelper = new DateHelper();

	/**
	 * Creates a StructuredDataContainer and stores it in Neo4J
	 *
	 * @param structuredDataContainerIO to be stored
	 * @param username                  of the related user
	 * @return the created StructuredDataContainer
	 */
	@Override
	public StructuredDataContainer createContainer(StructuredDataContainerIO structuredDataContainerIO,
			String username) {
		var user = userDAO.find(username);
		String mongoid = structuredDataService.createStructuredDataContainer();
		var toCreate = new StructuredDataContainer();
		toCreate.setCreatedAt(dateHelper.getDate());
		toCreate.setCreatedBy(user);
		toCreate.setMongoId(mongoid);
		toCreate.setName(structuredDataContainerIO.getName());

		var created = structuredDataContainerDAO.createOrUpdate(toCreate);
		permissionsDAO.createOrUpdate(new Permissions(created, user, PermissionType.Private));
		return created;
	}

	/**
	 * Searches the StructuredDataContainer in Neo4j
	 *
	 * @param id identifies the searched StructuredDataContainer
	 * @return the StructuredDataContainer with matching id or null
	 */
	@Override
	public StructuredDataContainer getContainer(long id) {
		StructuredDataContainer structuredDataContainer = structuredDataContainerDAO.find(id);
		if (structuredDataContainer == null || structuredDataContainer.isDeleted()) {
			log.error("Structured Data Container with id {} is null or deleted", id);
			return null;
		}
		return structuredDataContainer;
	}

	/**
	 * Searches the database for all StructuredDataContainers
	 *
	 * @param params   QueryParamsHelper
	 * @param username the name of the user
	 * @return a list of StructuredDataContainers
	 */
	@Override
	public List<StructuredDataContainer> getAllContainers(QueryParamHelper params, String username) {
		var containers = structuredDataContainerDAO.findAllStructuredDataContainers(params, username);
		return containers;
	}

	/**
	 * Deletes a StructuredDataContainer in Neo4j
	 *
	 * @param structuredDataId identifies the StructuredDataContainer
	 * @param username         identifies the deleting user
	 * @return a boolean to determine if StructuredDataContainer was successfully
	 *         deleted
	 */
	@Override
	public boolean deleteContainer(long structuredDataId, String username) {
		var user = userDAO.find(username);
		StructuredDataContainer structuredDataContainer = structuredDataContainerDAO.find(structuredDataId);
		if (structuredDataContainer == null) {
			return false;
		}
		String mongoid = structuredDataContainer.getMongoId();
		structuredDataContainer.setDeleted(true);
		structuredDataContainer.setUpdatedAt(dateHelper.getDate());
		structuredDataContainer.setUpdatedBy(user);
		structuredDataContainerDAO.createOrUpdate(structuredDataContainer);
		return structuredDataService.deleteStructuredDataContainer(mongoid);
	}

	/**
	 * Upload structured data
	 *
	 * @param structuredDataContainerID identifies the container
	 * @param payload                   the payload to upload
	 * @return StructuredData with the new oid
	 */
	public StructuredData createStructuredData(long structuredDataContainerID, StructuredDataPayload payload) {
		var structuredDataContainer = structuredDataContainerDAO.find(structuredDataContainerID);
		if (structuredDataContainer == null || structuredDataContainer.isDeleted()) {
			log.error("Structured Data Container with id {} is null or deleted", structuredDataContainerID);
			return null;
		}
		var result = structuredDataService.createStructuredData(structuredDataContainer.getMongoId(), payload);
		if (result == null) {
			log.error("Failed to create structured data");
			return null;
		}
		structuredDataContainer.addStructuredData(result);
		structuredDataContainerDAO.createOrUpdate(structuredDataContainer);
		return result;
	}

	/**
	 * Get uploaded structured data
	 *
	 * @param structuredDataContainerID identifies the container
	 * @param oid                       identifies the structured data within the
	 *                                  container
	 * @return StructuredDataPayload
	 */
	public StructuredDataPayload getStructuredData(long structuredDataContainerID, String oid) {
		var structuredDataContainer = structuredDataContainerDAO.find(structuredDataContainerID);
		if (structuredDataContainer == null || structuredDataContainer.isDeleted()) {
			log.error("Structured Data Container with id {} is null or deleted", structuredDataContainerID);
			return null;
		}
		var result = structuredDataService.getPayload(structuredDataContainer.getMongoId(), oid);
		return result;
	}

	/**
	 * Delete one single structured data object
	 *
	 * @param structuredDataContainerID identifies the container
	 * @param oid                       identifies the structured data within the
	 *                                  container
	 * @return Whether the deletion was successful or not
	 */
	public boolean deleteStructuredData(long structuredDataContainerID, String oid) {
		var structuredDataContainer = structuredDataContainerDAO.find(structuredDataContainerID);
		if (structuredDataContainer == null || structuredDataContainer.isDeleted())
			return false;
		var result = structuredDataService.deletePayload(structuredDataContainer.getMongoId(), oid);
		if (result) {
			var newStructuredDatas = structuredDataContainer.getStructuredDatas().stream()
					.filter(f -> !f.getOid().equals(oid)).toList();
			structuredDataContainer.setStructuredDatas(newStructuredDatas);
			structuredDataContainerDAO.createOrUpdate(structuredDataContainer);
		}
		return result;
	}

}
