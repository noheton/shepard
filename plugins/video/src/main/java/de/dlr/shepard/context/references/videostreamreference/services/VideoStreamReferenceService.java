package de.dlr.shepard.context.references.videostreamreference.services;

import static com.mongodb.client.model.Filters.eq;

import com.mongodb.client.MongoDatabase;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidRequestException;
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
import de.dlr.shepard.storage.gridfs.GridFsFileStorage;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.NotFoundException;
import jakarta.xml.bind.DatatypeConverter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
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
   * MONGO-AUDIT-013 — maximum file size (in bytes) for which content-based
   * MD5 deduplication is attempted. Files larger than this threshold are
   * forwarded directly to storage without buffering, trading possible
   * duplicate blobs for bounded heap usage. 100 MiB matches a typical
   * short-form video clip; the bulk of the LUMEN showcase duplicate
   * (8.6 MiB × 3) is well within this window.
   */
  static final long DEDUP_MAX_SIZE_BYTES = 100L * 1024 * 1024; // 100 MiB

  /**
   * MONGO-AUDIT-2026-05-24-012 — configurable upper bound for video file uploads.
   * Shares the same config key as the file upload cap so operators have a single
   * knob. Set to {@code 0} to disable the check (unrestricted uploads).
   */
  @ConfigProperty(name = "shepard.mongo.file.max-bytes", defaultValue = "2147483648")
  long mongoFileMaxBytes;

  /** Attribute name for MD5 in the {@code _shepard_videos} bookkeeping documents. */
  private static final String MD5_ATTR = "md5";

  @Inject
  @Named("mongoDatabase")
  MongoDatabase mongoDatabase;

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
    try {
      StorageGetResponse getResp = storage.get(locator);
      try (InputStream videoStream = getResp.stream()) {
        probe = videoProbeService.probe(videoStream, mimeType);
      }
    } catch (Exception ex) {
      Log.warnf("VID1a/attachBytes: probe failed (locator=%s): %s", locator, ex.getMessage());
    }

    Long fileSizeBytes = probe.fileSizeBytes() != null ? probe.fileSizeBytes() : sizeHint;
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
    return updated;
  }

  /**
   * Create a new {@link VideoStreamReference} and store the file payload.
   *
   * <p>Steps:
   * <ol>
   *   <li>Resolve the parent DataObject by its {@code appId}.</li>
   *   <li>Run {@link VideoProbeService#probe(InputStream)} on the temp file
   *       supplied by the multipart layer — best-effort; never fails the upload.</li>
   *   <li>Create a new MongoDB collection (one per Reference; GridFS-backed).</li>
   *   <li>Store the bytes via {@link FileService#createFile}.</li>
   *   <li>Persist the {@link VideoStreamReference} node in Neo4j.</li>
   *   <li>Backfill {@code fileSizeBytes} from GridFS after the write.</li>
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

    // MONGO-AUDIT-013 — MD5-based deduplication for GridFS video blobs.
    //
    // Strategy: buffer the stream, compute MD5, check _shepard_videos for an
    // existing document with the same MD5. If found, reuse its _id as the
    // locator rather than uploading a second copy of the same bytes.
    //
    // Size guard: only buffer (and dedup) when the declared size is unknown or
    // within DEDUP_MAX_SIZE_BYTES. Very large uploads fall through to direct
    // storage.put() to avoid exhausting heap.
    //
    // The locator synthesised for a dedup hit is "gridfs:_shepard_videos:<oid>",
    // which is exactly the shape GridFsFileStorage.put() would return — so the
    // rest of the create() path (probe-by-fetch, Neo4j persist) is unchanged.
    StorageLocator locator;
    if (contentLength == null || contentLength <= DEDUP_MAX_SIZE_BYTES) {
      locator = storeWithDedup(storage, fileName, mimeType, contentLength, payload);
    } else {
      Log.debugf(
        "VID1a/DEDUP: file size %d > threshold %d — skipping dedup, streaming directly",
        contentLength, (Object) DEDUP_MAX_SIZE_BYTES
      );
      StoragePutRequest req = new StoragePutRequest(VIDEO_CONTAINER, fileName, mimeType, payload, contentLength, null);
      locator = storage.put(req);
    }

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

  /**
   * MONGO-AUDIT-013 — buffer the stream, compute its MD5, and either reuse
   * an existing blob or upload a fresh one.
   *
   * <p>Steps:
   * <ol>
   *   <li>Read all bytes from {@code payload} into memory while streaming
   *       through a {@link DigestInputStream} to compute the MD5 in one
   *       pass.</li>
   *   <li>Query the {@code _shepard_videos} MongoDB collection for an
   *       existing bookkeeping document whose {@code md5} field matches
   *       the computed digest (upper-case hex, as written by
   *       {@link jakarta.xml.bind.DatatypeConverter#printHexBinary}).</li>
   *   <li>If a match is found: return a synthetic {@link StorageLocator}
   *       pointing at the existing document's {@code _id}. The blob is
   *       reused; no GridFS write occurs.</li>
   *   <li>If no match: call {@code storage.put()} with the buffered bytes.
   *       The {@link GridFsFileStorage} path writes a new GridFS blob and
   *       inserts a new bookkeeping document.</li>
   * </ol>
   *
   * <p>If buffering fails (I/O error) or MD5 is unavailable (should never
   * happen on any JVM that passes the JCE suite), the method falls back to
   * direct {@code storage.put()} so the upload path is never broken by
   * the dedup check.
   *
   * @param storage       the active {@link FileStorage} adapter
   * @param fileName      filename for the bookkeeping document
   * @param mimeType      MIME type hint (nullable)
   * @param contentLength pre-known size (nullable)
   * @param payload       the raw video byte stream (consumed by this method)
   * @return the {@link StorageLocator} for the stored (or reused) blob
   * @throws StorageException on storage-tier write failure
   */
  StorageLocator storeWithDedup(
    FileStorage storage,
    String fileName,
    String mimeType,
    Long contentLength,
    InputStream payload
  ) throws StorageException {
    // Step 1: buffer + digest.
    byte[] bytes;
    String md5Hex;
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      try (DigestInputStream dis = new DigestInputStream(payload, md)) {
        bytes = dis.readAllBytes();
      }
      md5Hex = DatatypeConverter.printHexBinary(md.digest());
    } catch (NoSuchAlgorithmException | IOException ex) {
      // MD5 unavailable or I/O error: fall back to direct put with original
      // stream consumed; log and let storage.put() handle the original stream
      // if bytes is null (it won't be — but be safe).
      Log.warnf("VID1a/DEDUP: could not buffer stream for dedup (%s) — uploading without dedup", ex.getMessage());
      StoragePutRequest req = new StoragePutRequest(VIDEO_CONTAINER, fileName, mimeType, payload, contentLength, null);
      return storage.put(req);
    }

    // Step 2: check for an existing blob with this MD5.
    try {
      Document existing = mongoDatabase
        .getCollection(VIDEO_CONTAINER)
        .find(eq(MD5_ATTR, md5Hex))
        .first();
      if (existing != null) {
        String existingOid = existing.getObjectId("_id").toHexString();
        Log.infof(
          "VID1a/DEDUP: reusing existing blob (md5=%s, oid=%s) — skipping GridFS write",
          md5Hex, existingOid
        );
        return new StorageLocator(GridFsFileStorage.ID, VIDEO_CONTAINER + GridFsFileStorage.LOCATOR_SEPARATOR + existingOid);
      }
    } catch (RuntimeException ex) {
      // Dedup lookup failure must not break the upload — log and continue to fresh upload.
      Log.warnf("VID1a/DEDUP: MD5 lookup in %s failed (%s) — uploading without dedup", VIDEO_CONTAINER, ex.getMessage());
    }

    // Step 3: no existing blob — upload fresh.
    Log.debugf("VID1a/DEDUP: no existing blob for md5=%s — uploading new blob", md5Hex);
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
