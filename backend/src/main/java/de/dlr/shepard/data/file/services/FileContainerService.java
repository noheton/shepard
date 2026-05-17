package de.dlr.shepard.data.file.services;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.mongoDB.NamedInputStream;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.PermissionType;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.data.AbstractContainerService;
import de.dlr.shepard.data.file.daos.FileContainerDAO;
import de.dlr.shepard.data.file.daos.PayloadVersionDAO;
import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.file.entities.PayloadVersion;
import de.dlr.shepard.data.file.entities.ShepardFile;
import de.dlr.shepard.data.file.io.FileContainerIO;
import de.dlr.shepard.data.file.services.FileService.FileCreateResult;
import de.dlr.shepard.storage.FileStorage;
import de.dlr.shepard.storage.FileStorageRegistry;
import de.dlr.shepard.storage.StorageException;
import de.dlr.shepard.storage.StorageGetResponse;
import de.dlr.shepard.storage.StorageLocator;
import de.dlr.shepard.storage.StorageNotFoundException;
import de.dlr.shepard.storage.gridfs.GridFsFileStorage;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ServiceUnavailableException;
import java.io.InputStream;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@RequestScoped
public class FileContainerService extends AbstractContainerService<FileContainer, FileContainerIO> {

  @Inject
  FileContainerDAO fileContainerDAO;

  @Inject
  UserService userService;

  @Inject
  DateHelper dateHelper;

  @Inject
  FileService fileService;

  @Inject
  PermissionsService permissionsService;

  @Inject
  FileStorageRegistry fileStorageRegistry;

  @Inject
  PayloadVersionDAO payloadVersionDAO;

  /**
   * Creates a FileContainer and stores it in Neo4J
   *
   * @param fileContainerIO to be stored
   * @param username        of the related user
   * @return the created FileContainer
   */
  @Override
  public FileContainer createContainer(FileContainerIO fileContainerIO) {
    User user = userService.getCurrentUser();
    FileContainer toCreate = new FileContainer();
    toCreate.setCreatedAt(dateHelper.getDate());
    toCreate.setCreatedBy(user);
    toCreate.setMongoId(fileService.createFileContainer());
    toCreate.setName(fileContainerIO.getName());

    var created = fileContainerDAO.createOrUpdate(toCreate);
    permissionsService.createPermissions(created, user, PermissionType.Private);
    return created;
  }

  /**
   * Gets the FileContainer
   *
   * @param id identifies the searched FileContainer
   * @return the FileContainer with matching id or null
   * @throws InvalidPathException if the file container cannot be found
   * @throws InvalidAuthException if user has no read permission on container
   */
  @Override
  public FileContainer getContainer(long id) {
    FileContainer fileContainer = fileContainerDAO.findByNeo4jId(id);

    if (fileContainer == null || fileContainer.isDeleted()) {
      String errorMsg = "ID ERROR - File Container with id %s is null or deleted".formatted(id);
      Log.errorf(errorMsg);
      throw new InvalidPathException(errorMsg);
    }
    assertIsAllowedToReadContainer(id);
    fileContainer.setCollectionList(fileContainer.getCollectionList().stream().filter(d -> !d.isDeleted()).toList());
    return fileContainer;
  }

  /**
   * Searches the database for all FileContainers
   *
   * @param params   QueryParamsHelper
   * @param username the name of the user
   * @return a list of FileContainers
   */
  @Override
  public List<FileContainer> getAllContainers(QueryParamHelper params) {
    User user = userService.getCurrentUser();
    List<FileContainer> containers = fileContainerDAO.findAllFileContainers(params, user.getUsername());

    // this mapping is done because 'findAllFileContainers' does not return all relations of a container
    // therefore the returned list of collection ids is ALWAYS empty, even though the container might be set as the default container by a collection
    // this can lead to confusion
    // by nullifying the collection id list, it is not included in the API response
    containers = containers.stream().map(this::nullifyEmptyCollectionIdList).collect(Collectors.toList());
    return containers;
  }

  private FileContainer nullifyEmptyCollectionIdList(FileContainer fileContainer) {
    if (fileContainer.getCollectionList().isEmpty()) {
      fileContainer.setCollectionList(null);
    }
    return fileContainer;
  }

