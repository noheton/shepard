package de.dlr.shepard.v2.admin.resources;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.common.exceptions.ApiError;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.v2.admin.io.GrantInstanceAdminIO;
import de.dlr.shepard.v2.admin.io.InstanceAdminGrantIO;
import de.dlr.shepard.v2.admin.io.NukeRequestIO;
import de.dlr.shepard.v2.admin.io.NukeResultIO;
import de.dlr.shepard.v2.admin.io.PermissionAuditEntryIO;
import de.dlr.shepard.v2.admin.io.PermissionAuditLogEntryIO;
import de.dlr.shepard.v2.admin.services.InstanceAdminService;
import de.dlr.shepard.v2.admin.services.NukeService;
import de.dlr.shepard.v2.admin.services.PermissionAuditLogQueryService;
import de.dlr.shepard.v2.admin.services.PermissionAuditService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
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
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * The {@code /v2/admin/instance-admins} + {@code /v2/admin/permission-audit}
 * endpoints, per {@code aidocs/51 §10}. All gated by
 * {@code @RolesAllowed("instance-admin")} — the role-check routes
 * through {@code JWTSecurityContext.isUserInRole} which consults the
 * dual-source-resolved principal.
 *
 * <p>This class lives under {@code de.dlr.shepard.v2.admin.resources}
 * per the P4 namespace convention; the {@code @Path} string starts
 * with {@code /v2/admin/...}.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/admin")
@RequestScoped
public class InstanceAdminRest {

  @Inject
  InstanceAdminService instanceAdminService;

  @Inject
  PermissionAuditService permissionAuditService;

  @Inject
  PermissionAuditLogQueryService permissionAuditLogQueryService;

  @Inject
  AuthenticationContext authenticationContext;

  @Inject
  NukeService nukeService;

  /**
   * Manual role-check fallback: in addition to {@code @RolesAllowed},
   * each method calls {@link #requireInstanceAdmin(SecurityContext)}
   * for defence-in-depth (the runtime gate doesn't engage if
   * {@code quarkus-security} isn't on the classpath, but the manual
   * check always does).
   */
  private static void requireInstanceAdmin(SecurityContext securityContext) {
    if (securityContext == null || !securityContext.isUserInRole(Constants.INSTANCE_ADMIN_ROLE)) {
      throw new InvalidAuthException("instance-admin role required");
    }
  }

