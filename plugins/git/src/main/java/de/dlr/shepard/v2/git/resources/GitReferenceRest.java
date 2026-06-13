package de.dlr.shepard.v2.git.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.references.git.adapters.GitAdapterException;
import de.dlr.shepard.context.references.git.daos.GitReferenceDAO;
import de.dlr.shepard.context.references.git.entities.GitReference;
import de.dlr.shepard.context.references.git.io.CheckUpdateResultIO;
import de.dlr.shepard.context.references.git.io.GitArtifactPreviewIO;
import de.dlr.shepard.context.references.git.services.GitReferenceService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
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
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

/**
 * Special-ops surface for GitReferences.
 *
 * <p>CRUD (list / create / read / delete / patch) was removed by
 * PLUGIN-PERKIND-CRUD-CLEANUP; those operations are now served by the
 * unified {@code /v2/references?kind=git} surface
 * ({@code ReferencesV2Rest} + {@code GitReferenceKindHandler}).
 *
 * <p>This class retains only the two domain-specific operations that have
 * no equivalent on the generic reference surface: G1b preview and G1d
 * check-update.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/data-objects/{dataObjectAppId}/git-references")
@RequestScoped
@Tag(name = "Git references")
public class GitReferenceRest {

  private static final Logger LOG = Logger.getLogger(GitReferenceRest.class);

  @Inject
  GitReferenceDAO gitReferenceDAO;

  @Inject
  PermissionsService permissionsService;

  @Inject
  EntityIdResolver entityIdResolver;

  @Inject
  GitReferenceService gitReferenceService;

  @GET
  @Path("/{appId}/preview")
  @Operation(
    operationId = "previewGitReference",
    summary = "Server-side inline preview of a tracked-artifact GitReference (G1b).",
    description = "Resolves the GitReference, picks the caller's PAT for the matching git host (via " +
    "G1-cred), routes through the per-host GitAdapter, and returns the file content (UTF-8) up to " +
    "shepard.git.preview.max-bytes (default 1 MB). All non-fatal failure modes are reported as " +
    "200 with `available=false` + a `reason` discriminator so the UI can render the explanation " +
    "inline rather than as an error. Possible reasons: not-tracked, unsupported-host, no-credential, " +
    "invalid-repo-url, fetch-failed."
  )
  @APIResponse(
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = GitArtifactPreviewIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No DataObject or GitReference with those appIds.")
  @APIResponse(responseCode = "501", description = "Host has no adapter (e.g. non-GitLab host in v1).")
  public Response preview(
    @PathParam("dataObjectAppId") String dataObjectAppId,
    @PathParam("appId") String gitReferenceAppId,
    @Context SecurityContext securityContext
  ) {
    var gate = checkAccess(dataObjectAppId, AccessType.Read, securityContext);
    if (gate != null) return gate;

    GitReference gr = gitReferenceDAO.findByAppId(gitReferenceAppId);
    if (gr == null || gr.getDataObject() == null || !dataObjectAppId.equals(gr.getDataObject().getAppId())) {
      return problem(Response.Status.NOT_FOUND, "GitReference not found");
    }

    String caller = securityContext.getUserPrincipal().getName();
    GitArtifactPreviewIO out = gitReferenceService.previewArtifact(gr, caller);
    if (!out.isAvailable() && "unsupported-host".equals(out.getReason())) {
      return Response.status(Response.Status.NOT_IMPLEMENTED)
        .type("application/problem+json")
        .entity(new ProblemJson(
            "https://shepard.dlr.de/problems/git.adapter.unsupported-host",
            "No GitAdapter is registered for this host.",
            501,
            "v1 ships a GitLab adapter only; GitHub and Gitea ship in G1d.",
            null))
        .build();
    }
    return Response.ok(out).build();
  }

  @POST
  @Path("/{appId}/check-update")
  @Operation(
    operationId = "checkGitReferenceUpdate",
    summary = "Check whether the upstream Git ref has moved since last resolution (G1d).",
    description = "Resolves the GitReference's ref via the matching GitAdapter " +
    "(using the caller's stored PAT if present; works without a PAT for public " +
    "repos that the adapter can read anonymously). Compares the current SHA to " +
    "the persisted resolvedSha. Side-effect: updates resolvedSha + " +
    "resolvedAtMillis on the reference, even when nothing changed (refreshes " +
    "the timestamp). Returns {currentSha, previousSha, updated, checkedAtMillis}."
  )
  @APIResponse(
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = CheckUpdateResultIO.class))
  )
  @APIResponse(responseCode = "400", description = "Reference has no ref to resolve, or repoUrl can't be parsed.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No DataObject or GitReference with those appIds.")
  @APIResponse(responseCode = "502", description = "Adapter failed to resolve the ref against upstream.")
  public Response checkUpdate(
    @PathParam("dataObjectAppId") String dataObjectAppId,
    @PathParam("appId") String gitReferenceAppId,
    @Context SecurityContext securityContext
  ) {
    var gate = checkAccess(dataObjectAppId, AccessType.Write, securityContext);
    if (gate != null) return gate;

    GitReference gr = gitReferenceDAO.findByAppId(gitReferenceAppId);
    if (gr == null || gr.getDataObject() == null || !dataObjectAppId.equals(gr.getDataObject().getAppId())) {
      return problem(Response.Status.NOT_FOUND, "GitReference not found");
    }

    String caller = securityContext.getUserPrincipal().getName();
    try {
      CheckUpdateResultIO result = gitReferenceService.checkForUpdate(gr, caller);
      gitReferenceDAO.createOrUpdate(gr);
      return Response.ok(result).build();
    } catch (GitAdapterException e) {
      return Response.status(e.getStatus())
        .type("application/problem+json")
        .entity(new ProblemJson(
          "urn:shepard:error:upstream",
          "Git adapter error",
          e.getStatus(),
          e.getMessage(),
          null))
        .build();
    }
  }

  private Response checkAccess(String dataObjectAppId, AccessType accessType, SecurityContext securityContext) {
    String caller = securityContext.getUserPrincipal() != null ? securityContext.getUserPrincipal().getName() : null;
    if (caller == null) return problem(Response.Status.UNAUTHORIZED, "Authentication required");
    long ogmId;
    try {
      ogmId = entityIdResolver.resolveLong(dataObjectAppId);
    } catch (NotFoundException nfe) {
      return problem(Response.Status.NOT_FOUND, "DataObject not found");
    }
    if (!permissionsService.isAccessTypeAllowedForUser(ogmId, accessType, caller)) {
      return problem(Response.Status.FORBIDDEN, "Insufficient permissions");
    }
    return null;
  }

  private static Response problem(Response.Status status, String detail) {
    String type = switch (status) {
      case UNAUTHORIZED -> "urn:shepard:error:unauthorized";
      case FORBIDDEN -> "urn:shepard:error:forbidden";
      case BAD_REQUEST -> "urn:shepard:error:validation";
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
