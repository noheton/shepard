package de.dlr.shepard.v2.labjournal.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.labJournal.daos.LabJournalEntryDAO;
import de.dlr.shepard.context.labJournal.entities.LabJournalEntry;
import de.dlr.shepard.context.labJournal.services.LabJournalRenderService;
import de.dlr.shepard.v2.labjournal.io.LabJournalRenderIO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * J1a — {@code GET /v2/lab-journal/{appId}/render}.
 *
 * <p>Returns the sanitised HTML produced by rendering the entry's
 * {@code journalContent} as CommonMark + GFM markdown. This is a
 * read-only, side-effect-free transformation — the stored content is
 * never changed.
 *
 * <p>Content negotiation:
 * <ul>
 *   <li>{@code Accept: text/html} → bare HTML string,
 *       {@code Content-Type: text/html; charset=utf-8}.</li>
 *   <li>{@code Accept: application/json} (or default) →
 *       {@link LabJournalRenderIO} envelope with {@code html} and
 *       {@code sourceLength} fields.</li>
 * </ul>
 *
 * <p>Permission: Read on the parent DataObject — same gate as the
 * existing {@code GET /shepard/api/labJournalEntries/{id}} endpoint.
 *
 * <p>Backwards compat: plain-text entries (pre-J1a) render as
 * {@code <p>} elements (CommonMark passes plain text through
 * unchanged). No migration required.
 *
 * @see LabJournalRenderService
 */
@Path("/v2/lab-journal")
@RequestScoped
@Tag(name = "Lab journal (v2)")
public class LabJournalRenderRest {

  @Inject
  LabJournalEntryDAO labJournalEntryDAO;

  @Inject
  LabJournalRenderService renderService;

  @Inject
  PermissionsService permissionsService;

  /**
   * Render a lab journal entry's content as sanitised HTML.
   *
   * @param appId the application-level identifier of the {@code LabJournalEntry}.
   * @param sc    the JAX-RS security context providing the caller identity.
   * @return 200 with HTML or JSON body; 401 unauthenticated; 403 forbidden; 404 not found.
   */
  @GET
  @Path("/{appId}/render")
  @Produces({ MediaType.TEXT_HTML, MediaType.APPLICATION_JSON })
  @Operation(
    summary = "Render a lab journal entry as sanitised HTML.",
    description =
      "Parses the entry's journalContent field as CommonMark + GFM markdown " +
      "(tables, strikethrough, task-list items) and returns sanitised HTML. " +
      "javascript: hrefs are stripped (sanitizeUrls=true). " +
      "Plain-text entries render as <p> elements — no migration required. " +
      "Content negotiation: Accept: text/html returns the raw HTML string; " +
      "Accept: application/json (or default) returns {html, sourceLength}. " +
      "Permission: Read on the parent DataObject."
  )
  @APIResponse(
    responseCode = "200",
    description = "Rendered HTML.",
    content = {
      @Content(mediaType = MediaType.TEXT_HTML, schema = @Schema(type = SchemaType.STRING)),
      @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = LabJournalRenderIO.class)),
    }
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No LabJournalEntry with that appId, or the entry is deleted.")
  public Response render(@PathParam("appId") String appId, @Context SecurityContext sc) {
    // Auth gate
    String caller = sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    // Resolve entry — null or deleted both → 404
    LabJournalEntry entry = labJournalEntryDAO.findByAppId(appId);
    if (entry == null || entry.isDeleted()) return Response.status(Response.Status.NOT_FOUND).build();

    // Permission check via parent DataObject
    var dataObject = entry.getDataObject();
    if (dataObject == null) return Response.status(Response.Status.NOT_FOUND).build();
    if (!permissionsService.isAccessTypeAllowedForUser(dataObject.getId(), AccessType.Read, caller)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    // Render
    String content = entry.getContent();
    String html = renderService.renderToHtml(content);
    int sourceLength = content != null ? content.length() : 0;

    return Response.ok(new LabJournalRenderIO(html, sourceLength)).build();
  }
}
