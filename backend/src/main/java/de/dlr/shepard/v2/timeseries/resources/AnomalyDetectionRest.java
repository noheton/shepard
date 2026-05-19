package de.dlr.shepard.v2.timeseries.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.references.timeseriesreference.daos.TimeseriesReferenceDAO;
import de.dlr.shepard.context.references.timeseriesreference.model.ReferencedTimeseriesNodeEntity;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.context.references.timeseriesreference.services.AnomalyDetectionService;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.v2.timeseries.io.AnomalyDetectRequestIO;
import de.dlr.shepard.v2.timeseries.io.AnomalyDetectResultIO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * AI1b — rolling-median MAD anomaly detection endpoint.
 *
 * <p>Auth pattern mirrors {@link TimeseriesAnnotationRest}: Read on the
 * parent DataObject to run detection; Write additionally required when
 * {@code createAnnotations=true}.
 *
 * <p>Series selection:
 * <ul>
 *   <li>If the reference holds exactly one series and all five filter
 *       fields are null, that series is used automatically.</li>
 *   <li>Otherwise all five filter fields must be supplied and must
 *       resolve to exactly one series; the request is rejected with
 *       400 otherwise.</li>
 * </ul>
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/timeseries-references/{refAppId}/detect-anomalies")
@RequestScoped
@Tag(name = "Timeseries annotations (v2)")
public class AnomalyDetectionRest {

  @Inject
  TimeseriesReferenceDAO timeseriesReferenceDAO;

  @Inject
  PermissionsService permissionsService;

  @Inject
  AnomalyDetectionService anomalyDetectionService;

  // ── helpers ───────────────────────────────────────────────────────────────

  private TimeseriesReference resolveRef(String refAppId) {
    return timeseriesReferenceDAO.findByAppId(refAppId);
  }

