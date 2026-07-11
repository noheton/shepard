package de.dlr.shepard.v2.importer.resources;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.v2.importer.entities.ImportLock;
import de.dlr.shepard.v2.importer.io.ImportLockIO;
import de.dlr.shepard.v2.importer.io.ImportLockIO.AbandonRequestIO;
import de.dlr.shepard.v2.importer.io.ImportLockIO.AcquireRequestIO;
import de.dlr.shepard.v2.importer.io.ImportLockIO.LockStatusIO;
import de.dlr.shepard.v2.importer.services.ImportLockService;
import io.quarkus.security.Authenticated;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
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
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import static de.dlr.shepard.v2.common.ProblemResponse.problem;

/**
 * IMP-LOCK — {@code /v2/import/lock}: persistent import-in-progress lock.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /v2/import/lock} — read current lock status; 204 when no lock exists.</li>
 *   <li>{@code POST /v2/import/lock} — acquire a new lock (start import).</li>
 *   <li>{@code POST /v2/import/lock/{lockId}/heartbeat} — extend heartbeat while running.</li>
 *   <li>{@code POST /v2/import/lock/{lockId}/release} — normal completion (→ COMPLETED).</li>
 *   <li>{@code POST /v2/import/lock/{lockId}/abandon} — error termination (→ FAILED).</li>
 *   <li>{@code DELETE /v2/import/lock/{lockId}} — admin cancel (→ CANCELLED); requires
 *       {@code instance-admin} role.</li>
 * </ul>
 *
 * <p>Intended callers: client-side importer scripts (e.g. {@code mffd-import-v15.py}).
 * The lock persists across server restarts so a restarted backend can surface
 * "an import was in progress" rather than silently losing state.
 *
 * <p>The {@code lockId} returned on acquire is also the {@code runId} accepted by
 * {@link ImportDiagnosticsV2Rest} on its {@code /v2/import/diagnostics/{runId}} paths.
 * Use it throughout the import run to push and query structured diagnostic events.
 *
 * <p>Auth: all endpoints require authentication.  Only {@code DELETE} requires the
 * {@code instance-admin} role.
 */
@Path("/v2/import/lock")
@Authenticated
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Import")
public class ImportLockV2Rest {

  private static final String PT_BAD_REQUEST  = "/problems/import-lock.bad-request";
  private static final String PT_NOT_FOUND    = "/problems/import-lock.not-found";
  private static final String PT_CONFLICT     = "/problems/import-lock.conflict";
  private static final String PT_UNAUTHORIZED = "/problems/import-lock.unauthorized";

  @Inject
  ImportLockService lockService;

  // ─── GET /v2/import/lock ────────────────────────────────────────────────────

  @GET
  @Operation(
    operationId = "getCurrent",
    summary = "Get current import lock status (IMP-LOCK)",
    description =
      "Returns the most recent import lock regardless of status, so callers can tell " +
      "whether an import is currently running or what the last import's outcome was. " +
      "Returns 204 when no lock has ever been created."
  )
  @APIResponse(
    responseCode = "200",
    description = "Lock status",
    content = @Content(schema = @Schema(implementation = LockStatusIO.class))
  )
  @APIResponse(responseCode = "204", description = "No lock exists")
  @APIResponse(responseCode = "401", description = "Authentication required")
  public Response getCurrent(@Context SecurityContext sc) {
    if (caller(sc) == null) return unauthorized();

    ImportLock lock = lockService.findCurrent();
    if (lock == null) return Response.noContent().build();
    return Response.ok(LockStatusIO.from(lock)).build();
  }

  // ─── POST /v2/import/lock ───────────────────────────────────────────────────

