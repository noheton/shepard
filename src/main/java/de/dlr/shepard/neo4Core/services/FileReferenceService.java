package de.dlr.shepard.neo4Core.services;

import java.util.List;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.mongoDB.File;
import de.dlr.shepard.mongoDB.FileService;
import de.dlr.shepard.mongoDB.NamedInputStream;
import de.dlr.shepard.neo4Core.dao.DataObjectDAO;
import de.dlr.shepard.neo4Core.dao.FileContainerDAO;
import de.dlr.shepard.neo4Core.dao.FileReferenceDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.FileReference;
import de.dlr.shepard.neo4Core.io.FileReferenceIO;
import de.dlr.shepard.util.DateHelper;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class FileReferenceService {

	private FileReferenceDAO fileReferenceDAO = new FileReferenceDAO();
	private DataObjectDAO dataObjectDAO = new DataObjectDAO();
	private FileContainerDAO containerDAO = new FileContainerDAO();
	private UserDAO userDAO = new UserDAO();
	private DateHelper dateHelper = new DateHelper();
	private FileService fileService = new FileService();

	public List<FileReference> getAllFileReferences(long dataObjectId) {
		var references = fileReferenceDAO.findByDataObject(dataObjectId);
		return references;
	}

	public FileReference getFileReference(long id) {
		FileReference fileReference = fileReferenceDAO.find(id);
		if (fileReference == null || fileReference.isDeleted()) {
			return null;
		}
		return fileReference;
	}

	public FileReference createFileReference(long dataObjectId, FileReferenceIO fileReference, String username)
			throws InvalidBodyException {
		var user = userDAO.find(username);
		var dataObject = dataObjectDAO.find(dataObjectId);
		var container = containerDAO.find(fileReference.getFileContainerId());
		if (container == null || container.isDeleted())
			throw new InvalidBodyException("invalid container");
		var toCreate = new FileReference();
		toCreate.setCreatedAt(dateHelper.getDate());
		toCreate.setCreatedBy(user);
		toCreate.setDataObject(dataObject);
		toCreate.setName(fileReference.getName());
		toCreate.setFileContainer(container);

		// Get filename per file
		for (var file : fileReference.getFileOids()) {
			var newFile = fileService.getFile(container.getMongoId(), file);
			if (newFile != null) {
				toCreate.addFile(newFile);
			} else {
				log.warn("Could not find file with oid: {}", file);
			}
		}

		return fileReferenceDAO.createOrUpdate(toCreate);
	}

	public boolean deleteReference(long fileReferenceId, String username) {
		FileReference fileReference = fileReferenceDAO.find(fileReferenceId);
		var user = userDAO.find(username);
		fileReference.setDeleted(true);
		fileReference.setUpdatedBy(user);
		fileReference.setUpdatedAt(dateHelper.getDate());
		fileReferenceDAO.createOrUpdate(fileReference);
		return true;
	}

	public NamedInputStream getPayload(long fileId, String oid) {
		FileReference reference = fileReferenceDAO.find(fileId);
		String containerId = reference.getFileContainer().getMongoId();
		var result = fileService.getPayload(containerId, oid);
		return result;
	}

	public List<File> getFiles(long fileId) {
		FileReference reference = fileReferenceDAO.find(fileId);
		return reference.getFiles();
	}

}
