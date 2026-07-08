package de.dlr.shepard.v2.timeseries.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import de.dlr.shepard.v2.timeseries.io.CrossDoBulkDataRequestIO;
import de.dlr.shepard.v2.timeseries.io.CrossDoSeriesIO;
import de.dlr.shepard.v2.timeseries.services.CrossDoChannelResolver;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import static de.dlr.shepard.v2.common.ProblemResponse.problem;

/**
 * TS-CROSS-DO-VIEW-1 — cross-DataObject timeseries bulk-data endpoint.
 *
 * <p>Route: {@code POST /v2/data-objects/cross-timeseries-bulk}
 *
 * <p>Takes a list of DataObject appIds + one channel-predicate IRI, and
 * returns LTTB-downsampled series — one entry per DataObject — for the
 * channel that carries that predicate on each DO. DataObjects with no
 * matching channel return an empty {@code points} list; DataObjects the
 * caller can't read are silently dropped (never 403 the whole request).
 *
 * <p>Sibling of {@code POST /v2/timeseries-containers/{containerId}/channels/data/bulk}
 * (TS-OPT2), not an extension — the two endpoints address different
 * grain: TS-OPT2 is many channels of one container, this is many DOs each
 * resolved to one channel-predicate match.
 *
 * <p>Documented in {@code docs/help/cross-track-view.md} +
 * {@code docs/reference/collections.md}; tracked in {@code aidocs/16}
 * under {@code TS-CROSS-DO-VIEW-1}.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/data-objects/cross-timeseries-bulk")
@RequestScoped
@Tag(name = "Timeseries")
public class CrossDoBulkDataRest {

  private static final String PT_UNAUTHORIZED = "/problems/timeseries-cross-do.unauthorized";

  /** Default LTTB target rows per series (per request body), per the GAP-2 brief. */
  static final int DEFAULT_DOWNSAMPLE_TO = 500;

  /** Hard cap on LTTB target so a misbehaving caller can't exhaust the response budget. */
  static final int HARD_MAX_DOWNSAMPLE = 5000;

  @Inject
  DataObjectDAO dataObjectDAO;

  @Inject
  CrossDoChannelResolver crossDoChannelResolver;

  @Inject
  TimeseriesService timeseriesService;

  @Inject
  PermissionsService permissionsService;

  @POST
  @Operation(
    operationId = "getCrossDoBulkData",
    summary = "Fetch one channel-predicate series across many DataObjects (TS-CROSS-DO-VIEW-1).",
    description =
      "Resolves each DataObject (by appId) to the channel whose `SemanticAnnotation.propertyIRI` " +
      "equals the request body's `channelPredicate`, then returns LTTB-downsampled points in the " +
      "supplied time window — one entry per resolved DataObject. " +
      "Up to 100 DataObjects per request; default downsample target is 500 rows per series " +
      "(max 5000). " +
      "DataObjects with no matching channel return an empty `points` list. " +
      "DataObjects the caller can't read are silently dropped (no 403 for the whole request). " +
      "Where a single DataObject has multiple channels matching the predicate, the first by " +
      "`symbolicName` ascending is picked (deterministic). " +
      "Timestamps are absolute UTC nanoseconds; clients render within-DO relative time on the " +
      "frontend by subtracting each series' first timestamp.",
    extensions = @Extension(name = "x-agent-hint", value = "Pass dataObjectAppIds + channelPredicate (urn:shepard:...). Returns one downsampled series per DO; empty points means 'no channel matched'.")
  )
  @APIResponse(
    responseCode = "200",
    description = "Resolved series across the requested DataObjects.",
    content = @Content(schema = @Schema(implementation = PagedResponseIO.class))
  )
  @APIResponse(responseCode = "400", description = "Validation error on request body.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response getCrossDoBulkData(
    @NotNull @Valid CrossDoBulkDataRequestIO body,
    @Context SecurityContext sc
  ) {
    final String caller = sc != null && sc.getUserPrincipal() != null
      ? sc.getUserPrincipal().getName()
      : null;
    if (caller == null) return problem(PT_UNAUTHORIZED, "Unauthorized", Response.Status.UNAUTHORIZED, "Authentication required");

    final int downsampleTo = clampDownsample(body.downsampleTo());
    final long start = body.start();
    final long end = body.end();
    final String predicate = body.channelPredicate();

    // Batch phase 1: collect valid appIds, preserving input order.
    List<String> requestedIds = new ArrayList<>();
    for (String id : body.dataObjectAppIds()) {
      if (id != null && !id.isBlank()) requestedIds.add(id);
    }

    // Batch phase 2: single Neo4j round-trip for permission gate (replaces N serial calls).
    Set<String> allowedIds =
      permissionsService.filterAllowedDataObjectAppIds(requestedIds, AccessType.Read, caller);

    // Batch phase 3: single Neo4j round-trip for DO names (only for allowed DOs).
    // Unknown appIds (not in the collection graph) simply won't appear in this map.
    Map<String, String> namesByAppId = dataObjectDAO.findNamesByAppIds(allowedIds);

    List<CrossDoSeriesIO> out = new ArrayList<>();
    for (String doAppId : requestedIds) {
      // Silently drop forbidden DOs; unknown DOs (not attached to any collection) are also
      // dropped since they won't appear in the allowedIds set.
      if (!allowedIds.contains(doAppId)) continue;

      String doName = namesByAppId.get(doAppId);

      List<CrossDoChannelResolver.ResolvedChannel> matches =
        crossDoChannelResolver.resolveChannelsByPredicate(doAppId, predicate);

      if (matches.isEmpty()) {
        out.add(new CrossDoSeriesIO(doAppId, doName, predicate, null, List.of()));
        continue;
      }

      // Deterministic pick: first by symbolicName ASC (already enforced in the resolver).
      CrossDoChannelResolver.ResolvedChannel pick = matches.get(0);

      Timeseries tuple = new Timeseries(
        pick.measurement(), pick.device(), pick.location(),
        pick.symbolicName(), pick.field()
      );
      List<TimeseriesDataPoint> points;
      try {
        points = timeseriesService.getDataPointsLttbOptimised(
          pick.containerId(), tuple, start, end, downsampleTo
        );
      } catch (RuntimeException ex) {
        points = List.of();
      }

      out.add(new CrossDoSeriesIO(doAppId, doName, predicate, pick.symbolicName(), points));
    }

    return Response.ok(new PagedResponseIO<>(out, out.size(), 0, out.size())).build();
  }

  /**
   * Clamp the downsample target to the [1, HARD_MAX_DOWNSAMPLE] range,
   * with DEFAULT_DOWNSAMPLE_TO when null. Exposed package-private so tests
   * can pin the exact contract.
   */
  static int clampDownsample(Integer requested) {
    if (requested == null) return DEFAULT_DOWNSAMPLE_TO;
    if (requested < 1) return 1;
    if (requested > HARD_MAX_DOWNSAMPLE) return HARD_MAX_DOWNSAMPLE;
    return requested;
  }
}
