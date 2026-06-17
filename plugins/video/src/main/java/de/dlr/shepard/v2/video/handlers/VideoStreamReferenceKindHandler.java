package de.dlr.shepard.v2.video.handlers;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.references.videostreamreference.daos.VideoStreamReferenceDAO;
import de.dlr.shepard.context.references.videostreamreference.model.VideoStreamReference;
import de.dlr.shepard.context.references.videostreamreference.services.VideoStreamReferenceService;
import de.dlr.shepard.storage.StorageException;
import de.dlr.shepard.storage.StorageNotInstalledException;
import de.dlr.shepard.v2.references.io.ReferenceV2IO;
import de.dlr.shepard.v2.references.spi.ReferenceKindHandler;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PLUGIN-REF-HANDLER-VIDEO — {@link ReferenceKindHandler} for {@code kind=video}.
 *
 * <p>Discovered via CDI {@code @Any Instance<ReferenceKindHandler>} by the
 * {@code ReferencesV2Service} dispatcher. Delegates CRUD to the existing
 * {@link VideoStreamReferenceService}.
 *
 * <p>APISIMP-VIDEO-STREAMREF-PATH: creates use Option C two-step shape —
 * {@link #create} mints a metadata-only node; {@link #uploadContent} attaches
 * the binary payload via {@code PUT /v2/references/{appId}/content}.
 *
 * <p>Payload key set: {@code storageLocator, mimeType, fileSizeBytes,
 * durationSeconds, width, height, frameRate, videoCodec, audioCodec,
 * wallClockTimestamp}.
 */
@RequestScoped
public class VideoStreamReferenceKindHandler implements ReferenceKindHandler {

  @Inject
  VideoStreamReferenceService videoStreamReferenceService;

  @Inject
  VideoStreamReferenceDAO videoStreamReferenceDAO;

  @Inject
  UserService userService;

  @Inject
  DateHelper dateHelper;

  @Override
  public String kind() {
    return "video";
  }

  @Override
  public boolean owns(BasicReference reference) {
    return reference instanceof VideoStreamReference;
  }

  @Override
  public BasicReference findByAppId(String appId) {
    return videoStreamReferenceService.findByAppId(appId);
  }

  @Override
  public ReferenceV2IO toIO(BasicReference reference) {
    VideoStreamReference ref = (VideoStreamReference) reference;
    ReferenceV2IO io = new ReferenceV2IO(ref, kind());
    io.setReferenceShape("stream");
    io.put("storageLocator", ref.getStorageLocator());
    io.put("mimeType", ref.getMimeType());
    io.put("fileSizeBytes", ref.getFileSizeBytes());
    io.put("durationSeconds", ref.getDurationSeconds());
    io.put("width", ref.getWidth());
    io.put("height", ref.getHeight());
    io.put("frameRate", ref.getFrameRate());
    io.put("videoCodec", ref.getVideoCodec());
    io.put("audioCodec", ref.getAudioCodec());
    io.put("wallClockTimestamp", ref.getWallClockTimestamp());
    return io;
  }

  /**
   * APISIMP-VIDEO-STREAMREF-PATH Option C phase 1 — create a metadata-only
   * VideoStreamReference node. The caller must follow up with
   * {@code PUT /v2/references/{appId}/content} to store the binary bytes.
   *
   * <p>Body must include a non-blank {@code name} field.
   */
  @Override
  public ReferenceV2IO create(String dataObjectAppId, Map<String, Object> body) {
    Object nameVal = body != null ? body.get("name") : null;
    if (nameVal == null || nameVal.toString().isBlank()) {
      throw new BadRequestException("kind=video create body must include a non-blank 'name' field");
    }
    VideoStreamReference created = videoStreamReferenceService.createMetadata(
      dataObjectAppId, nameVal.toString().trim()
    );
    return toIO(created);
  }

  /**
   * APISIMP-VIDEO-STREAMREF-PATH Option C phase 2 — attach binary content to
   * an existing VideoStreamReference via {@code PUT /v2/references/{appId}/content}.
   */
  @Override
  public ReferenceV2IO uploadContent(String appId, InputStream input, String filename, long declaredSize) {
    try {
      VideoStreamReference updated = videoStreamReferenceService.attachPayload(
        appId, filename, null, declaredSize, input
      );
      return toIO(updated);
    } catch (StorageNotInstalledException ex) {
      throw new WebApplicationException(ex.getMessage(), Response.Status.SERVICE_UNAVAILABLE);
    } catch (StorageException ex) {
      throw new WebApplicationException(ex.getMessage(), Response.Status.INTERNAL_SERVER_ERROR);
    }
  }

  @Override
  public ReferenceV2IO patch(String appId, Map<String, Object> patch) {
    VideoStreamReference ref = videoStreamReferenceService.findByAppId(appId);
    if (ref == null) throw new NotFoundException("No VideoStreamReference with appId " + appId);
    boolean changed = false;
    if (patch != null && patch.containsKey("name")) {
      Object v = patch.get("name");
      if (!(v instanceof String s) || s.isBlank()) {
        throw new BadRequestException("'name' must be a non-blank string");
      }
      if (!s.equals(ref.getName())) {
        ref.setName(s);
        changed = true;
      }
    }
    if (changed) {
      User user = userService.getCurrentUser();
      ref.setUpdatedAt(dateHelper.getDate());
      ref.setUpdatedBy(user);
      ref = videoStreamReferenceDAO.createOrUpdate(ref);
    }
    return toIO(ref);
  }

  @Override
  public void delete(String appId) {
    VideoStreamReference ref = videoStreamReferenceService.findByAppId(appId);
    if (ref == null) throw new NotFoundException("No VideoStreamReference with appId " + appId);
    videoStreamReferenceService.delete(ref);
  }

  @Override
  public List<ReferenceV2IO> listByDataObject(String dataObjectAppId, String subKind) {
    if (dataObjectAppId == null || dataObjectAppId.isBlank()) {
      throw new BadRequestException("dataObjectAppId is required");
    }
    List<VideoStreamReference> refs = videoStreamReferenceService.listByDataObject(dataObjectAppId);
    List<ReferenceV2IO> out = new ArrayList<>(refs.size());
    for (VideoStreamReference ref : refs) {
      if (ref != null && !ref.isDeleted()) out.add(toIO(ref));
    }
    return out;
  }
}