  @POST
  @Operation(
    operationId = "acquire",
    summary = "Acquire an import lock (IMP-LOCK)",
    description =
      "Creates a new RUNNING import lock for the specified collection. " +
      "Returns 409 if a fresh lock already exists (heartbeat within the last 5 minutes). " +
      "If the existing lock is stale (> 5 min without heartbeat), it is automatically " +
      "transitioned to ABANDONED and a new lock is created."
  )
  @APIResponse(
    responseCode = "201",
    description = "Lock acquired",
    content = @Content(schema = @Schema(implementation = LockStatusIO.class))
  )
  @APIResponse(responseCode = "400", description = "Missing targetCollectionAppId")
  @APIResponse(responseCode = "401", description = "Authentication required")
  @APIResponse(responseCode = "409", description = "A fresh import lock already exists")
  public Response acquire(AcquireRequestIO body, @Context SecurityContext sc) {
    String caller = caller(sc);
    if (caller == null) return unauthorized();

    if (body == null || body.targetCollectionAppId() == null
        || body.targetCollectionAppId().isBlank()) {
      return problem(PT_BAD_REQUEST, "Bad request", Response.Status.BAD_REQUEST,
        "targetCollectionAppId is required");
    }

    ImportLock lock = lockService.acquire(body.targetCollectionAppId(), caller);
    if (lock == null) {
      return problem(PT_CONFLICT, "Lock already exists", Response.Status.CONFLICT,
        "A fresh import lock already exists — wait for it to complete or become stale");
    }
    return Response.status(Response.Status.CREATED)
      .entity(LockStatusIO.from(lock))
      .build();
  }

  // ─── POST /v2/import/lock/{lockId}/heartbeat ───────────────────────────────

