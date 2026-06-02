package de.dlr.shepard.v2.video.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.util.HttpRangeUtil;
import de.dlr.shepard.context.references.videostreamreference.daos.VideoStreamReferenceDAO;
import de.dlr.shepard.context.references.videostreamreference.io.VideoStreamReferenceIO;
import de.dlr.shepard.context.references.videostreamreference.model.VideoStreamReference;
import de.dlr.shepard.context.references.videostreamreference.services.VideoStreamReferenceService;
import de.dlr.shepard.storage.StorageException;
import de.dlr.shepard.storage.StorageGetResponse;
import de.dlr.shepard.storage.StorageNotInstalledException;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/**
 * VID1a — {@code /v2/data-objects/{dataObjectAppId}/video-stream-references}
 * CRUD endpoints.
 *
 * <p>Auth: every endpoint resolves the parent {@code DataObject} appId from
 * the path, then asks {@link PermissionsService} — same pattern as
 * {@link de.dlr.shepard.v2.file.resources.FileReferenceV2Rest}.
 * Read permission required for {@code GET}; Write permission required for
 * {@code POST} and {@code DELETE}.
 *
 * <p>Note: This feature lands in-tree for VID1a to lean on the existing
 * FileStorage SPI + permission gate. Plugin extraction is post-VID1-series
 * work (per CLAUDE.md plugin-first rule §"New payload kinds").
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/data-objects/{dataObjectAppId}/video-stream-references")
@RequestScoped
@Tag(name = "Video stream references (v2)")
public class VideoStreamReferenceV2Rest {

  @Inject
  VideoStreamReferenceService videoStreamReferenceService;

  @Inject
  VideoStreamReferenceDAO videoStreamReferenceDAO;

  @Inject
  PermissionsService permissionsService;

  // ─── list ────────────────────────────────────────────────────────────────

  @GET
  @Operation(summary = "List all VideoStreamReferences for a DataObject (VID1a).")
  @APIResponse(
    responseCode = "200",
    content = @Content(
      schema = @Schema(type = SchemaType.ARRAY, implementation = VideoStreamReferenceIO.class)
    )
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No DataObject with that appId.")
  public Response list(
    @PathParam("dataObjectAppId") String dataObjectAppId,
    @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    Long doOgmId = videoStreamReferenceService.getDataObjectOgmId(dataObjectAppId);
    if (doOgmId == null) return Response.status(Response.Status.NOT_FOUND).build();

    // DataObjects have no own :Permissions node — walk up to the parent
    // Collection via the perm-walk helper. Gating on doOgmId directly
    // always 403'd because PermissionsDAO.findByEntityNeo4jId returns
    // null for DOs.
    if (!permissionsService.isAccessAllowedForDataObjectAppId(dataObjectAppId, AccessType.Read, caller)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    List<VideoStreamReferenceIO> result = videoStreamReferenceService
      .listByDataObject(dataObjectAppId)
      .stream()
      .map(VideoStreamReferenceIO::new)
      .toList();

    return Response.ok(result).build();
  }

  // ─── upload ──────────────────────────────────────────────────────────────

  @POST
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Operation(
    summary = "Upload a video file and create a VideoStreamReference.",
    description = "Multipart body. The 'file' part carries the video bytes. " +
    "The optional 'name' query parameter sets the Reference name (defaults to the uploaded filename). " +
    "ffprobe metadata is extracted server-side (best-effort; upload succeeds even if ffprobe is absent)."
  )
  @APIResponse(
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = VideoStreamReferenceIO.class))
  )
  @APIResponse(responseCode = "400", description = "Missing file part.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No DataObject with that appId.")
  @APIResponse(responseCode = "503", description = "No active file storage adapter configured.")
  public Response upload(
    @PathParam("dataObjectAppId") String dataObjectAppId,
    @QueryParam("name") String name,
    @RestForm("file") FileUpload upload,
    @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    if (upload == null || upload.uploadedFile() == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("file part is required").build();
    }

    Long doOgmId = videoStreamReferenceService.getDataObjectOgmId(dataObjectAppId);
    if (doOgmId == null) return Response.status(Response.Status.NOT_FOUND).build();

    // Walk DO → parent Collection for the perm check (see GET above).
    if (!permissionsService.isAccessAllowedForDataObjectAppId(dataObjectAppId, AccessType.Write, caller)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    String refName = (name != null && !name.isBlank()) ? name : upload.fileName();
    String fileName = upload.fileName();
    String mimeType = upload.contentType();

    File uploaded = upload.uploadedFile().toFile();
    Long contentLength = uploaded.length() > 0 ? uploaded.length() : null;

    try (InputStream is = new FileInputStream(uploaded)) {
      VideoStreamReference created = videoStreamReferenceService.create(
        dataObjectAppId, refName, fileName, mimeType, contentLength, is
      );
      return Response.status(Response.Status.CREATED).entity(new VideoStreamReferenceIO(created)).build();
    } catch (jakarta.ws.rs.NotFoundException nfe) {
      return Response.status(Response.Status.NOT_FOUND).build();
    } catch (StorageNotInstalledException ex) {
      return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity(ex.getMessage()).build();
    } catch (StorageException ex) {
      Log.errorf("VID1a upload: storage error — %s", ex.getMessage());
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
    } catch (IOException ex) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
    }
  }

  // ─── get one ─────────────────────────────────────────────────────────────

  @GET
  @Path("/{appId}")
  @Operation(summary = "Get one VideoStreamReference by appId (VID1a).")
  @APIResponse(
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = VideoStreamReferenceIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No VideoStreamReference with that appId, or DataObject mismatch.")
  public Response getOne(
    @PathParam("dataObjectAppId") String dataObjectAppId,
    @PathParam("appId") String appId,
    @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    VideoStreamReference ref = videoStreamReferenceDAO.findByAppId(appId);
    if (ref == null) return Response.status(Response.Status.NOT_FOUND).build();

    Response gate = checkParentAndAccess(ref, dataObjectAppId, AccessType.Read, caller);
    if (gate != null) return gate;

    return Response.ok(new VideoStreamReferenceIO(ref)).build();
  }

  // ─── delete ──────────────────────────────────────────────────────────────

  @DELETE
  @Path("/{appId}")
  @Operation(summary = "Delete a VideoStreamReference and its stored bytes (VID1a).")
  @APIResponse(responseCode = "204", description = "Deleted.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No VideoStreamReference with that appId, or DataObject mismatch.")
  public Response delete(
    @PathParam("dataObjectAppId") String dataObjectAppId,
    @PathParam("appId") String appId,
    @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    VideoStreamReference ref = videoStreamReferenceDAO.findByAppId(appId);
    if (ref == null) return Response.status(Response.Status.NOT_FOUND).build();

    Response gate = checkParentAndAccess(ref, dataObjectAppId, AccessType.Write, caller);
    if (gate != null) return gate;

    videoStreamReferenceService.delete(ref);
    return Response.noContent().build();
  }

  // ─── download ────────────────────────────────────────────────────────────

  @GET
  @Path("/{appId}/download")
  @Produces({ MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_JSON })
  @Operation(
    summary = "Download the raw video bytes for a VideoStreamReference (VID1a + MFFD-VIDEOREF-SCALE-1).",
    description =
      "Streams the video bytes stored for the `VideoStreamReference` identified by " +
      "`appId` (UUID v7). Designed for both download (`?download=1` + " +
      "`Content-Disposition: attachment`) and inline HTML5 `<video>` playback.\n\n" +
      "**Range requests:** a single `Range: bytes=START-END` header is honoured " +
      "(single-range only; multi-range and suffix-range are declined per HttpRangeUtil " +
      "semantics). A valid range returns HTTP 206 with `Content-Range` and " +
      "`Accept-Ranges: bytes` headers — this is what unlocks browser-native scrubbing " +
      "on multi-GB MP4s. An unsatisfiable range (start ≥ total) returns 416 with " +
      "`Content-Range: bytes */TOTAL`. The `Accept-Ranges: bytes` header is always " +
      "included in 200 responses so clients can probe range support.\n\n" +
      "**Auth:** Read permission on the parent DataObject. The browser cannot send a " +
      "custom `Authorization` header on `<video src>`, so the JWT may be supplied via " +
      "the `?access_token=…` query param fallback handled by `JWTFilter`.\n\n" +
      "**Content-Disposition:** set to `attachment; filename=\"<originalName>\"` for " +
      "explicit download UX. HTML5 `<video>` ignores this and plays inline anyway."
  )
  @APIResponse(
    responseCode = "200",
    description = "Full video bytes; `Accept-Ranges: bytes` indicates partial-content support.",
    content = @Content(
      mediaType = MediaType.APPLICATION_OCTET_STREAM,
      schema = @Schema(type = SchemaType.STRING, format = "binary")
    )
  )
  @APIResponse(
    responseCode = "206",
    description = "Partial content — single-range `Range: bytes=START-END` was honoured; `Content-Range` header is set."
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No VideoStreamReference with that appId, or bytes missing.")
  @APIResponse(responseCode = "416", description = "Range not satisfiable — start offset is beyond the file size; `Content-Range: bytes */TOTAL` is included.")
  @APIResponse(responseCode = "503", description = "No active file storage adapter configured.")
  public Response download(
    @PathParam("dataObjectAppId") String dataObjectAppId,
    @PathParam("appId") String appId,
    @HeaderParam("Range") String rangeHeader,
    @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    VideoStreamReference ref = videoStreamReferenceDAO.findByAppId(appId);
    if (ref == null) return Response.status(Response.Status.NOT_FOUND).build();

    Response gate = checkParentAndAccess(ref, dataObjectAppId, AccessType.Read, caller);
    if (gate != null) return gate;

    StorageGetResponse payload;
    try {
      payload = videoStreamReferenceService.getPayload(ref);
    } catch (jakarta.ws.rs.NotFoundException nfe) {
      return Response.status(Response.Status.NOT_FOUND).entity(nfe.getMessage()).build();
    } catch (StorageNotInstalledException ex) {
      return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity(ex.getMessage()).build();
    } catch (StorageException ex) {
      Log.errorf("VID1a download: storage error — %s", ex.getMessage());
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
    }

    // Determine filename for Content-Disposition.
    String filename = payload.fileName();
    if (filename == null || filename.isBlank()) {
      filename = ref.getName();
    }
    String contentType = payload.contentType() != null
      ? payload.contentType()
      : (ref.getMimeType() != null ? ref.getMimeType() : MediaType.APPLICATION_OCTET_STREAM);

    Long total = payload.sizeBytes() != null
      ? payload.sizeBytes()
      : (ref.getFileSizeBytes() != null ? ref.getFileSizeBytes() : null);

    // No range header → full body. Always advertise Accept-Ranges so HTML5
    // <video> can probe for range support and scrub natively.
    if (rangeHeader == null || rangeHeader.isBlank()) {
      Response.ResponseBuilder rb = Response.ok(payload.stream(), contentType)
        .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
        .header("Accept-Ranges", "bytes");
      if (total != null) {
        rb.header("Content-Length", total);
      }
      return rb.build();
    }

    // Range path. If we don't know the total size (legacy GridFS row without
    // bookkeeping), we can't satisfy a range — fall back to the full body.
    if (total == null || total <= 0) {
      Log.warnf(
        "VID1a download: Range header supplied but total size unknown — serving full body (appId=%s)",
        appId
      );
      return Response.ok(payload.stream(), contentType)
        .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
        .header("Accept-Ranges", "bytes")
        .build();
    }

    long[] range = HttpRangeUtil.parseRange(rangeHeader, total);
    if (range == null) {
      // Close the source stream so the underlying GridFS / S3 handle is
      // released — we are not going to consume it.
      try {
        if (payload.stream() != null) payload.stream().close();
      } catch (IOException ioe) {
        Log.debugf("VID1a download: close-after-416 failed (ignored): %s", ioe.getMessage());
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

  // ─── helpers ─────────────────────────────────────────────────────────────

  private String callerOrNull(SecurityContext sc) {
    return sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
  }

  /**
   * Validate that the reference's parent DataObject matches the URL's
   * {@code dataObjectAppId} and that the caller has the required access.
   *
   * @return {@code null} when access is granted; a short-circuit Response otherwise.
   */
  private Response checkParentAndAccess(
    VideoStreamReference ref,
    String dataObjectAppId,
    AccessType accessType,
    String caller
  ) {
    if (ref.getDataObject() == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    // Verify the reference belongs to the DataObject in the URL.
    String refParentAppId = ref.getDataObject().getAppId();
    if (refParentAppId != null && !refParentAppId.equals(dataObjectAppId)) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    // DataObjects have no own :Permissions node — walk up to the parent
    // Collection via the perm-walk helper. The 4-arg isAccessTypeAllowedForUser
    // always 403s for DOs because PermissionsDAO.findByEntityNeo4jId returns null.
    if (!permissionsService.isAccessAllowedForDataObjectAppId(dataObjectAppId, accessType, caller)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    return null;
  }
}
