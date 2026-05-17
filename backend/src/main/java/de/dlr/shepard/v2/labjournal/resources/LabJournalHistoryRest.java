package de.dlr.shepard.v2.labjournal.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.labJournal.daos.LabJournalEntryDAO;
import de.dlr.shepard.context.labJournal.daos.LabJournalEntryRevisionDAO;
import de.dlr.shepard.context.labJournal.entities.LabJournalEntry;
import de.dlr.shepard.v2.labjournal.io.LabJournalRevisionIO;
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
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * J1d — {@code GET /v2/lab-journal/{entryAppId}/history}.
 *
 * <p>Returns the append-only revision history of a lab journal entry —
 * one element per edit, ordered newest revision first. Each revision
 * captures the entry's content as it existed immediately <em>before</em>
 * the corresponding update was applied.
 *
 * <p>Permission: Read on the parent DataObject — same gate as
 * {@link LabJournalRenderRest}.
 *
 * <p>An empty array is returned when the entry has never been edited.
 *
 * <p>Auth: 401 unauthenticated, 403 forbidden, 404 entry not found or
 * deleted.
 *
 * @see LabJournalEntryRevisionDAO
 */
@Path("/v2/lab-journal")
@RequestScoped
@Tag(name = "Lab journal (v2)")
public class LabJournalHistoryRest {

  @Inject
  LabJournalEntryDAO labJournalEntryDAO;

  @Inject
  LabJournalEntryRevisionDAO labJournalEntryRevisionDAO;

  @Inject
  PermissionsService permissionsService;

  /**
   * List the edit history of a lab journal entry.
   *
   * @param entryAppId the application-level identifier of the {@code LabJournalEntry}.
   * @param sc         the JAX-RS security context providing the caller identity.
   * @return 200 with a (possibly empty) revision array; 401 unauthenticated;
   *         403 forbidden; 404 when no entry with that appId exists or the
   *         entry is deleted.
   */
  @GET
  @Path("/{entryAppId}/history")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(
    summary = "List the edit history of a lab journal entry.",
    description =
      "Returns an append-only revision list for the entry identified by entryAppId. " +
      "Each element captures the entry's content as it existed immediately before " +
      "the corresponding update was applied. Revisions are ordered newest first " +
      "(highest revisionNumber first). An empty array is returned when the entry " +
      "has never been edited. " +
      "Permission: Read on the parent DataObject."
  )
  @APIResponse(
    responseCode = "200",
    description = "Revision history (may be empty).",
    content = @Content(
      mediaType = MediaType.APPLICATION_JSON,
      schema = @Schema(type = SchemaType.ARRAY, implementation = LabJournalRevisionIO.class)
    )
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the parent DataObject.")
  @APIResponse(
    responseCode = "404",
    description = "No LabJournalEntry with that appId, or the entry is deleted."
  )
  public Response history(@PathParam("entryAppId") String entryAppId, @Context SecurityContext sc) {
    // 401 if unauthenticated
    String caller = sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    // Resolve entry — null or deleted → 404
    LabJournalEntry entry = labJournalEntryDAO.findByAppId(entryAppId);
    if (entry == null || entry.isDeleted()) return Response.status(Response.Status.NOT_FOUND).build();

    // Permission check via parent DataObject
    var dataObject = entry.getDataObject();
    if (dataObject == null) return Response.status(Response.Status.NOT_FOUND).build();
    if (!permissionsService.isAccessTypeAllowedForUser(dataObject.getId(), AccessType.Read, caller, 0L)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    // Fetch revisions (newest first, already ordered by DAO query)
    List<LabJournalRevisionIO> revisions = labJournalEntryRevisionDAO
      .findByEntry(entry.getId())
      .stream()
      .map(LabJournalRevisionIO::new)
      .toList();

    return Response.ok(revisions).build();
  }
}
