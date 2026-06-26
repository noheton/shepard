package de.dlr.shepard.v2.admin.semantic;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.semantic.daos.OntologyGitSourceDAO;
import de.dlr.shepard.context.semantic.entities.OntologyGitSource;
import de.dlr.shepard.context.semantic.services.OntologyGitIngestService;
import de.dlr.shepard.context.semantic.services.OntologyGitIngestService.IngestResult;
import de.dlr.shepard.v2.admin.semantic.io.OntologyGitIngestResultIO;
import de.dlr.shepard.v2.admin.semantic.io.OntologyGitSourceIO;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.DefaultValue;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * TPL5 — admin CRUD + on-demand-ingest for
 * {@link de.dlr.shepard.context.semantic.entities.OntologyGitSource} records.
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code GET  /v2/admin/semantic/git-sources} — list all git sources</li>
 *   <li>{@code POST /v2/admin/semantic/git-sources} — create a git source</li>
 *   <li>{@code DELETE /v2/admin/semantic/git-sources/{appId}} — delete a git source</li>
 *   <li>{@code POST /v2/admin/semantic/git-sources/{appId}/ingest} — trigger ingest now</li>
 * </ul>
 *
 * <p>All endpoints require the {@code instance-admin} role.
 *
 * @see OntologyGitIngestService
 */
@Path("/v2/admin/semantic/git-sources")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "Admin")
public class OntologyGitSourceRest {

  static final String PROBLEM_TYPE_AUTH = "/problems/auth.denied";
  static final String PROBLEM_TYPE_NOT_FOUND = "/problems/semantic.git-source.not-found";
  static final String PROBLEM_TYPE_VALIDATION = "/problems/semantic.git-source.invalid-input";

  @Inject
  OntologyGitSourceDAO gitSourceDAO;

  @Inject
  OntologyGitIngestService ingestService;

  @Inject
  AuthenticationContext authenticationContext;

  // ────────────────────────────────────────────────────────────────────────
  //  GET — list
  // ────────────────────────────────────────────────────────────────────────

