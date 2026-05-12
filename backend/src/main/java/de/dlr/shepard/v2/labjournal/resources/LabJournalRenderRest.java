package de.dlr.shepard.v2.labjournal.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.util.MarkdownRenderer;
import de.dlr.shepard.context.labJournal.daos.LabJournalEntryDAO;
import de.dlr.shepard.context.labJournal.entities.LabJournalEntry;
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
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * {@code GET /v2/lab-journal/{appId}/render} — returns a sanitised HTML render
 * of a LabJournalEntry's body (CommonMark + GFM, OWASP Safelist.relaxed()).
 * J1a per {@code aidocs/37}.
 */
@Path("/v2/lab-journal")
@RequestScoped
@Tag(name = "Lab Journal (v2)")
public class LabJournalRenderRest {

  @Inject
  LabJournalEntryDAO labJournalEntryDAO;

  @Inject
  PermissionsService permissionsService;

  @Inject
  EntityIdResolver entityIdResolver;

  @GET
  @Path("/{appId}/render")
  @Produces(MediaType.TEXT_HTML)
  @Operation(
    summary = "Render a LabJournalEntry as sanitised HTML.",
    description = "Parses the entry's body as CommonMark (GFM tables + strikethrough enabled) " +
    "and returns the sanitised HTML. Sanitisation uses OWASP Jsoup Safelist.relaxed(): " +
    "block + inline elements, links, and images are allowed; scripts and iframes are stripped. " +
    "Returns an empty string for a null body. Requires Read permission on the owning Collection."
  )
  @APIResponse(responseCode = "200", description = "Sanitised HTML of the entry body.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the owning Collection.")
  @APIResponse(responseCode = "404", description = "No LabJournalEntry with the supplied appId.")
  public Response render(@PathParam("appId") String appId, @Context SecurityContext securityContext) {
    String caller = securityContext.getUserPrincipal() != null ? securityContext.getUserPrincipal().getName() : null;
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    long ogmId;
    try {
      ogmId = entityIdResolver.resolveLong(appId);
    } catch (NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    LabJournalEntry entry = labJournalEntryDAO.findByNeo4jId(ogmId);
    if (entry == null || entry.isDeleted()) return Response.status(Response.Status.NOT_FOUND).build();

    long collectionId = entry.getDataObject().getCollection().getId();
    if (!permissionsService.isAccessTypeAllowedForUser(collectionId, AccessType.Read, caller)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    return Response.ok(MarkdownRenderer.renderToSafeHtml(entry.getContent())).build();
  }
}
