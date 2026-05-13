package de.dlr.shepard.v2.admin.semantic;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.semantic.OntologyRefreshService;
import de.dlr.shepard.context.semantic.OntologyRefreshService.BundleError;
import de.dlr.shepard.context.semantic.OntologyRefreshService.RefreshOutcome;
import de.dlr.shepard.v2.admin.semantic.io.RefreshOntologiesRequestIO;
import de.dlr.shepard.v2.admin.semantic.io.RefreshOntologiesResultIO;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * N1c — operator endpoint that re-fetches the bundled ontologies from
 * each manifest entry's pinned {@code canonicalUrl}, recomputes the
 * SHA-256, and re-imports into n10s when the hash differs (or when
 * {@code force=true}).
 *
 * <p>The complement to N1b's startup pre-seed (ADR-0019). Pre-seed
 * ships minimum-viable Turtle stubs in the JAR so the casual
 * annotation flow works on day one; this endpoint lets an operator
 * pull in the full canonical Turtle without waiting for the next
 * shepard release.
 *
 * <p>Returns {@link RefreshOntologiesResultIO} on success — per-bundle
 * failures live in the {@code errors[]} array with 200 OK overall.
 * RFC 7807 {@link ProblemJson} is reserved for the auth-denied paths.
 *
 * <p>The endpoint is intentionally <b>operator-only</b> — there's no
 * frontend exposure. Refresh is a controlled-cadence action; making
 * it routine-clickable would invite accidental re-imports against
 * slow / rate-limited canonical hosts.
 *
 * @see OntologyRefreshService
 * @see de.dlr.shepard.context.semantic.OntologySeedService
 */
@Path("/v2/admin/semantic")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "Admin")
public class SemanticAdminRest {

  /** RFC 7807 type-URI for the auth-denied path. */
  static final String PROBLEM_TYPE_AUTH = "/problems/auth.denied";

  @Inject
  OntologyRefreshService refreshService;

  @Inject
  AuthenticationContext authenticationContext;

  /**
   * Defence-in-depth role check. Mirrors {@code HdfAdminRest} —
   * {@code @RolesAllowed} catches the canonical paths, the manual
   * check guards against test-only paths that bypass the JAX-RS
   * filter chain.
   */
  private static void requireInstanceAdmin(SecurityContext securityContext) {
    if (securityContext == null || securityContext.getUserPrincipal() == null) {
      throw new InvalidAuthException("Authentication required");
    }
    if (!securityContext.isUserInRole(Constants.INSTANCE_ADMIN_ROLE)) {
      throw new InvalidAuthException("instance-admin role required");
    }
  }

  @POST
  @Path("/refresh-ontologies")
  @Operation(
    summary = "Refresh bundled ontologies against their pinned canonical URLs.",
    description = "Walks every bundle in ontologies-manifest.json (or the subset named in " +
    "request.bundles), fetches each bundle's canonicalUrl, recomputes its SHA-256, and " +
    "re-imports into n10s when the hash differs. Use after a major ontology release, or " +
    "to land the full canonical Turtle in bulk over the shipped minimum-viable stubs " +
    "(per N1b / ADR-0019). Best-effort per bundle — partial failures return 200 with errors[]."
  )
  @APIResponse(
    responseCode = "200",
    description = "Refresh attempted. Per-bundle failures live in errors[].",
    content = @Content(schema = @Schema(implementation = RefreshOntologiesResultIO.class))
  )
  @APIResponse(
    responseCode = "401",
    description = "Authentication required (RFC 7807).",
    content = @Content(schema = @Schema(implementation = ProblemJson.class))
  )
  @APIResponse(
    responseCode = "403",
    description = "Caller lacks the instance-admin role (RFC 7807).",
    content = @Content(schema = @Schema(implementation = ProblemJson.class))
  )
  public Response refreshOntologies(
    RefreshOntologiesRequestIO body,
    @Context SecurityContext securityContext
  ) {
    try {
      requireInstanceAdmin(securityContext);
    } catch (InvalidAuthException denied) {
      Status status = denied.getMessage() != null && denied.getMessage().contains("Authentication required")
        ? Status.UNAUTHORIZED
        : Status.FORBIDDEN;
      return problem(
        PROBLEM_TYPE_AUTH,
        denied.getMessage(),
        status,
        denied.getMessage()
      );
    }

    final RefreshOntologiesRequestIO req = body == null ? new RefreshOntologiesRequestIO() : body;
    Log.infof(
      "SemanticAdminRest: refresh-ontologies invoked (bundles=%s, force=%s)",
      req.getBundles(),
      req.isForce()
    );

    RefreshOutcome outcome = refreshService.refresh(req.getBundles(), req.isForce());

    List<RefreshOntologiesResultIO.Error> errors = new ArrayList<>(outcome.errors.size());
    for (BundleError e : outcome.errors) {
      errors.add(new RefreshOntologiesResultIO.Error(e.bundle, e.reason));
    }
    RefreshOntologiesResultIO result = new RefreshOntologiesResultIO(
      outcome.requested,
      outcome.refreshed,
      outcome.alreadyCurrent,
      errors
    );
    return Response.ok(result).build();
  }

  // ─── helpers ────────────────────────────────────────────────────────────

  private Response problem(String type, String title, Status status, String detail) {
    ProblemJson body = new ProblemJson(type, title, status.getStatusCode(), detail, null);
    return Response.status(status).type("application/problem+json").entity(body).build();
  }
}
