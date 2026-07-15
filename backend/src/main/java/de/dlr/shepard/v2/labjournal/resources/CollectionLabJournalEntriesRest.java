package de.dlr.shepard.v2.labjournal.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.labJournal.entities.LabJournalEntry;
import de.dlr.shepard.context.labJournal.io.LabJournalEntryIO;
import de.dlr.shepard.v2.labjournal.daos.CollectionLabJournalEntriesDAO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import static de.dlr.shepard.v2.common.ProblemResponse.problem;

/**
 * UI-020 — bulk fetch of all lab journal entries within a single Collection.
 *
 * <p>Route:
 * <ul>
 *   <li>{@code GET /v2/collections/{appId}/lab-journal-entries}
 *       — returns every non-deleted {@link LabJournalEntry} attached to any
 *       non-deleted DataObject in the collection, sorted by {@code createdAt DESC}.
 *       Requires Read permission on the Collection.</li>
 * </ul>
 *
 * <p>This closes the N+1 hot bug
 * ({@code aidocs/agent-findings/ui-018-019-hypothesis-recheck-2026-05-24.md}):
 * the legacy frontend issued one
 * {@code GET /v2/collections/{appId}/lab-journal-entries} per DataObject,
 * which scaled to 8500+ concurrent requests on MFFD-Dropbox and exhausted the
 * browser socket pool. The frontend now hits this single endpoint.
 *
 * <p>The response shape mirrors {@link LabJournalEntryIO}; each entry already
 * carries {@code dataObjectAppId} (UUID v7 of the parent DataObject), letting
 * the frontend group client-side without a second round-trip.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/collections/{appId}/lab-journal-entries")
@RequestScoped
@Tag(name = "Collections")
public class CollectionLabJournalEntriesRest {

  private static final String PT_UNAUTHORIZED = "/problems/lab-journal.unauthorized";
  private static final String PT_NOT_FOUND = "/problems/lab-journal.not-found";
  private static final String PT_FORBIDDEN = "/problems/lab-journal.forbidden";

  @Inject
  CollectionLabJournalEntriesDAO entriesDAO;

  @Inject
  PermissionsService permissionsService;

  @Inject
  EntityIdResolver entityIdResolver;

  @GET
  @Operation(
    operationId = "listLabJournalEntries",
    summary = "Bulk list all lab journal entries for a collection (UI-020).",
    description =
      "Walks Collection → DataObject → LabJournalEntry once and returns every " +
      "non-deleted entry, sorted by createdAt descending. Each entry carries " +
      "its dataObjectAppId, so the frontend can group client-side without further " +
      "round-trips. Returns an empty array when the collection has no data " +
      "objects or none of them carry lab journal entries.\n\n" +
      "Auth: Read permission on the Collection. 401 if unauthenticated, " +
      "404 if the appId resolves to nothing, 403 if the caller lacks Read.\n\n" +
      "Pagination: `page` (0-based, default 0) and `pageSize` (1–200, default 50). "
  )
  @APIResponse(
    responseCode = "200",
    description = "Paged envelope: items + total + page + pageSize. Response body `total` carries the count.",
    headers = @Header(name = "X-Total-Count", description = "Total lab-journal entry count before paging.", schema = @Schema(implementation = Long.class)),
    content = @Content(schema = @Schema(implementation = PagedResponseIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the Collection.")
  @APIResponse(responseCode = "404", description = "No Collection with that appId.")
  public Response list(
    @PathParam("appId") String appId,
    @Parameter(description = "Zero-based page index (default 0).")
    @QueryParam("page") @DefaultValue("0") @PositiveOrZero int page,
    @Parameter(description = "Page size, 1–200 (default 50).")
    @QueryParam("pageSize") @DefaultValue("50") @Min(1) @Max(200) int pageSize,
    @Context SecurityContext sc
  ) {
    String caller = sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) return problem(PT_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, "No authenticated principal.");

    long ogmId;
    try {
      ogmId = entityIdResolver.resolveLong(appId);
    } catch (NotFoundException nfe) {
      return problem(PT_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No Collection with appId: " + appId);
    }

    if (!permissionsService.isAccessTypeAllowedForUser(ogmId, AccessType.Read, caller)) {
      return problem(PT_FORBIDDEN, "Forbidden", Response.Status.FORBIDDEN, "Caller lacks Read permission on the Collection.");
    }

    long total = entriesDAO.countByCollectionAppId(appId);
    int skip = page * pageSize;
    List<LabJournalEntry> entries = entriesDAO.findByCollectionAppId(appId, skip, pageSize);
    List<LabJournalEntryIO> ios = new ArrayList<>();
    for (LabJournalEntry e : entries) {
      // Defensive: skip orphan entries whose owning DataObject didn't hydrate.
      // The DAO Cypher projects a depth-1 neighbourhood so the incoming
      // has_labjournalentry edge from DataObject populates the back-reference;
      // this guard handles any residual orphan from a partial migration or OGM
      // hydration drift — closes BUG-LJ-V1-COLL-ID belt-and-braces.
      if (e.getDataObject() == null) {
        Log.warnf(
          "Skipping orphan LabJournalEntry (appId=%s) with null DataObject in collection %s",
          e.getAppId(),
          appId
        );
        continue;
      }
      ios.add(new LabJournalEntryIO(e));
    }
    return Response.ok(new PagedResponseIO<>(ios, total, page, pageSize))
        .header("X-Total-Count", total)
        .build();
  }

}
