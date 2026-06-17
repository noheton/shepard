package de.dlr.shepard.v2.video.handlers;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.references.videostreamreference.daos.VideoStreamReferenceDAO;
import de.dlr.shepard.context.references.videostreamreference.model.VideoStreamReference;
import de.dlr.shepard.context.references.videostreamreference.services.VideoStreamReferenceService;
import de.dlr.shepard.v2.references.io.ReferenceV2IO;
import de.dlr.shepard.v2.references.spi.ReferenceKindHandler;
import de.dlr.shepard.v2.video.daos.VideoAnnotationDAO;
import de.dlr.shepard.v2.video.model.VideoAnnotation;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PLUGIN-REF-HANDLER-VIDEO — {@link ReferenceKindHandler} for {@code kind=video}.
 *
 * <p>Discovered via CDI {@code @Any Instance<ReferenceKindHandler>} by the
 * {@code ReferencesV2Service} dispatcher. Delegates CRUD to the existing
 * {@link VideoStreamReferenceService}.
 *
 * <p>Video references are binary (multipart upload via
 * {@code POST /v2/video-stream-references}) — the {@link #create} method
 * always rejects with 400, matching the {@code FileReferenceKindHandler} shape.
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

  @Inject
  VideoAnnotationDAO videoAnnotationDAO;

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
   * Video references are binary — they are created via the multipart
   * {@code POST /v2/video-stream-references} upload endpoint, not the
   * JSON unified create. Rejects with 400.
   */
  @Override
  public ReferenceV2IO create(String dataObjectAppId, Map<String, Object> body) {
    throw new BadRequestException(
      "kind=video is a binary upload — use multipart POST /v2/video-stream-references?dataObjectAppId=… " +
      "instead of POST /v2/references"
    );
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

  // ─── annotation sub-resource (APISIMP-ANNOTATION-SUBRESOURCE-COLLISION) ──

  @Override
  public boolean supportsAnnotations() { return true; }

  @Override
  public List<Map<String, Object>> listAnnotations(String refAppId) {
    return videoAnnotationDAO.findByVideoReferenceAppId(refAppId).stream()
      .map(VideoStreamReferenceKindHandler::annotationToMap)
      .toList();
  }

  @Override
  public Map<String, Object> createAnnotation(String refAppId, Map<String, Object> body) {
    if (body == null || !body.containsKey("startSeconds") || body.get("startSeconds") == null) {
      throw new BadRequestException("startSeconds is required for video annotations");
    }
    String label = requireLabel(body);
    VideoAnnotation a = new VideoAnnotation();
    a.setStartSeconds(toDouble(body.get("startSeconds"), "startSeconds"));
    if (body.containsKey("endSeconds") && body.get("endSeconds") != null) {
      a.setEndSeconds(toDouble(body.get("endSeconds"), "endSeconds"));
    }
    a.setLabel(label);
    if (body.containsKey("description")) a.setDescription(asString(body.get("description")));
    if (Boolean.TRUE.equals(body.get("aiGenerated"))) a.setAiGenerated(true);
    if (body.containsKey("confidence") && body.get("confidence") != null) {
      a.setConfidence(toDouble(body.get("confidence"), "confidence"));
    }
    videoAnnotationDAO.createOrUpdate(a);
    videoAnnotationDAO.linkToReference(refAppId, a.getAppId());
    return annotationToMap(a);
  }

  @Override
  public Map<String, Object> getAnnotation(String refAppId, String annotationAppId) {
    VideoAnnotation a = videoAnnotationDAO.findByAppId(annotationAppId);
    if (a == null) throw new NotFoundException("Annotation not found: " + annotationAppId);
    return annotationToMap(a);
  }

  @Override
  public Map<String, Object> patchAnnotation(String refAppId, String annotationAppId, Map<String, Object> patch) {
    VideoAnnotation a = videoAnnotationDAO.findByAppId(annotationAppId);
    if (a == null) throw new NotFoundException("Annotation not found: " + annotationAppId);
    if (patch == null) return annotationToMap(a);
    if (patch.containsKey("startSeconds") && patch.get("startSeconds") != null) {
      a.setStartSeconds(toDouble(patch.get("startSeconds"), "startSeconds"));
    }
    if (patch.containsKey("endSeconds")) {
      a.setEndSeconds(patch.get("endSeconds") == null ? null : toDouble(patch.get("endSeconds"), "endSeconds"));
    }
    if (patch.containsKey("label")) {
      String lbl = patch.get("label") instanceof String s ? s : null;
      if (lbl == null || lbl.isBlank()) throw new BadRequestException("label must be non-blank when provided");
      a.setLabel(lbl.strip());
    }
    if (patch.containsKey("description")) a.setDescription(asString(patch.get("description")));
    if (patch.containsKey("confidence")) {
      a.setConfidence(patch.get("confidence") == null ? null : toDouble(patch.get("confidence"), "confidence"));
    }
    videoAnnotationDAO.createOrUpdate(a);
    return annotationToMap(a);
  }

  @Override
  public void deleteAnnotation(String refAppId, String annotationAppId) {
    VideoAnnotation a = videoAnnotationDAO.findByAppId(annotationAppId);
    if (a == null) throw new NotFoundException("Annotation not found: " + annotationAppId);
    videoAnnotationDAO.unlinkAndDelete(refAppId, a);
  }

  private static Map<String, Object> annotationToMap(VideoAnnotation a) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("appId", a.getAppId());
    m.put("startSeconds", a.getStartSeconds());
    m.put("endSeconds", a.getEndSeconds());
    m.put("label", a.getLabel());
    m.put("description", a.getDescription());
    m.put("aiGenerated", a.isAiGenerated());
    m.put("confidence", a.getConfidence());
    return m;
  }

  private static String requireLabel(Map<String, Object> body) {
    Object v = body.get("label");
    if (!(v instanceof String s) || s.isBlank()) {
      throw new BadRequestException("label is required and must be non-blank");
    }
    return s.strip();
  }

  private static Double toDouble(Object v, String field) {
    if (v instanceof Number n) return n.doubleValue();
    throw new BadRequestException("'" + field + "' must be a number, got: " + v);
  }

  private static String asString(Object v) {
    return v == null ? null : String.valueOf(v);
  }
}
