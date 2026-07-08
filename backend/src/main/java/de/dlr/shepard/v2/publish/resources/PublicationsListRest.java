package de.dlr.shepard.v2.publish.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.publish.PublishableKindRegistry;
import de.dlr.shepard.publish.daos.PublicationDAO;
import de.dlr.shepard.publish.entities.Publication;
import de.dlr.shepard.v2.publish.io.PublicationIO;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
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
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import static de.dlr.shepard.v2.common.ProblemResponse.problem;

/**
 * KIP1k — {@code GET /v2/{kind}/{appId}/publications} list endpoint.
 *
 * <p>Returns every {@link Publication} attached to the entity, ordered
 * {@code mintedAt DESC} (most-recent first — the "current" Publication
 * per the KIP1a append-only convention). The list includes retired rows
 * (so the caller can render the full history); callers that want only
 * active Publications should filter by {@code digitalObjectMutability != "retired"}.
 *
 * <p>This is the "GET helper" the {@link PublishButton} JSDoc
 * (KIP1e {@code KIP1a wire reality} scope-down note) anticipated:
 * <pre>
 *   "A future slice can wire a GET /v2/{kind}/{appId}/publications helper
 *    (or expose HAS_PUBLICATION on the v2 entity-detail shape) and upgrade
 *    the button to show 'Published — view PID' + the popover."
 * </pre>
 *
 * <p>Auth: caller must hold {@code Read} on the entity (same predicate
 * as other v2 GET endpoints that return entity state). This is intentionally
 * less restrictive than {@code POST /publish} (Write/Manage) — a reader
 * should be able to see whether something is published without being
 * a writer.
 *
 * <p>Pagination: optional {@code page}/{@code pageSize} params (default 0/50, max 500).
 * The response envelope is {@link PagedResponseIO} matching the canonical flat alias
 * {@code GET /v2/publications?entityAppId=…}.
 *
 * <p>No sorting or filter query params are exposed — clients that want
 * only active rows filter on {@code digitalObjectMutability} client-side.
 * The DAO already orders by {@code mintedAt DESC} so index [0] is always
 * the most-recent (potentially "current") Publication.
 */
@Produces(MediaType.APPLICATION_JSON)
@Path("/v2/{kind}/{appId}/publications")
@RequestScoped
@Tag(name = "Publish")
public class PublicationsListRest {

  static final String PT_UNAUTHORIZED = "/problems/publish.unauthorized";
  static final String PT_NOT_FOUND = "/problems/publish.not-found";
  static final String PT_KIND_UNSUPPORTED = "/problems/publish.kind.unsupported";
  static final String PT_FORBIDDEN = "/problems/publish.forbidden";

  @Inject
  PublishableKindRegistry kindRegistry;

  @Inject
  PublicationDAO publicationDAO;

  @Inject
  PermissionsService permissionsService;

  @Inject
  EntityIdResolver entityIdResolver;

