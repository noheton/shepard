package de.dlr.shepard.v2.admin.publish.resources;

import de.dlr.shepard.publish.daos.PublicationDAO;
import de.dlr.shepard.publish.entities.Publication;
import de.dlr.shepard.v2.admin.publish.io.AdminPublicationItemIO;
import de.dlr.shepard.v2.admin.publish.io.AdminPublicationListIO;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * RDM-003 — {@code GET /v2/admin/publications}.
 *
 * <p>Instance-wide PID audit list for {@code instance-admin} operators.
 * Returns all {@code :Publication} nodes across the instance, ordered
 * {@code mintedAt DESC}, paginated. Includes retired rows so operators
 * can audit the full lifecycle of every minted PID.
 *
 * <p>This endpoint closes the "no PID management UI" CRITICAL finding
 * from the RDM Scrutinizer pass
 * ({@code aidocs/agent-findings/rdm-scrutinizer-2026-05-24.md §Top-5 #3}):
 * previously {@code /admin/publications} returned 404 and operators
 * had to {@code curl} the per-entity endpoints to answer
 * "what got published on this instance?".
 *
 * <p>Auth: {@code instance-admin} role via {@code @RolesAllowed} —
 * no per-entity permission check needed; the role gate covers the
 * full instance namespace.
 *
 * <p>Route: {@code GET /v2/admin/publications?page=0&size=25}
 *
 * <p>Read-only: this surface intentionally provides no mutation methods.
 * PID lifecycle (retire, re-mint) stays on the per-entity
 * {@code /v2/{kind}/{appId}/publish} endpoints.
 */
@Produces(MediaType.APPLICATION_JSON)
@Path("/v2/admin/publications")
@RequestScoped
@RolesAllowed("instance-admin")
@Tag(name = "Admin publications (RDM-003)")
public class AdminPublicationsRest {

  static final int DEFAULT_PAGE_SIZE = 25;
  static final int MAX_PAGE_SIZE = 200;

  @Inject
  PublicationDAO publicationDAO;

  @GET
  @Operation(
    summary = "Instance-wide PID audit list (RDM-003).",
    description =
      "Returns all :Publication rows across the instance, ordered mintedAt DESC, paginated. " +
      "Includes retired rows (digitalObjectMutability='retired') so operators can audit the " +
      "full lifecycle of every minted PID. " +
      "Auth: instance-admin role required. Read-only — use DELETE /v2/{kind}/{appId}/publish to retire."
  )
  @APIResponse(
    responseCode = "200",
    description = "Paginated list of all Publications, most-recent first.",
    content = @Content(schema = @Schema(implementation = AdminPublicationListIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Instance-admin role required.")
  public Response list(
    @Parameter(description = "0-based page index.", example = "0")
    @QueryParam("page") @DefaultValue("0") int page,
    @Parameter(description = "Page size (1–200, default 25).", example = "25")
    @QueryParam("size") @DefaultValue("25") int size
  ) {
    // Clamp size to the allowed range to prevent runaway Cypher queries.
    int effectivePage = Math.max(0, page);
    int effectiveSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));

    List<Publication> rows = publicationDAO.findAll(effectivePage, effectiveSize);
    long total = publicationDAO.countAll();

    List<AdminPublicationItemIO> items = rows.stream()
      .map(AdminPublicationItemIO::from)
      .toList();

    return Response.ok(new AdminPublicationListIO(items, effectivePage, effectiveSize, total)).build();
  }
}
