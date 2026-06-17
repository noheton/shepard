package de.dlr.shepard.v2.video.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.util.HttpRangeUtil;
import de.dlr.shepard.context.references.videostreamreference.daos.VideoStreamReferenceDAO;
import de.dlr.shepard.context.references.videostreamreference.model.VideoStreamReference;
import de.dlr.shepard.context.references.videostreamreference.services.VideoStreamReferenceService;
import de.dlr.shepard.storage.StorageException;
import de.dlr.shepard.storage.StorageGetResponse;
import de.dlr.shepard.storage.StorageNotInstalledException;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.IOException;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * APISIMP-VIDEO-STREAMREF-PATH — range-aware video download mounted on the
 * unified {@code /v2/references} surface.
 *
 * <p>The old path {@code GET /v2/data-objects/{doAppId}/video-stream-references/{appId}/download}
 * returns 410 Gone and directs callers here.
 *
 * <p>Auth: Read permission on the reference's parent DataObject, resolved from
 * the loaded {@link VideoStreamReference} entity (no {@code dataObjectAppId}
 * needed in the URL — the appId is sufficient to locate the parent).
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/references")
@RequestScoped
@Tag(name = "Video stream references (v2)")
public class VideoStreamReferenceActionsRest {

  @Inject
  VideoStreamReferenceService videoStreamReferenceService;

  @Inject
  VideoStreamReferenceDAO videoStreamReferenceDAO;

  @Inject
  PermissionsService permissionsService;

  /**
   * Download the raw video bytes for a VideoStreamReference (APISIMP-VIDEO-STREAMREF-PATH).
   *
   * <p>Streams the video bytes stored for the {@code VideoStreamReference} identified
   * by {@code appId} (UUID v7). Designed for both file download and inline HTML5
   * {@code <video>} playback.
   *
   * <p>**Range requests:** a single {@code Range: bytes=START-END} header is honoured
   * (single-range only; multi-range and suffix-range are declined). A valid range returns
   * HTTP 206 with {@code Content-Range} and {@code Accept-Ranges: bytes} headers —
   * enabling browser-native scrubbing on multi-GB MP4s. An unsatisfiable range returns 416.
   *
   * <p>**Auth:** Read permission on the parent DataObject. The browser cannot send a
   * custom {@code Authorization} header on {@code <video src>}, so the JWT may be supplied
   * via the {@code ?access_token=…} query param fallback handled by {@code JWTFilter}.
   */
  @GET
  @Path("/{appId}/download")
  @Produces({ MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_JSON })
  @Operation(
    operationId = "downloadVideoStreamReferenceContent",
    summary = "Stream video bytes for a VideoStreamReference (MFFD-VIDEOREF-SCALE-1 range support).",
    description =
      "Streams the stored video bytes for the `VideoStreamReference` at `appId`. " +
      "A single `Range: bytes=START-END` header is honoured (HTTP 206 partial content) — " +
      "enabling browser-native scrubbing on multi-GB MP4s. " +
      "The `Accept-Ranges: bytes` header is always included in 200 responses. " +
      "Auth: Read on the parent DataObject (JWT via `Authorization` header or " +
      "`?access_token=` query param for `<video src>` contexts)."
  )
  @APIResponse(
    responseCode = "200",
    description = "Full video bytes; `Accept-Ranges: bytes` indicates range support.",
    content = @Content(
      mediaType = MediaType.APPLICATION_OCTET_STREAM,
      schema = @Schema(type = SchemaType.STRING, format = "binary")
    )
  )
  @APIResponse(
    responseCode = "206",
    description = "Partial content — single-range `Range: bytes=START-END` was honoured."
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No VideoStreamReference with that appId, or bytes not yet uploaded.")
  @APIResponse(responseCode = "416", description = "Range not satisfiable — `Content-Range: bytes */TOTAL` is included.")
  @APIResponse(responseCode = "503", description = "No active file storage adapter configured.")
  public Response download(
    @PathParam("appId") String appId,
    @HeaderParam("Range") String rangeHeader,
    @Context SecurityContext sc
  ) {
    String caller = sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) return problem(Response.Status.UNAUTHORIZED, "Authentication required");

    VideoStreamReference ref = videoStreamReferenceDAO.findByAppId(appId);
    if (ref == null || ref.isDeleted()) return problem(Response.Status.NOT_FOUND, "VideoStreamReference not found");

    // Gate on parent DataObject resolved from the entity itself.
    if (ref.getDataObject() == null) return problem(Response.Status.NOT_FOUND, "VideoStreamReference not found");
    String doAppId = ref.getDataObject().getAppId();
    if (doAppId == null || !permissionsService.isAccessAllowedForDataObjectAppId(doAppId, AccessType.Read, caller)) {
      return problem(Response.Status.FORBIDDEN, "Insufficient permissions");
    }

    StorageGetResponse payload;
    try {
      payload = videoStreamReferenceService.getPayload(ref);
    } catch (jakarta.ws.rs.NotFoundException nfe) {
      return problem(Response.Status.NOT_FOUND, nfe.getMessage());
    } catch (StorageNotInstalledException ex) {
      return problem(Response.Status.SERVICE_UNAVAILABLE, ex.getMessage());
    } catch (StorageException ex) {
      Log.errorf("VID/download: storage error appId=%s — %s", appId, ex.getMessage());
      return problem(Response.Status.INTERNAL_SERVER_ERROR, ex.getMessage());
    }

    String filename = payload.fileName();
    if (filename == null || filename.isBlank()) filename = ref.getName();
    String contentType = payload.contentType() != null
      ? payload.contentType()
      : (ref.getMimeType() != null ? ref.getMimeType() : MediaType.APPLICATION_OCTET_STREAM);
    Long total = payload.sizeBytes() != null
      ? payload.sizeBytes()
      : ref.getFileSizeBytes();

    if (rangeHeader == null || rangeHeader.isBlank()) {
      Response.ResponseBuilder rb = Response.ok(payload.stream(), contentType)
        .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
        .header("Accept-Ranges", "bytes");
      if (total != null) rb.header("Content-Length", total);
      return rb.build();
    }

    if (total == null || total <= 0) {
      Log.warnf("VID/download: Range header but total size unknown — full body (appId=%s)", appId);
      return Response.ok(payload.stream(), contentType)
        .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
        .header("Accept-Ranges", "bytes")
        .build();
    }

    long[] range = HttpRangeUtil.parseRange(rangeHeader, total);
    if (range == null) {
      try {
        if (payload.stream() != null) payload.stream().close();
      } catch (IOException ioe) {
        Log.debugf("VID/download: close-after-416 failed (ignored): %s", ioe.getMessage());
      }
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

  private static Response problem(Response.Status status, String detail) {
    String type = switch (status) {
      case UNAUTHORIZED -> "urn:shepard:error:unauthorized";
      case FORBIDDEN -> "urn:shepard:error:forbidden";
      case NOT_FOUND -> "urn:shepard:error:not-found";
      case SERVICE_UNAVAILABLE -> "urn:shepard:error:service-unavailable";
      default -> "urn:shepard:error:internal";
    };
    return Response.status(status)
      .type("application/problem+json")
      .entity(new ProblemJson(type, status.getReasonPhrase(), status.getStatusCode(), detail, null))
      .build();
  }
}
