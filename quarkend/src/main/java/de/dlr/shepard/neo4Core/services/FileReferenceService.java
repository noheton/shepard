package de.dlr.shepard.neo4Core.services;

import de.dlr.shepard.exceptions.InvalidAuthException;
import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.mongoDB.FileService;
import de.dlr.shepard.mongoDB.NamedInputStream;
import de.dlr.shepard.mongoDB.ShepardFile;
import de.dlr.shepard.neo4Core.dao.DataObjectDAO;
import de.dlr.shepard.neo4Core.dao.FileContainerDAO;
import de.dlr.shepard.neo4Core.dao.FileReferenceDAO;
import de.dlr.shepard.neo4Core.dao.ShepardFileDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.FileReference;
import de.dlr.shepard.neo4Core.io.FileReferenceIO;
import de.dlr.shepard.security.PermissionsUtil;
import de.dlr.shepard.util.AccessType;
import de.dlr.shepard.util.DateHelper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileReferenceService implements IReferenceService<FileReference, FileReferenceIO> {

  private FileReferenceDAO fileReferenceDAO = new FileReferenceDAO();
  private DataObjectDAO dataObjectDAO = new DataObjectDAO();
  private FileContainerDAO containerDAO = new FileContainerDAO();
  private ShepardFileDAO fileDAO = new ShepardFileDAO();
  private UserDAO userDAO = new UserDAO();
  private DateHelper dateHelper = new DateHelper();
  private FileService fileService = new FileService();
  private PermissionsUtil permissionsUtil = new PermissionsUtil();

  @Override
  public List<FileReference> getAllReferencesByDataObjectShepardId(long dataObjectShepardId) {
    var references = fileReferenceDAO.findByDataObjectShepardId(dataObjectShepardId);
    return references;
  }

  @Override
  public FileReference getReferenceByShepardId(long shepardId) {
    FileReference fileReference = fileReferenceDAO.findByShepardId(shepardId);
    if (fileReference == null || fileReference.isDeleted()) {
      log.error("File Reference with id {} is null or deleted", shepardId);
      return null;
    }
    return fileReference;
  }

  @Override
  public FileReference createReferenceByShepardId(
    long dataObjectShepardId,
    FileReferenceIO fileReference,
    String username
  ) {
    var user = userDAO.find(username);
    var dataObject = dataObjectDAO.findLightByShepardId(dataObjectShepardId);
    var container = containerDAO.findLightByNeo4jId(fileReference.getFileContainerId());
    if (container == null || container.isDeleted()) throw new InvalidBodyException("invalid container");
    var toCreate = new FileReference();
    toCreate.setCreatedAt(dateHelper.getDate());
    toCreate.setCreatedBy(user);
    toCreate.setDataObject(dataObject);
    toCreate.setName(fileReference.getName());
    toCreate.setFileContainer(container);

    // Get existing file
    for (var oid : fileReference.getFileOids()) {
      var file = fileDAO.find(container.getId(), oid);
      if (file != null) {
        toCreate.addFile(file);
      } else {
        log.warn("Could not find file with oid: {}", oid);
      }
    }

    var created = fileReferenceDAO.createOrUpdate(toCreate);
    created.setShepardId(created.getId());
    created = fileReferenceDAO.createOrUpdate(created);
    return created;
  }

  @Override
  public boolean deleteReferenceByShepardId(long fileReferenceShepardId, String username) {
    FileReference fileReference = fileReferenceDAO.findByShepardId(fileReferenceShepardId);
    var user = userDAO.find(username);
    fileReference.setDeleted(true);
    fileReference.setUpdatedBy(user);
    fileReference.setUpdatedAt(dateHelper.getDate());
    fileReferenceDAO.createOrUpdate(fileReference);
    return true;
  }

  public NamedInputStream getPayloadByShepardId(long fileReferenceShepardId, String oid, String username) {
    FileReference reference = fileReferenceDAO.findByShepardId(fileReferenceShepardId);
    // TODO: Handle missing container
    long containerId = reference.getFileContainer().getId();
    String mongoId = reference.getFileContainer().getMongoId();
    if (!permissionsUtil.isAllowed(containerId, AccessType.Read, username)) throw new InvalidAuthException(
      "You are not authorized to access this file"
    );
    return fileService.getPayload(mongoId, oid);
  }

  public List<ShepardFile> getFilesByShepardId(long fileReferenceShepardId) {
    FileReference reference = fileReferenceDAO.findByShepardId(fileReferenceShepardId);
    return reference.getFiles();
  }

  public List<NamedInputStream> getAllPayloadsByShepardId(long fileReferenceShepardId, String username) {
    FileReference reference = fileReferenceDAO.findByShepardId(fileReferenceShepardId);
    if (
      reference.getFileContainer() == null || reference.getFileContainer().isDeleted()
    ) return Collections.emptyList();

    if (
      !permissionsUtil.isAllowed(reference.getFileContainer().getId(), AccessType.Read, username)
    ) throw new InvalidAuthException();

    var files = reference.getFiles();
    var result = new ArrayList<NamedInputStream>(files.size());
    for (var file : files) {
      var nis = fileService.getPayload(reference.getFileContainer().getMongoId(), file.getOid());
      if (nis != null) result.add(nis);
    }
    return result;
  }
}
