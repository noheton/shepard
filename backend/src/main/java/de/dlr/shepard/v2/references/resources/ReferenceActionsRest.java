package de.dlr.shepard.v2.references.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.timeseriesreference.daos.TimeseriesReferenceDAO;
import de.dlr.shepard.context.references.timeseriesreference.model.ReferencedTimeseriesNodeEntity;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.context.references.timeseriesreference.services.AnomalyDetectionService;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.v2.references.services.ReferencesV2Service;
import de.dlr.shepard.v2.timeseries.io.AnomalyDetectRequestIO;
import de.dlr.shepard.v2.timeseries.io.AnomalyDetectResultIO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
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
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import static de.dlr.shepard.v2.common.ProblemResponse.problem;

/**
 * APISIMP-ANOMALY-ACTION-PATH — generic action dispatcher for references.
 *
 * <p>Routes {@code POST /v2/references/{appId}/actions?action=<name>} to
 * the appropriate kind-specific handler. Currently only {@code detect-anomalies}
 * is supported (timeseries references only). Unknown actions → 422.
 * A known action invoked on an incompatible reference kind → 422.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/references/{appId}/actions")
@RequestScoped
@Tag(name = "Timeseries")
public class ReferenceActionsRest {

  static final String ACTION_DETECT_ANOMALIES = "detect-anomalies";

  @Inject
  TimeseriesReferenceDAO timeseriesReferenceDAO;

  @Inject
  PermissionsService permissionsService;

  @Inject
  AnomalyDetectionService anomalyDetectionService;

  @Inject
  ReferencesV2Service referencesV2Service;

  // ── helpers ───────────────────────────────────────────────────────────────

  private static final String PT_UNAUTHORIZED = "/problems/reference-actions.unauthorized";
  private static final String PT_NOT_FOUND    = "/problems/reference-actions.not-found";
  private static final String PT_FORBIDDEN    = "/problems/reference-actions.forbidden";

  private Response checkAccess(String refAppId, AccessType type, SecurityContext sc,
                               TimeseriesReference ref) {
    String caller = sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) return problem(PT_UNAUTHORIZED, "Unauthorized", Response.Status.UNAUTHORIZED,
        "Authentication required");
    DataObject dataObject = ref.getDataObject();
    if (dataObject == null) return problem(PT_NOT_FOUND, "Not Found", Response.Status.NOT_FOUND,
        "Parent DataObject of reference not found");
    if (!permissionsService.isAccessAllowedForDataObjectAppId(dataObject.getAppId(), type, caller)) {
      return problem(PT_FORBIDDEN, "Forbidden", Response.Status.FORBIDDEN,
          "Insufficient permission on parent DataObject");
    }
    return null;
  }

  private Timeseries selectSeries(TimeseriesReference ref, AnomalyDetectRequestIO req,
                                  Response[] out) {
    List<ReferencedTimeseriesNodeEntity> all = ref.getReferencedTimeseriesList();

    boolean hasChannelAppId = req.getChannelAppId() != null;
    boolean hasTuple = req.hasTupleSelector();

    if (hasChannelAppId && hasTuple) {
      out[0] = problem("reference-actions.bad-request", "Unprocessable Entity", 422,
          "Provide either channelAppId or the 5-tuple fields (measurement/device/location/"
              + "symbolicName/field), not both.");
      return null;
    }

    if (hasChannelAppId) {
      var matched = all.stream()
          .filter(e -> req.getChannelAppId().equals(e.getAppId()))
          .findFirst();
      if (matched.isEmpty()) {
        out[0] = problem("reference-actions.bad-request", "Bad Request", Response.Status.BAD_REQUEST,
            "No channel with channelAppId '" + req.getChannelAppId()
                + "' is linked to this TimeseriesReference.");
        return null;
      }
      return matched.get().toTimeseries();
    }

    if (!hasTuple) {
      if (all.size() == 1) {
        return all.get(0).toTimeseries();
      }
      out[0] = problem("reference-actions.bad-request", "Bad Request", Response.Status.BAD_REQUEST,
          "The TimeseriesReference contains " + all.size()
              + " series; provide channelAppId or measurement/device/location/symbolicName/field"
              + " to select one.");
      return null;
    }

    List<ReferencedTimeseriesNodeEntity> matched = all.stream()
        .filter(e ->
            (req.getMeasurement() == null || req.getMeasurement().equals(e.getMeasurement()))
                && (req.getDevice() == null || req.getDevice().equals(e.getDevice()))
                && (req.getLocation() == null || req.getLocation().equals(e.getLocation()))
                && (req.getSymbolicName() == null || req.getSymbolicName().equals(e.getSymbolicName()))
                && (req.getField() == null || req.getField().equals(e.getField()))
        )
        .toList();

    if (matched.size() == 1) {
      return matched.get(0).toTimeseries();
    }
    String msg = matched.isEmpty()
        ? "No series in the reference matches the supplied filter fields."
        : "Filter fields are ambiguous — " + matched.size()
            + " series matched. Provide more specific filters.";
    out[0] = problem("reference-actions.bad-request", "Bad Request", Response.Status.BAD_REQUEST, msg);
    return null;
  }

  // ── endpoint ──────────────────────────────────────────────────────────────

  @POST
  @Operation(
      operationId = "referenceAction",
      summary = "Dispatch a named action on a reference (kind-discriminated).",
      description =
          "Dispatches the action named by the `action` query parameter against the reference "
              + "identified by `{appId}`. The action must be supported by the reference's kind.\n\n"
              + "**Currently supported actions:**\n\n"
              + "- `detect-anomalies` (**TimeseriesReference only**) — runs rolling-median MAD "
              + "anomaly detection on a single channel in the reference. The request body is an "
              + "`AnomalyDetectRequestIO` (all fields optional). Returns `AnomalyDetectResultIO`.\n\n"
              + "Returns **422** when the action is unknown or is not supported by the reference's kind.\n\n"
              + "Auth: Read permission is sufficient when `createAnnotations=false`; Write permission "
              + "on the parent DataObject is required when `createAnnotations=true` "
              + "(detect-anomalies only)."
  )
  @APIResponse(
      responseCode = "200",
      description = "Action completed. Response shape depends on `action`.",
      content = @Content(schema = @Schema(implementation = AnomalyDetectResultIO.class))
  )
  @APIResponse(responseCode = "400", description = "Invalid action parameters or series selection failed.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks the required permission.")
  @APIResponse(responseCode = "404", description = "No reference with that appId.")
  @APIResponse(responseCode = "422", description =
      "Action is unknown, or the action is not supported for this reference's kind.")
  public Response perform(
      @PathParam("appId") String appId,
      @Parameter(description = "Action name. Currently: `detect-anomalies`.", required = true)
      @QueryParam("action") String action,
      AnomalyDetectRequestIO body,
      @Context SecurityContext sc
  ) {
    if (action == null || action.isBlank()) {
      return problem("reference-actions.unknown-action", "Unprocessable Entity", 422,
          "Query parameter 'action' is required. Supported actions: " + ACTION_DETECT_ANOMALIES);
    }
    if (!ACTION_DETECT_ANOMALIES.equals(action)) {
      return problem("reference-actions.unknown-action", "Unprocessable Entity", 422,
          "Unknown action '" + action + "'. Supported actions: " + ACTION_DETECT_ANOMALIES);
    }

    // Caller must be authenticated before any DB lookup
    String caller = sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) {
      return problem(PT_UNAUTHORIZED, "Unauthorized", Response.Status.UNAUTHORIZED,
          "Authentication required");
    }

    // Resolve as TimeseriesReference
    TimeseriesReference ref = timeseriesReferenceDAO.findByAppId(appId);
    if (ref == null) {
      // Distinguish: does a reference with this appId exist under a different kind?
      boolean existsOtherKind = referencesV2Service.resolveByAppId(appId).isPresent();
      if (existsOtherKind) {
        return problem("reference-actions.unsupported-kind", "Unprocessable Entity", 422,
            "Action 'detect-anomalies' is only supported for timeseries references.");
      }
      return problem(PT_NOT_FOUND, "Not Found", Response.Status.NOT_FOUND,
          "No reference with appId: " + appId);
    }

    // Default body
    if (body == null) body = new AnomalyDetectRequestIO();

    // Validate numeric parameters early (before auth lookup)
    int rawWindow = body.effectiveWindow();
    if (rawWindow < 3) {
      return problem("reference-actions.bad-request", "Bad Request", Response.Status.BAD_REQUEST,
          "window must be ≥ 3, got " + rawWindow);
    }
    double k = body.effectiveK();
    if (k <= 0) {
      return problem("reference-actions.bad-request", "Bad Request", Response.Status.BAD_REQUEST,
          "k must be > 0, got " + k);
    }

    // Auth: Read always; Write additionally if createAnnotations
    AccessType required = body.isCreateAnnotations() ? AccessType.Write : AccessType.Read;
    Response gate = checkAccess(appId, required, sc, ref);
    if (gate != null) return gate;

    // Check the reference has a non-deleted container
    if (ref.getTimeseriesContainer() == null || ref.getTimeseriesContainer().isDeleted()) {
      return problem("reference-actions.bad-request", "Bad Request", Response.Status.BAD_REQUEST,
          "The TimeseriesReference's container is missing or deleted.");
    }

    // Select series
    Response[] errorHolder = new Response[1];
    Timeseries series = selectSeries(ref, body, errorHolder);
    if (series == null) return errorHolder[0];

    // Run detection
    AnomalyDetectResultIO result;
    try {
      result = anomalyDetectionService.detect(ref, series, body);
    } catch (IllegalArgumentException e) {
      return problem("reference-actions.bad-request", "Bad Request", Response.Status.BAD_REQUEST,
          e.getMessage());
    }

    return Response.ok(result).build();
  }
}
