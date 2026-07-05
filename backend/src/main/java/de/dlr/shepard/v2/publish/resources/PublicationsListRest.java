package de.dlr.shepard.v2.publish.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.publish.PublishableKind;
import de.dlr.shepard.publish.PublishableKindRegistry;
import de.dlr.shepard.publish.daos.PublicationDAO;
import de.dlr.shepard.publish.entities.Publication;
import de.dlr.shepard.v2.publish.io.PublicationIO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
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
 * <p>Pagination is intentionally absent: entities rarely accumulate
 * more than a handful of Publication rows (one per forced re-mint), so
 * a plain list is appropriate and keeps the consumer side simple.
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
    description = "Returns every :Publication row attached to the entity ordered by mintedAt DESC. " +
    "Includes retired rows (digitalObjectMutability = 'retired') so callers can render the full " +
    "publication history. Clients wanting only active Publications should filter " +
    "digitalObjectMutability != 'retired' client-side. " +
    "Auth: Read permission on the entity. No pagination — entities rarely have more than a " +
    "handful of Publication rows.\n\n" +
    "DEPRECATED (APISIMP-PUBLICATIONS-KIND-PATH-SEGMENT): use the kind-agnostic alias " +
    "GET /v2/publications?entityAppId={appId} instead — it does not require the caller to " +
    "know the entity-kind URL segment. This path is kept for backward compatibility.",
    deprecated = true
  )
  @APIResponse(
    responseCode = "200",
    description = "List of Publications, most-recent first. Empty array when entity has never been published.",
    content = @Content(schema = @Schema(implementation = PublicationIO[].class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the entity.")
  @APIResponse(responseCode = "404", description = "Unsupported `{kind}` segment, or no entity with `{appId}` of that kind.")
  public Response list(
    @Parameter(description = "Entity-kind URL segment (e.g. 'data-objects', 'collections').", required = true)
    @PathParam("kind") String kind,
    @Parameter(description = "AppId of the entity whose Publications to list.", required = true)
    @PathParam("appId") String appId,
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

    // Bounded fetch: entity publication histories are tiny in practice (1–5 rows),
    // but SKIP 0 LIMIT 1000 ensures Cypher never issues a fully unbounded scan.
    List<Publication> rows = publicationDAO.findByEntityAppId(appId, 0, 1000);

    // Build resolver URLs for each Publication. Re-use the same
    // absoluteUrl helper as PublishRest (inlined here to avoid
    // cross-class package-private dependency — the pattern is
    // established in KipResolverRest as well).
    List<PublicationIO> body = rows
      .stream()
      .map(p -> {
        String resolverUrl = absoluteUrl(uriInfo, "/v2/.well-known/kip/" + p.getPid());
        return PublicationIO.from(p, resolverUrl);
      })
      .toList();

    return Response.ok(body).build();
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

  private static Response problem(Response.Status status, String type, String title, String detail) {
    ProblemJson body = new ProblemJson(type, title, status.getStatusCode(), detail, null);
    return Response.status(status).type("application/problem+json").entity(body).build();
  }
}
