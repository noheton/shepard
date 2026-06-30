package de.dlr.shepard.context.references.videostreamreference.services;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidRequestException;
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
 */
@RequestScoped
public class VideoStreamReferenceService {

  /** Shared MongoDB / S3 container used for video file bytes (one per deployment). */
  public static final String VIDEO_CONTAINER = "_shepard_videos";

  /**
   * STORAGE-SPI-UNIFY-1 — maximum file size (in bytes) for which the
   * stream is buffered into memory to determine its exact size before
   * the SPI {@code put}. Files larger than this threshold are forwarded
   * directly to storage with their declared (Content-Length) size,
   * trading a possible size-unknown fallback for bounded heap usage.
   * 100 MiB matches a typical short-form video clip.
   *
   * <p>(Previously this gated GridFS-specific MD5 deduplication via a
   * direct {@code MongoDatabase} query — a "magic route" that bypassed
   * the {@link FileStorageRegistry} and broke under an S3 backend. The
   * dedup lookup was removed; size determination is the remaining
   * provider-agnostic reason to buffer.)
   */
  static final long DEDUP_MAX_SIZE_BYTES = 100L * 1024 * 1024; // 100 MiB

  /**
   * MONGO-AUDIT-2026-05-24-012 — configurable upper bound for video file uploads.
   * Shares the same config key as the file upload cap so operators have a single
   * knob. Set to {@code 0} to disable the check (unrestricted uploads).
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
   * APISIMP-VIDEO-STREAMREF-PATH — two-step create, step 1.
   *
   * <p>Creates a {@link VideoStreamReference} metadata node with no bytes
   * attached yet. The caller must follow up with {@link #attachBytes} to
   * store the video payload.
   *
   * @param dataObjectAppId parent DataObject's appId
   * @param name            human-readable reference name (required, non-blank)
   * @return the persisted entity (storageLocator is null)
   * @throws jakarta.ws.rs.NotFoundException when no DataObject with that appId exists
   */
  public VideoStreamReference createMetadataNode(String dataObjectAppId, String name) {
    if (name == null || name.isBlank()) name = "video";
    DataObject parent = resolveDataObjectByAppId(dataObjectAppId);
    if (parent == null) {
      throw new NotFoundException("No DataObject with appId " + dataObjectAppId);
    }
    User user = userService.getCurrentUser();
    VideoStreamReference ref = new VideoStreamReference();
    ref.setName(name);
    ref.setDataObject(parent);
    ref.setCreatedAt(dateHelper.getDate());
    ref.setCreatedBy(user);
    VideoStreamReference created = videoStreamReferenceDAO.createOrUpdate(ref);
    created.setShepardId(created.getId());
    return videoStreamReferenceDAO.createOrUpdate(created);
  }

