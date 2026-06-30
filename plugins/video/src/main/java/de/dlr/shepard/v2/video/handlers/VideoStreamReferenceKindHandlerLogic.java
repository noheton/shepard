package de.dlr.shepard.v2.video.handlers;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.HttpRangeUtil;
import de.dlr.shepard.context.references.videostreamreference.daos.VideoStreamReferenceDAO;
import de.dlr.shepard.context.references.videostreamreference.model.VideoStreamReference;
import de.dlr.shepard.context.references.videostreamreference.services.VideoStreamReferenceService;
import de.dlr.shepard.storage.StorageException;
import de.dlr.shepard.storage.StorageGetResponse;
import de.dlr.shepard.storage.StorageNotInstalledException;
import de.dlr.shepard.v2.references.io.ReferenceV2IO;
import io.quarkus.logging.Log;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CRIT-QUARKUS-CLASSTRANSFORM-VIDEOPAYLOAD — package-private static helper.
 *
 * <p>Quarkus's {@code ClassTransformingBuildStep} uses
 * {@code ClassWriter(COMPUTE_FRAMES)}, which re-derives all method frames from
 * scratch for every CDI bean it processes (not just the methods Arc injects into).
 * When {@code computeAllFrames} encounters a frame-merge involving
 * {@code VideoStreamReference}, it calls {@code ClassWriter.getCommonSuperClass}
 * which must load {@code BasicReference} to walk the type hierarchy.
 * {@code BasicReference} lives in {@code backend.jar}, which is a
 * {@code provided}-scope dependency of the plugin and is absent from the
 * narrowed transformation class-loader — causing {@code NoClassDefFoundError}.
 *
 * <p>This class holds every method body that declares a {@code VideoStreamReference}
 * local variable (not merely a parameter). It carries <em>no</em> CDI annotations,
 * so Arc never registers a bytecode transformer for it and
 * {@code ClassTransformingBuildStep} skips it entirely.
 *
 * <p>{@link VideoStreamReferenceKindHandler} — a {@code @RequestScoped} CDI bean —
 * becomes a set of trivial one-line delegates whose own method frames contain no
 * {@code VideoStreamReference} locals, eliminating the problematic frame-merge.
 */
class VideoStreamReferenceKindHandlerLogic {

  private VideoStreamReferenceKindHandlerLogic() {}

  // ─── toIO ────────────────────────────────────────────────────────────────

  static ReferenceV2IO toIO(VideoStreamReference ref, String kind) {
    ReferenceV2IO io = new ReferenceV2IO(ref, kind);
    io.setReferenceShape("stream");
    io.put("storageLocator", ref.getStorageLocator());
    io.put("mimeType", ref.getMimeType());
    io.put("fileSize", ref.getFileSizeBytes());
    io.put("fileSizeBytes", ref.getFileSizeBytes());
    io.put("durationSeconds", ref.getDurationSeconds());
    io.put("width", ref.getWidth());
    io.put("height", ref.getHeight());
    io.put("frameRate", ref.getFrameRate());
    io.put("videoCodec", ref.getVideoCodec());
    io.put("audioCodec", ref.getAudioCodec());
    String proxyLocator = ref.getProxyStorageLocator();
    String proxyStatusRaw = ref.getProxyStatus();
    boolean proxyAvailable = "READY".equalsIgnoreCase(proxyStatusRaw)
      && proxyLocator != null && !proxyLocator.isBlank();
    io.put("proxyStatus", proxyStatusRaw);
    io.put("proxyAvailable", proxyAvailable);
    io.put("proxyOid", proxyLocator);
    io.put("wallClockTimestamp", ref.getWallClockTimestamp());
    return io;
  }

  // ─── create ──────────────────────────────────────────────────────────────

  static ReferenceV2IO create(
    String dataObjectAppId,
    Map<String, Object> body,
    VideoStreamReferenceService service,
    String kind
  ) {
    Object nameObj = body != null ? body.get("name") : null;
    if (!(nameObj instanceof String name) || name.isBlank()) {
      throw new BadRequestException("'name' is required and must be a non-blank string for kind=video");
    }
    VideoStreamReference created = service.createMetadataNode(dataObjectAppId, name);
    return toIO(created, kind);
  }

  // ─── patch ───────────────────────────────────────────────────────────────

  static ReferenceV2IO patch(
    String appId,
    Map<String, Object> patchBody,
    VideoStreamReferenceDAO dao,
    VideoStreamReferenceService service,
    UserService userService,
    DateHelper dateHelper,
    String kind
  ) {
    VideoStreamReference ref = service.findByAppId(appId);
    if (ref == null) throw new NotFoundException("No VideoStreamReference with appId " + appId);
    boolean changed = false;
    if (patchBody != null && patchBody.containsKey("name")) {
      Object v = patchBody.get("name");
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
      ref = dao.createOrUpdate(ref);
    }
    return toIO(ref, kind);
  }

  // ─── delete ──────────────────────────────────────────────────────────────

  static void delete(String appId, VideoStreamReferenceService service) {
    VideoStreamReference ref = service.findByAppId(appId);
    if (ref == null) throw new NotFoundException("No VideoStreamReference with appId " + appId);
    service.delete(ref);
  }

  // ─── listByDataObject ────────────────────────────────────────────────────

  static List<ReferenceV2IO> listByDataObject(
    String dataObjectAppId,
    String subKind,
    VideoStreamReferenceService service,
    String kind
  ) {
    if (dataObjectAppId == null || dataObjectAppId.isBlank()) {
      throw new BadRequestException("dataObjectAppId is required");
    }
    List<VideoStreamReference> refs = service.listByDataObject(dataObjectAppId);
    List<ReferenceV2IO> out = new ArrayList<>(refs.size());
    for (VideoStreamReference ref : refs) {
      if (ref != null && !ref.isDeleted()) out.add(toIO(ref, kind));
    }
    return out;
  }