  @GET
  @Path("/instance-admins")
  @RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
  @Tag(name = "Admin")
  @Operation(description = "List Neo4j-side instance-admin grants (with audit metadata).")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(
      schema = @Schema(type = SchemaType.ARRAY, implementation = InstanceAdminGrantIO.class)
    )
  )
  @APIResponse(description = "forbidden", responseCode = "403")
  public Response listInstanceAdmins(@Context SecurityContext securityContext) {
    requireInstanceAdmin(securityContext);
    List<InstanceAdminGrantIO> grants = instanceAdminService.listInstanceAdmins();
    return Response.ok(grants).build();
  }

  @POST
  @Path("/instance-admins")
  @RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
  @Tag(name = "Admin")
  @Operation(description = "Grant the instance-admin role to a user.")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = InstanceAdminGrantIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @APIResponse(description = "forbidden", responseCode = "403")
  public Response grantInstanceAdmin(
    @Context SecurityContext securityContext,
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = GrantInstanceAdminIO.class))) @Valid GrantInstanceAdminIO body
  ) {
    requireInstanceAdmin(securityContext);
    String grantedBy = authenticationContext == null ? null : authenticationContext.getCurrentUserName();
    InstanceAdminGrantIO grant = instanceAdminService.grantInstanceAdmin(body.getUsername(), grantedBy);
    return Response.status(Status.CREATED).entity(grant).build();
  }

  @DELETE
  @Path("/instance-admins/{username}")
  @RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
  @Tag(name = "Admin")
  @Operation(description = "Revoke the Neo4j-side instance-admin grant from a user.")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  @APIResponse(description = "forbidden", responseCode = "403")
  public Response revokeInstanceAdmin(
    @Context SecurityContext securityContext,
    @PathParam("username") @NotBlank String username
  ) {
    requireInstanceAdmin(securityContext);
    boolean revoked = instanceAdminService.revokeInstanceAdmin(username);
    if (!revoked) {
      return Response.status(Status.NOT_FOUND)
        .entity(new ApiError(Status.NOT_FOUND.getStatusCode(), "NotFound", "No Neo4j-side instance-admin grant for user '" + username + "'"))
        .build();
    }
    return Response.status(Status.NO_CONTENT).build();
  }

  @GET
  @Path("/permission-audit")
  @RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
  @Tag(name = "Admin")
  @Operation(description = "List BasicEntity nodes that lack a :has_permissions edge (post-C3).")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(
      schema = @Schema(type = SchemaType.ARRAY, implementation = PermissionAuditEntryIO.class)
    )
  )
  @APIResponse(description = "forbidden", responseCode = "403")
  public Response permissionAudit(@Context SecurityContext securityContext) {
    requireInstanceAdmin(securityContext);
    List<PermissionAuditEntryIO> orphans = permissionAuditService.listOrphans();
    return Response.ok(orphans).build();
  }

  /**
   * F3 — permission audit log query endpoint.
   *
   * <p>Reads the {@code permission_audit_log} Postgres table. Results sorted
   * {@code occurred_at DESC}, paged via {@code page}/{@code size}.
   *
   * @param entityAppId filter by entity appId (optional)
   * @param actor       filter by actor_username (optional)
   * @param from        ISO-8601 lower bound for occurred_at (inclusive, optional)
   * @param to          ISO-8601 upper bound for occurred_at (exclusive, optional)
   * @param page        zero-based page index (default 0)
   * @param size        page size (default 50, max 500)
   */
  @GET
  @Path("/permission-audit/log")
  @RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
  @Tag(name = "Admin")
  @Operation(
    description = "F3: Query the Postgres permission audit log. Returns GRANT/REVOKE/UPDATE events " +
                  "sorted by occurred_at DESC. Supports optional filters: entityAppId, actor, " +
                  "from/to (ISO-8601), and pagination via page/size."
  )
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(
      schema = @Schema(type = SchemaType.ARRAY, implementation = PermissionAuditLogEntryIO.class)
    )
  )
  @APIResponse(description = "forbidden", responseCode = "403")
  @APIResponse(description = "bad request (invalid ISO-8601 date)", responseCode = "400")
  public Response permissionAuditLog(
    @Context SecurityContext securityContext,
    @QueryParam("entityAppId") String entityAppId,
    @QueryParam("actor") String actor,
    @QueryParam("from") String from,
    @QueryParam("to") String to,
    @QueryParam("page") @DefaultValue("0") int page,
    @QueryParam("size") @DefaultValue("50") int size
  ) {
    requireInstanceAdmin(securityContext);

    Instant fromInstant = null;
    Instant toInstant = null;
    try {
      if (from != null && !from.isBlank()) fromInstant = Instant.parse(from);
      if (to != null && !to.isBlank()) toInstant = Instant.parse(to);
    } catch (DateTimeParseException e) {
      return Response.status(Status.BAD_REQUEST)
        .entity(new de.dlr.shepard.common.exceptions.ApiError(
          Status.BAD_REQUEST.getStatusCode(), "BadRequest",
          "Invalid ISO-8601 date in 'from' or 'to' parameter: " + e.getMessage()
        ))
        .build();
    }

    List<PermissionAuditLogEntryIO> rows = permissionAuditLogQueryService.query(
      entityAppId, actor, fromInstant, toInstant, page, size
    );
    return Response.ok(rows).build();
  }

  /**
   * Nuclear instance reset — wipes ALL research data while preserving users,
   * API keys, and instance configuration (ROR, feature toggles, semantic repos, etc.).
   *
   * <p>The caller must be an instance-admin AND supply the exact confirmation phrase
   * {@code "yes drop everything"} to guard against accidental invocation.
   *
   * <p>What is deleted:
   * <ul>
   *   <li>All Neo4j data nodes (Collections, DataObjects, References, Containers,
   *       LabJournalEntries, SemanticAnnotations, Versions, Templates, …)</li>
   *   <li>All MongoDB collections that back file + structured-data containers</li>
   *   <li>All timeseries channel records from Postgres</li>
   *   <li>All Activity / provenance records</li>
   * </ul>
   *
   * <p>What is preserved:
   * <ul>
   *   <li>User, UserGroup, ApiKey nodes</li>
   *   <li>InstanceAdminGrant, InstanceRorConfig</li>
   *   <li>SemanticRepository, OntologyConfig, SemanticConfig</li>
   *   <li>FeatureToggle, SqlTimeseriesConfig</li>
   * </ul>
   *
   * <p>Intended for dev / demo instances. Do not expose on production without
   * additional network-level access control.
   */
  @POST
  @Path("/instance/nuke")
  @RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
  @Tag(name = "Admin")
  @Operation(
    summary = "Nuclear instance reset — wipes all research data (confirmation phrase required).",
    description =
      "Deletes every Collection, DataObject, Reference, Container, LabJournalEntry, " +
      "SemanticAnnotation, Activity, and their secondary-store payloads (MongoDB, Postgres). " +
      "Users, API keys, and instance configuration are preserved.\n\n" +
      "The caller must be instance-admin AND send `confirmPhrase: \"" + NukeService.CONFIRM_PHRASE + "\"` " +
      "in the request body to guard against accidental use."
  )
  @APIResponse(
    responseCode = "200",
    description = "Reset complete — returns deletion counts.",
    content = @Content(schema = @Schema(implementation = NukeResultIO.class))
  )
  @APIResponse(responseCode = "400", description = "Wrong confirmation phrase.")
  @APIResponse(responseCode = "403", description = "Not an instance-admin.")
  public Response nuke(
    @Context SecurityContext securityContext,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = NukeRequestIO.class))
    ) @Valid NukeRequestIO body
  ) {
    requireInstanceAdmin(securityContext);
    if (!nukeService.confirmPhraseValid(body.getConfirmPhrase())) {
      return Response.status(Status.BAD_REQUEST)
        .entity(new ApiError(Status.BAD_REQUEST.getStatusCode(), "BadRequest",
          "confirmPhrase must be exactly \"" + NukeService.CONFIRM_PHRASE + "\""))
        .build();
    }
    NukeResultIO result = nukeService.nuke();
    return Response.ok(result).build();
  }
}
