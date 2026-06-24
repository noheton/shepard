package de.dlr.shepard.v2.git.resources;

import de.dlr.shepard.common.exceptions.ProblemJson;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

/**
 * APISIMP-GIT-REF-PATH compat stub — 410 Gone carrier for the old per-DataObject paths.
 *
 * <p>CRUD was removed by PLUGIN-PERKIND-CRUD-CLEANUP (unified on
 * {@code /v2/references?kind=git}). Action sub-paths (preview, check-update)
 * were moved by APISIMP-GIT-REF-PATH to {@code /v2/references/{appId}/preview}
 * and {@code /v2/references/{appId}/check-update}
 * ({@link GitReferenceActionsRest}).
 *
 * <p>This class is a tombstone: every method returns 410 Gone with a
 * {@code Link} header pointing to the replacement path, per RFC 7231 §6.5.9.
 * It will be removed once the deprecation window closes.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/data-objects/{dataObjectAppId}/git-references")
@RequestScoped
@Tag(name = "Git references")
public class GitReferenceRest {

  private static final Logger LOG = Logger.getLogger(GitReferenceRest.class);

  @GET
  @Path("/{appId}/preview")
  @Operation(
    operationId = "previewGitReference",
    summary = "[GONE] Preview endpoint moved — use GET /v2/references/{appId}/preview.",
    description = "APISIMP-GIT-REF-PATH: this path is retired. Use GET /v2/references/{appId}/preview."
  )
  @APIResponse(responseCode = "410", description = "Endpoint retired. Use GET /v2/references/{appId}/preview.")
  public Response preview(
    @PathParam("dataObjectAppId") String dataObjectAppId,
    @PathParam("appId") String gitReferenceAppId,
    @Context SecurityContext securityContext
  ) {
    LOG.debugf("preview: 410 stub hit for DO=%s ref=%s — use GET /v2/references/{appId}/preview",
      dataObjectAppId, gitReferenceAppId);
    return gone("GET /v2/references/" + gitReferenceAppId + "/preview");
  }

  @POST
  @Path("/{appId}/check-update")
  @Operation(
    operationId = "checkGitReferenceUpdate",
    summary = "[GONE] Check-update endpoint moved — use POST /v2/references/{appId}/check-update.",
    description = "APISIMP-GIT-REF-PATH: this path is retired. Use POST /v2/references/{appId}/check-update."
  )
  @APIResponse(responseCode = "410", description = "Endpoint retired. Use POST /v2/references/{appId}/check-update.")
  public Response checkUpdate(
    @PathParam("dataObjectAppId") String dataObjectAppId,
    @PathParam("appId") String gitReferenceAppId,
    @Context SecurityContext securityContext
  ) {
    LOG.debugf("check-update: 410 stub hit for DO=%s ref=%s — use POST /v2/references/{appId}/check-update",
      dataObjectAppId, gitReferenceAppId);
    return gone("POST /v2/references/" + gitReferenceAppId + "/check-update");
  }

  private static Response gone(String replacement) {
    return Response.status(Response.Status.GONE)
      .type("application/problem+json")
      .header("Location", replacement)
      .entity(new ProblemJson(
        "urn:shepard:error:gone",
        "Endpoint retired",
        410,
        "This path has been retired by APISIMP-GIT-REF-PATH. Use: " + replacement,
        null))
      .build();
  }
}