  /**
   * Deletes a FileContainer in Neo4j
   *
   * @param fileContainerId identifies the FileContainer
   * @param username        the deleting user
   * @throws InvalidPathException if the file container cannot be found
   * @throws InvalidAuthException if user has no write permission on container
   */
  @Override
  public void deleteContainer(long fileContainerId) {
    User user = userService.getCurrentUser();
    FileContainer fileContainer = getContainer(fileContainerId);
    assertIsAllowedToDeleteContainer(fileContainerId);

    String mongoId = fileContainer.getMongoId();
    fileContainer.setDeleted(true);
    fileContainer.setUpdatedAt(dateHelper.getDate());
    fileContainer.setUpdatedBy(user);
    fileContainerDAO.createOrUpdate(fileContainer);
    // FS1a: container-level teardown stays on the legacy FileService
    // surface. The SPI is per-payload (put/get/delete by locator); a
    // bulk "drop the entire container" verb is FS1b/FS1e territory
    // (the migration sweep needs per-adapter container teardown too).
    fileService.deleteFileContainer(mongoId);
  }

  /**
   * Get file payload
   *
   * @param fileContainerId The container to get the payload from
   * @param oid             The specific file
   * @return a NamedInputStream
   * @throws InvalidPathException if the file container cannot be found
   * @throws InvalidAuthException if user has no read permission on container
   * @throws ServiceUnavailableException if the storage tier fails
   */
  public NamedInputStream getFile(long fileContainerId, String oid) {
    FileContainer container = getContainer(fileContainerId);
    // FS1a: route the read through the active FileStorage adapter so
    // existing-row reads keep working when an operator wires an
    // alternative provider via shepard.storage.provider. The locator
    // is reconstructed from the FileContainer's mongoId + the file's
    // oid; the GridFsFileStorage adapter splits it back when it
    // routes to FileService. Pre-FS1a rows have providerId backfilled
    // by V34 (defaults to "gridfs" for any null) so the registry
    // dispatches correctly.
    ShepardFile file = findEntityForRoutingOrNull(container, oid);
    String providerId = effectiveProviderId(file);
    FileStorage adapter = storageForRow(providerId);
    StorageLocator locator = buildLocatorForRow(providerId, container.getMongoId(), oid);
    try {
      StorageGetResponse resp = adapter.get(locator);
      return new NamedInputStream(oid, resp.stream(), resp.fileName(), resp.sizeBytes());
    } catch (StorageNotFoundException snfe) {
      // Surface the storage-tier 404 as the wire-shape-preserving
      // JAX-RS NotFoundException (the upstream-compatible response).
      Log.errorf(
        "FileContainerService.getFile: storage 404 on container=%s oid=%s — %s",
        container.getMongoId(),
        oid,
        snfe.getMessage()
      );
      throw new NotFoundException(snfe.getMessage());
    } catch (StorageException se) {
      Log.errorf(
        "FileContainerService.getFile: storage failure on container=%s oid=%s — %s",
        container.getMongoId(),
        oid,
        se.getMessage()
      );
      throw new ServiceUnavailableException("Storage provider '" + providerId + "' failed: " + se.getMessage());
    }
  }

