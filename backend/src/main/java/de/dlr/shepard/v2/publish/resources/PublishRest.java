package de.dlr.shepard.v2.publish.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.publish.PublishableKind;
import de.dlr.shepard.publish.PublishableKindRegistry;
import de.dlr.shepard.publish.minter.MinterException;
import de.dlr.shepard.publish.minter.MinterNotInstalledException;
import de.dlr.shepard.publish.services.PublishService;
import de.dlr.shepard.v2.publish.io.PublicationIO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import java.util.LinkedHashMap;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * KIP1a publish surface: {@code POST /v2/{kind}/{appId}/publish}
 * mints a PID via the active {@link de.dlr.shepard.publish.minter.Minter}
 * and attaches a {@link de.dlr.shepard.publish.entities.Publication}
 * row to the entity. Designed in {@code aidocs/66 §4.1}.
 *
 * <p>Supported {@code {kind}} segments in KIP1a:
 * {@code data-objects}, {@code collections}; future slices add bundles
 * / files / lab-journal-entries. The supported set is sourced from
 * {@link PublishableKindRegistry} so adding a kind doesn't change the
 * URL shape.
 *
 * <p>Permission: caller must hold {@code Write} or {@code Manage} on
 * the target entity (per {@code aidocs/66 §4.1}).
 *
 * <p>Idempotency: a second POST on an already-published entity
 * returns the existing Publication (200 OK, same PID, no fresh
 * mint). With {@code ?force=true}, a new PID is minted and attached
 * as an additional row — the most recent is "current" per the
 * KIP append-only convention.
 */
@Produces(MediaType.APPLICATION_JSON)
@Path("/v2/{kind}/{appId}/publish")
@RequestScoped
@Tag(name = "Publish (v2)")
public class PublishRest {

  @Inject
  PublishableKindRegistry kindRegistry;

  @Inject
  PublishService publishService;

  @Inject
  PermissionsService permissionsService;

  @Inject
  EntityIdResolver entityIdResolver;

  @POST
  @Operation(
    summary = "Publish an entity — mint a PID via the active Minter and attach a Publication row.",
    description = "Idempotent on the first call: a re-POST without `?force=true` returns the existing " +
    "Publication. With `?force=true`, a fresh PID is minted and attached as an additional row. " +
    "The returned `resolverUrl` is the public `/v2/.well-known/kip/{pid-suffix}` URL clients use to " +
    "dereference the PID at this shepard instance."
  )
  @APIResponse(
    responseCode = "200",
    description = "Publication row (existing or freshly-minted).",
    content = @Content(schema = @Schema(implementation = PublicationIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write/Manage permission on the entity.")
  @APIResponse(responseCode = "404", description = "Unsupported `{kind}` segment, or no entity with `{appId}` of that kind.")
  @APIResponse(responseCode = "500", description = "Active Minter failed; problem+json carries the operator-readable reason.")
  @APIResponse(
    responseCode = "503",
    description = "No minter installed (KIP1h). problem+json `publish.minter.not-installed` — install a minter plugin and set `shepard.publish.minter=<id>`."
  )
  public Response publish(
    @Parameter(description = "Entity-kind URL segment (e.g. 'data-objects', 'collections').", required = true)
    @PathParam("kind") String kind,
    @Parameter(description = "AppId of the entity to publish.", required = true) @PathParam("appId") String appId,
    @Parameter(description = "When true, mint a fresh PID even if the entity already has a Publication.")
    @QueryParam("force") @DefaultValue("false") boolean force,
    @Context SecurityContext securityContext,
    @Context UriInfo uriInfo
  ) {
    String caller = securityContext.getUserPrincipal() != null ? securityContext.getUserPrincipal().getName() : null;
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    var kindOpt = kindRegistry.bySegment(kind);
    if (kindOpt.isEmpty()) {
      return problem(
        Response.Status.NOT_FOUND,
        "https://shepard.dlr.de/problems/publish.kind.unsupported",
        "Unsupported publishable kind",
        "No publishable kind matches URL segment '" +
        kind +
        "'. Supported: " +
        String.join(", ", kindRegistry.supportedSegments()) +
        "."
      );
    }
    PublishableKind k = kindOpt.get();

    long ogmId;
    try {
      ogmId = entityIdResolver.resolveLong(appId);
    } catch (NotFoundException nfe) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    // Per aidocs/66 §4.1: caller must hold Write OR Manage. AccessType.Write
    // already maps to "Writer or Manager" in PermissionsService#rolesGrantAccess
    // (case Write -> isWriter || isManager). Owner-on-entity counts as
    // manager and is therefore admitted.
    if (!permissionsService.isAccessTypeAllowedForUser(ogmId, AccessType.Write, caller, 0L)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    String locatorUrl = absoluteUrl(uriInfo, "/v2/" + k.urlSegment() + "/" + appId);

    PublishService.PublishOutcome outcome;
    try {
      outcome = publishService.publish(k, appId, locatorUrl, caller, force);
    } catch (NotFoundException nfe) {
      // Entity exists at appId-level but isn't of the requested kind.
      return problem(
        Response.Status.NOT_FOUND,
        "https://shepard.dlr.de/problems/publish.entity.wrong-kind",
        "Entity is not of the requested kind",
        nfe.getMessage()
      );
    } catch (MinterNotInstalledException mnie) {
      // KIP1h: no minter wired (unset config, missing plugin, or
      // disabled bean). 503 RFC 7807 — operator gets an actionable
      // hint pointing at plugins/minter-local/.
      return problem(
        Response.Status.SERVICE_UNAVAILABLE,
        "https://shepard.dlr.de/problems/publish.minter.not-installed",
        "No minter installed",
        mnie.getMessage()
      );
    } catch (MinterException me) {
      return problem(
        Response.Status.INTERNAL_SERVER_ERROR,
        "https://shepard.dlr.de/problems/publish.minter.failed",
        "Active minter failed",
        me.getMessage()
      );
    }

    String resolverUrl = absoluteUrl(uriInfo, "/v2/.well-known/kip/" + outcome.publication().getPid());
    return Response.ok(PublicationIO.from(outcome.publication(), resolverUrl)).build();
  }

  /**
   * Build a fully-qualified URL at the supplied application-relative
   * path (e.g. {@code /v2/data-objects/01HF.../publish}) using the
   * request's own scheme + host + port — i.e. the URL the caller is
   * actually reaching this shepard at. No new config key required.
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
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("type", type);
    body.put("title", title);
    body.put("status", status.getStatusCode());
    body.put("detail", detail);
    return Response.status(status).type("application/problem+json").entity(body).build();
  }
}
