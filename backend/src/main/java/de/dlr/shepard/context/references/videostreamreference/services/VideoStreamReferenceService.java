package de.dlr.shepard.context.references.videostreamreference.services;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.videostreamreference.daos.VideoStreamReferenceDAO;
import de.dlr.shepard.context.references.videostreamreference.model.VideoStreamReference;
import de.dlr.shepard.storage.FileStorage;
import de.dlr.shepard.storage.FileStorageRegistry;
import de.dlr.shepard.storage.StorageException;
import de.dlr.shepard.storage.StorageGetResponse;
import de.dlr.shepard.storage.StorageLocator;
import de.dlr.shepard.storage.StorageNotInstalledException;
import de.dlr.shepard.storage.StoragePutRequest;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * VID1a — service layer for {@link VideoStreamReference} CRUD.
 *
 * <p>Permission enforcement happens at the REST layer
 * ({@link de.dlr.shepard.v2.video.resources.VideoStreamReferenceV2Rest});
 * this service trusts its callers, mirroring the
 * {@link de.dlr.shepard.context.references.file.services.SingletonFileReferenceService}
 * posture.
 */
@RequestScoped
public class VideoStreamReferenceService {

  /** Shared MongoDB / S3 container used for video file bytes (one per deployment). */
  public static final String VIDEO_CONTAINER = "_shepard_videos";

  @Inject
  VideoStreamReferenceDAO videoStreamReferenceDAO;

  @Inject
  DataObjectDAO dataObjectDAO;

  @Inject
  EntityIdResolver entityIdResolver;

  @Inject
  FileStorageRegistry fileStorageRegistry;

  @Inject
  VideoProbeService videoProbeService;

  @Inject
  UserService userService;

  @Inject
  DateHelper dateHelper;

  // ── query ─────────────────────────────────────────────────────────────────

  /**
   * Find one by appId. Returns {@code null} when not found.
   *
   * @param appId the reference's appId
   * @return the entity or {@code null}
   */
  public VideoStreamReference findByAppId(String appId) {
    return videoStreamReferenceDAO.findByAppId(appId);
  }

  /**
   * List all {@link VideoStreamReference} nodes attached to the given
   * DataObject (resolved by its {@code appId}).
   *
   * @param dataObjectAppId parent DataObject's appId
   * @return list of references (may be empty)
   * @throws NotFoundException when no DataObject with that appId exists
   */
  public List<VideoStreamReference> listByDataObject(String dataObjectAppId) {
    DataObject dataObject = resolveDataObjectByAppId(dataObjectAppId);
    if (dataObject == null) {
      throw new NotFoundException("No DataObject with appId " + dataObjectAppId);
    }
    return videoStreamReferenceDAO.findByDataObjectNeo4jId(dataObject.getId());
  }

  // ── create ────────────────────────────────────────────────────────────────

  /**
   * Create a new {@link VideoStreamReference} and store the file payload.
   *
   * <p>Steps:
   * <ol>
   *   <li>Resolve and validate the parent DataObject.</li>
   *   <li>Store the bytes via the active {@link FileStorage} adapter.</li>
   *   <li>Run ffprobe on the stored bytes (probe never blocks the upload).</li>
   *   <li>Persist the entity to Neo4j.</li>
   * </ol>
   *
   * @param dataObjectAppId  parent DataObject's appId
   * @param name             human-readable name for the reference (required, non-blank)
   * @param fileName         original filename (for storage metadata)
   * @param mimeType         MIME type hint (nullable; stored as-is)
   * @param contentLength    file size from HTTP Content-Length (nullable)
   * @param payload          the video byte stream
   * @return the persisted reference entity
   * @throws NotFoundException        when no DataObject with that appId exists
   * @throws StorageNotInstalledException when no storage adapter is active
   * @throws StorageException         on storage-tier write failure
   */
  public VideoStreamReference create(
    String dataObjectAppId,
    String name,
    String fileName,
    String mimeType,
    Long contentLength,
    InputStream payload
  ) throws StorageException {
    if (name == null || name.isBlank()) {
      name = fileName != null ? fileName : "video";
    }
    if (fileName == null || fileName.isBlank()) {
      fileName = name;
    }

    DataObject parent = resolveDataObjectByAppId(dataObjectAppId);
    if (parent == null) {
      throw new NotFoundException("No DataObject with appId " + dataObjectAppId);
    }

    Optional<FileStorage> storageOpt = fileStorageRegistry.activeStorage();
    if (storageOpt.isEmpty()) {
      throw new StorageNotInstalledException("No active file storage adapter configured");
    }
    FileStorage storage = storageOpt.get();

    // Run the probe before storing — we need to store anyway, so we
    // write to a temp file in probe() and then feed the stored bytes back
    // via storage.put. For simplicity we run probe on the raw stream first
    // then re-open the file for storage (upload side re-reads the temp via
    // the storage.put path below). However, since the InputStream is consumed
    // by probe, we must buffer it.
    //
    // To keep this path simple and avoid double-buffering: probe the
    // stored file AFTER storage.put by fetching it back. This is slightly
    // less efficient but keeps the code simple and avoids consuming the
    // stream twice.

    // Store the bytes first.
    StoragePutRequest req = new StoragePutRequest(VIDEO_CONTAINER, fileName, mimeType, payload, contentLength, null);
    StorageLocator locator = storage.put(req);

    // Now probe the stored bytes by fetching them back.
    VideoProbeResult probe = VideoProbeResult.empty();
    try {
      StorageGetResponse getResp = storage.get(locator);
      try (InputStream videoStream = getResp.stream()) {
        probe = videoProbeService.probe(videoStream, mimeType);
      }
    } catch (Exception ex) {
      Log.warnf("VID1a: probe failed after upload (locator=%s): %s", locator, ex.getMessage());
    }

    // Determine file size: prefer probe > Content-Length.
    Long fileSizeBytes = probe.fileSizeBytes() != null ? probe.fileSizeBytes() : contentLength;

    User user = userService.getCurrentUser();

    VideoStreamReference ref = new VideoStreamReference();
    ref.setName(name);
    ref.setDataObject(parent);
    ref.setCreatedAt(dateHelper.getDate());
    ref.setCreatedBy(user);
    ref.setMimeType(mimeType);
    ref.setFileSizeBytes(fileSizeBytes);
    ref.setStorageLocator(locator.providerId() + ":" + locator.locator());
    ref.setDurationSeconds(probe.durationSeconds());
    ref.setWidth(probe.width());
    ref.setHeight(probe.height());
    ref.setFrameRate(probe.frameRate());
    ref.setVideoCodec(probe.videoCodec());
    ref.setAudioCodec(probe.audioCodec());
    ref.setWallClockTimestamp(probe.wallClockTimestamp());

    VideoStreamReference created = videoStreamReferenceDAO.createOrUpdate(ref);
    created.setShepardId(created.getId());
    created = videoStreamReferenceDAO.createOrUpdate(created);

    Log.debugf(
      "VID1a: created VideoStreamReference appId=%s under DataObject appId=%s (locator=%s)",
      created.getAppId(), dataObjectAppId, locator
    );
    return created;
  }

