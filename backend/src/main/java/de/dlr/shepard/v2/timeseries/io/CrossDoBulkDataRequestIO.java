package de.dlr.shepard.v2.timeseries.io;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * TS-CROSS-DO-VIEW-1 — request body for the cross-DataObject bulk-data endpoint.
 *
 * <p>Route: {@code POST /v2/data-objects/cross-bulk?kind=timeseries}
 *
 * <p>Resolves each {@code dataObjectAppId} to its {@link
 * de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference}s,
 * finds the channel whose {@code AnnotatableTimeseries} carries a
 * {@code SemanticAnnotation} with {@code propertyIRI = channelPredicate}, and
 * fetches that channel's series in the {@code [start, end]} time window with
 * LTTB downsampling. {@code start} and {@code end} are ISO 8601 UTC strings
 * with optional nanosecond precision (e.g. {@code "2024-06-01T08:00:00Z"} or
 * {@code "2024-06-01T08:00:00.123456789Z"}). The response carries one entry
 * per resolved DataObject; DataObjects with no matching channel return an
 * empty {@code points} array (the whole request never 404s).
 *
 * <p>Permission gate is per-DataObject: callers without Read on a given DO are
 * silently dropped from the response (no 403 for the whole request — see also
 * {@link de.dlr.shepard.auth.permission.services.PermissionsService#isAccessAllowedForDataObjectAppId}).
 *
 * <p>Up to 100 DataObjects per request; downsample target between 1 and 5000
 * inclusive.
 *
 * <p>Single channel predicate per request; multi-channel small-multiples is
 * deferred to a future revision. Where a single DataObject has multiple
 * channels matching the predicate, the first by lexicographic
 * {@code symbolicName} ascending is picked (deterministic).
 */
@Schema(
  name = "CrossDoBulkDataRequest",
  description = "Cross-DataObject bulk-data request: many DOs, one channel predicate, one time window."
)
public record CrossDoBulkDataRequestIO(

  @NotEmpty
  @Size(max = 100)
  @Schema(
    description = "DataObject appIds (UUID v7 strings) to resolve channels under. Max 100.",
    required = true,
    example = "[\"01930a2b-fe4c-7e3c-9f1d-8a5b2c3d4e5f\",\"01930a2b-fe4c-7e3c-9f1d-8a5b2c3d4e60\"]"
  )
  List<@NotNull String> dataObjectAppIds,

  @NotNull
  @Schema(
    description = "Canonical channel-key annotation predicate IRI to match (exact match on " +
      "SemanticAnnotation.propertyIRI). Channels under each DataObject's TimeseriesReferences " +
      "are scanned for this predicate. Example: 'urn:shepard:afp:tcp-temperature-c'.",
    required = true,
    example = "urn:shepard:afp:tcp-temperature-c"
  )
  String channelPredicate,

  @NotNull
  @Schema(
    description = "Window start as ISO 8601 UTC, nanosecond precision supported. E.g. '2024-06-01T08:00:00Z' or '2024-06-01T08:00:00.000000000Z'.",
    required = true,
    example = "2024-06-01T08:00:00Z"
  )
  String start,

  @NotNull
  @Schema(
    description = "Window end as ISO 8601 UTC, nanosecond precision supported. Must be after start.",
    required = true,
    example = "2024-06-01T09:00:00Z"
  )
  String end,

  @Positive
  @Schema(
    description = "LTTB target rows per series. Minimum 1, default 500 (when null or omitted), hard cap 5000.",
    minimum = "1",
    maximum = "5000",
    example = "500"
  )
  Integer downsampleTo
) {}
