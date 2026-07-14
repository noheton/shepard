package de.dlr.shepard.v2.labjournal.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.labJournal.daos.LabJournalEntryDAO;
import de.dlr.shepard.context.labJournal.entities.LabJournalEntry;
import de.dlr.shepard.context.labJournal.io.LabJournalEntryIO;
import de.dlr.shepard.context.labJournal.services.LabJournalEntryService;
import de.dlr.shepard.v2.labjournal.io.UpdateLabJournalEntryIO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import static de.dlr.shepard.v2.common.ProblemResponse.problem;

/**
 * APISIMP-LJE-ENTRY-V2-CRUD — single-entry CRUD at
 * {@code /v2/lab-journal/{appId}}.
 *
 * <ul>
 *   <li>{@code GET} — read (Read permission on parent DataObject)</li>
 *   <li>{@code PUT} — update content (Write + must be entry creator)</li>
 *   <li>{@code DELETE} — soft-delete (Write + must be entry creator)</li>
 * </ul>
 *
 * <p>All operations address the entry by its UUID v7 {@code appId}.
 */
@Path("/v2/lab-journal")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
@Tag(name = "Lab journal")
public class LabJournalEntryRest {

  private static final String PT_UNAUTHORIZED = "/problems/lab-journal.unauthorized";
  private static final String PT_NOT_FOUND = "/problems/lab-journal.not-found";
  private static final String PT_FORBIDDEN = "/problems/lab-journal.forbidden";

  @Inject
  LabJournalEntryDAO labJournalEntryDAO;

  @Inject
  LabJournalEntryService labJournalEntryService;

  @Inject
  PermissionsService permissionsService;

  @GET
  @Path("/{appId}")
  @Operation(
    operationId = "getLabJournalEntry",
    summary = "[v2] Get a lab journal entry by appId.",
    description =
      "Returns the LabJournalEntry identified by its UUID v7 appId. " +
      "Permission: Read on the parent DataObject."
  )
  @APIResponse(
    responseCode = "200",
    description = "The LabJournalEntry.",
    content = @Content(
      mediaType = MediaType.APPLICATION_JSON,
      schema = @Schema(implementation = LabJournalEntryIO.class)
    )
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No LabJournalEntry with that appId, or the entry is deleted.")
  public Response getLabJournalEntry(
      @PathParam("appId") String appId,
      @Context SecurityContext sc) {
    String caller = sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) return problem(PT_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, "No authenticated principal.");

    LabJournalEntry entry = labJournalEntryDAO.findByAppId(appId);
    if (entry == null || entry.isDeleted()) return problem(PT_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No LabJournalEntry with appId: " + appId);

    var dataObject = entry.getDataObject();
    if (dataObject == null) return problem(PT_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "LabJournalEntry has no parent DataObject.");
    if (!permissionsService.isAccessAllowedForDataObjectAppId(dataObject.getAppId(), AccessType.Read, caller)) {
      return problem(PT_FORBIDDEN, "Forbidden", Response.Status.FORBIDDEN, "Caller lacks Read permission on the parent DataObject.");
    }

    return Response.ok(new LabJournalEntryIO(entry)).build();
  }

  @PUT
  @Path("/{appId}")
  @Operation(
    operationId = "updateLabJournalEntry",
    summary = "[v2] Update the content of a lab journal entry.",
    description =
      "Replaces the journalContent of the entry identified by appId. " +
      "Only the entry's creator may update it. " +
      "Permission: Write on the parent DataObject + must be the entry creator."
  )
  @APIResponse(
    responseCode = "200",
    description = "Updated LabJournalEntry.",
    content = @Content(
      mediaType = MediaType.APPLICATION_JSON,
      schema = @Schema(implementation = LabJournalEntryIO.class)
    )
  )
  @APIResponse(responseCode = "400", description = "Invalid or missing request body.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission or is not the entry creator.")
  @APIResponse(responseCode = "404", description = "No LabJournalEntry with that appId, or the entry is deleted.")
  public Response updateLabJournalEntry(
      @PathParam("appId") String appId,
      @Valid UpdateLabJournalEntryIO body,
      @Context SecurityContext sc) {
    String caller = sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) return problem(PT_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, "No authenticated principal.");

    LabJournalEntry entry = labJournalEntryDAO.findByAppId(appId);
    if (entry == null || entry.isDeleted()) return problem(PT_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No LabJournalEntry with appId: " + appId);

    var dataObject = entry.getDataObject();
    if (dataObject == null) return problem(PT_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "LabJournalEntry has no parent DataObject.");
    if (!permissionsService.isAccessAllowedForDataObjectAppId(dataObject.getAppId(), AccessType.Write, caller)) {
      return problem(PT_FORBIDDEN, "Forbidden", Response.Status.FORBIDDEN, "Caller lacks Write permission on the parent DataObject.");
    }

    // Creator-only write policy — throws InvalidAuthException (→ 403) if not creator
    labJournalEntryService.assertIsCreator(entry);

    LabJournalEntry updated = labJournalEntryService.updateLabJournalEntry(entry.getId(), body.getJournalContent());
    return Response.ok(new LabJournalEntryIO(updated)).build();
  }

  @DELETE
  @Path("/{appId}")
  @Operation(
    operationId = "deleteLabJournalEntry",
    summary = "[v2] Delete a lab journal entry.",
    description =
      "Soft-deletes the entry identified by appId. " +
      "Only the entry's creator may delete it. " +
      "Permission: Write on the parent DataObject + must be the entry creator."
  )
  @APIResponse(responseCode = "204", description = "Entry deleted.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission or is not the entry creator.")
  @APIResponse(responseCode = "404", description = "No LabJournalEntry with that appId, or the entry is deleted.")
  public Response deleteLabJournalEntry(
      @PathParam("appId") String appId,
      @Context SecurityContext sc) {
    String caller = sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) return problem(PT_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, "No authenticated principal.");

    LabJournalEntry entry = labJournalEntryDAO.findByAppId(appId);
    if (entry == null || entry.isDeleted()) return problem(PT_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No LabJournalEntry with appId: " + appId);

    var dataObject = entry.getDataObject();
    if (dataObject == null) return problem(PT_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "LabJournalEntry has no parent DataObject.");
    if (!permissionsService.isAccessAllowedForDataObjectAppId(dataObject.getAppId(), AccessType.Write, caller)) {
      return problem(PT_FORBIDDEN, "Forbidden", Response.Status.FORBIDDEN, "Caller lacks Write permission on the parent DataObject.");
    }

    // Creator-only write policy — throws InvalidAuthException (→ 403) if not creator
    labJournalEntryService.assertIsCreator(entry);
    labJournalEntryService.deleteLabJournalEntry(entry.getId());
    return Response.noContent().build();
  }
}