  /**
   * Auth gate: returns a non-null error Response when access is denied,
   * or {@code null} when access is granted.
   */
  private Response checkAccess(String refAppId, AccessType type, SecurityContext sc) {
    String caller = sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();
    TimeseriesReference ref = resolveRef(refAppId);
    if (ref == null) return Response.status(Response.Status.NOT_FOUND).build();
    var dataObject = ref.getDataObject();
    if (dataObject == null) return Response.status(Response.Status.NOT_FOUND).build();
    if (!permissionsService.isAccessTypeAllowedForUser(dataObject.getId(), type, caller, 0L)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    return null;
  }

  /**
   * Resolve a single Timeseries from the reference according to the
   * request's filter fields.
   *
   * @return the selected Timeseries, or {@code null} if selection fails;
   *         in that case a 400 error Response is written to {@code out[0]}
   */
  private Timeseries selectSeries(TimeseriesReference ref, AnomalyDetectRequestIO req, Response[] out) {
    List<ReferencedTimeseriesNodeEntity> all = ref.getReferencedTimeseriesList();

    boolean hasFilters = req.getMeasurement() != null ||
      req.getDevice() != null ||
      req.getLocation() != null ||
      req.getSymbolicName() != null ||
      req.getField() != null;

    if (!hasFilters) {
      // Auto-select if exactly one series
      if (all.size() == 1) {
        return all.get(0).toTimeseries();
      }
      out[0] = Response
        .status(Response.Status.BAD_REQUEST)
        .entity(
          "The TimeseriesReference contains " +
          all.size() +
          " series; provide measurement/device/location/symbolicName/field to select one."
        )
        .build();
      return null;
    }

    // Filter with partial matching: all non-null fields must match
    List<ReferencedTimeseriesNodeEntity> matched = all
      .stream()
      .filter(e ->
        (req.getMeasurement() == null || req.getMeasurement().equals(e.getMeasurement())) &&
        (req.getDevice() == null || req.getDevice().equals(e.getDevice())) &&
        (req.getLocation() == null || req.getLocation().equals(e.getLocation())) &&
        (req.getSymbolicName() == null || req.getSymbolicName().equals(e.getSymbolicName())) &&
        (req.getField() == null || req.getField().equals(e.getField()))
      )
      .toList();

    if (matched.size() == 1) {
      return matched.get(0).toTimeseries();
    }
    String msg = matched.isEmpty()
      ? "No series in the reference matches the supplied filter fields."
      : "Filter fields are ambiguous — " + matched.size() + " series matched. Provide more specific filters.";
    out[0] = Response.status(Response.Status.BAD_REQUEST).entity(msg).build();
    return null;
  }

  // ── endpoint ──────────────────────────────────────────────────────────────

  @POST
  @Operation(
    summary = "Run rolling-median MAD anomaly detection on a single timeseries in a TimeseriesReference.",
    description =
      "Fetches the timeseries data linked to the `:TimeseriesReference` identified by " +
      "`refAppId`, selects one channel series, applies the rolling-median Median Absolute " +
      "Deviation (MAD) algorithm, and returns detected anomaly intervals as an " +
      "`AnomalyDetectResultIO` response.\n\n" +
      "Series selection rules:\n" +
      "  - If the reference holds exactly one series and all five filter fields " +
      "(`measurement`, `device`, `location`, `symbolicName`, `field`) are null in the " +
      "request body, that series is selected automatically.\n" +
      "  - Otherwise all non-null filter fields must together resolve to exactly one " +
      "series; 400 is returned if zero or multiple series match.\n\n" +
      "Request body fields (all optional with defaults): `window` (int ≥ 3, default 51 — " +
      "rolling window size), `k` (float > 0, default 6.0 — MAD sensitivity multiplier; " +
      "higher = fewer detections), `createAnnotations` (boolean, default false — when " +
      "true, each detected interval is persisted as a `TimeseriesAnnotation` with " +
      "`aiGenerated=true`), `measurement`, `device`, `location`, `symbolicName`, `field` " +
      "(strings — series filter keys; omit all to auto-select when there is exactly one " +
      "series).\n\n" +
      "Example — auto-select, default params: `{}`. Example — explicit series and " +
      "tighter detection: `{\"measurement\": \"pressure\", \"field\": \"bar\", " +
      "\"window\": 7, \"k\": 2.5, \"createAnnotations\": true}`.\n\n" +
      "Auth: Read permission is sufficient when `createAnnotations=false`; Write " +
      "permission on the parent DataObject is required when `createAnnotations=true`."
  )
  @APIResponse(
    responseCode = "200",
    description = "Detection completed; `AnomalyDetectResultIO` contains the list of detected intervals and, when `createAnnotations=true`, the appIds of the created annotations.",
    content = @Content(schema = @Schema(implementation = AnomalyDetectResultIO.class))
  )
  @APIResponse(responseCode = "400", description = "Invalid parameters (`window < 3`, `k <= 0`), or series selection failed (zero or multiple matches, or the reference's container is missing/deleted).")
  @APIResponse(responseCode = "401", description = "Authentication required (no JWT or X-API-KEY).")
  @APIResponse(responseCode = "403", description = "Caller lacks the required permission on the parent DataObject (Read for detection, Write when createAnnotations=true).")
  @APIResponse(responseCode = "404", description = "No TimeseriesReference with that appId.")
  public Response detect(
    @PathParam("refAppId") String refAppId,
    AnomalyDetectRequestIO body,
    @Context SecurityContext sc
  ) {
    // Default body
    if (body == null) body = new AnomalyDetectRequestIO();

    // Validate numeric parameters early (before auth lookup)
    int rawWindow = body.effectiveWindow();
    if (rawWindow < 3) {
      return Response
        .status(Response.Status.BAD_REQUEST)
        .entity("window must be ≥ 3, got " + rawWindow)
        .build();
    }
    double k = body.effectiveK();
    if (k <= 0) {
      return Response
        .status(Response.Status.BAD_REQUEST)
        .entity("k must be > 0, got " + k)
        .build();
    }

    // Auth: Read always; Write additionally if createAnnotations
    AccessType required = body.isCreateAnnotations() ? AccessType.Write : AccessType.Read;
    var gate = checkAccess(refAppId, required, sc);
    if (gate != null) return gate;

    // Resolve reference
    TimeseriesReference ref = resolveRef(refAppId);
    // checkAccess already validated existence; ref is non-null here

    // Check the reference has a non-deleted container
    if (ref.getTimeseriesContainer() == null || ref.getTimeseriesContainer().isDeleted()) {
      return Response
        .status(Response.Status.BAD_REQUEST)
        .entity("The TimeseriesReference's container is missing or deleted.")
        .build();
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
      return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
    }

    return Response.ok(result).build();
  }
}
