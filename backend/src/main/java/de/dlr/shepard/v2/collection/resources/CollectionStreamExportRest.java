package de.dlr.shepard.v2.collection.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.daos.CollectionPropertiesDAO;
import de.dlr.shepard.context.export.ExportService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.InputStream;
import java.util.Optional;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * EXPORT-V2-STREAM — {@code GET /v2/collections/{appId}/export}.
 *
 * <p>Streams the collection's RO-Crate ZIP directly to the caller.
 * Works on all storage backends including GridFS, unlike
 * {@code POST /v2/collections/{appId}/export-url} (FS1g) which requires
 * presigned-URL support and returns 503 on GridFS. Resolves the numeric id
 * from the appId at call time — the numeric id never appears on the wire.
 *
 * <p>Replaces the v1 {@code GET /collections/{collectionId}/export} for this
 * fork's own frontend callers; the v1 path stays frozen for upstream clients.
 */
@Produces(MediaType.APPLICATION_JSON)
@Path("/v2/collections")
@RequestScoped
@Tag(name = "Collection stream export")
public class CollectionStreamExportRest {

  private static final String PT_UNAUTHORIZED = "/problems/collection-stream-export.unauthorized";
  private static final String PT_NOT_FOUND = "/problems/collection-stream-export.not-found";
  private static final String PT_FORBIDDEN = "/problems/collection-stream-export.forbidden";

  @Inject
  CollectionPropertiesDAO collectionPropertiesDAO;

  @Inject
  PermissionsService permissionsService;

  @Inject
  ExportService exportService;

  @GET
  @Path("/{appId}/export")
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  @Operation(
    summary = "Stream an RO-Crate ZIP for the collection directly.",
    description = "Builds the collection's RO-Crate ZIP export and streams it directly to the caller. " +
    "This endpoint works on all storage backends including GridFS. " +
    "To avoid streaming bytes through the JVM on object-storage deployments, " +
    "prefer POST /v2/collections/{appId}/export-url (returns a presigned S3 URL) when supported. " +
    "Requires Read permission on the collection."
  )
  @APIResponse(
    responseCode = "200",
    description = "RO-Crate ZIP, streamed as application/octet-stream.",
    content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM)
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the collection.")
  @APIResponse(responseCode = "404", description = "No collection with that appId.")
  public Response streamExport(
    @PathParam("appId") String collectionAppId,
    @Context SecurityContext securityContext
  ) {
    if (securityContext.getUserPrincipal() == null) {
      return problem(PT_UNAUTHORIZED, "Authentication required",
        Response.Status.UNAUTHORIZED, "caller identity unknown");
    }
    String caller = securityContext.getUserPrincipal().getName();

    Optional<Long> ogmId = collectionPropertiesDAO.findCollectionIdByAppId(collectionAppId);
    if (ogmId.isEmpty()) {
      return problem(PT_NOT_FOUND, "Collection not found",
        Response.Status.NOT_FOUND, "no Collection with appId '" + collectionAppId + "'");
    }

    if (!permissionsService.isAccessTypeAllowedForUser(ogmId.get(), AccessType.Read, caller, 0L)) {
      return problem(PT_FORBIDDEN, "Read access required",
        Response.Status.FORBIDDEN, "caller lacks Read on Collection '" + collectionAppId + "'");
    }

    String fileName = sanitizeFileName(collectionAppId) + "-export.zip";
    long oid = ogmId.get();
    StreamingOutput streaming = out -> {
      try (InputStream is = exportService.exportCollectionByShepardId(oid)) {
        is.transferTo(out);
      }
    };

    return Response.ok(streaming, MediaType.APPLICATION_OCTET_STREAM)
      .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
      .build();
  }

  private static String sanitizeFileName(String name) {
    return name.replaceAll("[^a-zA-Z0-9._-]", "_");
  }

  private static Response problem(String type, String title, Response.Status status, String detail) {
    return Response.status(status).type("application/problem+json")
      .entity(new ProblemJson(type, title, status.getStatusCode(), detail, null)).build();
  }
}
