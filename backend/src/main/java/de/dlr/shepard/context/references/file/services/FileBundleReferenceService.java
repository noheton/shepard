package de.dlr.shepard.context.references.file.services;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.exceptions.InvalidRequestException;
import de.dlr.shepard.common.identifier.AppIdGenerator;
import de.dlr.shepard.common.mongoDB.NamedInputStream;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.references.IReferenceService;
import de.dlr.shepard.context.references.file.daos.FileBundleReferenceDAO;
import de.dlr.shepard.context.references.file.entities.FileBundleReference;
import de.dlr.shepard.context.references.file.entities.FileGroup;
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

/**
 * Service for {@link FileBundleReference} (FR1a, formerly known as
 * {@code FileReferenceService} per {@code aidocs/53 §1.7}).
 *
 * <p>Behaviourally backward-compatible with the upstream
 * {@code FileReferenceService}: every method that the legacy
 * {@code /shepard/api/.../fileReferences/...} REST surface calls
 * keeps the same signature, parameters, and return shape.
 *
 * <p>FR1a-new: {@link #createReference} additionally creates a
 * default {@link FileGroup} ({@code name = "default"},
 * {@code index = 0}) under each new bundle and re-parents the bundle's
 * initial files under it. The bundle's own {@code HAS_PAYLOAD} edges
 * are kept as a compatibility shadow for the upstream-API read path.
 */
@RequestScoped
public class FileBundleReferenceService implements IReferenceService<FileBundleReference, FileReferenceIO> {

  @Inject
  FileBundleReferenceDAO fileBundleReferenceDAO;

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
   * Gets FileBundleReference list for a given dataobject.
   *
   * @param collectionShepardId
   * @param dataObjectShepardId
   * @param versionUID the version UUID
   * @return List<FileBundleReference>
   * @throws InvalidPathException If collection or dataobject cannot be found, or no association between dataobject and collection exists
   * @throws InvalidAuthException If user has no read permissions on collection or dataobject specified by request path
   */
  @Override
  public List<FileBundleReference> getAllReferencesByDataObjectId(
    long collectionShepardId,
    long dataObjectShepardId,
    UUID versionUID
  ) {
    dataObjectService.getDataObject(collectionShepardId, dataObjectShepardId, versionUID);

    List<FileBundleReference> references = fileBundleReferenceDAO.findByDataObjectShepardId(dataObjectShepardId);
    return references;
  }

  /**
   * Gets FileBundleReference by shepard id.
   *
   * @param collectionShepardId
   * @param dataObjectShepardId
   * @param shepardId
   * @param versionUID the version UUID
   * @return FileBundleReference
   * @throws InvalidPathException If reference with Id does not exist or is deleted, or if collection or dataObject Id of path is not valid
   * @throws InvalidAuthException If user has no read permissions on collection or dataobject specified by request path
   */
  @Override
  public FileBundleReference getReference(
    long collectionShepardId,
    long dataObjectShepardId,
    long shepardId,
    UUID versionUID
  ) {
    dataObjectService.getDataObject(collectionShepardId, dataObjectShepardId, versionUID);

    FileBundleReference fileReference = fileBundleReferenceDAO.findByShepardId(shepardId, versionUID);
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
   * Creates a new FileBundleReference.
   *
   * <p>FR1a behaviour: every freshly-created bundle gets a default
   * {@link FileGroup} ({@code name = "default"}, {@code index = 0})
   * and the bundle's initial files are re-parented under that group
   * (they remain attached to the bundle directly as a compatibility
   * shadow for the upstream API). The default-group behaviour is
   * additive — the upstream wire shape (flat {@code files} array)
   * is unchanged.
   *
   * @param collectionShepardId
   * @param dataObjectShepardId DataObject id for the reference to be created
   * @param fileReference Reference object
   * @return FileBundleReference
   * @throws InvalidPathException if collection or dataobject specified by their Ids are null or deleted
   * @throws InvalidAuthException if user has no permission to edit referencing collection or no read permissions on referenced container
   */
  @Override
  public FileBundleReference createReference(
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

    var toCreate = new FileBundleReference();
    toCreate.setCreatedAt(dateHelper.getDate());
    toCreate.setCreatedBy(user);
    toCreate.setDataObject(dataObject);
    toCreate.setName(fileReference.getName());
    toCreate.setFileContainer(container);

    // Get existing file
    var initialFiles = new ArrayList<ShepardFile>();
    for (var oid : fileReference.getFileOids()) {
      var file = fileDAO.find(container.getId(), oid);
      if (file != null) {
        toCreate.addFile(file);
        initialFiles.add(file);
      } else {
        Log.warnf("Could not find file with oid: %s", oid);
      }
    }

    // FR1a — attach a default FileGroup so every new bundle has at
    // least one navigable group from the moment it's created. The
    // bundle->file edges added above stay in place as the upstream
    // compatibility shadow.
    var defaultGroup = new FileGroup();
    defaultGroup.setAppId(AppIdGenerator.next());
    defaultGroup.setName("default");
    defaultGroup.setIndex(0);
    defaultGroup.setCreatedAt(dateHelper.getDate());
    defaultGroup.setCreatedBy(user);
    for (var f : initialFiles) {
      defaultGroup.addFile(f);
    }
    toCreate.addGroup(defaultGroup);

    var created = fileBundleReferenceDAO.createOrUpdate(toCreate);
    created.setShepardId(created.getId());
    created = fileBundleReferenceDAO.createOrUpdate(created);
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
    FileBundleReference fileReference = getReference(collectionShepardId, dataObjectShepardId, fileReferenceShepardId, null);
    collectionService.assertIsAllowedToEditCollection(collectionShepardId);

    User user = userService.getCurrentUser();
    fileReference.setDeleted(true);
    fileReference.setUpdatedBy(user);
    fileReference.setUpdatedAt(dateHelper.getDate());
    fileBundleReferenceDAO.createOrUpdate(fileReference);
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
    FileBundleReference reference = getReference(
      collectionShepardId,
      dataObjectShepardId,
      fileReferenceShepardId,
      versionUID
    );

    if (reference.getFileContainer() == null || reference.getFileContainer().isDeleted()) {
      String errorMsg =
        "Referenced FileContainer is not set or deleted in FileBundleReference with id %s".formatted(reference.getId());
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
   * @throws InvalidPathException when FileBundleReference cannot be found due to invalid collection, dataobject or reference Ids
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
    FileBundleReference reference = getReference(
      collectionShepardId,
      dataObjectShepardId,
      fileReferenceShepardId,
      versionUID
    );

    if (reference.getFileContainer() == null || reference.getFileContainer().isDeleted()) {
      String errorMsg =
        "FileContainer with id %s is not set or deleted in FileBundleReference".formatted(reference.getFileContainer());
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
   * @throws InvalidPathException when FileBundleReference cannot be found due to invalid collection, dataobject or reference Ids
   * @throws NotFoundException when container is not accessible
   * @throws InvalidAuthException when the user is not authorized to access the container
   */
  public List<NamedInputStream> getAllPayloads(
    long collectionShepardId,
    long dataObjectShepardId,
    long fileReferenceShepardId
  ) {
    FileBundleReference reference = getReference(collectionShepardId, dataObjectShepardId, fileReferenceShepardId, null);

    if (reference.getFileContainer() == null || reference.getFileContainer().isDeleted()) {
      String errorMsg =
        "Referenced FileContainer is not set or deleted in FileBundleReference with id %s".formatted(reference.getId());
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