  /**
   * APISIMP-VIDEO-STREAMREF-PATH — two-step create, step 2.
   *
   * <p>Stores the video bytes for an existing (possibly bytes-free) reference
   * node, runs ffprobe best-effort, and updates the Neo4j fields.
   *
   * @param ref           the VideoStreamReference to attach bytes to (must not be null)
   * @param fileName      original filename (used for MIME detection and GridFS)
   * @param mimeType      caller-supplied MIME type hint (nullable; probed if absent)
   * @param contentLength declared byte count from Content-Length (≤0 = unknown)
   * @param payload       raw video bytes (consumed by this method)
   * @return the updated entity
   * @throws StorageNotInstalledException when no storage adapter is active
   * @throws StorageException             on storage-tier write failure
   */
  public VideoStreamReference attachBytes(
    VideoStreamReference ref,
    String fileName,
    String mimeType,
    long contentLength,
    InputStream payload
  ) throws StorageException {
    if (fileName == null || fileName.isBlank()) fileName = ref.getName();

    Optional<FileStorage> storageOpt = fileStorageRegistry.activeStorage();
    if (storageOpt.isEmpty()) {
      throw new StorageNotInstalledException("No active file storage adapter configured");
    }
    FileStorage storage = storageOpt.get();

    if (mongoFileMaxBytes > 0 && contentLength > 0 && contentLength > mongoFileMaxBytes) {
      throw new InvalidRequestException(
        "File exceeds the maximum allowed size of " + mongoFileMaxBytes + " bytes"
      );
    }

    Long sizeHint = contentLength > 0 ? contentLength : null;
    StorageLocator locator;
    if (sizeHint == null || sizeHint <= DEDUP_MAX_SIZE_BYTES) {
      locator = storeWithDedup(storage, fileName, mimeType, sizeHint, payload);
    } else {
      StoragePutRequest req = new StoragePutRequest(VIDEO_CONTAINER, fileName, mimeType, payload, sizeHint, null);
      locator = storage.put(req);
    }

    VideoProbeResult probe = VideoProbeResult.empty();
    Long storedSize = null;
    try {
      StorageGetResponse getResp = storage.get(locator);
      // Authoritative stored byte count from the storage adapter (S3 object
      // content-length / GridFS length) — read before the stream is consumed.
      storedSize = getResp.sizeBytes();
      try (InputStream videoStream = getResp.stream()) {
        probe = videoProbeService.probe(videoStream, mimeType);
      }
    } catch (Exception ex) {
      Log.warnf("VID1a/attachBytes: probe failed (locator=%s): %s", locator, ex.getMessage());
    }

    // IMPORT-VIDEO-MP4-SHORTUPLOAD — the actual stored size wins. The declared
    // Content-Length does not round-trip for large chunked PUTs through the
    // proxy, and ffprobe is unreliable on these MP4s (exits 1), so falling back
    // to either recorded a fileSize that never matched the source and wedged
    // the importer's short-upload retry guard. The storage adapter's reported
    // size is the ground truth the client verifies against.
    Long fileSizeBytes = storedSize != null ? storedSize
      : (probe.fileSizeBytes() != null ? probe.fileSizeBytes() : sizeHint);
    User user = userService.getCurrentUser();
    ref.setMimeType(mimeType);
    ref.setFileSizeBytes(fileSizeBytes);
    ref.setStorageLocator(locator.providerId() + ":" + locator.locator());
    ref.setDurationSeconds(probe.durationSeconds());
    ref.setWidth(probe.width());
    ref.setHeight(probe.height());
    ref.setFrameRate(probe.frameRate());
    ref.setVideoCodec(probe.videoCodec());
    ref.setAudioCodec(probe.audioCodec());
    if (probe.wallClockTimestamp() != null) ref.setWallClockTimestamp(probe.wallClockTimestamp());
    ref.setUpdatedAt(dateHelper.getDate());
    ref.setUpdatedBy(user);
    VideoStreamReference updated = videoStreamReferenceDAO.createOrUpdate(ref);
    Log.debugf("VID1a/attachBytes: attached content to VideoStreamReference appId=%s (locator=%s)", ref.getAppId(), locator);
    // VID-FFMPEG-TRANSCODE-2026-06-29 — fire-and-forget enqueue a transcode
    // to the browser-playable h.264 proxy. Per the secondary-writes rule
    // this MUST NOT propagate any failure back to the upload caller.
    try {
      updated = transcodeOrchestrator.submit(updated);
    } catch (Exception ex) {
      Log.warnf(ex, "VID1a/attachBytes: transcode submit failed for appId=%s (upload itself succeeded)", updated.getAppId());
    }
    return updated;
  }

