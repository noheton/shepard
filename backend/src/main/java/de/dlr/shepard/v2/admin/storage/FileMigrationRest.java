package de.dlr.shepard.v2.admin.storage;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.storage.StorageException;
import de.dlr.shepard.storage.migration.FileMigrationService;
import de.dlr.shepard.storage.migration.FileMigrationState;
import de.dlr.shepard.v2.admin.storage.io.FileMigrationStateIO;
import de.dlr.shepard.v2.admin.storage.io.FileMigrationTriggerIO;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * FS1e1 + FS1e3 — file-storage migration REST endpoints.
 *
 * <ul>
 *   <li>{@code POST /v2/admin/files/migrate} (FS1e1) — trigger a
 *       background migration sweep (returns 202 Accepted
 *       immediately).</li>
 *   <li>{@code GET /v2/admin/files/migrate/status} (FS1e1) — poll
 *       migration progress.</li>
 *   <li>{@code POST /v2/admin/files/migrate/rollback/{appId}}
 *       (FS1e3) — per-file rollback: re-writes bytes from the
 *       current adapter to the previous adapter and clears the
 *       FS1e3 bookkeeping fields. Synchronous.</li>
 * </ul>
 *
 * <p>All endpoints require the {@code instance-admin} role.
 * Migration state is in-memory only; a restart resets to IDLE. Re-
 * running migration is safe — already-migrated files have
 * {@code providerId = target} and are excluded by the Cypher query.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/admin/files/migrate")
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "Admin")
public class FileMigrationRest {

  @Inject
  FileMigrationService migrationService;

  @POST
  @Operation(
    summary = "Trigger a file-storage migration.",
    description = "Copies every ShepardFile whose providerId equals sourceProviderId to the " +
    "targetProviderId adapter, then flips the providerId in Neo4j. Migration runs in the " +
    "background — the endpoint returns 202 immediately. Poll GET .../status to track progress. " +
    "Only one migration may run at a time. Re-running after a partial failure is safe (already-" +
    "migrated files are skipped). OIDs are preserved — existing API clients keep working."
  )
  @RequestBody(
    description = "Source and target storage adapter ids.",
    content = @Content(schema = @Schema(implementation = FileMigrationTriggerIO.class))
  )
  @APIResponse(
    responseCode = "202",
    description = "Migration triggered. Poll GET .../status for progress.",
    content = @Content(schema = @Schema(implementation = FileMigrationStateIO.class))
  )
  @APIResponse(responseCode = "400", description = "Invalid adapter ids or migration already running.")
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response trigger(FileMigrationTriggerIO body) {
    if (body == null
      || body.getSourceProviderId() == null || body.getSourceProviderId().isBlank()
      || body.getTargetProviderId() == null || body.getTargetProviderId().isBlank()) {
      throw new BadRequestException("sourceProviderId and targetProviderId are required");
    }
    FileMigrationState state;
    try {
      state = migrationService.triggerMigration(
        body.getSourceProviderId(), body.getTargetProviderId());
    } catch (IllegalArgumentException e) {
      Log.infof("FileMigrationRest: trigger rejected — %s", e.getMessage());
      throw new BadRequestException(e.getMessage());
    }
    return Response.accepted(FileMigrationStateIO.from(state)).build();
  }

  @GET
  @Path("/status")
  @Operation(
    summary = "Get the current file-storage migration status.",
    description = "Returns the in-memory state of the most recent migration job (IDLE when none " +
    "has been triggered since the last restart)."
  )
  @APIResponse(
    responseCode = "200",
    description = "Current migration state.",
    content = @Content(schema = @Schema(implementation = FileMigrationStateIO.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response status() {
    return Response.ok(FileMigrationStateIO.from(migrationService.getState())).build();
  }

  @POST
  @Path("/rollback/{appId}")
  @Operation(
    summary = "Roll back a single file's storage-adapter swap.",
    description =
      "FS1e3 — per-file rollback. Re-writes the file's bytes from the current adapter (providerId) " +
      "back to the previous adapter (previousProviderId), using the preserved previousLocator, then " +
      "restores providerId and clears the FS1e3 bookkeeping fields (previousProviderId, " +
      "previousLocator, migratedAt, migrationHmac). Synchronous — the response carries the outcome.\n\n" +
      "Refuses with 409 when the :ShepardFile row's previousProviderId is null (never migrated, or " +
      "already rolled back). Refuses with 404 when the appId is unknown. Caller must hold the " +
      "instance-admin role.\n\n" +
      "Note: this endpoint re-writes bytes; it does NOT delete from the current adapter. The orphaned " +
      "current-adapter bytes are catalogued by a future 'sweep-orphans' verb (runbook §17.E)."
  )
  @APIResponse(
    responseCode = "200",
    description = "Rollback succeeded — providerId restored, bookkeeping cleared."
  )
  @APIResponse(
    responseCode = "404",
    description = "No :ShepardFile with that appId exists."
  )
  @APIResponse(
    responseCode = "409",
    description = "The :ShepardFile has nothing to roll back (previousProviderId is null)."
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  @APIResponse(responseCode = "500", description = "Storage adapter failure during rollback.")
  public Response rollback(
    @Parameter(
      description = "The :ShepardFile.appId (UUID v7) to roll back.",
      required = true
    )
    @PathParam("appId") String appId
  ) {
    if (appId == null || appId.isBlank()) {
      throw new BadRequestException("appId path parameter is required");
    }
    try {
      migrationService.rollbackOne(appId);
      Log.infof("FileMigrationRest: per-file rollback succeeded — appId=%s", appId);
      return Response.ok(java.util.Map.of(
        "appId", appId,
        "status", "ROLLED_BACK"
      )).build();
    } catch (IllegalArgumentException e) {
      // Unknown appId → 404
      Log.infof("FileMigrationRest: rollback rejected (unknown appId=%s) — %s", appId, e.getMessage());
      throw new NotFoundException(e.getMessage());
    } catch (IllegalStateException e) {
      // Nothing to roll back → 409 Conflict
      Log.infof("FileMigrationRest: rollback rejected (no previous state, appId=%s) — %s",
        appId, e.getMessage());
      throw new WebApplicationException(e.getMessage(), Response.Status.CONFLICT);
    } catch (StorageException e) {
      Log.errorf(e, "FileMigrationRest: rollback failed for appId=%s — %s", appId, e.getMessage());
      throw new WebApplicationException(
        "Storage adapter failure during rollback: " + e.getMessage(),
        Response.Status.INTERNAL_SERVER_ERROR);
    }
  }
}
