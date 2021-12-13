package de.dlr.shepard.neo4Core.services;

import java.io.InputStream;
import java.util.List;

import de.dlr.shepard.mongoDB.File;
import de.dlr.shepard.mongoDB.FileService;
import de.dlr.shepard.mongoDB.NamedInputStream;
import de.dlr.shepard.neo4Core.dao.FileContainerDAO;
import de.dlr.shepard.neo4Core.dao.PermissionsDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.FileContainer;
import de.dlr.shepard.neo4Core.entities.Permissions;
import de.dlr.shepard.neo4Core.io.FileContainerIO;
import de.dlr.shepard.util.DateHelper;
import de.dlr.shepard.util.QueryParamHelper;

public class FileContainerService {

	private FileContainerDAO fileContainerDAO = new FileContainerDAO();
	private PermissionsDAO permissionsDAO = new PermissionsDAO();
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

		var created = fileContainerDAO.createOrUpdate(toCreate);
		permissionsDAO.createOrUpdate(new Permissions(created, user));
		return created;
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
	 * @param params   QueryParamsHelper
	 * @param username the name of the user
	 * @return a list of FileContainers
	 */
	public List<FileContainer> getAllFileContainers(QueryParamHelper params, String username) {
		var containers = fileContainerDAO.findAllFileContainers(params, username);
		return containers;
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

	/**
	 * Get file payload
	 *
	 * @param fileContainerId The container to get the payload from
	 * @param oid             The specific file
	 * @return a NamedInputStream
	 */
	public NamedInputStream getFile(long fileContainerId, String oid) {
		var container = fileContainerDAO.find(fileContainerId);
		if (container == null || container.isDeleted())
			return null;
		var result = fileService.getPayload(container.getMongoId(), oid);
		return result;
	}

	/**
	 * Create a new file
	 *
	 * @param fileId      identifies the file container
	 * @param fileName    the name of the new file
	 * @param inputStream the file itself
	 * @return The newly created file
	 */
	public File createFile(long fileId, String fileName, InputStream inputStream) {
		var fileContainer = fileContainerDAO.find(fileId);
		if (fileContainer == null || fileContainer.isDeleted())
			return null;
		var result = fileService.createFile(fileContainer.getMongoId(), fileName, inputStream);
		fileContainer.addFile(result);
		fileContainerDAO.createOrUpdate(fileContainer);
		return result;
	}

	/**
	 * Delete one file
	 *
	 * @param fileContainerId The container to get the payload from
	 * @param oid             The specific file
	 * @return Whether the deletion was successful or not
	 */
	public boolean deleteFile(long fileContainerId, String oid) {
		var container = fileContainerDAO.find(fileContainerId);
		if (container == null || container.isDeleted())
			return false;
		var result = fileService.deleteFile(container.getMongoId(), oid);
		if (result) {
			var newFiles = container.getFiles().stream().filter(f -> !f.getOid().equals(oid)).toList();
			container.setFiles(newFiles);
			fileContainerDAO.createOrUpdate(container);
		}
		return result;
	}

}
