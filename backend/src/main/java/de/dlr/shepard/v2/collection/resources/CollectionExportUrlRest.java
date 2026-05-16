package de.dlr.shepard.v2.collection.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.daos.CollectionPropertiesDAO;
import de.dlr.shepard.context.export.ExportSelection;
import de.dlr.shepard.context.export.ExportService;
import de.dlr.shepard.storage.FileStorage;
import de.dlr.shepard.storage.FileStorageRegistry;
import de.dlr.shepard.storage.StorageException;
import de.dlr.shepard.v2.collection.io.ExportUrlIO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * FS1g — {@code POST /v2/collections/{appId}/export-url}.
 *
 * <p>Builds an RO-Crate ZIP for the collection, uploads it to S3 under
 * {@code exports/<uuid>.zip}, and returns a presigned GET URL so the
 * client downloads the ZIP directly from the storage backend — the JVM
 * is not in the download path.
 *
 * <p>This is the synchronous variant of {@code aidocs/31 §O3}: the ZIP
 * is built in-request (blocking). The async variant (POST + 202 + jobId)
 * is deferred until O2 (async export jobs) lands. The actual win here
 * is offloading download bandwidth from the JVM to S3, which matters
 * most when many clients concurrently download recent exports.
 *
 * <p>Returns 503 when the active storage backend does not support
 * presigned export (GridFS). Clients should fall back to
 * {@code GET /collections/{id}/export}.
 *
 * <p>Export objects accumulate in S3 until cleaned up by a bucket
 * lifecycle rule; see {@code docs/reference/file-storage.md §Export URL lifecycle}.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/collections")
@RequestScoped
@Tag(name = "Collections — presigned export URL (FS1g)")
public class CollectionExportUrlRest {

  static final Duration EXPORT_DOWNLOAD_TTL = Duration.ofMinutes(30);

  @Inject
  CollectionPropertiesDAO collectionPropertiesDAO;

  @Inject
  PermissionsService permissionsService;

  @Inject
  ExportService exportService;

  @Inject
  FileStorageRegistry fileStorageRegistry;

  @POST
  @Path("/{appId}/export-url")
  @Operation(
    summary = "Build and upload an RO-Crate ZIP to S3, returning a presigned download URL.",
    description = "Builds the collection's RO-Crate ZIP export, uploads it to the active " +
    "S3-compatible storage backend under exports/<uuid>.zip, and returns a presigned GET URL " +
    "valid for 30 minutes. The client downloads the ZIP directly from S3 — no bytes are " +
    "streamed through the shepard backend during the download. " +
    "Returns 503 when the active storage provider does not support presigned export (GridFS); " +
    "callers should fall back to GET /collections/{collectionId}/export. " +
    "Requires Read permission on the collection."
  )
  @RequestBody(
    description = "Optional export selection filter. Omit or send {} for a full export.",
    content = @Content(schema = @Schema(implementation = ExportSelection.class))
  )
  @APIResponse(
    responseCode = "200",
    description = "Presigned download URL for the RO-Crate ZIP.",
    content = @Content(schema = @Schema(implementation = ExportUrlIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the collection.")
  @APIResponse(responseCode = "404", description = "No collection with that appId.")
  @APIResponse(
    responseCode = "503",
    description = "Active storage backend does not support presigned export. " +
    "Use GET /collections/{collectionId}/export for direct streaming."
  )
  public Response getExportUrl(
    @PathParam("appId") String collectionAppId,
    @Context SecurityContext securityContext,
    ExportSelection selection
  ) {
    if (securityContext.getUserPrincipal() == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    String caller = securityContext.getUserPrincipal().getName();

    Optional<Long> ogmId = collectionPropertiesDAO.findCollectionIdByAppId(collectionAppId);
    if (ogmId.isEmpty()) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    if (!permissionsService.isAccessTypeAllowedForUser(ogmId.get(), AccessType.Read, caller)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    FileStorage storage = fileStorageRegistry.activeStorage().orElse(null);
    if (storage == null) {
      throw new ServiceUnavailableException(
        "No active storage provider — configure shepard.storage.provider"
      );
    }

    byte[] zipBytes;
    try {
      zipBytes = exportService.exportCollectionByShepardId(ogmId.get(), selection).readAllBytes();
    } catch (IOException e) {
      Log.errorf("Export ZIP build failed for collection %s: %s", collectionAppId, e.getMessage());
      throw new InternalServerErrorException("Export build failed: " + e.getMessage());
    }

    String exportKey = UUID.randomUUID().toString();
    String fileName = sanitizeFileName(collectionAppId) + "-export.zip";

    Optional<URI> presigned;
    try {
      presigned = storage.presignedExportUrl(exportKey, zipBytes, fileName, EXPORT_DOWNLOAD_TTL);
    } catch (StorageException e) {
      Log.errorf("presignedExportUrl failed for collection %s: %s", collectionAppId, e.getMessage());
      throw new InternalServerErrorException("Storage error: " + e.getMessage());
    }

    if (presigned.isEmpty()) {
      throw new ServiceUnavailableException(
        "Active storage provider '" + storage.id() + "' does not support presigned export. " +
        "Use GET /collections/{collectionId}/export for direct streaming."
      );
    }

    Instant expiresAt = Instant.now().plus(EXPORT_DOWNLOAD_TTL);
    return Response.ok(new ExportUrlIO(presigned.get().toString(), fileName, expiresAt)).build();
  }

  private static String sanitizeFileName(String name) {
    return name.replaceAll("[^a-zA-Z0-9._-]", "_");
  }
}