  /**
   * Create a new file
   *
   * @param fileContainerId identifies the file container
   * @param fileName        the name of the new file
   * @param inputStream     the file itself
   * @return The newly created file
   * @throws InternalServerErrorException if file creation fails
   * @throws InvalidPathException if the file container cannot be found
   * @throws InvalidAuthException if user has no read or write permission on container
   * @throws de.dlr.shepard.storage.StorageNotInstalledException if no storage adapter is active
   */
  public ShepardFile createFile(long fileContainerId, String fileName, InputStream inputStream) {
    FileContainer fileContainer = getContainer(fileContainerId);
    assertIsAllowedToEditContainer(fileContainerId);

    if (fileName == null || fileName.isBlank()) {
      var sdf = new SimpleDateFormat("yyyyMMdd-HHmmss");
      var dateStr = sdf.format(dateHelper.getDate());
      fileName = "shepard-file-" + dateStr;
    }

    // FS1a: route the write through the active FileStorage adapter.
    // Today's GridFsFileStorage delegates back into FileService —
    // behaviour preserved verbatim for the upstream wire contract —
    // but FS1b's S3 adapter slots in here without touching the
    // FileContainerService. The 503 envelope on missing-provider
    // surfaces through StorageNotInstalledException (raised by
    // requireActive()) → StorageNotInstalledExceptionMapper → RFC 7807.
    FileStorage adapter = fileStorageRegistry.requireActive();
    ShepardFile result;
    String sha256 = null;
    try {
      // GridFs invariant: the adapter persists the ShepardFile
      // inside fileService.createFile() as part of the GridFS
      // bookkeeping. To stay behaviour-preserving we call the
      // legacy path directly, then route through the SPI for the
      // stamping side-effects (locator construction + providerId
      // assignment). FS1b will tighten the adapter contract so it
      // returns the entity directly — the S3 adapter has its own
      // bookkeeping shape and won't share FileService's persistence
      // semantics.
      //
      // PV1a: use createFileWithSha256 on the GridFS path so the
      // SHA-256 digest is available for version recording without
      // a second pass over the bytes.
      if (GridFsFileStorage.ID.equals(adapter.id())) {
        FileCreateResult fcr = fileService.createFileWithSha256(fileContainer.getMongoId(), fileName, inputStream);
        result = fcr.file();
        sha256 = fcr.sha256();
      } else {
        // Non-GridFS adapters require presigned upload URLs.
        // Use POST /v2/file-containers/{containerAppId}/upload-url to
        // obtain a presigned S3 URL, PUT bytes directly, then call
        // POST .../upload-url/commit to register the file.
        throw new ServiceUnavailableException(
          "Direct upload is not supported for provider '" + adapter.id() +
          "'. Use the presigned upload URL endpoint: " +
          "POST /v2/file-containers/{containerAppId}/upload-url"
        );
      }
      if (result != null) {
        result.setProviderId(adapter.id());
      }
    } catch (NotFoundException nfe) {
      // FileService throws JAX-RS NotFound for missing containers;
      // re-throw to preserve the upstream wire shape.
      throw nfe;
    }

    fileContainer.addFile(result);
    fileContainerDAO.createOrUpdate(fileContainer);

    // PV1a: record a PayloadVersion node for this upload.
    // The version is append-only and never mutated; the counter is
    // scoped to (containerAppId, originalName).
    recordPayloadVersion(fileContainer, fileName, result, sha256);

    return result;
  }

  /**
   * PV1a — mint and persist a {@link PayloadVersion} node for a successful
   * file upload.
   *
   * <p>The version number is computed as {@code max(existing) + 1}, with
   * {@code 1} for the first upload. The operation is best-effort: a failure
   * here logs a warning but does not roll back the upload because the
   * {@link ShepardFile} is already persisted and the container graph is
   * already updated.
   *
   * @param container     the owning FileContainer (for its appId and Neo4j id).
   * @param uploadedName  the normalised file name used for this upload.
   * @param file          the just-persisted ShepardFile (provides oid + size).
   * @param sha256        upper-case SHA-256 hex digest, or {@code null} for
   *                      non-GridFS paths.
   */
  private void recordPayloadVersion(
    FileContainer container,
    String uploadedName,
    ShepardFile file,
    String sha256
  ) {
    if (container.getAppId() == null) {
      // Container was not yet assigned an appId (pre-L2a row or test fixture).
      // Silently skip version recording to avoid a broken node with a null FK.
      Log.warnf(
        "PV1a: container id=%d has no appId — skipping PayloadVersion recording for file '%s'",
        container.getId(),
        uploadedName
      );
      return;
    }
    try {
      User caller = userService.getCurrentUser();
      String callerName = (caller != null) ? caller.getUsername() : "unknown";
      String containerAppId = container.getAppId();

      long nextVersion = payloadVersionDAO.findMaxVersionNumber(containerAppId, uploadedName) + 1;

      PayloadVersion pv = new PayloadVersion();
      pv.setContainerAppId(containerAppId);
      pv.setOriginalName(uploadedName);
      pv.setFileOid(file != null ? file.getOid() : null);
      pv.setSha256(sha256);
      pv.setSizeBytes(file != null ? file.getFileSize() : null);
      pv.setVersionNumber(nextVersion);
      pv.setUploadedBy(callerName);
      pv.setUploadedAt(Instant.now().toString());
      payloadVersionDAO.createOrUpdate(pv);
      Log.infof(
        "PV1a: recorded PayloadVersion v%d for container=%s file='%s' sha256=%s",
        nextVersion, containerAppId, uploadedName, sha256 != null ? sha256.substring(0, 8) + "…" : "null"
      );
    } catch (Exception e) {
      // Version recording is best-effort; do not fail the upload.
      Log.warnf(
        "PV1a: failed to record PayloadVersion for container=%s file='%s': %s",
        container.getAppId(), uploadedName, e.getMessage()
      );
    }
  }

