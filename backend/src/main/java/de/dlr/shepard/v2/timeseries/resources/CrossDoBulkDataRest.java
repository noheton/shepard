package de.dlr.shepard.v2.timeseries.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import de.dlr.shepard.v2.timeseriescontainer.io.TimeseriesDataPointV2IO;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import de.dlr.shepard.v2.timeseries.io.CrossDoBulkDataRequestIO;
import de.dlr.shepard.v2.timeseries.io.CrossDoSeriesIO;
import de.dlr.shepard.v2.timeseries.services.CrossDoChannelResolver;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import static de.dlr.shepard.v2.common.ProblemResponse.problem;

/**
 * TS-CROSS-DO-VIEW-1 — cross-DataObject timeseries bulk-data endpoint.
 *
 * <p>Route: {@code POST /v2/data-objects/cross-bulk?kind=timeseries}
 * (old path {@code /v2/data-objects/cross-timeseries-bulk} is removed;
 * requests to it now return 404).
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
@Path("/v2/data-objects/cross-bulk")
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
      "Response timestamps are ISO 8601 UTC strings; clients render within-DO relative time on the " +
      "frontend by subtracting each series' first timestamp. The request `start`/`end` fields " +
      "accept ISO 8601 UTC strings (e.g. '2024-06-01T08:00:00Z').\n\n" +
      "The `kind` query parameter discriminates the payload family. Currently only `timeseries` " +
      "is supported; future kinds (`file`, `structured`) will extend this endpoint without a new path.",
    extensions = @Extension(name = "x-agent-hint", value = "Pass kind=timeseries + dataObjectAppIds + channelPredicate (urn:shepard:...). Returns one downsampled series per DO; empty points means 'no channel matched'.")
  )
  @APIResponse(
    responseCode = "200",
    description = "Resolved series across the requested DataObjects.",
    headers = @Header(name = "X-Total-Count", description = "Total resolved DataObjects before page-slicing.", schema = @Schema(type = SchemaType.INTEGER)),
    content = @Content(schema = @Schema(implementation = PagedResponseIO.class)))
  @APIResponse(responseCode = "400", description = "Validation error on request body, or unsupported/missing `kind`.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response getCrossDoBulkData(
    @Parameter(
      description = "Payload-family discriminator. Currently only `timeseries` is accepted; "
        + "future kinds (`file`, `structured`) will extend this endpoint without a new path.",
      schema = @Schema(enumeration = {"timeseries"}, defaultValue = "timeseries")
    )
    @QueryParam("kind") String kind,
    @QueryParam("page") @DefaultValue("0") @PositiveOrZero int page,
    @QueryParam("pageSize") @DefaultValue("50") @Min(1) @Max(100) int pageSize,
    @NotNull @Valid CrossDoBulkDataRequestIO body,
    @Context SecurityContext sc
  ) {
    if (kind == null || !kind.equals("timeseries")) {
      return problem("cross-bulk.bad-kind", "Bad Request", Response.Status.BAD_REQUEST,
        "Query parameter 'kind' must be 'timeseries'; got: " + kind);
    }

    final String caller = sc != null && sc.getUserPrincipal() != null
      ? sc.getUserPrincipal().getName()
      : null;
    if (caller == null) return problem(PT_UNAUTHORIZED, "Unauthorized", Response.Status.UNAUTHORIZED, "Authentication required");

    final int downsampleTo = clampDownsample(body.downsampleTo());
    final long start;
    final long end;
    try {
      start = parseNs(body.start());
      end   = parseNs(body.end());
    } catch (DateTimeParseException e) {
      return problem("cross-bulk.bad-timestamp", "Bad Request", Response.Status.BAD_REQUEST,
        "Invalid ISO 8601 timestamp in 'start' or 'end': " + e.getParsedString() +
        ". Expected format: '2024-06-01T08:00:00Z' or '2024-06-01T08:00:00.123456789Z'.");
    }
    final String predicate = body.channelPredicate();

    // Batch phase 1: collect valid appIds, preserving input order.
    List<String> requestedIds = new ArrayList<>();
    for (String id : body.dataObjectAppIds()) {
      if (id != null && !id.isBlank()) requestedIds.add(id);
    }

    // Batch phase 2: single Neo4j round-trip for permission gate (replaces N serial calls).
    Set<String> allowedIds =
      permissionsService.filterAllowedDataObjectAppIds(requestedIds, AccessType.Read, caller);

    // Build the ordered, permission-filtered list so we can page BEFORE running LTTB queries.
    // Preserves input order; silently drops forbidden and unknown DOs.
    List<String> allowedRequestedIds = new ArrayList<>();
    for (String id : requestedIds) {
      if (allowedIds.contains(id)) allowedRequestedIds.add(id);
    }

    int total = allowedRequestedIds.size();
    int fromIndex = page * pageSize;
    int toIndex = Math.min(fromIndex + pageSize, total);
    List<String> pagedIds = fromIndex >= total ? List.of() : allowedRequestedIds.subList(fromIndex, toIndex);

    // Batch phase 3: single Neo4j round-trip for DO names — only for the page slice.
    Map<String, String> namesByAppId = pagedIds.isEmpty()
      ? Map.of()
      : dataObjectDAO.findNamesByAppIds(new HashSet<>(pagedIds));

    List<CrossDoSeriesIO> out = new ArrayList<>();
    for (String doAppId : pagedIds) {
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
      List<TimeseriesDataPointV2IO> points;
      try {
        points = timeseriesService.getDataPointsLttbOptimised(
          pick.containerId(), tuple, start, end, downsampleTo
        ).stream().map(TimeseriesDataPointV2IO::from).toList();
      } catch (RuntimeException ex) {
        points = List.of();
      }

      out.add(new CrossDoSeriesIO(doAppId, doName, predicate, pick.symbolicName(), points));
    }

    return Response.ok(new PagedResponseIO<>(out, total, page, pageSize))
        .header("X-Total-Count", (long) total)
        .build();
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

  /** Converts an ISO 8601 UTC string to nanoseconds since the Unix epoch. */
  static long parseNs(String iso) {
    Instant instant = Instant.parse(iso.trim());
    return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
  }
}
