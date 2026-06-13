package de.dlr.shepard.v2.collection.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.context.collection.services.ArchiveStateGuard;
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
 * #27-ARCHIVED — owner-or-instance-admin gated PATCH for a Collection's
 * publication-state field. Mirrors the {@code status} value used in the
 * generic {@code /v2/collections/{appId}} merge-patch but applies a
 * stricter auth gate (Manage permission OR {@code instance-admin} role)
 * and is documented as the single happy path for archiving / unarchiving
 * a Collection.
 *
 * <p>While the generic PATCH on the Collection resource accepts
 * {@code status} too (any Write-permitted user can set it), this dedicated
 * resource is the path the frontend Archive / Unarchive buttons call. It
 * also bypasses the {@link ArchiveStateGuard} so an archived collection
 * can be flipped back to READY — the guard's purpose is to block writes
 * <em>on children</em>, not on the parent's own state field.
 */
@Path("/v2/collections/{collectionAppId}/publication-state")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Authenticated
@Tag(name = "Collection lifecycle")
public class CollectionPublicationStateRest {

  private static final String PT_NOT_FOUND = "/problems/publication-state.not-found";
  private static final String PT_UNAUTHORIZED = "/problems/publication-state.unauthorized";

  private static final Set<String> VALID_STATES = Set.of(
    "DRAFT", "IN_REVIEW", "READY", "PUBLISHED", "ARCHIVED"
  );

  @Inject
  EntityIdResolver entityIdResolver;

  @Inject
  PermissionsService permissionsService;

  @Inject
  AuthenticationContext authenticationContext;

  @GET
  @Operation(
    operationId = "getCollectionPublicationState",
    summary = "Read the current publication state of a Collection.",
    description =
      "Returns the current `status` value of the Collection (DRAFT, IN_REVIEW, " +
      "READY, PUBLISHED, ARCHIVED) plus a convenience `archived` boolean. Auth: " +
      "Read permission on the Collection."
  )
  @APIResponse(
    responseCode = "200",
    description = "Current publication state.",
    content = @Content(schema = @Schema(implementation = PublicationStateIO.class))
  )
  @APIResponse(responseCode = "404", description = "No Collection with that appId.")
  public Response get(
    @PathParam("collectionAppId") String collectionAppId,
    @Context SecurityContext sc
  ) {
    Long ogmId = resolveOrNull(collectionAppId);
    if (ogmId == null) return problem(PT_NOT_FOUND, "Collection not found",
      Response.Status.NOT_FOUND, "no Collection with appId '" + collectionAppId + "'");

    String caller = sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) return problem(PT_UNAUTHORIZED, "Authentication required",
      Response.Status.UNAUTHORIZED, "caller identity unknown");
    if (!permissionsService.isAccessTypeAllowedForUser(ogmId, AccessType.Read, caller, 0L)) {
      return Response.status(Response.Status.FORBIDDEN)
        .type("application/problem+json")
        .entity(new ProblemJson("/problems/publication-state.forbidden",
          "Read access required", 403,
          "caller lacks Read on Collection '" + collectionAppId + "'", null))
        .build();
    }

    String status = readCollectionStatus(ogmId);
    return Response.ok(PublicationStateIO.of(status)).build();
  }

  @PATCH
  @Operation(
    operationId = "patchCollectionPublicationState",
    summary = "Flip the publication state of a Collection (Owner or instance-admin only).",
    description =
      "Sets the Collection's `status` to one of DRAFT, IN_REVIEW, READY, " +
      "PUBLISHED, ARCHIVED. When the new state is ARCHIVED, every subsequent " +
      "write to the Collection's children (DataObjects, References) returns " +
      "409 until the state is flipped back. Reads are unaffected.\n\n" +
      "Auth: Manage permission on the Collection OR the `instance-admin` role. " +
      "A user with only Write permission (the standard contributor role) cannot " +
      "archive — the generic Collection PATCH endpoint would accept the field " +
      "but this dedicated endpoint exists for the stricter Owner gate.\n\n" +
      "Body: `{\"state\": \"ARCHIVED\"}`. Unrecognised state values return 400."
  )
  @APIResponse(
    responseCode = "200",
    description = "Publication state updated.",
    content = @Content(schema = @Schema(implementation = PublicationStateIO.class))
  )
  @APIResponse(responseCode = "400", description = "Invalid state value.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Manage permission and is not an instance-admin.")
  @APIResponse(responseCode = "404", description = "No Collection with that appId.")
  public Response patch(
    @PathParam("collectionAppId") String collectionAppId,
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

    Long ogmId = resolveOrNull(collectionAppId);
    if (ogmId == null) return problem(PT_NOT_FOUND, "Collection not found",
      Response.Status.NOT_FOUND, "no Collection with appId '" + collectionAppId + "'");

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
          "Only the Collection owner (Manage permission) or an instance-admin " +
          "may flip publication-state.", null))
        .build();
    }

    writeCollectionStatus(ogmId, body.getState());
    return Response.ok(PublicationStateIO.of(body.getState())).build();
  }

  private Long resolveOrNull(String appId) {
    try {
      return entityIdResolver.resolveLong(appId);
    } catch (NotFoundException nfe) {
      return null;
    }
  }

  private String readCollectionStatus(long ogmId) {
    Map<String, Object> params = new HashMap<>();
    params.put("id", ogmId);
    Result r = NeoConnector.getInstance().getNeo4jSession().query(
      "MATCH (c:Collection) WHERE id(c) = $id RETURN c.status AS s LIMIT 1", params
    );
    for (Map<String, Object> row : r.queryResults()) {
      Object s = row.get("s");
      return s == null ? null : s.toString();
    }
    return null;
  }

  private void writeCollectionStatus(long ogmId, String state) {
    Map<String, Object> params = new HashMap<>();
    params.put("id", ogmId);
    params.put("state", state);
    NeoConnector.getInstance().getNeo4jSession().query(
      "MATCH (c:Collection) WHERE id(c) = $id SET c.status = $state, c.updatedAt = timestamp()",
      params
    );
  }

  private static Response problem(String type, String title, Response.Status status, String detail) {
    return Response.status(status).type("application/problem+json")
      .entity(new ProblemJson(type, title, status.getStatusCode(), detail, null)).build();
  }
}
