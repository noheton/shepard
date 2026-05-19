package de.dlr.shepard.v2.timeseries.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.references.timeseriesreference.daos.TimeseriesReferenceDAO;
import de.dlr.shepard.context.references.timeseriesreference.io.TimeseriesReferenceIO;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.context.references.timeseriesreference.services.TimeseriesReferenceService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PATCH;
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

/**
 * TM1a — {@code PATCH /v2/timeseries-references/{appId}} merge-patch endpoint
 * for updating the time-reference fields ({@code timeReference},
 * {@code wallClockOffset}, {@code wallClockOffsetSource}) of a
 * {@link TimeseriesReference}.
 *
 * <p>Auth: Write permission on the parent {@code DataObject}.
 * 400 when {@code timeReference} is {@code "EXPERIMENT_RELATIVE"} and
 * no {@code wallClockOffset} is present (neither in the patch nor
 * already stored on the entity). 401 when unauthenticated. 403 when
 * caller lacks Write permission. 404 when the reference is unknown.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(Constants.APPLICATION_MERGE_PATCH_JSON)
@Path("/v2/timeseries-references")
@RequestScoped
@Tag(name = "Timeseries references (v2)")
public class TimeseriesReferenceV2Rest {

  @Inject
  TimeseriesReferenceDAO timeseriesReferenceDAO;

  @Inject
  TimeseriesReferenceService timeseriesReferenceService;

  @Inject
  PermissionsService permissionsService;

  // ─── helpers ─────────────────────────────────────────────────────────────

  private Response checkAccess(String appId, AccessType type, SecurityContext sc) {
    String caller = sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();
    TimeseriesReference ref = timeseriesReferenceDAO.findByAppId(appId);
    if (ref == null) return Response.status(Response.Status.NOT_FOUND).build();
    var dataObject = ref.getDataObject();
    if (dataObject == null) return Response.status(Response.Status.NOT_FOUND).build();
    if (!permissionsService.isAccessTypeAllowedForUser(dataObject.getId(), type, caller, 0L)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    return null;
  }

  // ─── endpoints ───────────────────────────────────────────────────────────

  /**
   * PATCH /v2/timeseries-references/{appId}
   *
   * <p>RFC 7396 merge-patch. Only the three TM1 fields are mutable via this
   * endpoint. Null / absent fields in the patch are left untouched on the entity.
   *
   * <p>400 when {@code timeReference == "EXPERIMENT_RELATIVE"} and
   * {@code wallClockOffset} is neither provided in the patch nor already stored.
   */
  @PATCH
  @Path("/{appId}")
  @Operation(
    summary = "Update time-reference fields on a TimeseriesReference (TM1a merge-patch).",
    description =
      "Applies a merge-patch to the three TM1 time-alignment fields of the " +
      "`:TimeseriesReference` identified by `appId` (UUID v7). Only the fields " +
      "present in the request body are updated; absent fields are left unchanged.\n\n" +
      "Patchable fields:\n" +
      "  - `timeReference` (string) — the time-base mode; valid values are " +
      "`\"WALL_CLOCK\"` (timestamps are wall-clock UTC; default), `\"EXPERIMENT_RELATIVE\"` " +
      "(timestamps are offsets in nanoseconds from a known wall-clock anchor specified " +
      "by `wallClockOffset`).\n" +
      "  - `wallClockOffset` (long, nanoseconds) — the wall-clock anchor for " +
      "`EXPERIMENT_RELATIVE` mode; the epoch from which all channel timestamps are " +
      "measured. Required when `timeReference` is or becomes `EXPERIMENT_RELATIVE`.\n" +
      "  - `wallClockOffsetSource` (string) — human-readable description of how the " +
      "offset was determined (e.g. `\"GPS sync\"`, `\"NTP\"`, `\"manual estimate\"`).\n\n" +
      "Example: align to a known experiment start time — `{\"timeReference\": " +
      "\"EXPERIMENT_RELATIVE\", \"wallClockOffset\": 1700000000000000000, " +
      "\"wallClockOffsetSource\": \"GPS sync\"}`.\n\n" +
      "Validation: if the effective `timeReference` after the patch is " +
      "`EXPERIMENT_RELATIVE` and no `wallClockOffset` is present (neither in the patch " +
      "nor already stored on the entity), the call returns 400.\n\n" +
      "Content-Type: `application/merge-patch+json` (required).\n\n" +
      "Auth: Write permission on the parent DataObject (inherited from its Collection)."
  )
  @APIResponse(
    responseCode = "200",
    description = "Full TimeseriesReferenceIO reflecting the state after the patch was applied.",
    content = @Content(schema = @Schema(implementation = TimeseriesReferenceIO.class))
  )
  @APIResponse(responseCode = "400", description = "timeReference is `EXPERIMENT_RELATIVE` but no wallClockOffset is present in the patch or stored on the entity.")
  @APIResponse(responseCode = "401", description = "Authentication required (no JWT or X-API-KEY).")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No TimeseriesReference with that appId.")
  public Response patch(@PathParam("appId") String appId, TimeseriesReferenceIO body, @Context SecurityContext sc) {
    var gate = checkAccess(appId, AccessType.Write, sc);
    if (gate != null) return gate;

    TimeseriesReference ref = timeseriesReferenceDAO.findByAppId(appId);

    // Determine the effective timeReference after applying the patch.
    String effectiveTimeRef = body.getTimeReference() != null ? body.getTimeReference() : ref.getTimeReference();

    // Determine the effective wallClockOffset after applying the patch.
    Long effectiveOffset = body.getWallClockOffset() != null ? body.getWallClockOffset() : ref.getWallClockOffset();

    // Validate: EXPERIMENT_RELATIVE always needs a wallClockOffset.
    if ("EXPERIMENT_RELATIVE".equals(effectiveTimeRef) && effectiveOffset == null) {
      return Response
        .status(Response.Status.BAD_REQUEST)
        .entity("wallClockOffset is required when timeReference is EXPERIMENT_RELATIVE")
        .build();
    }

    TimeseriesReference updated = timeseriesReferenceService.updateTimeReference(ref, body);
    return Response.ok(new TimeseriesReferenceIO(updated)).build();
  }
}