  // ── download ──────────────────────────────────────────────────────────────

  /**
   * Retrieve the stored bytes for a {@link VideoStreamReference}.
   *
   * @param ref the reference entity (must have a non-null storageLocator)
   * @return the storage response containing the byte stream and metadata
   * @throws NotFoundException        when the locator is absent or the blob is missing
   * @throws StorageNotInstalledException when no storage adapter is active
   * @throws StorageException         on storage-tier read failure
   */
  public StorageGetResponse getPayload(VideoStreamReference ref) throws StorageException {
    String locatorRaw = ref.getStorageLocator();
    if (locatorRaw == null || locatorRaw.isBlank()) {
      throw new NotFoundException("VideoStreamReference has no stored file (upload may have failed)");
    }

    // Parse "providerId:locator" — split on first colon only.
    int colon = locatorRaw.indexOf(':');
    if (colon < 0) {
      throw new NotFoundException("VideoStreamReference has malformed storage locator: " + locatorRaw);
    }
    String providerId = locatorRaw.substring(0, colon);
    String locator = locatorRaw.substring(colon + 1);

    // Resolve the adapter by providerId. We first try the active one, then
    // fall back to any registered adapter with that id (for mixed-provider
    // history after a FS1e migration). For VID1a we only have one adapter,
    // so the active-adapter check is sufficient.
    Optional<FileStorage> storageOpt = fileStorageRegistry.activeStorage();
    if (storageOpt.isEmpty()) {
      throw new StorageNotInstalledException("No active file storage adapter configured");
    }

    return storageOpt.get().get(new StorageLocator(providerId, locator));
  }

  // ── delete ────────────────────────────────────────────────────────────────

  /**
   * Hard-delete a {@link VideoStreamReference} — removes the Neo4j node and
   * deletes the bytes from the storage backend (best-effort; log on failure).
   *
   * @param ref the entity to delete (must be non-null)
   */
  public void delete(VideoStreamReference ref) {
    // Remove bytes from storage (best-effort).
    String locatorRaw = ref.getStorageLocator();
    if (locatorRaw != null && !locatorRaw.isBlank()) {
      int colon = locatorRaw.indexOf(':');
      if (colon > 0) {
        String providerId = locatorRaw.substring(0, colon);
        String locator = locatorRaw.substring(colon + 1);
        try {
          Optional<FileStorage> storageOpt = fileStorageRegistry.activeStorage();
          if (storageOpt.isPresent()) {
            storageOpt.get().delete(new StorageLocator(providerId, locator));
          }
        } catch (Exception ex) {
          Log.warnf("VID1a: storage delete failed for locator %s — %s", locatorRaw, ex.getMessage());
        }
      }
    }

    // Soft-delete the Neo4j node (follow the BasicReference pattern).
    User user = userService.getCurrentUser();
    ref.setDeleted(true);
    ref.setUpdatedAt(dateHelper.getDate());
    ref.setUpdatedBy(user);
    videoStreamReferenceDAO.createOrUpdate(ref);

    Log.debugf("VID1a: deleted VideoStreamReference appId=%s", ref.getAppId());
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  /**
   * Resolve the DataObject OGM Long id from its appId, for use in the
   * permission gate before the entity is created.
   *
   * @param appId the DataObject's appId
   * @return the OGM Long id, or {@code null} when not found
   */
  public Long getDataObjectOgmId(String appId) {
    if (appId == null || appId.isBlank()) return null;
    try {
      return entityIdResolver.resolveLong(appId);
    } catch (NotFoundException e) {
      return null;
    }
  }

  private DataObject resolveDataObjectByAppId(String appId) {
    if (appId == null) return null;
    long ogmId;
    try {
      ogmId = entityIdResolver.resolveLong(appId);
    } catch (NotFoundException e) {
      return null;
    }
    DataObject dataObject = dataObjectDAO.findByNeo4jId(ogmId);
    if (dataObject == null || dataObject.isDeleted()) {
      return null;
    }
    return dataObject;
  }
}
