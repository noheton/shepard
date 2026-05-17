package de.dlr.shepard.v2.structureddatacontainer.resources;

import de.dlr.shepard.data.file.daos.PayloadVersionDAO;
import de.dlr.shepard.data.file.entities.PayloadVersion;
import de.dlr.shepard.data.structureddata.services.StructuredDataContainerService;
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
 * PV1b — REST surface for per-entry payload versioning on StructuredDataContainers.
 *
 * <p>Route:
 * <ul>
 *   <li>{@code GET /v2/structured-data-containers/{containerAppId}/files/{originalName}/versions}
 *       — returns the full version history for the named structured-data entry inside
 *       the container, ordered by {@code versionNumber} ascending (oldest first).</li>
 * </ul>
 *
 * <p>The {@code originalName} corresponds to {@link de.dlr.shepard.data.structureddata.entities.StructuredData#getName()};
 * entries uploaded without an explicit name use their Mongo oid as the name.
 *
 * <p>Read permission on the container is enforced by
 * {@link StructuredDataContainerService#getContainerByAppId(String)}, which throws
 * {@code InvalidAuthException} (→ 403) when the caller lacks read access.
 *
 * <p>The shared {@link PayloadVersionDAO} and {@link de.dlr.shepard.v2.file.io.PayloadVersionIO}
 * wire shape are reused from PV1a; no new Neo4j entities or migrations are needed.
 *
 * <p>Cross-references: {@code aidocs/data/46} PV1b scope; {@code aidocs/16} PV1b row.
 */
@Produces(MediaType.APPLICATION_JSON)
@Path("/v2/structured-data-containers")
@RequestScoped
@Tag(name = "Structured data containers — payload versioning (PV1b)")
public class StructuredDataPayloadVersionRest {

  @Inject
  StructuredDataContainerService structuredDataContainerService;

  @Inject
  PayloadVersionDAO payloadVersionDAO;

  /**
   * List all payload versions for the named structured-data entry in the given container.
   *
   * <p>Versions are returned in ascending {@code versionNumber} order (oldest first).
   * An empty array is returned when no versions have been recorded yet (e.g. entries
   * created before PV1b shipped, or containers that do not yet have an {@code appId}).
   *
   * @param containerAppId UUID v7 of the StructuredDataContainer.
   * @param originalName   The structured-data entry name as supplied at upload time
   *                       (or the Mongo oid for anonymous entries).
   * @return 200 with the version list, or 403/404 on access/not-found errors.
   */
  @GET
  @Path("/{containerAppId}/files/{originalName}/versions")
  @Operation(
    summary = "List all payload versions for a named structured-data entry.",
    description = "Returns the full upload history for the specified entry name within the container, " +
    "ordered by versionNumber ascending (oldest first). " +
    "The originalName corresponds to StructuredData.name; anonymous entries use their Mongo oid as the name. " +
    "Requires Read permission on the container."
  )
  @APIResponse(
    responseCode = "200",
    description = "Version list (may be empty).",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = PayloadVersionIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the container.")
  @APIResponse(responseCode = "404", description = "No StructuredDataContainer with that appId.")
  public Response listVersions(
    @PathParam("containerAppId") String containerAppId,
    @PathParam("originalName") String originalName
  ) {
    // Permission check: getContainerByAppId asserts Read and throws
    // InvalidAuthException (403) or InvalidPathException (404) on failure.
    structuredDataContainerService.getContainerByAppId(containerAppId);

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