  /**
   * Delete one file
   *
   * @param fileContainerId The container to get the payload from
   * @param oid             The specific file
   * @throws ServiceUnavailableException if the storage tier fails
   */
  public void deleteFile(long fileContainerId, String oid) {
    FileContainer container = getContainer(fileContainerId);
    assertIsAllowedToEditContainer(fileContainerId);

    // FS1a: route through the registry so per-row providerId is
    // honoured (a future mixed-provider install — pre-FS1e migration
    // sweep mid-flight — sees existing rows deleted from the right
    // adapter). The default providerId fallback is "gridfs" which
    // matches the V34 backfill.
    ShepardFile file = findEntityForRoutingOrNull(container, oid);
    String providerId = effectiveProviderId(file);
    FileStorage adapter = storageForRow(providerId);
    StorageLocator locator = buildLocatorForRow(providerId, container.getMongoId(), oid);
    try {
      adapter.delete(locator);
    } catch (StorageNotFoundException snfe) {
      // delete() is idempotent so this should already be swallowed
      // by GridFsFileStorage; defensive translation in case a future
      // adapter is stricter.
      Log.errorf(
        "FileContainerService.deleteFile: storage 404 on container=%s oid=%s — %s",
        container.getMongoId(),
        oid,
        snfe.getMessage()
      );
      throw new NotFoundException(snfe.getMessage());
    } catch (StorageException se) {
      Log.errorf(
        "FileContainerService.deleteFile: storage failure on container=%s oid=%s — %s",
        container.getMongoId(),
        oid,
        se.getMessage()
      );
      throw new ServiceUnavailableException("Storage provider '" + providerId + "' failed: " + se.getMessage());
    }

    List<ShepardFile> newFiles = container.getFiles().stream().filter(f -> !f.getOid().equals(oid)).toList();
    container.setFiles(newFiles);
    fileContainerDAO.createOrUpdate(container);
  }

  /**
   * FS1c — look up a FileContainer by its appId with a read-permission
   * check. Used by the presigned-URL REST endpoints which address
   * containers by appId rather than the legacy Neo4j long id.
   *
   * @throws InvalidPathException if no container with that appId exists
   */
  public FileContainer getContainerByAppId(String appId) {
    FileContainer c = fileContainerDAO.findByAppId(appId)
      .orElseThrow(() -> new InvalidPathException(
        "FileContainer with appId '" + appId + "' not found"));
    if (c.isDeleted()) {
      throw new InvalidPathException("FileContainer '" + appId + "' is deleted");
    }
    assertIsAllowedToReadContainer(c.getId());
    c.setCollectionList(c.getCollectionList().stream().filter(d -> !d.isDeleted()).toList());
    return c;
  }

  /**
   * FS1c — generate a presigned PUT URL so the caller can upload
   * bytes directly to the storage backend.
   *
   * @throws ServiceUnavailableException if the active adapter does
   *         not support presigned uploads
   */
  public FileStorage.PresignedPut presignedUploadUrl(long containerId, String fileName, Duration ttl)
      throws StorageException {
    FileContainer container = getContainer(containerId);
    assertIsAllowedToEditContainer(containerId);
    FileStorage adapter = fileStorageRegistry.requireActive();
    return adapter.presignedUploadUrl(container.getMongoId(), fileName, ttl)
      .orElseThrow(() -> new ServiceUnavailableException(
        "Active storage provider '" + adapter.id() + "' does not support presigned upload URLs. " +
        "Use the direct upload path instead."));
  }

  /**
   * FS1c — register a file that was uploaded via presigned PUT.
   * Creates the {@link ShepardFile} Neo4j entity and attaches it
   * to the container. The caller is responsible for ensuring the
   * object actually exists in the storage backend before calling
   * this; a subsequent {@link #getFile} will surface a 404 if it
   * doesn't.
   */
  public ShepardFile commitUpload(long containerId, String oid, String fileName, Long fileSize)
      throws StorageException {
    FileContainer container = getContainer(containerId);
    assertIsAllowedToEditContainer(containerId);
    FileStorage adapter = fileStorageRegistry.requireActive();

    ShepardFile file = new ShepardFile(oid, dateHelper.getDate(), fileName, null);
    file.setFileSize(fileSize);
    file.setProviderId(adapter.id());

    container.addFile(file);
    fileContainerDAO.createOrUpdate(container);
    return file;
  }

