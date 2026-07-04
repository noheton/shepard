package de.dlr.shepard.context.references.videostreamreference.services;

import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.videostreamreference.daos.VideoStreamReferenceDAO;
import de.dlr.shepard.context.references.videostreamreference.model.VideoStreamReference;
import de.dlr.shepard.plugins.video.transcode.VideoTranscodeOrchestrator;
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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * VID1a — service layer for {@link VideoStreamReference} CRUD.
 *
 * <p>Permission enforcement happens at the REST layer
 * ({@link de.dlr.shepard.v2.video.resources.VideoStreamReferenceV2Rest});
 * this service trusts its callers, mirroring the
 * {@link de.dlr.shepard.context.references.file.services.SingletonFileReferenceService}
 * posture.
 *
 * <p><b>CRIT-QUARKUS-CLASSTRANSFORM-VIDEOPAYLOAD</b> — methods that declare a
 * {@code VideoStreamReference} local variable have been extracted to
 * {@link VideoStreamReferenceServiceLogic} (a plain, non-CDI class). This class's
 * own methods are trivial one-line delegates whose frames contain no
 * {@code VideoStreamReference} locals, eliminating the problematic frame-merge
 * that triggers {@code NoClassDefFoundError: BasicReference}.
 */
@RequestScoped
public class VideoStreamReferenceService {

  /** Shared MongoDB / S3 container used for video file bytes (one per deployment). */
  public static final String VIDEO_CONTAINER = "_shepard_videos";

  /**
   * STORAGE-SPI-UNIFY-1 — maximum file size (in bytes) for which the
   * stream is buffered into memory to determine its exact size before
   * the SPI {@code put}.
   */
  static final long DEDUP_MAX_SIZE_BYTES = 100L * 1024 * 1024; // 100 MiB

  /**
   * MONGO-AUDIT-2026-05-24-012 — configurable upper bound for video file uploads.
   */
  @ConfigProperty(name = "shepard.mongo.file.max-bytes", defaultValue = "2147483648")
  long mongoFileMaxBytes;

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
  VideoTranscodeOrchestrator transcodeOrchestrator;

  @Inject
  UserService userService;

  @Inject
  DateHelper dateHelper;

  // ── query ─────────────────────────────────────────────────────────────────

  /**
   * Find one by appId. Returns {@code null} when not found.
   */
  public VideoStreamReference findByAppId(String appId) {
    return videoStreamReferenceDAO.findByAppId(appId);
  }

  /**
   * List all {@link VideoStreamReference} nodes attached to the given
   * DataObject (resolved by its {@code appId}).
   *
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
   * APISIMP-VIDEO-STREAMREF-PATH — two-step create, step 1.
   *
   * <p>Creates a {@link VideoStreamReference} metadata node with no bytes attached.
   * Delegates to {@link VideoStreamReferenceServiceLogic} to avoid VSR local
   * declarations in this CDI bean.
   */
  public VideoStreamReference createMetadataNode(String dataObjectAppId, String name) {
    return VideoStreamReferenceServiceLogic.createMetadataNode(
      dataObjectAppId, name,
      videoStreamReferenceDAO, dataObjectDAO, entityIdResolver, userService, dateHelper
    );
  }

  /**
   * APISIMP-VIDEO-STREAMREF-PATH — two-step create, step 2.
   *
   * <p>Stores video bytes, runs ffprobe best-effort, and updates Neo4j fields.
   * Delegates to {@link VideoStreamReferenceServiceLogic} to avoid VSR local
   * declarations in this CDI bean.
   */
  public VideoStreamReference attachBytes(
    VideoStreamReference ref,
    String fileName,
    String mimeType,
    long contentLength,
    InputStream payload
  ) throws StorageException {
    return VideoStreamReferenceServiceLogic.attachBytes(
      ref, fileName, mimeType, contentLength, payload,
      videoStreamReferenceDAO, fileStorageRegistry, videoProbeService,
      transcodeOrchestrator, userService, dateHelper, mongoFileMaxBytes
    );
  }

