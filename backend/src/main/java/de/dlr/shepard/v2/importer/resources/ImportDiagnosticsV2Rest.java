package de.dlr.shepard.v2.importer.resources;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.v2.importer.entities.ImportDiagnosticEvent;
import de.dlr.shepard.v2.importer.io.ImportDiagnosticsIO;
import de.dlr.shepard.v2.importer.io.ImportDiagnosticsIO.BatchIngestIO;
import de.dlr.shepard.v2.importer.io.ImportDiagnosticsIO.EventIO;
import de.dlr.shepard.v2.importer.io.ImportDiagnosticsIO.IngestEventIO;
import de.dlr.shepard.v2.importer.io.ImportDiagnosticsIO.RunSummaryIO;
import de.dlr.shepard.v2.importer.services.ImportDiagnosticsLog;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.List;
import java.util.Set;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * IMP-DIAG — {@code /v2/import/diagnostics}: structured diagnostic log for import runs.
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code GET /v2/import/diagnostics/{runId}} — all events for a run,
 *       with optional {@code ?level=} and {@code ?phase=} filters.</li>
 *   <li>{@code GET /v2/import/runs} — list recent run IDs with first/last timestamp
 *       and most-severe level seen.</li>
 *   <li>{@code POST /v2/import/diagnostics/{runId}/events} — ingest a single event
 *       from an external process (e.g. the Python importer).</li>
 *   <li>{@code POST /v2/import/diagnostics/{runId}/events/batch} — ingest a batch
 *       of events in a single call.</li>
 * </ul>
 *
 * <h2>runId identity</h2>
 * <p>The {@code runId} is the {@code lockId} from {@link de.dlr.shepard.v2.importer.entities.ImportLock}.
 * The Java lock lifecycle (acquire/release/abandon) automatically emits events via
 * {@link ImportDiagnosticsLog}; the POST ingress lets the external Python importer
 * push DO_CREATE / REF_ATTACH / FILE_UPLOAD phase events that are out of reach of
 * the Java service.
 *
 * <h2>Auth</h2>
 * <p>All endpoints require authentication.  There is no Write-on-collection
 * check because diagnostic data is considered operational metadata, readable by
 * any authenticated user who knows the {@code runId}.
 *
 * <h2>Memory model</h2>
 * <p>Events are held in-memory (last {@value ImportDiagnosticsLog#MAX_EVENTS_PER_RUN}
 * per run) and are not persisted across restarts.  Runs are evicted after 24 hours.
 */
@Path("/v2/import")
@Authenticated
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Import")
public class ImportDiagnosticsV2Rest {

  private static final String PT_BAD_REQUEST  = "/problems/import-diagnostics.bad-request";
  private static final String PT_UNAUTHORIZED = "/problems/import-diagnostics.unauthorized";

  /** Allowed level values for the {@code ?level=} filter. */
  private static final Set<String> VALID_LEVELS = Set.of(
    ImportDiagnosticEvent.LEVEL_INFO,
    ImportDiagnosticEvent.LEVEL_WARN,
    ImportDiagnosticEvent.LEVEL_ERROR
  );

  /** Allowed phase values for the {@code ?phase=} filter. */
  private static final Set<String> VALID_PHASES = Set.of(
    ImportDiagnosticEvent.PHASE_WARMUP,
    ImportDiagnosticEvent.PHASE_DO_CREATE,
    ImportDiagnosticEvent.PHASE_REF_ATTACH,
    ImportDiagnosticEvent.PHASE_FILE_UPLOAD,
    ImportDiagnosticEvent.PHASE_COMPLETE
  );

  @Inject
  ImportDiagnosticsLog diagnosticsLog;

  // ─── GET /v2/import/diagnostics/{runId} ──────────────────────────────────

  @GET
  @Path("/diagnostics/{runId}")
  @Operation(
    summary = "Get diagnostic events for an import run (IMP-DIAG)",
    description =
      "Returns all in-memory diagnostic events for the given runId (= ImportLock.lockId), " +
      "ordered oldest-first. " +
      "Optional query parameters: " +
      "`level` filters to a single severity (INFO / WARN / ERROR); " +
      "`phase` filters to a single import phase (WARMUP / DO_CREATE / REF_ATTACH / " +
      "FILE_UPLOAD / COMPLETE). " +
      "Returns an empty array when the runId is unknown or has no events. " +
      "Events are held in-memory — they are not persisted across server restarts " +
      "and are evicted after 24 hours."
  )
  @APIResponse(responseCode = "200", description = "Event list (may be empty)")
  @APIResponse(responseCode = "400", description = "Invalid level or phase filter value")
  @APIResponse(responseCode = "401", description = "Authentication required")
  public Response getEvents(
    @PathParam("runId") String runId,
    @Parameter(description = "Filter to a single severity level. One of: INFO, WARN, ERROR.",
               schema = @Schema(enumeration = {"INFO", "WARN", "ERROR"}))
    @QueryParam("level") String level,
    @Parameter(description = "Filter to a single import phase. One of: WARMUP, DO_CREATE, REF_ATTACH, FILE_UPLOAD, COMPLETE.",
               schema = @Schema(enumeration = {"WARMUP", "DO_CREATE", "REF_ATTACH", "FILE_UPLOAD", "COMPLETE"}))
    @QueryParam("phase") String phase,
    @Context SecurityContext sc
  ) {
    if (caller(sc) == null) return unauthorized();

    if (level != null && !VALID_LEVELS.contains(level)) {
      return badRequest("Invalid level filter '" + level + "'. Valid values: " + VALID_LEVELS);
    }
    if (phase != null && !VALID_PHASES.contains(phase)) {
      return badRequest("Invalid phase filter '" + phase + "'. Valid values: " + VALID_PHASES);
    }

    List<EventIO> events = diagnosticsLog.query(runId, level, phase)
        .stream()
        .map(EventIO::from)
        .toList();

    return Response.ok(events).build();
  }

  // ─── GET /v2/import/runs ──────────────────────────────────────────────────

  @GET
  @Path("/runs")
  @Operation(
    summary = "List recent import runs with diagnostic summary (IMP-DIAG)",
    description =
      "Returns a list of run IDs known to the in-memory diagnostic log, sorted by " +
      "start time descending (most recent first). " +
      "Each entry includes: runId, startedAt, lastEventAt, and lastLevel " +
      "(most-severe level seen: INFO / WARN / ERROR). " +
      "The list is populated by both Java-side lock lifecycle events and events " +
      "ingested from external processes via POST /v2/import/diagnostics/{runId}/events. " +
      "Runs are evicted after 24 hours; the list does not include runs from before " +
      "the last server restart."
  )
  @APIResponse(
    responseCode = "200",
    description = "Run summary list",
    content = @Content(schema = @Schema(implementation = RunSummaryIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required")
  public Response listRuns(@Context SecurityContext sc) {
    if (caller(sc) == null) return unauthorized();

    List<RunSummaryIO> runs = diagnosticsLog.listRuns()
        .stream()
        .map(RunSummaryIO::from)
        .toList();

    return Response.ok(runs).build();
  }

  // ─── POST /v2/import/diagnostics/{runId}/events ───────────────────────────

  @POST
  @Path("/diagnostics/{runId}/events")
  @Operation(
    summary = "Push a single diagnostic event from an external importer (IMP-DIAG)",
    description =
      "Ingests a single structured diagnostic event into the in-memory log for the " +
      "specified run. " +
      "Intended for the external Python importer, which calls this endpoint to record " +
      "DO_CREATE / REF_ATTACH / FILE_UPLOAD phase events that the Java service cannot " +
      "capture directly. " +
      "The runId must match an ImportLock.lockId obtained via POST /v2/import/lock. " +
      "Returns 204 on success. " +
      "Returns 400 when required fields are missing or level/phase values are invalid."
  )
  @APIResponse(responseCode = "204", description = "Event recorded")
  @APIResponse(responseCode = "400", description = "Missing required fields or invalid level/phase")
  @APIResponse(responseCode = "401", description = "Authentication required")
  public Response ingestEvent(
    @PathParam("runId") String runId,
    IngestEventIO body,
    @Context SecurityContext sc
  ) {
    if (caller(sc) == null) return unauthorized();

    Response validation = validateIngestBody(body);
    if (validation != null) return validation;

    diagnosticsLog.log(
      runId,
      body.level(),
      body.phase(),
      body.entityAppId(),
      body.message(),
      body.attributes()
    );
    return Response.noContent().build();
  }

  // ─── POST /v2/import/diagnostics/{runId}/events/batch ────────────────────

  @POST
  @Path("/diagnostics/{runId}/events/batch")
  @Operation(
    summary = "Push a batch of diagnostic events from an external importer (IMP-DIAG)",
    description =
      "Ingests multiple structured diagnostic events into the in-memory log for the " +
      "specified run in a single HTTP call. " +
      "All events are applied atomically in list order. " +
      "If any event in the batch has invalid level/phase values, the entire batch " +
      "is rejected with 400 and no events are recorded. " +
      "Returns 204 on success."
  )
  @APIResponse(responseCode = "204", description = "All events recorded")
  @APIResponse(responseCode = "400", description = "Empty batch or invalid event field")
  @APIResponse(responseCode = "401", description = "Authentication required")
  public Response ingestBatch(
    @PathParam("runId") String runId,
    BatchIngestIO body,
    @Context SecurityContext sc
  ) {
    if (caller(sc) == null) return unauthorized();

    if (body == null || body.events() == null || body.events().isEmpty()) {
      return badRequest("events list must not be empty");
    }

    // Validate all events before writing any (fail-fast).
    for (IngestEventIO event : body.events()) {
      Response validation = validateIngestBody(event);
      if (validation != null) return validation;
    }

    for (IngestEventIO event : body.events()) {
      diagnosticsLog.log(
        runId,
        event.level(),
        event.phase(),
        event.entityAppId(),
        event.message(),
        event.attributes()
      );
    }
    return Response.noContent().build();
  }

  // ─── Helpers ──────────────────────────────────────────────────────────────

  /**
   * Validate an ingest event body.
   *
   * @return a 400 response when validation fails; {@code null} when valid
   */
  private Response validateIngestBody(IngestEventIO body) {
    if (body == null) {
      return badRequest("Request body is required");
    }
    if (body.level() == null || body.level().isBlank()) {
      return badRequest("level is required");
    }
    if (!VALID_LEVELS.contains(body.level())) {
      return badRequest("Invalid level '" + body.level() + "'. Valid values: " + VALID_LEVELS);
    }
    if (body.phase() == null || body.phase().isBlank()) {
      return badRequest("phase is required");
    }
    if (!VALID_PHASES.contains(body.phase())) {
      return badRequest("Invalid phase '" + body.phase() + "'. Valid values: " + VALID_PHASES);
    }
    if (body.message() == null || body.message().isBlank()) {
      return badRequest("message is required");
    }
    return null;
  }

  private static Response badRequest(String message) {
    return problem(PT_BAD_REQUEST, "Bad request", Response.Status.BAD_REQUEST, message);
  }

  private static String caller(SecurityContext sc) {
    return sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
  }

  private static Response unauthorized() {
    return problem(PT_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED,
      "authentication required");
  }

  private static Response problem(String type, String title, Response.Status status, String detail) {
    ProblemJson body = new ProblemJson(type, title, status.getStatusCode(), detail, null);
    return Response.status(status).type("application/problem+json").entity(body).build();
  }
}
