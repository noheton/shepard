package de.dlr.shepard.v2.publish.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.publish.daos.PublicationDAO;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import de.dlr.shepard.v2.publish.io.PublicationIO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import static de.dlr.shepard.v2.common.ProblemResponse.problem;

/**
 * APISIMP-PUBLICATIONS-KIND-PATH-SEGMENT — kind-agnostic flat alias for the
 * per-entity publications list.
 *
 * <p>{@code GET /v2/publications?entityAppId={appId}} returns the same list as
 * {@code GET /v2/{kind}/{appId}/publications} without requiring the caller to
 * know the entity's kind URL segment. The kind-parameterised path is kept for
 * backward compatibility but is marked {@code deprecated} in OpenAPI.
 *
 * <p>Permission model: Read access
 * on the entity is sufficient. The OGM Long resolution is kind-agnostic —
 * {@link EntityIdResolver#resolveLong(String)} matches on {@code appId} across
 * all node labels, so no kind is needed for the permission gate.
 */
@Produces(MediaType.APPLICATION_JSON)
@Path("/v2/publications")
@RequestScoped
@Tag(name = "Publish")
public class FlatPublicationsRest {

  static final String PT_UNAUTHORIZED = "/problems/publish.unauthorized";
  static final String PT_NOT_FOUND    = "/problems/publish.not-found";
  static final String PT_FORBIDDEN    = "/problems/publish.forbidden";
  static final String PT_BAD_REQUEST  = "/problems/publish.bad-request";

  @Inject
  PublicationDAO publicationDAO;

  @Inject
  PermissionsService permissionsService;

  @Inject
  EntityIdResolver entityIdResolver;

  @GET
  @Operation(
    operationId = "listPublicationsFlat",
    summary = "List Publications by entityAppId (kind-agnostic).",
    description = "Returns the same list as GET /v2/{kind}/{appId}/publications without requiring " +
      "the caller to know the entity-kind URL segment. Pass any publishable entity's appId; the " +
      "server resolves the entity kind internally. Ordered mintedAt DESC (most-recent first). " +
      "Includes retired rows (digitalObjectMutability = 'retired'). " +
      "Auth: Read permission on the entity.\n\n" +
      "Pagination: `page` (0-based, default 0) and `pageSize` (1–200, default 50). "
  )
  @APIResponse(
    responseCode = "200",
    description = "Paged envelope: items + total + page + pageSize. Response body `total` carries the count.",
    content = @Content(schema = @Schema(implementation = PagedResponseIO.class))
  )
  @APIResponse(responseCode = "400", description = "Missing or blank entityAppId parameter (RFC 7807).")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the entity.")
  @APIResponse(responseCode = "404", description = "No entity with that appId.")
  public Response list(
    @Parameter(description = "AppId of the entity whose Publications to list.", required = true)
    @QueryParam("entityAppId") String entityAppId,
    @Parameter(description = "Zero-based page index (default 0).")
    @QueryParam("page") @DefaultValue("0") @PositiveOrZero int page,
    @Parameter(description = "Page size, 1–200 (default 50).")
    @QueryParam("pageSize") @DefaultValue("50") @Min(1) @Max(200) int pageSize,
    @Context SecurityContext securityContext,
    @Context UriInfo uriInfo
  ) {
    String caller = securityContext.getUserPrincipal() != null
      ? securityContext.getUserPrincipal().getName() : null;
    if (caller == null) {
      return problem(Response.Status.UNAUTHORIZED, PT_UNAUTHORIZED, "Authentication required",
        "Authentication is required to list publications.");
    }

    if (entityAppId == null || entityAppId.isBlank()) {
      return problem(Response.Status.BAD_REQUEST, PT_BAD_REQUEST, "Missing entityAppId",
        "Query parameter 'entityAppId' is required.");
    }

    long ogmId;
    try {
      ogmId = entityIdResolver.resolveLong(entityAppId);
    } catch (NotFoundException nfe) {
      return problem(Response.Status.NOT_FOUND, PT_NOT_FOUND, "Entity not found",
        "No entity with appId '" + entityAppId + "' found.");
    }

    if (!permissionsService.isAccessTypeAllowedForUser(ogmId, AccessType.Read, caller)) {
      return problem(Response.Status.FORBIDDEN, PT_FORBIDDEN, "Access denied",
        "Caller '" + caller + "' lacks Read permission on entity '" + entityAppId + "'.");
    }

    long total = publicationDAO.countByEntityAppId(entityAppId);
    // Widen to long before multiplying to prevent CWE-190 integer overflow;
    // clamp to total so skip never exceeds the actual result-set size.
    int skip = (int) Math.min((long) page * pageSize, total);
    List<PublicationIO> page_ = publicationDAO
      .findByEntityAppId(entityAppId, skip, pageSize)
      .stream()
      .map(p -> {
        String resolverUrl = PublishRestUtils.absoluteUrl(uriInfo, "/v2/.well-known/kip/" + p.getPid());
        return PublicationIO.from(p, resolverUrl);
      })
      .toList();

    return Response.ok(new PagedResponseIO<>(page_, total, page, pageSize))
      .build();
  }

}
