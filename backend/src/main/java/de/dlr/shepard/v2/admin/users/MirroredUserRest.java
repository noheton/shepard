package de.dlr.shepard.v2.admin.users;

import de.dlr.shepard.auth.users.daos.MirroredUserDAO;
import de.dlr.shepard.auth.users.entities.MirroredUser;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.v2.admin.users.io.MirroredUserCreateIO;
import de.dlr.shepard.v2.admin.users.io.MirroredUserIO;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * PROV-USER-MIRROR-ENDPOINT — admin REST surface for minting and updating
 * {@code :MirroredUser} nodes.
 *
 * <p>A {@code :MirroredUser} is a lightweight shadow node representing a user
 * from a remote Shepard instance. It exists so the PROV-O attribution chain
 * ({@code prov:wasAssociatedWith}) can reference a local, stable node for
 * cross-instance imports (e.g. the MFFD v15 importer script).
 *
 * <p>The endpoint is <strong>idempotent</strong>: calling it twice with the same
 * {@code (sourceInstance, sourceUsername)} pair returns the same {@code appId}.
 * Mutable fields ({@code sourceDisplayName}, {@code sourceEmail}) are updated on
 * every call.
 *
 * <p>Auth: {@code instance-admin} role only (same gate as all other
 * {@code /v2/admin/} endpoints). PROV1a's {@link
 * de.dlr.shepard.provenance.filters.ProvenanceCaptureFilter} automatically
 * captures the POST as an {@code :Activity} row.
 *
 * <p>API-version policy: lives exclusively on the {@code /v2/} development
 * surface — no upstream {@code /shepard/api/} surface is touched.
 *
 * @see de.dlr.shepard.auth.users.entities.MirroredUser
 * @see de.dlr.shepard.auth.users.daos.MirroredUserDAO
 */
@Path("/v2/admin/users/mirror")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "Admin")
public class MirroredUserRest {

  @Inject
  public MirroredUserDAO mirroredUserDAO;

  @POST
  @Operation(
    operationId = "mirrorUsers",
    summary = "Create or update a :MirroredUser node (idempotent).",
    description = "Mints a :MirroredUser shadow node for a user from a remote Shepard " +
      "instance. Idempotent: if a node with the same (sourceInstance, sourceUsername) pair " +
      "already exists, its mutable fields (sourceDisplayName, sourceEmail) are updated and " +
      "the same appId is returned. " +
      "Returns 201 on first creation, 200 on subsequent calls (same appId, updated fields). " +
      "Required fields: sourceInstance, sourceUsername. " +
      "Auth: instance-admin role only."
  )
  @APIResponse(
    responseCode = "201",
    description = "Mirror node created — new (sourceInstance, sourceUsername) pair.",
    content = @Content(schema = @Schema(implementation = MirroredUserIO.class))
  )
  @APIResponse(
    responseCode = "200",
    description = "Mirror node updated — pair already existed; same appId returned.",
    content = @Content(schema = @Schema(implementation = MirroredUserIO.class))
  )
  @APIResponse(
    responseCode = "400",
    description = "Required field missing: sourceInstance or sourceUsername is absent or blank."
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response post(
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = MirroredUserCreateIO.class))
    ) MirroredUserCreateIO body
  ) {
    // --- 400 validation ---
    if (body == null) {
      return badRequest("Request body is required.");
    }
    if (body.sourceInstance() == null || body.sourceInstance().isBlank()) {
      return badRequest("sourceInstance is required and must not be blank.");
    }
    if (body.sourceUsername() == null || body.sourceUsername().isBlank()) {
      return badRequest("sourceUsername is required and must not be blank.");
    }

    // --- idempotent create-or-update ---
    boolean alreadyExists = mirroredUserDAO
      .findBySourceInstanceAndUsername(body.sourceInstance(), body.sourceUsername())
      .isPresent();

    MirroredUser incoming = new MirroredUser();
    incoming.setSourceInstance(body.sourceInstance());
    incoming.setSourceUsername(body.sourceUsername());
    incoming.setSourceDisplayName(body.sourceDisplayName());
    incoming.setSourceEmail(body.sourceEmail());

    MirroredUser saved = mirroredUserDAO.createOrUpdateBySourceKey(incoming);

    Log.infof(
      "MirroredUser %s: appId=%s sourceInstance=%s sourceUsername=%s",
      alreadyExists ? "updated" : "created",
      saved.getAppId(),
      saved.getSourceInstance(),
      saved.getSourceUsername()
    );

    Status status = alreadyExists ? Status.OK : Status.CREATED;
    return Response.status(status).entity(MirroredUserIO.from(saved)).build();
  }

  // ─── helpers ─────────────────────────────────────────────────────────────

  private static Response badRequest(String message) {
    return Response.status(Status.BAD_REQUEST).type("application/problem+json")
      .entity(new ProblemJson("/problems/mirror-user.bad-request", "Bad Request",
        Status.BAD_REQUEST.getStatusCode(), message, null))
      .build();
  }
}
