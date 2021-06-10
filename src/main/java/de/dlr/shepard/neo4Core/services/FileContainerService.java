package de.dlr.shepard.neo4Core.services;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import de.dlr.shepard.mongoDB.File;
import de.dlr.shepard.mongoDB.FileService;
import de.dlr.shepard.neo4Core.dao.FileContainerDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.FileContainer;
import de.dlr.shepard.neo4Core.io.FileContainerIO;
import de.dlr.shepard.util.DateHelper;

public class FileContainerService {

	private FileContainerDAO fileContainerDAO = new FileContainerDAO();
	private UserDAO userDAO = new UserDAO();
	private DateHelper dateHelper = new DateHelper();
	private FileService fileService = new FileService();

	/**
	 * Creates a FileContainer and stores it in Neo4J
	 * 
	 * @param fileContainerIO to be stored
	 * @param username        of the related user
	 * @return the created FileContainer
	 */
	public FileContainer createFileContainer(FileContainerIO fileContainerIO, String username) {
		var user = userDAO.find(username);
		var toCreate = new FileContainer();
		toCreate.setCreatedAt(dateHelper.getDate());
		toCreate.setCreatedBy(user);
		toCreate.setMongoId(fileService.createFileContainer());
		toCreate.setName(fileContainerIO.getName());
		return fileContainerDAO.createOrUpdate(toCreate);
	}

	/**
	 * Searches the FileContainer in Neo4j
	 * 
	 * @param id identifies the searched FileContainer
	 * @return the FileContainer with matching id or null
	 */
	public FileContainer getFileContainer(long id) {
		FileContainer fileContainer = fileContainerDAO.find(id);
		if (fileContainer == null || fileContainer.isDeleted()) {
			return null;
		}
		return fileContainer;
	}

	/**
	 * Searches the database for all FileContainers
	 * 
	 * @return a list of FileContainers
	 */
	public List<FileContainer> getAllFileContainers() {
		var containers = fileContainerDAO.findAll();
		var result = new ArrayList<FileContainer>(containers.size());
		for (FileContainer fileContainer : containers) {
			if (!fileContainer.isDeleted()) {
				result.add(fileContainer);
			}
		}
		return result;
	}

	/**
	 * Deletes a FileContainer in Neo4j
	 * 
	 * @param fileContainerId identifies the FileContainer
	 * @param username        the deleting user
	 * @return a boolean to determine if FileContainer was successfully deleted
	 */
	public boolean deleteFileContainer(long fileContainerId, String username) {
		var user = userDAO.find(username);
		FileContainer fileContainer = fileContainerDAO.find(fileContainerId);
		if (fileContainer == null) {
			return false;
		}
		String mongoid = fileContainer.getMongoId();
		fileContainer.setDeleted(true);
		fileContainer.setUpdatedAt(dateHelper.getDate());
		fileContainer.setUpdatedBy(user);
		fileContainerDAO.createOrUpdate(fileContainer);
		return fileService.deleteFileContainer(mongoid);
	}

	public File createFile(long fileId, String fileName, InputStream inputStream) {
		var fileContainer = fileContainerDAO.find(fileId);
		if (fileContainer == null || fileContainer.isDeleted())
			return null;
		var result = fileService.createFile(fileContainer.getMongoId(), fileName, inputStream);
		return result;
	}

}
