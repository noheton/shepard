package de.dlr.shepard.neo4Core.services;

import java.util.List;

import de.dlr.shepard.exceptions.InvalidAuthException;
import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.mongoDB.FileService;
import de.dlr.shepard.mongoDB.NamedInputStream;
import de.dlr.shepard.mongoDB.ShepardFile;
import de.dlr.shepard.neo4Core.dao.DataObjectDAO;
import de.dlr.shepard.neo4Core.dao.FileContainerDAO;
import de.dlr.shepard.neo4Core.dao.FileReferenceDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.FileReference;
import de.dlr.shepard.neo4Core.io.FileReferenceIO;
import de.dlr.shepard.security.PermissionsUtil;
import de.dlr.shepard.util.AccessType;
import de.dlr.shepard.util.DateHelper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileReferenceService {

	private FileReferenceDAO fileReferenceDAO = new FileReferenceDAO();
	private DataObjectDAO dataObjectDAO = new DataObjectDAO();
	private FileContainerDAO containerDAO = new FileContainerDAO();
	private UserDAO userDAO = new UserDAO();
	private DateHelper dateHelper = new DateHelper();
	private FileService fileService = new FileService();
	private PermissionsUtil permissionsUtil = new PermissionsUtil();

	public List<FileReference> getAllFileReferences(long dataObjectId) {
		var references = fileReferenceDAO.findByDataObject(dataObjectId);
		return references;
	}

	public FileReference getFileReference(long id) {
		FileReference fileReference = fileReferenceDAO.find(id);
		if (fileReference == null || fileReference.isDeleted()) {
			log.error("File Reference with id {} is null or deleted", id);
			return null;
		}
		return fileReference;
	}

	public FileReference createFileReference(long dataObjectId, FileReferenceIO fileReference, String username) {
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

	public NamedInputStream getPayload(long fileReferenceId, String oid, String username) {
		FileReference reference = fileReferenceDAO.find(fileReferenceId);
		long containerId = reference.getFileContainer().getId();
		String mongoId = reference.getFileContainer().getMongoId();
		if (!permissionsUtil.isAllowed(containerId, AccessType.Read, username))
			throw new InvalidAuthException();

		return fileService.getPayload(mongoId, oid);
	}

	public List<ShepardFile> getFiles(long fileReferenceId) {
		FileReference reference = fileReferenceDAO.find(fileReferenceId);
		return reference.getFiles();
	}

}