  // ─── uploadContent ───────────────────────────────────────────────────────

  static ReferenceV2IO uploadContent(
    String appId,
    InputStream input,
    String filename,
    long declaredSize,
    VideoStreamReferenceDAO dao,
    VideoStreamReferenceService service,
    String kind
  ) {
    VideoStreamReference ref = dao.findByAppId(appId);
    if (ref == null || ref.isDeleted()) throw new NotFoundException("No VideoStreamReference with appId " + appId);
    String mimeType = detectMimeType(filename);
    try {
      VideoStreamReference updated = service.attachBytes(ref, filename, mimeType, declaredSize, input);
      return toIO(updated, kind);
    } catch (StorageNotInstalledException ex) {
      throw new jakarta.ws.rs.ServiceUnavailableException(ex.getMessage());
    } catch (StorageException ex) {
      Log.errorf("VID1a/uploadContent: storage error appId=%s: %s", appId, ex.getMessage());
      throw new jakarta.ws.rs.InternalServerErrorException(ex.getMessage());
    }
  }

  // ─── downloadContent ─────────────────────────────────────────────────────

  static Response downloadContent(
    String appId,
    String rangeHeader,
    String prefer,
    VideoStreamReferenceDAO dao,
    VideoStreamReferenceService service
  ) {
    VideoStreamReference ref = dao.findByAppId(appId);
    if (ref == null || ref.isDeleted()) {
      return problem(Response.Status.NOT_FOUND, "No VideoStreamReference with appId " + appId);
    }
    boolean preferSource = prefer != null && prefer.equalsIgnoreCase("source");
    StorageGetResponse payload;
    try {
      payload = service.getPayload(ref, preferSource);
    } catch (NotFoundException nfe) {
      return problem(Response.Status.NOT_FOUND, nfe.getMessage());
    } catch (StorageNotInstalledException ex) {
      return problem(Response.Status.SERVICE_UNAVAILABLE, ex.getMessage());
    } catch (StorageException ex) {
      Log.errorf("VID1a/downloadContent: storage error appId=%s: %s", appId, ex.getMessage());
      return problem(Response.Status.INTERNAL_SERVER_ERROR, ex.getMessage());
    }

    String filename = payload.fileName() != null && !payload.fileName().isBlank()
      ? payload.fileName() : ref.getName();
    String contentType = payload.contentType() != null
      ? payload.contentType()
      : (ref.getMimeType() != null ? ref.getMimeType() : MediaType.APPLICATION_OCTET_STREAM);
    Long total = payload.sizeBytes() != null ? payload.sizeBytes() : ref.getFileSizeBytes();

    if (rangeHeader == null || rangeHeader.isBlank()) {
      Response.ResponseBuilder rb = Response.ok(payload.stream(), contentType)
        .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
        .header("Accept-Ranges", "bytes");
      if (total != null) rb.header("Content-Length", total);
      return rb.build();
    }

    if (total == null || total <= 0) {
      Log.warnf("VID1a/downloadContent: Range header present but total size unknown — serving full body (appId={})", appId);
      return Response.ok(payload.stream(), contentType)
        .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
        .header("Accept-Ranges", "bytes")
        .build();
    }

    long[] range = HttpRangeUtil.parseRange(rangeHeader, total);
    if (range == null) {
      try { if (payload.stream() != null) payload.stream().close(); } catch (IOException ioe) { /* ignore */ }
      return Response.status(Response.Status.REQUESTED_RANGE_NOT_SATISFIABLE)
        .header("Content-Range", "bytes */" + total)
        .header("Accept-Ranges", "bytes")
        .build();
    }
    long start = range[0];
    long end = range[1];
    long length = end - start + 1;
    StreamingOutput ranged = HttpRangeUtil.sliceStream(payload.stream(), start, length);
    return Response.status(Response.Status.PARTIAL_CONTENT)
      .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
      .header("Content-Length", length)
      .header("Content-Range", "bytes " + start + "-" + end + "/" + total)
      .header("Accept-Ranges", "bytes")
      .entity(ranged)
      .type(contentType)
      .build();
  }

  // ─── private helpers ─────────────────────────────────────────────────────

  static Response problem(Response.Status status, String detail) {
    String type = switch (status) {
      case NOT_FOUND -> "urn:shepard:error:not-found";
      case SERVICE_UNAVAILABLE -> "urn:shepard:error:service-unavailable";
      default -> "urn:shepard:error:internal";
    };
    return Response.status(status)
      .type("application/problem+json")
      .entity(new de.dlr.shepard.common.exceptions.ProblemJson(type, status.getReasonPhrase(), status.getStatusCode(), detail, null))
      .build();
  }

  static String detectMimeType(String filename) {
    if (filename == null) return MediaType.APPLICATION_OCTET_STREAM;
    String lower = filename.toLowerCase();
    if (lower.endsWith(".mp4") || lower.endsWith(".m4v")) return "video/mp4";
    if (lower.endsWith(".mov")) return "video/quicktime";
    if (lower.endsWith(".avi")) return "video/x-msvideo";
    if (lower.endsWith(".mkv")) return "video/x-matroska";
    if (lower.endsWith(".webm")) return "video/webm";
    if (lower.endsWith(".ts")) return "video/mp2t";
    return MediaType.APPLICATION_OCTET_STREAM;
  }
}
