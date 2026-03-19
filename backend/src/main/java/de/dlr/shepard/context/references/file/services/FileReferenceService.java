package de.dlr.shepard.context.references.file.services;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.exceptions.InvalidRequestException;
import de.dlr.shepard.common.mongoDB.NamedInputStream;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.references.IReferenceService;
import de.dlr.shepard.context.references.file.daos.FileReferenceDAO;
import de.dlr.shepard.context.references.file.entities.FileReference;
import de.dlr.shepard.context.references.file.io.FileReferenceIO;
import de.dlr.shepard.context.version.services.VersionService;
import de.dlr.shepard.data.file.daos.ShepardFileDAO;
import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.file.entities.ShepardFile;
import de.dlr.shepard.data.file.services.FileContainerService;
import de.dlr.shepard.data.file.services.FileService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RequestScoped
public class FileReferenceService implements IReferenceService<FileReference, FileReferenceIO> {

  @Inject
  FileReferenceDAO fileReferenceDAO;

  @Inject
  DataObjectService dataObjectService;

  @Inject
  FileContainerService fileContainerService;

  @Inject
  ShepardFileDAO fileDAO;

  @Inject
  VersionService versionService;

  @Inject
  DateHelper dateHelper;

  @Inject
  FileService fileService;

  @Inject
  PermissionsService permissionsService;

  @Inject
  CollectionService collectionService;

  @Inject
  AuthenticationContext authenticationContext;

  @Inject
  UserService userService;

  /**
   * Gets FileReference list for a given dataobject.
   *
   * @param collectionShepardId
   * @param dataObjectShepardId
   * @param versionUID the version UUID
   * @return List<FileReference>
   * @throws InvalidPathException If collection or dataobject cannot be found, or no association between dataobject and collection exists
   * @throws InvalidAuthException If user has no read permissions on collection or dataobject specified by request path
   */
  @Override
  public List<FileReference> getAllReferencesByDataObjectId(
    long collectionShepardId,
    long dataObjectShepardId,
    UUID versionUID
  ) {
    dataObjectService.getDataObject(collectionShepardId, dataObjectShepardId, versionUID);

    List<FileReference> references = fileReferenceDAO.findByDataObjectShepardId(dataObjectShepardId);
    return references;
  }

  /**
   * Gets FileReference by shepard id.
   *
   * @param collectionShepardId
   * @param dataObjectShepardId
   * @param shepardId
   * @param versionUID the version UUID
   * @return FileReference
   * @throws InvalidPathException If reference with Id does not exist or is deleted, or if collection or dataObject Id of path is not valid
   * @throws InvalidAuthException If user has no read permissions on collection or dataobject specified by request path
   */
  @Override
  public FileReference getReference(
    long collectionShepardId,
    long dataObjectShepardId,
    long shepardId,
    UUID versionUID
  ) {
    dataObjectService.getDataObject(collectionShepardId, dataObjectShepardId, versionUID);

    FileReference fileReference = fileReferenceDAO.findByShepardId(shepardId, versionUID);
    if (fileReference == null || fileReference.isDeleted()) {
      String errorMsg = "ID ERROR - File Reference with id %s is null or deleted".formatted(shepardId);
      Log.error(errorMsg);
      throw new InvalidPathException(errorMsg);
    }

    if (
      fileReference.getDataObject() == null || !fileReference.getDataObject().getShepardId().equals(dataObjectShepardId)
    ) {
      String errorMsg = "ID ERROR - There is no association between dataObject and reference";
      Log.error(errorMsg);
      throw new InvalidPathException(errorMsg);
    }

    return fileReference;
  }

