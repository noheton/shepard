package de.dlr.shepard.v2.export.rep;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.daos.CollectionPropertiesDAO;
import io.quarkus.logging.Log;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.Optional;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * TPL14 — Regulatory Evidence Pack (REP) export.
 *
 * <p>{@code POST /v2/collections/{appId}/export/regulatory-evidence} — builds a
 * BagIt-packaged RO-Crate + PROV-O bundle for the named Collection and returns
 * it inline (Base64-encoded, ≤ 1 MB bags) or as a download URL for larger bags.
 *
 * <p>{@code GET /v2/collections/{appId}/export/regulatory-evidence/latest} — stub
 * returning 404 until a persistence layer is wired (TPL14b). Placed here so
 * the path is reserved and clients can adopt it.
 *
 * <p>Permission gating (MCP-PERMS-AUDIT-2, REST flip 2026-05-31):
 * <ul>
 *   <li>POST {@code .../regulatory-evidence} — requires <b>Write</b> on the
 *       Collection. Builds a REP bag and records a Collection-scoped PROV-O
 *       Activity ("REP exported by user X"). The MCP {@code rep_export} tool
 *       already gates Write; this REST flip aligns the two surfaces.</li>
 *   <li>GET {@code .../regulatory-evidence/latest} — Read suffices (no
 *       Activity recorded, no state mutated).</li>
 * </ul>
 */
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Path("/v2/collections")
@RequestScoped
@Tag(name = "Collections")
public class RepExportV2Rest {

  @Inject
  CollectionPropertiesDAO collectionPropertiesDAO;

  @Inject
  PermissionsService permissionsService;

  @Inject
  RepExportService repExportService;

  @POST
  @Path("/{appId}/export/regulatory-evidence")
  @Operation(
    operationId = "buildRepExport",
    summary = "Build a BagIt Regulatory Evidence Pack (REP) for a Collection.",
    description =
      "Builds a BagIt 1.0 (RFC 8493) bag containing:\n" +
      "  - data/ro-crate-metadata.json — RO-Crate 1.1 metadata for the Collection and all DataObjects\n" +
      "  - data/PROV-O.jsonld          — PROV-O JSON-LD provenance graph of all recorded activities\n" +
      "  - bagit.txt, bag-info.txt, manifest-sha256.txt, tagmanifest-sha256.txt\n\n" +
      "Bags ≤ 1 MB are returned inline (Base64 in the 'bagBase64' field). " +
      "Larger bags will return a 'downloadUrl' (not yet implemented — 500 until TPL14b ships). " +
      "Requires Write permission on the Collection (REP build records a Collection-scoped " +
      "PROV-O Activity; gate matches the MCP rep_export tool per MCP-PERMS-AUDIT-2)."
  )
  @APIResponse(
    responseCode = "200",
    description = "REP bag built successfully.",
    content = @Content(schema = @Schema(implementation = RepExportIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the collection.")
  @APIResponse(responseCode = "404", description = "No collection with that appId.")
  @APIResponse(responseCode = "500", description = "REP build failed (serialisation or packing error).")
  public Response buildRepExport(
    @PathParam("appId") String collectionAppId,
    @Context SecurityContext securityContext
  ) {
    if (securityContext.getUserPrincipal() == null) {
      return problem(Response.Status.UNAUTHORIZED, "/problems/rep-export.unauthorized", "Authentication required", "No valid JWT or API key was provided.");
    }
    String caller = securityContext.getUserPrincipal().getName();

    Optional<Long> ogmId = collectionPropertiesDAO.findCollectionIdByAppId(collectionAppId);
    if (ogmId.isEmpty()) {
      return problem(Response.Status.NOT_FOUND, "/problems/rep-export.collection.not-found", "Collection not found", "No collection with appId '" + collectionAppId + "'.");
    }

    if (!permissionsService.isAccessTypeAllowedForUser(ogmId.get(), AccessType.Write, caller, 0L)) {
      return problem(Response.Status.FORBIDDEN, "/problems/rep-export.permission.denied", "Permission denied", "Caller lacks Write permission on collection '" + collectionAppId + "'.");
    }

    Log.infof("TPL14 REP export requested: collection=%s caller=%s", collectionAppId, caller);
    RepExportIO result = repExportService.buildExport(collectionAppId, caller);
    return Response.ok(result).build();
  }

  @GET
  @Path("/{appId}/export/regulatory-evidence/latest")
  @Operation(
    operationId = "getLatestRepExport",
    summary = "Retrieve the most recent REP export for a Collection.",
    description =
      "Returns the most recently built Regulatory Evidence Pack for the given Collection. " +
      "Currently returns 404 — export persistence (TPL14b) is not yet implemented. " +
      "The path is reserved so clients can adopt it in advance. " +
      "Requires Read permission on the Collection."
  )
  @APIResponse(
    responseCode = "200",
    description = "Most recent REP bag.",
    content = @Content(schema = @Schema(implementation = RepExportIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the collection.")
  @APIResponse(responseCode = "404", description = "No collection with that appId, or no prior export exists.")
  public Response getLatestRepExport(
    @PathParam("appId") String collectionAppId,
    @Context SecurityContext securityContext
  ) {
    if (securityContext.getUserPrincipal() == null) {
      return problem(Response.Status.UNAUTHORIZED, "/problems/rep-export.unauthorized", "Authentication required", "No valid JWT or API key was provided.");
    }
    String caller = securityContext.getUserPrincipal().getName();

    Optional<Long> ogmId = collectionPropertiesDAO.findCollectionIdByAppId(collectionAppId);
    if (ogmId.isEmpty()) {
      return problem(Response.Status.NOT_FOUND, "/problems/rep-export.collection.not-found", "Collection not found", "No collection with appId '" + collectionAppId + "'.");
    }

    // GET stays on Read — fetching a previously-built REP is a read operation
    // (no Activity recorded, no state mutated). Only the POST build flipped
    // to Write per MCP-PERMS-AUDIT-2.
    if (!permissionsService.isAccessTypeAllowedForUser(ogmId.get(), AccessType.Read, caller, 0L)) {
      return problem(Response.Status.FORBIDDEN, "/problems/rep-export.permission.denied", "Permission denied", "Caller lacks Read permission on collection '" + collectionAppId + "'.");
    }

    // TPL14b: export persistence not yet implemented — return 404 with informative message.
    throw new NotFoundException(
      "No prior REP export found for collection " + collectionAppId +
      " (TPL14b persistence not yet implemented)"
    );
  }

  private static Response problem(Response.Status status, String type, String title, String detail) {
    ProblemJson body = new ProblemJson(type, title, status.getStatusCode(), detail, null);
    return Response.status(status).type("application/problem+json").entity(body).build();
  }
}
