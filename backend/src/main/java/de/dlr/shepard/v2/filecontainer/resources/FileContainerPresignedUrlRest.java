package de.dlr.shepard.v2.filecontainer.resources;

import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.file.entities.ShepardFile;
import de.dlr.shepard.data.file.services.FileContainerService;
import de.dlr.shepard.storage.FileStorage;
import de.dlr.shepard.storage.PresignTtlValidator;
import de.dlr.shepard.storage.StorageException;
import de.dlr.shepard.v2.filecontainer.io.PresignedDownloadUrlIO;
import de.dlr.shepard.v2.filecontainer.io.PresignedUploadRequestIO;
import de.dlr.shepard.v2.filecontainer.io.PresignedUploadUrlIO;
import de.dlr.shepard.v2.filecontainer.io.UploadCommitIO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * FS1c — presigned-URL endpoints for the FileContainer payload kind.
 *
 * <p>Routes:
 * <ul>
 *   <li>{@code POST /v2/file-containers/{containerAppId}/upload-url}
 *       — obtain a presigned PUT URL to upload a file directly to
 *       the storage backend (S3-compatible). Returns the URL plus
 *       the {@code oid} (UUID) assigned to this object.</li>
 *   <li>{@code POST /v2/file-containers/{containerAppId}/upload-url/commit}
 *       — after the PUT upload completes, call this endpoint to
 *       register the file in shepard (creates the Neo4j
 *       {@link ShepardFile} node and attaches it to the container).
 *       </li>
 *   <li>{@code GET /v2/file-containers/{containerAppId}/files/{oid}/download-url}
 *       — obtain a presigned GET URL to download a file directly
 *       from the storage backend.</li>
 * </ul>
 *
 * <p>These endpoints require the active storage provider to support
 * presigned URLs (currently only the {@code s3} adapter from FS1b
 * does). The GridFS default adapter returns 503 "not supported".
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/file-containers")
@RequestScoped
@Tag(name = "File containers — presigned URLs (FS1c)")
public class FileContainerPresignedUrlRest {

  @Inject
  FileContainerService fileContainerService;

  @Inject
  PresignTtlValidator ttlValidator;

  // ─── upload-url ───────────────────────────────────────────────────────────

  @POST
  @Path("/{containerAppId}/upload-url")
  @Operation(
    summary = "Obtain a presigned PUT URL to upload a file to S3 directly.",
    description = "Returns a short-lived PUT URL (15 minutes) and the assigned oid (UUID). " +
    "Upload the file bytes with a single HTTP PUT to uploadUrl, then call " +
    "POST .../upload-url/commit with the oid to register the file in shepard. " +
    "Requires Write permission on the container."
  )
  @RequestBody(
    description = "File name and optional content type.",
    content = @Content(schema = @Schema(implementation = PresignedUploadRequestIO.class))
  )
  @APIResponse(
    responseCode = "200",
    description = "Presigned upload URL + assigned oid.",
    content = @Content(schema = @Schema(implementation = PresignedUploadUrlIO.class))
  )
  @APIResponse(responseCode = "400", description = "Missing fileName.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the container.")
  @APIResponse(responseCode = "404", description = "No FileContainer with that appId.")
  @APIResponse(responseCode = "503", description = "Active storage provider does not support presigned uploads.")
  public Response getUploadUrl(
    @PathParam("containerAppId") String containerAppId,
    PresignedUploadRequestIO request
  ) {
    if (request == null || request.getFileName() == null || request.getFileName().isBlank()) {
      throw new BadRequestException("fileName is required");
    }
    FileContainer container = fileContainerService.getContainerByAppId(containerAppId);
    FileStorage.PresignedPut result;
    try {
      result = fileContainerService.presignedUploadUrl(container.getId(), request.getFileName(), ttlValidator.effectiveUploadTtl());
    } catch (StorageException se) {
      Log.errorf("presignedUploadUrl failed for container %s: %s", containerAppId, se.getMessage());
      throw new InternalServerErrorException("Storage error: " + se.getMessage());
    }
    return Response.ok(new PresignedUploadUrlIO(
      result.uploadUrl().toString(),
      result.assignedOid(),
      result.expiresAt()
    )).build();
  }

  @POST
  @Path("/{containerAppId}/upload-url/commit")
  @Operation(
    summary = "Register a file that was uploaded via presigned PUT.",
    description = "After the PUT upload to the presigned URL completes, call this endpoint to " +
    "create the ShepardFile metadata record and attach it to the container. " +
    "Requires Write permission on the container."
  )
  @RequestBody(
    description = "The oid from the upload-url response, plus file metadata.",
    content = @Content(schema = @Schema(implementation = UploadCommitIO.class))
  )
  @APIResponse(
    responseCode = "201",
    description = "File registered successfully.",
    content = @Content(schema = @Schema(implementation = ShepardFile.class))
  )
  @APIResponse(responseCode = "400", description = "Missing oid or fileName.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the container.")
  @APIResponse(responseCode = "404", description = "No FileContainer with that appId.")
  public Response commitUpload(
    @PathParam("containerAppId") String containerAppId,
    UploadCommitIO commit
  ) {
    if (commit == null || commit.getOid() == null || commit.getOid().isBlank()) {
      throw new BadRequestException("oid is required");
    }
    if (commit.getFileName() == null || commit.getFileName().isBlank()) {
      throw new BadRequestException("fileName is required");
    }
    FileContainer container = fileContainerService.getContainerByAppId(containerAppId);
    ShepardFile file;
    try {
      file = fileContainerService.commitUpload(
        container.getId(),
        commit.getOid(),
        commit.getFileName(),
        commit.getFileSize()
      );
    } catch (StorageException se) {
      Log.errorf("commitUpload failed for container %s oid %s: %s", containerAppId, commit.getOid(), se.getMessage());
      throw new InternalServerErrorException("Storage error: " + se.getMessage());
    }
    return Response.status(Response.Status.CREATED).entity(file).build();
  }

  // ─── download-url ─────────────────────────────────────────────────────────

  @GET
  @Path("/{containerAppId}/files/{oid}/download-url")
  @Operation(
    summary = "Obtain a presigned GET URL to download a file directly from S3.",
    description = "Returns a short-lived GET URL (5 minutes). Issue an HTTP GET to downloadUrl " +
    "to retrieve the file bytes directly from storage — no auth headers required. " +
    "Requires Read permission on the container."
  )
  @APIResponse(
    responseCode = "200",
    description = "Presigned download URL.",
    content = @Content(schema = @Schema(implementation = PresignedDownloadUrlIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the container.")
  @APIResponse(responseCode = "404", description = "No FileContainer or file with that id.")
  @APIResponse(responseCode = "503", description = "Active storage provider does not support presigned downloads.")
  public Response getDownloadUrl(
    @PathParam("containerAppId") String containerAppId,
    @PathParam("oid") String oid
  ) {
    FileContainer container = fileContainerService.getContainerByAppId(containerAppId);
    URI downloadUrl;
    try {
      downloadUrl = fileContainerService.presignedDownloadUrl(container.getId(), oid, ttlValidator.effectiveDownloadTtl());
    } catch (StorageException se) {
      Log.errorf("presignedDownloadUrl failed for container %s oid %s: %s", containerAppId, oid, se.getMessage());
      throw new InternalServerErrorException("Storage error: " + se.getMessage());
    }
    return Response.ok(new PresignedDownloadUrlIO(
      downloadUrl.toString(),
      java.time.Instant.now().plus(ttlValidator.effectiveDownloadTtl())
    )).build();
  }
}
