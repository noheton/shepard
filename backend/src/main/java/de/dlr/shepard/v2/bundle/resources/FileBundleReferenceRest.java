package de.dlr.shepard.v2.bundle.resources;

import de.dlr.shepard.common.exceptions.ProblemJson;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

/**
 * APISIMP-BUNDLE-REF-KIND-UNIFY slice 2 — 410 Gone tombstone for the old
 * {@code /v2/bundles/...} surface.
 *
 * <p>All CRUD was migrated to the unified {@code /v2/references?kind=bundle}
 * surface (handler: {@code FileBundleReferenceKindHandler}) and the group /
 * file sub-resources were moved to
 * {@code /v2/references/{bundleAppId}/groups/...}
 * ({@link BundleGroupsV2Rest}, slice 1, PR #2383).
 *
 * <p>This class is a tombstone: every method returns 410 Gone with a
 * {@code Location} header pointing to the canonical replacement path, per
 * RFC 7231 §6.5.9. It will be removed once the deprecation window closes.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/bundles")
@RequestScoped
@Tag(name = "References")
public class FileBundleReferenceRest {

  private static final Logger LOG = Logger.getLogger(FileBundleReferenceRest.class);

  // ─── bundle ───────────────────────────────────────────────────────────────

  @GET
  @Path("/{bundleAppId}")
  @Operation(
    operationId = "getBundle",
    summary = "[GONE] Bundle metadata endpoint moved — use GET /v2/references/{bundleAppId}.",
    description = "APISIMP-BUNDLE-REF-KIND-UNIFY: this path is retired. Use GET /v2/references/{bundleAppId}."
  )
  @APIResponse(responseCode = "410", description = "Endpoint retired. Use GET /v2/references/{bundleAppId}.")
  public Response getBundle(@PathParam("bundleAppId") String bundleAppId) {
    LOG.debugf("getBundle: 410 stub hit for bundle=%s — use GET /v2/references/{bundleAppId}", bundleAppId);
    return gone("GET /v2/references/" + bundleAppId);
  }

  // ─── groups ───────────────────────────────────────────────────────────────

  @GET
  @Path("/{bundleAppId}/groups")
  @Operation(
    operationId = "listGroups",
    summary = "[GONE] List-groups endpoint moved — use GET /v2/references/{bundleAppId}/groups.",
    description = "APISIMP-BUNDLE-REF-KIND-UNIFY: this path is retired. Use GET /v2/references/{bundleAppId}/groups."
  )
  @APIResponse(responseCode = "410", description = "Endpoint retired. Use GET /v2/references/{bundleAppId}/groups.")
  public Response listGroups(@PathParam("bundleAppId") String bundleAppId) {
    LOG.debugf("listGroups: 410 stub hit for bundle=%s — use GET /v2/references/%s/groups", bundleAppId, bundleAppId);
    return gone("GET /v2/references/" + bundleAppId + "/groups");
  }

  @POST
  @Path("/{bundleAppId}/groups")
  @Operation(
    operationId = "createGroup",
    summary = "[GONE] Create-group endpoint moved — use POST /v2/references/{bundleAppId}/groups.",
    description = "APISIMP-BUNDLE-REF-KIND-UNIFY: this path is retired. Use POST /v2/references/{bundleAppId}/groups."
  )
  @APIResponse(responseCode = "410", description = "Endpoint retired. Use POST /v2/references/{bundleAppId}/groups.")
  public Response createGroup(@PathParam("bundleAppId") String bundleAppId) {
    LOG.debugf("createGroup: 410 stub hit for bundle=%s — use POST /v2/references/%s/groups", bundleAppId, bundleAppId);
    return gone("POST /v2/references/" + bundleAppId + "/groups");
  }

  @GET
  @Path("/{bundleAppId}/groups/{groupAppId}")
  @Operation(
    operationId = "getGroup",
    summary = "[GONE] Get-group endpoint moved — use GET /v2/references/{bundleAppId}/groups/{groupAppId}.",
    description = "APISIMP-BUNDLE-REF-KIND-UNIFY: this path is retired. Use GET /v2/references/{bundleAppId}/groups/{groupAppId}."
  )
  @APIResponse(responseCode = "410", description = "Endpoint retired. Use GET /v2/references/{bundleAppId}/groups/{groupAppId}.")
  public Response getGroup(
    @PathParam("bundleAppId") String bundleAppId,
    @PathParam("groupAppId") String groupAppId
  ) {
    LOG.debugf("getGroup: 410 stub hit for bundle=%s group=%s", bundleAppId, groupAppId);
    return gone("GET /v2/references/" + bundleAppId + "/groups/" + groupAppId);
  }

  @PATCH
  @Path("/{bundleAppId}/groups/{groupAppId}")
  @Consumes({ "application/merge-patch+json", MediaType.APPLICATION_JSON })
  @Operation(
    operationId = "patchGroup",
    summary = "[GONE] Patch-group endpoint moved — use PATCH /v2/references/{bundleAppId}/groups/{groupAppId}.",
    description = "APISIMP-BUNDLE-REF-KIND-UNIFY: this path is retired. Use PATCH /v2/references/{bundleAppId}/groups/{groupAppId}."
  )
  @APIResponse(responseCode = "410", description = "Endpoint retired. Use PATCH /v2/references/{bundleAppId}/groups/{groupAppId}.")
  public Response patchGroup(
    @PathParam("bundleAppId") String bundleAppId,
    @PathParam("groupAppId") String groupAppId
  ) {
    LOG.debugf("patchGroup: 410 stub hit for bundle=%s group=%s", bundleAppId, groupAppId);
    return gone("PATCH /v2/references/" + bundleAppId + "/groups/" + groupAppId);
  }

  @DELETE
  @Path("/{bundleAppId}/groups/{groupAppId}")
  @Operation(
    operationId = "deleteGroup",
    summary = "[GONE] Delete-group endpoint moved — use DELETE /v2/references/{bundleAppId}/groups/{groupAppId}.",
    description = "APISIMP-BUNDLE-REF-KIND-UNIFY: this path is retired. Use DELETE /v2/references/{bundleAppId}/groups/{groupAppId}."
  )
  @APIResponse(responseCode = "410", description = "Endpoint retired. Use DELETE /v2/references/{bundleAppId}/groups/{groupAppId}.")
  public Response deleteGroup(
    @PathParam("bundleAppId") String bundleAppId,
    @PathParam("groupAppId") String groupAppId
  ) {
    LOG.debugf("deleteGroup: 410 stub hit for bundle=%s group=%s", bundleAppId, groupAppId);
    return gone("DELETE /v2/references/" + bundleAppId + "/groups/" + groupAppId);
  }

  // ─── files ────────────────────────────────────────────────────────────────

  @GET
  @Path("/{bundleAppId}/groups/{groupAppId}/files")
  @Operation(
    operationId = "listGroupFiles",
    summary = "[GONE] List-group-files endpoint moved — use GET /v2/references/{bundleAppId}/groups/{groupAppId}/files.",
    description = "APISIMP-BUNDLE-REF-KIND-UNIFY: this path is retired. Use GET /v2/references/{bundleAppId}/groups/{groupAppId}/files."
  )
  @APIResponse(responseCode = "410", description = "Endpoint retired. Use GET /v2/references/{bundleAppId}/groups/{groupAppId}/files.")
  public Response listGroupFiles(
    @PathParam("bundleAppId") String bundleAppId,
    @PathParam("groupAppId") String groupAppId
  ) {
    LOG.debugf("listGroupFiles: 410 stub hit for bundle=%s group=%s", bundleAppId, groupAppId);
    return gone("GET /v2/references/" + bundleAppId + "/groups/" + groupAppId + "/files");
  }

  @POST
  @Path("/{bundleAppId}/groups/{groupAppId}/files")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Operation(
    operationId = "uploadFileIntoGroup",
    summary = "[GONE] Upload-file endpoint moved — use POST /v2/references/{bundleAppId}/groups/{groupAppId}/files.",
    description = "APISIMP-BUNDLE-REF-KIND-UNIFY: this path is retired. Use POST /v2/references/{bundleAppId}/groups/{groupAppId}/files."
  )
  @APIResponse(responseCode = "410", description = "Endpoint retired. Use POST /v2/references/{bundleAppId}/groups/{groupAppId}/files.")
  public Response uploadFileIntoGroup(
    @PathParam("bundleAppId") String bundleAppId,
    @PathParam("groupAppId") String groupAppId
  ) {
    LOG.debugf("uploadFileIntoGroup: 410 stub hit for bundle=%s group=%s", bundleAppId, groupAppId);
    return gone("POST /v2/references/" + bundleAppId + "/groups/" + groupAppId + "/files");
  }

  // ─── helper ───────────────────────────────────────────────────────────────

  private static Response gone(String replacement) {
    return Response.status(Response.Status.GONE)
      .type("application/problem+json")
      .header("Location", replacement)
      .entity(new ProblemJson(
        "urn:shepard:error:gone",
        "Endpoint retired",
        410,
        "This path has been retired by APISIMP-BUNDLE-REF-KIND-UNIFY. Use: " + replacement,
        null))
      .build();
  }
}