  @POST
  @Path("/{lockId}/heartbeat")
  @Operation(
    operationId = "heartbeat",
    summary = "Extend import lock heartbeat (IMP-LOCK)",
    description =
      "Updates lastHeartbeatAt to now for the specified RUNNING lock. " +
      "Call approximately every 30 s while the import is running. " +
      "Returns 404 if the lock does not exist; returns 409 if the lock is not in RUNNING status."
  )
  @APIResponse(
    responseCode = "200",
    description = "Heartbeat recorded",
    content = @Content(schema = @Schema(implementation = LockStatusIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required")
  @APIResponse(responseCode = "404", description = "Lock not found")
  @APIResponse(responseCode = "409", description = "Lock is not in RUNNING status")
  public Response heartbeat(
    @Parameter(
      description =
        "Lock identifier returned by POST /v2/import/lock (the 'lockId' field in LockStatusIO). " +
        "Cross-resource alias: ImportDiagnosticsV2Rest uses this same value under the " +
        "name 'runId' on its /v2/import/diagnostics/{runId} paths."
    )
    @PathParam("lockId") String lockId,
    @Context SecurityContext sc
  ) {
    if (caller(sc) == null) return unauthorized();

    ImportLock lock = lockService.heartbeat(lockId);
    if (lock == null) {
      // Could be not-found or wrong-status — return 404 as the more useful signal.
      return problem(PT_NOT_FOUND, "Lock not found", Response.Status.NOT_FOUND,
        "Lock not found or not in RUNNING status: " + lockId);
    }
    return Response.ok(LockStatusIO.from(lock)).build();
  }

  // ─── POST /v2/import/lock/{lockId}/release ────────────────────────────────

  @POST
  @Path("/{lockId}/release")
  @Operation(
    operationId = "release",
    summary = "Release import lock on completion (IMP-LOCK)",
    description =
      "Transitions a RUNNING lock to COMPLETED status (normal import completion). " +
      "Returns 404 if the lock does not exist; returns 409 if the lock is not in RUNNING status."
  )
  @APIResponse(
    responseCode = "200",
    description = "Lock released (COMPLETED)",
    content = @Content(schema = @Schema(implementation = LockStatusIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required")
  @APIResponse(responseCode = "404", description = "Lock not found or not in RUNNING status")
  public Response release(
    @Parameter(
      description =
        "Lock identifier returned by POST /v2/import/lock (the 'lockId' field in LockStatusIO). " +
        "Cross-resource alias: ImportDiagnosticsV2Rest uses this same value under the " +
        "name 'runId' on its /v2/import/diagnostics/{runId} paths."
    )
    @PathParam("lockId") String lockId,
    @Context SecurityContext sc
  ) {
    if (caller(sc) == null) return unauthorized();

    ImportLock lock = lockService.release(lockId);
    if (lock == null) {
      return problem(PT_NOT_FOUND, "Lock not found", Response.Status.NOT_FOUND,
        "Lock not found or not in RUNNING status: " + lockId);
    }
    return Response.ok(LockStatusIO.from(lock)).build();
  }

  // ─── POST /v2/import/lock/{lockId}/abandon ────────────────────────────────

  @POST
  @Path("/{lockId}/abandon")
  @Operation(
    operationId = "abandon",
    summary = "Abandon import lock on error (IMP-LOCK)",
    description =
      "Transitions a RUNNING lock to FAILED status with an error description. " +
      "Use this when the import terminates abnormally. " +
      "Returns 404 if the lock does not exist; returns 409 if the lock is not in RUNNING status."
  )
  @APIResponse(
    responseCode = "200",
    description = "Lock marked FAILED",
    content = @Content(schema = @Schema(implementation = LockStatusIO.class))
  )
  @APIResponse(responseCode = "400", description = "Missing errorMessage")
  @APIResponse(responseCode = "401", description = "Authentication required")
  @APIResponse(responseCode = "404", description = "Lock not found or not in RUNNING status")
  public Response abandon(
    @Parameter(
      description =
        "Lock identifier returned by POST /v2/import/lock (the 'lockId' field in LockStatusIO). " +
        "Cross-resource alias: ImportDiagnosticsV2Rest uses this same value under the " +
        "name 'runId' on its /v2/import/diagnostics/{runId} paths."
    )
    @PathParam("lockId") String lockId,
    AbandonRequestIO body,
    @Context SecurityContext sc
  ) {
    if (caller(sc) == null) return unauthorized();

    if (body == null || body.errorMessage() == null || body.errorMessage().isBlank()) {
      return problem(PT_BAD_REQUEST, "Bad request", Response.Status.BAD_REQUEST,
        "errorMessage is required");
    }

    ImportLock lock = lockService.abandon(lockId, body.errorMessage());
    if (lock == null) {
      return problem(PT_NOT_FOUND, "Lock not found", Response.Status.NOT_FOUND,
        "Lock not found or not in RUNNING status: " + lockId);
    }
    return Response.ok(LockStatusIO.from(lock)).build();
  }

  // ─── DELETE /v2/import/lock/{lockId} ───────────────────────────────────────────

  @DELETE
  @Path("/{lockId}")
  @RolesAllowed("instance-admin")
  @Operation(
    operationId = "cancel",
    summary = "Admin cancel an import lock (IMP-LOCK)",
    description =
      "Transitions a RUNNING lock to CANCELLED status. " +
      "Terminal locks (COMPLETED, FAILED, CANCELLED, ABANDONED) are returned as-is — " +
      "the operation is idempotent with respect to admin intent. " +
      "Requires instance-admin role."
  )
  @APIResponse(
    responseCode = "200",
    description = "Lock cancelled (or already terminal)",
    content = @Content(schema = @Schema(implementation = LockStatusIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required")
  @APIResponse(responseCode = "403", description = "Requires instance-admin role")
  @APIResponse(responseCode = "404", description = "Lock not found")
  public Response cancel(
    @Parameter(
      description =
        "Lock identifier returned by POST /v2/import/lock (the 'lockId' field in LockStatusIO). " +
        "Cross-resource alias: ImportDiagnosticsV2Rest uses this same value under the " +
        "name 'runId' on its /v2/import/diagnostics/{runId} paths."
    )
    @PathParam("lockId") String lockId,
    @Context SecurityContext sc
  ) {
    if (caller(sc) == null) return unauthorized();

    ImportLock lock = lockService.cancel(lockId);
    if (lock == null) {
      return problem(PT_NOT_FOUND, "Lock not found", Response.Status.NOT_FOUND,
        "Lock not found: " + lockId);
    }
    return Response.ok(LockStatusIO.from(lock)).build();
  }

  // ─── Helpers ──────────────────────────────────────────────────────────────────────────────

  private static String caller(SecurityContext sc) {
    return sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
  }

  private static Response unauthorized() {
    return problem(PT_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED,
      "authentication required");
  }
}
