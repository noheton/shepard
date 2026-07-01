package de.dlr.shepard.v2.container.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.v2.collection.io.PublicationStateIO;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.neo4j.ogm.model.Result;

/**
 * #27-ARCHIVED — owner-or-instance-admin gated PATCH for a Container's
 * publication-state field. Polymorphic across {@code :FileContainer},
 * {@code :TimeseriesContainer}, {@code :StructuredDataContainer}, and
 * plugin-supplied subclasses such as {@code :HdfContainer} — the Cypher
 * matches any node carrying one of those labels by appId, so a single
 * REST resource serves every container kind.
 *
 * <p>Stricter auth than the AbstractContainerService write path: requires
 * Manage permission OR the {@code instance-admin} role (a Write-only
 * contributor cannot archive). This endpoint deliberately bypasses
 * {@link de.dlr.shepard.context.collection.services.ArchiveStateGuard}
 * so an archived container can be flipped back to READY — the guard's
 * job is to block writes against payload, not against the container's
 * own status field.
 */
@Path("/v2/containers/{containerAppId}/publication-state")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Authenticated
@Tag(name = "Containers")
public class ContainerPublicationStateRest {

  private static final String PT_NOT_FOUND = "/problems/container-publication-state.not-found";
  private static final String PT_UNAUTHORIZED = "/problems/container-publication-state.unauthorized";

  private static final Set<String> VALID_STATES = Set.of(
    "DRAFT", "IN_REVIEW", "READY", "PUBLISHED", "ARCHIVED"
  );

  /**
   * Cypher fragment that matches any container kind by id. Plugin containers
   * (HdfContainer, etc.) extending BasicContainer must add their label here
   * for status to be writable via this endpoint — covered today; plugins
   * adding new kinds in the future should extend this list (or, better,
   * we'll move to a shared :Container marker label in a follow-up).
   */
  private static final String CONTAINER_LABEL_FILTER =
    "(c:FileContainer OR c:TimeseriesContainer OR c:StructuredDataContainer OR c:HdfContainer)";

  @Inject
  EntityIdResolver entityIdResolver;

  @Inject
  PermissionsService permissionsService;

  @GET
  @Operation(
    operationId = "getContainerPublicationState",
    summary = "Read the current publication state of a Container.",
    description =
      "Returns the container's `status` and a convenience `archived` boolean. " +
      "Works for any container kind (File / Timeseries / StructuredData / Hdf). " +
      "Auth: Read permission on the container."
  )
  @APIResponse(
    responseCode = "200",
    description = "Current publication state.",
    content = @Content(schema = @Schema(implementation = PublicationStateIO.class))
  )
  @APIResponse(responseCode = "404", description = "No Container with that appId.")
  public Response get(
    @PathParam("containerAppId") String containerAppId,
    @Context SecurityContext sc
  ) {
    Long ogmId = resolveOrNull(containerAppId);
    if (ogmId == null) return problem(PT_NOT_FOUND, "Container not found",
      Response.Status.NOT_FOUND, "no Container with appId '" + containerAppId + "'");

    String caller = sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) return problem(PT_UNAUTHORIZED, "Authentication required",
      Response.Status.UNAUTHORIZED, "caller identity unknown");
    if (!permissionsService.isAccessTypeAllowedForUser(ogmId, AccessType.Read, caller, 0L)) {
      return Response.status(Response.Status.FORBIDDEN)
        .type("application/problem+json")
        .entity(new ProblemJson("/problems/container-publication-state.forbidden",
          "Read access required", 403,
          "caller lacks Read on Container '" + containerAppId + "'", null))
        .build();
    }
    String status = readContainerStatus(ogmId);
    return Response.ok(PublicationStateIO.of(status)).build();
  }

  @PATCH
  @Operation(
    operationId = "patchContainerPublicationState",
    summary = "Flip the publication state of a Container (Owner or instance-admin only).",
    description =
      "Sets the container's `status` to one of DRAFT, IN_REVIEW, READY, " +
      "PUBLISHED, ARCHIVED. When ARCHIVED, every subsequent payload write " +
      "(POST/PATCH/DELETE on files, channels, structured-data rows) returns " +
      "409 until the state is flipped back. Reads are unaffected.\n\n" +
      "Auth: Manage permission on the container OR `instance-admin` role.\n\n" +
      "Body: `{\"state\": \"ARCHIVED\"}`."
  )
  @APIResponse(
    responseCode = "200",
    description = "Publication state updated.",
    content = @Content(schema = @Schema(implementation = PublicationStateIO.class))
  )
  @APIResponse(responseCode = "400", description = "Invalid state value.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Manage permission and is not an instance-admin.")
  @APIResponse(responseCode = "404", description = "No Container with that appId.")
  public Response patch(
    @PathParam("containerAppId") String containerAppId,
    @Valid PublicationStateIO body,
    @Context SecurityContext sc
  ) {
    if (body == null || body.getState() == null || !VALID_STATES.contains(body.getState())) {
      return Response.status(Response.Status.BAD_REQUEST)
        .type("application/problem+json")
        .entity(new ProblemJson("/problems/publication-state.invalid",
          "Invalid publication state", 400,
          "state must be one of " + VALID_STATES, null))
        .build();
    }
    Long ogmId = resolveOrNull(containerAppId);
    if (ogmId == null) return problem(PT_NOT_FOUND, "Container not found",
      Response.Status.NOT_FOUND, "no Container with appId '" + containerAppId + "'");

    String caller = sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) return problem(PT_UNAUTHORIZED, "Authentication required",
      Response.Status.UNAUTHORIZED, "caller identity unknown");

    boolean isInstanceAdmin = sc.isUserInRole(Constants.INSTANCE_ADMIN_ROLE);
    boolean isManager = permissionsService.isAccessTypeAllowedForUser(
      ogmId, AccessType.Manage, caller, 0L
    );
    if (!isInstanceAdmin && !isManager) {
      return Response.status(Response.Status.FORBIDDEN)
        .type("application/problem+json")
        .entity(new ProblemJson("/problems/publication-state.forbidden",
          "Insufficient permission", 403,
          "Only the Container owner (Manage permission) or an instance-admin " +
          "may flip publication-state.", null))
        .build();
    }
    writeContainerStatus(ogmId, body.getState());
    return Response.ok(PublicationStateIO.of(body.getState())).build();
  }

  private Long resolveOrNull(String appId) {
    try {
      return entityIdResolver.resolveLong(appId);
    } catch (NotFoundException nfe) {
      return null;
    }
  }

  private String readContainerStatus(long ogmId) {
    Map<String, Object> params = new HashMap<>();
    params.put("id", ogmId);
    Result r = NeoConnector.getInstance().getNeo4jSession().query(
      "MATCH (c) WHERE id(c) = $id AND " + CONTAINER_LABEL_FILTER + " RETURN c.status AS s LIMIT 1",
      params
    );
    for (Map<String, Object> row : r.queryResults()) {
      Object s = row.get("s");
      return s == null ? null : s.toString();
    }
    return null;
  }

  private void writeContainerStatus(long ogmId, String state) {
    Map<String, Object> params = new HashMap<>();
    params.put("id", ogmId);
    params.put("state", state);
    NeoConnector.getInstance().getNeo4jSession().query(
      "MATCH (c) WHERE id(c) = $id AND " + CONTAINER_LABEL_FILTER +
      " SET c.status = $state, c.updatedAt = timestamp()",
      params
    );
  }

  private static Response problem(String type, String title, Response.Status status, String detail) {
    return Response.status(status).type("application/problem+json")
      .entity(new ProblemJson(type, title, status.getStatusCode(), detail, null)).build();
  }
}
