package de.dlr.shepard.v2.timeseriescontainer.io;

import jakarta.validation.constraints.NotBlank;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * TS-OPT2 (bulk-trace variant) — single channel selector inside a
 * {@link BulkTraceRequestIO}.
 *
 * <p>Carries the legacy 5-tuple needed to locate the channel plus a
 * caller-assigned {@code role} label (e.g. "x", "y", "rot_a") that
 * is echoed back in the response so the caller can fan out data to
 * their role variables without a second ID-lookup.
 */
@Schema(name = "BulkTraceChannel", description = "Channel selector with caller-assigned role label.")
public record BulkTraceChannelIO(

  @NotBlank
  @Schema(description = "Caller-assigned role label echoed verbatim in the response (e.g. \"x\", \"rot_a\").", required = true)
  String role,

  @NotBlank
  @Schema(description = "Timeseries measurement name.", required = true)
  String measurement,

  @Schema(description = "Timeseries device field. Null or blank = wildcard match.")
  String device,

  @Schema(description = "Timeseries location field. Null or blank = wildcard match.")
  String location,

  @Schema(description = "Timeseries symbolicName field. Null or blank = wildcard match.")
  String symbolicName,

  @NotBlank
  @Schema(description = "Timeseries field name.", required = true)
  String field
) {}