  /**
   * Creates a new FileReference
   *
   * @param collectionShepardId
   * @param dataObjectShepardId DataObject id for the reference to be created
   * @param fileReference Reference object
   * @return FileReference
   * @throws InvalidPathException if collection or dataobject specified by their Ids are null or deleted
   * @throws InvalidAuthException if user has no permission to edit referencing collection or no read permissions on referenced container
   */
  @Override
  public FileReference createReference(
    long collectionShepardId,
    long dataObjectShepardId,
    FileReferenceIO fileReference
  ) {
    var dataObject = dataObjectService.getDataObject(collectionShepardId, dataObjectShepardId);
    collectionService.assertIsAllowedToEditCollection(collectionShepardId);

    User user = userService.getCurrentUser();

    FileContainer container;
    try {
      container = fileContainerService.getContainer(fileReference.getFileContainerId());
    } catch (InvalidPathException | InvalidAuthException ex) {
      Log.error(ex.getMessage());
      throw new InvalidBodyException(ex.getMessage());
    }

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

  /**
   * Deletes the file reference.
   *
   * @param collectionShepardId
   * @param dataObjectShepardId
   * @param fileReferenceShepardId
   * @throws InvalidPathException if collection or dataobject specified by their Ids are null or deleted
   * @throws InvalidAuthException if user has no permissions to edit the collection, which the reference is assigned to
   */
  @Override
  public void deleteReference(long collectionShepardId, long dataObjectShepardId, long fileReferenceShepardId) {
    FileReference fileReference = getReference(collectionShepardId, dataObjectShepardId, fileReferenceShepardId, null);
    collectionService.assertIsAllowedToEditCollection(collectionShepardId);

    User user = userService.getCurrentUser();
    fileReference.setDeleted(true);
    fileReference.setUpdatedBy(user);
    fileReference.setUpdatedAt(dateHelper.getDate());
    fileReferenceDAO.createOrUpdate(fileReference);
  }

  /**
   * Returns list of ShepardFile.
   *
   * @param collectionShepardId
   * @param dataObjectShepardId
   * @param fileReferenceShepardId identifies the file reference
   * @param versionUID
   * @return list of shepard files
   * @throws InvalidPathException if collection, dataobject or reference specified by their Ids are null or deleted
   */
  public List<ShepardFile> getFiles(
    long collectionShepardId,
    long dataObjectShepardId,
    long fileReferenceShepardId,
    UUID versionUID
  ) {
    FileReference reference = getReference(
      collectionShepardId,
      dataObjectShepardId,
      fileReferenceShepardId,
      versionUID
    );

    if (reference.getFileContainer() == null || reference.getFileContainer().isDeleted()) {
      String errorMsg =
        "Referenced FileContainer is not set or deleted in FileReference with id %s".formatted(reference.getId());
      Log.error(errorMsg);
      throw new NotFoundException(errorMsg);
    }

    try {
      fileContainerService.getContainer(reference.getFileContainer().getId());
    } catch (InvalidPathException ex) {
      Log.error(ex.getMessage());
      throw new NotFoundException(ex.getMessage());
    }

    return reference.getFiles();
  }

  /**
   * Returns a NamedInputStream of the specified file
   *
   * @param fileReferenceShepardId identifies the file reference
   * @param oid identifies the actual file
   * @param username the current user
   * @return NamedInputStream
   * @throws InvalidPathException when FileReference cannot be found due to invalid collection, dataobject or reference Ids
   * @throws InvalidAuthException when the user is not authorized to access the container
   * @throws NotFoundException when mongoDb is not able to find document container or file by mongoId or oid, or when Referenced file container is not accessible
   * @throws InvalidRequestException when FileContainer is not accessible
   */
  public NamedInputStream getPayload(
    long collectionShepardId,
    long dataObjectShepardId,
    long fileReferenceShepardId,
    String oid,
    UUID versionUID
  ) {
    FileReference reference = getReference(
      collectionShepardId,
      dataObjectShepardId,
      fileReferenceShepardId,
      versionUID
    );

    if (reference.getFileContainer() == null || reference.getFileContainer().isDeleted()) {
      String errorMsg =
        "FileContainer with id %s is not set or deleted in FileReference".formatted(reference.getFileContainer());
      Log.error(errorMsg);
      throw new NotFoundException(errorMsg);
    }

    try {
      // check that FileContainer is actually accessible and user has permissions to read from it
      fileContainerService.getContainer(reference.getFileContainer().getId());
    } catch (InvalidPathException e) {
      Log.error(e.getMessage());
      throw new NotFoundException(e.getMessage());
    }

    String mongoId = reference.getFileContainer().getMongoId();
    return fileService.getPayload(mongoId, oid);
  }

  /**
   * Returns a list of NamedInputStreams of all files in that reference
   *
   * Returns empty input streams if referenced file container is not accessible.
   *
   * @param collectionShepardId
   * @param dataObjectShepardId
   * @param fileReferenceShepardId identifies the file reference
   * @return list of NamedInputStreams
   * @throws InvalidPathException when FileReference cannot be found due to invalid collection, dataobject or reference Ids
   * @throws NotFoundException when container is not accessible
   * @throws InvalidAuthException when the user is not authorized to access the container
   */
  public List<NamedInputStream> getAllPayloads(
    long collectionShepardId,
    long dataObjectShepardId,
    long fileReferenceShepardId
  ) {
    FileReference reference = getReference(collectionShepardId, dataObjectShepardId, fileReferenceShepardId, null);

    if (reference.getFileContainer() == null || reference.getFileContainer().isDeleted()) {
      String errorMsg =
        "Referenced FileContainer is not set or deleted in FileReference with id %s".formatted(reference.getId());
      Log.error(errorMsg);
      throw new NotFoundException(errorMsg);
    }

    try {
      // check that referenced container is actually accessible
      fileContainerService.getContainer(reference.getFileContainer().getId());
    } catch (InvalidPathException ex) {
      throw new NotFoundException(ex.getMessage());
    }

    List<ShepardFile> files = reference.getFiles();

    var result = new ArrayList<NamedInputStream>(files.size());
    for (var file : files) {
      NamedInputStream nis;
      try {
        nis = fileService.getPayload(reference.getFileContainer().getMongoId(), file.getOid());
        result.add(nis);
      } catch (NotFoundException e) {
        result.add(new NamedInputStream(file.getOid(), null, file.getFilename(), 0L));
      }
    }
    return result;
  }
}