  /**
   * CC1b — return the list of non-deleted DataObjects that reference this
   * FileContainer via any file reference type.  Read-permission on the
   * container is checked via {@link #getContainer(long)}.
   *
   * @param containerId numeric OGM id of the FileContainer
   * @return distinct DataObjects linked to this container
   */
  public List<DataObject> findLinkedDataObjectsById(long containerId) {
    FileContainer container = getContainer(containerId);
    String appId = container.getAppId();
    if (appId == null) {
      return java.util.Collections.emptyList();
    }
    return fileContainerDAO.findLinkedDataObjectsByContainerAppId(appId);
  }

  /**
   * FS1c — generate a presigned GET URL so the caller can download
   * bytes directly from the storage backend.
   *
   * @throws ServiceUnavailableException if the adapter for this row
   *         does not support presigned downloads
   */
  public URI presignedDownloadUrl(long containerId, String oid, Duration ttl) throws StorageException {
    FileContainer container = getContainer(containerId);
    ShepardFile file = findEntityForRoutingOrNull(container, oid);
    String providerId = effectiveProviderId(file);
    FileStorage adapter = storageForRow(providerId);
    StorageLocator locator = buildLocatorForRow(providerId, container.getMongoId(), oid);
    String fileName = file != null ? file.getFilename() : null;
    return adapter.presignedDownloadUrl(locator, fileName, ttl)
      .orElseThrow(() -> new ServiceUnavailableException(
        "Storage provider '" + providerId + "' does not support presigned download URLs."));
  }

  /**
   * Build the opaque locator value for a stored file row. GridFS
   * uses the {@code ":"} separator; all other adapters use {@code "/"}
   * to match their {@code containerMongoId/uuid} key format.
   */
  private static StorageLocator buildLocatorForRow(String providerId, String containerMongoId, String oid) {
    if (GridFsFileStorage.ID.equals(providerId)) {
      return new StorageLocator(providerId, containerMongoId + GridFsFileStorage.LOCATOR_SEPARATOR + oid);
    }
    return new StorageLocator(providerId, containerMongoId + "/" + oid);
  }

  /**
   * Locate the {@link ShepardFile} entity inside the container by
   * its oid (best-effort; falls back to null if neither the eager
   * relationship nor the GridFS bookkeeping have it).
   *
   * <p>Used to read the {@code providerId} property so the registry
   * routes correctly. Returns null for tests / fixtures where the
   * relationship side is unpopulated; the caller's
   * {@link #effectiveProviderId(ShepardFile)} fallback handles null
   * with the FS1a default {@code "gridfs"}.
   */
  private ShepardFile findEntityForRoutingOrNull(FileContainer container, String oid) {
    if (container.getFiles() != null) {
      for (ShepardFile f : container.getFiles()) {
        if (oid.equals(f.getOid())) return f;
      }
    }
    try {
      return fileService.getFile(container.getMongoId(), oid);
    } catch (NotFoundException nfe) {
      // The legacy path raises NotFound when the Mongo bookkeeping
      // document is missing — let the downstream storage call surface
      // the right error (StorageNotFoundException → NotFound) rather
      // than swallowing the diagnostic here. We just don't have the
      // entity to read providerId off of, so fall through to the
      // GridFS default below.
      return null;
    }
  }

  /**
   * @return {@code file.providerId} when present, falling back to
   *         {@link GridFsFileStorage#ID} for the (rare) entity-level
   *         null that pre-dates V34's backfill stamp or test
   *         fixtures.
   *
   * <p>The fallback covers the brief window between the V34 schema
   * change landing and the migration completing on a slow-restart
   * deployment, plus any in-test fixtures that don't populate the
   * field. New rows always get it stamped by {@link #createFile}.
   */
  private static String effectiveProviderId(ShepardFile file) {
    if (file == null) return GridFsFileStorage.ID;
    String pid = file.getProviderId();
    return (pid == null || pid.isBlank()) ? GridFsFileStorage.ID : pid;
  }

  /**
   * Pick the {@link FileStorage} adapter for a read / delete on a
   * row's provider. If the row's providerId points at an adapter
   * that's not installed, fall through to {@link
   * FileStorageRegistry#requireActive()} — the 503 envelope is the
   * right answer for "operator removed the adapter but the rows
   * still reference it" (FS1e migration not yet complete).
   */
  private FileStorage storageForRow(String providerId) {
    for (FileStorage s : fileStorageRegistry.list()) {
      if (providerId.equals(s.id())) {
        if (s.isEnabled()) return s;
        Log.warnf(
          "FileContainerService: storage adapter '%s' is registered but disabled — falling back to active provider",
          providerId
        );
        break;
      }
    }
    return fileStorageRegistry.requireActive();
  }
}