  /**
   * Create a new {@link VideoStreamReference} and store the file payload.
   * Delegates to {@link VideoStreamReferenceServiceLogic} to avoid VSR local
   * declarations in this CDI bean.
   */
  public VideoStreamReference create(
    String dataObjectAppId,
    String name,
    String fileName,
    String mimeType,
    Long contentLength,
    InputStream payload
  ) throws StorageException {
    return VideoStreamReferenceServiceLogic.create(
      dataObjectAppId, name, fileName, mimeType, contentLength, payload,
      videoStreamReferenceDAO, dataObjectDAO, entityIdResolver,
      fileStorageRegistry, videoProbeService, transcodeOrchestrator,
      userService, dateHelper, mongoFileMaxBytes
    );
  }

  // ── download ──────────────────────────────────────────────────────────────

  /**
   * Retrieve the stored bytes for a {@link VideoStreamReference}.
   */
  public StorageGetResponse getPayload(VideoStreamReference ref) throws StorageException {
    return getPayload(ref, false);
  }

  /**
   * VIDEO-HEVC-TRANSCODE-BACKFILL — proxy-aware payload retrieval.
   *
   * <p>When {@code preferSource} is {@code false} and the reference has
   * {@code proxyStatus = "READY"} + a non-blank {@code proxyStorageLocator},
   * returns the browser-friendly h.264 proxy bytes. When {@code preferSource}
   * is {@code true}, the original source locator is used.
   */
  public StorageGetResponse getPayload(VideoStreamReference ref, boolean preferSource)
      throws StorageException {
    String locatorRaw = ref.getStorageLocator();
    if (!preferSource) {
      String proxyLocator = ref.getProxyStorageLocator();
      String proxyStatus = ref.getProxyStatus();
      if (proxyLocator != null && !proxyLocator.isBlank()
        && proxyStatus != null && proxyStatus.equalsIgnoreCase("READY")) {
        locatorRaw = proxyLocator;
      }
    }
    if (locatorRaw == null || locatorRaw.isBlank()) {
      throw new NotFoundException("VideoStreamReference has no stored file (upload may have failed)");
    }

    int colon = locatorRaw.indexOf(':');
    if (colon < 0) {
      throw new NotFoundException("VideoStreamReference has malformed storage locator: " + locatorRaw);
    }
    String providerId = locatorRaw.substring(0, colon);
    String locator = locatorRaw.substring(colon + 1);

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
   */
  public void delete(VideoStreamReference ref) {
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

    ref.setDeleted(true);
    ref.setUpdatedAt(dateHelper.getDate());
    ref.setUpdatedBy(userService.getCurrentUser());
    videoStreamReferenceDAO.createOrUpdate(ref);

    Log.debugf("VID1a: deleted VideoStreamReference appId=%s", ref.getAppId());
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  /**
   * Resolve the DataObject OGM Long id from its appId.
   */
  public Long getDataObjectOgmId(String appId) {
    if (appId == null || appId.isBlank()) return null;
    try {
      return entityIdResolver.resolveLong(appId);
    } catch (NotFoundException e) {
      return null;
    }
  }

  /**
   * STORAGE-SPI-UNIFY-1 — buffer the stream to determine its exact size,
   * then store it through the {@link FileStorage} SPI.
   */
  StorageLocator storeWithDedup(
    FileStorage storage,
    String fileName,
    String mimeType,
    Long contentLength,
    InputStream payload
  ) throws StorageException {
    byte[] bytes;
    try {
      bytes = payload.readAllBytes();
    } catch (IOException ex) {
      Log.warnf("VID1a: could not buffer video stream (%s) — streaming directly to storage",
        ex.getMessage());
      StoragePutRequest req = new StoragePutRequest(
        VIDEO_CONTAINER, fileName, mimeType, payload, contentLength, null);
      return storage.put(req);
    }

    Long size = (long) bytes.length;
    StoragePutRequest req = new StoragePutRequest(
      VIDEO_CONTAINER, fileName, mimeType, new ByteArrayInputStream(bytes), size, null);
    return storage.put(req);
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
