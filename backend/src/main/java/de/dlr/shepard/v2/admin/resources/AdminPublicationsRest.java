package de.dlr.shepard.v2.admin.resources;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.publish.daos.PublicationDAO;
import de.dlr.shepard.v2.publish.io.PublicationIO;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.Comparator;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * {@code GET /v2/admin/publications} — instance-admin read-only listing of
 * every {@code :Publication} node across the entire instance (RDM-003).
 *
 * <p>Replaces the "what got published?" curl loop for operators: surfaces
 * all minted PIDs with retired-state badges, per-PID minter/mintedAt/
 * publishedBy columns, and entity-kind/entityAppId — exactly the columns
 * the admin pane's Publications table needs.
 *
 * <p>Pairs with KIP1f tombstone surface: {@code digitalObjectMutability}
 * is {@code null} on active rows, {@code "retired"} after a
 * {@code DELETE /v2/{kind}/{appId}/publish} retire call.
 *
 * <p>Gated on {@code instance-admin} per {@code aidocs/51} — only an
 * operator should be able to list all PIDs instance-wide.
 *
 * <p>Ordered {@code mintedAt DESC} so the newest publications appear first.
 * Pagination is intentionally omitted from this slice: the typical instance
 * has O(hundreds) of PIDs, not millions, and a simple list is what the
 * admin audit loop needs (DMP §5 data-security audit trail). Pagination
 * can be added additively in a follow-up slice.
 */
@Produces(MediaType.APPLICATION_JSON)
@Path("/v2/admin/publications")
@RequestScoped
@Tag(name = "Admin publications (v2)")
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
public class AdminPublicationsRest {

  @Inject
  PublicationDAO publicationDAO;

  @Context
  UriInfo uriInfo;

  @GET
  @Operation(
    summary = "List all minted PIDs across the instance (instance-admin only).",
    description =
      "Returns every :Publication row in the graph, sorted mintedAt DESC. " +
      "Active rows have digitalObjectMutability=null; retired rows have " +
      "digitalObjectMutability='retired'. Enables operator audit without a " +
      "Cypher shell (RDM-003 / DMP §5 data-security audit trail)."
  )
  @APIResponse(
    responseCode = "200",
    description = "List of all Publication records, newest first.",
    content = @Content(schema = @Schema(implementation = PublicationIO[].class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response list() {
    String resolverBase = resolverBase(uriInfo);

    List<PublicationIO> result = publicationDAO.findAll().stream()
      .sorted(Comparator.comparingLong(
        p -> p.getMintedAt() != null ? -p.getMintedAt() : 0L
      ))
      .map(p -> PublicationIO.from(p, resolverBase + p.getPid()))
      .toList();

    return Response.ok(result).build();
  }

  /**
   * Derive the {@code /v2/.well-known/kip/} base from the request's base URI.
   * Strips the default port (80/443) for canonical URLs, matching the pattern
   * in {@link de.dlr.shepard.v2.publish.resources.PublishRest#absoluteUrl}.
   */
  static String resolverBase(UriInfo uriInfo) {
    java.net.URI base = uriInfo.getBaseUri();
    String scheme = base.getScheme();
    int port = base.getPort();
    boolean isDefaultPort = (port == 80 && "http".equals(scheme)) ||
                            (port == 443 && "https".equals(scheme)) ||
                            port == -1;
    String host = isDefaultPort
      ? base.getHost()
      : base.getHost() + ":" + port;
    return scheme + "://" + host + "/v2/.well-known/kip/";
  }
}