  /**
   * Create a new {@link VideoStreamReference} and store the file payload.
   *
   * <p>Steps:
   * <ol>
   *   <li>Resolve the parent DataObject by its {@code appId}.</li>
   *   <li>Run the video probe on the temp file supplied by the multipart
   *       layer — best-effort; never fails the upload.</li>
   *   <li>Store the bytes through the active {@link FileStorage} adapter
   *       (resolved via {@link FileStorageRegistry}) — GridFS by default,
   *       S3 when {@code shepard.storage.provider=s3}.</li>
   *   <li>Persist the {@link VideoStreamReference} node in Neo4j.</li>
   *   <li>Backfill {@code fileSizeBytes} from the probe / Content-Length.</li>
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

    // MONGO-AUDIT-2026-05-24-012 — reject oversized video uploads before writing to GridFS.
    // The contentLength is provided by the REST layer from the multipart temp-file size.
    if (mongoFileMaxBytes > 0 && contentLength != null && contentLength > mongoFileMaxBytes) {
      throw new InvalidRequestException(
        "File exceeds the maximum allowed size of " + mongoFileMaxBytes + " bytes"
      );
    }

    // STORAGE-SPI-UNIFY-1 — store the bytes through the active FileStorage
    // adapter only. Size guard: buffer (to determine exact size) when the
    // declared size is unknown or within DEDUP_MAX_SIZE_BYTES; very large
    // uploads stream directly with their declared Content-Length to avoid
    // exhausting heap. No substrate is touched directly — the registry's
    // active adapter (GridFS or S3) owns the write.
    StorageLocator locator;
    if (contentLength == null || contentLength <= DEDUP_MAX_SIZE_BYTES) {
      locator = storeWithDedup(storage, fileName, mimeType, contentLength, payload);
    } else {
      Log.debugf(
        "VID1a: file size %d > buffer threshold %d — streaming directly to storage",
        contentLength, (Object) DEDUP_MAX_SIZE_BYTES
      );
      StoragePutRequest req = new StoragePutRequest(VIDEO_CONTAINER, fileName, mimeType, payload, contentLength, null);
      locator = storage.put(req);
    }

    // Now probe the stored bytes by fetching them back.
    VideoProbeResult probe = VideoProbeResult.empty();
    Long storedSize = null;
    try {
      StorageGetResponse getResp = storage.get(locator);
      // Authoritative stored byte count from the storage adapter (S3 object
      // content-length / GridFS length) — read before the stream is consumed.
      storedSize = getResp.sizeBytes();
      try (InputStream videoStream = getResp.stream()) {
        probe = videoProbeService.probe(videoStream, mimeType);
      }
    } catch (Exception ex) {
      Log.warnf("VID1a: probe failed after upload (locator=%s): %s", locator, ex.getMessage());
    }

    // IMPORT-VIDEO-MP4-SHORTUPLOAD — the actual stored size wins. The declared
    // Content-Length does not round-trip for large chunked PUTs through the
    // proxy, and ffprobe is unreliable on these MP4s (exits 1), so falling back
    // to either recorded a fileSize that never matched the source and wedged
    // the importer's short-upload retry guard. The storage adapter's reported
    // size is the ground truth the client verifies against.
    Long fileSizeBytes = storedSize != null ? storedSize
      : (probe.fileSizeBytes() != null ? probe.fileSizeBytes() : contentLength);

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
    // VID-FFMPEG-TRANSCODE-2026-06-29 — fire-and-forget enqueue a transcode.
    // Secondary-writes rule: never propagate to the upload caller.
    try {
      created = transcodeOrchestrator.submit(created);
    } catch (Exception ex) {
      Log.warnf(ex, "VID1a/create: transcode submit failed for appId=%s (upload itself succeeded)", created.getAppId());
    }
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
    return getPayload(ref, false);
  }

  /**
   * VIDEO-HEVC-TRANSCODE-BACKFILL — proxy-aware payload retrieval.
   *
   * <p>When {@code preferSource} is {@code false} (the default) and the
   * reference has a {@code proxyStatus = "READY"} + non-blank
   * {@code proxyStorageLocator}, this returns the browser-friendly h.264 proxy
   * bytes instead of the original (HEVC) source. When {@code preferSource} is
   * {@code true}, the original {@code storageLocator} is returned — preserving
   * an escape hatch for archive / download-the-original flows.
   *
   * @param ref          the reference entity
   * @param preferSource when true, force-serve the original source bytes even if
   *                     a READY proxy exists
   */
  public StorageGetResponse getPayload(VideoStreamReference ref, boolean preferSource) throws StorageException {
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

  /**
   * STORAGE-SPI-UNIFY-1 — buffer the stream to determine its exact size,
   * then store it through the {@link FileStorage} SPI.
   *
   * <p>Previously this performed GridFS-specific MD5 deduplication by
   * querying a {@code MongoDatabase} collection directly — a "magic
   * route" that bypassed {@link FileStorageRegistry#activeStorage()} and
   * silently broke under an S3 backend (the bytes were put through the
   * SPI, but the dedup short-circuit synthesised a {@code gridfs:} locator
   * even when the active provider was {@code s3}). The dedup lookup was
   * removed so every byte write routes through the active adapter and only
   * the active adapter; size determination (so object stores can pick
   * single- vs multipart) is the remaining provider-agnostic reason to
   * buffer.
   *
   * <p>If buffering fails (I/O error), the method falls back to a direct
   * {@code put} with the declared {@code contentLength} so the upload path
   * is never broken by the size pre-read.
   *
   * @param storage       the active {@link FileStorage} adapter
   * @param fileName      filename for the stored object's Content-Disposition
   * @param mimeType      MIME type hint (nullable)
   * @param contentLength pre-known size (nullable)
   * @param payload       the raw video byte stream (consumed by this method)
   * @return the {@link StorageLocator} for the stored blob
   * @throws StorageException on storage-tier write failure
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
      // Could not buffer: fall back to a direct put with the declared size.
      Log.warnf("VID1a: could not buffer video stream (%s) — streaming directly to storage", ex.getMessage());
      StoragePutRequest req = new StoragePutRequest(VIDEO_CONTAINER, fileName, mimeType, payload, contentLength, null);
      return storage.put(req);
    }

    Long size = (long) bytes.length;
    StoragePutRequest req = new StoragePutRequest(VIDEO_CONTAINER, fileName, mimeType, new ByteArrayInputStream(bytes), size, null);
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
