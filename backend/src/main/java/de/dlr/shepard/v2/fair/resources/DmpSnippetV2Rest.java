package de.dlr.shepard.v2.fair.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.daos.CollectionPropertiesDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.v2.fair.io.DmpSnippetIO;
import de.dlr.shepard.v2.fair.services.DmpSnippetService;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.Optional;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * FAIR7 — {@code GET /v2/collections/{appId}/dmp-snippet}.
 *
 * <p>Returns a copy-paste-ready Markdown DMP block pre-filled with all
 * FAIR-relevant fields from the Collection and its DataObjects. Researchers
 * paste the output directly into DFG / EU Horizon Europe DMP forms.
 *
 * <h3>Content negotiation</h3>
 * <ul>
 *   <li>Default ({@code Accept: *\/*} or {@code text/markdown}): returns the
 *       Markdown block as a plain {@code text/markdown} body.</li>
 *   <li>{@code Accept: application/json}: returns a {@link DmpSnippetIO} JSON
 *       wrapper carrying the same Markdown text in the {@code snippet} field
 *       plus a {@code missingFields} array listing FAIR fields that are not
 *       yet set.</li>
 * </ul>
 *
 * <h3>Auth</h3>
 * Same as {@code GET /v2/collections/{appId}} — Read permission required.
 * Returns 401 when unauthenticated, 404 when the appId doesn't resolve, 403
 * when the caller lacks Read access.
 *
 * <p>Cross-references: {@code aidocs/16} FAIR7 row; {@code aidocs/34} additive
 * endpoint ledger; {@code aidocs/44} feature matrix. Pure projection — no new
 * entities, no migrations.
 */
@Path("/v2/collections")
@Produces({ "text/markdown", MediaType.APPLICATION_JSON })
@RequestScoped
@Authenticated
@Tag(name = "FAIR DMP snippet")
public class DmpSnippetV2Rest {

  @Inject
  CollectionPropertiesDAO collectionPropertiesDAO;

  @Inject
  PermissionsService permissionsService;

  @Inject
  CollectionService collectionService;

  @Inject
  DmpSnippetService dmpSnippetService;

  /**
   * Generate a DMP snippet for the Collection identified by {@code appId}.
   *
   * @param collectionAppId UUID v7 appId of the Collection
   * @param accept          value of the HTTP {@code Accept} header; drives
   *                        content negotiation (JSON vs. Markdown)
   * @param sc              security context for caller identity
   * @return 200 with Markdown body or JSON wrapper; 401/403/404 on auth/id errors
   */
  @GET
  @Path("/{appId}/dmp-snippet")
  @Operation(
    summary = "Generate a FAIR DMP snippet for a Collection.",
    description =
      "Returns a copy-paste-ready Markdown Data Management Plan (DMP) block pre-filled with " +
      "all FAIR-relevant fields from the Collection (`name`, `description`, `license`, " +
      "`accessRights`) and its DataObjects (`createdByOrcid`, `embargoEndDate`, reference " +
      "kinds). Designed for researchers who need to fill in DFG / EU Horizon Europe DMP forms.\n\n" +
      "**Content negotiation:**\n" +
      "- `Accept: text/markdown` (default): returns the Markdown block as plain text.\n" +
      "- `Accept: application/json`: returns a JSON wrapper with `collectionAppId`, " +
      "`collectionName`, `snippet` (the same Markdown), and `missingFields` " +
      "(list of FAIR fields that are null/blank and should be set).\n\n" +
      "**Missing-fields detection:** the `missingFields` array (JSON variant) lists " +
      "field names that improve the DMP statement if populated: `license`, `accessRights`, " +
      "`orcid` (no DataObject has a `createdByOrcid`), `description` (Collection is blank).\n\n" +
      "**Auth:** Read permission on the Collection. `404` for unknown appIds; `403` for " +
      "callers without Read access; `401` when unauthenticated.\n\n" +
      "**PID note:** persistent identifier (PID) lookup is not implemented in this version. " +
      "The snippet always emits 'no PID assigned'; see `aidocs/16` FAIR7 row.\n\n" +
      "**No side effects:** pure read projection — no entity writes, no migrations required."
  )
  @APIResponse(
    responseCode = "200",
    description = "DMP snippet as Markdown (text/markdown) or JSON wrapper (application/json).",
    content = {
      @Content(mediaType = "text/markdown", schema = @Schema(type = org.eclipse.microprofile.openapi.annotations.enums.SchemaType.STRING)),
      @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = DmpSnippetIO.class))
    }
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the Collection.")
  @APIResponse(responseCode = "404", description = "No Collection with that appId.")
  public Response getDmpSnippet(
    @PathParam("appId") String collectionAppId,
    @HeaderParam(HttpHeaders.ACCEPT) String accept,
    @Context SecurityContext sc
  ) {
    // ── 401 ──────────────────────────────────────────────────────────────
    String caller = sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    // ── 404 ──────────────────────────────────────────────────────────────
    Optional<Long> ogmId = collectionPropertiesDAO.findCollectionIdByAppId(collectionAppId);
    if (ogmId.isEmpty()) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    // ── 403 ──────────────────────────────────────────────────────────────
    if (!permissionsService.isAccessTypeAllowedForUser(ogmId.get(), AccessType.Read, caller, 0L)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    // ── Load + generate ───────────────────────────────────────────────────
    Collection collection = collectionService.getCollectionWithDataObjectsAndIncomingReferences(ogmId.get());
    DmpSnippetIO io = dmpSnippetService.generate(collection);

    // ── Content negotiation ───────────────────────────────────────────────
    boolean wantsJson = accept != null && accept.contains(MediaType.APPLICATION_JSON);
    if (wantsJson) {
      return Response.ok(io, MediaType.APPLICATION_JSON).build();
    }
    return Response.ok(io.getSnippet(), "text/markdown").build();
  }
}
