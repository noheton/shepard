package de.dlr.shepard.v2.file.resources;

import de.dlr.shepard.data.file.daos.PayloadVersionDAO;
import de.dlr.shepard.data.file.entities.PayloadVersion;
import de.dlr.shepard.data.file.services.FileContainerService;
import de.dlr.shepard.v2.file.io.PayloadVersionIO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * PV1a — REST surface for per-file payload versioning.
 *
 * <p>Route:
 * <ul>
 *   <li>{@code GET /v2/file-containers/{containerAppId}/files/{originalName}/versions}
 *       — returns the full version history for the named file inside the
 *       container, ordered by {@code versionNumber} ascending.</li>
 * </ul>
 *
 * <p>Read permission on the container is enforced by
 * {@link FileContainerService#getContainerByAppId(String)}, which throws
 * {@code InvalidAuthException} (→ 403) when the caller lacks read access.
 *
 * <p>Cross-references: {@code aidocs/data/46} PV1a scope;
 * {@code aidocs/16} PV1a row.
 */
@Produces(MediaType.APPLICATION_JSON)
@Path("/v2/file-containers")
@RequestScoped
@Tag(name = "File containers — payload versioning (PV1a)")
public class PayloadVersionRest {

  @Inject
  FileContainerService fileContainerService;

  @Inject
  PayloadVersionDAO payloadVersionDAO;

  /**
   * List all payload versions for the named file in the given container.
   *
   * <p>Versions are returned in ascending {@code versionNumber} order
   * (oldest first). An empty array is returned when no versions exist for
   * the file yet (e.g. the file was committed via a presigned URL path before
   * PV1a shipped).
   *
   * @param containerAppId UUID v7 of the FileContainer.
   * @param originalName   The file name as supplied at upload time.
   * @return 200 with the version list, or 403/404 on access/not-found errors.
   */
  @GET
  @Path("/{containerAppId}/files/{originalName}/versions")
  @Operation(
    summary = "List all payload versions for a named file.",
    description = "Returns the full upload history for the specified file name within the container, " +
    "ordered by versionNumber ascending (oldest first). " +
    "Requires Read permission on the container."
  )
  @APIResponse(
    responseCode = "200",
    description = "Version list (may be empty).",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = PayloadVersionIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the container.")
  @APIResponse(responseCode = "404", description = "No FileContainer with that appId.")
  public Response listVersions(
    @PathParam("containerAppId") String containerAppId,
    @PathParam("originalName") String originalName
  ) {
    // Permission check: getContainerByAppId asserts Read and throws
    // InvalidAuthException (403) or InvalidPathException (404) on failure.
    fileContainerService.getContainerByAppId(containerAppId);

    List<PayloadVersion> versions = payloadVersionDAO.findByContainerAndName(containerAppId, originalName);
    List<PayloadVersionIO> body = versions.stream()
      .map(v -> new PayloadVersionIO(
        v.getAppId(),
        v.getVersionNumber(),
        v.getFileOid(),
        v.getSha256(),
        v.getSizeBytes(),
        v.getUploadedBy(),
        v.getUploadedAt()
      ))
      .toList();
    return Response.ok(body).build();
  }
}
