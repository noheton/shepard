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
import jakarta.ws.rs.NotFoundException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * CRIT-QUARKUS-CLASSTRANSFORM-VIDEOPAYLOAD — package-private static helper.
 *
 * <p>Holds every method body that declares a {@code VideoStreamReference} local
 * variable.  No CDI annotations → Arc never registers a bytecode transformer for
 * this class → {@code ClassTransformingBuildStep} skips it entirely, avoiding the
 * {@code getCommonSuperClass(VideoStreamReference,…)} → load {@code BasicReference}
 * → {@code NoClassDefFoundError} failure.
 *
 * <p>{@link VideoStreamReferenceService} — a {@code @RequestScoped} CDI bean —
 * becomes a set of trivial one-line delegates whose own method frames contain no
 * {@code VideoStreamReference} locals.
 */
class VideoStreamReferenceServiceLogic {

  private VideoStreamReferenceServiceLogic() {}

  // ── createMetadataNode ───────────────────────────────────────────────────

  static VideoStreamReference createMetadataNode(
    String dataObjectAppId,
    String name,
    VideoStreamReferenceDAO videoStreamReferenceDAO,
    DataObjectDAO dataObjectDAO,
    EntityIdResolver entityIdResolver,
    UserService userService,
    DateHelper dateHelper
  ) {
    if (name == null || name.isBlank()) name = "video";
    DataObject parent = resolveDataObjectByAppId(dataObjectAppId, dataObjectDAO, entityIdResolver);
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

  // ── attachBytes ─────────────────────────────────────────────────────────

  static VideoStreamReference attachBytes(
    VideoStreamReference ref,
    String fileName,
    String mimeType,
    long contentLength,
    InputStream payload,
    VideoStreamReferenceDAO videoStreamReferenceDAO,
    FileStorageRegistry fileStorageRegistry,
    VideoProbeService videoProbeService,
    VideoTranscodeOrchestrator transcodeOrchestrator,
    UserService userService,
    DateHelper dateHelper,
    long mongoFileMaxBytes
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
    if (sizeHint == null || sizeHint <= VideoStreamReferenceService.DEDUP_MAX_SIZE_BYTES) {
      locator = storeWithDedup(storage, fileName, mimeType, sizeHint, payload);
    } else {
      StoragePutRequest req = new StoragePutRequest(
        VideoStreamReferenceService.VIDEO_CONTAINER, fileName, mimeType, payload, sizeHint, null);
      locator = storage.put(req);
    }

    VideoProbeResult probe = VideoProbeResult.empty();
    Long storedSize = null;
    try {
      StorageGetResponse getResp = storage.get(locator);
      storedSize = getResp.sizeBytes();
      try (InputStream videoStream = getResp.stream()) {
        probe = videoProbeService.probe(videoStream, mimeType);
      }
    } catch (Exception ex) {
      Log.warnf("VID1a/attachBytes: probe failed (locator=%s): %s", locator, ex.getMessage());
    }

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
    Log.debugf("VID1a/attachBytes: attached content to VideoStreamReference appId=%s (locator=%s)",
      ref.getAppId(), locator);
    try {
      updated = transcodeOrchestrator.submit(updated);
    } catch (Exception ex) {
      Log.warnf(ex,
        "VID1a/attachBytes: transcode submit failed for appId=%s (upload itself succeeded)",
        updated.getAppId());
    }
    return updated;
  }

  // ── create ───────────────────────────────────────────────────────────────

  static VideoStreamReference create(
    String dataObjectAppId,
    String name,
    String fileName,
    String mimeType,
    Long contentLength,
    InputStream payload,
    VideoStreamReferenceDAO videoStreamReferenceDAO,
    DataObjectDAO dataObjectDAO,
    EntityIdResolver entityIdResolver,
    FileStorageRegistry fileStorageRegistry,
    VideoProbeService videoProbeService,
    VideoTranscodeOrchestrator transcodeOrchestrator,
    UserService userService,
    DateHelper dateHelper,
    long mongoFileMaxBytes
  ) throws StorageException {
    if (name == null || name.isBlank()) {
      name = fileName != null ? fileName : "video";
    }
    if (fileName == null || fileName.isBlank()) {
      fileName = name;
    }

    DataObject parent = resolveDataObjectByAppId(dataObjectAppId, dataObjectDAO, entityIdResolver);
    if (parent == null) {
      throw new NotFoundException("No DataObject with appId " + dataObjectAppId);
    }

    Optional<FileStorage> storageOpt = fileStorageRegistry.activeStorage();
    if (storageOpt.isEmpty()) {
      throw new StorageNotInstalledException("No active file storage adapter configured");
    }
    FileStorage storage = storageOpt.get();

    if (mongoFileMaxBytes > 0 && contentLength != null && contentLength > mongoFileMaxBytes) {
      throw new InvalidRequestException(
        "File exceeds the maximum allowed size of " + mongoFileMaxBytes + " bytes"
      );
    }

    StorageLocator locator;
    if (contentLength == null || contentLength <= VideoStreamReferenceService.DEDUP_MAX_SIZE_BYTES) {
      locator = storeWithDedup(storage, fileName, mimeType, contentLength, payload);
    } else {
      Log.debugf(
        "VID1a: file size %d > buffer threshold %d — streaming directly to storage",
        contentLength, (Object) VideoStreamReferenceService.DEDUP_MAX_SIZE_BYTES
      );
      StoragePutRequest req = new StoragePutRequest(
        VideoStreamReferenceService.VIDEO_CONTAINER, fileName, mimeType, payload, contentLength, null);
      locator = storage.put(req);
    }

    VideoProbeResult probe = VideoProbeResult.empty();
    Long storedSize = null;
    try {
      StorageGetResponse getResp = storage.get(locator);
      storedSize = getResp.sizeBytes();
      try (InputStream videoStream = getResp.stream()) {
        probe = videoProbeService.probe(videoStream, mimeType);
      }
    } catch (Exception ex) {
      Log.warnf("VID1a: probe failed after upload (locator=%s): %s", locator, ex.getMessage());
    }

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
    try {
      created = transcodeOrchestrator.submit(created);
    } catch (Exception ex) {
      Log.warnf(ex,
        "VID1a/create: transcode submit failed for appId=%s (upload itself succeeded)",
        created.getAppId());
    }
    return created;
  }

  // ── shared helpers ───────────────────────────────────────────────────────

  static StorageLocator storeWithDedup(
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
        VideoStreamReferenceService.VIDEO_CONTAINER, fileName, mimeType, payload, contentLength, null);
      return storage.put(req);
    }

    Long size = (long) bytes.length;
    StoragePutRequest req = new StoragePutRequest(
      VideoStreamReferenceService.VIDEO_CONTAINER, fileName, mimeType,
      new ByteArrayInputStream(bytes), size, null);
    return storage.put(req);
  }

  private static DataObject resolveDataObjectByAppId(
    String appId,
    DataObjectDAO dataObjectDAO,
    EntityIdResolver entityIdResolver
  ) {
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
