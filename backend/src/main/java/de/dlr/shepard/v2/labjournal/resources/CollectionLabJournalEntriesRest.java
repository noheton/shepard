package de.dlr.shepard.v2.labjournal.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.labJournal.entities.LabJournalEntry;
import de.dlr.shepard.context.labJournal.io.LabJournalEntryIO;
import de.dlr.shepard.v2.labjournal.daos.CollectionLabJournalEntriesDAO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * UI-020 — bulk fetch of all lab journal entries within a single Collection.
 *
 * <p>Route:
 * <ul>
 *   <li>{@code GET /v2/collections/{collectionAppId}/lab-journal-entries}
 *       — returns every non-deleted {@link LabJournalEntry} attached to any
 *       non-deleted DataObject in the collection, sorted by {@code createdAt DESC}.
 *       Requires Read permission on the Collection.</li>
 * </ul>
 *
 * <p>This closes the N+1 hot bug
 * ({@code aidocs/agent-findings/ui-018-019-hypothesis-recheck-2026-05-24.md}):
 * the legacy frontend issued one
 * {@code GET /shepard/api/labJournalEntries?dataObjectId=N} per DataObject,
 * which scaled to 8500+ concurrent requests on MFFD-Dropbox and exhausted the
 * browser socket pool. The frontend now hits this single endpoint.
 *
 * <p>The response shape mirrors {@link LabJournalEntryIO}; each entry already
 * carries {@code dataObjectId} (the DataObject's shepardId — same numeric space
 * as {@code GET /shepard/api/dataObjects/{id}}), letting the frontend group
 * client-side without a second round-trip.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/collections/{collectionAppId}/lab-journal-entries")
@RequestScoped
@Tag(name = "Collections — lab journal entries (UI-020)")
public class CollectionLabJournalEntriesRest {

  @Inject
  CollectionLabJournalEntriesDAO entriesDAO;

  @Inject
  PermissionsService permissionsService;

  @Inject
  EntityIdResolver entityIdResolver;

  @GET
  @Operation(
    summary = "Bulk list all lab journal entries for a collection (UI-020).",
    description =
      "Walks Collection → DataObject → LabJournalEntry once and returns every " +
      "non-deleted entry, sorted by createdAt descending. Each entry carries " +
      "its dataObjectId, so the frontend can group client-side without further " +
      "round-trips. Returns an empty array when the collection has no data " +
      "objects or none of them carry lab journal entries.\n\n" +
      "Auth: Read permission on the Collection. 401 if unauthenticated, " +
      "404 if the collectionAppId resolves to nothing, 403 if the caller lacks Read."
  )
  @APIResponse(
    responseCode = "200",
    description = "All lab journal entries in the collection (may be empty).",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = LabJournalEntryIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the Collection.")
  @APIResponse(responseCode = "404", description = "No Collection with that appId.")
  public Response list(
    @PathParam("collectionAppId") String collectionAppId,
    @Context SecurityContext sc
  ) {
    String caller = sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    long ogmId;
    try {
      ogmId = entityIdResolver.resolveLong(collectionAppId);
    } catch (NotFoundException nfe) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    if (!permissionsService.isAccessTypeAllowedForUser(ogmId, AccessType.Read, caller)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    List<LabJournalEntry> entries = entriesDAO.findByCollectionAppId(collectionAppId);
    List<LabJournalEntryIO> ios = new ArrayList<>(entries.size());
    for (LabJournalEntry e : entries) {
      ios.add(new LabJournalEntryIO(e));
    }
    return Response.ok(ios).build();
  }
}