  @GET
  @Operation(
    operationId = "listPublications",
    summary = "List Publications attached to an entity (most-recent first).",
    description = "Returns :Publication rows attached to the entity ordered by mintedAt DESC. " +
    "Includes retired rows (digitalObjectMutability = 'retired') so callers can render the full " +
    "publication history. Clients wanting only active Publications should filter " +
    "digitalObjectMutability != 'retired' client-side. " +
    "Auth: Read permission on the entity. Optional page/pageSize params (default 0/50, max 500); " +
    "response is a PagedResponseIO envelope matching GET /v2/publications.\n\n" +
    "DEPRECATED (APISIMP-PUBLICATIONS-KIND-PATH-SEGMENT): use the kind-agnostic alias " +
    "GET /v2/publications?entityAppId={appId} instead — it does not require the caller to " +
    "know the entity-kind URL segment. This path is kept for backward compatibility.",
    deprecated = true
  )
  @APIResponse(
    responseCode = "200",
    description = "Paged list of Publications, most-recent first.",
    content = @Content(schema = @Schema(implementation = PagedResponseIO.class)),
    headers = @Header(
      name = "X-Total-Count",
      description = "Total element count before paging.",
      schema = @Schema(type = SchemaType.INTEGER)
    )
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the entity.")
  @APIResponse(responseCode = "404", description = "Unsupported `{kind}` segment, or no entity with `{appId}` of that kind.")
  public Response list(
    @Parameter(description = "Entity-kind URL segment (e.g. 'data-objects', 'collections').", required = true)
    @PathParam("kind") String kind,
    @Parameter(description = "AppId of the entity whose Publications to list.", required = true)
    @PathParam("appId") String appId,
    @Parameter(description = "Zero-based page index (default 0).")
    @QueryParam("page") @DefaultValue("0") @PositiveOrZero int page,
    @Parameter(description = "Page size, 1–500 (default 50).")
    @QueryParam("pageSize") @DefaultValue("50") @Min(1) @Max(500) int pageSize,
    @Context SecurityContext securityContext,
    @Context UriInfo uriInfo
  ) {
    String caller = securityContext.getUserPrincipal() != null ? securityContext.getUserPrincipal().getName() : null;
    if (caller == null) return problem(Response.Status.UNAUTHORIZED, PT_UNAUTHORIZED, "Authentication required",
        "Authentication is required to list publications.");

    var kindOpt = kindRegistry.bySegment(kind);
    if (kindOpt.isEmpty()) {
      return problem(Response.Status.NOT_FOUND, PT_KIND_UNSUPPORTED, "Unsupported publishable kind",
        "No publishable kind matches URL segment '" + kind + "'. Supported: " +
        String.join(", ", kindRegistry.supportedSegments()) + ".");
    }
    // Resolve the OGM id so we can check permissions via PermissionsService.
    // The EntityIdResolver throws NotFoundException when the entity doesn't
    // exist; we surface that as 404 (same handling as PublishRest).
    long ogmId;
    try {
      ogmId = entityIdResolver.resolveLong(appId);
    } catch (NotFoundException nfe) {
      return problem(Response.Status.NOT_FOUND, PT_NOT_FOUND, "Entity not found",
          "No entity with appId '" + appId + "' found.");
    }
    // Read permission is sufficient for listing Publications — a researcher
    // who can see the entity should be able to see its PID history.
    if (!permissionsService.isAccessTypeAllowedForUser(ogmId, AccessType.Read, caller)) {
      return problem(Response.Status.FORBIDDEN, PT_FORBIDDEN, "Access denied",
          "Caller '" + caller + "' lacks Read permission on entity '" + appId + "'.");
    }

    long total = publicationDAO.countByEntityAppId(appId);
    // Widen to long before multiplying to prevent integer overflow;
    // clamp to total so skip never exceeds the actual result-set size.
    int skip = (int) Math.min((long) page * pageSize, total);
    List<PublicationIO> items = publicationDAO.findByEntityAppId(appId, skip, pageSize)
      .stream()
      .map(p -> {
        String resolverUrl = absoluteUrl(uriInfo, "/v2/.well-known/kip/" + p.getPid());
        return PublicationIO.from(p, resolverUrl);
      })
      .toList();

    return Response.ok(new PagedResponseIO<>(items, total, page, pageSize))
      .header("X-Total-Count", total)
      .build();
  }

  /**
   * Build a fully-qualified URL at the supplied application-relative path
   * using the request's own scheme + host + port. Mirrors the same helper
   * in {@link PublishRest} and {@code KipResolverRest} — kept local to
   * avoid promoting it to a public cross-module API.
   */
  static String absoluteUrl(UriInfo uriInfo, String applicationPath) {
    if (uriInfo == null) return applicationPath;
    var base = uriInfo.getBaseUri();
    String scheme = base.getScheme();
    String host = base.getHost();
    int port = base.getPort();
    StringBuilder sb = new StringBuilder();
    sb.append(scheme).append("://").append(host);
    if (port > 0 && !((scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443))) {
      sb.append(":").append(port);
    }
    sb.append(applicationPath.startsWith("/") ? applicationPath : "/" + applicationPath);
    return sb.toString();
  }

}
