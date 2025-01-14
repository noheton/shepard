package de.dlr.shepard.context.references.file.services;

import de.dlr.shepard.auth.security.PermissionsUtil;
import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.exceptions.InvalidRequestException;
import de.dlr.shepard.common.mongoDB.NamedInputStream;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.references.IReferenceService;
import de.dlr.shepard.context.references.file.daos.FileReferenceDAO;
import de.dlr.shepard.context.references.file.entities.FileReference;
import de.dlr.shepard.context.references.file.io.FileReferenceIO;
import de.dlr.shepard.context.version.services.VersionService;
import de.dlr.shepard.data.file.daos.FileContainerDAO;
import de.dlr.shepard.data.file.daos.ShepardFileDAO;
import de.dlr.shepard.data.file.entities.ShepardFile;
import de.dlr.shepard.data.file.services.FileService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RequestScoped
public class FileReferenceService implements IReferenceService<FileReference, FileReferenceIO> {

  private FileReferenceDAO fileReferenceDAO;
  private DataObjectDAO dataObjectDAO;
  private FileContainerDAO containerDAO;
  private ShepardFileDAO fileDAO;
  private UserDAO userDAO;
  private VersionService versionService;
  private DateHelper dateHelper;
  private FileService fileService;
  private PermissionsUtil permissionsUtil;

  FileReferenceService() {}

  @Inject
  public FileReferenceService(
    FileReferenceDAO fileReferenceDAO,
    DataObjectDAO dataObjectDAO,
    FileContainerDAO containerDAO,
    ShepardFileDAO fileDAO,
    UserDAO userDAO,
    VersionService versionService,
    DateHelper dateHelper,
    FileService fileService,
    PermissionsUtil permissionsUtil
  ) {
    this.fileReferenceDAO = fileReferenceDAO;
    this.dataObjectDAO = dataObjectDAO;
    this.containerDAO = containerDAO;
    this.versionService = versionService;
    this.fileDAO = fileDAO;
    this.userDAO = userDAO;
    this.dateHelper = dateHelper;
    this.fileService = fileService;
    this.permissionsUtil = permissionsUtil;
  }

  @Override
  public List<FileReference> getAllReferencesByDataObjectShepardId(long dataObjectShepardId, UUID versionUID) {
    var references = fileReferenceDAO.findByDataObjectShepardId(dataObjectShepardId);
    return references;
  }

  @Override
  public FileReference getReferenceByShepardId(long shepardId, UUID versionUID) {
    FileReference fileReference = fileReferenceDAO.findByShepardId(shepardId, versionUID);
    if (fileReference == null || fileReference.isDeleted()) {
      Log.errorf("File Reference with id %s is null or deleted", shepardId);
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
        Log.warnf("Could not find file with oid: %s", oid);
      }
    }

    var created = fileReferenceDAO.createOrUpdate(toCreate);
    created.setShepardId(created.getId());
    created = fileReferenceDAO.createOrUpdate(created);
    versionService.attachToVersionOfVersionableEntityAndReturnVersion(dataObject.getId(), created.getId());
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

  /**
   * Returns list of ShepardFile. This works even when the container in question is not accessible.
   *
   * @param fileReferenceShepardId identifies the file reference
   * @return list of shepard files
   */
  public List<ShepardFile> getFilesByShepardId(long fileReferenceShepardId, UUID versionUID) {
    FileReference reference = fileReferenceDAO.findByShepardId(fileReferenceShepardId, versionUID);
    return reference.getFiles();
  }

  /**
   * Returns a NamedInputStream of the specified file
   *
   * @param fileReferenceShepardId identifies the file reference
   * @param oid identifies the actual file
   * @param username the current user
   * @return NamedInputStream
   * @throws InvalidRequestException when container is not accessible
   * @throws InvalidAuthException when the user is not authorized to access the container
   */
  public NamedInputStream getPayloadByShepardId(
    long fileReferenceShepardId,
    String oid,
    String username,
    UUID versionUID
  ) {
    FileReference reference = fileReferenceDAO.findByShepardId(fileReferenceShepardId, versionUID);
    if (
      reference.getFileContainer() == null || reference.getFileContainer().isDeleted()
    ) throw new InvalidRequestException("The file container in question is not accessible");

    long containerId = reference.getFileContainer().getId();
    if (
      !permissionsUtil.isAccessTypeAllowedForUser(containerId, AccessType.Read, username)
    ) throw new InvalidAuthException("You are not authorized to access this file");

    String mongoId = reference.getFileContainer().getMongoId();
    return fileService.getPayload(mongoId, oid);
  }

  /**
   * Returns a list of NamedInputStreams of all files in that reference
   *
   * @param fileReferenceShepardId identifies the file reference
   * @param username the current user
   * @return list of NamedInputStreams
   * @throws InvalidRequestException when container is not accessible
   * @throws InvalidAuthException when the user is not authorized to access the container
   */
  public List<NamedInputStream> getAllPayloadsByShepardId(long fileReferenceShepardId, String username) {
    FileReference reference = fileReferenceDAO.findByShepardId(fileReferenceShepardId);
    var files = reference.getFiles();

    // Return empty named input streams when the container is not accessible
    if (
      reference.getFileContainer() == null || reference.getFileContainer().isDeleted()
    ) throw new InvalidRequestException("The file container in question is not accessible");

    // Throw exception when not isAllowed
    var containerId = reference.getFileContainer().getId();
    if (
      !permissionsUtil.isAccessTypeAllowedForUser(containerId, AccessType.Read, username)
    ) throw new InvalidAuthException("You are not authorized to access this file container");

    var result = new ArrayList<NamedInputStream>(files.size());
    for (var file : files) {
      var nis = fileService.getPayload(reference.getFileContainer().getMongoId(), file.getOid());
      if (nis != null) result.add(nis);
      else result.add(new NamedInputStream(file.getOid(), null, file.getFilename(), 0L));
    }
    return result;
  }
}