  @GET
  @Operation(
    operationId = "listSemanticGitSources",
    summary = "List all ontology git sources.",
    description = "Returns every registered OntologyGitSource (enabled and disabled), " +
    "ordered by name. Includes last-ingest status and error for quick health assessment.\n\n" +
    "Pagination (APISIMP-PAGINATION-LIST-GIT-SOURCES): `page` (0-based, default 0) and " +
    "`pageSize` (1–200, default 50). `X-Total-Count` header carries the total count before paging."
  )
  @APIResponse(
    responseCode = "200",
    description = "Git sources (may be empty). Header X-Total-Count = total count before paging.",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = OntologyGitSourceIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required (RFC 7807).")
  @APIResponse(responseCode = "403", description = "Caller lacks instance-admin role (RFC 7807).")
  public Response list(
    @Parameter(description = "Zero-based page index (default 0).")
    @QueryParam("page") @DefaultValue("0") @PositiveOrZero int page,
    @Parameter(description = "Page size, 1–200 (default 50).")
    @QueryParam("pageSize") @DefaultValue("50") @Min(1) @Max(200) int pageSize,
    @Context SecurityContext securityContext
  ) {
    Response denied = guardAdmin(securityContext);
    if (denied != null) return denied;

    List<OntologyGitSource> sources = gitSourceDAO.listAll();
    List<OntologyGitSourceIO> all = new ArrayList<>(sources.size());
    for (OntologyGitSource s : sources) all.add(OntologyGitSourceIO.from(s));

    long total = all.size();
    int from = (int) Math.min((long) page * pageSize, total);
    int to = (int) Math.min((long) from + pageSize, total);
    return Response.ok(all.subList(from, to)).header("X-Total-Count", total).build();
  }

  // ────────────────────────────────────────────────────────────────────────
  //  POST — create
  // ────────────────────────────────────────────────────────────────────────

  @POST
  @Operation(
    operationId = "createSemanticGitSource",
    summary = "Register a new ontology git source.",
    description = "Creates an OntologyGitSource record. The source is not immediately " +
    "ingested — call .../ingest to trigger an on-demand run, or wait for the nightly " +
    "scheduler (02:00 server-local, when shepard.tpl5.git-ingest.enabled=true). " +
    "Required fields: name, repoUrl, pathPattern. Defaults: branch=main, enabled=true."
  )
  @APIResponse(
    responseCode = "201",
    description = "Created.",
    content = @Content(schema = @Schema(implementation = OntologyGitSourceIO.class))
  )
  @APIResponse(responseCode = "400", description = "Invalid input (RFC 7807).")
  @APIResponse(responseCode = "401", description = "Authentication required (RFC 7807).")
  @APIResponse(responseCode = "403", description = "Caller lacks instance-admin role (RFC 7807).")
  public Response create(
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = OntologyGitSourceIO.class))
    ) OntologyGitSourceIO body,
    @Context SecurityContext securityContext
  ) {
    Response denied = guardAdmin(securityContext);
    if (denied != null) return denied;

    if (body == null) {
      return problem(PROBLEM_TYPE_VALIDATION, "Missing request body", Status.BAD_REQUEST, "Request body is required.");
    }
    if (body.getName() == null || body.getName().isBlank()) {
      return problem(PROBLEM_TYPE_VALIDATION, "Missing name", Status.BAD_REQUEST, "name is required.");
    }

    // URL and branch validation via the service layer
    String urlError = OntologyGitIngestService.validateRepoUrl(body.getRepoUrl());
    if (urlError != null) {
      return problem(PROBLEM_TYPE_VALIDATION, "Invalid repoUrl", Status.BAD_REQUEST, urlError);
    }
    String branch = body.getBranch() != null && !body.getBranch().isBlank() ? body.getBranch() : "main";
    String branchError = OntologyGitIngestService.validateBranch(branch);
    if (branchError != null) {
      return problem(PROBLEM_TYPE_VALIDATION, "Invalid branch", Status.BAD_REQUEST, branchError);
    }
    if (body.getPathPattern() == null || body.getPathPattern().isBlank()) {
      return problem(PROBLEM_TYPE_VALIDATION, "Missing pathPattern", Status.BAD_REQUEST, "pathPattern is required.");
    }

    OntologyGitSource entity = new OntologyGitSource();
    entity.setName(body.getName());
    entity.setRepoUrl(body.getRepoUrl());
    entity.setBranch(branch);
    entity.setPathPattern(body.getPathPattern());
    entity.setTargetRepoAppId(body.getTargetRepoAppId());
    entity.setEnabled(body.getEnabled() == null || body.getEnabled());
    entity.setCreatedAt(System.currentTimeMillis());
    entity.setCreatedBy(callerName(securityContext));

    OntologyGitSource saved = gitSourceDAO.createOrUpdate(entity);
    Log.infof("TPL5: git source '%s' created by %s (appId=%s).",
      saved.getName(), saved.getCreatedBy(), saved.getAppId());

    return Response.status(Status.CREATED).entity(OntologyGitSourceIO.from(saved)).build();
  }

  // ────────────────────────────────────────────────────────────────────────
  //  DELETE
  // ────────────────────────────────────────────────────────────────────────

  @DELETE
  @Path("/{appId}")
  @Operation(
    operationId = "deleteSemanticGitSource",
    summary = "Delete an ontology git source.",
    description = "Removes the OntologyGitSource record. Does NOT remove the " +
    "UserOntologyBundle entries that were previously ingested from this source — " +
    "those remain in the catalogue and must be deleted separately via " +
    "DELETE /v2/admin/semantic/ontologies/{bundleId} if desired."
  )
  @APIResponse(responseCode = "204", description = "Deleted.")
  @APIResponse(responseCode = "404", description = "No git source with that appId (RFC 7807).")
  @APIResponse(responseCode = "401", description = "Authentication required (RFC 7807).")
  @APIResponse(responseCode = "403", description = "Caller lacks instance-admin role (RFC 7807).")
  public Response delete(
    @PathParam("appId") String appId,
    @Context SecurityContext securityContext
  ) {
    Response denied = guardAdmin(securityContext);
    if (denied != null) return denied;

    OntologyGitSource existing = gitSourceDAO.findByAppId(appId);
    if (existing == null) {
      return problem(
        PROBLEM_TYPE_NOT_FOUND,
        "Git source not found",
        Status.NOT_FOUND,
        "No OntologyGitSource with appId '" + appId + "'."
      );
    }

    if (existing.getId() != null) {
      gitSourceDAO.deleteByNeo4jId(existing.getId());
    }
    Log.infof("TPL5: git source '%s' (appId=%s) deleted by %s.",
      existing.getName(), appId, callerName(securityContext));

    return Response.noContent().build();
  }

  // ────────────────────────────────────────────────────────────────────────
  //  POST /{appId}/ingest — on-demand trigger
  // ────────────────────────────────────────────────────────────────────────

  @POST
  @Path("/{appId}/ingest")
  @Operation(
    summary = "Trigger an immediate ingest from a git source.",
    description = "Clones the repository (shallow, --depth=1), finds files matching " +
    "pathPattern, and ingests each as a UserOntologyBundle in the internal n10s store. " +
    "Runs synchronously — returns when the ingest is complete or has failed. " +
    "The source's lastStatus and lastIngestedAt are updated on completion. " +
    "Works regardless of the source's enabled flag."
  )
  @APIResponse(
    responseCode = "200",
    description = "Ingest attempted. Check ok field for success/failure.",
    content = @Content(schema = @Schema(implementation = OntologyGitIngestResultIO.class))
  )
  @APIResponse(responseCode = "404", description = "No git source with that appId (RFC 7807).")
  @APIResponse(responseCode = "401", description = "Authentication required (RFC 7807).")
  @APIResponse(responseCode = "403", description = "Caller lacks instance-admin role (RFC 7807).")
  public Response triggerIngest(
    @PathParam("appId") String appId,
    @Context SecurityContext securityContext
  ) {
    Response denied = guardAdmin(securityContext);
    if (denied != null) return denied;

    OntologyGitSource source = gitSourceDAO.findByAppId(appId);
    if (source == null) {
      return problem(
        PROBLEM_TYPE_NOT_FOUND,
        "Git source not found",
        Status.NOT_FOUND,
        "No OntologyGitSource with appId '" + appId + "'."
      );
    }

    Log.infof("TPL5: on-demand ingest triggered for git source '%s' (appId=%s) by %s.",
      source.getName(), appId, callerName(securityContext));

    IngestResult result = ingestService.ingest(source);

    OntologyGitIngestResultIO io = result.ok
      ? OntologyGitIngestResultIO.ok(result.filesIngested)
      : OntologyGitIngestResultIO.error(result.error, result.filesIngested);

    return Response.ok(io).build();
  }

  // ────────────────────────────────────────────────────────────────────────
  //  Helpers
  // ────────────────────────────────────────────────────────────────────────

  private Response guardAdmin(SecurityContext securityContext) {
    try {
      requireInstanceAdmin(securityContext);
      return null;
    } catch (InvalidAuthException denied) {
      Status status = denied.getMessage() != null && denied.getMessage().contains("Authentication required")
        ? Status.UNAUTHORIZED
        : Status.FORBIDDEN;
      return problem(PROBLEM_TYPE_AUTH, denied.getMessage(), status, denied.getMessage());
    }
  }

  private static void requireInstanceAdmin(SecurityContext securityContext) {
    if (securityContext == null || securityContext.getUserPrincipal() == null) {
      throw new InvalidAuthException("Authentication required");
    }
    if (!securityContext.isUserInRole(Constants.INSTANCE_ADMIN_ROLE)) {
      throw new InvalidAuthException("instance-admin role required");
    }
  }

  private static String callerName(SecurityContext sc) {
    if (sc == null || sc.getUserPrincipal() == null) return null;
    String n = sc.getUserPrincipal().getName();
    return (n == null || n.isBlank()) ? null : n;
  }

  private Response problem(String type, String title, Status status, String detail) {
    ProblemJson body = new ProblemJson(type, title, status.getStatusCode(), detail, null);
    return Response.status(status).type("application/problem+json").entity(body).build();
  }
}
