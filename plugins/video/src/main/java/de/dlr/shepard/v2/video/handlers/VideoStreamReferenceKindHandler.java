package de.dlr.shepard.v2.video.handlers;

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
import jakarta.ws.rs.core.Response;
import java.io.InputStream;
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
 * accepts a name-only body and returns a stub reference; bytes are attached
 * via {@code PUT /v2/references/{appId}/content}.
 *
 * <p>Payload key set: {@code storageLocator, mimeType, fileSizeBytes,
 * durationSeconds, width, height, frameRate, videoCodec, audioCodec,
 * wallClockTimestamp}.
 *
 * <p><b>CRIT-QUARKUS-CLASSTRANSFORM-VIDEOPAYLOAD</b> — all methods that
 * declare a {@code VideoStreamReference} local variable have been extracted
 * to {@link VideoStreamReferenceKindHandlerLogic} (a plain, non-CDI class).
 * This is necessary because {@code ClassTransformingBuildStep} uses
 * {@code ClassWriter(COMPUTE_FRAMES)} and recomputes frames for <em>all</em>
 * methods in every CDI bean it processes; a frame-merge in those methods would
 * trigger {@code getCommonSuperClass(VideoStreamReference, …)} → load
 * {@code BasicReference} from {@code backend.jar} (provided-scope, absent from
 * the narrow transformation class-loader) → {@code NoClassDefFoundError}.
 * The handler's own methods are now trivial one-line delegates whose frames
 * contain no {@code VideoStreamReference} locals, so no problematic merge occurs.
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

  /**
   * Delegates to {@link VideoStreamReferenceKindHandlerLogic#toIO} — see that
   * class for the rationale.
   */
  @Override
  public ReferenceV2IO toIO(BasicReference reference) {
    return VideoStreamReferenceKindHandlerLogic.toIO((VideoStreamReference) reference, kind());
  }

  /**
   * APISIMP-VIDEO-STREAMREF-PATH — two-step create, step 1.
   *
   * <p>Creates a metadata-only {@link VideoStreamReference} node with no bytes
   * attached yet. Accepts {@code {"name":"<filename>"}} in the body.
   * Follow up with {@code PUT /v2/references/{appId}/content?filename=…} to
   * attach the video bytes.
   */
  @Override
  public ReferenceV2IO create(String dataObjectAppId, Map<String, Object> body) {
    return VideoStreamReferenceKindHandlerLogic.create(dataObjectAppId, body, videoStreamReferenceService, kind());
  }

  @Override
  public ReferenceV2IO patch(String appId, Map<String, Object> patch) {
    return VideoStreamReferenceKindHandlerLogic.patch(appId, patch, videoStreamReferenceDAO, videoStreamReferenceService, userService, dateHelper, kind());
  }

  @Override
  public void delete(String appId) {
    VideoStreamReferenceKindHandlerLogic.delete(appId, videoStreamReferenceService);
  }

  @Override
  public List<ReferenceV2IO> listByDataObject(String dataObjectAppId, String subKind) {
    return VideoStreamReferenceKindHandlerLogic.listByDataObject(dataObjectAppId, subKind, videoStreamReferenceService, kind());
  }

  /**
   * APISIMP-VIDEO-STREAMREF-PATH — two-step create, step 2.
   *
   * <p>Attaches binary video bytes to an existing (bytes-free) reference node,
   * runs ffprobe best-effort, and updates the Neo4j fields.
   */
  @Override
  public ReferenceV2IO uploadContent(String appId, InputStream input, String filename, long declaredSize) {
    return VideoStreamReferenceKindHandlerLogic.uploadContent(appId, input, filename, declaredSize, videoStreamReferenceDAO, videoStreamReferenceService, kind());
  }

  @Override
  public Response downloadContent(String appId, String rangeHeader) {
    return VideoStreamReferenceKindHandlerLogic.downloadContent(appId, rangeHeader, null, videoStreamReferenceDAO, videoStreamReferenceService);
  }

  /**
   * VIDEO-HEVC-TRANSCODE-BACKFILL — when {@code prefer=source} is set, force-
   * serve the original (HEVC) source bytes; otherwise, prefer the browser-
   * friendly h.264 proxy when {@code proxyStatus = "READY"}.
   */
  @Override
  public Response downloadContent(String appId, String rangeHeader, String prefer) {
    return VideoStreamReferenceKindHandlerLogic.downloadContent(appId, rangeHeader, prefer, videoStreamReferenceDAO, videoStreamReferenceService);
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
  public long countAnnotations(String refAppId) {
    return videoAnnotationDAO.countByVideoReferenceAppId(refAppId);
  }

  @Override
  public List<Map<String, Object>> listAnnotations(String refAppId, long skip, int limit) {
    return videoAnnotationDAO.findByVideoReferenceAppId(refAppId, skip, limit).stream()
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
