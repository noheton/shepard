package de.dlr.shepard.v2.git.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.ProblemJson;
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
import static de.dlr.shepard.v2.common.ProblemResponse.problem;

/**
 * APISIMP-GIT-REF-PATH — domain-specific action endpoints for GitReferences
 * on the unified {@code /v2/references} surface.
 *
 * <p>CRUD (list / create / read / delete / patch) is handled by
 * {@code ReferencesV2Rest} + {@code GitReferenceKindHandler}. The per-kind
 * per-DataObject path {@code /v2/data-objects/{dataObjectAppId}/git-references}
 * ({@code GitReferenceRest}) retains only 410 stubs after this migration.
 *
 * <p>These action sub-paths follow the same path pattern used by video download
 * ({@code /v2/references/{appId}/content}) — the reference is addressed by its
 * own {@code appId}; the parent DataObject is resolved internally.
 *
 * <h2>Routes</h2>
 * <ul>
 *   <li>{@code GET  /v2/references/{appId}/preview}       — G1b inline preview.</li>
 *   <li>{@code POST /v2/references/{appId}/check-update}  — G1d upstream SHA resolution.</li>
 * </ul>
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/references")
@RequestScoped
@Tag(name = "Git references")
public class GitReferenceActionsRest {

  private static final Logger LOG = Logger.getLogger(GitReferenceActionsRest.class);

  @Inject
  GitReferenceDAO gitReferenceDAO;

  @Inject
  PermissionsService permissionsService;

  @Inject
  GitReferenceService gitReferenceService;

  // ── G1b: preview ──────────────────────────────────────────────────────────

  @GET
  @Path("/{appId}/preview")
  @Operation(
    operationId = "previewGitReferenceV2",
    summary = "Server-side inline preview of a tracked-artifact GitReference (G1b).",
    description = "Resolves the GitReference by appId, picks the caller's PAT for the matching git host " +
    "(via G1-cred), routes through the per-host GitAdapter, and returns the file content (UTF-8) up to " +
    "shepard.git.preview.max-bytes (default 1 MB). All non-fatal failure modes are reported as " +
    "200 with `available=false` + a `reason` discriminator so the UI can render the explanation " +
    "inline rather than as an error. Possible reasons: not-tracked, unsupported-host, no-credential, " +
    "invalid-repo-url, fetch-failed. The parent DataObject is resolved from the reference — " +
    "no dataObjectAppId is needed in the URL."
  )
  @APIResponse(
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = GitArtifactPreviewIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No GitReference with this appId.")
  @APIResponse(responseCode = "501", description = "No adapter registered for this git host.")
  public Response preview(
    @PathParam("appId") String appId,
    @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return problem(Response.Status.UNAUTHORIZED, "Authentication required");

    GitReference gr = gitReferenceDAO.findByAppId(appId);
    if (gr == null || gr.getDataObject() == null) {
      return problem(Response.Status.NOT_FOUND, "GitReference not found");
    }

    String doAppId = gr.getDataObject().getAppId();
    if (!permissionsService.isAccessAllowedForDataObjectAppId(doAppId, AccessType.Read, caller)) {
      return problem(Response.Status.FORBIDDEN, "Insufficient permissions");
    }

    GitArtifactPreviewIO out = gitReferenceService.previewArtifact(gr, caller);
    if (!out.isAvailable() && "unsupported-host".equals(out.getReason())) {
      return Response.status(Response.Status.NOT_IMPLEMENTED)
        .type("application/problem+json")
        .entity(new ProblemJson(
          "/problems/git.adapter.unsupported-host",
          "No GitAdapter is registered for this host.",
          501,
          "Adapters: GitLab (G1b), GitHub + Gitea (G1d). Ensure the plugin is enabled and the host is listed.",
          null))
        .build();
    }
    return Response.ok(out).build();
  }

  // ── G1d: check-update ─────────────────────────────────────────────────────

  @POST
  @Path("/{appId}/check-update")
  @Operation(
    operationId = "checkGitReferenceUpdateV2",
    summary = "Check whether the upstream Git ref has moved since last resolution (G1d).",
    description = "Resolves the GitReference's ref via the matching GitAdapter " +
    "(using the caller's stored PAT if present; works without a PAT for public repos). " +
    "Compares the current SHA to the persisted resolvedSha. Side-effect: updates " +
    "resolvedSha + resolvedAtMillis on the reference. Returns {currentSha, previousSha, " +
    "updated, checkedAtMillis}. Requires Write permission on the parent DataObject."
  )
  @APIResponse(
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = CheckUpdateResultIO.class))
  )
  @APIResponse(responseCode = "400", description = "Reference has no ref to resolve, or repoUrl cannot be parsed.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No GitReference with this appId.")
  @APIResponse(responseCode = "502", description = "Adapter failed to resolve the ref against upstream.")
  public Response checkUpdate(
    @PathParam("appId") String appId,
    @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return problem(Response.Status.UNAUTHORIZED, "Authentication required");

    GitReference gr = gitReferenceDAO.findByAppId(appId);
    if (gr == null || gr.getDataObject() == null) {
      return problem(Response.Status.NOT_FOUND, "GitReference not found");
    }

    String doAppId = gr.getDataObject().getAppId();
    if (!permissionsService.isAccessAllowedForDataObjectAppId(doAppId, AccessType.Write, caller)) {
      return problem(Response.Status.FORBIDDEN, "Insufficient permissions");
    }

    try {
      CheckUpdateResultIO result = gitReferenceService.checkForUpdate(gr, caller);
      gitReferenceDAO.createOrUpdate(gr);
      return Response.ok(result).build();
    } catch (GitAdapterException e) {
      LOG.debugf("check-update failed for GitReference %s: %s", appId, e.getMessage());
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

  // ── helpers ───────────────────────────────────────────────────────────────

  private String callerOrNull(SecurityContext sc) {
    return sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
  }

}
