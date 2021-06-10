package de.dlr.shepard.neo4Core.services;

import java.util.ArrayList;
import java.util.List;

import de.dlr.shepard.mongoDB.StructuredData;
import de.dlr.shepard.mongoDB.StructuredDataPayload;
import de.dlr.shepard.mongoDB.StructuredDataService;
import de.dlr.shepard.neo4Core.dao.StructuredDataContainerDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.StructuredDataContainer;
import de.dlr.shepard.neo4Core.io.StructuredDataContainerIO;
import de.dlr.shepard.util.DateHelper;

public class StructuredDataContainerService {

	private StructuredDataContainerDAO structuredDataContainerDAO = new StructuredDataContainerDAO();
	private StructuredDataService structuredDataService = new StructuredDataService();
	private UserDAO userDAO = new UserDAO();
	private DateHelper dateHelper = new DateHelper();

	/**
	 * Creates a StructuredDataContainer and stores it in Neo4J
	 * 
	 * @param structuredDataContainerIO to be stored
	 * @param username                  of the related user
	 * @return the created StructuredDataContainer
	 */
	public StructuredDataContainer createStructuredDataContainer(StructuredDataContainerIO structuredDataContainerIO,
			String username) {
		var user = userDAO.find(username);
		String mongoid = structuredDataService.createStructuredDataContainer();
		var toCreate = new StructuredDataContainer();
		toCreate.setCreatedAt(dateHelper.getDate());
		toCreate.setCreatedBy(user);
		toCreate.setMongoId(mongoid);
		toCreate.setName(structuredDataContainerIO.getName());
		return structuredDataContainerDAO.createOrUpdate(toCreate);
	}

	/**
	 * Searches the StructuredDataContainer in Neo4j
	 * 
	 * @param id identifies the searched StructuredDataContainer
	 * @return the StructuredDataContainer with matching id or null
	 */
	public StructuredDataContainer getStructuredDataContainer(long id) {
		StructuredDataContainer structuredDataContainer = structuredDataContainerDAO.find(id);
		if (structuredDataContainer == null || structuredDataContainer.isDeleted()) {
			return null;
		}
		return structuredDataContainer;
	}

	/**
	 * Searches the database for all StructuredDataContainers
	 * 
	 * @return a list of StructuredDataContainers
	 */
	public List<StructuredDataContainer> getAllStructuredDataContainers() {
		var containers = structuredDataContainerDAO.findAll();
		var result = new ArrayList<StructuredDataContainer>(containers.size());
		for (StructuredDataContainer structuredDataContainer : containers) {
			if (!structuredDataContainer.isDeleted()) {
				result.add(structuredDataContainer);
			}
		}
		return result;
	}

	/**
	 * Deletes a StructuredDataContainer in Neo4j
	 * 
	 * @param structuredDataId identifies the StructuredDataContainer
	 * @param username         identifies the deleting user
	 * @return a boolean to determine if StructuredDataContainer was successfully
	 *         deleted
	 */
	public boolean deleteStructuredDataContainer(long structuredDataId, String username) {
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

	public StructuredData createStructuredData(long structuredDataContainerID, StructuredDataPayload payload) {
		var structuredDataContainer = structuredDataContainerDAO.find(structuredDataContainerID);
		if (structuredDataContainer == null || structuredDataContainer.isDeleted())
			return null;
		var result = structuredDataService.createStructuredData(structuredDataContainer.getMongoId(), payload);
		return result;
	}

}
